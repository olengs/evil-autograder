package generation.llm;

import java.nio.file.Path;
import java.util.List;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.OpenAIException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FileObject;
import com.openai.models.files.FilePurpose;

import utils.PdfFileUtils;

public final class OpenAiCompatibleClient implements LlmClient { //openai / groq / openrouter / ollama chat-completions via the openai sdk > exam pdf only where the provider accepts file parts
    // the sdk needs some key > local servers like ollama accept any non-blank value
    private static final String NO_KEY_PLACEHOLDER = "none";

    private final OpenAIClient client;
    private final boolean supportsPdf; // only real openai takes pdf file parts > groq / openrouter / ollama do not

    // blank baseUrl > the sdk's default https://api.openai.com/v1 > otherwise e.g. https://api.groq.com/openai/v1
    public OpenAiCompatibleClient(String apiKey, String baseUrl, boolean supportsPdf) {
        this.supportsPdf = supportsPdf;
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .apiKey(apiKey == null || apiKey.isBlank() ? NO_KEY_PLACEHOLDER : apiKey)
                .timeout(REQUEST_TIMEOUT); // same budget as the gemini client
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
        if (request.hasDocument()) {
            // prompt text plus the uploaded pdf by file id
            builder.addMessage(ChatCompletionUserMessageParam.builder()
                    .contentOfArrayOfContentParts(List.of(
                            ChatCompletionContentPart.ofText(
                                    ChatCompletionContentPartText.builder().text(request.getPrompt()).build()),
                            ChatCompletionContentPart.ofFile(ChatCompletionContentPart.File.builder()
                                    .file(ChatCompletionContentPart.File.FileObject.builder()
                                            .fileId(request.getDocument().reference()).build())
                                    .build())))
                    .build());
        } else {
            builder.addUserMessage(request.getPrompt());
        }
        ChatCompletionCreateParams params = builder.build();

        ChatCompletion completion;
        try {
            completion = client.chat().completions().create(params);
        } catch (OpenAIException error) {
            // covers transport failures & non-2xx statuses > the sdk message carries the provider's error
            throw new LlmException(providerName() + " request failed > " + error.getMessage(), error);
        }
        return extractText(completion);
    }

    @Override
    public UploadedDocument uploadPdf(Path pdf) {
        if (!supportsPdf) {
            throw new LlmException("this provider cannot take a pdf > use llm.provider=openai or gemini, or set llm.send.pdf=false");
        }
        try {
            FileObject uploaded = client.files().create(FileCreateParams.builder()
                    .file(pdf)
                    .purpose(FilePurpose.USER_DATA)
                    .build());
            return new UploadedDocument(uploaded.id(), uploaded.id(), PdfFileUtils.PDF_MIME_TYPE,
                    PdfFileUtils.displayName(pdf));
        } catch (OpenAIException error) {
            throw new LlmException(providerName() + " pdf upload failed > " + error.getMessage(), error);
        }
    }

    @Override
    public void deleteUpload(UploadedDocument document) {
        if (document == null || document.name().isBlank()) {
            return;
        }
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
