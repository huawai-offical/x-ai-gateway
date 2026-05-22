package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.DistributedKeyCreateResponse;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyClientConfigResponse;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyInitialAccountGroupBindingRequest;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyOnboardingDeepLinkResponse;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyOnboardingPackResponse;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyOnboardingSnippetResponse;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyRequest;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyResponse;
import com.prodigalgal.xaigateway.admin.api.DistributedKeySecretExportGrantResponse;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeySecretService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeySecrets;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.gateway.core.shared.ProtocolSuite;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccountGroupBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeySecretExportGrantEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountGroupBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccessGroupGrantRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeySecretExportGrantRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountGroupRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DistributedKeyAdminService {

    private final DistributedKeyRepository distributedKeyRepository;
    private final DistributedKeySecretService distributedKeySecretService;
    private final DistributedKeyBindingRepository distributedKeyBindingRepository;
    private final DistributedKeyAccountGroupBindingRepository distributedKeyAccountGroupBindingRepository;
    private final DistributedKeyAccessGroupGrantRepository keyGrantRepository;
    private final DistributedKeySecretExportGrantRepository secretExportGrantRepository;
    private final GatewayUserRepository gatewayUserRepository;
    private final UpstreamAccountGroupRepository upstreamAccountGroupRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final Optional<OpsAuditService> opsAuditService;
    private static final Duration SECRET_EXPORT_TTL = Duration.ofMinutes(15);

    public DistributedKeyAdminService(
            DistributedKeyRepository distributedKeyRepository,
            DistributedKeySecretService distributedKeySecretService,
            DistributedKeyBindingRepository distributedKeyBindingRepository,
            DistributedKeyAccountGroupBindingRepository distributedKeyAccountGroupBindingRepository,
            DistributedKeyAccessGroupGrantRepository keyGrantRepository,
            DistributedKeySecretExportGrantRepository secretExportGrantRepository,
            GatewayUserRepository gatewayUserRepository,
            UpstreamAccountGroupRepository upstreamAccountGroupRepository,
            CredentialCryptoService credentialCryptoService,
            Optional<OpsAuditService> opsAuditService) {
        this.distributedKeyRepository = distributedKeyRepository;
        this.distributedKeySecretService = distributedKeySecretService;
        this.distributedKeyBindingRepository = distributedKeyBindingRepository;
        this.distributedKeyAccountGroupBindingRepository = distributedKeyAccountGroupBindingRepository;
        this.keyGrantRepository = keyGrantRepository;
        this.secretExportGrantRepository = secretExportGrantRepository;
        this.gatewayUserRepository = gatewayUserRepository;
        this.upstreamAccountGroupRepository = upstreamAccountGroupRepository;
        this.credentialCryptoService = credentialCryptoService;
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
        entity.setKeyPrefix(secrets.keyPrefix());
        entity.setSecretHash(secrets.secretHash());
        entity.setMaskedKey(secrets.maskedKey());
        DistributedKeyEntity saved = distributedKeyRepository.save(entity);
        createInitialAccountGroupBindings(saved, request.resolvedInitialAccountGroupBindings());
        if (saved.isActive()) {
            assertHasActiveGroupBinding(saved.getId());
        }
        IssuedSecretExportGrant grant = registerSecretExportGrant(saved, secrets.fullKey(), "CREATE");
        return new DistributedKeyCreateResponse(toResponse(saved), secrets.fullKey(), grant.token(), grant.expiresAt());
    }

    public DistributedKeyResponse update(Long id, DistributedKeyRequest request) {
        DistributedKeyEntity entity = getRequired(id);
        apply(entity, request, false);
        if (entity.isActive()) {
            assertHasActiveGroupBinding(id);
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
        IssuedSecretExportGrant grant = registerSecretExportGrant(saved, secrets.fullKey(), "ROTATE");
        return new DistributedKeyCreateResponse(toResponse(saved), secrets.fullKey(), grant.token(), grant.expiresAt());
    }

    public DistributedKeyResponse toggle(Long id, boolean active) {
        DistributedKeyEntity entity = getRequired(id);
        if (active) {
            assertHasActiveGroupBinding(id);
        }
        entity.setActive(active);
        return toResponse(distributedKeyRepository.save(entity));
    }

    public void delete(Long id) {
        DistributedKeyEntity entity = getRequired(id);
        distributedKeyBindingRepository.deleteAllByDistributedKey_Id(id);
        distributedKeyAccountGroupBindingRepository.deleteAllByDistributedKey_Id(id);
        keyGrantRepository.deleteAllByDistributedKey_Id(id);
        secretExportGrantRepository.deleteAllByDistributedKey_Id(id);
        distributedKeyRepository.delete(entity);
    }

    public DistributedKeyClientConfigResponse exportClientConfig(Long id, String format, String clientFamily, String baseUrl) {
        DistributedKeyEntity entity = getRequired(id);
        String normalizedFormat = normalizeConfigFormat(format);
        String normalizedClientFamily = GatewayClientFamily.from(clientFamily).name();
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String maskedKey = entity.getMaskedKey() == null || entity.getMaskedKey().isBlank()
                ? entity.getKeyPrefix() + "..."
                : entity.getMaskedKey();
        String secretPlaceholder = "<仅创建或轮换时展示一次；当前为 " + maskedKey + ">";
        recordClientConfigExport(entity, normalizedFormat, normalizedClientFamily, normalizedBaseUrl, "masked_only");
        return buildClientConfigResponse(
                entity,
                normalizedFormat,
                normalizedClientFamily,
                normalizedBaseUrl,
                secretPlaceholder,
                "安全导出不会返回完整 secret；完整 key 仅在创建或轮换后的短窗口内可一次性下载。"
        );
    }

    public DistributedKeyOnboardingPackResponse exportOnboardingPack(Long id, String baseUrl) {
        DistributedKeyEntity entity = getRequired(id);
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String apiBaseUrl = normalizedBaseUrl + "/v1";
        String maskedKey = maskedKey(entity);
        String secretPlaceholder = "<仅创建或轮换时展示一次；当前为 " + maskedKey + ">";
        List<DistributedKeyOnboardingSnippetResponse> clientConfigs = List.of(
                snippet("Codex config.toml", GatewayClientFamily.CODEX, "config_toml", normalizedBaseUrl, secretPlaceholder),
                snippet("Claude Code shell env", GatewayClientFamily.CLAUDE_CODE, "env", normalizedBaseUrl, secretPlaceholder),
                snippet("Gemini CLI shell env", GatewayClientFamily.GEMINI_CLI, "env", normalizedBaseUrl, secretPlaceholder),
                snippet("OpenCode config.toml", GatewayClientFamily.OPENCODE, "config_toml", normalizedBaseUrl, secretPlaceholder),
                snippet("OpenClaw config.toml", GatewayClientFamily.OPENCLAW, "config_toml", normalizedBaseUrl, secretPlaceholder),
                snippet("Cursor OpenAI-compatible env", GatewayClientFamily.CURSOR, "env", normalizedBaseUrl, secretPlaceholder),
                snippet("Windsurf OpenAI-compatible env", GatewayClientFamily.WINDSURF, "env", normalizedBaseUrl, secretPlaceholder),
                snippet("Kiro OpenAI-compatible env", GatewayClientFamily.KIRO, "env", normalizedBaseUrl, secretPlaceholder),
                snippet("GitHub Copilot-compatible env", GatewayClientFamily.GITHUB_COPILOT, "env", normalizedBaseUrl, secretPlaceholder),
                snippet("curl smoke", GatewayClientFamily.GENERIC_OPENAI, "curl", normalizedBaseUrl, secretPlaceholder)
        );
        List<DistributedKeyOnboardingDeepLinkResponse> deepLinks = List.of(
                new DistributedKeyOnboardingDeepLinkResponse(
                        "xag scheme import",
                        "xag",
                        "xag://import/client-config?keyName=" + encode(entity.getKeyName())
                                + "&baseUrl=" + encode(apiBaseUrl)
                                + "&secret=one-time-or-local-secret"
                                + "&maskedKey=" + encode(maskedKey),
                        "Deep Link 不携带完整 secret；用户只导入云端 endpoint 元数据，不需要本地 proxy。"
                ),
                new DistributedKeyOnboardingDeepLinkResponse(
                        "HTTPS import",
                        "https",
                        normalizedBaseUrl + "/portal/client-import?keyName=" + encode(entity.getKeyName())
                                + "&baseUrl=" + encode(apiBaseUrl),
                        "HTTPS 导入入口只传递配置元数据，完整 secret 仍遵循一次性导出策略。"
                )
        );
        recordClientConfigExport(entity, "onboarding_pack", "MULTI_CLI", normalizedBaseUrl, "masked_only");
        return new DistributedKeyOnboardingPackResponse(
                entity.getKeyName(),
                maskedKey,
                normalizedBaseUrl,
                apiBaseUrl,
                "完整 secret 仅在创建或轮换后通过一次性 token 消费；接入包默认只返回 masked key 占位。",
                clientConfigs,
                deepLinks,
                buildMcpServerConfig(apiBaseUrl, secretPlaceholder),
                List.of(
                        "将当前 CLI/AI IDE 统一接入云端 x-ai-gateway endpoint，并优先使用公开模型别名。",
                        "通过 X-AI-Gateway-Client-Family、X-AI-Gateway-Client-Instance、X-AI-Gateway-Workspace-Hint 标记客户端来源。",
                        "当请求失败时，先读取 response error_code，再查看 /admin/observability/traces/{requestId}。",
                        "需要图片、音频、视频或文件能力时，先确认 provider capability matrix 是否标记为 NATIVE。"
                ),
                List.of(
                        "client-config-export：复制或下载当前客户端配置片段。",
                        "cloud-cli-request-filter：在云端执行 replace/remove/mask 请求过滤，不读取用户本地 workspace。",
                        "gateway-trace-reader：根据 requestId 拉取 trace、route decision、cache hit 和 usage。",
                        "provider-smoke-runner：用 curl smoke 示例验证 baseUrl、secret 和模型别名。"
                ),
                List.of(
                        "curl " + apiBaseUrl + "/chat/completions -H \"Authorization: Bearer " + secretPlaceholder
                                + "\" -H \"Content-Type: application/json\" -d '{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}]}'",
                        "curl " + normalizedBaseUrl + "/actuator/health/readiness",
                        "curl " + normalizedBaseUrl + "/admin/observability/summary"
                ),
                List.of(
                        "401：检查 secret 是否为完整值，不要把 masked key 当成真实 key 使用。",
                        "404：确认 baseUrl 是否带了重复 /v1；接入包中的 apiBaseUrl 已经包含 /v1。",
                        "客户端命中错误账号分组：检查 X-AI-Gateway-Client-Family 与 key/access group/account group 的 allowedClientFamilies。",
                        "request filter 未命中：检查 gateway.cli.request-filter.enabled、rule.action、clientFamilies、role 与 contains。",
                        "模型不可用：查看 key 的 allowedModels、allowedProviderTypes 与 route policy runtime state。",
                        "Claude/Gemini CLI 自定义 base URL 不兼容时，改用 OpenAI-compatible 客户端或 OpenCode/OpenClaw 配置。"
                ),
                Instant.now()
        );
    }

    public DistributedKeyClientConfigResponse consumeOneTimeClientConfig(
            Long id,
            String grantToken,
            String format,
            String clientFamily,
            String baseUrl) {
        DistributedKeyEntity entity = getRequired(id);
        DistributedKeySecretExportGrantEntity grant = requireGrant(id, grantToken);
        if (grant.isRevoked()) {
            throw new IllegalArgumentException("一次性配置下载 token 已撤销。");
        }
        if (grant.isConsumed()) {
            throw new IllegalArgumentException("一次性配置下载 token 已被使用。");
        }
        if (grant.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("一次性配置下载 token 已过期，请重新轮换 key。");
        }
        String fullKey = credentialCryptoService.decrypt(grant.getFullKeyCiphertext());
        grant.setConsumedAt(Instant.now());
        secretExportGrantRepository.save(grant);
        String normalizedFormat = normalizeConfigFormat(format);
        String normalizedClientFamily = GatewayClientFamily.from(clientFamily).name();
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        recordClientConfigExport(entity, normalizedFormat, normalizedClientFamily, normalizedBaseUrl, "one_time_secret");
        return buildClientConfigResponse(
                entity,
                normalizedFormat,
                normalizedClientFamily,
                normalizedBaseUrl,
                fullKey,
                "一次性完整 secret 配置已生成；该 token 已消费，不能重复下载。"
        );
    }

    public DistributedKeySecretExportGrantResponse revokeOneTimeClientConfig(Long id, String grantToken) {
        DistributedKeyEntity entity = getRequired(id);
        DistributedKeySecretExportGrantEntity grant = requireGrant(id, grantToken);
        if (!grant.isRevoked()) {
            grant.setRevokedAt(Instant.now());
            secretExportGrantRepository.save(grant);
        }
        opsAuditService.ifPresent(service -> service.record(
                "DISTRIBUTED_KEY",
                "REVOKE_CLIENT_CONFIG_EXPORT",
                "DistributedKey",
                String.valueOf(entity.getId()),
                "{\"keyName\":\"" + escapeJson(entity.getKeyName()) + "\",\"grantTokenHash\":\""
                        + escapeJson(credentialCryptoService.fingerprint(grantToken)) + "\"}"
        ));
        return toGrantResponse(entity, grant, grantToken);
    }

    private DistributedKeyClientConfigResponse buildClientConfigResponse(
            DistributedKeyEntity entity,
            String normalizedFormat,
            String normalizedClientFamily,
            String normalizedBaseUrl,
            String apiKey,
            String warning) {
        String apiBaseUrl = normalizedBaseUrl + "/v1";
        String config = renderClientConfig(normalizedFormat, normalizedClientFamily, normalizedBaseUrl, apiKey);
        String maskedKey = maskedKey(entity);
        return new DistributedKeyClientConfigResponse(
                entity.getKeyName(),
                normalizedClientFamily,
                normalizedFormat,
                maskedKey,
                warning,
                config
        );
    }

    private String renderClientConfig(
            String normalizedFormat,
            String normalizedClientFamily,
            String normalizedBaseUrl,
            String apiKey) {
        String apiBaseUrl = normalizedBaseUrl + "/v1";
        return switch (normalizedFormat) {
            case "auth_json" -> """
                    {
                      "OPENAI_API_KEY": "%s",
                      "OPENAI_BASE_URL": "%s",
                      "X_AI_GATEWAY_API_KEY": "%s",
                      "X_AI_GATEWAY_CLIENT_FAMILY": "%s",
                      "X_AI_GATEWAY_CLIENT_INSTANCE": "default",
                      "X_AI_GATEWAY_WORKSPACE_HINT": "default"
                    }
                    """.formatted(apiKey, apiBaseUrl, apiKey, normalizedClientFamily).trim();
            case "env" -> """
                    export OPENAI_API_KEY="%s"
                    export OPENAI_BASE_URL="%s"
                    export X_AI_GATEWAY_API_KEY="%s"
                    export X_AI_GATEWAY_CLIENT_FAMILY="%s"
                    export X_AI_GATEWAY_CLIENT_INSTANCE="default"
                    export X_AI_GATEWAY_WORKSPACE_HINT="default"
                    export ANTHROPIC_API_KEY="%s"
                    export ANTHROPIC_BASE_URL="%s"
                    export GEMINI_API_KEY="%s"
                    export GEMINI_BASE_URL="%s"
                    """.formatted(
                            apiKey,
                            apiBaseUrl,
                            apiKey,
                            normalizedClientFamily,
                            apiKey,
                            apiBaseUrl,
                            apiKey,
                            apiBaseUrl
                    ).trim();
            case "curl" -> """
                    curl %s/chat/completions \\
                      -H "Authorization: Bearer %s" \\
                      -H "Content-Type: application/json" \\
                      -H "X-AI-Gateway-Client-Family: %s" \\
                      -H "X-AI-Gateway-Client-Instance: default" \\
                      -H "X-AI-Gateway-Workspace-Hint: default" \\
                      -d '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"ping"}]}'
                    """.formatted(apiBaseUrl, apiKey, normalizedClientFamily).trim();
            default -> """
                    [model_providers.x-ai-gateway]
                    name = "x-ai-gateway"
                    base_url = "%s"
                    env_key = "X_AI_GATEWAY_API_KEY"
                    wire_api = "chat"
                    client_family = "%s"
                    client_instance = "default"
                    workspace_hint = "default"

                    # 将下方值写入安全的 secret manager；普通导出只会给出占位符。
                    api_key = "%s"
                    """.formatted(apiBaseUrl, normalizedClientFamily, apiKey).trim();
        };
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
        entity.setAllowedProtocolSuites(normalizeProtocolSuites(request.allowedProtocolSuites()));
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

    private void assertHasActiveGroupBinding(Long distributedKeyId) {
        long activeBindings = distributedKeyAccountGroupBindingRepository
                .countByDistributedKey_IdAndActiveTrueAndGroup_ActiveTrue(distributedKeyId);
        if (activeBindings <= 0) {
            throw new IllegalArgumentException("分发 key 启用前必须先绑定已启用的账号分组。");
        }
    }

    private void createInitialAccountGroupBindings(
            DistributedKeyEntity distributedKey,
            List<DistributedKeyInitialAccountGroupBindingRequest> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        Set<String> seenScopes = new LinkedHashSet<>();
        for (DistributedKeyInitialAccountGroupBindingRequest binding : bindings) {
            if (binding == null) {
                continue;
            }
            Long groupId = binding.groupId();
            ProviderType providerType = binding.providerType();
            if (groupId == null || providerType == null) {
                throw new IllegalArgumentException("初始账号分组绑定必须包含账号分组和运行时 provider。");
            }
            String scope = groupId + ":" + providerType.name();
            if (!seenScopes.add(scope)) {
                throw new IllegalArgumentException("初始账号分组绑定存在重复账号分组和运行时 provider。");
            }
            UpstreamAccountGroupEntity group = upstreamAccountGroupRepository.findById(groupId)
                    .orElseThrow(() -> new IllegalArgumentException("未找到初始绑定的账号分组。"));
            DistributedKeyAccountGroupBindingEntity entity = new DistributedKeyAccountGroupBindingEntity();
            entity.setDistributedKey(distributedKey);
            entity.setGroup(group);
            entity.setProviderType(providerType);
            entity.setPriority(binding.priority() == null ? 100 : binding.priority());
            entity.setActive(binding.active() == null || binding.active());
            distributedKeyAccountGroupBindingRepository.save(entity);
        }
    }

    private List<String> normalizeProtocolSuites(List<String> protocolSuites) {
        return ProtocolSuite.normalizeList(protocolSuites);
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
            case "curl", "curl_config" -> "curl";
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

    private DistributedKeyOnboardingSnippetResponse snippet(
            String name,
            GatewayClientFamily clientFamily,
            String format,
            String normalizedBaseUrl,
            String apiKey) {
        String normalizedFormat = normalizeConfigFormat(format);
        return new DistributedKeyOnboardingSnippetResponse(
                name,
                clientFamily.name(),
                normalizedFormat,
                renderClientConfig(normalizedFormat, clientFamily.name(), normalizedBaseUrl, apiKey)
        );
    }

    private String buildMcpServerConfig(String apiBaseUrl, String apiKey) {
        return """
                {
                  "mcpServers": {
                    "x-ai-gateway": {
                      "command": "npx",
                      "args": ["-y", "@modelcontextprotocol/server-openapi", "%s/openapi.json"],
                      "env": {
                        "X_AI_GATEWAY_API_KEY": "%s",
                        "OPENAI_BASE_URL": "%s"
                      }
                    }
                  }
                }
                """.formatted(apiBaseUrl, apiKey, apiBaseUrl).trim();
    }

    private String maskedKey(DistributedKeyEntity entity) {
        return entity.getMaskedKey() == null || entity.getMaskedKey().isBlank()
                ? entity.getKeyPrefix() + "..."
                : entity.getMaskedKey();
    }

    private String encode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void recordClientConfigExport(
            DistributedKeyEntity entity,
            String format,
            String clientFamily,
            String baseUrl,
            String secretPolicy) {
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
                        + "\"secretPolicy\":\"" + escapeJson(secretPolicy) + "\""
                        + "}"
        ));
    }

    private IssuedSecretExportGrant registerSecretExportGrant(DistributedKeyEntity entity, String fullKey, String sourceAction) {
        String token = UUID.randomUUID() + "-" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(SECRET_EXPORT_TTL);
        DistributedKeySecretExportGrantEntity grant = new DistributedKeySecretExportGrantEntity();
        grant.setDistributedKey(entity);
        grant.setTokenHash(credentialCryptoService.fingerprint(token));
        grant.setFullKeyCiphertext(credentialCryptoService.encrypt(fullKey));
        grant.setSourceAction(sourceAction);
        grant.setExpiresAt(expiresAt);
        secretExportGrantRepository.save(grant);
        opsAuditService.ifPresent(service -> service.record(
                "DISTRIBUTED_KEY",
                "ISSUE_CLIENT_CONFIG_EXPORT",
                "DistributedKey",
                String.valueOf(entity.getId()),
                "{"
                        + "\"keyName\":\"" + escapeJson(entity.getKeyName()) + "\","
                        + "\"sourceAction\":\"" + escapeJson(sourceAction) + "\","
                        + "\"expiresAt\":\"" + expiresAt + "\","
                        + "\"secretPolicy\":\"one_time\""
                        + "}"
        ));
        return new IssuedSecretExportGrant(token, expiresAt);
    }

    private DistributedKeySecretExportGrantEntity requireGrant(Long distributedKeyId, String grantToken) {
        if (grantToken == null || grantToken.isBlank()) {
            throw new IllegalArgumentException("一次性配置下载 token 不能为空。");
        }
        String tokenHash = credentialCryptoService.fingerprint(grantToken);
        return secretExportGrantRepository.findByDistributedKey_IdAndTokenHash(distributedKeyId, tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("一次性配置下载 token 不存在或不属于该 key。"));
    }

    private DistributedKeySecretExportGrantResponse toGrantResponse(
            DistributedKeyEntity entity,
            DistributedKeySecretExportGrantEntity grant,
            String grantToken) {
        return new DistributedKeySecretExportGrantResponse(
                entity.getId(),
                entity.getKeyName(),
                entity.getMaskedKey(),
                grantToken,
                grant.getExpiresAt(),
                grant.isConsumed(),
                grant.isRevoked(),
                "一次性配置下载 token 只在创建/轮换后的短窗口内有效，撤销后不能恢复。"
        );
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
                entity.getAllowedProtocolSuites(),
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

    private record IssuedSecretExportGrant(String token, Instant expiresAt) {
    }
}
