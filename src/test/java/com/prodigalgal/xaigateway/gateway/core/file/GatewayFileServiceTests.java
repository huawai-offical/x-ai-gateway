package com.prodigalgal.xaigateway.gateway.core.file;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        Mockito.when(gatewayFileRepository.save(any())).thenAnswer(invocation -> {
            GatewayFileEntity entity = invocation.getArgument(0);
            if (entity.getCreatedAt() == null) {
                org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", 1L);
                org.springframework.test.util.ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-04-12T04:00:00Z"));
            }
            return entity;
        });
        Mockito.when(gatewayFileBindingRepository.save(any())).thenAnswer(invocation -> {
            GatewayFileBindingEntity entity = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", 10L);
            return entity;
        });

        FilePart filePart = Mockito.mock(FilePart.class);
        Mockito.when(filePart.filename()).thenReturn("demo.txt");
        Mockito.when(filePart.headers()).thenReturn(new HttpHeaders() {{
            setContentType(MediaType.TEXT_PLAIN);
        }});
        Mockito.when(filePart.transferTo(any(Path.class))).thenAnswer(invocation -> {
            Path target = invocation.getArgument(0);
            Files.writeString(target, "hello", StandardCharsets.UTF_8);
            return Mono.empty();
        });

        GatewayFileResponse response = service.createFile(1L, filePart, "assistants").block();

        assertEquals("processed", response.status());
        ArgumentCaptor<GatewayFileBindingEntity> captor = ArgumentCaptor.forClass(GatewayFileBindingEntity.class);
        Mockito.verify(gatewayFileBindingRepository).save(captor.capture());
        assertEquals("file-upstream-1", captor.getValue().getExternalFileId());
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

        GatewayFileEntity file = new GatewayFileEntity();
        org.springframework.test.util.ReflectionTestUtils.setField(file, "id", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(file, "createdAt", Instant.parse("2026-04-12T04:00:00Z"));
        file.setFileKey("file-1");
        file.setDistributedKeyId(1L);
        file.setFilename("demo.txt");
        file.setMimeType("text/plain");
        file.setStoragePath(tempDir.resolve("demo.txt").toString());
        file.setStatus("processed");

        GatewayFileBindingEntity binding = new GatewayFileBindingEntity();
        binding.setGatewayFileId(1L);
        binding.setCredentialId(101L);
        binding.setProviderType(ProviderType.OPENAI_DIRECT);
        binding.setExternalFileId("file-upstream-1");
        binding.setStatus("SYNCED");

        UpstreamCredentialEntity credential = credential();

        Mockito.when(gatewayFileRepository.findByFileKeyAndDeletedFalse("file-1")).thenReturn(Optional.of(file));
        Mockito.when(gatewayFileBindingRepository.findAllByGatewayFileIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(binding));
        Mockito.when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(credential));
        Mockito.when(upstreamSiteProfileRepository.findById(1L)).thenReturn(Optional.of(siteProfile()));
        Mockito.when(credentialCryptoService.decrypt("cipher")).thenReturn("api-key");

        service.deleteFile("file-1", 1L);

        assertEquals("DELETE", method.get());
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
        entity.setSupportsFiles(true);
        entity.setSupportedProtocols(List.of("openai"));
        entity.setAuthStrategy(AuthStrategy.BEARER);
        entity.setPathStrategy(PathStrategy.OPENAI_V1);
        entity.setErrorSchemaStrategy(ErrorSchemaStrategy.OPENAI_ERROR);
        entity.setHealthState("READY");
        return entity;
    }
}
