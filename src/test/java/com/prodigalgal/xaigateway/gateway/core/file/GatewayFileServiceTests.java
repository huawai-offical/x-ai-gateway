package com.prodigalgal.xaigateway.gateway.core.file;

import com.google.genai.Client;
import tools.jackson.databind.ObjectMapper;
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
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayFileBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayFileEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class GatewayFileServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateGatewayFileAndUpstreamBinding() throws Exception {
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);

        GatewayProperties properties = new GatewayProperties();
        properties.getStorage().setFileRoot(tempDir.toString());

        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("""
                        {"id":"file-upstream-1","object":"file","filename":"demo.txt","bytes":5,"status":"processed"}
                        """)
                .build());

        GatewayFileService service = new GatewayFileService(
                gatewayFileRepository,
                gatewayFileBindingRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                credentialCryptoService,
                properties,
                WebClient.builder().exchangeFunction(exchangeFunction),
                new ObjectMapper()
        );

        Mockito.when(distributedKeyQueryService.findActiveById(1L))
                .thenReturn(Optional.of(distributedKey(ProviderType.OPENAI_DIRECT, 101L, "https://api.openai.com")));
        Mockito.when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(List.of(101L)))
                .thenReturn(List.of(credential(101L, ProviderType.OPENAI_DIRECT, 1L, "https://api.openai.com")));
        Mockito.when(upstreamSiteProfileRepository.findById(1L)).thenReturn(Optional.of(siteProfile(1L, UpstreamSiteKind.OPENAI_DIRECT)));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(1L)).thenReturn(Optional.of(snapshot(true, AuthStrategy.BEARER, PathStrategy.OPENAI_V1)));
        Mockito.when(credentialCryptoService.decrypt("cipher")).thenReturn("api-key");
        Mockito.when(gatewayFileRepository.save(any())).thenAnswer(invocation -> {
            GatewayFileEntity entity = invocation.getArgument(0);
            if (entity.getCreatedAt() == null) {
                ReflectionTestUtils.setField(entity, "id", 1L);
                ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-04-12T04:00:00Z"));
            }
            return entity;
        });
        Mockito.when(gatewayFileBindingRepository.save(any())).thenAnswer(invocation -> {
            GatewayFileBindingEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 10L);
            return entity;
        });

        FilePart filePart = textFilePart("demo.txt", "hello", MediaType.TEXT_PLAIN);

        GatewayFileResponse response = service.createFile(1L, filePart, "assistants").block();

        assertEquals("processed", response.status());
        ArgumentCaptor<GatewayFileBindingEntity> captor = ArgumentCaptor.forClass(GatewayFileBindingEntity.class);
        Mockito.verify(gatewayFileBindingRepository).save(captor.capture());
        assertEquals("file-upstream-1", captor.getValue().getExternalFileId());
    }

    @Test
    void shouldCreateGatewayFileAndGeminiBinding() {
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        GeminiChatModelFactory geminiChatModelFactory = Mockito.mock(GeminiChatModelFactory.class);

        GatewayProperties properties = new GatewayProperties();
        properties.getStorage().setFileRoot(tempDir.toString());

        GatewayFileService service = new GatewayFileService(
                gatewayFileRepository,
                gatewayFileBindingRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                credentialCryptoService,
                credentialMaterialResolver,
                geminiChatModelFactory,
                properties,
                WebClient.builder(),
                new ObjectMapper()
        );

        UpstreamCredentialEntity credential = credential(
                201L,
                ProviderType.GEMINI_DIRECT,
                2L,
                "https://generativelanguage.googleapis.com"
        );
        UpstreamSiteProfileEntity siteProfile = geminiSiteProfile(2L);
        ResolvedCredentialMaterial credentialMaterial = resolvedMaterial(201L, 2L, "api-key");

        Mockito.when(distributedKeyQueryService.findActiveById(1L))
                .thenReturn(Optional.of(distributedKey(ProviderType.GEMINI_DIRECT, 201L, credential.getBaseUrl())));
        Mockito.when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(List.of(201L)))
                .thenReturn(List.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(2L)).thenReturn(Optional.of(siteProfile));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(2L))
                .thenReturn(Optional.of(snapshot(true, AuthStrategy.API_KEY_QUERY, PathStrategy.GEMINI_V1BETA_MODELS)));
        Mockito.when(credentialMaterialResolver.resolveStored(credential)).thenReturn(credentialMaterial);
        Mockito.when(gatewayFileRepository.save(any())).thenAnswer(invocation -> {
            GatewayFileEntity entity = invocation.getArgument(0);
            if (entity.getCreatedAt() == null) {
                ReflectionTestUtils.setField(entity, "id", 11L);
                ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-04-12T04:00:00Z"));
            }
            return entity;
        });
        Mockito.when(gatewayFileBindingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        com.google.genai.Files filesFacade = Mockito.mock(com.google.genai.Files.class);
        Client client = geminiClient(filesFacade);
        Mockito.when(geminiChatModelFactory.createClient(UpstreamSiteKind.GEMINI_DIRECT, credential.getBaseUrl(), credentialMaterial))
                .thenReturn(client);
        com.google.genai.types.File uploadedFile = geminiFile("files/gemini-upstream-1", "demo.txt", "text/plain", 5L);
        Mockito.when(filesFacade.upload(any(byte[].class), any(com.google.genai.types.UploadFileConfig.class)))
                .thenReturn(uploadedFile);

        GatewayFileResponse response = service.createFile(1L, textFilePart("demo.txt", "hello", MediaType.TEXT_PLAIN), "assistants").block();

        assertEquals("uploaded", response.status());
        ArgumentCaptor<GatewayFileBindingEntity> bindingCaptor = ArgumentCaptor.forClass(GatewayFileBindingEntity.class);
        Mockito.verify(gatewayFileBindingRepository).save(bindingCaptor.capture());
        assertEquals("files/gemini-upstream-1", bindingCaptor.getValue().getExternalFileId());
        assertEquals(2L, bindingCaptor.getValue().getSiteProfileId());
        Mockito.verify(filesFacade).upload(any(byte[].class), any(com.google.genai.types.UploadFileConfig.class));
    }

    @Test
    void shouldCreateGatewayFileAndVertexBinding() {
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        GeminiChatModelFactory geminiChatModelFactory = Mockito.mock(GeminiChatModelFactory.class);

        GatewayProperties properties = new GatewayProperties();
        properties.getStorage().setFileRoot(tempDir.toString());

        GatewayFileService service = new GatewayFileService(
                gatewayFileRepository,
                gatewayFileBindingRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                credentialCryptoService,
                credentialMaterialResolver,
                geminiChatModelFactory,
                properties,
                WebClient.builder(),
                new ObjectMapper()
        );

        UpstreamCredentialEntity credential = credential(
                301L,
                ProviderType.GEMINI_DIRECT,
                3L,
                "https://aiplatform.googleapis.com"
        );
        UpstreamSiteProfileEntity siteProfile = googleGenAiSiteProfile(3L, UpstreamSiteKind.VERTEX_AI, AuthStrategy.BEARER);
        ResolvedCredentialMaterial credentialMaterial = resolvedMaterial(
                301L,
                3L,
                "vertex-token",
                Map.of("projectId", "demo-project", "location", "us-central1")
        );

        Mockito.when(distributedKeyQueryService.findActiveById(1L))
                .thenReturn(Optional.of(distributedKey(ProviderType.GEMINI_DIRECT, 301L, credential.getBaseUrl())));
        Mockito.when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(List.of(301L)))
                .thenReturn(List.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(3L)).thenReturn(Optional.of(siteProfile));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(3L))
                .thenReturn(Optional.of(snapshot(true, AuthStrategy.BEARER, PathStrategy.GEMINI_V1BETA_MODELS)));
        Mockito.when(credentialMaterialResolver.resolveStored(credential)).thenReturn(credentialMaterial);
        Mockito.when(gatewayFileRepository.save(any())).thenAnswer(invocation -> {
            GatewayFileEntity entity = invocation.getArgument(0);
            if (entity.getCreatedAt() == null) {
                ReflectionTestUtils.setField(entity, "id", 12L);
                ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-04-12T04:00:00Z"));
            }
            return entity;
        });
        Mockito.when(gatewayFileBindingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        com.google.genai.Files filesFacade = Mockito.mock(com.google.genai.Files.class);
        Client client = geminiClient(filesFacade);
        Mockito.when(geminiChatModelFactory.createClient(UpstreamSiteKind.VERTEX_AI, credential.getBaseUrl(), credentialMaterial))
                .thenReturn(client);
        com.google.genai.types.File uploadedFile = geminiFile("files/vertex-upstream-1", "vertex-demo.txt", "text/plain", 5L);
        Mockito.when(filesFacade.upload(any(byte[].class), any(com.google.genai.types.UploadFileConfig.class)))
                .thenReturn(uploadedFile);

        GatewayFileResponse response = service.createFile(1L, textFilePart("vertex-demo.txt", "hello", MediaType.TEXT_PLAIN), "assistants").block();

        assertEquals("uploaded", response.status());
        ArgumentCaptor<GatewayFileBindingEntity> bindingCaptor = ArgumentCaptor.forClass(GatewayFileBindingEntity.class);
        Mockito.verify(gatewayFileBindingRepository).save(bindingCaptor.capture());
        assertEquals("files/vertex-upstream-1", bindingCaptor.getValue().getExternalFileId());
        assertEquals(3L, bindingCaptor.getValue().getSiteProfileId());
        Mockito.verify(filesFacade).upload(any(byte[].class), any(com.google.genai.types.UploadFileConfig.class));
    }

    @Test
    void shouldResolveGoogleNativeFileByExternalId() {
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        GeminiChatModelFactory geminiChatModelFactory = Mockito.mock(GeminiChatModelFactory.class);

        GatewayProperties properties = new GatewayProperties();
        properties.getStorage().setFileRoot(tempDir.toString());

        GatewayFileService service = new GatewayFileService(
                gatewayFileRepository,
                gatewayFileBindingRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                credentialCryptoService,
                credentialMaterialResolver,
                geminiChatModelFactory,
                properties,
                WebClient.builder(),
                new ObjectMapper()
        );

        GatewayFileEntity file = gatewayFileEntity(51L, "file-google-1", tempDir.resolve("google.txt"), "google.txt", "text/plain", 1L);
        GatewayFileBindingEntity binding = geminiBinding(51L, 201L, 2L, "files/google-upstream-1");
        UpstreamCredentialEntity credential = credential(201L, ProviderType.GEMINI_DIRECT, 2L, "https://generativelanguage.googleapis.com");
        UpstreamSiteProfileEntity siteProfile = geminiSiteProfile(2L);
        ResolvedCredentialMaterial credentialMaterial = resolvedMaterial(201L, 2L, "api-key");

        Mockito.when(distributedKeyQueryService.findActiveById(1L))
                .thenReturn(Optional.of(distributedKey(ProviderType.GEMINI_DIRECT, 201L, credential.getBaseUrl())));
        Mockito.when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(List.of(201L))).thenReturn(List.of(credential));
        Mockito.when(upstreamCredentialRepository.findById(201L)).thenReturn(Optional.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(2L)).thenReturn(Optional.of(siteProfile));
        Mockito.when(gatewayFileBindingRepository.findAllBySiteProfileIdAndExternalFileIdOrderByCreatedAtDesc(2L, "files/google-upstream-1"))
                .thenReturn(List.of(binding));
        Mockito.when(gatewayFileRepository.findById(51L)).thenReturn(Optional.of(file));
        Mockito.when(credentialMaterialResolver.resolveStored(credential)).thenReturn(credentialMaterial);
        Mockito.when(gatewayFileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(gatewayFileBindingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        com.google.genai.Files filesFacade = Mockito.mock(com.google.genai.Files.class);
        Client client = geminiClient(filesFacade);
        com.google.genai.types.File upstreamFile = geminiFile("files/google-upstream-1", "google-updated.txt", "text/plain", 9L);
        Mockito.when(geminiChatModelFactory.createClient(UpstreamSiteKind.GEMINI_DIRECT, credential.getBaseUrl(), credentialMaterial))
                .thenReturn(client);
        Mockito.when(filesFacade.get(Mockito.eq("files/google-upstream-1"), any(com.google.genai.types.GetFileConfig.class)))
                .thenReturn(upstreamFile);

        GatewayFileService.GoogleNativeFileView view = service.getGoogleNativeFile("files/google-upstream-1", 1L);

        assertEquals("files/google-upstream-1", view.externalFileId());
        assertEquals("google-updated.txt", view.displayName());
        assertEquals("file-google-1", view.response().id());
    }

    @Test
    void shouldReadGeminiFileContentUsingUpstreamBinding() throws Exception {
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        GeminiChatModelFactory geminiChatModelFactory = Mockito.mock(GeminiChatModelFactory.class);

        GatewayProperties properties = new GatewayProperties();
        properties.getStorage().setFileRoot(tempDir.toString());

        GatewayFileService service = new GatewayFileService(
                gatewayFileRepository,
                gatewayFileBindingRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                credentialCryptoService,
                credentialMaterialResolver,
                geminiChatModelFactory,
                properties,
                WebClient.builder(),
                new ObjectMapper()
        );

        Path localFile = tempDir.resolve("local.txt");
        Files.writeString(localFile, "local-content", StandardCharsets.UTF_8);

        GatewayFileEntity file = gatewayFileEntity(21L, "file-1", localFile, "demo.txt", "text/plain", 1L);
        GatewayFileBindingEntity binding = geminiBinding(21L, 201L, 2L, "files/gemini-upstream-1");
        UpstreamCredentialEntity credential = credential(
                201L,
                ProviderType.GEMINI_DIRECT,
                2L,
                "https://generativelanguage.googleapis.com"
        );
        UpstreamSiteProfileEntity siteProfile = geminiSiteProfile(2L);
        ResolvedCredentialMaterial credentialMaterial = resolvedMaterial(201L, 2L, "api-key");

        Mockito.when(gatewayFileRepository.findByFileKeyAndDeletedFalse("file-1")).thenReturn(Optional.of(file));
        Mockito.when(gatewayFileBindingRepository.findAllByGatewayFileIdOrderByCreatedAtDesc(21L)).thenReturn(List.of(binding));
        Mockito.when(upstreamCredentialRepository.findById(201L)).thenReturn(Optional.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(2L)).thenReturn(Optional.of(siteProfile));
        Mockito.when(credentialMaterialResolver.resolveStored(credential)).thenReturn(credentialMaterial);
        Mockito.when(gatewayFileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(gatewayFileBindingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        com.google.genai.Files filesFacade = Mockito.mock(com.google.genai.Files.class);
        Client client = geminiClient(filesFacade);
        Mockito.when(geminiChatModelFactory.createClient(UpstreamSiteKind.GEMINI_DIRECT, credential.getBaseUrl(), credentialMaterial))
                .thenReturn(client);
        com.google.genai.types.File upstreamFile = geminiFile("files/gemini-upstream-1", "demo.txt", "text/plain", 14L);
        Mockito.when(filesFacade.get(Mockito.eq("files/gemini-upstream-1"), any(com.google.genai.types.GetFileConfig.class)))
                .thenReturn(upstreamFile);
        Mockito.doAnswer(invocation -> {
            Path target = Path.of(invocation.getArgument(1, String.class));
            Files.writeString(target, "remote-content", StandardCharsets.UTF_8);
            return null;
        }).when(filesFacade).download(Mockito.eq("files/gemini-upstream-1"), any(String.class), any(com.google.genai.types.DownloadFileConfig.class));

        GatewayFileContent content = service.getFileContent("file-1", 1L);

        assertEquals("text/plain", content.mimeType());
        assertArrayEquals("remote-content".getBytes(StandardCharsets.UTF_8), content.bytes());
        Mockito.verify(filesFacade).get(Mockito.eq("files/gemini-upstream-1"), any(com.google.genai.types.GetFileConfig.class));
        Mockito.verify(filesFacade).download(Mockito.eq("files/gemini-upstream-1"), any(String.class), any(com.google.genai.types.DownloadFileConfig.class));
    }

    @Test
    void shouldDeleteUpstreamFileBeforeLocalDelete() {
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);

        GatewayProperties properties = new GatewayProperties();
        properties.getStorage().setFileRoot(tempDir.toString());

        java.util.concurrent.atomic.AtomicReference<String> method = new java.util.concurrent.atomic.AtomicReference<>();
        ExchangeFunction exchangeFunction = request -> {
            method.set(request.method().name());
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        };

        GatewayFileService service = new GatewayFileService(
                gatewayFileRepository,
                gatewayFileBindingRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                credentialCryptoService,
                properties,
                WebClient.builder().exchangeFunction(exchangeFunction),
                new ObjectMapper()
        );

        GatewayFileEntity file = gatewayFileEntity(1L, "file-1", tempDir.resolve("demo.txt"), "demo.txt", "text/plain", 1L);
        GatewayFileBindingEntity binding = new GatewayFileBindingEntity();
        binding.setGatewayFileId(1L);
        binding.setCredentialId(101L);
        binding.setProviderType(ProviderType.OPENAI_DIRECT);
        binding.setExternalFileId("file-upstream-1");
        binding.setStatus("SYNCED");

        UpstreamCredentialEntity credential = credential(101L, ProviderType.OPENAI_DIRECT, 1L, "https://api.openai.com");

        Mockito.when(gatewayFileRepository.findByFileKeyAndDeletedFalse("file-1")).thenReturn(Optional.of(file));
        Mockito.when(gatewayFileBindingRepository.findAllByGatewayFileIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(binding));
        Mockito.when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(1L)).thenReturn(Optional.of(siteProfile(1L, UpstreamSiteKind.OPENAI_DIRECT)));
        Mockito.when(credentialCryptoService.decrypt("cipher")).thenReturn("api-key");

        service.deleteFile("file-1", 1L);

        assertEquals("DELETE", method.get());
    }

    @Test
    void shouldDeleteGeminiUpstreamFileBeforeLocalDelete() throws Exception {
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        GeminiChatModelFactory geminiChatModelFactory = Mockito.mock(GeminiChatModelFactory.class);

        GatewayProperties properties = new GatewayProperties();
        properties.getStorage().setFileRoot(tempDir.toString());

        GatewayFileService service = new GatewayFileService(
                gatewayFileRepository,
                gatewayFileBindingRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                credentialCryptoService,
                credentialMaterialResolver,
                geminiChatModelFactory,
                properties,
                WebClient.builder(),
                new ObjectMapper()
        );

        Path localFile = tempDir.resolve("delete-me.txt");
        Files.writeString(localFile, "hello", StandardCharsets.UTF_8);
        GatewayFileEntity file = gatewayFileEntity(31L, "file-1", localFile, "delete-me.txt", "text/plain", 1L);
        GatewayFileBindingEntity binding = geminiBinding(31L, 201L, 2L, "files/gemini-upstream-1");
        UpstreamCredentialEntity credential = credential(
                201L,
                ProviderType.GEMINI_DIRECT,
                2L,
                "https://generativelanguage.googleapis.com"
        );
        UpstreamSiteProfileEntity siteProfile = geminiSiteProfile(2L);
        ResolvedCredentialMaterial credentialMaterial = resolvedMaterial(201L, 2L, "api-key");

        Mockito.when(gatewayFileRepository.findByFileKeyAndDeletedFalse("file-1")).thenReturn(Optional.of(file));
        Mockito.when(gatewayFileBindingRepository.findAllByGatewayFileIdOrderByCreatedAtDesc(31L)).thenReturn(List.of(binding));
        Mockito.when(upstreamCredentialRepository.findById(201L)).thenReturn(Optional.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(2L)).thenReturn(Optional.of(siteProfile));
        Mockito.when(credentialMaterialResolver.resolveStored(credential)).thenReturn(credentialMaterial);
        Mockito.when(gatewayFileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(gatewayFileBindingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        com.google.genai.Files filesFacade = Mockito.mock(com.google.genai.Files.class);
        Client client = geminiClient(filesFacade);
        Mockito.when(geminiChatModelFactory.createClient(UpstreamSiteKind.GEMINI_DIRECT, credential.getBaseUrl(), credentialMaterial))
                .thenReturn(client);

        service.deleteFile("file-1", 1L);

        ArgumentCaptor<GatewayFileBindingEntity> bindingCaptor = ArgumentCaptor.forClass(GatewayFileBindingEntity.class);
        Mockito.verify(gatewayFileBindingRepository).save(bindingCaptor.capture());
        assertEquals("DELETED", bindingCaptor.getValue().getStatus());
        assertFalse(Files.exists(localFile));
        Mockito.verify(filesFacade).delete(Mockito.eq("files/gemini-upstream-1"), any(com.google.genai.types.DeleteFileConfig.class));
    }

    private FilePart textFilePart(String filename, String content, MediaType mediaType) {
        FilePart filePart = Mockito.mock(FilePart.class);
        Mockito.when(filePart.filename()).thenReturn(filename);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        Mockito.when(filePart.headers()).thenReturn(headers);
        Mockito.when(filePart.transferTo(any(Path.class))).thenAnswer(invocation -> {
            Path target = invocation.getArgument(0);
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return Mono.empty();
        });
        return filePart;
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

    private SiteCapabilitySnapshotEntity snapshot(
            boolean supportsFiles,
            AuthStrategy authStrategy,
            PathStrategy pathStrategy) {
        SiteCapabilitySnapshotEntity entity = new SiteCapabilitySnapshotEntity();
        entity.setSupportsFiles(supportsFiles);
        entity.setSupportedProtocols(List.of("openai"));
        entity.setAuthStrategy(authStrategy);
        entity.setPathStrategy(pathStrategy);
        entity.setErrorSchemaStrategy(pathStrategy == PathStrategy.GEMINI_V1BETA_MODELS ? ErrorSchemaStrategy.GEMINI_ERROR : ErrorSchemaStrategy.OPENAI_ERROR);
        entity.setHealthState("READY");
        return entity;
    }

    private ResolvedCredentialMaterial resolvedMaterial(Long credentialId, Long siteProfileId, String secret) {
        return resolvedMaterial(credentialId, siteProfileId, secret, Map.of());
    }

    private ResolvedCredentialMaterial resolvedMaterial(Long credentialId, Long siteProfileId, String secret, Map<String, Object> metadata) {
        return new ResolvedCredentialMaterial(
                credentialId,
                siteProfileId,
                null,
                secret,
                "fp",
                metadata,
                null,
                "credential"
        );
    }

    private Client geminiClient(com.google.genai.Files filesFacade) {
        Client client = Client.builder().apiKey("test").build();
        ReflectionTestUtils.setField(client, "files", filesFacade);
        return client;
    }

    private com.google.genai.types.File geminiFile(String name, String displayName, String mimeType, long sizeBytes) {
        com.google.genai.types.File upstreamFile = Mockito.mock(com.google.genai.types.File.class);
        Mockito.when(upstreamFile.name()).thenReturn(Optional.of(name));
        Mockito.when(upstreamFile.displayName()).thenReturn(Optional.of(displayName));
        Mockito.when(upstreamFile.mimeType()).thenReturn(Optional.of(mimeType));
        Mockito.when(upstreamFile.sizeBytes()).thenReturn(Optional.of(sizeBytes));
        Mockito.when(upstreamFile.state()).thenReturn(Optional.empty());
        return upstreamFile;
    }

    private GatewayFileEntity gatewayFileEntity(Long id, String fileKey, Path storagePath, String filename, String mimeType, Long distributedKeyId) {
        GatewayFileEntity file = new GatewayFileEntity();
        ReflectionTestUtils.setField(file, "id", id);
        ReflectionTestUtils.setField(file, "createdAt", Instant.parse("2026-04-12T04:00:00Z"));
        file.setFileKey(fileKey);
        file.setDistributedKeyId(distributedKeyId);
        file.setFilename(filename);
        file.setMimeType(mimeType);
        file.setStoragePath(storagePath.toString());
        file.setStatus("processed");
        file.setPurpose("assistants");
        file.setSizeBytes(12L);
        return file;
    }

    private GatewayFileBindingEntity geminiBinding(Long gatewayFileId, Long credentialId, Long siteProfileId, String externalFileId) {
        GatewayFileBindingEntity binding = new GatewayFileBindingEntity();
        binding.setGatewayFileId(gatewayFileId);
        binding.setCredentialId(credentialId);
        binding.setSiteProfileId(siteProfileId);
        binding.setProviderType(ProviderType.GEMINI_DIRECT);
        binding.setExternalFileId(externalFileId);
        binding.setStatus("SYNCED");
        return binding;
    }
}
