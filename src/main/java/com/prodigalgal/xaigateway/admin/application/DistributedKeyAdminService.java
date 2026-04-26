package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.DistributedKeyCreateResponse;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyClientConfigResponse;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyRequest;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyResponse;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeySecretService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeySecrets;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountPoolBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccessGroupGrantRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
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
    private final DistributedKeyBindingRepository distributedKeyBindingRepository;
    private final DistributedKeyAccountPoolBindingRepository distributedKeyAccountPoolBindingRepository;
    private final DistributedKeyAccessGroupGrantRepository keyGrantRepository;
    private final GatewayUserRepository gatewayUserRepository;
    private final Optional<OpsAuditService> opsAuditService;

    public DistributedKeyAdminService(
            DistributedKeyRepository distributedKeyRepository,
            DistributedKeySecretService distributedKeySecretService,
            DistributedKeyBindingRepository distributedKeyBindingRepository,
            DistributedKeyAccountPoolBindingRepository distributedKeyAccountPoolBindingRepository,
            DistributedKeyAccessGroupGrantRepository keyGrantRepository,
            GatewayUserRepository gatewayUserRepository,
            Optional<OpsAuditService> opsAuditService) {
        this.distributedKeyRepository = distributedKeyRepository;
        this.distributedKeySecretService = distributedKeySecretService;
        this.distributedKeyBindingRepository = distributedKeyBindingRepository;
        this.distributedKeyAccountPoolBindingRepository = distributedKeyAccountPoolBindingRepository;
        this.keyGrantRepository = keyGrantRepository;
        this.gatewayUserRepository = gatewayUserRepository;
        this.opsAuditService = opsAuditService;
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
        apply(entity, request, true);
        if (entity.isActive()) {
            throw new IllegalArgumentException("分发 key 启用前必须先绑定账号池。");
        }
        entity.setKeyPrefix(secrets.keyPrefix());
        entity.setSecretHash(secrets.secretHash());
        entity.setMaskedKey(secrets.maskedKey());
        DistributedKeyEntity saved = distributedKeyRepository.save(entity);
        return new DistributedKeyCreateResponse(toResponse(saved), secrets.fullKey());
    }

    public DistributedKeyResponse update(Long id, DistributedKeyRequest request) {
        DistributedKeyEntity entity = getRequired(id);
        apply(entity, request, false);
        if (entity.isActive()) {
            assertHasActivePoolBinding(id);
        }
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
        if (active) {
            assertHasActivePoolBinding(id);
        }
        entity.setActive(active);
        return toResponse(distributedKeyRepository.save(entity));
    }

    public void delete(Long id) {
        DistributedKeyEntity entity = getRequired(id);
        distributedKeyBindingRepository.deleteAllByDistributedKey_Id(id);
        distributedKeyAccountPoolBindingRepository.deleteAllByDistributedKey_Id(id);
        keyGrantRepository.deleteAllByDistributedKey_Id(id);
        distributedKeyRepository.delete(entity);
    }

    public DistributedKeyClientConfigResponse exportClientConfig(Long id, String format, String clientFamily, String baseUrl) {
        DistributedKeyEntity entity = getRequired(id);
        String normalizedFormat = normalizeConfigFormat(format);
        String normalizedClientFamily = GatewayClientFamily.from(clientFamily).name();
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String apiBaseUrl = normalizedBaseUrl + "/v1";
        String maskedKey = entity.getMaskedKey() == null || entity.getMaskedKey().isBlank()
                ? entity.getKeyPrefix() + "..."
                : entity.getMaskedKey();
        String secretPlaceholder = "<仅创建或轮换时展示一次；当前为 " + maskedKey + ">";
        String config = switch (normalizedFormat) {
            case "auth_json" -> """
                    {
                      "OPENAI_API_KEY": "%s",
                      "OPENAI_BASE_URL": "%s",
                      "X_AI_GATEWAY_API_KEY": "%s"
                    }
                    """.formatted(secretPlaceholder, apiBaseUrl, secretPlaceholder).trim();
            case "env" -> """
                    export OPENAI_API_KEY="%s"
                    export OPENAI_BASE_URL="%s"
                    export X_AI_GATEWAY_API_KEY="%s"
                    """.formatted(secretPlaceholder, apiBaseUrl, secretPlaceholder).trim();
            default -> """
                    [model_providers.x-ai-gateway]
                    name = "x-ai-gateway"
                    base_url = "%s"
                    env_key = "X_AI_GATEWAY_API_KEY"
                    wire_api = "chat"

                    # 将下方环境变量替换为创建或轮换时一次性展示的完整 key。
                    # 当前 key: %s
                    """.formatted(apiBaseUrl, maskedKey).trim();
        };
        recordClientConfigExport(entity, normalizedFormat, normalizedClientFamily, normalizedBaseUrl);
        return new DistributedKeyClientConfigResponse(
                entity.getKeyName(),
                normalizedClientFamily,
                normalizedFormat,
                maskedKey,
                "安全导出不会返回完整 secret；完整 key 仅在创建或轮换时展示一次。",
                config
        );
    }

    private DistributedKeyEntity getRequired(Long id) {
        Optional<DistributedKeyEntity> entity = distributedKeyRepository.findById(id);
        if (entity.isEmpty()) {
            throw new IllegalArgumentException("未找到指定的 DistributedKey。");
        }
        return entity.get();
    }

    private void apply(DistributedKeyEntity entity, DistributedKeyRequest request, boolean isCreate) {
        entity.setKeyName(request.keyName().trim());
        entity.setDescription(blankToNull(request.description()));
        entity.setOwnerUser(resolveOwnerUser(request.ownerUserId()));
        entity.setActive(resolveActive(request.active(), entity.isActive(), isCreate));
        entity.setAllowedProtocols(normalizeProtocols(request.allowedProtocols()));
        entity.setAllowedModels(normalizeModels(request.allowedModels()));
        entity.setAllowedProviderTypes(normalizeProviderTypes(request.allowedProviderTypes()));
        entity.setExpiresAt(request.expiresAt());
        entity.setBudgetLimitMicros(request.budgetLimitMicros());
        entity.setBudgetWindowSeconds(request.budgetWindowSeconds());
        entity.setRpmLimit(request.rpmLimit());
        entity.setTpmLimit(request.tpmLimit());
        entity.setConcurrencyLimit(request.concurrencyLimit());
        entity.setStickySessionTtlSeconds(request.stickySessionTtlSeconds());
        entity.setAllowedClientFamilies(normalizeClientFamilies(request.allowedClientFamilies()));
        entity.setRequireClientFamilyMatch(Boolean.TRUE.equals(request.requireClientFamilyMatch()));
    }

    private boolean resolveActive(Boolean requestedActive, boolean currentActive, boolean isCreate) {
        if (requestedActive != null) {
            return requestedActive;
        }
        if (isCreate) {
            return false;
        }
        return currentActive;
    }

    private GatewayUserEntity resolveOwnerUser(Long ownerUserId) {
        if (ownerUserId == null) {
            return null;
        }
        return gatewayUserRepository.findById(ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定 Key 所属用户。"));
    }

    private void assertHasActivePoolBinding(Long distributedKeyId) {
        long activeBindings = distributedKeyAccountPoolBindingRepository
                .countByDistributedKey_IdAndActiveTrue(distributedKeyId);
        if (activeBindings <= 0) {
            throw new IllegalArgumentException("分发 key 启用前必须先绑定账号池。");
        }
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

    private List<String> normalizeProviderTypes(List<String> providerTypes) {
        if (providerTypes == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String providerType : providerTypes) {
            if (providerType == null || providerType.isBlank()) {
                continue;
            }
            String value = ProviderType.valueOf(providerType.trim().toUpperCase(Locale.ROOT)).name();
            if (!normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private List<String> normalizeClientFamilies(List<String> clientFamilies) {
        if (clientFamilies == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String clientFamily : clientFamilies) {
            if (clientFamily == null || clientFamily.isBlank()) {
                continue;
            }
            String value = GatewayClientFamily.from(clientFamily).name();
            if (!normalized.contains(value)) {
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

    private String normalizeConfigFormat(String format) {
        if (format == null || format.isBlank()) {
            return "config_toml";
        }
        String normalized = format.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "toml", "config_toml" -> "config_toml";
            case "auth", "auth_json", "json" -> "auth_json";
            case "env", "shell" -> "env";
            default -> throw new IllegalArgumentException("不支持的客户端配置格式。");
        };
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:8080";
        }
        String value = baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/v1")) {
            return value.substring(0, value.length() - 3);
        }
        return value;
    }

    private void recordClientConfigExport(
            DistributedKeyEntity entity,
            String format,
            String clientFamily,
            String baseUrl) {
        opsAuditService.ifPresent(service -> service.record(
                "DISTRIBUTED_KEY",
                "EXPORT_CLIENT_CONFIG",
                "DistributedKey",
                String.valueOf(entity.getId()),
                "{"
                        + "\"keyName\":\"" + escapeJson(entity.getKeyName()) + "\","
                        + "\"format\":\"" + escapeJson(format) + "\","
                        + "\"clientFamily\":\"" + escapeJson(clientFamily) + "\","
                        + "\"baseUrl\":\"" + escapeJson(baseUrl) + "\","
                        + "\"secretPolicy\":\"masked_only\""
                        + "}"
        ));
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private DistributedKeyResponse toResponse(DistributedKeyEntity entity) {
        return new DistributedKeyResponse(
                entity.getId(),
                entity.getKeyName(),
                entity.getKeyPrefix(),
                entity.getMaskedKey(),
                entity.getDescription(),
                entity.getOwnerUser() == null ? null : entity.getOwnerUser().getId(),
                entity.getOwnerUser() == null ? null : entity.getOwnerUser().getEmail(),
                entity.isActive(),
                entity.getAllowedProtocols(),
                entity.getAllowedModels(),
                entity.getAllowedProviderTypes(),
                entity.getExpiresAt(),
                entity.getBudgetLimitMicros(),
                entity.getBudgetWindowSeconds(),
                entity.getRpmLimit(),
                entity.getTpmLimit(),
                entity.getConcurrencyLimit(),
                entity.getStickySessionTtlSeconds(),
                entity.getAllowedClientFamilies(),
                entity.isRequireClientFamilyMatch(),
                entity.getLastUsedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
