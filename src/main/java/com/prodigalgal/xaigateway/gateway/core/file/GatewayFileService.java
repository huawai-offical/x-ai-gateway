package com.prodigalgal.xaigateway.gateway.core.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Transactional
public class GatewayFileService {

    private final GatewayFileRepository gatewayFileRepository;
    private final GatewayFileBindingRepository gatewayFileBindingRepository;
    private final DistributedKeyQueryService distributedKeyQueryService;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final UpstreamSiteProfileRepository upstreamSiteProfileRepository;
    private final SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository;
    private final SiteCapabilityTruthService siteCapabilityTruthService;
    private final CredentialCryptoService credentialCryptoService;
    private final CredentialMaterialResolver credentialMaterialResolver;
    private final GatewayProperties gatewayProperties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Autowired
    public GatewayFileService(
            GatewayFileRepository gatewayFileRepository,
            GatewayFileBindingRepository gatewayFileBindingRepository,
            DistributedKeyQueryService distributedKeyQueryService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            SiteCapabilityTruthService siteCapabilityTruthService,
            CredentialCryptoService credentialCryptoService,
            CredentialMaterialResolver credentialMaterialResolver,
            GatewayProperties gatewayProperties,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {
        this.gatewayFileRepository = gatewayFileRepository;
        this.gatewayFileBindingRepository = gatewayFileBindingRepository;
        this.distributedKeyQueryService = distributedKeyQueryService;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.upstreamSiteProfileRepository = upstreamSiteProfileRepository;
        this.siteCapabilitySnapshotRepository = siteCapabilitySnapshotRepository;
        this.siteCapabilityTruthService = siteCapabilityTruthService;
        this.credentialCryptoService = credentialCryptoService;
        this.credentialMaterialResolver = credentialMaterialResolver;
        this.gatewayProperties = gatewayProperties;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    public GatewayFileService(
            GatewayFileRepository gatewayFileRepository,
            GatewayFileBindingRepository gatewayFileBindingRepository,
            DistributedKeyQueryService distributedKeyQueryService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            SiteCapabilityTruthService siteCapabilityTruthService,
            CredentialCryptoService credentialCryptoService,
            GatewayProperties gatewayProperties,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {
        this(
                gatewayFileRepository,
                gatewayFileBindingRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                siteCapabilitySnapshotRepository,
                siteCapabilityTruthService,
                credentialCryptoService,
                new CredentialMaterialResolver(new com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService(
                        null,
                        null,
                        null,
                        null
                ), credentialCryptoService, objectMapper),
                gatewayProperties,
                webClientBuilder,
                objectMapper
        );
    }

    public Mono<GatewayFileResponse> createFile(Long distributedKeyId, FilePart filePart, String purpose) {
        UpstreamFileTarget upstreamTarget = resolveUpstreamFileTarget(distributedKeyId)
                .orElseThrow(() -> new IllegalArgumentException("当前 DistributedKey 没有可用的 files 上游编排站点。"));
        String fileKey = "file-" + UUID.randomUUID().toString().replace("-", "");
        Path directory = ensureStorageDirectory();
        Path storagePath = directory.resolve(fileKey + "-" + sanitizeFilename(filePart.filename()));

        return filePart.transferTo(storagePath)
                .then(Mono.fromCallable(() -> {
                    GatewayFileEntity file = persistFile(
                            distributedKeyId,
                            fileKey,
                            storagePath,
                            filePart.filename(),
                            filePart.headers().getContentType() == null ? "application/octet-stream" : filePart.headers().getContentType().toString(),
                            purpose
                    );
                    synchronizeUpstreamFile(file, upstreamTarget);
                    return toResponse(file);
                }));
    }

    @Transactional(readOnly = true)
    public List<GatewayFileResponse> listFiles(Long distributedKeyId) {
        return gatewayFileRepository.findTop100ByDistributedKeyIdAndDeletedFalseOrderByCreatedAtDesc(distributedKeyId)
                .stream()
                .sorted(Comparator.comparing(GatewayFileEntity::getCreatedAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GatewayFileResponse getFile(String fileKey, Long distributedKeyId) {
        GatewayFileEntity entity = getRequired(fileKey, distributedKeyId);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public GatewayFileContent getFileContent(String fileKey, Long distributedKeyId) {
        GatewayFileEntity entity = getRequired(fileKey, distributedKeyId);
        try {
            byte[] bytes = Files.readAllBytes(Path.of(entity.getStoragePath()));
            return new GatewayFileContent(toResponse(entity), bytes, entity.getMimeType());
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取文件内容。", exception);
        }
    }

    @Transactional(readOnly = true)
    public GatewayFileResource resolveFileResource(String fileKey, Long distributedKeyId) {
        GatewayFileEntity entity = getRequired(fileKey, distributedKeyId);
        return new GatewayFileResource(
                entity.getFileKey(),
                entity.getMimeType(),
                entity.getFilename(),
                new FileSystemResource(Path.of(entity.getStoragePath()))
        );
    }

    public void deleteFile(String fileKey, Long distributedKeyId) {
        GatewayFileEntity entity = getRequired(fileKey, distributedKeyId);
        gatewayFileBindingRepository.findAllByGatewayFileIdOrderByCreatedAtDesc(entity.getId()).stream()
                .findFirst()
                .ifPresent(binding -> deleteUpstreamFile(binding));
        entity.setDeleted(true);
        entity.setStatus("deleted");
        gatewayFileRepository.save(entity);
        try {
            Files.deleteIfExists(Path.of(entity.getStoragePath()));
        } catch (IOException exception) {
            throw new IllegalStateException("删除本地文件失败。", exception);
        }
    }

    private void synchronizeUpstreamFile(GatewayFileEntity file, UpstreamFileTarget upstreamTarget) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(file.getStoragePath()));
            MultiValueMap<String, HttpEntity<?>> body = new LinkedMultiValueMap<>();
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.parseMediaType(file.getMimeType()));
            body.add("file", new HttpEntity<>(new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return file.getFilename();
                }
            }, fileHeaders));
            if (file.getPurpose() != null && !file.getPurpose().isBlank()) {
                HttpHeaders purposeHeaders = new HttpHeaders();
                purposeHeaders.setContentType(MediaType.TEXT_PLAIN);
                body.add("purpose", new HttpEntity<>(file.getPurpose(), purposeHeaders));
            }

            JsonNode upstreamResponse = upstreamTarget.client().post()
                    .uri(upstreamTarget.path())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            if (upstreamResponse == null || upstreamResponse.path("id").asText(null) == null) {
                throw new IllegalStateException("上游 files 响应缺少 id。");
            }

            GatewayFileBindingEntity binding = new GatewayFileBindingEntity();
            binding.setGatewayFileId(file.getId());
            binding.setProviderType(upstreamTarget.credential().getProviderType());
            binding.setCredentialId(upstreamTarget.credential().getId());
            binding.setExternalFileId(upstreamResponse.path("id").asText());
            binding.setExternalFilename(upstreamResponse.path("filename").asText(file.getFilename()));
            binding.setStatus("SYNCED");
            binding.setLastSyncedAt(Instant.now());
            gatewayFileBindingRepository.save(binding);

            file.setFilename(upstreamResponse.path("filename").asText(file.getFilename()));
            file.setPurpose(upstreamResponse.path("purpose").asText(file.getPurpose()));
            file.setStatus(upstreamResponse.path("status").asText("synced"));
            file.setSizeBytes(upstreamResponse.path("bytes").asLong(file.getSizeBytes()));
            gatewayFileRepository.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("读取本地文件失败。", exception);
        }
    }

    private void deleteUpstreamFile(GatewayFileBindingEntity binding) {
        UpstreamCredentialEntity credential = upstreamCredentialRepository.findById(binding.getCredentialId())
                .orElse(null);
        if (credential == null || credential.isDeleted() || !credential.isActive()) {
            return;
        }
        UpstreamSiteProfileEntity siteProfile = resolveSiteProfile(credential.getSiteProfileId()).orElse(null);
        if (siteProfile == null) {
            return;
        }
        try {
            SiteClientRequest request = buildSiteClient(credential, siteProfile, "/v1/files/" + binding.getExternalFileId());
            request.client().delete().uri(request.path()).retrieve().toBodilessEntity().block();
            binding.setStatus("DELETED");
            binding.setLastSyncedAt(Instant.now());
            gatewayFileBindingRepository.save(binding);
        } catch (RuntimeException ignored) {
            // 删除失败时保留本地删除结果，避免让清理过程阻塞用户请求。
        }
    }

    private Optional<UpstreamFileTarget> resolveUpstreamFileTarget(Long distributedKeyId) {
        DistributedKeyView distributedKey = distributedKeyQueryService.findActiveById(distributedKeyId)
                .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"));
        Map<Long, UpstreamCredentialEntity> credentials = new LinkedHashMap<>();
        for (UpstreamCredentialEntity credential : upstreamCredentialRepository.findAllByIdInAndDeletedFalse(
                distributedKey.bindings().stream().map(DistributedCredentialBindingView::credentialId).toList())) {
            if (credential.isActive()) {
                credentials.put(credential.getId(), credential);
            }
        }

        for (DistributedCredentialBindingView binding : distributedKey.bindings()) {
            UpstreamCredentialEntity credential = credentials.get(binding.credentialId());
            if (credential == null || credential.getSiteProfileId() == null) {
                continue;
            }
            UpstreamSiteProfileEntity siteProfile = resolveSiteProfile(credential.getSiteProfileId()).orElse(null);
            SiteCapabilitySnapshotEntity snapshot = siteCapabilitySnapshotRepository.findBySiteProfile_Id(credential.getSiteProfileId())
                    .orElse(null);
            if (siteProfile == null || !siteCapabilityTruthService.supportsFeature(siteProfile, snapshot, InteropFeature.FILE_OBJECT)) {
                continue;
            }
            return Optional.of(new UpstreamFileTarget(credential, siteProfile, buildSiteClient(credential, siteProfile, "/v1/files")));
        }
        return Optional.empty();
    }

    private Optional<UpstreamSiteProfileEntity> resolveSiteProfile(Long siteProfileId) {
        if (siteProfileId == null) {
            return Optional.empty();
        }
        return upstreamSiteProfileRepository.findById(siteProfileId);
    }

    private SiteClientRequest buildSiteClient(
            UpstreamCredentialEntity credential,
            UpstreamSiteProfileEntity siteProfile,
            String requestPath) {
        ResolvedCredentialMaterial credentialMaterial = credentialMaterialResolver.resolveStored(credential);
        WebClient.Builder builder = webClientBuilder.clone().baseUrl(credential.getBaseUrl().replaceAll("/+$", ""));
        String path = resolvePath(credential.getBaseUrl(), requestPath);
        if (siteProfile.getAuthStrategy() == AuthStrategy.BEARER) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + credentialMaterial.secret());
        } else if (siteProfile.getAuthStrategy() == AuthStrategy.API_KEY_HEADER) {
            builder.defaultHeader("x-api-key", credentialMaterial.secret());
        } else if (siteProfile.getAuthStrategy() == AuthStrategy.AZURE_API_KEY) {
            builder.defaultHeader("api-key", credentialMaterial.secret());
        } else {
            throw new IllegalArgumentException("当前站点鉴权策略不支持 files 编排。");
        }
        if (siteProfile.getPathStrategy() != PathStrategy.OPENAI_V1) {
            throw new IllegalArgumentException("当前站点路径策略不支持 files 编排。");
        }
        return new SiteClientRequest(builder.build(), path);
    }

    private GatewayFileEntity persistFile(
            Long distributedKeyId,
            String fileKey,
            Path storagePath,
            String filename,
            String mimeType,
            String purpose) {
        try {
            byte[] bytes = Files.readAllBytes(storagePath);
            GatewayFileEntity entity = new GatewayFileEntity();
            entity.setFileKey(fileKey);
            entity.setDistributedKeyId(distributedKeyId);
            entity.setFilename(filename);
            entity.setMimeType(mimeType);
            entity.setPurpose(purpose);
            entity.setSizeBytes(bytes.length);
            entity.setSha256(sha256(bytes));
            entity.setStoragePath(storagePath.toAbsolutePath().toString());
            entity.setStatus("staged_local");
            return gatewayFileRepository.save(entity);
        } catch (IOException exception) {
            throw new IllegalStateException("读取上传文件失败。", exception);
        }
    }

    private GatewayFileEntity getRequired(String fileKey, Long distributedKeyId) {
        Optional<GatewayFileEntity> entity = gatewayFileRepository.findByFileKeyAndDeletedFalse(fileKey);
        if (entity.isEmpty() || !entity.get().getDistributedKeyId().equals(distributedKeyId)) {
            throw new IllegalArgumentException("未找到指定的文件对象。");
        }
        return entity.get();
    }

    private GatewayFileResponse toResponse(GatewayFileEntity entity) {
        return GatewayFileResponse.from(
                entity.getFileKey(),
                entity.getFilename(),
                entity.getPurpose(),
                entity.getSizeBytes(),
                entity.getCreatedAt(),
                entity.getStatus()
        );
    }

    private Path ensureStorageDirectory() {
        try {
            Path root = Path.of(gatewayProperties.getStorage().getFileRoot()).toAbsolutePath();
            Files.createDirectories(root);
            return root;
        } catch (IOException exception) {
            throw new IllegalStateException("创建文件存储目录失败。", exception);
        }
    }

    public Path ensureStorageDirectoryForSync() {
        return ensureStorageDirectory();
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload.bin";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境缺少 SHA-256。", exception);
        }
    }

    private String resolvePath(String baseUrl, String requestPath) {
        String normalizedBaseUrl = baseUrl.replaceAll("/+$", "");
        String normalizedPath = requestPath.startsWith("/") ? requestPath : "/" + requestPath;
        if (normalizedBaseUrl.endsWith("/v1") && normalizedPath.startsWith("/v1/")) {
            return normalizedPath.substring(3);
        }
        return normalizedPath;
    }

    private record SiteClientRequest(
            WebClient client,
            String path
    ) {
    }

    private record UpstreamFileTarget(
            UpstreamCredentialEntity credential,
            UpstreamSiteProfileEntity siteProfile,
            SiteClientRequest request
    ) {
        private WebClient client() {
            return request.client();
        }

        private String path() {
            return request.path();
        }
    }
}
