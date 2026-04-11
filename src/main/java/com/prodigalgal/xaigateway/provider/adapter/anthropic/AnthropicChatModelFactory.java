package com.prodigalgal.xaigateway.provider.adapter.anthropic;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class AnthropicChatModelFactory {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final WebClient.Builder webClientBuilder;
    private final ObservationRegistry observationRegistry;

    public AnthropicChatModelFactory(
            WebClient.Builder webClientBuilder,
            ObservationRegistry observationRegistry) {
        this.webClientBuilder = webClientBuilder;
        this.observationRegistry = observationRegistry;
    }

    public AnthropicChatModel create(String baseUrl, String apiKey, AnthropicChatOptions options) {
        AnthropicApi api = new AnthropicApi.Builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .anthropicVersion(ANTHROPIC_VERSION)
                .webClientBuilder(webClientBuilder.clone())
                .build();

        return AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(options)
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .observationRegistry(observationRegistry)
                .build();
    }
}
