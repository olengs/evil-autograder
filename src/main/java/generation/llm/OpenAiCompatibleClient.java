package generation.llm;

import java.net.URI;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class OpenAiCompatibleClient extends AbstractHttpLlmClient { //groq / openrouter / ollama chat-completions (text only)
    private final String baseUrl; // required > e.g. https://api.groq.com/openai/v1
    private final String apiKey; // may be blank for a local ollama server

    public OpenAiCompatibleClient(String apiKey, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("openai-compatible base url is required (llm.base.url)");
        }
        this.apiKey = apiKey == null ? "" : apiKey;
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    @Override
    public String providerName() {
        return "openai-compatible";
    }

    @Override
    public String complete(LlmRequest request) {
        String body = buildRequestBody(request);
        URI uri = URI.create(baseUrl + "/chat/completions");
        // send bearer auth only when a key is present > local servers may need none
        String response = apiKey.isBlank()
                ? postJson(uri, body)
                : postJson(uri, body, "Authorization", "Bearer " + apiKey);
        return extractText(response);
    }

    // { "model":..., "messages":[ {"role":"user","content": prompt} ] }
    // pdf bytes are ignored here > text-only providers receive extracted pdf text inside the prompt
    String buildRequestBody(LlmRequest request) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", request.getPrompt());

        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject root = new JsonObject();
        root.addProperty("model", request.getModel());
        root.add("messages", messages);
        return gson.toJson(root);
    }

    // choices[0].message.content > fail loudly when the shape is unexpected
    private String extractText(String responseBody) {
        try {
            JsonObject root = gson.fromJson(responseBody, JsonObject.class);
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new LlmException("response had no choices > " + trim(responseBody));
            }
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            String content = message.get("content").getAsString();
            if (content == null || content.isBlank()) {
                throw new LlmException("response contained no content > " + trim(responseBody));
            }
            return content;
        } catch (LlmException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new LlmException("could not parse openai-compatible response > " + trim(responseBody), error);
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
