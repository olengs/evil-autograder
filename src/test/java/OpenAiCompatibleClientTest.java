import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import generation.llm.LlmException;
import generation.llm.LlmRequest;
import generation.llm.OpenAiCompatibleClient;
import generation.schema.GeneratedTesters;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleClientTest {
    @Test
    void officialOpenAiDoesNotAutomaticallyRetryServerErrors() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            calls.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] error = "{\"error\":{\"message\":\"simulated failure\",\"type\":\"server_error\"}}".getBytes();
            exchange.sendResponseHeaders(500, error.length);
            try (var output = exchange.getResponseBody()) {
                output.write(error);
            }
        });
        server.start();
        try {
            var client = new OpenAiCompatibleClient("test-key",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", true);
            assertThrows(LlmException.class, () -> client.completeStructured(
                    new LlmRequest("gpt-6.1-terra", "Generate all testers.", null), GeneratedTesters.class));
            assertEquals(1, calls.get());
            var json = new ObjectMapper().readTree(requestBody.get());
            assertEquals("gpt-6.1-terra", json.path("model").asText());
            assertEquals("high", json.path("reasoning").path("effort").asText());
            assertEquals("json_schema", json.path("text").path("format").path("type").asText());
        } finally {
            server.stop(0);
        }
    }
}
