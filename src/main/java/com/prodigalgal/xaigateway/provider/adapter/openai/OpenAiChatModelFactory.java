package com.prodigalgal.xaigateway.provider.adapter.openai;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class OpenAiChatModelFactory {

    private final WebClient.Builder webClientBuilder;
    private final ObservationRegistry observationRegistry;

    public OpenAiChatModelFactory(
            WebClient.Builder webClientBuilder,
            ObservationRegistry observationRegistry) {
        this.webClientBuilder = webClientBuilder;
        this.observationRegistry = observationRegistry;
    }

    public OpenAiChatModel create(String baseUrl, String apiKey, OpenAiChatOptions options) {
        OpenAiApi api = createApi(baseUrl, apiKey);

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .observationRegistry(observationRegistry)
                .build();
    }

    public OpenAiApi createApi(String baseUrl, String apiKey) {
        return new OpenAiApi.Builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .webClientBuilder(webClientBuilder.clone())
                .build();
    }
}
