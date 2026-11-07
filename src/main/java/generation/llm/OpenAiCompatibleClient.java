package generation.llm;

import java.nio.file.Path;
import java.util.List;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.OpenAIException;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.chat.completions.StructuredChatCompletion;
import com.openai.models.chat.completions.StructuredChatCompletionCreateParams;
import com.openai.models.chat.completions.StructuredChatCompletionMessage;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FileObject;
import com.openai.models.files.FilePurpose;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputFile;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseInputText;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.StructuredResponseOutputMessage;

public final class OpenAiCompatibleClient implements LlmClient { //openai / groq / openrouter / ollama via the openai sdk > pdf upload only on the real openai platform
    // the sdk needs some key > local servers like ollama accept any non-blank value
    private static final String NO_KEY_PLACEHOLDER = "none";

    private final OpenAIClient client;
    // real openai platform > the only one here that takes pdf file parts & the responses api
    // groq / openrouter / ollama get chat completions > structured output through response_format
    private final boolean officialOpenAi;

    // blank baseUrl > the sdk's default https://api.openai.com/v1 > otherwise e.g. https://api.groq.com/openai/v1
    public OpenAiCompatibleClient(String apiKey, String baseUrl, boolean officialOpenAi) {
        this.officialOpenAi = officialOpenAi;
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .apiKey(apiKey == null || apiKey.isBlank() ? NO_KEY_PLACEHOLDER : apiKey)
                .timeout(REQUEST_TIMEOUT); // same budget as the gemini client
        if (officialOpenAi) {
            // keep one-shot generation to one attempt even on timeouts, rate limits or server errors
            builder.maxRetries(0);
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        this.client = builder.build();
    }

    @Override
    public String providerName() {
        return "openai-compatible";
    }

    @Override
    public String complete(LlmRequest request) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder().model(request.getModel());
        addUserMessage(builder, request);
        ChatCompletion completion;
        try {
            completion = client.chat().completions().create(builder.build());
        } catch (OpenAIException error) {
            // covers transport failures & non-2xx statuses > the sdk message carries the provider's error
            throw LlmException.requestFailed(providerName(), error);
        }
        return extractText(completion);
    }

    @Override
    public boolean supportsStructuredOutput() {
        return true;
    }

    // structured outputs through the responses api > text(Class) makes the sdk send the strict json schema for the class
    @Override
    public <T> T completeStructured(LlmRequest request, Class<T> type) {
        if (!officialOpenAi) {
            return completeStructuredChat(request, type);
        }
        // system message first > then the prompt as the user message
        ResponseInputItem system = ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(EasyInputMessage.Role.SYSTEM)
                .content(STRUCTURED_NOTE)
                .build());
        ResponseInputItem user = request.hasDocument()
                // the uploaded exam pdf rides along as a file part > easy messages only carry text
                ? ResponseInputItem.ofMessage(ResponseInputItem.Message.builder()
                        .role(ResponseInputItem.Message.Role.USER)
                        .addContent(ResponseInputText.builder().text(request.getPrompt()).build())
                        .addContent(ResponseInputFile.builder().fileId(request.getDocument().reference()).build())
                        .build())
                : ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                        .role(EasyInputMessage.Role.USER)
                        .content(request.getPrompt())
                        .build());

        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                .model(request.getModel())
                .inputOfResponse(List.of(system, user));
        if ("gpt-6.1-sol".equals(request.getModel()) || "gpt-6.1-terra".equals(request.getModel())) {
            builder.reasoning(Reasoning.builder().effort(ReasoningEffort.HIGH).build());
        }
        StructuredResponseCreateParams<T> params = builder.text(type).build();

        StructuredResponse<T> response;
        try {
            response = client.responses().create(params);
        } catch (OpenAIException error) {
            throw LlmException.requestFailed(providerName(), error);
        }
        return extractOutput(response);
    }

    // compatible servers only speak chat completions > responseFormat(Class) sends the same json schema as response_format
    // a server or model without json schema support rejects the request & that error is reported
    private <T> T completeStructuredChat(LlmRequest request, Class<T> type) {
        StructuredChatCompletionCreateParams<T> params = ChatCompletionCreateParams.builder()
                .model(request.getModel())
                .addSystemMessage(STRUCTURED_NOTE)
                .addUserMessage(request.getPrompt())
                .responseFormat(type)
                .build();
        StructuredChatCompletion<T> completion;
        try {
            completion = client.chat().completions().create(params);
        } catch (OpenAIException error) {
            throw LlmException.requestFailed(providerName(), error);
        }
        try {
            if (completion.choices().isEmpty()) {
                throw new LlmException(providerName() + " response had no choices");
            }
            StructuredChatCompletionMessage<T> message = completion.choices().get(0).message();
            if (message.refusal().isPresent()) {
                throw new LlmException("model refused > " + message.refusal().get());
            }
            return message.content().orElseThrow(() -> new LlmException(providerName() + " response contained no content"));
        } catch (LlmException error) {
            throw error;
        } catch (RuntimeException error) {
            // the sdk throws when the json cannot be converted to the class
            throw new LlmException("could not parse structured reply > " + error.getMessage(), error);
        }
    }

    // output item > message > content > typed output > fail loudly on a refusal or a truncated reply
    private <T> T extractOutput(StructuredResponse<T> response) {
        if (response.status().filter(ResponseStatus.INCOMPLETE::equals).isPresent()) {
            throw new LlmException("response was incomplete (output limit reached)");
        }
        try {
            for (StructuredResponseOutputMessage<T> message : response.output().stream()
                    .flatMap(item -> item.message().stream()).toList()) {
                for (StructuredResponseOutputMessage.Content<T> content : message.content()) {
                    if (content.refusal().isPresent()) {
                        throw new LlmException("model refused > " + content.refusal().orElseThrow().refusal());
                    }
                    if (content.outputText().isPresent()) {
                        return content.outputText().get();
                    }
                }
            }
        } catch (LlmException error) {
            throw error;
        } catch (RuntimeException error) {
            // the sdk throws when the json cannot be converted to the class
            throw new LlmException("could not parse structured reply > " + error.getMessage(), error);
        }
        throw new LlmException("response contained no structured output");
    }

    // prompt text plus the uploaded pdf by file id when one was uploaded
    private static void addUserMessage(ChatCompletionCreateParams.Builder builder, LlmRequest request) {
        if (request.hasDocument()) {
            builder.addMessage(documentMessage(request));
        } else {
            builder.addUserMessage(request.getPrompt());
        }
    }

    private static ChatCompletionUserMessageParam documentMessage(LlmRequest request) {
        return ChatCompletionUserMessageParam.builder()
                .contentOfArrayOfContentParts(List.of(
                        ChatCompletionContentPart.ofText(
                                ChatCompletionContentPartText.builder().text(request.getPrompt()).build()),
                        ChatCompletionContentPart.ofFile(ChatCompletionContentPart.File.builder()
                                .file(ChatCompletionContentPart.File.FileObject.builder()
                                        .fileId(request.getDocument().reference()).build())
                                .build())))
                .build();
    }

    @Override
    public UploadedDocument uploadPdf(Path pdf) {
        if (!officialOpenAi) {
            throw new LlmException("this provider cannot take a pdf > use llm.provider=openai or gemini, or set llm.send.pdf=false");
        }
        try {
            FileObject uploaded = client.files().create(FileCreateParams.builder()
                    .file(pdf)
                    .purpose(FilePurpose.USER_DATA)
                    .build());
            return new UploadedDocument(uploaded.id(), uploaded.id());
        } catch (OpenAIException error) {
            throw LlmException.requestFailed(providerName(), error);
        }
    }

    @Override
    public void deleteUpload(UploadedDocument document) {
        try {
            client.files().delete(document.name());
        } catch (RuntimeException ignored) {
            // best effort > a leftover file only costs storage
        }
    }

    // choices[0].message.content > fail loudly when the reply is empty
    private String extractText(ChatCompletion completion) {
        List<ChatCompletion.Choice> choices = completion.choices();
        if (choices.isEmpty()) {
            throw new LlmException(providerName() + " response had no choices");
        }
        return choices.get(0).message().content()
                .filter(content -> !content.isBlank())
                .orElseThrow(() -> new LlmException(providerName() + " response contained no content"));
    }
}
