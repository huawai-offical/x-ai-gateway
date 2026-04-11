package com.prodigalgal.xaigateway.provider.adapter.gemini;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

@Service
public class GeminiChatModelFactory {

    private final ObservationRegistry observationRegistry;

    public GeminiChatModelFactory(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    public GoogleGenAiChatModel create(String baseUrl, String apiKey, GoogleGenAiChatOptions options) {
        HttpOptions.Builder httpOptionsBuilder = HttpOptions.builder();
        if (baseUrl != null && !baseUrl.isBlank()) {
            httpOptionsBuilder.baseUrl(baseUrl);
        }

        Client client = Client.builder()
                .apiKey(apiKey)
                .vertexAI(false)
                .httpOptions(httpOptionsBuilder.build())
                .build();

        return GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .defaultOptions(options)
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .observationRegistry(observationRegistry)
                .build();
    }
}
