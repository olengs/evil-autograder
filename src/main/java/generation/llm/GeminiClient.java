package generation.llm;

import java.net.URI;
import java.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class GeminiClient extends AbstractHttpLlmClient { //google gemini generateContent > supports native pdf input
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    private final String baseUrl;
    private final String apiKey;

    public GeminiClient(String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("gemini api key is required");
        }
        this.apiKey = apiKey;
        // allow overriding the endpoint > fall back to the public one
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : stripTrailingSlash(baseUrl);
    }

    @Override
    public String providerName() {
        return "gemini";
    }

    @Override
    public String complete(LlmRequest request) {
        String body = buildRequestBody(request);
        // key travels as a query param for the generateContent endpoint
        URI uri = URI.create(baseUrl + "/v1beta/models/" + request.getModel() + ":generateContent?key=" + apiKey);
        String response = postJson(uri, body);
        return extractText(response);
    }

    // { "contents":[ { "parts":[ {"text":...}, {"inline_data":{...}} ] } ] }
    String buildRequestBody(LlmRequest request) {
        JsonArray parts = new JsonArray();

        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", request.getPrompt());
        parts.add(textPart);

        // attach the pdf inline when native-pdf mode is on
        if (request.hasPdf()) {
            JsonObject inlineData = new JsonObject();
            inlineData.addProperty("mime_type", "application/pdf");
            inlineData.addProperty("data", Base64.getEncoder().encodeToString(request.getPdfBytes()));
            JsonObject pdfPart = new JsonObject();
            pdfPart.add("inline_data", inlineData);
            parts.add(pdfPart);
        }

        JsonObject content = new JsonObject();
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);

        JsonObject root = new JsonObject();
        root.add("contents", contents);
        return gson.toJson(root);
    }

    // candidates[0].content.parts[*].text > joined > fail loudly when the shape is unexpected
    private String extractText(String responseBody) {
        try {
            JsonObject root = gson.fromJson(responseBody, JsonObject.class);
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new LlmException("gemini response had no candidates > " + trim(responseBody));
            }
            JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
            JsonArray parts = content.getAsJsonArray("parts");
            StringBuilder text = new StringBuilder();
            for (JsonElement part : parts) {
                JsonElement t = part.getAsJsonObject().get("text");
                if (t != null && !t.isJsonNull()) {
                    text.append(t.getAsString());
                }
            }
            if (text.isEmpty()) {
                throw new LlmException("gemini response contained no text > " + trim(responseBody));
            }
            return text.toString();
        } catch (LlmException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new LlmException("could not parse gemini response > " + trim(responseBody), error);
        }
    }

    private static String trim(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 500 ? body.substring(0, 500) + "..." : body;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
