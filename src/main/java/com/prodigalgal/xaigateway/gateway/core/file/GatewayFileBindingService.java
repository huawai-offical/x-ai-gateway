package com.prodigalgal.xaigateway.gateway.core.file;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayFileBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayFileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GatewayFileBindingService {

    private final GatewayFileRepository gatewayFileRepository;
    private final GatewayFileBindingRepository gatewayFileBindingRepository;

    public GatewayFileBindingService(
            GatewayFileRepository gatewayFileRepository,
            GatewayFileBindingRepository gatewayFileBindingRepository) {
        this.gatewayFileRepository = gatewayFileRepository;
        this.gatewayFileBindingRepository = gatewayFileBindingRepository;
    }

    public List<GatewayFileBindingResponse> listBindings(String fileKey) {
        GatewayFileEntity file = getRequiredFile(fileKey);
        return gatewayFileBindingRepository.findAllByGatewayFileIdOrderByCreatedAtDesc(file.getId())
                .stream()
                .map(entity -> toResponse(file.getFileKey(), entity))
                .toList();
    }

    public GatewayFileBindingResponse createBinding(
            String fileKey,
            ProviderType providerType,
            Long credentialId,
            String externalFileId,
            String externalFilename) {
        GatewayFileEntity file = getRequiredFile(fileKey);
        GatewayFileBindingEntity entity = new GatewayFileBindingEntity();
        entity.setGatewayFileId(file.getId());
        entity.setProviderType(providerType);
        entity.setCredentialId(credentialId);
        entity.setExternalFileId(externalFileId);
        entity.setExternalFilename(externalFilename);
        entity.setStatus("ACTIVE");
        GatewayFileBindingEntity saved = gatewayFileBindingRepository.save(entity);
        return toResponse(file.getFileKey(), saved);
    }

    public void deleteBinding(String fileKey, Long bindingId) {
        GatewayFileEntity file = getRequiredFile(fileKey);
        GatewayFileBindingEntity binding = getRequiredBinding(bindingId);
        if (!binding.getGatewayFileId().equals(file.getId())) {
            throw new IllegalArgumentException("文件绑定不属于指定文件。");
        }
        gatewayFileBindingRepository.delete(binding);
    }

    private GatewayFileEntity getRequiredFile(String fileKey) {
        Optional<GatewayFileEntity> file = gatewayFileRepository.findByFileKeyAndDeletedFalse(fileKey);
        if (file.isEmpty()) {
            throw new IllegalArgumentException("未找到指定的文件对象。");
        }
        return file.get();
    }

    private GatewayFileBindingEntity getRequiredBinding(Long bindingId) {
        return gatewayFileBindingRepository.findById(bindingId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的文件绑定。"));
    }

    private GatewayFileBindingResponse toResponse(String fileKey, GatewayFileBindingEntity entity) {
        return new GatewayFileBindingResponse(
                entity.getId(),
                fileKey,
                entity.getProviderType(),
                entity.getCredentialId(),
                entity.getExternalFileId(),
                entity.getExternalFilename(),
                entity.getStatus(),
                entity.getLastSyncedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
