package com.prodigalgal.xaigateway.gateway.core.resource;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCacheReferenceEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCacheReferenceRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class GatewayCacheResourceServiceTests {

    @Test
    void shouldImportGatewayCacheReference() {
        UpstreamCacheReferenceRepository repository = Mockito.mock(UpstreamCacheReferenceRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        GatewayCacheResourceService service = new GatewayCacheResourceService(repository, objectMapper);

        Mockito.when(repository.findByDistributedKeyIdAndProviderTypeAndModelGroupAndPrefixHash(
                        1L,
                        ProviderType.GEMINI_DIRECT,
                        "gemini-2.5-pro",
                        "prefix-1"))
                .thenReturn(Optional.empty());
        Mockito.when(repository.save(any())).thenAnswer(invocation -> {
            UpstreamCacheReferenceEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 42L);
            ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-04-30T10:00:00Z"));
            ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-04-30T10:00:00Z"));
            return entity;
        });

        ObjectNode request = objectMapper.createObjectNode();
        request.put("providerType", "GEMINI_DIRECT");
        request.put("credentialId", 11L);
        request.put("modelGroup", "gemini-2.5-pro");
        request.put("prefixHash", "prefix-1");
        request.put("externalCacheRef", "cachedContents/demo");

        ObjectNode response = service.importCache(1L, request);

        assertEquals("cache_42", response.path("id").asText());
        assertEquals("gateway.cache", response.path("object").asText());
        assertEquals("ACTIVE", response.path("status").asText());
    }

    @Test
    void shouldMarkCacheReferenceInvalidOnDelete() {
        UpstreamCacheReferenceRepository repository = Mockito.mock(UpstreamCacheReferenceRepository.class);
        GatewayCacheResourceService service = new GatewayCacheResourceService(repository, new ObjectMapper());
        UpstreamCacheReferenceEntity entity = entity();

        Mockito.when(repository.findByIdAndDistributedKeyId(42L, 1L)).thenReturn(Optional.of(entity));
        Mockito.when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ObjectNode response = service.delete(1L, "cache_42");

        assertTrue(response.path("deleted").asBoolean());
        assertEquals("INVALIDATED", entity.getStatus());
    }

    private UpstreamCacheReferenceEntity entity() {
        UpstreamCacheReferenceEntity entity = new UpstreamCacheReferenceEntity();
        ReflectionTestUtils.setField(entity, "id", 42L);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-04-30T10:00:00Z"));
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-04-30T10:00:00Z"));
        entity.setDistributedKeyId(1L);
        entity.setProviderType(ProviderType.GEMINI_DIRECT);
        entity.setCredentialId(11L);
        entity.setModelGroup("gemini-2.5-pro");
        entity.setPrefixHash("prefix-1");
        entity.setExternalCacheRef("cachedContents/demo");
        entity.setStatus("ACTIVE");
        return entity;
    }
}
