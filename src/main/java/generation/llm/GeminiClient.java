package generation.llm;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import com.google.genai.types.UploadFileConfig;

import utils.PdfFileUtils;

public final class GeminiClient implements LlmClient { //google gemini generateContent via the google gen ai sdk > exam pdf goes up through the files api
    private final Client client;

    // blank baseUrl > the sdk's public gemini endpoint > otherwise an override e.g.
    // a proxy
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
        // prompt text first > then the uploaded pdf by reference when one was uploaded
        List<Part> parts = new ArrayList<>();
        parts.add(Part.fromText(request.getPrompt()));
        if (request.hasDocument()) {
            UploadedDocument document = request.getDocument();
            parts.add(Part.fromUri(document.reference(), document.mimeType()));
        }

        GenerateContentResponse response;
        try {
            response = client.models.generateContent(request.getModel(),
                    Content.fromParts(parts.toArray(Part[]::new)), null);
        } catch (ApiException error) {
            // http status & provider message
            throw requestFailed(error);
        } catch (GenAiIOException error) {
            // transport failure
            throw requestFailed(error);
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
            return new UploadedDocument(uri, uploaded.name().orElse(""), PdfFileUtils.PDF_MIME_TYPE,
                    PdfFileUtils.displayName(pdf));
        } catch (ApiException error) {
            throw requestFailed(error);
        } catch (GenAiIOException error) {
            throw requestFailed(error);
        }
    }

    @Override
    public void deleteUpload(UploadedDocument document) {
        if (document == null || document.name().isBlank()) {
            return;
        }
        try {
            client.files.delete(document.name(), null);
        } catch (RuntimeException ignored) {
            // best effort > gemini also expires uploaded files on its own
        }
    }

    // separate catches on purpose > a multi-catch types the variable as the sdk's package-private
    // BaseException & the jvm then throws IllegalAccessError at runtime
    private LlmException requestFailed(RuntimeException error) {
        return new LlmException(providerName() + " request failed > " + error.getMessage(), error);
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
