package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ClientInstanceAuthorizationRequest;
import com.prodigalgal.xaigateway.admin.api.ClientInstanceAuthorizationResponse;
import com.prodigalgal.xaigateway.admin.api.ClientInstanceConfigResponse;
import com.prodigalgal.xaigateway.admin.api.ClientInstanceRequest;
import com.prodigalgal.xaigateway.admin.api.ClientInstanceResponse;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.infra.persistence.entity.ClientInstanceEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.ClientInstanceGrantEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeySecretExportGrantEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ClientInstanceGrantRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.ClientInstanceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeySecretExportGrantRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class ClientInstanceAdminService {

    private static final Duration DEFAULT_GRANT_TTL = Duration.ofMinutes(10);
    private static final int MAX_GRANT_TTL_SECONDS = 900;

    private final ClientInstanceRepository clientInstanceRepository;
    private final ClientInstanceGrantRepository clientInstanceGrantRepository;
    private final DistributedKeyRepository distributedKeyRepository;
    private final DistributedKeySecretExportGrantRepository secretExportGrantRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final ObjectMapper objectMapper;
    private final Optional<OpsAuditService> opsAuditService;

    public ClientInstanceAdminService(
            ClientInstanceRepository clientInstanceRepository,
            ClientInstanceGrantRepository clientInstanceGrantRepository,
            DistributedKeyRepository distributedKeyRepository,
            DistributedKeySecretExportGrantRepository secretExportGrantRepository,
            CredentialCryptoService credentialCryptoService,
            ObjectMapper objectMapper,
            Optional<OpsAuditService> opsAuditService) {
        this.clientInstanceRepository = clientInstanceRepository;
        this.clientInstanceGrantRepository = clientInstanceGrantRepository;
        this.distributedKeyRepository = distributedKeyRepository;
        this.secretExportGrantRepository = secretExportGrantRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.objectMapper = objectMapper;
        this.opsAuditService = opsAuditService;
    }

    @Transactional(readOnly = true)
    public List<ClientInstanceResponse> list(Long distributedKeyId) {
        List<ClientInstanceEntity> instances = distributedKeyId == null
                ? clientInstanceRepository.findAllByOrderByUpdatedAtDesc()
                : clientInstanceRepository.findAllByDistributedKey_IdOrderByUpdatedAtDesc(distributedKeyId);
        return instances.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ClientInstanceResponse get(Long id) {
        return toResponse(getRequired(id));
    }

    public ClientInstanceResponse register(ClientInstanceRequest request) {
        DistributedKeyEntity key = getDistributedKey(request.distributedKeyId());
        String instanceId = normalizeInstanceId(request.instanceId());
        clientInstanceRepository.findByDistributedKey_IdAndInstanceId(key.getId(), instanceId)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("该 DistributedKey 下已存在相同 client instance。");
                });
        ClientInstanceEntity entity = new ClientInstanceEntity();
        apply(entity, key, request);
        ClientInstanceEntity saved = clientInstanceRepository.save(entity);
        recordAudit(saved, "REGISTER_CLIENT_INSTANCE", "registered");
        return toResponse(saved);
    }

    public ClientInstanceResponse update(Long id, ClientInstanceRequest request) {
        ClientInstanceEntity entity = getRequired(id);
        DistributedKeyEntity key = getDistributedKey(request.distributedKeyId());
        String nextInstanceId = normalizeInstanceId(request.instanceId());
        if (!entity.getDistributedKey().getId().equals(key.getId()) || !entity.getInstanceId().equals(nextInstanceId)) {
            clientInstanceRepository.findByDistributedKey_IdAndInstanceId(key.getId(), nextInstanceId)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("该 DistributedKey 下已存在相同 client instance。");
                    });
        }
        apply(entity, key, request);
        ClientInstanceEntity saved = clientInstanceRepository.save(entity);
        recordAudit(saved, "UPDATE_CLIENT_INSTANCE", "updated");
        return toResponse(saved);
    }

    public ClientInstanceResponse toggle(Long id, Boolean active, String reason) {
        ClientInstanceEntity entity = getRequired(id);
        if (entity.isRevoked()) {
            throw new IllegalArgumentException("已撤销的 client instance 不能重新启用。");
        }
        Instant now = Instant.now();
        if (Boolean.TRUE.equals(active)) {
            entity.setStatus("ACTIVE");
            entity.setDisabledAt(null);
        } else {
            entity.setStatus("DISABLED");
            entity.setDisabledAt(now);
        }
        ClientInstanceEntity saved = clientInstanceRepository.save(entity);
        recordAudit(saved, Boolean.TRUE.equals(active) ? "ENABLE_CLIENT_INSTANCE" : "DISABLE_CLIENT_INSTANCE", defaultString(reason, "status_changed"));
        return toResponse(saved);
    }

    public ClientInstanceResponse revoke(Long id, String reason) {
        ClientInstanceEntity entity = getRequired(id);
        Instant now = Instant.now();
        entity.setStatus("REVOKED");
        entity.setDisabledAt(entity.getDisabledAt() == null ? now : entity.getDisabledAt());
        entity.setRevokedAt(now);
        clientInstanceGrantRepository.findAllByClientInstance_Id(id).forEach(grant -> {
            if (!grant.isRevoked() && !grant.isConsumed()) {
                grant.setRevokedAt(now);
                clientInstanceGrantRepository.save(grant);
            }
        });
        ClientInstanceEntity saved = clientInstanceRepository.save(entity);
        recordAudit(saved, "REVOKE_CLIENT_INSTANCE", defaultString(reason, "revoked"));
        return toResponse(saved);
    }

    public ClientInstanceAuthorizationResponse issueAuthorization(Long id, ClientInstanceAuthorizationRequest request) {
        ClientInstanceEntity instance = getRequired(id);
        if (!instance.isActive()) {
            throw new IllegalArgumentException("client instance 未处于 ACTIVE 状态，不能发行授权。");
        }
        String fullKey = resolveFullKey(instance.getDistributedKey().getId(), request);
        String grantToken = UUID.randomUUID() + "-" + UUID.randomUUID();
        Instant now = Instant.now();
        ClientInstanceGrantEntity grant = new ClientInstanceGrantEntity();
        grant.setClientInstance(instance);
        grant.setTokenHash(credentialCryptoService.fingerprint(grantToken));
        grant.setFullKeyCiphertext(credentialCryptoService.encrypt(fullKey));
        grant.setSource(normalizeSource(request == null ? null : request.source()));
        grant.setConfigFormat(normalizeConfigFormat(request == null ? null : request.format()));
        grant.setBaseUrl(normalizeBaseUrl(request == null ? null : request.baseUrl()));
        grant.setExpiresAt(resolveExpiresAt(request, now));
        Map<String, Object> grantMetadata = new LinkedHashMap<>();
        grantMetadata.put("pluginName", defaultString(request == null ? null : request.pluginName(), instance.getPluginName()));
        grantMetadata.put("pluginVersion", defaultString(request == null ? null : request.pluginVersion(), instance.getPluginVersion()));
        grantMetadata.put("issuedAt", now.toString());
        grant.setMetadataJson(writeJson(grantMetadata));
        ClientInstanceGrantEntity savedGrant = clientInstanceGrantRepository.save(grant);
        instance.setLastAuthorizedAt(now);
        instance.setLastSeenAt(now);
        clientInstanceRepository.save(instance);
        recordAudit(instance, "ISSUE_CLIENT_INSTANCE_AUTHORIZATION", savedGrant.getSource());
        return toGrantResponse(instance, savedGrant, grantToken);
    }

    public ClientInstanceConfigResponse consumeAuthorization(Long id, String grantToken) {
        ClientInstanceEntity instance = getRequired(id);
        if (!instance.isActive()) {
            throw new IllegalArgumentException("client instance 未处于 ACTIVE 状态，不能消费授权。");
        }
        ClientInstanceGrantEntity grant = requireGrant(id, grantToken);
        validateConsumable(grant);
        String fullKey = credentialCryptoService.decrypt(grant.getFullKeyCiphertext());
        Instant now = Instant.now();
        grant.setConsumedAt(now);
        clientInstanceGrantRepository.save(grant);
        instance.setLastSeenAt(now);
        instance.setLastAuthorizedAt(now);
        clientInstanceRepository.save(instance);
        recordAudit(instance, "CONSUME_CLIENT_INSTANCE_AUTHORIZATION", grant.getSource());
        String config = renderClientConfig(
                grant.getConfigFormat(),
                instance.getClientFamily(),
                grant.getBaseUrl(),
                fullKey,
                instance.getInstanceId(),
                instance.getWorkspaceHint()
        );
        return new ClientInstanceConfigResponse(
                instance.getId(),
                instance.getInstanceId(),
                instance.getClientFamily(),
                instance.getWorkspaceHint(),
                grant.getConfigFormat(),
                grant.getBaseUrl(),
                config,
                buildPluginMessage(instance, grant, null),
                now,
                "一次性授权已消费；该 grantToken 不能再次使用。"
        );
    }

    public ClientInstanceAuthorizationResponse revokeAuthorization(Long id, String grantToken) {
        ClientInstanceEntity instance = getRequired(id);
        ClientInstanceGrantEntity grant = requireGrant(id, grantToken);
        if (!grant.isRevoked()) {
            grant.setRevokedAt(Instant.now());
            clientInstanceGrantRepository.save(grant);
        }
        recordAudit(instance, "REVOKE_CLIENT_INSTANCE_AUTHORIZATION", grant.getSource());
        return toGrantResponse(instance, grant, grantToken);
    }

    private void apply(ClientInstanceEntity entity, DistributedKeyEntity key, ClientInstanceRequest request) {
        GatewayClientFamily clientFamily = GatewayClientFamily.from(request.clientFamily());
        String instanceId = normalizeInstanceId(request.instanceId());
        entity.setDistributedKey(key);
        entity.setInstanceId(instanceId);
        entity.setDisplayName(defaultString(request.displayName(), instanceId));
        entity.setClientFamily(clientFamily.name());
        entity.setWorkspaceHint(truncate(defaultString(request.workspaceHint(), "default"), 256));
        entity.setPluginName(truncate(blankToNull(request.pluginName()), 128));
        entity.setPluginVersion(truncate(blankToNull(request.pluginVersion()), 64));
        entity.setDeepLinkScheme(truncate(defaultString(request.deepLinkScheme(), "xag"), 64));
        entity.setMetadataJson(writeJson(sanitizeMetadata(readMetadataMap(request.metadataJson()))));
        if (Boolean.TRUE.equals(request.active())) {
            entity.setStatus("ACTIVE");
            entity.setDisabledAt(null);
        } else if (Boolean.FALSE.equals(request.active())) {
            entity.setStatus("DISABLED");
            entity.setDisabledAt(Instant.now());
        } else if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("ACTIVE");
        }
    }

    private String resolveFullKey(Long distributedKeyId, ClientInstanceAuthorizationRequest request) {
        if (request != null && request.fullKey() != null && !request.fullKey().isBlank()) {
            return request.fullKey().trim();
        }
        if (request == null || request.secretExportGrantToken() == null || request.secretExportGrantToken().isBlank()) {
            throw new IllegalArgumentException("发行插件/Deep Link 授权时必须提供完整 secret 或一次性 secret export token。");
        }
        String sourceToken = request.secretExportGrantToken().trim();
        String tokenHash = credentialCryptoService.fingerprint(sourceToken);
        DistributedKeySecretExportGrantEntity sourceGrant = secretExportGrantRepository
                .findByDistributedKey_IdAndTokenHash(distributedKeyId, tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("一次性 secret export token 不存在或不属于该 DistributedKey。"));
        if (sourceGrant.isRevoked()) {
            throw new IllegalArgumentException("一次性 secret export token 已撤销。");
        }
        if (sourceGrant.isConsumed()) {
            throw new IllegalArgumentException("一次性 secret export token 已被使用。");
        }
        if (sourceGrant.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("一次性 secret export token 已过期。");
        }
        String fullKey = credentialCryptoService.decrypt(sourceGrant.getFullKeyCiphertext());
        sourceGrant.setConsumedAt(Instant.now());
        secretExportGrantRepository.save(sourceGrant);
        return fullKey;
    }

    private ClientInstanceEntity getRequired(Long id) {
        return clientInstanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定 client instance。"));
    }

    private DistributedKeyEntity getDistributedKey(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("distributedKeyId 不能为空。");
        }
        return distributedKeyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定 DistributedKey。"));
    }

    private ClientInstanceGrantEntity requireGrant(Long clientInstanceId, String grantToken) {
        if (grantToken == null || grantToken.isBlank()) {
            throw new IllegalArgumentException("client instance 授权 token 不能为空。");
        }
        return clientInstanceGrantRepository.findByClientInstance_IdAndTokenHash(
                        clientInstanceId,
                        credentialCryptoService.fingerprint(grantToken)
                )
                .orElseThrow(() -> new IllegalArgumentException("client instance 授权 token 不存在。"));
    }

    private void validateConsumable(ClientInstanceGrantEntity grant) {
        if (grant.isRevoked()) {
            throw new IllegalArgumentException("client instance 授权 token 已撤销。");
        }
        if (grant.isConsumed()) {
            throw new IllegalArgumentException("client instance 授权 token 已被使用。");
        }
        if (grant.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("client instance 授权 token 已过期。");
        }
    }

    private ClientInstanceAuthorizationResponse toGrantResponse(
            ClientInstanceEntity instance,
            ClientInstanceGrantEntity grant,
            String grantToken) {
        return new ClientInstanceAuthorizationResponse(
                instance.getId(),
                instance.getInstanceId(),
                instance.getClientFamily(),
                grantToken,
                grant.getExpiresAt(),
                grant.isConsumed(),
                grant.isRevoked(),
                buildDeepLink(instance, grant, grantToken),
                buildPluginMessage(instance, grant, grantToken),
                "Deep Link 和 plugin message 只携带一次性 grantToken，不携带完整长期 secret。"
        );
    }

    private ClientInstanceResponse toResponse(ClientInstanceEntity entity) {
        DistributedKeyEntity key = entity.getDistributedKey();
        return new ClientInstanceResponse(
                entity.getId(),
                key == null ? null : key.getId(),
                key == null ? null : key.getKeyName(),
                key == null ? null : key.getMaskedKey(),
                entity.getInstanceId(),
                entity.getDisplayName(),
                entity.getClientFamily(),
                entity.getWorkspaceHint(),
                entity.getPluginName(),
                entity.getPluginVersion(),
                entity.getDeepLinkScheme(),
                entity.getStatus(),
                entity.getLastAuthorizedAt(),
                entity.getLastSeenAt(),
                entity.getLastRequestAt(),
                entity.getLastRequestId(),
                entity.getDisabledAt(),
                entity.getRevokedAt(),
                entity.getMetadataJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String buildDeepLink(ClientInstanceEntity instance, ClientInstanceGrantEntity grant, String grantToken) {
        String scheme = defaultString(instance.getDeepLinkScheme(), "xag");
        return scheme + "://authorize/client-instance"
                + "?grantToken=" + encode(grantToken)
                + "&baseUrl=" + encode(grant.getBaseUrl() + "/v1")
                + "&clientFamily=" + encode(instance.getClientFamily())
                + "&clientInstance=" + encode(instance.getInstanceId())
                + "&workspaceHint=" + encode(instance.getWorkspaceHint())
                + "&format=" + encode(grant.getConfigFormat());
    }

    private String buildPluginMessage(ClientInstanceEntity instance, ClientInstanceGrantEntity grant, String grantToken) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", grantToken == null ? "x-ai-gateway.client_config" : "x-ai-gateway.client_authorization");
        message.put("grantToken", grantToken);
        message.put("baseUrl", grant.getBaseUrl() + "/v1");
        message.put("clientFamily", instance.getClientFamily());
        message.put("clientInstance", instance.getInstanceId());
        message.put("workspaceHint", instance.getWorkspaceHint());
        message.put("format", grant.getConfigFormat());
        message.put("expiresAt", grant.getExpiresAt().toString());
        message.put("secretPolicy", "one_time_grant");
        return writeJson(message);
    }

    private String renderClientConfig(
            String normalizedFormat,
            String clientFamily,
            String normalizedBaseUrl,
            String apiKey,
            String instanceId,
            String workspaceHint) {
        String apiBaseUrl = normalizedBaseUrl + "/v1";
        String safeWorkspace = defaultString(workspaceHint, "default");
        return switch (normalizedFormat) {
            case "auth_json" -> """
                    {
                      "OPENAI_API_KEY": "%s",
                      "OPENAI_BASE_URL": "%s",
                      "X_AI_GATEWAY_API_KEY": "%s",
                      "X_AI_GATEWAY_CLIENT_FAMILY": "%s",
                      "X_AI_GATEWAY_CLIENT_INSTANCE": "%s",
                      "X_AI_GATEWAY_WORKSPACE_HINT": "%s"
                    }
                    """.formatted(apiKey, apiBaseUrl, apiKey, clientFamily, instanceId, safeWorkspace).trim();
            case "env" -> """
                    export OPENAI_API_KEY="%s"
                    export OPENAI_BASE_URL="%s"
                    export X_AI_GATEWAY_API_KEY="%s"
                    export X_AI_GATEWAY_CLIENT_FAMILY="%s"
                    export X_AI_GATEWAY_CLIENT_INSTANCE="%s"
                    export X_AI_GATEWAY_WORKSPACE_HINT="%s"
                    export ANTHROPIC_API_KEY="%s"
                    export ANTHROPIC_BASE_URL="%s"
                    export GEMINI_API_KEY="%s"
                    export GEMINI_BASE_URL="%s"
                    """.formatted(apiKey, apiBaseUrl, apiKey, clientFamily, instanceId, safeWorkspace, apiKey, apiBaseUrl, apiKey, apiBaseUrl).trim();
            case "curl" -> """
                    curl %s/chat/completions \\
                      -H "Authorization: Bearer %s" \\
                      -H "Content-Type: application/json" \\
                      -H "X-AI-Gateway-Client-Family: %s" \\
                      -H "X-AI-Gateway-Client-Instance: %s" \\
                      -H "X-AI-Gateway-Workspace-Hint: %s" \\
                      -d '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"ping"}]}'
                    """.formatted(apiBaseUrl, apiKey, clientFamily, instanceId, safeWorkspace).trim();
            default -> """
                    [model_providers.x-ai-gateway]
                    name = "x-ai-gateway"
                    base_url = "%s"
                    env_key = "X_AI_GATEWAY_API_KEY"
                    wire_api = "chat"
                    client_family = "%s"
                    client_instance = "%s"
                    workspace_hint = "%s"

                    # 该值由一次性授权下发；消费后 grantToken 立即失效。
                    api_key = "%s"
                    """.formatted(apiBaseUrl, clientFamily, instanceId, safeWorkspace, apiKey).trim();
        };
    }

    private Instant resolveExpiresAt(ClientInstanceAuthorizationRequest request, Instant now) {
        if (request != null && request.expiresAt() != null) {
            return request.expiresAt();
        }
        int ttlSeconds = request == null || request.ttlSeconds() == null
                ? (int) DEFAULT_GRANT_TTL.toSeconds()
                : Math.max(60, Math.min(MAX_GRANT_TTL_SECONDS, request.ttlSeconds()));
        return now.plusSeconds(ttlSeconds);
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
            case "curl", "curl_config" -> "curl";
            default -> throw new IllegalArgumentException("不支持的客户端配置格式。");
        };
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "PLUGIN";
        }
        String normalized = source.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "PLUGIN", "DEEPLINK", "DEEP_LINK", "ADMIN" -> "DEEP_LINK".equals(normalized) ? "DEEPLINK" : normalized;
            default -> throw new IllegalArgumentException("不支持的 client instance 授权来源。");
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

    private String normalizeInstanceId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("instanceId 不能为空。");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "-")
                .replaceAll("-+", "-");
        if (normalized.length() > 128) {
            normalized = normalized.substring(0, 128);
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("instanceId 归一化后不能为空。");
        }
        return normalized;
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key != null && !key.isBlank()) {
                sanitized.put(key, isSensitiveKey(key) ? "***" : value);
            }
        });
        return sanitized;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("api_key")
                || normalized.contains("apikey")
                || normalized.contains("authorization")
                || normalized.contains("cookie");
    }

    private Map<String, Object> readMetadataMap(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(metadataJson, new TypeReference<>() {
            });
            return parsed == null ? Map.of() : parsed;
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("无法解析 client instance metadataJson。", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("无法序列化 client instance JSON。", exception);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private void recordAudit(ClientInstanceEntity entity, String action, String detail) {
        opsAuditService.ifPresent(service -> service.record(
                "CLIENT_INSTANCE",
                action,
                "ClientInstance",
                entity.getId() == null ? entity.getInstanceId() : String.valueOf(entity.getId()),
                "{"
                        + "\"distributedKeyId\":\"" + (entity.getDistributedKey() == null ? "" : entity.getDistributedKey().getId()) + "\","
                        + "\"instanceId\":\"" + escapeJson(entity.getInstanceId()) + "\","
                        + "\"clientFamily\":\"" + escapeJson(entity.getClientFamily()) + "\","
                        + "\"detail\":\"" + escapeJson(detail) + "\""
                        + "}"
        ));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
