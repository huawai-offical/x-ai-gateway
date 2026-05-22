package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ExportedClientConfigResponse;
import com.prodigalgal.xaigateway.admin.api.AccountImportAuthJsonRequest;
import com.prodigalgal.xaigateway.admin.api.AccountModelRefreshResponse;
import com.prodigalgal.xaigateway.admin.api.OfficialAccountType;
import com.prodigalgal.xaigateway.admin.api.ProgrammingAccountIdentityResponse;
import com.prodigalgal.xaigateway.admin.api.UpstreamAccountResponse;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
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
    private final UpstreamAccountGroupRepository upstreamAccountGroupRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final SupportedModelCatalogService supportedModelCatalogService;
    private final OAuthSessionRefreshService oauthSessionRefreshService;
    private final ObjectMapper objectMapper;
    private final CodexAuthJsonParser codexAuthJsonParser;
    private final SensitiveJsonSanitizer sensitiveJsonSanitizer;

    public AccountAdminService(
            UpstreamAccountRepository upstreamAccountRepository,
            UpstreamAccountGroupRepository upstreamAccountGroupRepository,
            CredentialCryptoService credentialCryptoService,
            SupportedModelCatalogService supportedModelCatalogService,
            OAuthSessionRefreshService oauthSessionRefreshService,
            ObjectMapper objectMapper) {
        this.upstreamAccountRepository = upstreamAccountRepository;
        this.upstreamAccountGroupRepository = upstreamAccountGroupRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.supportedModelCatalogService = supportedModelCatalogService;
        this.oauthSessionRefreshService = oauthSessionRefreshService;
        this.objectMapper = objectMapper;
        this.codexAuthJsonParser = new CodexAuthJsonParser(objectMapper);
        this.sensitiveJsonSanitizer = new SensitiveJsonSanitizer(objectMapper);
    }

    @Transactional(readOnly = true)
    public List<UpstreamAccountResponse> list(Long groupId) {
        if (groupId == null) {
            return upstreamAccountRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
        }
        return listByGroup(groupId);
    }

    @Transactional(readOnly = true)
    public List<UpstreamAccountResponse> listByGroup(Long groupId) {
        return upstreamAccountRepository.findAllByGroup_IdOrderByCreatedAtDesc(groupId).stream().map(this::toResponse).toList();
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

    public AccountModelRefreshResponse refreshModels(Long id) {
        UpstreamAccountEntity entity = getRequired(id);
        List<String> models = supportedModelCatalogService.listByUpstreamProvider(entity.getProviderType());
        if (entity.getProviderType() == UpstreamAccountProviderType.CODEX_OAUTH) {
            models = mergeModels(models, OfficialAccountType.CODEX.defaultModels());
        }
        if (models.isEmpty()) {
            models = supportedModelCatalogService.normalize(entity.getSupportedModels());
        }
        entity.setSupportedModels(models);
        UpstreamAccountEntity saved = upstreamAccountRepository.save(entity);
        return new AccountModelRefreshResponse(
                saved.getId(),
                models.size(),
                models.stream().limit(10).toList(),
                Instant.now()
        );
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
        UpstreamAccountGroupEntity group = resolveGroup(request.groupId());
        UpstreamAccountProviderType providerType = group.getProviderType();
        String metadataJson = resolveImportMetadataJson(request);
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
        entity.setGroup(group);
        entity.setProviderType(providerType);
        entity.setAccountName(resolveAccountName(
                firstNonBlank(request.accountName(), parsedCodexAuth == null ? null : parsedCodexAuth.accountName()),
                group == null ? null : group.getGroupName()
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
        entity.setSupportedModels(supportedModelCatalogService.resolveForAccountImport(group, request.supportedModels()));
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

    private String resolveImportMetadataJson(AccountImportAuthJsonRequest request) {
        String explicitAuthJson = firstNonBlank(request.authJsonContent(), request.metadataJson());
        if (explicitAuthJson != null) {
            return explicitAuthJson.trim();
        }

        String pathText = firstNonBlank(request.authJsonFilePath(), firstPath(request.authJsonFilePaths()));
        if (pathText == null) {
            return "{}";
        }
        try {
            Path path = Path.of(pathText.trim()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("auth.json 文件不存在或不可读取。");
            }
            return Files.readString(path).trim();
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("auth.json 文件路径无效。", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("auth.json 文件读取失败。", exception);
        }
    }

    private String firstPath(List<String> paths) {
        if (paths == null) {
            return null;
        }
        for (String path : paths) {
            if (path != null && !path.isBlank()) {
                return path;
            }
        }
        return null;
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

    private List<String> mergeModels(List<String> primary, List<String> fallback) {
        java.util.LinkedHashSet<String> models = new java.util.LinkedHashSet<>();
        models.addAll(supportedModelCatalogService.normalize(primary));
        models.addAll(supportedModelCatalogService.normalize(fallback));
        return supportedModelCatalogService.normalize(List.copyOf(models));
    }

    private UpstreamAccountGroupEntity resolveGroup(Long groupId) {
        if (groupId == null) {
            throw new IllegalArgumentException("上游账号必须归入一个账号分组。");
        }
        return upstreamAccountGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定账号分组。"));
    }

    private String resolveAccountName(String accountName, String groupName) {
        if (accountName != null && !accountName.isBlank()) {
            return accountName.trim();
        }
        String namePrefix = groupName == null || groupName.isBlank() ? "unassigned" : groupName;
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
                entity.getGroup() == null ? null : entity.getGroup().getId(),
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
        List<String> allowedFamilies = entity.getGroup() == null || entity.getGroup().getAllowedClientFamilies() == null
                ? List.of()
                : entity.getGroup().getAllowedClientFamilies().stream()
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
