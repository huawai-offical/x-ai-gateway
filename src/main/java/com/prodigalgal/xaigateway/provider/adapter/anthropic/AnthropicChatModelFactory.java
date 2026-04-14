package com.prodigalgal.xaigateway.provider.adapter.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClientAsync;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.stereotype.Service;

@Service
public class AnthropicChatModelFactory {

    private final ObservationRegistry observationRegistry;

    public AnthropicChatModelFactory(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    public AnthropicChatModel create(String baseUrl, String apiKey, AnthropicChatOptions options) {
        AnthropicClient client = buildClient(baseUrl, apiKey);
        AnthropicClientAsync clientAsync = buildAsyncClient(baseUrl, apiKey);

        return AnthropicChatModel.builder()
                .anthropicClient(client)
                .anthropicClientAsync(clientAsync)
                .options(options)
                .observationRegistry(observationRegistry)
                .build();
    }

    private AnthropicClient buildClient(String baseUrl, String apiKey) {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .maxRetries(0);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    private AnthropicClientAsync buildAsyncClient(String baseUrl, String apiKey) {
        AnthropicOkHttpClientAsync.Builder builder = AnthropicOkHttpClientAsync.builder()
                .apiKey(apiKey)
                .maxRetries(0);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }
}
