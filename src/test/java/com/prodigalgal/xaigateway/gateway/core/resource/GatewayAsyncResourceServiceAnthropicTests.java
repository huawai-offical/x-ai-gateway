package com.prodigalgal.xaigateway.gateway.core.resource;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.batches.BatchCancelParams;
import com.anthropic.models.beta.messages.batches.BatchRetrieveParams;
import com.anthropic.models.beta.messages.batches.BetaMessageBatch;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayAsyncResourceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import com.prodigalgal.xaigateway.provider.adapter.anthropic.AnthropicChatModelFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

class GatewayAsyncResourceServiceAnthropicTests {

    @Test
    void shouldCreateAnthropicNativeMessageBatchWithLocalLineageMetadata() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        AnthropicChatModelFactory anthropicChatModelFactory = Mockito.mock(AnthropicChatModelFactory.class);

        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                Mockito.mock(GatewayFileRepository.class),
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                credentialMaterialResolver,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                anthropicChatModelFactory,
                Mockito.mock(com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory.class),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-16T05:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );

        UpstreamCredentialEntity credential = credential(601L, 6L, "https://api.anthropic.com");
        UpstreamSiteProfileEntity siteProfile = anthropicSiteProfile(6L);
        ResolvedCredentialMaterial material = resolvedMaterial(601L, 6L, "anthropic-key");

        Mockito.when(distributedKeyQueryService.findActiveById(1L))
                .thenReturn(Optional.of(distributedKey(601L, credential.getBaseUrl())));
        Mockito.when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(List.of(601L)))
                .thenReturn(List.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(6L)).thenReturn(Optional.of(siteProfile));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(6L)).thenReturn(Optional.of(snapshot()));
        Mockito.when(credentialMaterialResolver.resolveStored(credential)).thenReturn(material);
        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AnthropicClient client = Mockito.mock(AnthropicClient.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(anthropicChatModelFactory.createClient(credential.getBaseUrl(), material.secret())).thenReturn(client);
        BetaMessageBatch createdBatch = anthropicBatch("msgbatch_123", "in_progress", 0L, 0L, 0L, 2L, 0L);
        Mockito.when(client.beta().messages().batches().create(any())).thenReturn(createdBatch);

        JsonNode response = service.createAnthropicMessageBatch(1L, nativeBatchCreatePayload(), null);

        assertEquals("msgbatch_123", response.path("id").asText());
        assertEquals("message_batch", response.path("object").asText());
        assertEquals("running", response.path("status").asText());
        assertEquals("in_progress", response.path("processing_status").asText());

        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(gatewayAsyncResourceRepository).save(captor.capture());
        GatewayAsyncResourceEntity entity = captor.getValue();
        assertEquals("msgbatch_123", entity.getUpstreamObjectId());
        assertEquals("claude-sonnet-4", entity.getRequestModel());
        assertTrue(entity.getResourceKey().startsWith("batch_"));
        assertTrue(entity.getMetadataJson().contains("\"batch_protocol\":\"anthropic_native\""));
        assertTrue(entity.getMetadataJson().contains("\"object_mode\":\"upstream_object_with_local_lineage\""));
        assertTrue(entity.getMetadataJson().contains("\"upstream_object_id\":\"msgbatch_123\""));
        assertTrue(entity.getMetadataJson().contains("\"site_profile_id\":6"));
        assertTrue(entity.getResponsePayloadJson().contains("\"object\":\"message_batch\""));
    }

    @Test
    void shouldGetAndCancelAnthropicNativeMessageBatchUsingUpstreamObjectId() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        AnthropicChatModelFactory anthropicChatModelFactory = Mockito.mock(AnthropicChatModelFactory.class);
        AtomicReference<GatewayAsyncResourceEntity> stored = new AtomicReference<>();

        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                Mockito.mock(GatewayFileRepository.class),
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                credentialMaterialResolver,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                anthropicChatModelFactory,
                Mockito.mock(com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory.class),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-16T05:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );

        UpstreamCredentialEntity credential = credential(601L, 6L, "https://api.anthropic.com");
        UpstreamSiteProfileEntity siteProfile = anthropicSiteProfile(6L);
        ResolvedCredentialMaterial material = resolvedMaterial(601L, 6L, "anthropic-key");

        Mockito.when(distributedKeyQueryService.findActiveById(1L))
                .thenReturn(Optional.of(distributedKey(601L, credential.getBaseUrl())));
        Mockito.when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(List.of(601L)))
                .thenReturn(List.of(credential));
        Mockito.when(upstreamCredentialRepository.findById(601L)).thenReturn(Optional.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(6L)).thenReturn(Optional.of(siteProfile));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(6L)).thenReturn(Optional.of(snapshot()));
        Mockito.when(credentialMaterialResolver.resolveStored(credential)).thenReturn(material);
        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> {
            GatewayAsyncResourceEntity entity = invocation.getArgument(0);
            stored.set(entity);
            return entity;
        });
        Mockito.when(gatewayAsyncResourceRepository.findByDistributedKeyIdAndResourceTypeAndUpstreamObjectIdAndDeletedFalse(anyLong(), any(), anyString()))
                .thenAnswer(invocation -> {
                    GatewayAsyncResourceEntity entity = stored.get();
                    String upstreamObjectId = invocation.getArgument(2, String.class);
                    if (entity != null
                            && entity.getDistributedKeyId().equals(invocation.getArgument(0, Long.class))
                            && entity.getResourceType() == invocation.getArgument(1, GatewayAsyncResourceType.class)
                            && upstreamObjectId.equals(entity.getUpstreamObjectId())
                            && !entity.isDeleted()) {
                        return Optional.of(entity);
                    }
                    return Optional.empty();
                });

        AnthropicClient client = Mockito.mock(AnthropicClient.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(anthropicChatModelFactory.createClient(credential.getBaseUrl(), material.secret())).thenReturn(client);
        BetaMessageBatch createdBatch = anthropicBatch("msgbatch_123", "in_progress", 0L, 0L, 0L, 2L, 0L);
        BetaMessageBatch completedBatch = anthropicBatch("msgbatch_123", "ended", 2L, 0L, 0L, 0L, 0L);
        BetaMessageBatch cancelledBatch = anthropicBatch("msgbatch_123", "canceled", 0L, 0L, 2L, 0L, 0L);
        Mockito.when(client.beta().messages().batches().create(any())).thenReturn(createdBatch);
        Mockito.when(client.beta().messages().batches().retrieve(anyString(), any(BatchRetrieveParams.class))).thenReturn(completedBatch, cancelledBatch);
        Mockito.when(client.beta().messages().batches().cancel(anyString(), any(BatchCancelParams.class))).thenReturn(cancelledBatch);

        JsonNode createResponse = service.createAnthropicMessageBatch(1L, nativeBatchCreatePayload(), null);
        JsonNode getResponse = service.getAnthropicMessageBatch("msgbatch_123", 1L);
        JsonNode cancelResponse = service.cancelAnthropicMessageBatch("msgbatch_123", 1L);

        assertEquals("msgbatch_123", createResponse.path("id").asText());
        assertEquals("completed", getResponse.path("status").asText());
        assertEquals("ended", getResponse.path("processing_status").asText());
        assertEquals("cancelled", cancelResponse.path("status").asText());
        assertEquals("canceled", cancelResponse.path("processing_status").asText());

        GatewayAsyncResourceEntity entity = stored.get();
        assertEquals("msgbatch_123", entity.getUpstreamObjectId());
        assertEquals("cancelled", entity.getStatus());
        assertTrue(entity.getMetadataJson().contains("\"batch_protocol\":\"anthropic_native\""));
        assertTrue(entity.getMetadataJson().contains("\"upstream_object_id\":\"msgbatch_123\""));
        assertTrue(entity.getMetadataJson().contains("\"type\":\"synced\""));
        assertTrue(entity.getResponsePayloadJson().contains("\"status\":\"cancelled\""));
        Mockito.verify(client.beta().messages().batches()).cancel(anyString(), any(BatchCancelParams.class));
    }

    private ObjectNode nativeBatchCreatePayload() {
        ObjectMapper mapper = new ObjectMapper();
        return (ObjectNode) mapper.valueToTree(Map.of(
                "requests", List.of(Map.of(
                        "custom_id", "req-1",
                        "params", Map.of(
                                "model", "claude-sonnet-4",
                                "max_tokens", 256,
                                "messages", List.of(Map.of("role", "user", "content", "hello"))
                        )
                ))
        ));
    }

    private BetaMessageBatch anthropicBatch(
            String id,
            String processingStatus,
            long succeeded,
            long errored,
            long canceled,
            long processing,
            long expired) {
        BetaMessageBatch batch = Mockito.mock(BetaMessageBatch.class, Mockito.RETURNS_DEEP_STUBS);
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-04-16T05:00:00Z");
        OffsetDateTime expiresAt = OffsetDateTime.parse("2026-04-17T05:00:00Z");
        Mockito.when(batch.id()).thenReturn(id);
        Mockito.when(batch.processingStatus().toString()).thenReturn(processingStatus);
        Mockito.when(batch.createdAt()).thenReturn(createdAt);
        Mockito.when(batch.expiresAt()).thenReturn(expiresAt);
        Mockito.when(batch.endedAt()).thenReturn(Optional.empty());
        Mockito.when(batch.cancelInitiatedAt()).thenReturn(Optional.empty());
        Mockito.when(batch.archivedAt()).thenReturn(Optional.empty());
        Mockito.when(batch.resultsUrl()).thenReturn(Optional.empty());
        Mockito.when(batch.requestCounts().succeeded()).thenReturn(succeeded);
        Mockito.when(batch.requestCounts().errored()).thenReturn(errored);
        Mockito.when(batch.requestCounts().canceled()).thenReturn(canceled);
        Mockito.when(batch.requestCounts().processing()).thenReturn(processing);
        Mockito.when(batch.requestCounts().expired()).thenReturn(expired);
        return batch;
    }

    private DistributedKeyView distributedKey(Long credentialId, String baseUrl) {
        return new DistributedKeyView(
                1L,
                "test",
                "sk-gw-test",
                "masked",
                List.of(ProviderType.ANTHROPIC_DIRECT.name().toLowerCase()),
                List.of(),
                List.of(new DistributedCredentialBindingView(
                        1L,
                        credentialId,
                        ProviderType.ANTHROPIC_DIRECT.name().toLowerCase(),
                        ProviderType.ANTHROPIC_DIRECT,
                        baseUrl,
                        10,
                        100
                ))
        );
    }

    private UpstreamCredentialEntity credential(Long id, Long siteProfileId, String baseUrl) {
        UpstreamCredentialEntity entity = new UpstreamCredentialEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setProviderType(ProviderType.ANTHROPIC_DIRECT);
        entity.setSiteProfileId(siteProfileId);
        entity.setBaseUrl(baseUrl);
        entity.setApiKeyCiphertext("cipher");
        entity.setActive(true);
        return entity;
    }

    private UpstreamSiteProfileEntity anthropicSiteProfile(Long id) {
        UpstreamSiteProfileEntity entity = new UpstreamSiteProfileEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setSiteKind(UpstreamSiteKind.ANTHROPIC_DIRECT);
        entity.setAuthStrategy(AuthStrategy.API_KEY_HEADER);
        entity.setPathStrategy(PathStrategy.ANTHROPIC_V1_MESSAGES);
        entity.setErrorSchemaStrategy(ErrorSchemaStrategy.ANTHROPIC_ERROR);
        entity.setActive(true);
        return entity;
    }

    private SiteCapabilitySnapshotEntity snapshot() {
        SiteCapabilitySnapshotEntity entity = new SiteCapabilitySnapshotEntity();
        entity.setSupportedProtocols(List.of("anthropic_native"));
        entity.setSupportsFiles(true);
        entity.setSupportsBatches(true);
        entity.setAuthStrategy(AuthStrategy.API_KEY_HEADER);
        entity.setPathStrategy(PathStrategy.ANTHROPIC_V1_MESSAGES);
        entity.setErrorSchemaStrategy(ErrorSchemaStrategy.ANTHROPIC_ERROR);
        entity.setHealthState("READY");
        return entity;
    }

    private ResolvedCredentialMaterial resolvedMaterial(Long credentialId, Long siteProfileId, String secret) {
        return new ResolvedCredentialMaterial(credentialId, siteProfileId, null, secret, "fp", Map.of(), null, "credential");
    }
}
