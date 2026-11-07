package generation.llm;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import config.Config;
import io.github.cdimascio.dotenv.Dotenv;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.OpenAIException;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
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

    public OpenAiClient(String apiKey, Duration requestTimeout) {
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                //keep one-shot generation to one attempt even on timeouts, rate limits or server errors
                .maxRetries(0)
                .timeout(requestTimeout);
        this.client = builder.build();
    }

    public static LlmClient create(Config config) {
        return new OpenAiClient(readApiKey(config.getLlmApiKeyEnv()), config.getLlmRequestTimeout());
    }

    //the key comes from a real environment variable first > then a git-ignored .env file
    //dotenv-java applies that precedence for us > never from config.properties or source code
    private static String readApiKey(String envVarName) {
        String key = Dotenv.configure().ignoreIfMissing().ignoreIfMalformed().load().get(envVarName);
        if (key == null || key.isBlank()) {
            //report the variable name only > never echo a key value
            throw new LlmException("missing api key > set " + envVarName
                    + " as an environment variable or in a .env file");
        }
        return key;
    }

    @Override
    public String providerName() {
        return "openai";
    }

    //structured outputs through the responses api > text(Class) makes the sdk send the strict json schema for the class
    @Override
    public <T> T completeStructured(LlmRequest request, Class<T> type) {
        //the prompt is the only message > all instructions come from tester-prompt.json
        ResponseInputItem user = request.hasDocument()
                //the uploaded exam pdf rides along as a file part > easy messages only carry text
                ? ResponseInputItem.ofMessage(ResponseInputItem.Message.builder()
                        .role(ResponseInputItem.Message.Role.USER)
                        .addContent(ResponseInputText.builder().text(request.prompt()).build())
                        .addContent(ResponseInputFile.builder().fileId(request.document()).build())
                        .build())
                : ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                        .role(EasyInputMessage.Role.USER)
                        .content(request.prompt())
                        .build());

        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                .model(request.model())
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

    //output item > message > content > typed output > fail loudly on a refusal or a truncated reply
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
            //the sdk throws when the json cannot be converted to the class
            throw new LlmException("could not parse structured reply > " + error.getMessage(), error);
        }
        throw new LlmException("response contained no structured output");
    }

    @Override
    public String uploadPdf(Path pdf) {
        try {
            FileObject uploaded = client.files().create(FileCreateParams.builder()
                    .file(pdf)
                    .purpose(FilePurpose.USER_DATA)
                    .build());
            return uploaded.id();
        } catch (OpenAIException error) {
            throw LlmException.requestFailed(providerName(), error);
        }
    }

    @Override
    public void deleteUpload(String document) {
        try {
            client.files().delete(document);
        } catch (RuntimeException ignored) {
            //best effort > a leftover file only costs storage
        }
    }
}
