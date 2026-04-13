package com.prodigalgal.xaigateway.gateway.core.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayFileBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayFileEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@Transactional
public class UpstreamFileImportService {

    private final GatewayFileRepository gatewayFileRepository;
    private final GatewayFileBindingRepository gatewayFileBindingRepository;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final CredentialMaterialResolver credentialMaterialResolver;
    private final GatewayFileService gatewayFileService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public UpstreamFileImportService(
            GatewayFileRepository gatewayFileRepository,
            GatewayFileBindingRepository gatewayFileBindingRepository,
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            CredentialMaterialResolver credentialMaterialResolver,
            GatewayFileService gatewayFileService,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {
        this.gatewayFileRepository = gatewayFileRepository;
        this.gatewayFileBindingRepository = gatewayFileBindingRepository;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.credentialMaterialResolver = credentialMaterialResolver;
        this.gatewayFileService = gatewayFileService;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    public UpstreamImportedFileResponse importExternalReference(
            Long distributedKeyId,
            ProviderType providerType,
            Long credentialId,
            String externalFileId,
            String externalFilename,
            String mimeType,
            String purpose
    ) {
        requireSupportedProvider(providerType);
        UpstreamCredentialEntity credential = getRequiredCredential(credentialId, providerType);

        GatewayFileEntity file = new GatewayFileEntity();
        file.setFileKey("file-" + UUID.randomUUID().toString().replace("-", ""));
        file.setDistributedKeyId(distributedKeyId);
        file.setFilename(externalFilename == null || externalFilename.isBlank() ? externalFileId : externalFilename.trim());
        file.setMimeType(mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType.trim());
        file.setPurpose(purpose == null || purpose.isBlank() ? null : purpose.trim());
        file.setSizeBytes(0L);
        file.setSha256(HexFormat.of().formatHex(credentialCryptoService.fingerprint(externalFileId).getBytes()));
        file.setStoragePath("external://" + providerType.name() + "/" + externalFileId);
        file.setStatus("external_only");
        GatewayFileEntity savedFile = gatewayFileRepository.save(file);

        GatewayFileBindingEntity binding = new GatewayFileBindingEntity();
        binding.setGatewayFileId(savedFile.getId());
        binding.setProviderType(providerType);
        binding.setCredentialId(credentialId);
        binding.setExternalFileId(externalFileId.trim());
        binding.setExternalFilename(savedFile.getFilename());
        binding.setStatus("ACTIVE");
        binding.setLastSyncedAt(null);
        GatewayFileBindingEntity savedBinding = gatewayFileBindingRepository.save(binding);

        return new UpstreamImportedFileResponse(
                GatewayFileResponse.from(
                        savedFile.getFileKey(),
                        savedFile.getFilename(),
                        savedFile.getPurpose(),
                        savedFile.getSizeBytes(),
                        savedFile.getCreatedAt(),
                        savedFile.getStatus()
                ),
                new GatewayFileBindingResponse(
                        savedBinding.getId(),
                        savedFile.getFileKey(),
                        savedBinding.getProviderType(),
                        savedBinding.getCredentialId(),
                        savedBinding.getExternalFileId(),
                        savedBinding.getExternalFilename(),
                        savedBinding.getStatus(),
                        savedBinding.getLastSyncedAt(),
                        savedBinding.getCreatedAt(),
                        savedBinding.getUpdatedAt()
                )
        );
    }

    public GatewayFileResponse syncImportedFile(String fileKey) {
        GatewayFileEntity file = gatewayFileRepository.findByFileKeyAndDeletedFalse(fileKey)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的文件对象。"));
        GatewayFileBindingEntity binding = gatewayFileBindingRepository.findAllByGatewayFileIdOrderByCreatedAtDesc(file.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到文件 binding。"));

        requireSupportedProvider(binding.getProviderType());
        UpstreamCredentialEntity credential = getRequiredCredential(binding.getCredentialId(), binding.getProviderType());
        String secret = credentialMaterialResolver.resolveStored(credential).secret();

        WebClient client = webClientBuilder.clone()
                .baseUrl(normalizeBaseUrl(credential.getBaseUrl()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secret)
                .build();

        JsonNode metadata = client.get()
                .uri(resolveOpenAiFileMetadataPath(credential.getBaseUrl(), binding.getExternalFileId()))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        byte[] bytes = client.get()
                .uri(resolveOpenAiFileContentPath(credential.getBaseUrl(), binding.getExternalFileId()))
                .retrieve()
                .bodyToMono(byte[].class)
                .block();

        if (bytes == null) {
            throw new IllegalStateException("上游文件内容为空。");
        }

        Path directory = gatewayFileService.ensureStorageDirectoryForSync();
        Path target = directory.resolve(file.getFileKey() + "-" + sanitizeFilename(file.getFilename()));
        try {
            Files.write(target, bytes);
        } catch (IOException exception) {
            throw new IllegalStateException("写入同步文件失败。", exception);
        }

        file.setStoragePath(target.toAbsolutePath().toString());
        file.setStatus("processed");
        file.setSizeBytes(bytes.length);
        file.setMimeType(metadata == null ? file.getMimeType() : metadata.path("mime_type").asText(file.getMimeType()));
        file.setFilename(metadata == null ? file.getFilename() : metadata.path("filename").asText(file.getFilename()));
        file.setPurpose(metadata == null ? file.getPurpose() : metadata.path("purpose").asText(file.getPurpose()));
        gatewayFileRepository.save(file);

        binding.setStatus("SYNCED");
        binding.setLastSyncedAt(Instant.now());
        if (metadata != null) {
            binding.setExternalFilename(metadata.path("filename").asText(binding.getExternalFilename()));
        }
        gatewayFileBindingRepository.save(binding);

        return GatewayFileResponse.from(
                file.getFileKey(),
                file.getFilename(),
                file.getPurpose(),
                file.getSizeBytes(),
                file.getCreatedAt(),
                file.getStatus()
        );
    }

    private void requireSupportedProvider(ProviderType providerType) {
        if (providerType != ProviderType.OPENAI_DIRECT && providerType != ProviderType.OPENAI_COMPATIBLE) {
            throw new IllegalArgumentException("当前最小实现仅支持 OpenAI/OpenAI-compatible upstream file import。");
        }
    }

    private UpstreamCredentialEntity getRequiredCredential(Long credentialId, ProviderType providerType) {
        UpstreamCredentialEntity credential = upstreamCredentialRepository.findById(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的上游凭证。"));
        if (credential.isDeleted() || credential.getProviderType() != providerType) {
            throw new IllegalArgumentException("上游凭证不存在或类型不匹配。");
        }
        return credential;
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.replaceAll("/+$", "");
    }

    private String resolveOpenAiFileMetadataPath(String baseUrl, String fileId) {
        return baseUrl.endsWith("/v1") ? "/files/" + fileId : "/v1/files/" + fileId;
    }

    private String resolveOpenAiFileContentPath(String baseUrl, String fileId) {
        return baseUrl.endsWith("/v1") ? "/files/" + fileId + "/content" : "/v1/files/" + fileId + "/content";
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "imported.bin";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
