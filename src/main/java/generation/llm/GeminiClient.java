package generation.llm;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import com.google.genai.types.UploadFileConfig;
import com.openai.core.JsonField;
import com.openai.core.ObjectMappers;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import utils.PdfFileUtils;

public final class GeminiClient implements LlmClient { //google gemini generateContent via the google gen ai sdk > exam pdf goes up through the files api
    // the openai sdk's mapper knows its JsonField types > used only to turn the schema into a plain map
    private static final ObjectMapper SDK_JSON = ObjectMappers.jsonMapper();
    // a plain mapper reads replies > the structured output classes use public fields, which the sdk mapper ignores
    private static final ObjectMapper REPLY_JSON = new ObjectMapper();

    private final Client client;

    // blank baseUrl > the sdk's public gemini endpoint > otherwise an override e.g. a proxy
    public GeminiClient(String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("gemini api key is required");
        }
        HttpOptions.Builder httpOptions = HttpOptions.builder().timeout((int) REQUEST_TIMEOUT.toMillis()); // sdk takes milliseconds
        if (baseUrl != null && !baseUrl.isBlank()) {
            httpOptions.baseUrl(baseUrl);
        }
        this.client = Client.builder().apiKey(apiKey).httpOptions(httpOptions.build()).build();
    }

    @Override
    public String providerName() {
        return "gemini";
    }

    @Override
    public String complete(LlmRequest request) {
        return generate(request, null);
    }

    @Override
    public boolean supportsStructuredOutput() {
        return true;
    }

    // json mode with the schema built from the class > the same schema the openai sdk sends for its structured outputs
    @Override
    public <T> T completeStructured(LlmRequest request, Class<T> type) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(STRUCTURED_NOTE)))
                .responseMimeType("application/json")
                .responseJsonSchema(jsonSchemaOf(type))
                .build();
        String json = generate(request, config);
        try {
            return REPLY_JSON.readValue(json, type);
        } catch (JsonProcessingException | RuntimeException error) {
            throw new LlmException("could not parse structured reply > " + error.getMessage(), error);
        }
    }

    // let the openai sdk build its response_format for the class & read the json schema back out
    // the sdk's own schema helpers are not callable from java > this keeps one schema for every provider
    private static Map<?, ?> jsonSchemaOf(Class<?> type) {
        JsonField<?> schema = ChatCompletionCreateParams.builder()
                .model("schema-only").addUserMessage("schema-only") // required by build() > never sent
                .responseFormat(type).build().rawParams()
                .responseFormat().flatMap(ChatCompletionCreateParams.ResponseFormat::jsonSchema)
                .orElseThrow(() -> new LlmException("could not build a json schema for " + type.getSimpleName()))
                .jsonSchema()._schema(); // the raw field > the typed schema() accessor rejects a free-form schema
        // a plain map so the gemini sdk serialises it as json schema > not as openai JsonField objects
        return SDK_JSON.convertValue(schema, Map.class);
    }

    // prompt text first > then the uploaded pdf by reference when one was uploaded > config null for plain text
    private String generate(LlmRequest request, GenerateContentConfig config) {
        List<Part> parts = new ArrayList<>();
        parts.add(Part.fromText(request.getPrompt()));
        if (request.hasDocument()) {
            parts.add(Part.fromUri(request.getDocument().reference(), PdfFileUtils.PDF_MIME_TYPE));
        }

        GenerateContentResponse response;
        try {
            response = client.models.generateContent(request.getModel(),
                    Content.fromParts(parts.toArray(Part[]::new)), config);
        } catch (ApiException error) {
            // separate catches on purpose > a multi-catch types the variable as the sdk's package-private
            // BaseException & the jvm then throws IllegalAccessError at runtime
            throw LlmException.requestFailed(providerName(), error);
        } catch (GenAiIOException error) {
            throw LlmException.requestFailed(providerName(), error);
        }
        return extractText(response);
    }

    @Override
    public UploadedDocument uploadPdf(Path pdf) {
        UploadFileConfig config = UploadFileConfig.builder()
                .mimeType(PdfFileUtils.PDF_MIME_TYPE)
                .displayName(PdfFileUtils.displayName(pdf))
                .build();
        try {
            com.google.genai.types.File uploaded = client.files.upload(pdf.toString(), config);
            String uri = uploaded.uri().orElseThrow(() -> new LlmException("gemini upload returned no file uri"));
            return new UploadedDocument(uri, uploaded.name().orElse(""));
        } catch (ApiException error) {
            throw LlmException.requestFailed(providerName(), error);
        } catch (GenAiIOException error) {
            throw LlmException.requestFailed(providerName(), error);
        }
    }

    @Override
    public void deleteUpload(UploadedDocument document) {
        if (document.name().isBlank()) {
            return;
        }
        try {
            client.files.delete(document.name(), null);
        } catch (RuntimeException ignored) {
            // best effort > gemini also expires uploaded files on its own
        }
    }

    // text of the first candidate's parts joined by the sdk > fail loudly when the reply is empty
    private String extractText(GenerateContentResponse response) {
        if (response.candidates().map(List::isEmpty).orElse(true)) {
            throw new LlmException("gemini response had no candidates");
        }
        String text = response.text();
        if (text == null || text.isBlank()) {
            throw new LlmException("gemini response contained no text");
        }
        return text;
    }
}
