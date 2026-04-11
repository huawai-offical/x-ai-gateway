package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.DistributedKeyCreateResponse;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyRequest;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyResponse;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeySecretService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeySecrets;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DistributedKeyAdminService {

    private final DistributedKeyRepository distributedKeyRepository;
    private final DistributedKeySecretService distributedKeySecretService;

    public DistributedKeyAdminService(
            DistributedKeyRepository distributedKeyRepository,
            DistributedKeySecretService distributedKeySecretService) {
        this.distributedKeyRepository = distributedKeyRepository;
        this.distributedKeySecretService = distributedKeySecretService;
    }

    @Transactional(readOnly = true)
    public List<DistributedKeyResponse> list() {
        return distributedKeyRepository.findAll().stream()
                .sorted(Comparator.comparing(DistributedKeyEntity::getCreatedAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    public DistributedKeyCreateResponse create(DistributedKeyRequest request) {
        DistributedKeySecrets secrets = distributedKeySecretService.generate();
        DistributedKeyEntity entity = new DistributedKeyEntity();
        apply(entity, request);
        entity.setKeyPrefix(secrets.keyPrefix());
        entity.setSecretHash(secrets.secretHash());
        entity.setMaskedKey(secrets.maskedKey());
        DistributedKeyEntity saved = distributedKeyRepository.save(entity);
        return new DistributedKeyCreateResponse(toResponse(saved), secrets.fullKey());
    }

    public DistributedKeyResponse update(Long id, DistributedKeyRequest request) {
        DistributedKeyEntity entity = getRequired(id);
        apply(entity, request);
        return toResponse(distributedKeyRepository.save(entity));
    }

    public DistributedKeyCreateResponse rotate(Long id) {
        DistributedKeyEntity entity = getRequired(id);
        DistributedKeySecrets secrets = distributedKeySecretService.generate();
        entity.setKeyPrefix(secrets.keyPrefix());
        entity.setSecretHash(secrets.secretHash());
        entity.setMaskedKey(secrets.maskedKey());
        DistributedKeyEntity saved = distributedKeyRepository.save(entity);
        return new DistributedKeyCreateResponse(toResponse(saved), secrets.fullKey());
    }

    public DistributedKeyResponse toggle(Long id, boolean active) {
        DistributedKeyEntity entity = getRequired(id);
        entity.setActive(active);
        return toResponse(distributedKeyRepository.save(entity));
    }

    private DistributedKeyEntity getRequired(Long id) {
        Optional<DistributedKeyEntity> entity = distributedKeyRepository.findById(id);
        if (entity.isEmpty()) {
            throw new IllegalArgumentException("未找到指定的 DistributedKey。");
        }
        return entity.get();
    }

    private void apply(DistributedKeyEntity entity, DistributedKeyRequest request) {
        entity.setKeyName(request.keyName().trim());
        entity.setDescription(blankToNull(request.description()));
        entity.setActive(request.active() == null || request.active());
        entity.setAllowedProtocols(normalizeProtocols(request.allowedProtocols()));
        entity.setAllowedModels(normalizeModels(request.allowedModels()));
    }

    private List<String> normalizeProtocols(List<String> protocols) {
        if (protocols == null) {
            return List.of();
        }

        return protocols.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private List<String> normalizeModels(List<String> models) {
        if (models == null) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (String model : models) {
            if (model == null || model.isBlank()) {
                continue;
            }
            String value = ModelIdNormalizer.normalize(model);
            if (value != null && !value.isBlank() && !normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private DistributedKeyResponse toResponse(DistributedKeyEntity entity) {
        return new DistributedKeyResponse(
                entity.getId(),
                entity.getKeyName(),
                entity.getKeyPrefix(),
                entity.getMaskedKey(),
                entity.getDescription(),
                entity.isActive(),
                entity.getAllowedProtocols(),
                entity.getAllowedModels(),
                entity.getLastUsedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
