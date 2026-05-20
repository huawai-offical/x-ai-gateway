package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceCanonicalizer;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayAsyncResourceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncResourceAdminServiceTests {

    @Test
    void shouldListAsyncResourcesWithFilters() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayAsyncResourceCanonicalizer canonicalizer = new GatewayAsyncResourceCanonicalizer(
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(GatewayFileRepository.class),
                new ObjectMapper()
        );
        AsyncResourceAdminService service = new AsyncResourceAdminService(gatewayAsyncResourceRepository, canonicalizer);

        when(gatewayAsyncResourceRepository.search(ArgumentMatchers.eq(1L), ArgumentMatchers.eq(GatewayAsyncResourceType.UPLOAD), ArgumentMatchers.eq("created"), ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(entity("upload_1", GatewayAsyncResourceType.UPLOAD, "created")));

        var result = service.listAsyncResources(1L, GatewayAsyncResourceType.UPLOAD, "CREATED", null, null);

        assertEquals(1, result.size());
        assertEquals("created", result.get(0).normalizedStatus());
        assertEquals("created", result.get(0).latestTransition().status());
        verify(gatewayAsyncResourceRepository).search(ArgumentMatchers.eq(1L), ArgumentMatchers.eq(GatewayAsyncResourceType.UPLOAD), ArgumentMatchers.eq("created"), ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void shouldReturnAsyncResourceDetail() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayAsyncResourceCanonicalizer canonicalizer = new GatewayAsyncResourceCanonicalizer(
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(GatewayFileRepository.class),
                new ObjectMapper()
        );
        AsyncResourceAdminService service = new AsyncResourceAdminService(gatewayAsyncResourceRepository, canonicalizer);

        GatewayAsyncResourceEntity entity = entity("upload_1", GatewayAsyncResourceType.UPLOAD, "in_progress");
        entity.setMetadataJson("""
                {"object_mode":"upstream_object_with_local_lineage","upstream_object_id":"upload-upstream-1","events":[{"type":"created","status":"queued","at":1713150000}]}
                """);
        entity.setRequestPayloadJson("{\"input_file_id\":\"file-upstream-1\"}");
        when(gatewayAsyncResourceRepository.findByResourceKeyAndDeletedFalse("upload_1")).thenReturn(Optional.of(entity));

        var detail = service.getAsyncResource("upload_1");

        assertEquals("in_progress", detail.lifecycle().normalizedStatus());
        assertEquals(1, detail.transitions().size());
        assertEquals("upstream_object_with_local_lineage", detail.lineage().objectMode());
        assertEquals("upload-upstream-1", detail.lineage().upstreamObjectId());
    }

    private GatewayAsyncResourceEntity entity(String resourceKey, GatewayAsyncResourceType type, String status) {
        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey(resourceKey);
        entity.setResourceType(type);
        entity.setStatus(status);
        entity.setRequestModel("model-x");
        entity.setRequestPayloadJson("{}");
        entity.setResponsePayloadJson("{}");
        entity.setMetadataJson("""
                {"object_mode":"gateway_response_object","events":[{"type":"created","status":"%s","at":1713150000}]}
                """.formatted(status));
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-04-15T10:00:00Z"));
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-04-15T10:05:00Z"));
        return entity;
    }
}
