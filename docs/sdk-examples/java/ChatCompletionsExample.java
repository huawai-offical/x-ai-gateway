import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ChatCompletionsExample {
    public static void main(String[] args) throws Exception {
        String baseUrl = getenv("X_AI_GATEWAY_BASE_URL", "http://localhost:8080").replaceAll("/$", "");
        String apiKey = System.getenv("X_AI_GATEWAY_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("X_AI_GATEWAY_API_KEY is required");
        }
        String model = getenv("X_AI_GATEWAY_MODEL", "gpt-4o-mini");
        String payload = """
                {"model":"%s","messages":[{"role":"user","content":"ping"}]}
                """.formatted(model).trim();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("X-AI-Gateway-Client-Family", "GENERIC_OPENAI")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
    }

    private static String getenv(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
