package openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FilePurpose;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputFile;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseInputText;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import config.Config;
import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public final class OpenAiClient {
    private final OpenAIClient client;
    private final ReasoningEffort reasoningEffort;

    public OpenAiClient(String apiKey, Duration requestTimeout, ReasoningEffort reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey).maxRetries(0).timeout(requestTimeout).build();
    }

    public static OpenAiClient create(Config config) {
        try {
            config.validateGenerationSettings();
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("invalid generation configuration > " + error.getMessage(), error);
        }
        return new OpenAiClient(readApiKey(config.getLlmApiKeyEnv()), config.getLlmRequestTimeout(),
                ReasoningEffort.of(config.getLlmReasoningEffort()));
    }

    // Environment variables take precedence over the git-ignored .env file.
    public static String readApiKey(String envVarName) {
        String key = Dotenv.configure().ignoreIfMissing().ignoreIfMalformed().load().get(envVarName);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("OpenAI API key missing > add " + envVarName + "=your-api-key to .env in the project folder, then press Enter to retry");
        }
        return key;
    }

    //The SDK derives the response schema from GeneratedTesters.
    public GeneratedTesters generate(String model, String prompt, String documentId) {
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model is required");
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("prompt is required");
        StructuredResponseCreateParams<GeneratedTesters> params = ResponseCreateParams.builder()
                .model(model)
                .inputOfResponse(List.of(userMessage(prompt, documentId)))
                .reasoning(Reasoning.builder().effort(reasoningEffort).build())
                .text(GeneratedTesters.class).build();
        return extractOutput(client.responses().create(params));
    }

    private static ResponseInputItem userMessage(String prompt, String documentId) {
        if (documentId == null) {
            return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                    .role(EasyInputMessage.Role.USER).content(prompt).build());
        }
        return ResponseInputItem.ofMessage(ResponseInputItem.Message.builder()
                .role(ResponseInputItem.Message.Role.USER)
                .addContent(ResponseInputText.builder().text(prompt).build())
                .addContent(ResponseInputFile.builder().fileId(documentId).build()).build());
    }

    private GeneratedTesters extractOutput(StructuredResponse<GeneratedTesters> response) {
        if (response.status().filter(ResponseStatus.INCOMPLETE::equals).isPresent()) {
            throw new IllegalStateException("response was incomplete (output limit reached)");
        }
        try {
            for (var item : response.output()) {
                if (item.message().isEmpty()) continue;
                for (var content : item.message().orElseThrow().content()) {
                    if (content.refusal().isPresent()) {
                        throw new IllegalStateException("model refused > " + content.refusal().orElseThrow().refusal());
                    }
                    var output = content.outputText();
                    if (output.isPresent()) return output.get();
                }
            }
        } catch (IllegalStateException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new IllegalStateException("could not parse structured reply > " + error.getMessage(), error);
        }
        throw new IllegalStateException("response contained no structured output");
    }

    public String uploadPdf(Path pdf) {
        return client.files().create(FileCreateParams.builder().file(pdf).purpose(FilePurpose.USER_DATA).build()).id();
    }

    public void deleteUpload(String document) {
        try {
            client.files().delete(document);
        } catch (RuntimeException ignored) {
            // Cleanup must not replace the generation result or original failure.
        }
    }
}
