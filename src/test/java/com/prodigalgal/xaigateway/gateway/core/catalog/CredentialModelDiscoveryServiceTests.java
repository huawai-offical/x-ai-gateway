package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryService;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyScopeType;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.persistence.entity.ModelPolicyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ModelPolicyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
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

    @Test
    void shouldDeduplicateDiscoveredPoliciesWhenRefreshingCredentialModels() {
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        ProviderSiteRegistryService providerSiteRegistryService = Mockito.mock(ProviderSiteRegistryService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        ModelPolicyRepository modelPolicyRepository = Mockito.mock(ModelPolicyRepository.class);
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("""
                        {
                          "data": [
                            {"id":"mimo-v2-omni"},
                            {"id":"MIMO-V2-OMNI"}
                          ]
                        }
                        """)
                .build());
        CredentialModelDiscoveryService service = new CredentialModelDiscoveryService(
                credentialRepository,
                Mockito.mock(CredentialCryptoService.class),
                providerSiteRegistryService,
                new com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService(),
                credentialMaterialResolver,
                modelPolicyRepository,
                WebClient.builder().exchangeFunction(exchangeFunction)
        );
        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        ReflectionTestUtils.setField(credential, "id", 101L);
        credential.setProviderType(ProviderType.OPENAI_COMPATIBLE);
        credential.setBaseUrl("https://token-plan-sgp.xiaomimimo.com/v1");
        credential.setApiKeyCiphertext("cipher");
        credential.setApiKeyFingerprint("fp");
        credential.setSiteProfileId(2L);
        UpstreamSiteProfileEntity site = new UpstreamSiteProfileEntity();
        ReflectionTestUtils.setField(site, "id", 2L);
        site.setSiteKind(UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC);

        Mockito.when(credentialRepository.findById(101L)).thenReturn(Optional.of(credential));
        Mockito.when(credentialRepository.save(Mockito.any(UpstreamCredentialEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(credentialMaterialResolver.resolveStored(credential))
                .thenReturn(new ResolvedCredentialMaterial(101L, 2L, CredentialAuthKind.API_KEY, "secret", "fp", java.util.Map.of(), null, "credential"));
        Mockito.when(providerSiteRegistryService.ensureSiteProfile(ProviderType.OPENAI_COMPATIBLE, credential.getBaseUrl(), 2L))
                .thenReturn(site);
        Mockito.when(modelPolicyRepository.findAllByScopeTypeAndScopeIdAndEnabledTrueOrderByPriorityAscCreatedAtAsc(
                ModelPolicyScopeType.CREDENTIAL,
                101L
        )).thenReturn(List.of());
        Mockito.when(modelPolicyRepository.save(Mockito.any(ModelPolicyEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.refreshCredential(101L);

        ArgumentCaptor<ModelPolicyEntity> policyCaptor = ArgumentCaptor.forClass(ModelPolicyEntity.class);
        Mockito.verify(modelPolicyRepository, Mockito.times(1)).save(policyCaptor.capture());
        assertEquals("mimo-v2-omni", policyCaptor.getValue().getPublicModelKey());
        assertTrue(policyCaptor.getValue().getSupportedProtocols().contains("openai"));
        assertTrue(policyCaptor.getValue().getSupportedProtocols().contains("responses"));
    }
}
