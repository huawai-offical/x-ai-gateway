package com.prodigalgal.xaigateway.gateway.core.resource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResponse;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class GatewayAsyncResourceServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateAndCancelLocalVideoTask() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                Mockito.mock(DistributedKeyQueryService.class),
                Mockito.mock(UpstreamCredentialRepository.class),
                Mockito.mock(UpstreamSiteProfileRepository.class),
                snapshotRepository,
                Mockito.mock(GatewayFileRepository.class),
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                objectMapper,
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );
        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", "veo-3");
        request.put("prompt", "demo");

        JsonNode created = service.createVideoTask(1L, request);

        assertTrue(created.path("id").asText().startsWith("video_"));
        assertEquals("video.generation", created.path("object").asText());
        assertEquals("queued", created.path("status").asText());
        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(gatewayAsyncResourceRepository).save(captor.capture());
        GatewayAsyncResourceEntity entity = captor.getValue();
        assertEquals(GatewayAsyncResourceType.VIDEO, entity.getResourceType());
        assertTrue(entity.getMetadataJson().contains("gateway_local_async_task"));

        Mockito.when(gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                        created.path("id").asText(),
                        GatewayAsyncResourceType.VIDEO))
                .thenReturn(Optional.of(entity));

        JsonNode cancelled = service.cancelVideoTask(created.path("id").asText(), 1L);

        assertEquals("cancelled", cancelled.path("status").asText());
        assertEquals("cancelled", entity.getStatus());
        assertTrue(entity.getMetadataJson().contains("user_cancelled"));
        Mockito.verify(gatewayAsyncResourceRepository, Mockito.times(2)).save(any());
    }

    @Test
    void shouldKeepFailedLocalMusicTaskTerminalWhenCancelled() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                Mockito.mock(DistributedKeyQueryService.class),
                Mockito.mock(UpstreamCredentialRepository.class),
                Mockito.mock(UpstreamSiteProfileRepository.class),
                snapshotRepository,
                Mockito.mock(GatewayFileRepository.class),
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                objectMapper,
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );
        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", "music-demo");
        request.put("prompt", "demo");
        request.put("status", "failed");

        JsonNode created = service.createMusicTask(1L, request);
        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(gatewayAsyncResourceRepository).save(captor.capture());
        GatewayAsyncResourceEntity entity = captor.getValue();
        Mockito.when(gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                        created.path("id").asText(),
                        GatewayAsyncResourceType.MUSIC))
                .thenReturn(Optional.of(entity));

        JsonNode cancelled = service.cancelMusicTask(created.path("id").asText(), 1L);

        assertEquals("failed", cancelled.path("status").asText());
        assertEquals("failed", entity.getStatus());
        Mockito.verify(gatewayAsyncResourceRepository, Mockito.times(1)).save(any());
    }

    @Test
    void shouldCreateSyncAndCancelUpstreamVideoAndMusicTasks() throws Exception {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        java.util.List<String> requestedPaths = new java.util.ArrayList<>();
        ExchangeFunction exchangeFunction = request -> {
            String path = request.url().getPath();
            requestedPaths.add(request.method() + " " + path);
            String body;
            if (path.endsWith("/cancel")) {
                body = "{\"id\":\"upstream-cancelled\",\"status\":\"cancelled\"}";
            } else if (path.contains("/vid-upstream-1")) {
                body = "{\"id\":\"vid-upstream-1\",\"object\":\"video.generation\",\"status\":\"completed\",\"output_url\":\"https://cdn.example/video.mp4\"}";
            } else if (path.contains("/music-upstream-1")) {
                body = "{\"id\":\"music-upstream-1\",\"object\":\"music.generation\",\"status\":\"completed\",\"output_url\":\"https://cdn.example/music.mp3\"}";
            } else if (path.contains("/videos/generations")) {
                body = "{\"id\":\"vid-upstream-1\",\"object\":\"video.generation\",\"status\":\"queued\"}";
            } else {
                body = "{\"id\":\"music-upstream-1\",\"object\":\"music.generation\",\"status\":\"queued\"}";
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build());
        };

        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                Mockito.mock(GatewayFileRepository.class),
                Mockito.mock(GatewayFileBindingRepository.class),
                credentialCryptoService,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                objectMapper,
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder().exchangeFunction(exchangeFunction)
        );
        Mockito.when(distributedKeyQueryService.findActiveById(1L))
                .thenReturn(Optional.of(distributedKey(ProviderType.OPENAI_DIRECT, 101L, "https://api.openai.com")));
        Mockito.when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(List.of(101L)))
                .thenReturn(List.of(credential(101L, ProviderType.OPENAI_DIRECT, 1L, "https://api.openai.com")));
        Mockito.when(upstreamCredentialRepository.findById(101L))
                .thenReturn(Optional.of(credential(101L, ProviderType.OPENAI_DIRECT, 1L, "https://api.openai.com")));
        Mockito.when(upstreamSiteProfileRepository.findById(1L)).thenReturn(Optional.of(siteProfile(1L, UpstreamSiteKind.OPENAI_DIRECT)));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(1L))
                .thenReturn(Optional.of(snapshot(false, false, AuthStrategy.BEARER, PathStrategy.OPENAI_V1)));
        Mockito.when(credentialCryptoService.decrypt("cipher")).thenReturn("api-key");
        java.util.List<GatewayAsyncResourceEntity> savedEntities = new java.util.ArrayList<>();
        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> {
            GatewayAsyncResourceEntity entity = invocation.getArgument(0);
            savedEntities.add(entity);
            return entity;
        });

        JsonNode video = service.createVideoTask(1L, objectMapper.readTree("""
                {"model":"veo-compatible","prompt":"demo","provider_mode":"upstream"}
                """));
        GatewayAsyncResourceEntity videoEntity = savedEntities.get(savedEntities.size() - 1);
        Mockito.when(gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse(video.path("id").asText(), GatewayAsyncResourceType.VIDEO))
                .thenReturn(Optional.of(videoEntity));

        JsonNode syncedVideo = service.getVideoTask(video.path("id").asText(), 1L);
        JsonNode cancelledVideo = service.cancelVideoTask(video.path("id").asText(), 1L);

        JsonNode music = service.createMusicTask(1L, objectMapper.readTree("""
                {"model":"music-compatible","prompt":"demo","preferred_credential_id":101}
                """));
        GatewayAsyncResourceEntity musicEntity = savedEntities.get(savedEntities.size() - 1);
        Mockito.when(gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse(music.path("id").asText(), GatewayAsyncResourceType.MUSIC))
                .thenReturn(Optional.of(musicEntity));
        JsonNode syncedMusic = service.getMusicTask(music.path("id").asText(), 1L);

        assertTrue(video.path("id").asText().startsWith("video_"));
        assertEquals("completed", syncedVideo.path("status").asText());
        assertEquals("cancelled", cancelledVideo.path("status").asText());
        assertTrue(music.path("id").asText().startsWith("music_"));
        assertEquals("completed", syncedMusic.path("status").asText());
        assertTrue(videoEntity.getMetadataJson().contains("vid-upstream-1"));
        assertTrue(musicEntity.getMetadataJson().contains("music-upstream-1"));
        assertTrue(videoEntity.getMetadataJson().contains("\"provider_support_tier\":\"native_openai_style\""));
        assertTrue(musicEntity.getMetadataJson().contains("\"provider_support_status\":\"SUPPORTED\""));
        assertTrue(videoEntity.getMetadataJson().contains("\"provider_smoke_hint\""));
        assertTrue(requestedPaths.contains("POST /v1/videos/generations"));
        assertTrue(requestedPaths.contains("GET /v1/videos/generations/vid-upstream-1"));
        assertTrue(requestedPaths.contains("POST /v1/videos/generations/vid-upstream-1/cancel"));
        assertTrue(requestedPaths.contains("POST /v1/music/generations"));
        assertTrue(requestedPaths.contains("GET /v1/music/generations/music-upstream-1"));
    }

    @Test
    void shouldExposeMediaProviderSupportMatrix() {
        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                Mockito.mock(GatewayAsyncResourceRepository.class),
                Mockito.mock(DistributedKeyQueryService.class),
                Mockito.mock(UpstreamCredentialRepository.class),
                Mockito.mock(UpstreamSiteProfileRepository.class),
                Mockito.mock(SiteCapabilitySnapshotRepository.class),
                Mockito.mock(GatewayFileRepository.class),
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), Mockito.mock(SiteCapabilitySnapshotRepository.class)),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );

        JsonNode matrix = service.mediaProviderMatrix();

        assertEquals("gateway.media_provider_matrix", matrix.path("object").asText());
        assertTrue(matrix.path("video").toString().contains("openai_compatible"));
        assertTrue(matrix.path("video").toString().contains("Gemini"));
        assertTrue(matrix.path("video").toString().contains("provider_specific_adapter"));
        assertTrue(matrix.path("music").toString().contains("suno"));
        assertTrue(matrix.path("music").toString().contains("music_generation"));
        assertTrue(matrix.path("music").toString().contains("operator_configured_suno_music_pricing"));
        assertTrue(matrix.path("music").toString().contains("XAG_SMOKE_SUNO"));
        assertTrue(matrix.path("music").toString().contains("NOT_SUPPORTED"));
    }

    @Test
    void shouldRunGeminiVeoProviderAdapterLifecycle() throws Exception {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                Mockito.mock(DistributedKeyQueryService.class),
                Mockito.mock(UpstreamCredentialRepository.class),
                Mockito.mock(UpstreamSiteProfileRepository.class),
                snapshotRepository,
                Mockito.mock(GatewayFileRepository.class),
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                objectMapper,
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );
        java.util.List<GatewayAsyncResourceEntity> savedEntities = new java.util.ArrayList<>();
        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> {
            GatewayAsyncResourceEntity entity = invocation.getArgument(0);
            savedEntities.add(entity);
            return entity;
        });

        JsonNode created = service.createVideoTask(1L, objectMapper.readTree("""
                {"model":"veo-3","prompt":"demo","provider_mode":"adapter","provider_family":"gemini"}
                """));
        GatewayAsyncResourceEntity entity = savedEntities.get(savedEntities.size() - 1);
        Mockito.when(gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                        created.path("id").asText(),
                        GatewayAsyncResourceType.VIDEO))
                .thenReturn(Optional.of(entity));

        JsonNode synced = service.getVideoTask(created.path("id").asText(), 1L);
        JsonNode download = service.downloadVideoTaskArtifact(created.path("id").asText(), 1L);

        JsonNode cancellable = service.createVideoTask(1L, objectMapper.readTree("""
                {"model":"veo-3","prompt":"demo","provider_mode":"adapter","provider_family":"gemini"}
                """));
        GatewayAsyncResourceEntity cancellableEntity = savedEntities.get(savedEntities.size() - 1);
        Mockito.when(gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                        cancellable.path("id").asText(),
                        GatewayAsyncResourceType.VIDEO))
                .thenReturn(Optional.of(cancellableEntity));
        JsonNode cancelled = service.cancelVideoTask(cancellable.path("id").asText(), 1L);

        assertEquals("queued", created.path("status").asText());
        assertEquals("completed", synced.path("status").asText());
        assertEquals("media.artifact_download", download.path("object").asText());
        assertTrue(download.path("download_url").asText().contains("/videos/" + created.path("id").asText() + "/download"));
        assertEquals("cancelled", cancelled.path("status").asText());
        assertTrue(entity.getMetadataJson().contains("\"object_mode\":\"provider_specific_media_adapter\""));
        assertTrue(entity.getMetadataJson().contains("\"provider_adapter\":\"gemini_veo\""));
        assertTrue(entity.getMetadataJson().contains("\"downloaded\""));
        assertTrue(cancellableEntity.getMetadataJson().contains("\"cancel_reason\":\"user_cancelled\""));
    }

    @Test
    void shouldRunSunoMusicProviderAdapterLifecycle() throws Exception {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                Mockito.mock(DistributedKeyQueryService.class),
                Mockito.mock(UpstreamCredentialRepository.class),
                Mockito.mock(UpstreamSiteProfileRepository.class),
                snapshotRepository,
                Mockito.mock(GatewayFileRepository.class),
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                objectMapper,
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );
        java.util.List<GatewayAsyncResourceEntity> savedEntities = new java.util.ArrayList<>();
        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> {
            GatewayAsyncResourceEntity entity = invocation.getArgument(0);
            savedEntities.add(entity);
            return entity;
        });

        JsonNode created = service.createMusicTask(1L, objectMapper.readTree("""
                {
                  "model":"suno_music",
                  "prompt":"a calm synth theme",
                  "title":"Smoke Song",
                  "tags":"ambient,synth",
                  "provider_mode":"adapter",
                  "provider_family":"suno"
                }
                """));
        GatewayAsyncResourceEntity entity = savedEntities.get(savedEntities.size() - 1);
        Mockito.when(gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                        created.path("id").asText(),
                        GatewayAsyncResourceType.MUSIC))
                .thenReturn(Optional.of(entity));

        JsonNode synced = service.getMusicTask(created.path("id").asText(), 1L);
        JsonNode download = service.downloadMusicTaskArtifact(created.path("id").asText(), 1L);

        JsonNode cancellable = service.createMusicTask(1L, objectMapper.readTree("""
                {"model":"suno_music","prompt":"demo","provider_mode":"adapter","provider_family":"suno"}
                """));
        GatewayAsyncResourceEntity cancellableEntity = savedEntities.get(savedEntities.size() - 1);
        Mockito.when(gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                        cancellable.path("id").asText(),
                        GatewayAsyncResourceType.MUSIC))
                .thenReturn(Optional.of(cancellableEntity));
        JsonNode cancelled = service.cancelMusicTask(cancellable.path("id").asText(), 1L);

        assertEquals("queued", created.path("status").asText());
        assertEquals("submitted", created.path("provider_status").asText());
        assertEquals("completed", synced.path("status").asText());
        assertEquals("success", synced.path("provider_status").asText());
        assertEquals("media.artifact_download", download.path("object").asText());
        assertEquals("audio/mpeg", download.path("content_type").asText());
        assertTrue(download.path("download_url").asText().contains("/music/" + created.path("id").asText() + "/download"));
        assertEquals("cancelled", cancelled.path("status").asText());
        assertTrue(entity.getMetadataJson().contains("\"provider_adapter\":\"suno_music\""));
        assertTrue(entity.getMetadataJson().contains("\"provider_capability\":\"music_generation\""));
        assertTrue(entity.getMetadataJson().contains("\"provider_pricing_source\":\"operator_configured_suno_music_pricing\""));
        assertTrue(entity.getMetadataJson().contains("\"AUTHENTICATION_FAILED\""));
        assertTrue(entity.getMetadataJson().contains("\"PROVIDER_RATE_LIMITED\""));
        assertTrue(entity.getMetadataJson().contains("\"downloaded\""));
        assertTrue(cancellableEntity.getMetadataJson().contains("\"cancel_reason\":\"user_cancelled\""));
    }

    @Test
    void shouldStoreGatewayResponseObjectMetadataForLifecycleProjection() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                Mockito.mock(DistributedKeyQueryService.class),
                Mockito.mock(UpstreamCredentialRepository.class),
                Mockito.mock(UpstreamSiteProfileRepository.class),
                snapshotRepository,
                Mockito.mock(GatewayFileRepository.class),
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );

        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GatewayAsyncResourceEntity storedEntity = new GatewayAsyncResourceEntity();
        storedEntity.setResourceKey("resp_test");
        storedEntity.setDistributedKeyId(1L);
        storedEntity.setResourceType(GatewayAsyncResourceType.RESPONSE);
        storedEntity.setStatus("completed");
        storedEntity.setResponsePayloadJson("{\"id\":\"resp_test\",\"object\":\"response\",\"status\":\"completed\"}");
        storedEntity.setMetadataJson("{\"object_mode\":\"gateway_response_object\",\"events\":[{\"type\":\"stored\",\"status\":\"completed\",\"at\":1712894400}]}");
        Mockito.when(gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse("resp_test", GatewayAsyncResourceType.RESPONSE))
                .thenReturn(Optional.of(storedEntity));

        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put("model", "gpt-4o");
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("id", "resp-upstream");
        response.put("status", "completed");

        service.storeResponse(1L, "gpt-4o", request, response);
        service.deleteResponse("resp_test", 1L);

        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(gatewayAsyncResourceRepository, Mockito.atLeast(2)).save(captor.capture());
        assertTrue(captor.getAllValues().get(0).getMetadataJson().contains("gateway_response_object"));
        assertTrue(captor.getAllValues().get(captor.getAllValues().size() - 1).getMetadataJson().contains("\"type\":\"deleted\""));
    }

    @Test
    void shouldPersistOpenAiResponseLineageWhenStoredNativeResponseUsesOpenAiDirect() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                Mockito.mock(DistributedKeyQueryService.class),
                Mockito.mock(UpstreamCredentialRepository.class),
                Mockito.mock(UpstreamSiteProfileRepository.class),
                snapshotRepository,
                Mockito.mock(GatewayFileRepository.class),
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                objectMapper,
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );
        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", "writer-fast");
        request.put("store", true);
        ObjectNode upstreamResponse = objectMapper.createObjectNode();
        upstreamResponse.put("id", "resp_upstream_1");
        upstreamResponse.put("object", "response");
        upstreamResponse.put("model", "gpt-4.1");
        upstreamResponse.put("status", "in_progress");

        JsonNode stored = service.storeResponse(
                1L,
                "writer-fast",
                request,
                upstreamResponse,
                openAiDirectResponseSelection()
        );

        assertTrue(stored.path("id").asText().startsWith("resp_"));
        assertFalse("resp_upstream_1".equals(stored.path("id").asText()));
        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(gatewayAsyncResourceRepository).save(captor.capture());
        GatewayAsyncResourceEntity entity = captor.getValue();
        assertEquals("resp_upstream_1", entity.getUpstreamObjectId());
        assertTrue(entity.getMetadataJson().contains("\"object_mode\":\"upstream_response_with_local_lineage\""));
        assertTrue(entity.getMetadataJson().contains("\"upstream_object_id\":\"resp_upstream_1\""));
        assertTrue(entity.getMetadataJson().contains("\"credential_id\":101"));
        assertTrue(entity.getMetadataJson().contains("\"site_profile_id\":1"));
        assertTrue(entity.getMetadataJson().contains("\"resolved_model_key\":\"gpt-4.1\""));
    }

    @Test
    void shouldPassthroughRemoteResponseLifecycleUsingStoredLineage() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        List<String> calls = new ArrayList<>();
        ExchangeFunction exchangeFunction = request -> {
            String query = request.url().getRawQuery();
            calls.add(request.method().name() + " " + request.url().getPath() + (query == null ? "" : "?" + query));
            assertEquals("Bearer api-key", request.headers().getFirst(HttpHeaders.AUTHORIZATION));
            String path = request.url().getPath();
            String method = request.method().name();
            String body = switch (method + " " + path) {
                case "GET /v1/responses/resp_upstream_1" -> """
                        {"id":"resp_upstream_1","object":"response","status":"completed","model":"gpt-4.1"}
                        """;
                case "POST /v1/responses/resp_upstream_1/cancel" -> """
                        {"id":"resp_upstream_1","object":"response","status":"cancelled","model":"gpt-4.1"}
                        """;
                case "GET /v1/responses/resp_upstream_1/input_items" -> """
                        {"object":"list","data":[{"id":"msg_item_1","type":"message","role":"user"}],"has_more":false,"first_id":"msg_item_1","last_id":"msg_item_1"}
                        """;
                case "DELETE /v1/responses/resp_upstream_1" -> """
                        {"id":"resp_upstream_1","object":"response","deleted":true}
                        """;
                default -> throw new AssertionError("Unexpected upstream call: " + method + " " + path);
            };
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build());
        };
        ObjectMapper objectMapper = new ObjectMapper();
        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                Mockito.mock(DistributedKeyQueryService.class),
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                Mockito.mock(GatewayFileRepository.class),
                Mockito.mock(GatewayFileBindingRepository.class),
                credentialCryptoService,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                objectMapper,
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder().exchangeFunction(exchangeFunction)
        );
        GatewayAsyncResourceEntity entity = upstreamResponseEntity();
        Mockito.when(gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse("resp_local_1", GatewayAsyncResourceType.RESPONSE))
                .thenReturn(Optional.of(entity));
        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(upstreamCredentialRepository.findById(101L))
                .thenReturn(Optional.of(credential(101L, ProviderType.OPENAI_DIRECT, 1L, "https://api.openai.com")));
        Mockito.when(upstreamSiteProfileRepository.findById(1L))
                .thenReturn(Optional.of(siteProfile(1L, UpstreamSiteKind.OPENAI_DIRECT)));
        Mockito.when(credentialCryptoService.decrypt("cipher")).thenReturn("api-key");

        JsonNode retrieved = service.getResponse("resp_local_1", 1L, List.of("reasoning.encrypted_content"));
        JsonNode cancelled = service.cancelResponse("resp_local_1", 1L);
        JsonNode inputItems = service.listResponseInputItems(
                "resp_local_1",
                1L,
                "msg_after",
                List.of("message.input_image.image_url", "file_search_call.results"),
                50,
                "asc"
        );
        JsonNode deleted = service.deleteResponse("resp_local_1", 1L);

        assertEquals("resp_local_1", retrieved.path("id").asText());
        assertEquals("completed", retrieved.path("status").asText());
        assertEquals("resp_local_1", cancelled.path("id").asText());
        assertEquals("cancelled", cancelled.path("status").asText());
        assertEquals("msg_item_1", inputItems.path("data").get(0).path("id").asText());
        assertEquals("resp_local_1", deleted.path("id").asText());
        assertTrue(deleted.path("deleted").asBoolean());
        assertTrue(entity.isDeleted());
        assertTrue(calls.contains("GET /v1/responses/resp_upstream_1?include=reasoning.encrypted_content"));
        assertTrue(calls.contains("POST /v1/responses/resp_upstream_1/cancel"));
        assertTrue(calls.contains("GET /v1/responses/resp_upstream_1/input_items?after=msg_after&include=message.input_image.image_url&include=file_search_call.results&limit=50&order=asc"));
        assertTrue(calls.contains("DELETE /v1/responses/resp_upstream_1"));
    }

    @Test
    void shouldCreateAndManageGeminiLocalUploadWithoutUpstreamObjectId() throws Exception {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        GatewayFileService gatewayFileService = Mockito.mock(GatewayFileService.class);

        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                gatewayFileRepository,
                gatewayFileBindingRepository,
                gatewayFileService,
                credentialCryptoService,
                credentialMaterialResolver,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );

        UpstreamCredentialEntity credential = credential(201L, ProviderType.GEMINI_DIRECT, 2L, "https://generativelanguage.googleapis.com");
        UpstreamSiteProfileEntity siteProfile = geminiSiteProfile(2L);
        SiteCapabilitySnapshotEntity snapshot = snapshot(
                true,
                true,
                AuthStrategy.API_KEY_QUERY,
                PathStrategy.GEMINI_V1BETA_MODELS
        );
        ResolvedCredentialMaterial material = resolvedMaterial(201L, 2L, "api-key");
        java.util.concurrent.atomic.AtomicReference<GatewayAsyncResourceEntity> stored = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<GatewayFileEntity> savedFile = new java.util.concurrent.atomic.AtomicReference<>();

        Mockito.when(distributedKeyQueryService.findActiveById(1L))
                .thenReturn(Optional.of(distributedKey(ProviderType.GEMINI_DIRECT, 201L, credential.getBaseUrl())));
        Mockito.when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(List.of(201L)))
                .thenReturn(List.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(2L)).thenReturn(Optional.of(siteProfile));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(2L)).thenReturn(Optional.of(snapshot));
        Mockito.when(credentialMaterialResolver.resolveStored(credential)).thenReturn(material);
        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> {
            GatewayAsyncResourceEntity entity = invocation.getArgument(0);
            stored.set(entity);
            return entity;
        });
        Mockito.when(gatewayFileRepository.save(any())).thenAnswer(invocation -> {
            GatewayFileEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-04-12T04:00:00Z"));
            savedFile.set(entity);
            return entity;
        });
        Mockito.when(gatewayFileService.ensureStorageDirectoryForSync()).thenReturn(tempDir);
        Mockito.when(gatewayFileService.createFileFromBytes(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(byte[].class),
                Mockito.any()
        )).thenAnswer(invocation -> {
            Long distributedKeyId = invocation.getArgument(0);
            String filename = invocation.getArgument(1);
            String mimeType = invocation.getArgument(2);
            String purpose = invocation.getArgument(3);
            byte[] bytes = invocation.getArgument(4);
            String fileKey = "file-local-upload-output";
            Path storagePath = tempDir.resolve(fileKey + "-" + filename);
            Files.write(storagePath, bytes);
            GatewayFileEntity entity = new GatewayFileEntity();
            entity.setFileKey(fileKey);
            entity.setDistributedKeyId(distributedKeyId);
            entity.setFilename(filename);
            entity.setMimeType(mimeType);
            entity.setPurpose(purpose);
            entity.setSizeBytes(bytes.length);
            entity.setStoragePath(storagePath.toAbsolutePath().toString());
            entity.setStatus("staged_local");
            GatewayFileEntity saved = gatewayFileRepository.save(entity);
            return GatewayFileResponse.from(
                    saved.getFileKey(),
                    saved.getFilename(),
                    saved.getPurpose(),
                    saved.getSizeBytes(),
                    saved.getCreatedAt(),
                    saved.getStatus()
            );
        });
        Mockito.when(gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse(any(), any()))
                .thenAnswer(invocation -> {
                    String resourceKey = invocation.getArgument(0);
                    GatewayAsyncResourceType resourceType = invocation.getArgument(1);
                    GatewayAsyncResourceEntity entity = stored.get();
                    if (entity == null || !resourceKey.equals(entity.getResourceKey()) || resourceType != entity.getResourceType()) {
                        return Optional.empty();
                    }
                    return Optional.of(entity);
                });

        ObjectMapper mapper = new ObjectMapper();
        JsonNode createResponse = service.createUpload(1L, mapper.readTree("""
                {
                  "filename":"conversation-input.jsonl",
                  "bytes":5,
                  "purpose":"assistants"
                }
                """), 201L);

        String uploadId = createResponse.path("id").asText();
        assertEquals("upload", createResponse.path("object").asText());
        assertEquals("created", createResponse.path("status").asText());
        assertEquals("conversation-input.jsonl", createResponse.path("filename").asText());

        GatewayAsyncResourceEntity createdEntity = stored.get();
        assertTrue(createdEntity.getMetadataJson().contains("\"object_mode\":\"gateway_upload_object\""));
        assertTrue(createdEntity.getMetadataJson().contains("\"credential_id\":201"));
        assertTrue(createdEntity.getMetadataJson().contains("\"site_profile_id\":2"));
        assertTrue(createdEntity.getMetadataJson().contains("\"partsCount\":0"));
        assertTrue(createdEntity.getMetadataJson().contains("\"part_bindings\":[]"));
        assertTrue(createdEntity.getMetadataJson().contains("\"parts\":[]"));
        assertTrue(!createdEntity.getMetadataJson().contains("upstream_object_id"));

        JsonNode getResponse = service.getUpload(uploadId, 1L);
        assertEquals(uploadId, getResponse.path("id").asText());
        assertEquals("created", getResponse.path("status").asText());

        FilePart filePart = Mockito.mock(FilePart.class);
        Mockito.when(filePart.filename()).thenReturn("part.bin");
        Mockito.when(filePart.headers()).thenReturn(new HttpHeaders() {{
            setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }});
        Mockito.when(filePart.content()).thenReturn(Flux.just(new DefaultDataBufferFactory().wrap("hello".getBytes(StandardCharsets.UTF_8))));

        JsonNode partResponse = service.addUploadPart(uploadId, 1L, filePart).block();
        assertEquals("upload.part", partResponse.path("object").asText());
        assertEquals(uploadId, partResponse.path("upload_id").asText());
        assertTrue(stored.get().getMetadataJson().contains("\"object_mode\":\"gateway_upload_object\""));
        assertTrue(stored.get().getMetadataJson().contains("\"partsCount\":1"));
        String partStoragePath = new ObjectMapper()
                .readTree(stored.get().getMetadataJson())
                .path("part_bindings")
                .get(0)
                .path("storage_path")
                .asText();
        assertTrue(Files.exists(Path.of(partStoragePath)));

        JsonNode completed = service.completeUpload(uploadId, 1L);
        assertEquals("completed", completed.path("status").asText());
        assertEquals(1, completed.path("parts_count").asInt());
        assertEquals(5, completed.path("bytes_received").asLong());
        assertEquals(savedFile.get().getFileKey(), completed.path("file_id").asText());
        assertEquals("conversation-input.jsonl", savedFile.get().getFilename());
        assertEquals("assistants", savedFile.get().getPurpose());
        assertEquals(5L, savedFile.get().getSizeBytes());
        assertEquals("staged_local", savedFile.get().getStatus());
        assertEquals("hello", Files.readString(Path.of(savedFile.get().getStoragePath())));
        assertTrue(stored.get().getMetadataJson().contains("\"produced_file_key\":\"" + savedFile.get().getFileKey() + "\""));
        assertTrue(stored.get().getMetadataJson().contains("\"produced_file_bytes\":5"));
        assertTrue(stored.get().getMetadataJson().contains("\"part_files_cleaned\":true"));
        assertTrue(!Files.exists(Path.of(partStoragePath)));
        JsonNode completedAgain = service.completeUpload(uploadId, 1L);
        assertEquals(completed.path("file_id").asText(), completedAgain.path("file_id").asText());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.cancelUpload(uploadId, 1L));
        assertEquals("已完成的 Upload 不允许取消。", error.getMessage());
    }

    @Test
    void shouldRejectCompletingCancelledLocalUpload() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                Mockito.mock(DistributedKeyQueryService.class),
                Mockito.mock(UpstreamCredentialRepository.class),
                Mockito.mock(UpstreamSiteProfileRepository.class),
                Mockito.mock(SiteCapabilitySnapshotRepository.class),
                Mockito.mock(GatewayFileRepository.class),
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), Mockito.mock(SiteCapabilitySnapshotRepository.class)),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );

        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey("upload_cancelled_local");
        entity.setDistributedKeyId(1L);
        entity.setResourceType(GatewayAsyncResourceType.UPLOAD);
        entity.setStatus("cancelled");
        entity.setMetadataJson("{\"object_mode\":\"gateway_upload_object\",\"events\":[]}");
        entity.setResponsePayloadJson("{\"id\":\"upload_cancelled_local\",\"object\":\"upload\",\"status\":\"cancelled\"}");
        Mockito.when(gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse("upload_cancelled_local", GatewayAsyncResourceType.UPLOAD))
                .thenReturn(Optional.of(entity));

        JsonNode cancelledAgain = service.cancelUpload("upload_cancelled_local", 1L);
        assertEquals("cancelled", cancelledAgain.path("status").asText());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.completeUpload("upload_cancelled_local", 1L));
        assertEquals("已取消的 Upload 不允许继续完成 Upload。", error.getMessage());
    }

    @Test
    void shouldExplainGeminiRealtimeAsBlockedWhenNoNativeTargetExists() throws Exception {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                Mockito.mock(GatewayFileRepository.class),
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService.class),
                Mockito.mock(CredentialCryptoService.class),
                credentialMaterialResolver,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                new ObjectMapper(),
                Clock.systemUTC(),
                WebClient.builder()
        );

        UpstreamCredentialEntity credential = credential(201L, ProviderType.GEMINI_DIRECT, 2L, "https://generativelanguage.googleapis.com");
        Mockito.when(distributedKeyQueryService.findActiveById(1L))
                .thenReturn(Optional.of(distributedKey(ProviderType.GEMINI_DIRECT, 201L, credential.getBaseUrl())));
        Mockito.when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(List.of(201L)))
                .thenReturn(List.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(2L)).thenReturn(Optional.of(geminiSiteProfile(2L)));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(2L))
                .thenReturn(Optional.of(snapshot(
                        true,
                        true,
                        AuthStrategy.API_KEY_QUERY,
                        PathStrategy.GEMINI_V1BETA_MODELS
                )));
        Mockito.when(credentialMaterialResolver.resolveStored(credential)).thenReturn(resolvedMaterial(201L, 2L, "api-key"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.createRealtimeClientSecret(
                1L,
                new ObjectMapper().readTree("""
                        {
                          "model":"gemini-2.5-flash-live"
                        }
                        """),
                201L
        ));

        assertTrue(error.getMessage().contains("Gemini ephemeral/live token"));
    }

    @Test
    void shouldAddUploadPartUsingBoundUpstreamMetadata() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
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
                Mockito.mock(DistributedKeyQueryService.class),
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
        entity.setMetadataJson("{\"upstream_object_id\":\"upload-upstream-1\",\"credential_id\":101,\"site_profile_id\":1,\"events\":[]}");
        Mockito.when(gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse("upload_1", GatewayAsyncResourceType.UPLOAD))
                .thenReturn(Optional.of(entity));
        Mockito.when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(credential(101L, ProviderType.OPENAI_DIRECT, 1L, "https://api.openai.com")));
        Mockito.when(upstreamSiteProfileRepository.findById(1L)).thenReturn(Optional.of(siteProfile(1L, UpstreamSiteKind.OPENAI_DIRECT)));
        Mockito.when(credentialCryptoService.decrypt("cipher")).thenReturn("api-key");
        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FilePart filePart = Mockito.mock(FilePart.class);
        Mockito.when(filePart.filename()).thenReturn("part.bin");
        Mockito.when(filePart.headers()).thenReturn(new HttpHeaders() {{
            setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }});
        Mockito.when(filePart.content()).thenReturn(Flux.just(new DefaultDataBufferFactory().wrap("hello".getBytes())));

        JsonNode response = service.addUploadPart("upload_1", 1L, filePart).block();

        assertEquals("/v1/uploads/upload-upstream-1/parts", requestedPath.get());
        assertEquals("part-upstream-1", response.path("id").asText());
        assertEquals("upload_1", response.path("upload_id").asText());
        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(gatewayAsyncResourceRepository).save(captor.capture());
        assertTrue(captor.getValue().getMetadataJson().contains("part-upstream-1"));
    }

    @Test
    void shouldProtectRemoteUploadTerminalStateBeforeCallingUpstream() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        java.util.concurrent.atomic.AtomicBoolean upstreamCalled = new java.util.concurrent.atomic.AtomicBoolean(false);
        ExchangeFunction exchangeFunction = request -> {
            upstreamCalled.set(true);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{}")
                    .build());
        };
        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                Mockito.mock(DistributedKeyQueryService.class),
                Mockito.mock(UpstreamCredentialRepository.class),
                Mockito.mock(UpstreamSiteProfileRepository.class),
                Mockito.mock(SiteCapabilitySnapshotRepository.class),
                Mockito.mock(GatewayFileRepository.class),
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), Mockito.mock(SiteCapabilitySnapshotRepository.class)),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder().exchangeFunction(exchangeFunction)
        );

        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey("upload_done");
        entity.setDistributedKeyId(1L);
        entity.setResourceType(GatewayAsyncResourceType.UPLOAD);
        entity.setStatus("completed");
        entity.setMetadataJson("{\"upstream_object_id\":\"upload-upstream-1\",\"credential_id\":101,\"site_profile_id\":1,\"events\":[]}");
        entity.setResponsePayloadJson("{\"id\":\"upload_done\",\"object\":\"upload\",\"status\":\"completed\"}");
        Mockito.when(gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse("upload_done", GatewayAsyncResourceType.UPLOAD))
                .thenReturn(Optional.of(entity));

        JsonNode completed = service.completeUpload("upload_done", 1L);
        assertEquals("completed", completed.path("status").asText());
        IllegalArgumentException cancelError = assertThrows(IllegalArgumentException.class, () -> service.cancelUpload("upload_done", 1L));
        assertEquals("已完成的 Upload 不允许取消。", cancelError.getMessage());
        assertFalse(upstreamCalled.get());
    }

    @Test
    void shouldRejectRemoteUploadPartWhenTerminal() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                Mockito.mock(DistributedKeyQueryService.class),
                Mockito.mock(UpstreamCredentialRepository.class),
                Mockito.mock(UpstreamSiteProfileRepository.class),
                Mockito.mock(SiteCapabilitySnapshotRepository.class),
                Mockito.mock(GatewayFileRepository.class),
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), Mockito.mock(SiteCapabilitySnapshotRepository.class)),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );

        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey("upload_cancelled");
        entity.setDistributedKeyId(1L);
        entity.setResourceType(GatewayAsyncResourceType.UPLOAD);
        entity.setStatus("cancelled");
        entity.setMetadataJson("{\"upstream_object_id\":\"upload-upstream-1\",\"events\":[]}");
        entity.setResponsePayloadJson("{\"id\":\"upload_cancelled\",\"object\":\"upload\",\"status\":\"cancelled\"}");
        Mockito.when(gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse("upload_cancelled", GatewayAsyncResourceType.UPLOAD))
                .thenReturn(Optional.of(entity));

        FilePart filePart = Mockito.mock(FilePart.class);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.addUploadPart("upload_cancelled", 1L, filePart).block());
        assertEquals("已取消的 Upload 不允许继续追加 part。", error.getMessage());
    }

    private RouteSelectionResult openAiDirectResponseSelection() {
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "openai",
                ProviderType.OPENAI_DIRECT,
                1L,
                ProviderFamily.OPENAI,
                UpstreamSiteKind.OPENAI_DIRECT,
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1,
                ErrorSchemaStrategy.OPENAI_ERROR,
                "https://api.openai.com",
                "gpt-4.1",
                "gpt-4.1",
                List.of("openai"),
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT,
                InteropCapabilityLevel.NATIVE
        );
        RouteCandidateView routeCandidate = new RouteCandidateView(candidate, 11L, 10, 100);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "writer-fast",
                "writer-fast",
                "gpt-4.1",
                "openai",
                "prefix",
                "fingerprint",
                "default",
                RouteSelectionSource.WEIGHTED_HASH,
                routeCandidate,
                List.of(routeCandidate)
        );
    }

    private GatewayAsyncResourceEntity upstreamResponseEntity() {
        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey("resp_local_1");
        entity.setDistributedKeyId(1L);
        entity.setResourceType(GatewayAsyncResourceType.RESPONSE);
        entity.setStatus("in_progress");
        entity.setUpstreamObjectId("resp_upstream_1");
        entity.setRequestPayloadJson("{\"model\":\"writer-fast\",\"store\":true}");
        entity.setResponsePayloadJson("{\"id\":\"resp_local_1\",\"object\":\"response\",\"status\":\"in_progress\"}");
        entity.setMetadataJson("""
                {"object_mode":"upstream_response_with_local_lineage","upstream_object_id":"resp_upstream_1","credential_id":101,"site_profile_id":1,"events":[]}
                """);
        return entity;
    }

    private DistributedKeyView distributedKey(ProviderType providerType, Long credentialId, String baseUrl) {
        return new DistributedKeyView(
                1L,
                "test",
                "sk-gw-test",
                "masked",
                List.of(providerType.name().toLowerCase()),
                List.of(),
                List.of(new DistributedCredentialBindingView(1L, credentialId, providerType.name().toLowerCase(), providerType, baseUrl, 10, 100))
        );
    }

    private UpstreamCredentialEntity credential(Long id, ProviderType providerType, Long siteProfileId, String baseUrl) {
        UpstreamCredentialEntity entity = new UpstreamCredentialEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setProviderType(providerType);
        entity.setSiteProfileId(siteProfileId);
        entity.setBaseUrl(baseUrl);
        entity.setApiKeyCiphertext("cipher");
        entity.setActive(true);
        return entity;
    }

    private UpstreamSiteProfileEntity siteProfile(Long id, UpstreamSiteKind siteKind) {
        UpstreamSiteProfileEntity entity = new UpstreamSiteProfileEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setSiteKind(siteKind);
        entity.setAuthStrategy(AuthStrategy.BEARER);
        entity.setPathStrategy(PathStrategy.OPENAI_V1);
        entity.setErrorSchemaStrategy(ErrorSchemaStrategy.OPENAI_ERROR);
        entity.setActive(true);
        return entity;
    }

    private UpstreamSiteProfileEntity geminiSiteProfile(Long id) {
        return googleGenAiSiteProfile(id, UpstreamSiteKind.GEMINI_DIRECT, AuthStrategy.API_KEY_QUERY);
    }

    private UpstreamSiteProfileEntity googleGenAiSiteProfile(Long id, UpstreamSiteKind siteKind, AuthStrategy authStrategy) {
        UpstreamSiteProfileEntity entity = new UpstreamSiteProfileEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setSiteKind(siteKind);
        entity.setAuthStrategy(authStrategy);
        entity.setPathStrategy(PathStrategy.GEMINI_V1BETA_MODELS);
        entity.setErrorSchemaStrategy(ErrorSchemaStrategy.GEMINI_ERROR);
        entity.setActive(true);
        return entity;
    }

    private SiteCapabilitySnapshotEntity snapshot(boolean supportsFiles, boolean supportsUploads, AuthStrategy authStrategy, PathStrategy pathStrategy) {
        SiteCapabilitySnapshotEntity entity = new SiteCapabilitySnapshotEntity();
        entity.setSupportsFiles(supportsFiles);
        entity.setSupportsUploads(supportsUploads);
        entity.setAuthStrategy(authStrategy);
        entity.setPathStrategy(pathStrategy);
        entity.setErrorSchemaStrategy(pathStrategy == PathStrategy.GEMINI_V1BETA_MODELS ? ErrorSchemaStrategy.GEMINI_ERROR : ErrorSchemaStrategy.OPENAI_ERROR);
        entity.setHealthState("READY");
        entity.setSupportedProtocols(List.of("openai"));
        return entity;
    }

    private ResolvedCredentialMaterial resolvedMaterial(Long credentialId, Long siteProfileId, String secret) {
        return resolvedMaterial(credentialId, siteProfileId, secret, Map.of());
    }

    private ResolvedCredentialMaterial resolvedMaterial(Long credentialId, Long siteProfileId, String secret, Map<String, Object> metadata) {
        return new ResolvedCredentialMaterial(credentialId, siteProfileId, null, secret, "fp", metadata, null, "credential");
    }

}
