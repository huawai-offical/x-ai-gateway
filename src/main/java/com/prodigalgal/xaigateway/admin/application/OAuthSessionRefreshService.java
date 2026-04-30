package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class OAuthSessionRefreshService {

    private static final Duration EXPIRY_REFRESH_WINDOW = Duration.ofMinutes(10);
    private static final int DEFAULT_BATCH_SIZE = 50;

    private final UpstreamAccountRepository upstreamAccountRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final ObjectMapper objectMapper;
    private final Map<UpstreamAccountProviderType, OAuthSessionRefreshAdapter> adapters;
    private final Optional<OpsAuditService> opsAuditService;
    private final Optional<OpsTimelineService> opsTimelineService;

    public OAuthSessionRefreshService(
            UpstreamAccountRepository upstreamAccountRepository,
            CredentialCryptoService credentialCryptoService,
            ObjectMapper objectMapper,
            List<OAuthSessionRefreshAdapter> adapters,
            Optional<OpsAuditService> opsAuditService,
            Optional<OpsTimelineService> opsTimelineService) {
        this.upstreamAccountRepository = upstreamAccountRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.objectMapper = objectMapper;
        this.adapters = adapters.stream()
                .collect(Collectors.toMap(OAuthSessionRefreshAdapter::providerType, Function.identity(), (left, right) -> left));
        this.opsAuditService = opsAuditService;
        this.opsTimelineService = opsTimelineService;
    }

    @Scheduled(fixedDelayString = "${gateway.oauth-session-refresh.fixed-delay:PT1M}")
    public void scheduledRefresh() {
        refreshDueAccounts(DEFAULT_BATCH_SIZE);
    }

    public List<OAuthSessionRefreshOutcome> refreshDueAccounts(int limit) {
        Instant now = Instant.now();
        return upstreamAccountRepository.findAll().stream()
                .filter(this::isRefreshManagedAccount)
                .sorted(Comparator.comparing(this::sortTime, Comparator.nullsFirst(Comparator.naturalOrder())))
                .limit(Math.max(1, limit))
                .map(entity -> refresh(entity, now, false))
                .toList();
    }

    public OAuthSessionRefreshOutcome refreshAccount(Long accountId) {
        UpstreamAccountEntity entity = upstreamAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定账号。"));
        return refresh(entity, Instant.now(), true);
    }

    private OAuthSessionRefreshOutcome refresh(UpstreamAccountEntity entity, Instant now, boolean force) {
        Optional<OAuthSessionRefreshOutcome> skipped = skipReason(entity, now, force);
        if (skipped.isPresent()) {
            return skipped.get();
        }

        OAuthSessionRefreshAdapter adapter = adapters.get(entity.getProviderType());
        if (adapter == null) {
            return fail(entity, now, "未配置 provider 专属刷新适配器：" + entity.getProviderType(), true);
        }

        try {
            OAuthSessionRefreshRequest request = new OAuthSessionRefreshRequest(
                    entity.getId(),
                    entity.getAccountName(),
                    entity.getProviderType(),
                    decryptNullable(entity.getAccessTokenCiphertext()),
                    decryptNullable(entity.getRefreshTokenCiphertext()),
                    entity.getMetadataJson(),
                    now
            );
            OAuthSessionRefreshResult result = adapter.refresh(request);
            return succeed(entity, now, result);
        } catch (Exception exception) {
            return fail(entity, now, exception.getMessage(), true);
        }
    }

    private Optional<OAuthSessionRefreshOutcome> skipReason(UpstreamAccountEntity entity, Instant now, boolean force) {
        if (!isRefreshManagedAccount(entity)) {
            return Optional.of(outcome(entity, "SKIPPED", "NOT_MANAGED", now));
        }
        if (entity.getRefreshTokenCiphertext() == null || entity.getRefreshTokenCiphertext().isBlank()) {
            entity.setRefreshStatus("ACCESS_ONLY");
            entity.setLastRefreshResultJson(writeJson(Map.of(
                    "status", "skipped",
                    "reason", "no_refresh_token",
                    "checkedAt", now.toString()
            )));
            upstreamAccountRepository.save(entity);
            return Optional.of(outcome(entity, "SKIPPED", "NO_REFRESH_TOKEN", now));
        }
        if (!force && entity.getCooldownUntil() != null && entity.getCooldownUntil().isAfter(now)) {
            return Optional.of(outcome(entity, "SKIPPED", "COOLDOWN", now));
        }
        if (!force && entity.getNextRefreshAfter() != null && entity.getNextRefreshAfter().isAfter(now)) {
            return Optional.of(outcome(entity, "SKIPPED", "NOT_DUE", now));
        }
        if (!force
                && entity.getNextRefreshAfter() == null
                && entity.getTokenExpiresAt() != null
                && entity.getTokenExpiresAt().isAfter(now.plus(EXPIRY_REFRESH_WINDOW))) {
            return Optional.of(outcome(entity, "SKIPPED", "TOKEN_STILL_VALID", now));
        }
        return Optional.empty();
    }

    private OAuthSessionRefreshOutcome succeed(UpstreamAccountEntity entity, Instant now, OAuthSessionRefreshResult result) {
        if (result.accessToken() != null && !result.accessToken().isBlank()) {
            entity.setAccessTokenCiphertext(credentialCryptoService.encrypt(result.accessToken()));
        }
        if (result.refreshToken() != null && !result.refreshToken().isBlank()) {
            entity.setRefreshTokenCiphertext(credentialCryptoService.encrypt(result.refreshToken()));
        }

        Instant tokenExpiresAt = result.tokenExpiresAt() == null ? now.plus(Duration.ofHours(1)) : result.tokenExpiresAt();
        Instant nextRefreshAfter = result.nextRefreshAfter() == null ? tokenExpiresAt.minus(EXPIRY_REFRESH_WINDOW.dividedBy(2)) : result.nextRefreshAfter();
        entity.setTokenExpiresAt(tokenExpiresAt);
        entity.setNextRefreshAfter(nextRefreshAfter);
        entity.setLastRefreshAt(now);
        entity.setRefreshStatus("REFRESHED");
        entity.setRefreshFailureCount(0);
        entity.setCooldownUntil(null);
        entity.setHealthy(true);
        entity.setLastErrorMessage(null);
        entity.setQuotaWindowStartedAt(result.quotaWindowStartedAt());
        entity.setQuotaWindowSeconds(result.quotaWindowSeconds());
        entity.setQuotaRemainingTokens(result.quotaRemainingTokens());
        entity.setQuotaRemainingRequests(result.quotaRemainingRequests());
        entity.setHeaderSnapshotJson(writeJson(sanitizeHeaders(result.headerSnapshot())));
        entity.setLastRefreshResultJson(writeJson(successResult(now, result, tokenExpiresAt, nextRefreshAfter)));

        UpstreamAccountEntity saved = upstreamAccountRepository.save(entity);
        recordAudit(saved, "REFRESH_SUCCEEDED", "INFO", saved.getLastRefreshResultJson(), now);
        return new OAuthSessionRefreshOutcome(
                saved.getId(),
                saved.getProviderType().name(),
                "REFRESHED",
                result.adapterName(),
                saved.getLastRefreshAt(),
                saved.getNextRefreshAfter(),
                saved.getCooldownUntil()
        );
    }

    private OAuthSessionRefreshOutcome fail(UpstreamAccountEntity entity, Instant now, String message, boolean persist) {
        int failureCount = entity.getRefreshFailureCount() + 1;
        Instant cooldownUntil = now.plus(cooldownDuration(failureCount));
        entity.setLastRefreshAt(now);
        entity.setRefreshStatus("FAILED");
        entity.setRefreshFailureCount(failureCount);
        entity.setCooldownUntil(cooldownUntil);
        entity.setHealthy(false);
        entity.setLastErrorMessage(truncate(defaultString(message, "OAuth / Session 刷新失败。"), 512));
        entity.setLastRefreshResultJson(writeJson(Map.of(
                "status", "failed",
                "providerType", entity.getProviderType().name(),
                "failureCount", String.valueOf(failureCount),
                "errorMessage", entity.getLastErrorMessage(),
                "cooldownUntil", cooldownUntil.toString(),
                "refreshedAt", now.toString()
        )));

        UpstreamAccountEntity saved = persist ? upstreamAccountRepository.save(entity) : entity;
        recordAudit(saved, "REFRESH_FAILED", "WARNING", saved.getLastRefreshResultJson(), now);
        return new OAuthSessionRefreshOutcome(
                saved.getId(),
                saved.getProviderType().name(),
                "FAILED",
                saved.getLastErrorMessage(),
                saved.getLastRefreshAt(),
                saved.getNextRefreshAfter(),
                saved.getCooldownUntil()
        );
    }

    private Duration cooldownDuration(int failureCount) {
        long minutes = Math.min(60, Math.max(5, failureCount * 5L));
        return Duration.ofMinutes(minutes);
    }

    private boolean isRefreshManagedAccount(UpstreamAccountEntity entity) {
        return entity.isActive() && !entity.isFrozen() && entity.getProviderType() != null;
    }

    private Instant sortTime(UpstreamAccountEntity entity) {
        if (entity.getNextRefreshAfter() != null) {
            return entity.getNextRefreshAfter();
        }
        return entity.getTokenExpiresAt();
    }

    private OAuthSessionRefreshOutcome outcome(UpstreamAccountEntity entity, String status, String reason, Instant now) {
        return new OAuthSessionRefreshOutcome(
                entity.getId(),
                entity.getProviderType() == null ? null : entity.getProviderType().name(),
                status,
                reason,
                now,
                entity.getNextRefreshAfter(),
                entity.getCooldownUntil()
        );
    }

    private Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        headers.forEach((key, value) -> sanitized.put(key, isSensitiveHeader(key) ? "***" : value));
        return sanitized;
    }

    private boolean isSensitiveHeader(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return normalized.contains("authorization")
                || normalized.contains("api-key")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("cookie");
    }

    private Map<String, String> successResult(
            Instant now,
            OAuthSessionRefreshResult result,
            Instant tokenExpiresAt,
            Instant nextRefreshAfter) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("status", "refreshed");
        payload.put("adapter", result.adapterName());
        payload.put("tokenExpiresAt", tokenExpiresAt.toString());
        payload.put("nextRefreshAfter", nextRefreshAfter.toString());
        payload.put("refreshedAt", now.toString());
        if (result.refreshMetadata() != null) {
            result.refreshMetadata().forEach((key, value) -> payload.put("metadata." + key, value));
        }
        return payload;
    }

    private void recordAudit(UpstreamAccountEntity entity, String action, String severity, String detailJson, Instant now) {
        String resourceRef = entity.getId() == null ? entity.getExternalAccountId() : String.valueOf(entity.getId());
        opsAuditService.ifPresent(service -> service.record("OAUTH_SESSION", action, "upstream_account", resourceRef, detailJson));
        opsTimelineService.ifPresent(service -> service.recordEvent(
                "OAUTH_SESSION_REFRESH",
                severity,
                "oauth-session-refresh",
                "upstream_account",
                resourceRef,
                "OAuth / Session 账号刷新：" + entity.getAccountName(),
                detailJson,
                now
        ));
    }

    private String decryptNullable(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        return credentialCryptoService.decrypt(ciphertext);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "{}";
        }
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
}
