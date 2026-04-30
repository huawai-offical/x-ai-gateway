package com.prodigalgal.xaigateway.gateway.core.resource;

import com.google.genai.Client;
import com.google.genai.types.BatchJob;
import com.google.genai.types.JobState;
import com.google.genai.types.TunedModel;
import com.google.genai.types.TuningJob;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.catalog.FineTunedModelRegistrationService;
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
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.PageRequest;
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
        storedEntity.setResponsePayloadJson("{\"id\":\"resp_test\",\"status\":\"completed\"}");
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
                .thenReturn(Optional.of(distributedKey(ProviderType.OPENAI_DIRECT, 101L, "https://api.openai.com")));
        Mockito.when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(List.of(101L)))
                .thenReturn(List.of(credential(101L, ProviderType.OPENAI_DIRECT, 1L, "https://api.openai.com")));
        Mockito.when(upstreamSiteProfileRepository.findById(1L)).thenReturn(Optional.of(siteProfile(1L, UpstreamSiteKind.OPENAI_DIRECT)));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(1L))
                .thenReturn(Optional.of(snapshot(false, false, true, false, AuthStrategy.BEARER, PathStrategy.OPENAI_V1)));
        Mockito.when(credentialCryptoService.decrypt("cipher")).thenReturn("api-key");

        GatewayFileEntity file = gatewayFileEntity(1L, "file-local-1", "payload.jsonl", tempDir.resolve("payload.jsonl"), 1L);
        Mockito.when(gatewayFileRepository.findByFileKeyAndDeletedFalse("file-local-1")).thenReturn(Optional.of(file));
        GatewayFileBindingEntity binding = new GatewayFileBindingEntity();
        binding.setGatewayFileId(1L);
        binding.setCredentialId(101L);
        binding.setExternalFileId("file-upstream-1");
        Mockito.when(gatewayFileBindingRepository.findAllByGatewayFileIdAndCredentialIdOrderByCreatedAtDesc(1L, 101L)).thenReturn(List.of(binding));
        Mockito.when(gatewayFileBindingRepository.findAllByGatewayFileIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(binding));
        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put("input_file_id", "file-local-1");
        request.put("endpoint", "/v1/chat/completions");

        JsonNode response = service.createBatch(1L, request);

        assertTrue(response.path("id").asText().startsWith("batch_"));
        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(gatewayAsyncResourceRepository).save(captor.capture());
        assertTrue(captor.getValue().getRequestPayloadJson().contains("file-upstream-1"));
        assertTrue(captor.getValue().getMetadataJson().contains("batch-upstream-1"));
        assertEquals("batch-upstream-1", captor.getValue().getUpstreamObjectId());
    }

    @Test
    void shouldCreateGeminiBatchWithLocalLineageMetadata() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        GeminiChatModelFactory geminiChatModelFactory = Mockito.mock(GeminiChatModelFactory.class);

        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                gatewayFileRepository,
                gatewayFileBindingRepository,
                credentialCryptoService,
                credentialMaterialResolver,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                geminiChatModelFactory,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );

        UpstreamCredentialEntity credential = credential(201L, ProviderType.GEMINI_DIRECT, 2L, "https://generativelanguage.googleapis.com");
        UpstreamSiteProfileEntity siteProfile = geminiSiteProfile(2L);
        ResolvedCredentialMaterial credentialMaterial = resolvedMaterial(201L, 2L, "api-key");

        Mockito.when(distributedKeyQueryService.findActiveById(1L))
                .thenReturn(Optional.of(distributedKey(ProviderType.GEMINI_DIRECT, 201L, credential.getBaseUrl())));
        Mockito.when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(List.of(201L))).thenReturn(List.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(2L)).thenReturn(Optional.of(siteProfile));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(2L))
                .thenReturn(Optional.of(snapshot(false, false, true, true, AuthStrategy.API_KEY_QUERY, PathStrategy.GEMINI_V1BETA_MODELS)));
        Mockito.when(credentialMaterialResolver.resolveStored(credential)).thenReturn(credentialMaterial);
        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GatewayFileEntity file = gatewayFileEntity(41L, "file-local-1", "payload.jsonl", tempDir.resolve("payload.jsonl"), 1L);
        Mockito.when(gatewayFileRepository.findByFileKeyAndDeletedFalse("file-local-1")).thenReturn(Optional.of(file));
        GatewayFileBindingEntity binding = gatewayBinding(41L, 201L, 2L, "files/upstream-input");
        Mockito.when(gatewayFileBindingRepository.findAllByGatewayFileIdAndCredentialIdOrderByCreatedAtDesc(41L, 201L))
                .thenReturn(List.of(binding));

        com.google.genai.Batches batchesFacade = Mockito.mock(com.google.genai.Batches.class);
        Client client = geminiClient(batchesFacade, null);
        Mockito.when(geminiChatModelFactory.createClient(UpstreamSiteKind.GEMINI_DIRECT, credential.getBaseUrl(), credentialMaterial))
                .thenReturn(client);
        BatchJob batchJob = batchJob("batches/upstream-1", "gemini-2.5-pro", JobState.Known.JOB_STATE_PENDING);
        Mockito.when(batchesFacade.create(Mockito.eq("gemini-2.5-pro"), any(com.google.genai.types.BatchJobSource.class), any(com.google.genai.types.CreateBatchJobConfig.class)))
                .thenReturn(batchJob);

        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put("model", "gemini-2.5-pro");
        request.put("input_file_id", "file-local-1");
        request.put("endpoint", "/v1/chat/completions");

        JsonNode response = service.createBatch(1L, request);

        assertTrue(response.path("id").asText().startsWith("batch_"));
        assertEquals("batch", response.path("object").asText());
        assertEquals("validating", response.path("status").asText());
        assertEquals("files/upstream-input", response.path("input_file_id").asText());
        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(gatewayAsyncResourceRepository).save(captor.capture());
        assertTrue(captor.getValue().getRequestPayloadJson().contains("files/upstream-input"));
        assertTrue(captor.getValue().getMetadataJson().contains("\"upstream_object_id\":\"batches/upstream-1\""));
        assertTrue(captor.getValue().getMetadataJson().contains("\"site_profile_id\":2"));
        assertTrue(captor.getValue().getMetadataJson().contains("\"object_mode\":\"upstream_object_with_local_lineage\""));
        assertEquals("batches/upstream-1", captor.getValue().getUpstreamObjectId());
    }

    @Test
    void shouldCreateVertexBatchWithLocalLineageMetadata() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        GeminiChatModelFactory geminiChatModelFactory = Mockito.mock(GeminiChatModelFactory.class);

        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                gatewayFileRepository,
                gatewayFileBindingRepository,
                credentialCryptoService,
                credentialMaterialResolver,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                geminiChatModelFactory,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );

        UpstreamCredentialEntity credential = credential(301L, ProviderType.GEMINI_DIRECT, 3L, "https://aiplatform.googleapis.com");
        UpstreamSiteProfileEntity siteProfile = googleGenAiSiteProfile(3L, UpstreamSiteKind.VERTEX_AI, AuthStrategy.BEARER);
        ResolvedCredentialMaterial credentialMaterial = resolvedMaterial(
                301L,
                3L,
                "vertex-token",
                Map.of("projectId", "demo-project", "location", "us-central1")
        );

        Mockito.when(distributedKeyQueryService.findActiveById(1L))
                .thenReturn(Optional.of(distributedKey(ProviderType.GEMINI_DIRECT, 301L, credential.getBaseUrl())));
        Mockito.when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(List.of(301L))).thenReturn(List.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(3L)).thenReturn(Optional.of(siteProfile));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(3L))
                .thenReturn(Optional.of(snapshot(false, false, true, true, AuthStrategy.BEARER, PathStrategy.GEMINI_V1BETA_MODELS)));
        Mockito.when(credentialMaterialResolver.resolveStored(credential)).thenReturn(credentialMaterial);
        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GatewayFileEntity file = gatewayFileEntity(42L, "file-local-vertex", "payload.jsonl", tempDir.resolve("vertex-payload.jsonl"), 1L);
        Mockito.when(gatewayFileRepository.findByFileKeyAndDeletedFalse("file-local-vertex")).thenReturn(Optional.of(file));
        GatewayFileBindingEntity binding = gatewayBinding(42L, 301L, 3L, "files/vertex-upstream-input");
        Mockito.when(gatewayFileBindingRepository.findAllByGatewayFileIdAndCredentialIdOrderByCreatedAtDesc(42L, 301L))
                .thenReturn(List.of(binding));

        com.google.genai.Batches batchesFacade = Mockito.mock(com.google.genai.Batches.class);
        Client client = geminiClient(batchesFacade, null);
        Mockito.when(geminiChatModelFactory.createClient(UpstreamSiteKind.VERTEX_AI, credential.getBaseUrl(), credentialMaterial))
                .thenReturn(client);
        BatchJob batchJob = batchJob("batches/vertex-upstream-1", "gemini-2.5-pro", JobState.Known.JOB_STATE_PENDING);
        Mockito.when(batchesFacade.create(Mockito.eq("gemini-2.5-pro"), any(com.google.genai.types.BatchJobSource.class), any(com.google.genai.types.CreateBatchJobConfig.class)))
                .thenReturn(batchJob);

        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put("model", "gemini-2.5-pro");
        request.put("input_file_id", "file-local-vertex");
        request.put("endpoint", "/v1/chat/completions");

        JsonNode response = service.createBatch(1L, request);

        assertTrue(response.path("id").asText().startsWith("batch_"));
        assertEquals("validating", response.path("status").asText());
        assertEquals("files/vertex-upstream-input", response.path("input_file_id").asText());
        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(gatewayAsyncResourceRepository).save(captor.capture());
        assertTrue(captor.getValue().getRequestPayloadJson().contains("files/vertex-upstream-input"));
        assertTrue(captor.getValue().getMetadataJson().contains("\"upstream_object_id\":\"batches/vertex-upstream-1\""));
        assertTrue(captor.getValue().getMetadataJson().contains("\"site_profile_id\":3"));
        assertEquals("batches/vertex-upstream-1", captor.getValue().getUpstreamObjectId());
    }

    @Test
    void shouldCreateGeminiTuningFromGatewayTrainingFile() throws Exception {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        GeminiChatModelFactory geminiChatModelFactory = Mockito.mock(GeminiChatModelFactory.class);

        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                gatewayFileRepository,
                gatewayFileBindingRepository,
                credentialCryptoService,
                credentialMaterialResolver,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                geminiChatModelFactory,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );

        UpstreamCredentialEntity credential = credential(201L, ProviderType.GEMINI_DIRECT, 2L, "https://generativelanguage.googleapis.com");
        UpstreamSiteProfileEntity siteProfile = geminiSiteProfile(2L);
        ResolvedCredentialMaterial credentialMaterial = resolvedMaterial(201L, 2L, "api-key");

        Mockito.when(distributedKeyQueryService.findActiveById(1L))
                .thenReturn(Optional.of(distributedKey(ProviderType.GEMINI_DIRECT, 201L, credential.getBaseUrl())));
        Mockito.when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(List.of(201L))).thenReturn(List.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(2L)).thenReturn(Optional.of(siteProfile));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(2L))
                .thenReturn(Optional.of(snapshot(false, false, true, true, AuthStrategy.API_KEY_QUERY, PathStrategy.GEMINI_V1BETA_MODELS)));
        Mockito.when(credentialMaterialResolver.resolveStored(credential)).thenReturn(credentialMaterial);
        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Path trainingFile = tempDir.resolve("training.jsonl");
        Files.writeString(trainingFile, """
                {"input":"问题","output":"答案"}
                """, StandardCharsets.UTF_8);
        GatewayFileEntity file = gatewayFileEntity(51L, "file-local-train", "training.jsonl", trainingFile, 1L);
        Mockito.when(gatewayFileRepository.findByFileKeyAndDeletedFalse("file-local-train")).thenReturn(Optional.of(file));
        GatewayFileBindingEntity binding = gatewayBinding(51L, 201L, 2L, "files/upstream-train");
        Mockito.when(gatewayFileBindingRepository.findAllByGatewayFileIdAndCredentialIdOrderByCreatedAtDesc(51L, 201L))
                .thenReturn(List.of(binding));

        com.google.genai.Tunings tuningsFacade = Mockito.mock(com.google.genai.Tunings.class);
        Client client = geminiClient(null, tuningsFacade);
        Mockito.when(geminiChatModelFactory.createClient(UpstreamSiteKind.GEMINI_DIRECT, credential.getBaseUrl(), credentialMaterial))
                .thenReturn(client);
        TuningJob tuningJob = tuningJob("tunings/upstream-1", "tunedModels/demo", JobState.Known.JOB_STATE_PENDING);
        Mockito.when(tuningsFacade.tune(Mockito.eq("gemini-2.5-pro"), any(com.google.genai.types.TuningDataset.class), any(com.google.genai.types.CreateTuningJobConfig.class)))
                .thenReturn(tuningJob);

        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put("model", "gemini-2.5-pro");
        request.put("training_file", "file-local-train");
        request.put("suffix", "demo-suffix");

        JsonNode response = service.createTuning(1L, request);

        assertTrue(response.path("id").asText().startsWith("ftjob_"));
        assertEquals("fine_tuning.job", response.path("object").asText());
        assertEquals("queued", response.path("status").asText());
        assertEquals("files/upstream-train", response.path("training_file").asText());
        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(gatewayAsyncResourceRepository).save(captor.capture());
        assertTrue(captor.getValue().getRequestPayloadJson().contains("files/upstream-train"));
        assertTrue(captor.getValue().getMetadataJson().contains("\"upstream_object_id\":\"tunings/upstream-1\""));
        assertTrue(captor.getValue().getMetadataJson().contains("\"site_profile_id\":2"));
    }

    @Test
    void shouldRejectGeminiValidationFileForTuning() throws Exception {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);

        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                gatewayFileRepository,
                gatewayFileBindingRepository,
                credentialCryptoService,
                credentialMaterialResolver,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                Mockito.mock(GeminiChatModelFactory.class),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );

        UpstreamCredentialEntity credential = credential(201L, ProviderType.GEMINI_DIRECT, 2L, "https://generativelanguage.googleapis.com");
        Mockito.when(distributedKeyQueryService.findActiveById(1L))
                .thenReturn(Optional.of(distributedKey(ProviderType.GEMINI_DIRECT, 201L, credential.getBaseUrl())));
        Mockito.when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(List.of(201L))).thenReturn(List.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(2L)).thenReturn(Optional.of(geminiSiteProfile(2L)));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(2L))
                .thenReturn(Optional.of(snapshot(false, false, true, true, AuthStrategy.API_KEY_QUERY, PathStrategy.GEMINI_V1BETA_MODELS)));
        Mockito.when(credentialMaterialResolver.resolveStored(credential)).thenReturn(resolvedMaterial(201L, 2L, "api-key"));

        Path trainingFile = tempDir.resolve("training.jsonl");
        Files.writeString(trainingFile, """
                {"input":"问题","output":"答案"}
                """, StandardCharsets.UTF_8);
        Mockito.when(gatewayFileRepository.findByFileKeyAndDeletedFalse("file-local-train"))
                .thenReturn(Optional.of(gatewayFileEntity(61L, "file-local-train", "training.jsonl", trainingFile, 1L)));
        Mockito.when(gatewayFileRepository.findByFileKeyAndDeletedFalse("file-local-validation"))
                .thenReturn(Optional.of(gatewayFileEntity(62L, "file-local-validation", "validation.jsonl", trainingFile, 1L)));
        GatewayFileBindingEntity binding = gatewayBinding(61L, 201L, 2L, "files/upstream-file");
        Mockito.when(gatewayFileBindingRepository.findAllByGatewayFileIdAndCredentialIdOrderByCreatedAtDesc(61L, 201L)).thenReturn(List.of(binding));
        Mockito.when(gatewayFileBindingRepository.findAllByGatewayFileIdAndCredentialIdOrderByCreatedAtDesc(62L, 201L)).thenReturn(List.of(binding));

        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put("model", "gemini-2.5-pro");
        request.put("training_file", "file-local-train");
        request.put("validation_file", "file-local-validation");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.createTuning(1L, request));
        assertEquals("Gemini tuning 暂不支持 validation_file。", error.getMessage());
    }

    @Test
    void shouldRegisterFineTunedModelAndAliasWhenGeminiTuningSucceeds() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        GeminiChatModelFactory geminiChatModelFactory = Mockito.mock(GeminiChatModelFactory.class);
        FineTunedModelRegistrationService fineTunedModelRegistrationService = Mockito.mock(FineTunedModelRegistrationService.class);

        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                Mockito.mock(DistributedKeyQueryService.class),
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                Mockito.mock(GatewayFileRepository.class),
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService.class),
                Mockito.mock(CredentialCryptoService.class),
                credentialMaterialResolver,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                fineTunedModelRegistrationService,
                Mockito.mock(com.prodigalgal.xaigateway.provider.adapter.anthropic.AnthropicChatModelFactory.class),
                geminiChatModelFactory,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );

        UpstreamCredentialEntity credential = credential(201L, ProviderType.GEMINI_DIRECT, 2L, "https://generativelanguage.googleapis.com");
        ResolvedCredentialMaterial material = resolvedMaterial(201L, 2L, "api-key");
        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey("ftjob_local_1");
        entity.setDistributedKeyId(1L);
        entity.setResourceType(GatewayAsyncResourceType.TUNING);
        entity.setStatus("queued");
        entity.setRequestPayloadJson("{\"model\":\"gemini-2.5-pro\",\"training_file\":\"files/upstream-train\",\"suffix\":\"demo-suffix\"}");
        entity.setResponsePayloadJson("{\"id\":\"ftjob_local_1\",\"object\":\"fine_tuning.job\",\"status\":\"queued\"}");
        entity.setMetadataJson("""
                {
                  "upstream_object_id":"tunings/upstream-1",
                  "credential_id":201,
                  "site_profile_id":2,
                  "events":[]
                }
                """);

        Mockito.when(gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse("ftjob_local_1", GatewayAsyncResourceType.TUNING))
                .thenReturn(Optional.of(entity));
        Mockito.when(upstreamCredentialRepository.findById(201L)).thenReturn(Optional.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(2L)).thenReturn(Optional.of(geminiSiteProfile(2L)));
        Mockito.when(credentialMaterialResolver.resolveStored(credential)).thenReturn(material);
        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(fineTunedModelRegistrationService.register(
                        2L,
                        ProviderType.GEMINI_DIRECT,
                        "gemini-2.5-pro",
                        "tunedModels/demo",
                        "demo-suffix",
                        "ftjob_local_1"))
                .thenReturn(new FineTunedModelRegistrationService.RegistrationResult(
                        "tunedModels/demo",
                        "tunedmodels-demo",
                        List.of("demo-suffix")));

        com.google.genai.Tunings tuningsFacade = Mockito.mock(com.google.genai.Tunings.class);
        Client client = geminiClient(null, tuningsFacade);
        Mockito.when(geminiChatModelFactory.createClient(UpstreamSiteKind.GEMINI_DIRECT, credential.getBaseUrl(), material))
                .thenReturn(client);
        TuningJob succeededJob = tuningJob("tunings/upstream-1", "tunedModels/demo", JobState.Known.JOB_STATE_SUCCEEDED);
        Mockito.when(tuningsFacade.get(Mockito.eq("tunings/upstream-1"), any(com.google.genai.types.GetTuningJobConfig.class)))
                .thenReturn(succeededJob);

        JsonNode response = service.getTuning("ftjob_local_1", 1L);

        assertEquals("succeeded", response.path("status").asText());
        assertEquals("tunedModels/demo", response.path("fine_tuned_model").asText());
        assertEquals("demo-suffix", response.path("registered_aliases").get(0).asText());
        Mockito.verify(fineTunedModelRegistrationService).register(
                2L,
                ProviderType.GEMINI_DIRECT,
                "gemini-2.5-pro",
                "tunedModels/demo",
                "demo-suffix",
                "ftjob_local_1");
        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(gatewayAsyncResourceRepository, Mockito.atLeast(2)).save(captor.capture());
        GatewayAsyncResourceEntity latest = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertTrue(latest.getMetadataJson().contains("\"registered_model_key\":\"tunedmodels-demo\""));
        assertTrue(latest.getMetadataJson().contains("\"registered_aliases\":[\"demo-suffix\"]"));
        assertTrue(latest.getResponsePayloadJson().contains("\"registered_aliases\":[\"demo-suffix\"]"));
    }

    @Test
    void shouldCancelGeminiTuningAndRefreshState() {
        GatewayAsyncResourceRepository gatewayAsyncResourceRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        GeminiChatModelFactory geminiChatModelFactory = Mockito.mock(GeminiChatModelFactory.class);
        FineTunedModelRegistrationService fineTunedModelRegistrationService = Mockito.mock(FineTunedModelRegistrationService.class);

        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                Mockito.mock(DistributedKeyQueryService.class),
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                gatewayFileRepository,
                gatewayFileBindingRepository,
                Mockito.mock(com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService.class),
                Mockito.mock(CredentialCryptoService.class),
                credentialMaterialResolver,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                fineTunedModelRegistrationService,
                Mockito.mock(com.prodigalgal.xaigateway.provider.adapter.anthropic.AnthropicChatModelFactory.class),
                geminiChatModelFactory,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );

        UpstreamCredentialEntity credential = credential(201L, ProviderType.GEMINI_DIRECT, 2L, "https://generativelanguage.googleapis.com");
        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey("ftjob_local_1");
        entity.setDistributedKeyId(1L);
        entity.setResourceType(GatewayAsyncResourceType.TUNING);
        entity.setStatus("queued");
        entity.setRequestPayloadJson("{\"model\":\"gemini-2.5-pro\",\"training_file\":\"files/upstream-train\"}");
        entity.setResponsePayloadJson("{\"id\":\"ftjob_local_1\",\"object\":\"fine_tuning.job\",\"status\":\"queued\"}");
        entity.setMetadataJson("""
                {
                  "upstream_object_id":"tunings/upstream-1",
                  "credential_id":201,
                  "site_profile_id":2,
                  "registered_model_key":"tunedmodels-demo",
                  "registered_aliases":["demo-suffix"],
                  "events":[]
                }
                """);

        Mockito.when(gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse("ftjob_local_1", GatewayAsyncResourceType.TUNING))
                .thenReturn(Optional.of(entity));
        Mockito.when(upstreamCredentialRepository.findById(201L)).thenReturn(Optional.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(2L)).thenReturn(Optional.of(geminiSiteProfile(2L)));
        Mockito.when(credentialMaterialResolver.resolveStored(credential)).thenReturn(resolvedMaterial(201L, 2L, "api-key"));
        Mockito.when(gatewayAsyncResourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        com.google.genai.Tunings tuningsFacade = Mockito.mock(com.google.genai.Tunings.class);
        Client client = geminiClient(null, tuningsFacade);
        Mockito.when(geminiChatModelFactory.createClient(UpstreamSiteKind.GEMINI_DIRECT, credential.getBaseUrl(), resolvedMaterial(201L, 2L, "api-key")))
                .thenReturn(client);
        TuningJob cancelledJob = tuningJob("tunings/upstream-1", "tunedModels/demo", JobState.Known.JOB_STATE_CANCELLED);
        Mockito.when(tuningsFacade.get(Mockito.eq("tunings/upstream-1"), any(com.google.genai.types.GetTuningJobConfig.class)))
                .thenReturn(cancelledJob);

        JsonNode response = service.cancelTuning("ftjob_local_1", 1L);

        assertEquals("ftjob_local_1", response.path("id").asText());
        assertEquals("cancelled", response.path("status").asText());
        Mockito.verify(tuningsFacade).cancel(Mockito.eq("tunings/upstream-1"), any(com.google.genai.types.CancelTuningJobConfig.class));
        Mockito.verify(fineTunedModelRegistrationService).unregister(2L, "tunedmodels-demo", List.of("demo-suffix"), "ftjob_local_1");
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
        GeminiChatModelFactory geminiChatModelFactory = Mockito.mock(GeminiChatModelFactory.class);

        GatewayAsyncResourceService service = new GatewayAsyncResourceService(
                gatewayAsyncResourceRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                gatewayFileRepository,
                gatewayFileBindingRepository,
                credentialCryptoService,
                credentialMaterialResolver,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                geminiChatModelFactory,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-12T04:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );

        UpstreamCredentialEntity credential = credential(201L, ProviderType.GEMINI_DIRECT, 2L, "https://generativelanguage.googleapis.com");
        UpstreamSiteProfileEntity siteProfile = geminiSiteProfile(2L);
        SiteCapabilitySnapshotEntity snapshot = snapshot(
                true,
                true,
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
                  "filename":"batch-input.jsonl",
                  "bytes":5,
                  "purpose":"batch"
                }
                """), 201L);

        String uploadId = createResponse.path("id").asText();
        assertEquals("upload", createResponse.path("object").asText());
        assertEquals("created", createResponse.path("status").asText());
        assertEquals("batch-input.jsonl", createResponse.path("filename").asText());

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
        assertEquals("batch-input.jsonl", savedFile.get().getFilename());
        assertEquals("batch", savedFile.get().getPurpose());
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
                Mockito.mock(CredentialCryptoService.class),
                credentialMaterialResolver,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                Mockito.mock(GeminiChatModelFactory.class),
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

    @Test
    void shouldListPersistedTuningJobs() {
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
        entity.setResourceKey("ftjob_1");
        entity.setDistributedKeyId(1L);
        entity.setResourceType(GatewayAsyncResourceType.TUNING);
        entity.setStatus("running");
        entity.setResponsePayloadJson("{\"id\":\"ftjob_1\",\"object\":\"fine_tuning.job\",\"status\":\"running\"}");
        Mockito.when(gatewayAsyncResourceRepository.search(1L, GatewayAsyncResourceType.TUNING, null, PageRequest.of(0, 100)))
                .thenReturn(List.of(entity));

        JsonNode response = service.listTunings(1L);

        assertEquals("list", response.path("object").asText());
        assertEquals("ftjob_1", response.path("data").get(0).path("id").asText());
        assertFalse(response.path("has_more").asBoolean());
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

    private SiteCapabilitySnapshotEntity snapshot(boolean supportsFiles, boolean supportsUploads, boolean supportsBatches, boolean supportsTuning, AuthStrategy authStrategy, PathStrategy pathStrategy) {
        SiteCapabilitySnapshotEntity entity = new SiteCapabilitySnapshotEntity();
        entity.setSupportsFiles(supportsFiles);
        entity.setSupportsUploads(supportsUploads);
        entity.setSupportsBatches(supportsBatches);
        entity.setSupportsTuning(supportsTuning);
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

    private GatewayFileEntity gatewayFileEntity(Long id, String fileKey, String filename, Path storagePath, Long distributedKeyId) {
        GatewayFileEntity file = new GatewayFileEntity();
        ReflectionTestUtils.setField(file, "id", id);
        ReflectionTestUtils.setField(file, "createdAt", Instant.parse("2026-04-12T04:00:00Z"));
        file.setFileKey(fileKey);
        file.setDistributedKeyId(distributedKeyId);
        file.setFilename(filename);
        file.setMimeType("application/jsonl");
        file.setStoragePath(storagePath.toString());
        file.setStatus("processed");
        file.setPurpose("assistants");
        file.setSizeBytes(32L);
        return file;
    }

    private GatewayFileBindingEntity gatewayBinding(Long gatewayFileId, Long credentialId, Long siteProfileId, String externalFileId) {
        GatewayFileBindingEntity binding = new GatewayFileBindingEntity();
        binding.setGatewayFileId(gatewayFileId);
        binding.setCredentialId(credentialId);
        binding.setSiteProfileId(siteProfileId);
        binding.setProviderType(ProviderType.GEMINI_DIRECT);
        binding.setExternalFileId(externalFileId);
        return binding;
    }

    private Client geminiClient(com.google.genai.Batches batchesFacade, com.google.genai.Tunings tuningsFacade) {
        Client client = Client.builder().apiKey("test").build();
        if (batchesFacade != null) {
            ReflectionTestUtils.setField(client, "batches", batchesFacade);
        }
        if (tuningsFacade != null) {
            ReflectionTestUtils.setField(client, "tunings", tuningsFacade);
        }
        return client;
    }

    private BatchJob batchJob(String name, String model, JobState.Known state) {
        BatchJob batchJob = Mockito.mock(BatchJob.class);
        JobState jobState = jobState(state);
        Mockito.when(batchJob.name()).thenReturn(Optional.of(name));
        Mockito.when(batchJob.model()).thenReturn(Optional.of(model));
        Mockito.when(batchJob.createTime()).thenReturn(Optional.of(Instant.parse("2026-04-12T04:00:00Z")));
        Mockito.when(batchJob.state()).thenReturn(Optional.of(jobState));
        Mockito.when(batchJob.error()).thenReturn(Optional.empty());
        return batchJob;
    }

    private TuningJob tuningJob(String name, String tunedModelName, JobState.Known state) {
        TuningJob tuningJob = Mockito.mock(TuningJob.class);
        TunedModel tunedModel = Mockito.mock(TunedModel.class);
        JobState jobState = jobState(state);
        Mockito.when(tunedModel.model()).thenReturn(Optional.of(tunedModelName));
        Mockito.when(tuningJob.name()).thenReturn(Optional.of(name));
        Mockito.when(tuningJob.createTime()).thenReturn(Optional.of(Instant.parse("2026-04-12T04:00:00Z")));
        Mockito.when(tuningJob.state()).thenReturn(Optional.of(jobState));
        Mockito.when(tuningJob.tuningJobState()).thenReturn(Optional.empty());
        Mockito.when(tuningJob.tunedModel()).thenReturn(Optional.of(tunedModel));
        Mockito.when(tuningJob.error()).thenReturn(Optional.empty());
        return tuningJob;
    }

    private JobState jobState(JobState.Known state) {
        JobState jobState = Mockito.mock(JobState.class);
        Mockito.when(jobState.knownEnum()).thenReturn(state);
        return jobState;
    }
}
