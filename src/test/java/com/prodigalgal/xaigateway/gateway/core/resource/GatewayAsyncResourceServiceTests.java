package com.prodigalgal.xaigateway.gateway.core.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayFileBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayFileEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayAsyncResourceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class GatewayAsyncResourceServiceTests {

    @Test
    void shouldRewriteGatewayFileIdAndPersistUpstreamMetadataForBatch() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);

        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("""
                        {"id":"batch-upstream-1","object":"batch","status":"validating"}
                        """)
                .build());

        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                gatewayFileRepository,
                gatewayFileBindingRepository,
                credentialCryptoService,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder().exchangeFunction(exchangeFunction)
        );

        Mockito.when(distributedKeyQueryService.findActiveById(1L))
                .thenReturn(Optional.of(new DistributedKeyView(
                        1L,
                        "test",
                        "sk-gw-test",
                        "masked",
                        List.of("openai"),
                        List.of(),
                        List.of(new DistributedCredentialBindingView(1L, 101L, "openai", ProviderType.OPENAI_DIRECT, "https://api.openai.com", 10, 100))
                )));
        Mockito.when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(List.of(101L)))
                .thenReturn(List.of(credential()));
        Mockito.when(upstreamSiteProfileRepository.findById(1L)).thenReturn(Optional.of(siteProfile()));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(1L)).thenReturn(Optional.of(snapshot()));
        Mockito.when(credentialCryptoService.decrypt("cipher")).thenReturn("api-key");

        GatewayFileEntity file = new GatewayFileEntity();
        org.springframework.test.util.ReflectionTestUtils.setField(file, "id", 1L);
        file.setFileKey("file-local-1");
        file.setDistributedKeyId(1L);
        Mockito.when(gatewayFileRepository.findByFileKeyAndDeletedFalse("file-local-1")).thenReturn(Optional.of(file));

        GatewayFileBindingEntity binding = new GatewayFileBindingEntity();
        binding.setGatewayFileId(1L);
        binding.setExternalFileId("file-upstream-1");
        Mockito.when(gatewayFileBindingRepository.findAllByGatewayFileIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(binding));

        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put("input_file_id", "file-local-1");
        request.put("endpoint", "/v1/chat/completions");

        com.fasterxml.jackson.databind.JsonNode response = service.createBatch(1L, request);

        assertTrue(response.path("id").asText().startsWith("batch_"));
        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(gatewayAsyncResourceRepository).save(captor.capture());
        assertTrue(captor.getValue().getRequestPayloadJson().contains("file-upstream-1"));
        assertTrue(captor.getValue().getMetadataJson().contains("batch-upstream-1"));
    }

    @Test
    void shouldAddUploadPartUsingBoundUpstreamMetadata() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);

        java.util.concurrent.atomic.AtomicReference<String> requestedPath = new java.util.concurrent.atomic.AtomicReference<>();
        ExchangeFunction exchangeFunction = request -> {
            requestedPath.set(request.url().getPath());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                            {"id":"part-upstream-1","object":"upload.part"}
                            """)
                    .build());
        };

        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                gatewayFileRepository,
                gatewayFileBindingRepository,
                credentialCryptoService,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder().exchangeFunction(exchangeFunction)
        );

        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey("upload_1");
        entity.setDistributedKeyId(1L);
        entity.setResourceType(GatewayAsyncResourceType.UPLOAD);
        entity.setStatus("created");
        entity.setMetadataJson("""
                {"upstream_object_id":"upload-upstream-1","credential_id":101,"site_profile_id":1,"events":[]}
                """);
        Mockito.when(gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse("upload_1", GatewayAsyncResourceType.UPLOAD))
                .thenReturn(Optional.of(entity));
        Mockito.when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(credential()));
        Mockito.when(upstreamSiteProfileRepository.findById(1L)).thenReturn(Optional.of(siteProfile()));
        Mockito.when(credentialCryptoService.decrypt("cipher")).thenReturn("api-key");
        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FilePart filePart = Mockito.mock(FilePart.class);
        Mockito.when(filePart.filename()).thenReturn("part.bin");
        Mockito.when(filePart.headers()).thenReturn(new HttpHeaders() {{
            setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }});
        Mockito.when(filePart.content()).thenReturn(Flux.just(new DefaultDataBufferFactory().wrap("hello".getBytes())));

        com.fasterxml.jackson.databind.JsonNode response = service.addUploadPart("upload_1", 1L, filePart).block();

        assertEquals("/v1/uploads/upload-upstream-1/parts", requestedPath.get());
        assertEquals("part-upstream-1", response.path("id").asText());
        assertEquals("upload_1", response.path("upload_id").asText());
        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(gatewayAsyncResourceRepository).save(captor.capture());
        assertTrue(captor.getValue().getMetadataJson().contains("part-upstream-1"));
        Mockito.verifyNoInteractions(distributedKeyQueryService);
    }

    private UpstreamCredentialEntity credential() {
        UpstreamCredentialEntity entity = new UpstreamCredentialEntity();
        org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", 101L);
        entity.setProviderType(ProviderType.OPENAI_DIRECT);
        entity.setSiteProfileId(1L);
        entity.setBaseUrl("https://api.openai.com");
        entity.setApiKeyCiphertext("cipher");
        entity.setActive(true);
        return entity;
    }

    private UpstreamSiteProfileEntity siteProfile() {
        UpstreamSiteProfileEntity entity = new UpstreamSiteProfileEntity();
        org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", 1L);
        entity.setSiteKind(UpstreamSiteKind.OPENAI_DIRECT);
        entity.setAuthStrategy(AuthStrategy.BEARER);
        entity.setPathStrategy(PathStrategy.OPENAI_V1);
        entity.setErrorSchemaStrategy(ErrorSchemaStrategy.OPENAI_ERROR);
        entity.setActive(true);
        return entity;
    }

    private SiteCapabilitySnapshotEntity snapshot() {
        SiteCapabilitySnapshotEntity entity = new SiteCapabilitySnapshotEntity();
        entity.setSupportsBatches(true);
        entity.setAuthStrategy(AuthStrategy.BEARER);
        entity.setPathStrategy(PathStrategy.OPENAI_V1);
        entity.setErrorSchemaStrategy(ErrorSchemaStrategy.OPENAI_ERROR);
        entity.setHealthState("READY");
        entity.setSupportedProtocols(List.of("openai"));
        return entity;
    }
}
