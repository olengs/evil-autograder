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

public final class OpenAiClient implements LlmClient { //talks to the openai platform through the openai sdk > responses api for structured output & pdf upload
    private final OpenAIClient client;

    public OpenAiClient(String apiKey) {
        this(apiKey, null);
    }

    // a blank baseUrl uses the sdk's default https://api.openai.com/v1 > a custom one lets a test point at a local server
    public OpenAiClient(String apiKey, String baseUrl) {
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                // keep one-shot generation to one attempt even on timeouts, rate limits or server errors
                .maxRetries(0)
                .timeout(REQUEST_TIMEOUT);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        this.client = builder.build();
    }

    @Override
    public String providerName() {
        return "openai";
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
        // the prompt is the only message > all instructions come from tester-prompt.json
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
                .inputOfResponse(List.of(user))
                .reasoning(Reasoning.builder().effort(ReasoningEffort.HIGH).build());
        StructuredResponseCreateParams<T> params = builder.text(type).build();

        StructuredResponse<T> response;
        try {
            response = client.responses().create(params);
        } catch (OpenAIException error) {
            throw LlmException.requestFailed(providerName(), error);
        }
        return extractOutput(response);
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
