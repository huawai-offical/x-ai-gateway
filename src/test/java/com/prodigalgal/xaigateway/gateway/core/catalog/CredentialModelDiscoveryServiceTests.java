package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryService;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialModelDiscoveryServiceTests {

    @Test
    void shouldDiscoverOllamaCapabilitiesFromTagsAndShow() {
        AtomicInteger showCalls = new AtomicInteger();
        ExchangeFunction exchangeFunction = request -> {
            if (request.url().getPath().endsWith("/api/tags")) {
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("""
                                {
                                  "models": [
                                    {"name":"qwen3:latest"},
                                    {"name":"llava:latest"}
                                  ]
                                }
                                """)
                        .build());
            }
            int index = showCalls.getAndIncrement();
            String body = index == 0
                    ? """
                    {"capabilities":["tools","thinking"]}
                    """
                    : """
                    {"capabilities":["vision"]}
                    """;
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build());
        };

        CredentialModelDiscoveryService service = new CredentialModelDiscoveryService(
                Mockito.mock(UpstreamCredentialRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                Mockito.mock(ProviderSiteRegistryService.class),
                WebClient.builder().exchangeFunction(exchangeFunction)
        );

        CredentialModelDiscoveryService.CredentialConnectivityProbe probe =
                service.probe(ProviderType.OLLAMA_DIRECT, "http://localhost:11434", null);

        assertEquals(2, probe.models().size());
        DiscoveredModelDefinition qwen = probe.models().get(0);
        DiscoveredModelDefinition llava = probe.models().get(1);
        assertTrue(qwen.supportsTools());
        assertTrue(qwen.supportsThinking());
        assertEquals(ReasoningTransport.OLLAMA_THINKING, qwen.reasoningTransport());
        assertTrue(qwen.supportedProtocols().contains("anthropic_native"));
        assertTrue(llava.supportsImageInput());
    }

    @Test
    void shouldFallbackToHeuristicsWhenOllamaShowFails() {
        ExchangeFunction exchangeFunction = request -> {
            if (request.url().getPath().endsWith("/api/tags")) {
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("""
                                {
                                  "models": [
                                    {"name":"gpt-oss:20b"}
                                  ]
                                }
                                """)
                        .build());
            }
            return Mono.error(new IllegalStateException("show failed"));
        };

        CredentialModelDiscoveryService service = new CredentialModelDiscoveryService(
                Mockito.mock(UpstreamCredentialRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                Mockito.mock(ProviderSiteRegistryService.class),
                WebClient.builder().exchangeFunction(exchangeFunction)
        );

        CredentialModelDiscoveryService.CredentialConnectivityProbe probe =
                service.probe(ProviderType.OLLAMA_DIRECT, "http://localhost:11434", null);

        assertEquals(1, probe.models().size());
        DiscoveredModelDefinition model = probe.models().get(0);
        assertTrue(model.supportsTools());
        assertTrue(model.supportsThinking());
        assertEquals(ReasoningTransport.OLLAMA_THINKING, model.reasoningTransport());
    }

    @Test
    void shouldDiscoverCohereCompatibilityModelsViaOpenAiCompatibleSurface() {
        ExchangeFunction exchangeFunction = request -> {
            assertEquals("/compatibility/v1/models", request.url().getPath());
            assertEquals("Bearer cohere-secret", request.headers().getFirst(HttpHeaders.AUTHORIZATION));
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                            {
                              "data": [
                                {"id":"command-a-03-2025"},
                                {"id":"embed-v4.0"}
                              ]
                            }
                            """)
                    .build());
        };

        CredentialModelDiscoveryService service = new CredentialModelDiscoveryService(
                Mockito.mock(UpstreamCredentialRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                Mockito.mock(ProviderSiteRegistryService.class),
                WebClient.builder().exchangeFunction(exchangeFunction)
        );

        CredentialModelDiscoveryService.CredentialConnectivityProbe probe = service.probe(
                ProviderType.OPENAI_COMPATIBLE,
                "https://api.cohere.ai/compatibility/v1",
                CredentialAuthKind.API_KEY,
                "cohere-secret",
                java.util.Map.of()
        );

        assertEquals(2, probe.models().size());
        assertTrue(probe.models().stream().anyMatch(model -> model.modelName().equals("command-a-03-2025")));
        assertTrue(probe.models().stream().anyMatch(DiscoveredModelDefinition::supportsEmbeddings));
    }

    @Test
    void shouldDiscoverVertexModelsViaBearerCredentialAndMetadata() {
        ExchangeFunction exchangeFunction = request -> {
            assertEquals("/v1beta1/publishers/google/models", request.url().getPath());
            assertEquals("Bearer ya29.vertex-token", request.headers().getFirst(HttpHeaders.AUTHORIZATION));
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                            {
                              "publisherModels": [
                                {"name":"publishers/google/models/gemini-2.5-pro"},
                                {"name":"publishers/google/models/text-embedding-004"}
                              ]
                            }
                            """)
                    .build());
        };

        CredentialModelDiscoveryService service = new CredentialModelDiscoveryService(
                Mockito.mock(UpstreamCredentialRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                Mockito.mock(ProviderSiteRegistryService.class),
                WebClient.builder().exchangeFunction(exchangeFunction)
        );

        CredentialModelDiscoveryService.CredentialConnectivityProbe probe = service.probe(
                ProviderType.GEMINI_DIRECT,
                "https://aiplatform.googleapis.com/v1/projects/demo/locations/us-central1/endpoints/openapi",
                CredentialAuthKind.GOOGLE_ACCESS_TOKEN,
                "ya29.vertex-token",
                java.util.Map.of("projectId", "demo", "location", "us-central1")
        );

        assertEquals(2, probe.models().size());
        assertTrue(probe.models().stream().anyMatch(model -> model.modelName().equals("gemini-2.5-pro")));
        assertTrue(probe.models().stream().allMatch(model -> model.supportedProtocols().contains("google_native")));
    }
}
