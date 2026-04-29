package com.prodigalgal.xaigateway.gateway.core.file;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.HttpResponse;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.files.FileDeleteParams;
import com.anthropic.models.beta.files.FileDownloadParams;
import com.anthropic.models.beta.files.FileMetadata;
import com.anthropic.models.beta.files.FileRetrieveMetadataParams;
import com.anthropic.models.beta.files.FileUploadParams;
import com.google.genai.Client;
import com.google.genai.types.DeleteFileConfig;
import com.google.genai.types.DownloadFileConfig;
import com.google.genai.types.FileState;
import com.google.genai.types.GetFileConfig;
import com.google.genai.types.UploadFileConfig;
import io.micrometer.observation.ObservationRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
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
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory;
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
import java.util.Set;
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
    private final AnthropicChatModelFactory anthropicChatModelFactory;
    private final GeminiChatModelFactory geminiChatModelFactory;
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
            AnthropicChatModelFactory anthropicChatModelFactory,
            GeminiChatModelFactory geminiChatModelFactory,
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
        this.anthropicChatModelFactory = anthropicChatModelFactory;
        this.geminiChatModelFactory = geminiChatModelFactory;
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
            CredentialMaterialResolver credentialMaterialResolver,
            GeminiChatModelFactory geminiChatModelFactory,
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
                credentialMaterialResolver,
                new AnthropicChatModelFactory(ObservationRegistry.NOOP),
                geminiChatModelFactory,
                gatewayProperties,
                webClientBuilder,
                objectMapper
        );
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
                new AnthropicChatModelFactory(ObservationRegistry.NOOP),
                new GeminiChatModelFactory(ObservationRegistry.NOOP),
                gatewayProperties,
                webClientBuilder,
                objectMapper
        );
    }

    public Mono<GatewayFileResponse> createFile(Long distributedKeyId, FilePart filePart, String purpose) {
        return createFile(distributedKeyId, filePart, purpose, null);
    }

    public GatewayFileResponse createFileFromBytes(
            Long distributedKeyId,
            String filename,
            String mimeType,
            String purpose,
            byte[] bytes) {
        return createFileFromBytes(distributedKeyId, filename, mimeType, purpose, bytes, null);
    }

    public GatewayFileResponse createFileFromBytes(
            Long distributedKeyId,
            String filename,
            String mimeType,
            String purpose,
            byte[] bytes,
            Long preferredCredentialId) {
        UpstreamFileTarget upstreamTarget = resolveUpstreamFileTarget(distributedKeyId, preferredCredentialId)
                .orElseThrow(() -> new IllegalArgumentException("当前 DistributedKey 没有可用的 files 上游编排站点。"));
        Path directory = ensureStorageDirectory();
        String safeFilename = sanitizeFilename(filename);
        String fileKey = "file-" + UUID.randomUUID().toString().replace("-", "");
        Path storagePath = directory.resolve(fileKey + "-" + safeFilename);
        try {
            Files.write(storagePath, bytes == null ? new byte[0] : bytes);
            GatewayFileEntity file = persistFile(
                    distributedKeyId,
                    fileKey,
                    storagePath,
                    filename == null || filename.isBlank() ? safeFilename : filename.trim(),
                    mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType.trim(),
                    purpose
            );
            synchronizeUpstreamFile(file, upstreamTarget);
            return toResponse(file);
        } catch (IOException exception) {
            throw new IllegalStateException("写入网关文件失败。", exception);
        }
    }

    public Mono<GatewayFileResponse> createFile(Long distributedKeyId, FilePart filePart, String purpose, Long preferredCredentialId) {
        UpstreamFileTarget upstreamTarget = resolveUpstreamFileTarget(distributedKeyId, preferredCredentialId)
                .orElseThrow(() -> new IllegalArgumentException("当前 DistributedKey 没有可用的 files 上游编排站点。"));
        return createFile(distributedKeyId, filePart, purpose, upstreamTarget, filePart.filename());
    }

    public Mono<GoogleNativeFileView> createGoogleNativeFile(
            Long distributedKeyId,
            FilePart filePart,
            String purpose,
            String displayName) {
        UpstreamFileTarget upstreamTarget = resolveGoogleNativeFileTarget(distributedKeyId)
                .orElseThrow(() -> new IllegalArgumentException("当前 DistributedKey 没有可用的 Google files 上游站点。"));
        String targetFilename = sanitizeGoogleDisplayName(displayName, filePart.filename());
        return createFile(distributedKeyId, filePart, purpose, upstreamTarget, targetFilename)
                .map(response -> getGoogleNativeFileByGatewayFileKey(response.id(), distributedKeyId));
    }

    private Mono<GatewayFileResponse> createFile(
            Long distributedKeyId,
            FilePart filePart,
            String purpose,
            UpstreamFileTarget upstreamTarget,
            String targetFilename) {
        String fileKey = "file-" + UUID.randomUUID().toString().replace("-", "");
        Path directory = ensureStorageDirectory();
        Path storagePath = directory.resolve(fileKey + "-" + sanitizeFilename(filePart.filename()));

        return filePart.transferTo(storagePath)
                .then(Mono.fromCallable(() -> {
                    GatewayFileEntity file = persistFile(
                            distributedKeyId,
                            fileKey,
                            storagePath,
                            targetFilename,
                            filePart.headers().getContentType() == null ? "application/octet-stream" : filePart.headers().getContentType().toString(),
                            purpose
                    );
                    synchronizeUpstreamFile(file, upstreamTarget);
                    return toResponse(file);
                }));
    }

    public GatewayFileResponse createFileFromExisting(Long distributedKeyId, String sourceFileKey, String purpose) {
        return createFileFromExisting(distributedKeyId, sourceFileKey, purpose, null);
    }

    public GatewayFileResponse createFileFromExisting(Long distributedKeyId, String sourceFileKey, String purpose, Long preferredCredentialId) {
        UpstreamFileTarget upstreamTarget = resolveUpstreamFileTarget(distributedKeyId, preferredCredentialId)
                .orElseThrow(() -> new IllegalArgumentException("当前 DistributedKey 没有可用的 files 上游编排站点。"));
        GatewayFileEntity source = getRequired(sourceFileKey, distributedKeyId);
        String fileKey = "file-" + UUID.randomUUID().toString().replace("-", "");
        Path directory = ensureStorageDirectory();
        Path storagePath = directory.resolve(fileKey + "-" + sanitizeFilename(source.getFilename()));
        try {
            Files.copy(Path.of(source.getStoragePath()), storagePath);
            GatewayFileEntity file = persistFile(
                    distributedKeyId,
                    fileKey,
                    storagePath,
                    source.getFilename(),
                    source.getMimeType(),
                    purpose == null || purpose.isBlank() ? source.getPurpose() : purpose
            );
            synchronizeUpstreamFile(file, upstreamTarget);
            return toResponse(file);
        } catch (IOException exception) {
            throw new IllegalStateException("复制网关文件失败。", exception);
        }
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
    public List<GoogleNativeFileView> listGoogleNativeFiles(Long distributedKeyId) {
        Set<Long> activeCredentialIds = activeCredentialIds(distributedKeyId);
        return gatewayFileRepository.findTop100ByDistributedKeyIdAndDeletedFalseOrderByCreatedAtDesc(distributedKeyId)
                .stream()
                .map(file -> latestAccessibleGoogleBinding(file.getId(), activeCredentialIds)
                        .map(binding -> toGoogleNativeView(file, binding)))
                .flatMap(Optional::stream)
                .toList();
    }

    public GatewayFileResponse getFile(String fileKey, Long distributedKeyId) {
        GatewayFileEntity entity = getRequired(fileKey, distributedKeyId);
        latestBinding(entity.getId()).ifPresent(binding -> refreshUpstreamFile(entity, binding));
        return toResponse(entity);
    }

    public GoogleNativeFileView getGoogleNativeFile(String externalFileId, Long distributedKeyId) {
        GoogleBindingResolution resolution = resolveGoogleBinding(externalFileId, distributedKeyId);
        refreshUpstreamFile(resolution.entity(), resolution.binding());
        return toGoogleNativeView(resolution.entity(), resolution.binding());
    }

    public GoogleNativeFileView getGoogleNativeFileByGatewayFileKey(String fileKey, Long distributedKeyId) {
        GatewayFileEntity entity = getRequired(fileKey, distributedKeyId);
        GatewayFileBindingEntity binding = latestAccessibleGoogleBinding(entity.getId(), activeCredentialIds(distributedKeyId))
                .orElseThrow(() -> new IllegalArgumentException("未找到指定文件对应的 Google native 绑定。"));
        refreshUpstreamFile(entity, binding);
        return toGoogleNativeView(entity, binding);
    }

    public GatewayFileContent getFileContent(String fileKey, Long distributedKeyId) {
        GatewayFileEntity entity = getRequired(fileKey, distributedKeyId);
        return latestBinding(entity.getId())
                .map(binding -> readUpstreamFileContent(entity, binding))
                .orElseGet(() -> readLocalFileContent(entity));
    }

    @Transactional(readOnly = true)
    public String resolveGatewayFileKeyByGoogleFileName(String externalFileId, Long distributedKeyId) {
        return resolveGoogleBinding(externalFileId, distributedKeyId).entity().getFileKey();
    }

    @Transactional(readOnly = true)
    public Long resolveGoogleCredentialIdForFileName(String externalFileId, Long distributedKeyId) {
        return resolveGoogleBinding(externalFileId, distributedKeyId).binding().getCredentialId();
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveAnthropicExternalFileId(String fileKey, Long distributedKeyId) {
        GatewayFileEntity entity = getRequired(fileKey, distributedKeyId);
        return latestAccessibleAnthropicBinding(entity.getId(), activeCredentialIds(distributedKeyId))
                .map(GatewayFileBindingEntity::getExternalFileId);
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
        latestBinding(entity.getId()).ifPresent(this::deleteUpstreamFile);
        entity.setDeleted(true);
        entity.setStatus("deleted");
        gatewayFileRepository.save(entity);
        try {
            Files.deleteIfExists(Path.of(entity.getStoragePath()));
        } catch (IOException exception) {
            throw new IllegalStateException("删除本地文件失败。", exception);
        }
    }

    public void deleteGoogleNativeFile(String externalFileId, Long distributedKeyId) {
        deleteFile(resolveGatewayFileKeyByGoogleFileName(externalFileId, distributedKeyId), distributedKeyId);
    }

    private void synchronizeUpstreamFile(GatewayFileEntity file, UpstreamFileTarget upstreamTarget) {
        if (supportsGoogleGenAiFiles(upstreamTarget.siteProfile().getSiteKind())) {
            synchronizeGeminiFile(file, upstreamTarget);
            return;
        }
        if (supportsAnthropicFiles(upstreamTarget.siteProfile().getSiteKind())) {
            synchronizeAnthropicFile(file, upstreamTarget);
            return;
        }
        synchronizeOpenAiStyleFile(file, upstreamTarget);
    }

    private void synchronizeOpenAiStyleFile(GatewayFileEntity file, UpstreamFileTarget upstreamTarget) {
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
            binding.setSiteProfileId(upstreamTarget.siteProfile().getId());
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

    private void synchronizeGeminiFile(GatewayFileEntity file, UpstreamFileTarget upstreamTarget) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(file.getStoragePath()));
            UploadFileConfig config = UploadFileConfig.builder()
                    .mimeType(file.getMimeType())
                    .displayName(file.getFilename())
                    .build();
            try (Client client = geminiChatModelFactory.createClient(
                    upstreamTarget.siteProfile().getSiteKind(),
                    upstreamTarget.credential().getBaseUrl(),
                    upstreamTarget.credentialMaterial()
            )) {
                com.google.genai.types.File upstreamFile = client.files.upload(bytes, config);
                saveGeminiBinding(file, upstreamTarget, upstreamFile);
                refreshGeminiFileEntity(file, upstreamFile);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("读取本地文件失败。", exception);
        }
    }

    private void saveGeminiBinding(
            GatewayFileEntity file,
            UpstreamFileTarget upstreamTarget,
            com.google.genai.types.File upstreamFile) {
        String externalFileId = upstreamFile.name()
                .orElseThrow(() -> new IllegalStateException("Gemini files.upload 响应缺少 name。"));
        GatewayFileBindingEntity binding = new GatewayFileBindingEntity();
        binding.setGatewayFileId(file.getId());
        binding.setProviderType(upstreamTarget.credential().getProviderType());
        binding.setCredentialId(upstreamTarget.credential().getId());
        binding.setSiteProfileId(upstreamTarget.siteProfile().getId());
        binding.setExternalFileId(externalFileId);
        binding.setExternalFilename(upstreamFile.displayName().orElse(file.getFilename()));
        binding.setStatus(geminiBindingStatus(upstreamFile));
        binding.setLastSyncedAt(Instant.now());
        gatewayFileBindingRepository.save(binding);
    }

    private void synchronizeAnthropicFile(GatewayFileEntity file, UpstreamFileTarget upstreamTarget) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(file.getStoragePath()));
            FileUploadParams params = FileUploadParams.builder()
                    .file(bytes)
                    .addBeta(AnthropicBeta.FILES_API_2025_04_14)
                    .build();
            AnthropicClient client = anthropicChatModelFactory.createClient(
                    upstreamTarget.credential().getBaseUrl(),
                    upstreamTarget.credentialMaterial().secret()
            );
            try {
                FileMetadata upstreamFile = client.beta().files().upload(params);
                saveAnthropicBinding(file, upstreamTarget, upstreamFile);
                refreshAnthropicFileEntity(file, upstreamFile);
            } finally {
                client.close();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("读取本地文件失败。", exception);
        }
    }

    private void saveAnthropicBinding(
            GatewayFileEntity file,
            UpstreamFileTarget upstreamTarget,
            FileMetadata upstreamFile) {
        GatewayFileBindingEntity binding = new GatewayFileBindingEntity();
        binding.setGatewayFileId(file.getId());
        binding.setProviderType(upstreamTarget.credential().getProviderType());
        binding.setCredentialId(upstreamTarget.credential().getId());
        binding.setSiteProfileId(upstreamTarget.siteProfile().getId());
        binding.setExternalFileId(upstreamFile.id());
        binding.setExternalFilename(upstreamFile.filename());
        binding.setStatus(anthropicBindingStatus(upstreamFile));
        binding.setLastSyncedAt(Instant.now());
        gatewayFileBindingRepository.save(binding);
    }

    private void refreshUpstreamFile(GatewayFileEntity entity, GatewayFileBindingEntity binding) {
        BindingContext bindingContext = resolveBindingContext(binding).orElse(null);
        if (bindingContext == null) {
            return;
        }
        if (supportsGoogleGenAiFiles(bindingContext.siteProfile().getSiteKind())) {
            try (Client client = geminiChatModelFactory.createClient(
                    bindingContext.siteProfile().getSiteKind(),
                    bindingContext.credential().getBaseUrl(),
                    bindingContext.credentialMaterial()
            )) {
                com.google.genai.types.File upstreamFile = client.files.get(
                        binding.getExternalFileId(),
                        GetFileConfig.builder().build()
                );
                refreshGeminiFileEntity(entity, upstreamFile);
                binding.setExternalFilename(upstreamFile.displayName().orElse(binding.getExternalFilename()));
                binding.setStatus(geminiBindingStatus(upstreamFile));
                binding.setLastSyncedAt(Instant.now());
                gatewayFileBindingRepository.save(binding);
            } catch (RuntimeException ignored) {
                // 刷新失败时保留本地结果，避免 metadata 查询阻塞读取。
            }
            return;
        }
        if (supportsAnthropicFiles(bindingContext.siteProfile().getSiteKind())) {
            AnthropicClient client = anthropicChatModelFactory.createClient(
                    bindingContext.credential().getBaseUrl(),
                    bindingContext.credentialMaterial().secret()
            );
            try {
                FileMetadata upstreamFile = client.beta().files().retrieveMetadata(
                        binding.getExternalFileId(),
                        FileRetrieveMetadataParams.builder()
                                .addBeta(AnthropicBeta.FILES_API_2025_04_14)
                                .build()
                );
                refreshAnthropicFileEntity(entity, upstreamFile);
                binding.setExternalFilename(upstreamFile.filename());
                binding.setStatus(anthropicBindingStatus(upstreamFile));
                binding.setLastSyncedAt(Instant.now());
                gatewayFileBindingRepository.save(binding);
            } catch (RuntimeException ignored) {
                // 刷新失败时保留本地结果，避免 metadata 查询阻塞读取。
            } finally {
                client.close();
            }
        }
    }

    private GatewayFileContent readUpstreamFileContent(GatewayFileEntity entity, GatewayFileBindingEntity binding) {
        BindingContext bindingContext = resolveBindingContext(binding).orElse(null);
        if (bindingContext == null) {
            return readLocalFileContent(entity);
        }
        Path tempFile = null;
        try {
            if (supportsGoogleGenAiFiles(bindingContext.siteProfile().getSiteKind())) {
                try (Client client = geminiChatModelFactory.createClient(
                        bindingContext.siteProfile().getSiteKind(),
                        bindingContext.credential().getBaseUrl(),
                        bindingContext.credentialMaterial()
                )) {
                    com.google.genai.types.File upstreamFile = client.files.get(
                            binding.getExternalFileId(),
                            GetFileConfig.builder().build()
                    );
                    refreshGeminiFileEntity(entity, upstreamFile);
                    tempFile = Files.createTempFile(ensureStorageDirectory(), "gemini-file-", ".bin");
                    client.files.download(
                            binding.getExternalFileId(),
                            tempFile.toString(),
                            DownloadFileConfig.builder().build()
                    );
                    byte[] bytes = Files.readAllBytes(tempFile);
                    String mimeType = upstreamFile.mimeType().orElse(entity.getMimeType());
                    return new GatewayFileContent(
                            toResponse(entity),
                            bytes,
                            mimeType == null || mimeType.isBlank() ? MediaType.APPLICATION_OCTET_STREAM_VALUE : mimeType
                    );
                }
            }
            if (supportsAnthropicFiles(bindingContext.siteProfile().getSiteKind())) {
                AnthropicClient client = anthropicChatModelFactory.createClient(
                        bindingContext.credential().getBaseUrl(),
                        bindingContext.credentialMaterial().secret()
                );
                try {
                    FileMetadata upstreamFile = client.beta().files().retrieveMetadata(
                            binding.getExternalFileId(),
                            FileRetrieveMetadataParams.builder()
                                    .addBeta(AnthropicBeta.FILES_API_2025_04_14)
                                    .build()
                    );
                    refreshAnthropicFileEntity(entity, upstreamFile);
                    try (HttpResponse response = client.beta().files().download(
                            binding.getExternalFileId(),
                            FileDownloadParams.builder()
                                    .addBeta(AnthropicBeta.FILES_API_2025_04_14)
                                    .build()
                    )) {
                        byte[] bytes = response.body().readAllBytes();
                        String mimeType = upstreamFile.mimeType();
                        return new GatewayFileContent(
                                toResponse(entity),
                                bytes,
                                mimeType == null || mimeType.isBlank() ? MediaType.APPLICATION_OCTET_STREAM_VALUE : mimeType
                        );
                    }
                } finally {
                    client.close();
                }
            }
            return readLocalFileContent(entity);
        } catch (IOException | RuntimeException exception) {
            return readLocalFileContent(entity);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private GatewayFileContent readLocalFileContent(GatewayFileEntity entity) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(entity.getStoragePath()));
            return new GatewayFileContent(toResponse(entity), bytes, entity.getMimeType());
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取文件内容。", exception);
        }
    }

    private void deleteUpstreamFile(GatewayFileBindingEntity binding) {
        BindingContext bindingContext = resolveBindingContext(binding).orElse(null);
        if (bindingContext == null) {
            return;
        }
        try {
            if (supportsGoogleGenAiFiles(bindingContext.siteProfile().getSiteKind())) {
                try (Client client = geminiChatModelFactory.createClient(
                        bindingContext.siteProfile().getSiteKind(),
                        bindingContext.credential().getBaseUrl(),
                        bindingContext.credentialMaterial()
                )) {
                    client.files.delete(binding.getExternalFileId(), DeleteFileConfig.builder().build());
                }
            } else if (supportsAnthropicFiles(bindingContext.siteProfile().getSiteKind())) {
                AnthropicClient client = anthropicChatModelFactory.createClient(
                        bindingContext.credential().getBaseUrl(),
                        bindingContext.credentialMaterial().secret()
                );
                try {
                    client.beta().files().delete(
                            binding.getExternalFileId(),
                            FileDeleteParams.builder()
                                    .addBeta(AnthropicBeta.FILES_API_2025_04_14)
                                    .build()
                    );
                } finally {
                    client.close();
                }
            } else {
                SiteClientRequest request = buildSiteClient(bindingContext.credential(), bindingContext.siteProfile(), "/v1/files/" + binding.getExternalFileId());
                request.client().delete().uri(request.path()).retrieve().toBodilessEntity().block();
            }
            binding.setStatus("DELETED");
            binding.setLastSyncedAt(Instant.now());
            gatewayFileBindingRepository.save(binding);
        } catch (RuntimeException ignored) {
            // 删除失败时保留本地删除结果，避免让清理过程阻塞用户请求。
        }
    }

    private void refreshGeminiFileEntity(GatewayFileEntity entity, com.google.genai.types.File upstreamFile) {
        entity.setFilename(upstreamFile.displayName().orElse(entity.getFilename()));
        entity.setMimeType(upstreamFile.mimeType().orElse(entity.getMimeType()));
        entity.setStatus(geminiFileStatus(upstreamFile));
        entity.setSizeBytes(upstreamFile.sizeBytes().orElse(entity.getSizeBytes()));
        gatewayFileRepository.save(entity);
    }

    private void refreshAnthropicFileEntity(GatewayFileEntity entity, FileMetadata upstreamFile) {
        entity.setFilename(upstreamFile.filename());
        entity.setMimeType(upstreamFile.mimeType());
        entity.setStatus(anthropicFileStatus(upstreamFile));
        entity.setSizeBytes(upstreamFile.sizeBytes());
        gatewayFileRepository.save(entity);
    }

    private String geminiFileStatus(com.google.genai.types.File upstreamFile) {
        FileState.Known state = upstreamFile.state().map(FileState::knownEnum).orElse(null);
        if (state == null) {
            return "uploaded";
        }
        return switch (state) {
            case ACTIVE -> "processed";
            case PROCESSING -> "processing";
            case FAILED -> "failed";
            case STATE_UNSPECIFIED, FILE_STATE_UNSPECIFIED -> "uploaded";
        };
    }

    private String geminiBindingStatus(com.google.genai.types.File upstreamFile) {
        FileState.Known state = upstreamFile.state().map(FileState::knownEnum).orElse(null);
        if (state == null) {
            return "SYNCED";
        }
        return switch (state) {
            case ACTIVE -> "ACTIVE";
            case PROCESSING -> "PROCESSING";
            case FAILED -> "FAILED";
            case STATE_UNSPECIFIED, FILE_STATE_UNSPECIFIED -> "SYNCED";
        };
    }

    private String anthropicFileStatus(FileMetadata upstreamFile) {
        return upstreamFile.downloadable().orElse(false) ? "processed" : "uploaded";
    }

    private String anthropicBindingStatus(FileMetadata upstreamFile) {
        return upstreamFile.downloadable().orElse(false) ? "ACTIVE" : "SYNCED";
    }

    private Optional<UpstreamFileTarget> resolveUpstreamFileTarget(Long distributedKeyId) {
        return resolveUpstreamFileTarget(distributedKeyId, null);
    }

    private Optional<UpstreamFileTarget> resolveUpstreamFileTarget(Long distributedKeyId, Long preferredCredentialId) {
        DistributedKeyView distributedKey = distributedKeyQueryService.findActiveById(distributedKeyId)
                .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"));
        Map<Long, UpstreamCredentialEntity> credentials = activeCredentials(distributedKey);

        if (preferredCredentialId != null) {
            UpstreamCredentialEntity preferred = credentials.get(preferredCredentialId);
            if (preferred != null && preferred.getSiteProfileId() != null) {
                UpstreamSiteProfileEntity siteProfile = resolveSiteProfile(preferred.getSiteProfileId()).orElse(null);
                SiteCapabilitySnapshotEntity snapshot = siteCapabilitySnapshotRepository.findBySiteProfile_Id(preferred.getSiteProfileId())
                        .orElse(null);
                if (siteProfile != null && siteCapabilityTruthService.supportsFeature(siteProfile, snapshot, InteropFeature.FILE_OBJECT)) {
                    return Optional.of(buildUpstreamFileTarget(preferred, siteProfile));
                }
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
            return Optional.of(buildUpstreamFileTarget(credential, siteProfile));
        }
        return Optional.empty();
    }

    private Optional<UpstreamFileTarget> resolveGoogleNativeFileTarget(Long distributedKeyId) {
        DistributedKeyView distributedKey = distributedKeyQueryService.findActiveById(distributedKeyId)
                .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"));
        Map<Long, UpstreamCredentialEntity> credentials = activeCredentials(distributedKey);
        for (DistributedCredentialBindingView binding : distributedKey.bindings()) {
            UpstreamCredentialEntity credential = credentials.get(binding.credentialId());
            if (credential == null || credential.getSiteProfileId() == null) {
                continue;
            }
            UpstreamSiteProfileEntity siteProfile = resolveSiteProfile(credential.getSiteProfileId()).orElse(null);
            SiteCapabilitySnapshotEntity snapshot = siteCapabilitySnapshotRepository.findBySiteProfile_Id(credential.getSiteProfileId())
                    .orElse(null);
            if (siteProfile == null
                    || !supportsGoogleGenAiFiles(siteProfile.getSiteKind())
                    || !siteCapabilityTruthService.supportsFeature(siteProfile, snapshot, InteropFeature.FILE_OBJECT)) {
                continue;
            }
            return Optional.of(buildUpstreamFileTarget(credential, siteProfile));
        }
        return Optional.empty();
    }

    private Optional<UpstreamSiteProfileEntity> resolveSiteProfile(Long siteProfileId) {
        if (siteProfileId == null) {
            return Optional.empty();
        }
        return upstreamSiteProfileRepository.findById(siteProfileId);
    }

    private UpstreamFileTarget buildUpstreamFileTarget(
            UpstreamCredentialEntity credential,
            UpstreamSiteProfileEntity siteProfile) {
        ResolvedCredentialMaterial credentialMaterial = credentialMaterialResolver.resolveStored(credential);
        SiteClientRequest request = supportsGoogleGenAiFiles(siteProfile.getSiteKind())
                || supportsAnthropicFiles(siteProfile.getSiteKind())
                ? null
                : buildSiteClient(credential, siteProfile, "/v1/files");
        return new UpstreamFileTarget(credential, siteProfile, credentialMaterial, request);
    }

    private boolean supportsGoogleGenAiFiles(UpstreamSiteKind siteKind) {
        return siteKind == UpstreamSiteKind.GEMINI_DIRECT || siteKind == UpstreamSiteKind.VERTEX_AI;
    }

    private boolean supportsAnthropicFiles(UpstreamSiteKind siteKind) {
        return siteKind == UpstreamSiteKind.ANTHROPIC_DIRECT;
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

    private Optional<BindingContext> resolveBindingContext(GatewayFileBindingEntity binding) {
        UpstreamCredentialEntity credential = upstreamCredentialRepository.findById(binding.getCredentialId())
                .orElse(null);
        if (credential == null || credential.isDeleted() || !credential.isActive()) {
            return Optional.empty();
        }
        Long siteProfileId = binding.getSiteProfileId() != null ? binding.getSiteProfileId() : credential.getSiteProfileId();
        UpstreamSiteProfileEntity siteProfile = resolveSiteProfile(siteProfileId).orElse(null);
        if (siteProfile == null) {
            return Optional.empty();
        }
        ResolvedCredentialMaterial credentialMaterial = credentialMaterialResolver.resolveStored(credential);
        return Optional.of(new BindingContext(credential, siteProfile, credentialMaterial));
    }

    private Optional<GatewayFileBindingEntity> latestBinding(Long gatewayFileId) {
        return gatewayFileBindingRepository.findAllByGatewayFileIdOrderByCreatedAtDesc(gatewayFileId).stream()
                .findFirst();
    }

    private Optional<GatewayFileBindingEntity> latestAccessibleGoogleBinding(Long gatewayFileId, Set<Long> activeCredentialIds) {
        return gatewayFileBindingRepository.findAllByGatewayFileIdOrderByCreatedAtDesc(gatewayFileId).stream()
                .filter(binding -> activeCredentialIds.contains(binding.getCredentialId()))
                .filter(binding -> resolveBindingContext(binding)
                        .map(context -> supportsGoogleGenAiFiles(context.siteProfile().getSiteKind()))
                        .orElse(false))
                .findFirst();
    }

    private Optional<GatewayFileBindingEntity> latestAccessibleAnthropicBinding(Long gatewayFileId, Set<Long> activeCredentialIds) {
        return gatewayFileBindingRepository.findAllByGatewayFileIdOrderByCreatedAtDesc(gatewayFileId).stream()
                .filter(binding -> activeCredentialIds.contains(binding.getCredentialId()))
                .filter(binding -> resolveBindingContext(binding)
                        .map(context -> supportsAnthropicFiles(context.siteProfile().getSiteKind()))
                        .orElse(false))
                .findFirst();
    }

    private GoogleBindingResolution resolveGoogleBinding(String externalFileId, Long distributedKeyId) {
        DistributedKeyView distributedKey = distributedKeyQueryService.findActiveById(distributedKeyId)
                .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"));
        Map<Long, UpstreamCredentialEntity> credentials = activeCredentials(distributedKey);
        for (DistributedCredentialBindingView distributedBinding : distributedKey.bindings()) {
            UpstreamCredentialEntity credential = credentials.get(distributedBinding.credentialId());
            if (credential == null || credential.getSiteProfileId() == null) {
                continue;
            }
            UpstreamSiteProfileEntity siteProfile = resolveSiteProfile(credential.getSiteProfileId()).orElse(null);
            if (siteProfile == null || !supportsGoogleGenAiFiles(siteProfile.getSiteKind())) {
                continue;
            }
            List<GatewayFileBindingEntity> bindings = gatewayFileBindingRepository
                    .findAllBySiteProfileIdAndExternalFileIdOrderByCreatedAtDesc(siteProfile.getId(), externalFileId);
            for (GatewayFileBindingEntity binding : bindings) {
                if (!credentials.containsKey(binding.getCredentialId())) {
                    continue;
                }
                GatewayFileEntity entity = gatewayFileRepository.findById(binding.getGatewayFileId()).orElse(null);
                if (entity == null || entity.isDeleted() || !entity.getDistributedKeyId().equals(distributedKeyId)) {
                    continue;
                }
                return new GoogleBindingResolution(entity, binding);
            }
        }
        throw new IllegalArgumentException("未找到指定的 Google native 文件对象。");
    }

    private GoogleNativeFileView toGoogleNativeView(GatewayFileEntity entity, GatewayFileBindingEntity binding) {
        return new GoogleNativeFileView(
                toResponse(entity),
                binding.getExternalFileId(),
                binding.getExternalFilename(),
                entity.getMimeType(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getSha256(),
                binding.getCredentialId(),
                binding.getSiteProfileId(),
                entity.getStatus()
        );
    }

    private Map<Long, UpstreamCredentialEntity> activeCredentials(DistributedKeyView distributedKey) {
        Map<Long, UpstreamCredentialEntity> credentials = new LinkedHashMap<>();
        for (UpstreamCredentialEntity credential : upstreamCredentialRepository.findAllByIdInAndDeletedFalse(
                distributedKey.bindings().stream().map(DistributedCredentialBindingView::credentialId).toList())) {
            if (credential.isActive()) {
                credentials.put(credential.getId(), credential);
            }
        }
        return credentials;
    }

    private Set<Long> activeCredentialIds(Long distributedKeyId) {
        DistributedKeyView distributedKey = distributedKeyQueryService.findActiveById(distributedKeyId)
                .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"));
        return activeCredentials(distributedKey).keySet();
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

    private String sanitizeGoogleDisplayName(String displayName, String fallbackFilename) {
        String value = displayName == null || displayName.isBlank() ? fallbackFilename : displayName;
        return sanitizeFilename(value);
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
            ResolvedCredentialMaterial credentialMaterial,
            SiteClientRequest request
    ) {
        private WebClient client() {
            if (request == null) {
                throw new IllegalStateException("当前 UpstreamFileTarget 不支持 WebClient 调用。");
            }
            return request.client();
        }

        private String path() {
            if (request == null) {
                throw new IllegalStateException("当前 UpstreamFileTarget 不支持路径解析。");
            }
            return request.path();
        }
    }

    private record BindingContext(
            UpstreamCredentialEntity credential,
            UpstreamSiteProfileEntity siteProfile,
            ResolvedCredentialMaterial credentialMaterial
    ) {
    }

    private record GoogleBindingResolution(
            GatewayFileEntity entity,
            GatewayFileBindingEntity binding
    ) {
    }

    public record GoogleNativeFileView(
            GatewayFileResponse response,
            String externalFileId,
            String displayName,
            String mimeType,
            Instant createdAt,
            Instant updatedAt,
            String sha256,
            Long credentialId,
            Long siteProfileId,
            String status
    ) {
    }
}
