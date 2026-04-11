package com.prodigalgal.xaigateway.gateway.core.file;

import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayFileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@Transactional
public class GatewayFileService {

    private final GatewayFileRepository gatewayFileRepository;
    private final GatewayProperties gatewayProperties;

    public GatewayFileService(
            GatewayFileRepository gatewayFileRepository,
            GatewayProperties gatewayProperties) {
        this.gatewayFileRepository = gatewayFileRepository;
        this.gatewayProperties = gatewayProperties;
    }

    public Mono<GatewayFileResponse> createFile(Long distributedKeyId, FilePart filePart, String purpose) {
        String fileKey = "file-" + UUID.randomUUID().toString().replace("-", "");
        Path directory = ensureStorageDirectory();
        Path storagePath = directory.resolve(fileKey + "-" + sanitizeFilename(filePart.filename()));

        return filePart.transferTo(storagePath)
                .then(Mono.fromCallable(() -> toResponse(
                        persistFile(
                                distributedKeyId,
                                fileKey,
                                storagePath,
                                filePart.filename(),
                                filePart.headers().getContentType() == null ? "application/octet-stream" : filePart.headers().getContentType().toString(),
                                purpose
                        )
                )));
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
        entity.setDeleted(true);
        entity.setStatus("deleted");
        gatewayFileRepository.save(entity);
        try {
            Files.deleteIfExists(Path.of(entity.getStoragePath()));
        } catch (IOException exception) {
            throw new IllegalStateException("删除本地文件失败。", exception);
        }
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
            entity.setStatus("processed");
            GatewayFileEntity saved = gatewayFileRepository.save(entity);
            return saved;
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
}
