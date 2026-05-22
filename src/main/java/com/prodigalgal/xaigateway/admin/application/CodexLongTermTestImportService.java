package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OfficialAccountType;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class CodexLongTermTestImportService {

    private static final String ADAPTER_NAME = "codex-long-term-test-import";
    private static final String DEFAULT_GROUP_DESCRIPTION = "Codex 真实 auth.json 长期测试账号分组。";

    private final UpstreamAccountRepository accountRepository;
    private final UpstreamAccountGroupRepository groupRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final SupportedModelCatalogService supportedModelCatalogService;
    private final ObjectMapper objectMapper;
    private final CodexAuthJsonParser codexAuthJsonParser;
    private final CodexResponsesSmokeHttpClient codexResponsesSmokeHttpClient;

    public CodexLongTermTestImportService(
            UpstreamAccountRepository accountRepository,
            UpstreamAccountGroupRepository groupRepository,
            CredentialCryptoService credentialCryptoService,
            SupportedModelCatalogService supportedModelCatalogService,
            ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.groupRepository = groupRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.supportedModelCatalogService = supportedModelCatalogService;
        this.objectMapper = objectMapper;
        this.codexAuthJsonParser = new CodexAuthJsonParser(objectMapper);
        this.codexResponsesSmokeHttpClient = new CodexResponsesSmokeHttpClient(objectMapper);
    }

    @Transactional
    public CodexLongTermTestImportResult importAuthJson(String rawJson, String requestedGroupName) {
        CodexAuthJsonParser.ParsedCodexAuthJson parsed = codexAuthJsonParser.parse(rawJson);
        Instant now = Instant.now();
        UpstreamAccountGroupEntity group = resolveGroup(requestedGroupName);
        String externalAccountId = parsed.identityKey();
        UpstreamAccountEntity entity = resolveExistingAccount(parsed, externalAccountId).orElseGet(UpstreamAccountEntity::new);
        boolean created = entity.getId() == null;

        entity.setGroup(group);
        entity.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        entity.setAccountName(parsed.accountName());
        entity.setExternalAccountId(externalAccountId);
        entity.setAccessTokenCiphertext(credentialCryptoService.encrypt(parsed.accessToken()));
        entity.setRefreshTokenCiphertext(parsed.refreshToken() == null ? null : credentialCryptoService.encrypt(parsed.refreshToken()));
        entity.setActive(true);
        entity.setFrozen(false);
        entity.setHealthy(true);
        entity.setTokenExpiresAt(parsed.tokenExpiresAt());
        entity.setLastRefreshAt(now);
        entity.setRefreshStatus("QUOTA_READY");
        entity.setRefreshFailureCount(0);
        entity.setCooldownUntil(null);
        entity.setLastErrorMessage(null);
        entity.setSupportedModels(supportedModelCatalogService.normalize(OfficialAccountType.CODEX.defaultModels()));

        int windowSeconds = OfficialAccountType.CODEX.defaultQuotaWindowSeconds();
        Instant resetAt = parsed.tokenExpiresAt() == null ? now.plusSeconds(windowSeconds) : parsed.tokenExpiresAt();
        entity.setQuotaWindowSeconds(windowSeconds);
        entity.setQuotaWindowStartedAt(resetAt.minusSeconds(windowSeconds));
        entity.setQuotaRemainingTokens(OfficialAccountType.CODEX.defaultQuotaRemainingTokens());
        entity.setQuotaRemainingRequests(OfficialAccountType.CODEX.defaultQuotaRemainingRequests());
        entity.setNextRefreshAfter(now.plusSeconds(Math.min(3600, Math.max(300, windowSeconds / 4L))));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("official_account_type", OfficialAccountType.CODEX.name());
        metadata.put("client_family", OfficialAccountType.CODEX.clientFamily());
        metadata.put("managed_by", ADAPTER_NAME);
        metadata.put("long_term_test", true);
        metadata.put("codex_auth_json", parsed.safeSummary());
        metadata.put("account_identity", Map.of(
                "identityKey", parsed.identityKey(),
                "identitySource", parsed.identitySource(),
                "identityStrength", parsed.identityStrength(),
                "accountId", parsed.accountId() == null ? "unknown" : parsed.accountId()
        ));
        metadata.put("plan_tier", OfficialAccountType.CODEX.defaultPlanTier());
        metadata.put("subscription_tier", OfficialAccountType.CODEX.defaultPlanTier());
        metadata.put("quota_status", "READY");
        metadata.put("quota_reset_at", resetAt.toString());
        metadata.put("quota_last_refresh_at", now.toString());
        metadata.put("quota_next_refresh_after", entity.getNextRefreshAfter().toString());
        entity.setMetadataJson(writeJson(metadata));

        entity.setHeaderSnapshotJson(writeJson(Map.of(
                "authorization", "Bearer ***",
                "x-client-family", "CODEX",
                "x-long-term-test", "true"
        )));
        entity.setLastRefreshResultJson(writeJson(Map.of(
                "status", created ? "created" : "updated",
                "adapter", ADAPTER_NAME,
                "accountType", OfficialAccountType.CODEX.name(),
                "externalAccountId", externalAccountId,
                "identitySource", parsed.identitySource(),
                "identityStrength", parsed.identityStrength(),
                "credentialPresence", Map.of(
                        "accessToken", true,
                        "refreshToken", parsed.refreshToken() != null
                ),
                "responsesSmoke", responsesSmokePreview(entity.getSupportedModels().get(0)),
                "importedAt", now.toString()
        )));

        UpstreamAccountEntity saved = accountRepository.save(entity);
        String routeBlockReason = routeBlockReason(saved);
        return new CodexLongTermTestImportResult(
                saved.getId(),
                group.getId(),
                saved.getAccountName(),
                saved.getExternalAccountId(),
                created ? "CREATED" : "UPDATED",
                routeBlockReason == null,
                routeBlockReason,
                String.valueOf(parsed.safeSummary().get("accessTokenFingerprint")),
                now,
                parsed.safeSummary()
        );
    }

    private Optional<UpstreamAccountEntity> resolveExistingAccount(
            CodexAuthJsonParser.ParsedCodexAuthJson parsed,
            String externalAccountId) {
        Optional<UpstreamAccountEntity> byExternalId = accountRepository
                .findFirstByProviderTypeAndExternalAccountIdOrderByUpdatedAtDesc(UpstreamAccountProviderType.CODEX_OAUTH, externalAccountId);
        if (byExternalId.isPresent()) {
            return byExternalId;
        }
        if (parsed.accountId() != null && !parsed.accountId().isBlank() && !parsed.accountId().equals(externalAccountId)) {
            Optional<UpstreamAccountEntity> byLegacyAccountId = accountRepository
                    .findFirstByProviderTypeAndExternalAccountIdOrderByUpdatedAtDesc(UpstreamAccountProviderType.CODEX_OAUTH, parsed.accountId());
            if (byLegacyAccountId.isPresent()) {
                return byLegacyAccountId;
            }
        }
        if ("WEAK_TOKEN".equals(parsed.identityStrength())) {
            return Optional.empty();
        }
        List<UpstreamAccountEntity> accounts = accountRepository.findAllByProviderTypeOrderByUpdatedAtDesc(UpstreamAccountProviderType.CODEX_OAUTH);
        for (UpstreamAccountEntity account : accounts == null ? Collections.<UpstreamAccountEntity>emptyList() : accounts) {
            if (parsed.identityKey().equals(metadataIdentityKey(account.getMetadataJson()))) {
                return Optional.of(account);
            }
        }
        return Optional.empty();
    }

    private String metadataIdentityKey(String metadataJson) {
        Map<String, Object> metadata = readMap(metadataJson);
        String direct = text(metadata.get("identityKey"));
        if (direct != null) {
            return direct;
        }
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
        return null;
    }

    private UpstreamAccountGroupEntity resolveGroup(String requestedGroupName) {
        String groupName = requestedGroupName == null || requestedGroupName.isBlank()
                ? "codex-long-term-test"
                : requestedGroupName.trim();
        return groupRepository.findByGroupNameIgnoreCase(groupName).orElseGet(() -> {
            UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
            group.setGroupName(groupName);
            group.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
            group.setSupportedModels(supportedModelCatalogService.normalize(OfficialAccountType.CODEX.defaultModels()));
            group.setSupportedProtocols(List.of("responses", "chat_completions"));
            group.setAllowedClientFamilies(List.of("CODEX"));
            group.setActive(true);
            group.setDescription(DEFAULT_GROUP_DESCRIPTION);
            return groupRepository.save(group);
        });
    }

    private Map<String, Object> responsesSmokePreview(String model) {
        return codexResponsesSmokeHttpClient.requestPreview(
                model,
                "x-ai-gateway codex long-term smoke",
                null,
                null
        );
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("无法序列化 Codex 长期测试导入结果。", exception);
        }
    }

    private Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(value, new TypeReference<>() {
            });
            return parsed == null ? Map.of() : parsed;
        } catch (JacksonException exception) {
            return Map.of();
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
