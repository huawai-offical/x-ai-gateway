package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ExportedClientConfigResponse;
import com.prodigalgal.xaigateway.admin.api.AccountImportAuthJsonRequest;
import com.prodigalgal.xaigateway.admin.api.ProgrammingAccountIdentityResponse;
import com.prodigalgal.xaigateway.admin.api.UpstreamAccountResponse;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountPoolEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountPoolRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class AccountAdminService {

    private final UpstreamAccountRepository upstreamAccountRepository;
    private final UpstreamAccountPoolRepository upstreamAccountPoolRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final SupportedModelCatalogService supportedModelCatalogService;
    private final OAuthSessionRefreshService oauthSessionRefreshService;
    private final ObjectMapper objectMapper;
    private final CodexAuthJsonParser codexAuthJsonParser;
    private final SensitiveJsonSanitizer sensitiveJsonSanitizer;

    public AccountAdminService(
            UpstreamAccountRepository upstreamAccountRepository,
            UpstreamAccountPoolRepository upstreamAccountPoolRepository,
            CredentialCryptoService credentialCryptoService,
            SupportedModelCatalogService supportedModelCatalogService,
            OAuthSessionRefreshService oauthSessionRefreshService,
            ObjectMapper objectMapper) {
        this.upstreamAccountRepository = upstreamAccountRepository;
        this.upstreamAccountPoolRepository = upstreamAccountPoolRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.supportedModelCatalogService = supportedModelCatalogService;
        this.oauthSessionRefreshService = oauthSessionRefreshService;
        this.objectMapper = objectMapper;
        this.codexAuthJsonParser = new CodexAuthJsonParser(objectMapper);
        this.sensitiveJsonSanitizer = new SensitiveJsonSanitizer(objectMapper);
    }

    @Transactional(readOnly = true)
    public List<UpstreamAccountResponse> list(Long poolId) {
        if (poolId == null) {
            return upstreamAccountRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
        }
        return listByPool(poolId);
    }

    @Transactional(readOnly = true)
    public List<UpstreamAccountResponse> listByPool(Long poolId) {
        return upstreamAccountRepository.findAllByPool_IdOrderByCreatedAtDesc(poolId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UpstreamAccountResponse get(Long id) {
        return toResponse(getRequired(id));
    }

    public UpstreamAccountResponse toggleFrozen(Long id, boolean frozen) {
        UpstreamAccountEntity entity = getRequired(id);
        entity.setFrozen(frozen);
        return toResponse(upstreamAccountRepository.save(entity));
    }

    public UpstreamAccountResponse refresh(Long id) {
        oauthSessionRefreshService.refreshAccount(id);
        return toResponse(getRequired(id));
    }

    public UpstreamAccountResponse resetRuntime(Long id) {
        UpstreamAccountEntity entity = getRequired(id);
        entity.setFrozen(false);
        entity.setHealthy(true);
        entity.setLastErrorMessage(null);
        entity.setRefreshFailureCount(0);
        entity.setCooldownUntil(null);
        entity.setNextRefreshAfter(null);
        if (entity.getRefreshStatus() == null || entity.getRefreshStatus().isBlank()
                || "FAILED".equalsIgnoreCase(entity.getRefreshStatus())
                || "QUOTA_FAILED".equalsIgnoreCase(entity.getRefreshStatus())) {
            entity.setRefreshStatus("READY");
        }
        return toResponse(upstreamAccountRepository.save(entity));
    }

    public UpstreamAccountResponse updateNetwork(Long id, Long proxyId, Long tlsFingerprintProfileId) {
        UpstreamAccountEntity entity = getRequired(id);
        entity.setProxyId(proxyId);
        entity.setTlsFingerprintProfileId(tlsFingerprintProfileId);
        return toResponse(upstreamAccountRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public ExportedClientConfigResponse exportConfig(Long id, String clientFamily) {
        UpstreamAccountEntity entity = getRequired(id);
        String token = entity.getAccessTokenCiphertext() == null ? "" : credentialCryptoService.decrypt(entity.getAccessTokenCiphertext());
        String config = switch (entity.getProviderType()) {
            case OPENAI_OAUTH, CODEX_OAUTH -> "{\n  \"OPENAI_API_KEY\": \"" + token + "\"\n}";
            case GEMINI_OAUTH, ANTIGRAVITY_OAUTH -> "{\n  \"GEMINI_API_KEY\": \"" + token + "\"\n}";
            case CLAUDE_ACCOUNT, CLAUDE_PLAN -> "{\n  \"ANTHROPIC_API_KEY\": \"" + token + "\"\n}";
            case COPILOT_OAUTH -> "{\n  \"GITHUB_COPILOT_TOKEN\": \"" + token + "\"\n}";
        };
        return new ExportedClientConfigResponse(entity.getAccountName(), clientFamily, config);
    }

    @Transactional(readOnly = true)
    public ProgrammingAccountIdentityResponse programmingIdentity(Long id, String clientFamily) {
        UpstreamAccountEntity entity = getRequired(id);
        JsonNode metadata = readMetadata(defaultString(entity.getMetadataJson(), "{}"));
        String requestedClientFamily = defaultString(clientFamily, firstNonBlank(
                parseText(metadata.findValue("client_family")),
                parseText(metadata.findValue("clientFamily")),
                "CODEX"
        )).toUpperCase(Locale.ROOT);
        String identitySubject = firstNonBlank(
                parseText(metadata.findValue("identity_subject")),
                parseText(metadata.findValue("identitySubject")),
                parseText(metadata.findValue("subject")),
                entity.getExternalAccountId()
        );
        String identityEmail = firstNonBlank(
                parseText(metadata.findValue("identity_email")),
                parseText(metadata.findValue("identityEmail")),
                parseText(metadata.findValue("email"))
        );
        String adoptionDecision = firstNonBlank(
                parseText(metadata.findValue("adoption_decision")),
                parseText(metadata.findValue("adoptionDecision")),
                parseText(metadata.findValue("identity_adoption")),
                "ADOPTED"
        ).toUpperCase(Locale.ROOT);
        String authorizationStatus = firstNonBlank(entity.getRefreshStatus(), "UNKNOWN").toUpperCase(Locale.ROOT);
        String routeBlockReason = routeBlockReason(entity, requestedClientFamily, adoptionDecision, authorizationStatus);

        return new ProgrammingAccountIdentityResponse(
                entity.getId(),
                entity.getProviderType(),
                entity.getAccountName(),
                entity.getExternalAccountId(),
                identitySubject,
                identityEmail,
                requestedClientFamily,
                adoptionDecision,
                authorizationStatus,
                entity.getQuotaRemainingTokens(),
                entity.getQuotaRemainingRequests(),
                entity.getQuotaWindowSeconds(),
                entity.getQuotaWindowStartedAt(),
                routeBlockReason == null,
                routeBlockReason,
                entity.getLastRefreshResultJson()
        );
    }

    public UpstreamAccountResponse importAuthJson(AccountImportAuthJsonRequest request) {
        UpstreamAccountPoolEntity pool = resolvePool(request.poolId());
        UpstreamAccountProviderType providerType = pool != null
                ? pool.getProviderType()
                : UpstreamAccountProviderType.OPENAI_OAUTH;
        String metadataJson = request.metadataJson() == null || request.metadataJson().isBlank() ? "{}" : request.metadataJson().trim();
        JsonNode metadata = readMetadata(metadataJson);
        CodexAuthJsonParser.ParsedCodexAuthJson parsedCodexAuth = tryParseCodexAuthJson(providerType, metadataJson);

        String accessToken = firstNonBlank(
                request.accessToken(),
                parsedCodexAuth == null ? null : parsedCodexAuth.accessToken()
        );
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken 不能为空。");
        }
        String refreshToken = firstNonBlank(
                request.refreshToken(),
                parsedCodexAuth == null ? null : parsedCodexAuth.refreshToken()
        );
        String externalAccountId = resolveImportExternalAccountId(request.externalAccountId(), parsedCodexAuth, providerType.name());

        UpstreamAccountEntity entity = resolveExistingImportedAccount(providerType, parsedCodexAuth, externalAccountId)
                .orElseGet(UpstreamAccountEntity::new);
        entity.setPool(pool);
        entity.setProviderType(providerType);
        entity.setAccountName(resolveAccountName(
                firstNonBlank(request.accountName(), parsedCodexAuth == null ? null : parsedCodexAuth.accountName()),
                pool == null ? null : pool.getPoolName()
        ));
        entity.setExternalAccountId(externalAccountId);
        entity.setAccessTokenCiphertext(credentialCryptoService.encrypt(accessToken.trim()));
        entity.setRefreshTokenCiphertext(refreshToken == null || refreshToken.isBlank()
                ? null
                : credentialCryptoService.encrypt(refreshToken.trim()));
        entity.setActive(request.active() == null || request.active());
        entity.setFrozen(false);
        entity.setHealthy(true);
        entity.setLastRefreshAt(Instant.now());
        entity.setMetadataJson(buildSanitizedImportMetadata(metadataJson, parsedCodexAuth, providerType));
        entity.setSupportedModels(supportedModelCatalogService.resolveForAccountImport(pool, request.supportedModels()));
        entity.setProxyId(request.proxyId());
        entity.setTlsFingerprintProfileId(request.tlsFingerprintProfileId());
        entity.setSiteProfileId(request.siteProfileId());
        entity.setTokenExpiresAt(resolveInstant(
                request.tokenExpiresAt(),
                metadata,
                List.of("expires_at", "expiresAt", "expiry", "token_expiry", "tokenExpiry"),
                List.of("expires_in", "expiresIn")
        ));
        entity.setRefreshStatus(resolveRefreshStatus(request.refreshStatus(), metadata, entity.getRefreshTokenCiphertext() != null));
        entity.setNextRefreshAfter(resolveInstant(request.nextRefreshAfter(), metadata, List.of("next_refresh_after", "nextRefreshAfter"), List.of()));
        entity.setCooldownUntil(resolveInstant(request.cooldownUntil(), metadata, List.of("cooldown_until", "cooldownUntil"), List.of()));
        entity.setQuotaWindowStartedAt(resolveInstant(request.quotaWindowStartedAt(), metadata, List.of("quota_window_started_at", "quotaWindowStartedAt", "quota_reset_at", "quotaResetAt"), List.of()));
        entity.setQuotaWindowSeconds(resolveInteger(request.quotaWindowSeconds(), metadata, List.of("quota_window_seconds", "quotaWindowSeconds", "window_seconds", "windowSeconds")));
        entity.setQuotaRemainingTokens(resolveLong(request.quotaRemainingTokens(), metadata, List.of("quota_remaining_tokens", "quotaRemainingTokens", "remaining_tokens", "remainingTokens")));
        entity.setQuotaRemainingRequests(resolveLong(request.quotaRemainingRequests(), metadata, List.of("quota_remaining_requests", "quotaRemainingRequests", "remaining_requests", "remainingRequests")));
        entity.setHeaderSnapshotJson(sensitiveJsonSanitizer.sanitizeJson(resolveJsonSnapshot(request.headerSnapshotJson(), metadata, List.of("header_snapshot", "headerSnapshot", "headers", "request_headers", "requestHeaders"))));
        entity.setLastRefreshResultJson(sensitiveJsonSanitizer.sanitizeJson(resolveJsonSnapshot(request.lastRefreshResultJson(), metadata, List.of("last_refresh_result", "lastRefreshResult", "refresh_result", "refreshResult"))));

        return toResponse(upstreamAccountRepository.save(entity));
    }

    private CodexAuthJsonParser.ParsedCodexAuthJson tryParseCodexAuthJson(
            UpstreamAccountProviderType providerType,
            String metadataJson) {
        if (providerType != UpstreamAccountProviderType.CODEX_OAUTH || metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            return codexAuthJsonParser.parse(metadataJson);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String resolveImportExternalAccountId(
            String requestedExternalAccountId,
            CodexAuthJsonParser.ParsedCodexAuthJson parsedCodexAuth,
            String providerName) {
        if (parsedCodexAuth != null && !"WEAK_TOKEN".equals(parsedCodexAuth.identityStrength())) {
            return parsedCodexAuth.identityKey();
        }
        return resolveExternalAccountId(firstNonBlank(
                requestedExternalAccountId,
                parsedCodexAuth == null ? null : parsedCodexAuth.identityKey()
        ), providerName);
    }

    private Optional<UpstreamAccountEntity> resolveExistingImportedAccount(
            UpstreamAccountProviderType providerType,
            CodexAuthJsonParser.ParsedCodexAuthJson parsedCodexAuth,
            String externalAccountId) {
        if (parsedCodexAuth == null) {
            return Optional.empty();
        }
        Optional<UpstreamAccountEntity> byExternalId = upstreamAccountRepository
                .findFirstByProviderTypeAndExternalAccountIdOrderByUpdatedAtDesc(providerType, externalAccountId);
        if (byExternalId != null && byExternalId.isPresent()) {
            return byExternalId;
        }
        if (parsedCodexAuth.accountId() != null && !parsedCodexAuth.accountId().isBlank()) {
            Optional<UpstreamAccountEntity> byLegacyAccountId = upstreamAccountRepository
                    .findFirstByProviderTypeAndExternalAccountIdOrderByUpdatedAtDesc(providerType, parsedCodexAuth.accountId());
            if (byLegacyAccountId != null && byLegacyAccountId.isPresent()) {
                return byLegacyAccountId;
            }
        }
        if ("WEAK_TOKEN".equals(parsedCodexAuth.identityStrength())) {
            return Optional.empty();
        }
        List<UpstreamAccountEntity> accounts = upstreamAccountRepository.findAllByProviderTypeOrderByUpdatedAtDesc(providerType);
        if (accounts == null) {
            return Optional.empty();
        }
        return accounts.stream()
                .filter(account -> parsedCodexAuth.identityKey().equals(metadataIdentityKey(account.getMetadataJson())))
                .findFirst();
    }

    private String buildSanitizedImportMetadata(
            String metadataJson,
            CodexAuthJsonParser.ParsedCodexAuthJson parsedCodexAuth,
            UpstreamAccountProviderType providerType) {
        Map<String, Object> metadata = sensitiveJsonSanitizer.readMap(metadataJson);
        if (parsedCodexAuth != null) {
            metadata.put("official_account_type", "CODEX");
            metadata.put("client_family", "CODEX");
            metadata.put("codex_auth_json", parsedCodexAuth.safeSummary());
            metadata.put("account_identity", Map.of(
                    "identityKey", parsedCodexAuth.identityKey(),
                    "identitySource", parsedCodexAuth.identitySource(),
                    "identityStrength", parsedCodexAuth.identityStrength(),
                    "accountId", parsedCodexAuth.accountId() == null ? "unknown" : parsedCodexAuth.accountId()
            ));
        } else {
            metadata.putIfAbsent("provider_type", providerType.name());
        }
        return sensitiveJsonSanitizer.writeJson(metadata);
    }

    private String metadataIdentityKey(String metadataJson) {
        Map<String, Object> metadata = sensitiveJsonSanitizer.readMap(metadataJson);
        Object accountIdentity = metadata.get("account_identity");
        if (accountIdentity instanceof Map<?, ?> accountIdentityMap) {
            String value = text(accountIdentityMap.get("identityKey"));
            if (value != null) {
                return value;
            }
        }
        Object codexAuthJson = metadata.get("codex_auth_json");
        if (codexAuthJson instanceof Map<?, ?> codexAuthMap) {
            String value = text(codexAuthMap.get("identityKey"));
            if (value != null) {
                return value;
            }
            return text(codexAuthMap.get("identity_key"));
        }
        return text(metadata.get("identityKey"));
    }

    private UpstreamAccountEntity getRequired(Long id) {
        return upstreamAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定账号。"));
    }

    private UpstreamAccountPoolEntity resolvePool(Long poolId) {
        if (poolId == null) {
            return null;
        }
        return upstreamAccountPoolRepository.findById(poolId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定账号池。"));
    }

    private String resolveAccountName(String accountName, String poolName) {
        if (accountName != null && !accountName.isBlank()) {
            return accountName.trim();
        }
        String namePrefix = poolName == null || poolName.isBlank() ? "unassigned" : poolName;
        return namePrefix + "-" + Instant.now().toEpochMilli();
    }

    private String resolveExternalAccountId(String externalAccountId, String providerName) {
        if (externalAccountId != null && !externalAccountId.isBlank()) {
            return externalAccountId.trim();
        }
        return providerName.toLowerCase() + ":" + Instant.now().toEpochMilli();
    }

    private UpstreamAccountResponse toResponse(UpstreamAccountEntity entity) {
        long totalRequests = entity.getTotalRequestCount();
        long successRequests = entity.getSuccessfulRequestCount();
        long totalTokens = entity.getTotalTokenCount();
        long cacheHitTokens = entity.getTotalCacheHitTokenCount();
        long durationSamples = entity.getDurationSampleCount();
        long firstTokenSamples = entity.getFirstTokenSampleCount();
        return new UpstreamAccountResponse(
                entity.getId(),
                entity.getPool() == null ? null : entity.getPool().getId(),
                entity.getAccountName(),
                entity.getProviderType(),
                supportedModelCatalogService.normalize(entity.getSupportedModels()),
                entity.getExternalAccountId(),
                entity.isActive(),
                entity.isFrozen(),
                entity.isHealthy(),
                entity.getLastErrorMessage(),
                entity.getProxyId(),
                entity.getTlsFingerprintProfileId(),
                entity.getLastRefreshAt(),
                entity.getLastUsedAt(),
                entity.getTokenExpiresAt(),
                entity.getRefreshStatus(),
                entity.getRefreshFailureCount(),
                entity.getNextRefreshAfter(),
                entity.getCooldownUntil(),
                entity.getQuotaWindowStartedAt(),
                entity.getQuotaWindowSeconds(),
                entity.getQuotaRemainingTokens(),
                entity.getQuotaRemainingRequests(),
                entity.getHeaderSnapshotJson(),
                entity.getLastRefreshResultJson(),
                totalRequests,
                successRequests,
                entity.getFailedRequestCount(),
                entity.getCanceledRequestCount(),
                totalTokens,
                cacheHitTokens,
                entity.getTotalCacheWriteTokenCount(),
                entity.getTotalSavedInputTokenCount(),
                ratio(successRequests, totalRequests),
                ratio(cacheHitTokens, totalTokens),
                entity.getTotalDurationMs(),
                durationSamples,
                ratio(entity.getTotalDurationMs(), durationSamples),
                entity.getTotalFirstTokenMs(),
                firstTokenSamples,
                ratio(entity.getTotalFirstTokenMs(), firstTokenSamples),
                entity.getLastFirstTokenMs(),
                entity.getMinFirstTokenMs(),
                entity.getMaxFirstTokenMs(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return ((double) numerator) / denominator;
    }

    private JsonNode readMetadata(String metadataJson) {
        try {
            return objectMapper.readTree(metadataJson);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private Instant resolveInstant(Instant explicit, JsonNode metadata, List<String> instantKeys, List<String> expiresInKeys) {
        if (explicit != null) {
            return explicit;
        }
        for (String key : instantKeys) {
            Instant parsed = parseInstant(findValue(metadata, key));
            if (parsed != null) {
                return parsed;
            }
        }
        for (String key : expiresInKeys) {
            Long seconds = parseLong(findValue(metadata, key));
            if (seconds != null && seconds > 0) {
                return Instant.now().plusSeconds(seconds);
            }
        }
        return null;
    }

    private Long resolveLong(Long explicit, JsonNode metadata, List<String> keys) {
        if (explicit != null) {
            return explicit;
        }
        for (String key : keys) {
            Long parsed = parseLong(findValue(metadata, key));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private Integer resolveInteger(Integer explicit, JsonNode metadata, List<String> keys) {
        if (explicit != null) {
            return explicit;
        }
        Long value = resolveLong(null, metadata, keys);
        return value == null ? null : Math.toIntExact(value);
    }

    private String resolveJsonSnapshot(String explicit, JsonNode metadata, List<String> keys) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        for (String key : keys) {
            JsonNode value = findValue(metadata, key);
            if (value != null && !value.isNull()) {
                return value.isTextual() ? value.asText() : value.toString();
            }
        }
        return null;
    }

    private String resolveRefreshStatus(String explicit, JsonNode metadata, boolean hasRefreshToken) {
        String status = explicit == null || explicit.isBlank()
                ? parseText(findValue(metadata, "refresh_status"))
                : explicit;
        if (status == null || status.isBlank()) {
            status = parseText(findValue(metadata, "refreshStatus"));
        }
        if (status == null || status.isBlank()) {
            status = hasRefreshToken ? "READY" : "ACCESS_ONLY";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private JsonNode findValue(JsonNode root, String key) {
        return root == null ? null : root.findValue(key);
    }

    private Instant parseInstant(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            long epoch = value.asLong();
            return epoch > 10_000_000_000L ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
        }
        String text = parseText(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Long parseLong(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asLong();
        }
        String text = parseText(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String parseText(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return value.asText();
        }
        if (value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        return null;
    }

    private String routeBlockReason(
            UpstreamAccountEntity entity,
            String clientFamily,
            String adoptionDecision,
            String authorizationStatus) {
        if (!entity.isActive()) {
            return "ACCOUNT_INACTIVE";
        }
        if (entity.isFrozen()) {
            return "ACCOUNT_FROZEN";
        }
        if (!entity.isHealthy()) {
            return "ACCOUNT_UNHEALTHY";
        }
        if ("REJECTED".equalsIgnoreCase(adoptionDecision)) {
            return "IDENTITY_REJECTED";
        }
        if ("FAILED".equalsIgnoreCase(authorizationStatus)) {
            return "AUTHORIZATION_FAILED";
        }
        if (entity.getQuotaRemainingTokens() != null && entity.getQuotaRemainingTokens() <= 0) {
            return "QUOTA_TOKENS_EXHAUSTED";
        }
        if (entity.getQuotaRemainingRequests() != null && entity.getQuotaRemainingRequests() <= 0) {
            return "QUOTA_REQUESTS_EXHAUSTED";
        }
        List<String> allowedFamilies = entity.getPool() == null || entity.getPool().getAllowedClientFamilies() == null
                ? List.of()
                : entity.getPool().getAllowedClientFamilies().stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(value -> value.trim().toUpperCase(Locale.ROOT))
                        .toList();
        if (!allowedFamilies.isEmpty() && !allowedFamilies.contains(clientFamily.toUpperCase(Locale.ROOT))) {
            return "CLIENT_FAMILY_NOT_ALLOWED";
        }
        return null;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

}
