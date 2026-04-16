package com.prodigalgal.xaigateway.gateway.core.file;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.HttpResponse;
import com.anthropic.models.beta.files.FileDeleteParams;
import com.anthropic.models.beta.files.FileDownloadParams;
import com.anthropic.models.beta.files.FileMetadata;
import com.anthropic.models.beta.files.FileRetrieveMetadataParams;
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
import com.prodigalgal.xaigateway.provider.adapter.anthropic.AnthropicChatModelFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;

class GatewayFileServiceAnthropicTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateAnthropicFileBinding() {
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        AnthropicChatModelFactory anthropicChatModelFactory = Mockito.mock(AnthropicChatModelFactory.class);

        GatewayFileService service = new GatewayFileService(
                gatewayFileRepository,
                gatewayFileBindingRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                Mockito.mock(CredentialCryptoService.class),
                credentialMaterialResolver,
                anthropicChatModelFactory,
                Mockito.mock(com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory.class),
                properties(),
                WebClient.builder(),
                new ObjectMapper()
        );

        UpstreamCredentialEntity credential = credential(501L, 5L, "https://api.anthropic.com");
        UpstreamSiteProfileEntity siteProfile = anthropicSiteProfile(5L);
        ResolvedCredentialMaterial material = resolvedMaterial(501L, 5L, "anthropic-key");

        Mockito.when(distributedKeyQueryService.findActiveById(1L))
                .thenReturn(Optional.of(distributedKey(501L, credential.getBaseUrl())));
        Mockito.when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(List.of(501L)))
                .thenReturn(List.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(5L)).thenReturn(Optional.of(siteProfile));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(5L))
                .thenReturn(Optional.of(snapshot(true)));
        Mockito.when(credentialMaterialResolver.resolveStored(credential)).thenReturn(material);
        Mockito.when(gatewayFileRepository.save(any())).thenAnswer(invocation -> {
            GatewayFileEntity entity = invocation.getArgument(0);
            if (entity.getCreatedAt() == null) {
                ReflectionTestUtils.setField(entity, "id", 21L);
                ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-04-16T01:00:00Z"));
            }
            return entity;
        });
        Mockito.when(gatewayFileBindingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AnthropicClient client = Mockito.mock(AnthropicClient.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(anthropicChatModelFactory.createClient(credential.getBaseUrl(), material.secret())).thenReturn(client);
        FileMetadata upstreamFile = anthropicFile("file_beta_1", "demo.txt", "text/plain", 5L, true);
        Mockito.when(client.beta().files().upload(any())).thenReturn(upstreamFile);

        GatewayFileResponse response = service.createFile(1L, textFilePart("demo.txt", "hello", MediaType.TEXT_PLAIN), "assistants").block();

        assertEquals("processed", response.status());
        ArgumentCaptor<GatewayFileBindingEntity> captor = ArgumentCaptor.forClass(GatewayFileBindingEntity.class);
        Mockito.verify(gatewayFileBindingRepository).save(captor.capture());
        assertEquals("file_beta_1", captor.getValue().getExternalFileId());
        assertEquals(5L, captor.getValue().getSiteProfileId());
    }

    @Test
    void shouldReadAnthropicFileContentUsingUpstreamBinding() throws Exception {
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        AnthropicChatModelFactory anthropicChatModelFactory = Mockito.mock(AnthropicChatModelFactory.class);

        GatewayFileService service = new GatewayFileService(
                gatewayFileRepository,
                gatewayFileBindingRepository,
                Mockito.mock(DistributedKeyQueryService.class),
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                Mockito.mock(CredentialCryptoService.class),
                credentialMaterialResolver,
                anthropicChatModelFactory,
                Mockito.mock(com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory.class),
                properties(),
                WebClient.builder(),
                new ObjectMapper()
        );

        Path localFile = tempDir.resolve("local.txt");
        Files.writeString(localFile, "local", StandardCharsets.UTF_8);
        GatewayFileEntity file = gatewayFileEntity(22L, "file-1", localFile, "demo.txt", "text/plain", 1L);
        GatewayFileBindingEntity binding = anthropicBinding(22L, 501L, 5L, "file_beta_1");
        UpstreamCredentialEntity credential = credential(501L, 5L, "https://api.anthropic.com");
        UpstreamSiteProfileEntity siteProfile = anthropicSiteProfile(5L);
        ResolvedCredentialMaterial material = resolvedMaterial(501L, 5L, "anthropic-key");

        Mockito.when(gatewayFileRepository.findByFileKeyAndDeletedFalse("file-1")).thenReturn(Optional.of(file));
        Mockito.when(gatewayFileBindingRepository.findAllByGatewayFileIdOrderByCreatedAtDesc(22L)).thenReturn(List.of(binding));
        Mockito.when(upstreamCredentialRepository.findById(501L)).thenReturn(Optional.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(5L)).thenReturn(Optional.of(siteProfile));
        Mockito.when(credentialMaterialResolver.resolveStored(credential)).thenReturn(material);
        Mockito.when(gatewayFileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AnthropicClient client = Mockito.mock(AnthropicClient.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(anthropicChatModelFactory.createClient(credential.getBaseUrl(), material.secret())).thenReturn(client);
        FileMetadata upstreamFile = anthropicFile("file_beta_1", "demo.txt", "text/plain", 14L, true);
        HttpResponse response = Mockito.mock(HttpResponse.class);
        Mockito.when(response.body()).thenReturn(new ByteArrayInputStream("remote-content".getBytes(StandardCharsets.UTF_8)));
        Mockito.when(client.beta().files().retrieveMetadata(any(String.class), any(FileRetrieveMetadataParams.class))).thenReturn(upstreamFile);
        Mockito.when(client.beta().files().download(any(String.class), any(FileDownloadParams.class))).thenReturn(response);

        GatewayFileContent content = service.getFileContent("file-1", 1L);

        assertEquals("text/plain", content.mimeType());
        assertArrayEquals("remote-content".getBytes(StandardCharsets.UTF_8), content.bytes());
    }

    @Test
    void shouldDeleteAnthropicUpstreamFileBeforeLocalDelete() throws Exception {
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        AnthropicChatModelFactory anthropicChatModelFactory = Mockito.mock(AnthropicChatModelFactory.class);

        GatewayFileService service = new GatewayFileService(
                gatewayFileRepository,
                gatewayFileBindingRepository,
                Mockito.mock(DistributedKeyQueryService.class),
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                snapshotRepository,
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                Mockito.mock(CredentialCryptoService.class),
                credentialMaterialResolver,
                anthropicChatModelFactory,
                Mockito.mock(com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory.class),
                properties(),
                WebClient.builder(),
                new ObjectMapper()
        );

        Path localFile = tempDir.resolve("delete-me.txt");
        Files.writeString(localFile, "hello", StandardCharsets.UTF_8);
        GatewayFileEntity file = gatewayFileEntity(23L, "file-1", localFile, "delete-me.txt", "text/plain", 1L);
        GatewayFileBindingEntity binding = anthropicBinding(23L, 501L, 5L, "file_beta_1");
        UpstreamCredentialEntity credential = credential(501L, 5L, "https://api.anthropic.com");
        UpstreamSiteProfileEntity siteProfile = anthropicSiteProfile(5L);
        ResolvedCredentialMaterial material = resolvedMaterial(501L, 5L, "anthropic-key");

        Mockito.when(gatewayFileRepository.findByFileKeyAndDeletedFalse("file-1")).thenReturn(Optional.of(file));
        Mockito.when(gatewayFileBindingRepository.findAllByGatewayFileIdOrderByCreatedAtDesc(23L)).thenReturn(List.of(binding));
        Mockito.when(upstreamCredentialRepository.findById(501L)).thenReturn(Optional.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(5L)).thenReturn(Optional.of(siteProfile));
        Mockito.when(credentialMaterialResolver.resolveStored(credential)).thenReturn(material);
        Mockito.when(gatewayFileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(gatewayFileBindingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AnthropicClient client = Mockito.mock(AnthropicClient.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(anthropicChatModelFactory.createClient(credential.getBaseUrl(), material.secret())).thenReturn(client);

        service.deleteFile("file-1", 1L);

        ArgumentCaptor<GatewayFileBindingEntity> captor = ArgumentCaptor.forClass(GatewayFileBindingEntity.class);
        Mockito.verify(gatewayFileBindingRepository).save(captor.capture());
        assertEquals("DELETED", captor.getValue().getStatus());
        assertFalse(Files.exists(localFile));
        Mockito.verify(client.beta().files()).delete(any(String.class), any(FileDeleteParams.class));
    }

    private GatewayProperties properties() {
        GatewayProperties properties = new GatewayProperties();
        properties.getStorage().setFileRoot(tempDir.toString());
        return properties;
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

    private DistributedKeyView distributedKey(Long credentialId, String baseUrl) {
        return new DistributedKeyView(
                1L,
                "test",
                "sk-gw-test",
                "masked",
                List.of(ProviderType.ANTHROPIC_DIRECT.name().toLowerCase()),
                List.of(),
                List.of(new DistributedCredentialBindingView(1L, credentialId, ProviderType.ANTHROPIC_DIRECT.name().toLowerCase(), ProviderType.ANTHROPIC_DIRECT, baseUrl, 10, 100))
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
        entity.setAuthStrategy(AuthStrategy.BEARER);
        entity.setPathStrategy(PathStrategy.ANTHROPIC_V1_MESSAGES);
        entity.setErrorSchemaStrategy(ErrorSchemaStrategy.ANTHROPIC_ERROR);
        entity.setActive(true);
        return entity;
    }

    private SiteCapabilitySnapshotEntity snapshot(boolean supportsFiles) {
        SiteCapabilitySnapshotEntity entity = new SiteCapabilitySnapshotEntity();
        entity.setSupportsFiles(supportsFiles);
        entity.setSupportedProtocols(List.of("anthropic_native"));
        entity.setAuthStrategy(AuthStrategy.BEARER);
        entity.setPathStrategy(PathStrategy.ANTHROPIC_V1_MESSAGES);
        entity.setErrorSchemaStrategy(ErrorSchemaStrategy.ANTHROPIC_ERROR);
        entity.setHealthState("READY");
        return entity;
    }

    private ResolvedCredentialMaterial resolvedMaterial(Long credentialId, Long siteProfileId, String secret) {
        return new ResolvedCredentialMaterial(credentialId, siteProfileId, null, secret, "fp", java.util.Map.of(), null, "credential");
    }

    private FileMetadata anthropicFile(String id, String filename, String mimeType, long sizeBytes, boolean downloadable) {
        FileMetadata file = Mockito.mock(FileMetadata.class);
        Mockito.when(file.id()).thenReturn(id);
        Mockito.when(file.filename()).thenReturn(filename);
        Mockito.when(file.mimeType()).thenReturn(mimeType);
        Mockito.when(file.sizeBytes()).thenReturn(sizeBytes);
        Mockito.when(file.downloadable()).thenReturn(Optional.of(downloadable));
        return file;
    }

    private GatewayFileEntity gatewayFileEntity(Long id, String fileKey, Path storagePath, String filename, String mimeType, Long distributedKeyId) {
        GatewayFileEntity file = new GatewayFileEntity();
        ReflectionTestUtils.setField(file, "id", id);
        ReflectionTestUtils.setField(file, "createdAt", Instant.parse("2026-04-16T01:00:00Z"));
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

    private GatewayFileBindingEntity anthropicBinding(Long gatewayFileId, Long credentialId, Long siteProfileId, String externalFileId) {
        GatewayFileBindingEntity binding = new GatewayFileBindingEntity();
        binding.setGatewayFileId(gatewayFileId);
        binding.setCredentialId(credentialId);
        binding.setSiteProfileId(siteProfileId);
        binding.setProviderType(ProviderType.ANTHROPIC_DIRECT);
        binding.setExternalFileId(externalFileId);
        binding.setStatus("SYNCED");
        return binding;
    }
}
