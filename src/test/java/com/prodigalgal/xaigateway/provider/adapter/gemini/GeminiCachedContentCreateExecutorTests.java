package com.prodigalgal.xaigateway.provider.adapter.gemini;

import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

class GeminiCachedContentCreateExecutorTests {

    @Test
    void shouldCreateGeminiCachedContentAndBindReference() {
        Fixture fixture = new Fixture();
        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        ReflectionTestUtils.setField(credential, "id", 11L);
        credential.setProviderType(ProviderType.GEMINI_DIRECT);
        credential.setBaseUrl("https://generativelanguage.googleapis.com");
        credential.setActive(true);
        Mockito.when(fixture.credentialRepository.findById(11L)).thenReturn(Optional.of(credential));
        Mockito.when(fixture.credentialMaterialResolver.resolveStored(credential))
                .thenReturn(new ResolvedCredentialMaterial(
                        11L,
                        null,
                        CredentialAuthKind.API_KEY,
                        "gemini-secret",
                        "fp",
                        Map.of(),
                        null,
                        "credential"));
        ObjectNode upstreamResponse = fixture.objectMapper.createObjectNode();
        upstreamResponse.put("name", "cachedContents/demo");
        Mockito.when(fixture.apiClient.create(Mockito.eq("https://generativelanguage.googleapis.com"), Mockito.eq("gemini-secret"), any(ObjectNode.class)))
                .thenReturn(upstreamResponse);
        ObjectNode request = fixture.objectMapper.createObjectNode();
        request.put("credentialId", 11);
        request.put("model", "gemini-2.5-pro");
        request.put("prefixHash", "prefix-1");
        request.putArray("contents").addObject()
                .put("role", "user")
                .putArray("parts").addObject().put("text", "hello");

        ObjectNode response = fixture.executor.create(1L, request);

        assertEquals("gateway.cache.create_result", response.path("object").asText());
        assertEquals("cachedContents/demo", response.path("external_cache_ref").asText());
        assertEquals("models/gemini-2.5-pro", response.path("upstream_request").path("model").asText());
        Mockito.verify(fixture.referenceService).bind(
                1L,
                "gemini-2.5-pro",
                "prefix-1",
                11L,
                "cachedContents/demo");
    }

    private static class Fixture {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        private final CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        private final GeminiCachedContentReferenceService referenceService = Mockito.mock(GeminiCachedContentReferenceService.class);
        private final GeminiCachedContentApiClient apiClient = Mockito.mock(GeminiCachedContentApiClient.class);
        private final GeminiCachedContentCreateExecutor executor = new GeminiCachedContentCreateExecutor(
                credentialRepository,
                credentialMaterialResolver,
                referenceService,
                apiClient,
                objectMapper);
    }
}
