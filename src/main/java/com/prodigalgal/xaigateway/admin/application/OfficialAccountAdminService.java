package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OfficialAccountImportRequest;
import com.prodigalgal.xaigateway.admin.api.OfficialAccountQuotaRefreshRequest;
import com.prodigalgal.xaigateway.admin.api.OfficialAccountQuotaResponse;
import com.prodigalgal.xaigateway.admin.api.OfficialAccountType;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountPoolEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountPoolRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class OfficialAccountAdminService {

    private static final String IMPORT_REFRESH_ADAPTER = "official-account-quota-local";

    private final UpstreamAccountRepository upstreamAccountRepository;
    private final UpstreamAccountPoolRepository upstreamAccountPoolRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final SupportedModelCatalogService supportedModelCatalogService;
    private final ObjectMapper objectMapper;

    public OfficialAccountAdminService(
            UpstreamAccountRepository upstreamAccountRepository,
            UpstreamAccountPoolRepository upstreamAccountPoolRepository,
            CredentialCryptoService credentialCryptoService,
            SupportedModelCatalogService supportedModelCatalogService,
            ObjectMapper objectMapper) {
        this.upstreamAccountRepository = upstreamAccountRepository;
        this.upstreamAccountPoolRepository = upstreamAccountPoolRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.supportedModelCatalogService = supportedModelCatalogService;
        this.objectMapper = objectMapper;
    }

    public OfficialAccountQuotaResponse importOfficialAccount(OfficialAccountImportRequest request) {
        OfficialAccountType accountType = request.accountType();
        UpstreamAccountPoolEntity pool = resolvePool(request.poolId(), accountType);
        String accessToken = requireSecret(request.accessToken(), "accessToken");
        String refreshToken = normalizeBlank(request.refreshToken());
        Instant now = Instant.now();

        UpstreamAccountEntity entity = new UpstreamAccountEntity();
        entity.setPool(pool);
        entity.setProviderType(accountType.providerType());
        entity.setAccountName(resolveAccountName(request.accountName(), accountType));
        entity.setExternalAccountId(resolveExternalAccountId(request.externalAccountId(), accountType));
        entity.setAccessTokenCiphertext(credentialCryptoService.encrypt(accessToken));
        entity.setRefreshTokenCiphertext(refreshToken == null ? null : credentialCryptoService.encrypt(refreshToken));
        entity.setActive(request.active() == null || request.active());
        entity.setFrozen(false);
        entity.setHealthy(true);
        entity.setTokenExpiresAt(request.tokenExpiresAt());
        entity.setLastRefreshAt(now);
        entity.setRefreshStatus("IMPORTED");
        entity.setSupportedModels(resolveSupportedModels(pool, request.supportedModels(), accountType));
        entity.setProxyId(request.proxyId());
        entity.setTlsFingerprintProfileId(request.tlsFingerprintProfileId());
        entity.setSiteProfileId(request.siteProfileId());

        Map<String, Object> metadata = sanitizeMetadata(readMetadataMap(request.metadataJson()));
        metadata.put("official_account_type", accountType.name());
        metadata.put("client_family", accountType.clientFamily());
        metadata.put("managed_by", "official_account_import");
        metadata.put("imported_at", now.toString());
        entity.setMetadataJson(writeJson(metadata));

        if (request.refreshQuotaAfterImport() == null || request.refreshQuotaAfterImport()) {
            applyQuotaSuccess(entity, accountType, QuotaInput.fromImport(request), now, "import");
        } else {
            entity.setLastRefreshResultJson(writeJson(Map.of(
                    "status", "imported",
                    "accountType", accountType.name(),
                    "importedAt", now.toString(),
                    "quotaRefresh", "skipped"
            )));
        }

        return toResponse(upstreamAccountRepository.save(entity));
    }

    public OfficialAccountQuotaResponse refreshQuota(Long accountId, OfficialAccountQuotaRefreshRequest request) {
        UpstreamAccountEntity entity = getRequired(accountId);
        OfficialAccountType accountType = resolveOfficialAccountType(entity);
        Instant now = Instant.now();
        QuotaInput input = QuotaInput.fromRefresh(request);
        if (Boolean.TRUE.equals(input.forceFailure()) || !isBlank(input.quotaError())) {
            applyQuotaFailure(entity, accountType, defaultString(input.quotaError(), "官方账号配额刷新失败。"), now);
        } else {
            applyQuotaSuccess(entity, accountType, input, now, "manual_refresh");
        }
        return toResponse(upstreamAccountRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public OfficialAccountQuotaResponse quota(Long accountId) {
        return toResponse(getRequired(accountId));
    }

    private UpstreamAccountEntity getRequired(Long accountId) {
        return upstreamAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定官方账号。"));
    }

    private UpstreamAccountPoolEntity resolvePool(Long poolId, OfficialAccountType accountType) {
        if (poolId == null) {
            return null;
        }
        UpstreamAccountPoolEntity pool = upstreamAccountPoolRepository.findById(poolId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定账号池。"));
        if (pool.getProviderType() != accountType.providerType()) {
            throw new IllegalArgumentException("账号池 providerType 与官方账号类型不匹配。");
        }
        return pool;
    }

    private List<String> resolveSupportedModels(
            UpstreamAccountPoolEntity pool,
            List<String> requestedModels,
            OfficialAccountType accountType) {
        List<String> normalized = supportedModelCatalogService.normalize(requestedModels);
        if (!normalized.isEmpty()) {
            return normalized;
        }
        if (pool != null && pool.getSupportedModels() != null && !pool.getSupportedModels().isEmpty()) {
            return supportedModelCatalogService.normalize(pool.getSupportedModels());
        }
        return accountType.defaultModels();
    }

    private void applyQuotaSuccess(
            UpstreamAccountEntity entity,
            OfficialAccountType accountType,
            QuotaInput input,
            Instant now,
            String trigger) {
        int windowSeconds = input.quotaWindowSeconds() == null || input.quotaWindowSeconds() <= 0
                ? accountType.defaultQuotaWindowSeconds()
                : input.quotaWindowSeconds();
        Instant resetAt = input.quotaResetAt() == null ? now.plusSeconds(windowSeconds) : input.quotaResetAt();
        Instant windowStartedAt = resetAt.minusSeconds(windowSeconds);
        Instant nextRefreshAfter = nextRefreshAfter(now, resetAt, windowSeconds);
        String planTier = defaultString(input.planTier(), accountType.defaultPlanTier());
        String subscriptionTier = defaultString(input.subscriptionTier(), planTier);
        Long remainingTokens = input.quotaRemainingTokens() == null
                ? accountType.defaultQuotaRemainingTokens()
                : Math.max(0, input.quotaRemainingTokens());
        Long remainingRequests = input.quotaRemainingRequests() == null
                ? accountType.defaultQuotaRemainingRequests()
                : Math.max(0, input.quotaRemainingRequests());

        entity.setLastRefreshAt(now);
        entity.setRefreshStatus("QUOTA_READY");
        entity.setRefreshFailureCount(0);
        entity.setNextRefreshAfter(nextRefreshAfter);
        entity.setCooldownUntil(null);
        entity.setHealthy(true);
        entity.setLastErrorMessage(null);
        entity.setQuotaWindowStartedAt(windowStartedAt);
        entity.setQuotaWindowSeconds(windowSeconds);
        entity.setQuotaRemainingTokens(remainingTokens);
        entity.setQuotaRemainingRequests(remainingRequests);

        Map<String, Object> metadata = readMetadataMap(entity.getMetadataJson());
        metadata.put("official_account_type", accountType.name());
        metadata.put("client_family", accountType.clientFamily());
        metadata.put("plan_tier", planTier);
        metadata.put("subscription_tier", subscriptionTier);
        metadata.put("quota_status", "READY");
        metadata.put("quota_reset_at", resetAt.toString());
        metadata.put("quota_last_refresh_at", now.toString());
        metadata.put("quota_next_refresh_after", nextRefreshAfter.toString());
        metadata.remove("quota_error");
        entity.setMetadataJson(writeJson(sanitizeMetadata(metadata)));
        entity.setLastRefreshResultJson(writeJson(Map.of(
                "status", "refreshed",
                "adapter", IMPORT_REFRESH_ADAPTER,
                "trigger", trigger,
                "accountType", accountType.name(),
                "planTier", planTier,
                "subscriptionTier", subscriptionTier,
                "quotaResetAt", resetAt.toString(),
                "nextRefreshAfter", nextRefreshAfter.toString(),
                "refreshedAt", now.toString()
        )));
    }

    private void applyQuotaFailure(
            UpstreamAccountEntity entity,
            OfficialAccountType accountType,
            String errorMessage,
            Instant now) {
        int failureCount = entity.getRefreshFailureCount() + 1;
        Instant nextRefreshAfter = now.plus(cooldownDuration(failureCount));
        String truncated = truncate(errorMessage, 512);
        entity.setLastRefreshAt(now);
        entity.setRefreshStatus("QUOTA_FAILED");
        entity.setRefreshFailureCount(failureCount);
        entity.setNextRefreshAfter(nextRefreshAfter);
        entity.setCooldownUntil(nextRefreshAfter);
        entity.setHealthy(false);
        entity.setLastErrorMessage(truncated);

        Map<String, Object> metadata = readMetadataMap(entity.getMetadataJson());
        metadata.put("official_account_type", accountType.name());
        metadata.put("client_family", accountType.clientFamily());
        metadata.put("quota_status", "ERROR");
        metadata.put("quota_error", truncated);
        metadata.put("quota_last_refresh_at", now.toString());
        metadata.put("quota_next_refresh_after", nextRefreshAfter.toString());
        entity.setMetadataJson(writeJson(sanitizeMetadata(metadata)));
        entity.setLastRefreshResultJson(writeJson(Map.of(
                "status", "failed",
                "adapter", IMPORT_REFRESH_ADAPTER,
                "accountType", accountType.name(),
                "failureCount", String.valueOf(failureCount),
                "errorMessage", truncated,
                "nextRefreshAfter", nextRefreshAfter.toString(),
                "refreshedAt", now.toString()
        )));
    }

    private OfficialAccountQuotaResponse toResponse(UpstreamAccountEntity entity) {
        OfficialAccountType accountType = resolveOfficialAccountType(entity);
        Map<String, Object> metadata = readMetadataMap(entity.getMetadataJson());
        Instant quotaResetAt = parseInstant(text(metadata.get("quota_reset_at")));
        if (quotaResetAt == null && entity.getQuotaWindowStartedAt() != null && entity.getQuotaWindowSeconds() != null) {
            quotaResetAt = entity.getQuotaWindowStartedAt().plusSeconds(entity.getQuotaWindowSeconds());
        }
        String routeBlockReason = routeBlockReason(entity);
        return new OfficialAccountQuotaResponse(
                entity.getId(),
                entity.getPool() == null ? null : entity.getPool().getId(),
                entity.getAccountName(),
                accountType,
                entity.getProviderType(),
                supportedModelCatalogService.normalize(entity.getSupportedModels()),
                entity.getExternalAccountId(),
                defaultString(text(metadata.get("plan_tier")), accountType.defaultPlanTier()),
                defaultString(text(metadata.get("subscription_tier")), accountType.defaultPlanTier()),
                defaultString(text(metadata.get("quota_status")), entity.getRefreshStatus()),
                entity.getQuotaWindowStartedAt(),
                entity.getQuotaWindowSeconds(),
                quotaResetAt,
                entity.getQuotaRemainingTokens(),
                entity.getQuotaRemainingRequests(),
                entity.getLastRefreshAt(),
                entity.getNextRefreshAfter(),
                entity.getRefreshStatus(),
                entity.getRefreshFailureCount(),
                entity.isActive(),
                entity.isFrozen(),
                entity.isHealthy(),
                routeBlockReason == null,
                routeBlockReason,
                firstNonBlank(text(metadata.get("quota_error")), entity.getLastErrorMessage()),
                entity.getLastRefreshResultJson()
        );
    }

    private OfficialAccountType resolveOfficialAccountType(UpstreamAccountEntity entity) {
        Map<String, Object> metadata = readMetadataMap(entity.getMetadataJson());
        String rawType = text(metadata.get("official_account_type"));
        if (!isBlank(rawType)) {
            try {
                return OfficialAccountType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("未知官方账号类型：" + rawType);
            }
        }
        UpstreamAccountProviderType providerType = entity.getProviderType();
        if (providerType == UpstreamAccountProviderType.CODEX_OAUTH) {
            return OfficialAccountType.CODEX;
        }
        if (providerType == UpstreamAccountProviderType.COPILOT_OAUTH) {
            return OfficialAccountType.GITHUB_COPILOT;
        }
        if (providerType == UpstreamAccountProviderType.GEMINI_OAUTH || providerType == UpstreamAccountProviderType.ANTIGRAVITY_OAUTH) {
            return OfficialAccountType.GEMINI_CLI;
        }
        throw new IllegalArgumentException("该账号不是受支持的 AI IDE/CLI 官方账号：" + providerType);
    }

    private Instant nextRefreshAfter(Instant now, Instant resetAt, int windowSeconds) {
        long refreshSeconds = Math.max(300, Math.min(3_600, windowSeconds / 4L));
        Instant next = now.plusSeconds(refreshSeconds);
        if (next.isBefore(resetAt)) {
            return next;
        }
        Instant beforeReset = resetAt.minusSeconds(Math.max(60, Math.min(300, windowSeconds / 10L)));
        return beforeReset.isAfter(now) ? beforeReset : now.plusSeconds(300);
    }

    private Duration cooldownDuration(int failureCount) {
        long minutes = Math.min(60, Math.max(5, failureCount * 5L));
        return Duration.ofMinutes(minutes);
    }

    private String routeBlockReason(UpstreamAccountEntity entity) {
        if (!entity.isActive()) {
            return "ACCOUNT_INACTIVE";
        }
        if (entity.isFrozen()) {
            return "ACCOUNT_FROZEN";
        }
        if (!entity.isHealthy()) {
            return "ACCOUNT_UNHEALTHY";
        }
        if (entity.getQuotaRemainingTokens() != null && entity.getQuotaRemainingTokens() <= 0) {
            return "QUOTA_TOKENS_EXHAUSTED";
        }
        if (entity.getQuotaRemainingRequests() != null && entity.getQuotaRemainingRequests() <= 0) {
            return "QUOTA_REQUESTS_EXHAUSTED";
        }
        return null;
    }

    private String resolveAccountName(String accountName, OfficialAccountType accountType) {
        if (!isBlank(accountName)) {
            return accountName.trim();
        }
        return accountType.externalPrefix() + "-" + Instant.now().toEpochMilli();
    }

    private String resolveExternalAccountId(String externalAccountId, OfficialAccountType accountType) {
        if (!isBlank(externalAccountId)) {
            return externalAccountId.trim();
        }
        return accountType.externalPrefix() + ":" + Instant.now().toEpochMilli();
    }

    private String requireSecret(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空。");
        }
        return value.trim();
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key == null || isBlank(key)) {
                return;
            }
            sanitized.put(key, sanitizeValue(key, value));
        });
        return sanitized;
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeValue(String key, Object value) {
        if (isSensitiveKey(key)) {
            return "***";
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> nested = new LinkedHashMap<>();
            mapValue.forEach((nestedKey, nestedValue) -> {
                if (nestedKey != null) {
                    nested.put(String.valueOf(nestedKey), sanitizeValue(String.valueOf(nestedKey), nestedValue));
                }
            });
            return nested;
        }
        if (value instanceof List<?> listValue) {
            List<Object> sanitizedList = new ArrayList<>();
            for (Object child : listValue) {
                sanitizedList.add(child instanceof Map<?, ?> ? sanitizeValue(key, child) : child);
            }
            return sanitizedList;
        }
        return value;
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
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(metadataJson, new TypeReference<>() {
            });
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (JacksonException exception) {
            return new LinkedHashMap<>();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("无法序列化官方账号 metadata。", exception);
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record QuotaInput(
            String planTier,
            String subscriptionTier,
            Integer quotaWindowSeconds,
            Long quotaRemainingTokens,
            Long quotaRemainingRequests,
            Instant quotaResetAt,
            String quotaError,
            Boolean forceFailure) {

        static QuotaInput fromImport(OfficialAccountImportRequest request) {
            return new QuotaInput(
                    request.planTier(),
                    request.subscriptionTier(),
                    request.quotaWindowSeconds(),
                    request.quotaRemainingTokens(),
                    request.quotaRemainingRequests(),
                    request.quotaResetAt(),
                    null,
                    false
            );
        }

        static QuotaInput fromRefresh(OfficialAccountQuotaRefreshRequest request) {
            if (request == null) {
                return new QuotaInput(null, null, null, null, null, null, null, false);
            }
            return new QuotaInput(
                    request.planTier(),
                    request.subscriptionTier(),
                    request.quotaWindowSeconds(),
                    request.quotaRemainingTokens(),
                    request.quotaRemainingRequests(),
                    request.quotaResetAt(),
                    request.quotaError(),
                    request.forceFailure()
            );
        }
    }
}
