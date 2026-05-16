package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OfficialAccountImportRequest;
import com.prodigalgal.xaigateway.admin.api.OfficialCodexResponsesSmokeRequest;
import com.prodigalgal.xaigateway.admin.api.OfficialCodexResponsesSmokeResponse;
import com.prodigalgal.xaigateway.admin.api.OfficialAccountQuotaRefreshRequest;
import com.prodigalgal.xaigateway.admin.api.OfficialAccountQuotaResponse;
import com.prodigalgal.xaigateway.admin.api.OfficialAccountType;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountPoolEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountPoolRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class OfficialAccountAdminService {

    private static final String IMPORT_REFRESH_ADAPTER = "official-account-quota-local";
    private static final String CODEX_AUTH_JSON_ADAPTER = "codex-auth-json-local-inspection";

    private final UpstreamAccountRepository upstreamAccountRepository;
    private final UpstreamAccountPoolRepository upstreamAccountPoolRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final SupportedModelCatalogService supportedModelCatalogService;
    private final ObjectMapper objectMapper;
    private final CodexAuthJsonParser codexAuthJsonParser;
    private final CodexResponsesSmokeHttpClient codexResponsesSmokeHttpClient;
    private final SensitiveJsonSanitizer sensitiveJsonSanitizer;

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
        this.codexAuthJsonParser = new CodexAuthJsonParser(objectMapper);
        this.codexResponsesSmokeHttpClient = new CodexResponsesSmokeHttpClient(objectMapper);
        this.sensitiveJsonSanitizer = new SensitiveJsonSanitizer(objectMapper);
    }

    public OfficialAccountQuotaResponse importOfficialAccount(OfficialAccountImportRequest request) {
        OfficialAccountType accountType = request.accountType();
        CodexAuthJsonParser.ParsedCodexAuthJson parsedCodexAuth = tryParseCodexAuthJson(accountType, request.metadataJson());
        UpstreamAccountPoolEntity pool = resolvePool(request.poolId(), accountType);
        String accessToken = requireSecret(firstNonBlank(
                request.accessToken(),
                parsedCodexAuth == null ? null : parsedCodexAuth.accessToken()
        ), "accessToken");
        String refreshToken = normalizeBlank(firstNonBlank(
                request.refreshToken(),
                parsedCodexAuth == null ? null : parsedCodexAuth.refreshToken()
        ));
        Instant now = Instant.now();

        String externalAccountId = resolveOfficialExternalAccountId(request.externalAccountId(), parsedCodexAuth, accountType);
        UpstreamAccountEntity entity = resolveExistingOfficialAccount(accountType, parsedCodexAuth, externalAccountId)
                .orElseGet(UpstreamAccountEntity::new);
        boolean created = entity.getId() == null;
        entity.setPool(pool);
        entity.setProviderType(accountType.providerType());
        entity.setAccountName(resolveAccountName(firstNonBlank(
                request.accountName(),
                parsedCodexAuth == null ? null : parsedCodexAuth.accountName()
        ), accountType));
        entity.setExternalAccountId(externalAccountId);
        entity.setAccessTokenCiphertext(credentialCryptoService.encrypt(accessToken));
        entity.setRefreshTokenCiphertext(refreshToken == null ? null : credentialCryptoService.encrypt(refreshToken));
        entity.setActive(request.active() == null || request.active());
        entity.setFrozen(false);
        entity.setHealthy(true);
        entity.setTokenExpiresAt(request.tokenExpiresAt() == null && parsedCodexAuth != null
                ? parsedCodexAuth.tokenExpiresAt()
                : request.tokenExpiresAt());
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
        metadata.put("import_status", created ? "CREATED" : "UPDATED");
        if (parsedCodexAuth != null) {
            metadata.put("codex_auth_json", parsedCodexAuth.safeSummary());
            metadata.put("account_identity", Map.of(
                    "identityKey", parsedCodexAuth.identityKey(),
                    "identitySource", parsedCodexAuth.identitySource(),
                    "identityStrength", parsedCodexAuth.identityStrength(),
                    "accountId", parsedCodexAuth.accountId() == null ? "unknown" : parsedCodexAuth.accountId()
            ));
            metadata.put("import_dedupe_key", externalAccountId);
        }
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
        if (accountType == OfficialAccountType.CODEX && request == null) {
            applyCodexAuthJsonSnapshot(entity, accountType, now);
            return toResponse(upstreamAccountRepository.save(entity));
        }
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

    public OfficialCodexResponsesSmokeResponse codexResponsesSmoke(Long accountId, OfficialCodexResponsesSmokeRequest request) {
        UpstreamAccountEntity entity = getRequired(accountId);
        OfficialAccountType accountType = resolveOfficialAccountType(entity);
        if (accountType != OfficialAccountType.CODEX) {
            throw new IllegalArgumentException("只有 CODEX 官方账号支持 Codex App API responses smoke。");
        }
        Instant now = Instant.now();
        String routeBlockReason = routeBlockReason(entity);
        boolean routeEligible = routeBlockReason == null;
        String model = firstNonBlank(
                request == null ? null : request.model(),
                entity.getSupportedModels() == null || entity.getSupportedModels().isEmpty() ? null : entity.getSupportedModels().get(0),
                accountType.defaultModels().get(0)
        );
        boolean dryRun = request == null || request.dryRun() == null || request.dryRun();
        String requestedBaseUrl = request == null ? null : request.baseUrl();
        String chatGptAccountId = resolveChatGptAccountId(entity);
        String status = routeEligible ? (dryRun ? "DRY_RUN_READY" : "LIVE_SMOKE_PENDING") : "ROUTE_BLOCKED";
        Map<String, Object> requestPreview = codexResponsesSmokePreview(
                model,
                request == null ? null : request.input(),
                requestedBaseUrl,
                chatGptAccountId
        );
        String path = text(requestPreview.get("path"));
        String baseUrl = text(requestPreview.get("baseUrl"));
        boolean codexAppApi = Boolean.TRUE.equals(requestPreview.get("codexAppApi"));
        CodexResponsesSmokeHttpClient.CodexResponsesSmokeResult liveResult = null;
        String message = routeEligible ? "Codex App API responses dry-run smoke 已具备执行前置条件。" : "Codex 账号当前被路由保护阻断。";
        if (routeEligible && !dryRun) {
            try {
                liveResult = codexResponsesSmokeHttpClient.execute(
                        credentialCryptoService.decrypt(entity.getAccessTokenCiphertext()),
                        model,
                        request == null ? null : request.input(),
                        requestedBaseUrl,
                        request == null ? null : request.timeoutSeconds(),
                        chatGptAccountId
                );
                path = liveResult.path();
                baseUrl = liveResult.baseUrl();
                codexAppApi = liveResult.codexAppApi();
                status = liveResult.success() ? "LIVE_SMOKE_OK" : "LIVE_SMOKE_FAILED";
                message = liveResult.success() ? "Codex App API responses 真实 smoke 成功。" : "Codex App API responses 真实 smoke 失败，可按脱敏 failureType 重试或排查。";
            } catch (RuntimeException exception) {
                liveResult = new CodexResponsesSmokeHttpClient.CodexResponsesSmokeResult(
                        false,
                        null,
                        null,
                        null,
                        0L,
                        baseUrl,
                        path,
                        codexAppApi,
                        "CREDENTIAL_DECRYPT_FAILED",
                        truncate(exception.getMessage(), 240),
                        null
                );
                status = "LIVE_SMOKE_FAILED";
                message = "Codex 账号凭证无法解密，真实 smoke 未发起。";
            }
        }
        String classification = smokeClassification(routeEligible, dryRun, routeBlockReason, liveResult);
        String skippedReason = smokeSkippedReason(classification, routeEligible, dryRun, routeBlockReason, liveResult);
        message = smokeMessage(classification, routeEligible, dryRun, message);
        String credentialFingerprint = routeEligible
                ? credentialFingerprint(entity)
                : credentialCiphertextFingerprint(entity);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("classification", classification);
        result.put("adapter", CODEX_AUTH_JSON_ADAPTER);
        result.put("accountType", accountType.name());
        result.put("method", "POST");
        result.put("path", path);
        result.put("baseUrl", baseUrl);
        result.put("codexAppApi", codexAppApi);
        result.put("model", model);
        result.put("dryRun", dryRun);
        result.put("routeEligible", routeEligible);
        if (skippedReason != null) {
            result.put("skippedReason", skippedReason);
        }
        if (routeBlockReason != null) {
            result.put("routeBlockReason", routeBlockReason);
        }
        if (liveResult != null) {
            result.put("httpStatus", liveResult.httpStatus());
            result.put("upstreamRequestId", liveResult.upstreamRequestId());
            result.put("upstreamResponseId", liveResult.upstreamResponseId());
            result.put("durationMs", liveResult.durationMs());
            result.put("failureType", liveResult.failureType());
            result.put("failureMessage", liveResult.failureMessage());
            if (liveResult.usageProbe() != null) {
                result.put("keepalive", liveResult.usageProbe().toSafeMap());
            }
        }
        result.put("checkedAt", now.toString());
        entity.setLastRefreshResultJson(writeJson(result));
        upstreamAccountRepository.save(entity);
        return new OfficialCodexResponsesSmokeResponse(
                entity.getId(),
                status,
                classification,
                skippedReason,
                "POST",
                path,
                baseUrl,
                codexAppApi,
                model,
                dryRun,
                routeEligible,
                routeBlockReason,
                credentialFingerprint,
                liveResult == null ? null : liveResult.httpStatus(),
                liveResult == null ? null : liveResult.upstreamRequestId(),
                liveResult == null ? null : liveResult.upstreamResponseId(),
                liveResult == null ? null : liveResult.durationMs(),
                liveResult == null ? null : liveResult.failureType(),
                liveResult == null ? null : liveResult.failureMessage(),
                liveResult == null || liveResult.usageProbe() == null ? null : liveResult.usageProbe().toSafeMap(),
                now,
                message,
                requestPreview
        );
    }

    private String smokeClassification(
            boolean routeEligible,
            boolean dryRun,
            String routeBlockReason,
            CodexResponsesSmokeHttpClient.CodexResponsesSmokeResult liveResult) {
        if (!routeEligible) {
            return isBudgetBlockReason(routeBlockReason) ? "BUDGET_BLOCKED" : "SKIPPED";
        }
        if (dryRun) {
            return "SKIPPED";
        }
        if (liveResult == null) {
            return "SKIPPED";
        }
        if (liveResult.success()) {
            return "PASS";
        }
        if (isUnsupportedFailure(liveResult)) {
            return "UNSUPPORTED";
        }
        if (isNoPermissionFailure(liveResult)) {
            return "NO_PERMISSION";
        }
        if (isBudgetFailure(liveResult)) {
            return "BUDGET_BLOCKED";
        }
        return "FAIL";
    }

    private String smokeSkippedReason(
            String classification,
            boolean routeEligible,
            boolean dryRun,
            String routeBlockReason,
            CodexResponsesSmokeHttpClient.CodexResponsesSmokeResult liveResult) {
        if ("PASS".equals(classification) || "FAIL".equals(classification)) {
            return null;
        }
        if (!routeEligible) {
            return routeBlockReason;
        }
        if (dryRun) {
            return "DRY_RUN";
        }
        if (liveResult != null && !isBlank(liveResult.failureType())) {
            return liveResult.failureType();
        }
        return classification;
    }

    private String smokeMessage(String classification, boolean routeEligible, boolean dryRun, String fallback) {
        return switch (classification) {
            case "PASS" -> "Codex App API responses 真实 smoke 成功。";
            case "BUDGET_BLOCKED" -> routeEligible && !dryRun
                    ? "Codex App API responses 真实 smoke 已被额度或速率预算保护阻断。"
                    : "Codex 账号当前被配额预算保护阻断。";
            case "NO_PERMISSION" -> "Codex App API responses 真实 smoke 已因认证或权限不足跳过。";
            case "UNSUPPORTED" -> "Codex App API responses 真实 smoke 已确认模型或参数不支持。";
            case "SKIPPED" -> dryRun
                    ? "Codex App API responses dry-run smoke 已完成安全预检，未消耗额度。"
                    : fallback;
            default -> fallback;
        };
    }

    private boolean isUnsupportedFailure(CodexResponsesSmokeHttpClient.CodexResponsesSmokeResult liveResult) {
        String failureType = upper(liveResult.failureType());
        return failureType.contains("UNSUPPORTED")
                || failureType.contains("NOT_SUPPORTED")
                || failureType.contains("MODEL_NOT_SUPPORTED");
    }

    private boolean isNoPermissionFailure(CodexResponsesSmokeHttpClient.CodexResponsesSmokeResult liveResult) {
        if (liveResult.httpStatus() != null && (liveResult.httpStatus() == 401 || liveResult.httpStatus() == 403)) {
            return true;
        }
        String failureType = upper(liveResult.failureType());
        return failureType.contains("AUTH")
                || failureType.contains("PERMISSION")
                || failureType.contains("UNAUTHORIZED")
                || failureType.contains("FORBIDDEN")
                || failureType.contains("NO_PERMISSION");
    }

    private boolean isBudgetFailure(CodexResponsesSmokeHttpClient.CodexResponsesSmokeResult liveResult) {
        if (liveResult.httpStatus() != null && liveResult.httpStatus() == 429) {
            return true;
        }
        String failureType = upper(liveResult.failureType());
        if (failureType.contains("BUDGET")
                || failureType.contains("QUOTA")
                || failureType.contains("RATE_LIMIT")
                || failureType.contains("RATE")
                || failureType.contains("LIMIT")) {
            return true;
        }
        CodexResponsesSmokeHttpClient.CodexUsageProbeResult usageProbe = liveResult.usageProbe();
        if (usageProbe == null) {
            return false;
        }
        return Boolean.TRUE.equals(usageProbe.limitReached())
                || Boolean.FALSE.equals(usageProbe.allowed())
                || (usageProbe.httpStatus() != null && usageProbe.httpStatus() == 429)
                || isBudgetBlockReason(usageProbe.failureType());
    }

    private boolean isBudgetBlockReason(String value) {
        String upper = upper(value);
        return upper.contains("QUOTA")
                || upper.contains("BUDGET")
                || upper.contains("RATE_LIMIT")
                || upper.contains("RATE")
                || upper.contains("LIMIT");
    }

    private String upper(String value) {
        return value == null ? "" : value.toUpperCase(java.util.Locale.ROOT);
    }

    private UpstreamAccountEntity getRequired(Long accountId) {
        return upstreamAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定官方账号。"));
    }

    private String resolveOfficialExternalAccountId(
            String requestedExternalAccountId,
            CodexAuthJsonParser.ParsedCodexAuthJson parsedCodexAuth,
            OfficialAccountType accountType) {
        if (accountType == OfficialAccountType.CODEX && parsedCodexAuth != null
                && !"WEAK_TOKEN".equals(parsedCodexAuth.identityStrength())) {
            return parsedCodexAuth.identityKey();
        }
        return resolveExternalAccountId(firstNonBlank(
                requestedExternalAccountId,
                parsedCodexAuth == null ? null : parsedCodexAuth.identityKey()
        ), accountType);
    }

    private Optional<UpstreamAccountEntity> resolveExistingOfficialAccount(
            OfficialAccountType accountType,
            CodexAuthJsonParser.ParsedCodexAuthJson parsedCodexAuth,
            String externalAccountId) {
        if (accountType != OfficialAccountType.CODEX || parsedCodexAuth == null) {
            return Optional.empty();
        }
        Optional<UpstreamAccountEntity> byExternalId = upstreamAccountRepository
                .findFirstByProviderTypeAndExternalAccountIdOrderByUpdatedAtDesc(accountType.providerType(), externalAccountId);
        if (byExternalId != null && byExternalId.isPresent()) {
            return byExternalId;
        }
        if (parsedCodexAuth.accountId() != null && !parsedCodexAuth.accountId().isBlank()) {
            Optional<UpstreamAccountEntity> byLegacyAccountId = upstreamAccountRepository
                    .findFirstByProviderTypeAndExternalAccountIdOrderByUpdatedAtDesc(accountType.providerType(), parsedCodexAuth.accountId());
            if (byLegacyAccountId != null && byLegacyAccountId.isPresent()) {
                return byLegacyAccountId;
            }
        }
        if ("WEAK_TOKEN".equals(parsedCodexAuth.identityStrength())) {
            return Optional.empty();
        }
        List<UpstreamAccountEntity> accounts = upstreamAccountRepository.findAllByProviderTypeOrderByUpdatedAtDesc(accountType.providerType());
        if (accounts == null) {
            return Optional.empty();
        }
        return accounts.stream()
                .filter(account -> parsedCodexAuth.identityKey().equals(metadataIdentityKey(account.getMetadataJson())))
                .findFirst();
    }

    private String metadataIdentityKey(String metadataJson) {
        Map<String, Object> metadata = sensitiveJsonSanitizer.readMap(metadataJson);
        Object accountIdentity = metadata.get("account_identity");
        if (accountIdentity instanceof Map<?, ?> accountIdentityMap) {
            String value = text(accountIdentityMap.get("identityKey"));
            if (!isBlank(value)) {
                return value;
            }
        }
        Object codexAuthJson = metadata.get("codex_auth_json");
        if (codexAuthJson instanceof Map<?, ?> codexAuthMap) {
            String value = text(codexAuthMap.get("identityKey"));
            if (!isBlank(value)) {
                return value;
            }
            return text(codexAuthMap.get("identity_key"));
        }
        return text(metadata.get("identityKey"));
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

    private void applyCodexAuthJsonSnapshot(UpstreamAccountEntity entity, OfficialAccountType accountType, Instant now) {
        QuotaInput input = new QuotaInput(
                text(readMetadataMap(entity.getMetadataJson()).get("plan_tier")),
                text(readMetadataMap(entity.getMetadataJson()).get("subscription_tier")),
                null,
                null,
                null,
                entity.getTokenExpiresAt(),
                null,
                false
        );
        applyQuotaSuccess(entity, accountType, input, now, "codex_auth_json_snapshot");
        Map<String, Object> metadata = readMetadataMap(entity.getMetadataJson());
        metadata.put("codex_adapter_status", "LOCAL_INSPECTION_READY");
        metadata.put("codex_responses_smoke_status", "DRY_RUN_READY");
        entity.setMetadataJson(writeJson(sanitizeMetadata(metadata)));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "refreshed");
        result.put("adapter", CODEX_AUTH_JSON_ADAPTER);
        result.put("accountType", accountType.name());
        result.put("planTier", defaultString(text(metadata.get("plan_tier")), accountType.defaultPlanTier()));
        result.put("subscriptionTier", defaultString(text(metadata.get("subscription_tier")), accountType.defaultPlanTier()));
        result.put("credentialPresence", Map.of(
                "accessToken", entity.getAccessTokenCiphertext() != null,
                "refreshToken", entity.getRefreshTokenCiphertext() != null
        ));
        result.put("responsesSmoke", codexResponsesSmokePreview(
                entity.getSupportedModels() == null || entity.getSupportedModels().isEmpty()
                        ? accountType.defaultModels().get(0)
                        : entity.getSupportedModels().get(0),
                null,
                null,
                resolveChatGptAccountId(entity)
        ));
        result.put("refreshedAt", now.toString());
        entity.setLastRefreshResultJson(writeJson(result));
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

    private CodexAuthJsonParser.ParsedCodexAuthJson tryParseCodexAuthJson(OfficialAccountType accountType, String metadataJson) {
        if (accountType != OfficialAccountType.CODEX || isBlank(metadataJson)) {
            return null;
        }
        try {
            return codexAuthJsonParser.parse(metadataJson);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Map<String, Object> codexResponsesSmokePreview(
            String model,
            String input,
            String requestedBaseUrl,
            String chatGptAccountId) {
        return codexResponsesSmokeHttpClient.requestPreview(model, input, requestedBaseUrl, chatGptAccountId);
    }

    private String resolveChatGptAccountId(UpstreamAccountEntity entity) {
        Map<String, Object> metadata = readMetadataMap(entity.getMetadataJson());
        String fromIdentity = rawAccountId(metadata.get("account_identity"));
        if (!isBlank(fromIdentity)) {
            return fromIdentity;
        }
        String fromSummary = rawAccountId(metadata.get("codex_auth_json"));
        if (!isBlank(fromSummary)) {
            return fromSummary;
        }
        String external = entity.getExternalAccountId();
        if (!isBlank(external) && !external.startsWith("codex:")) {
            return external;
        }
        return null;
    }

    private String rawAccountId(Object value) {
        if (value instanceof Map<?, ?> map) {
            String accountId = text(map.get("accountId"));
            if (!isBlank(accountId) && !"unknown".equalsIgnoreCase(accountId)) {
                return accountId;
            }
            String snake = text(map.get("account_id"));
            if (!isBlank(snake) && !"unknown".equalsIgnoreCase(snake)) {
                return snake;
            }
        }
        return null;
    }

    private String credentialFingerprint(UpstreamAccountEntity entity) {
        if (isBlank(entity.getAccessTokenCiphertext())) {
            return null;
        }
        try {
            return sha256Fingerprint(credentialCryptoService.decrypt(entity.getAccessTokenCiphertext()));
        } catch (RuntimeException exception) {
            return sha256Fingerprint(entity.getAccessTokenCiphertext());
        }
    }

    private String credentialCiphertextFingerprint(UpstreamAccountEntity entity) {
        return isBlank(entity.getAccessTokenCiphertext()) ? null : sha256Fingerprint(entity.getAccessTokenCiphertext());
    }

    private String sha256Fingerprint(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hex = HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
            return hex.substring(0, 16).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境缺少 SHA-256。", exception);
        }
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
