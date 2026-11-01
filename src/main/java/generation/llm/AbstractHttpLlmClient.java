package generation.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

abstract class AbstractHttpLlmClient implements LlmClient { //shared http + json plumbing for concrete llm clients
    // one shared client > generous timeouts because model replies can be slow
    // TODO to config?
    protected static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    protected static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    protected final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    // disable html escaping > these are api payloads not html > keeps base64 & urls readable
    protected final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    // post a json body & return the raw response body > map transport & status failures to LlmException
    protected String postJson(URI uri, String jsonBody, String... headerPairs) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        // optional header pairs > key then value > e.g. Authorization, Bearer ...
        for (int i = 0; i + 1 < headerPairs.length; i += 2) {
            builder.header(headerPairs[i], headerPairs[i + 1]);
        }

        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException error) {
            throw new LlmException(providerName() + " request failed > " + error.getMessage(), error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new LlmException(providerName() + " request interrupted", error);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            // include a trimmed body so the caller can see the provider's error message
            String body = response.body() == null ? "" : response.body();
            String trimmed = body.length() > 500 ? body.substring(0, 500) + "..." : body;
            throw new LlmException(providerName() + " returned HTTP " + status + " > " + trimmed);
        }
        return response.body();
    }
}
