package com.prodigalgal.xaigateway.gateway.core.credential;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CredentialMaterialResolver {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AccountSelectionService accountSelectionService;
    private final CredentialCryptoService credentialCryptoService;
    private final ObjectMapper objectMapper;

    public CredentialMaterialResolver(
            AccountSelectionService accountSelectionService,
            CredentialCryptoService credentialCryptoService,
            ObjectMapper objectMapper) {
        this.accountSelectionService = accountSelectionService;
        this.credentialCryptoService = credentialCryptoService;
        this.objectMapper = objectMapper;
    }

    public ResolvedCredentialMaterial resolve(RouteSelectionResult selectionResult, UpstreamCredentialEntity credential) {
        if (selectionResult == null) {
            return resolveStored(credential);
        }
        Optional<UpstreamAccountEntity> account = accountSelectionService.resolveActiveAccount(
                selectionResult.distributedKeyId(),
                selectionResult.selectedCandidate().candidate().providerType(),
                selectionResult.clientFamily() == null ? GatewayClientFamily.GENERIC_OPENAI : selectionResult.clientFamily(),
                300
        );
        return account.map(value -> resolveFromAccount(credential, value)).orElseGet(() -> resolveStored(credential));
    }

    public ResolvedCredentialMaterial resolveStored(UpstreamCredentialEntity credential) {
        if (credential == null) {
            throw new IllegalArgumentException("未找到可解析的凭证。");
        }
        String secret = credentialCryptoService.decrypt(credential.getApiKeyCiphertext());
        return new ResolvedCredentialMaterial(
                credential.getId(),
                credential.getSiteProfileId(),
                defaultAuthKind(credential.getAuthKind(), credential.getProviderType(), credential.getBaseUrl(), parseMetadata(credential.getCredentialMetadataJson())),
                secret,
                credential.getApiKeyFingerprint(),
                parseMetadata(credential.getCredentialMetadataJson()),
                null,
                "credential"
        );
    }

    public ResolvedCredentialMaterial resolveTransient(
            ProviderType providerType,
            String baseUrl,
            CredentialAuthKind authKind,
            String secret,
            Map<String, Object> metadata) {
        if ((secret == null || secret.isBlank()) && providerType != ProviderType.OLLAMA_DIRECT) {
            throw new IllegalArgumentException("凭证 secret 不能为空。");
        }
        Map<String, Object> normalizedMetadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        String normalizedSecret = secret == null ? "" : secret.trim();
        return new ResolvedCredentialMaterial(
                null,
                null,
                defaultAuthKind(authKind, providerType, baseUrl, normalizedMetadata),
                normalizedSecret,
                normalizedSecret.isBlank() ? null : credentialCryptoService.fingerprint(normalizedSecret),
                normalizedMetadata,
                null,
                "transient"
        );
    }

    private ResolvedCredentialMaterial resolveFromAccount(
            UpstreamCredentialEntity credential,
            UpstreamAccountEntity account) {
        String secret = credentialCryptoService.decrypt(account.getAccessTokenCiphertext());
        Map<String, Object> metadata = new LinkedHashMap<>(parseMetadata(credential.getCredentialMetadataJson()));
        metadata.putAll(parseMetadata(account.getMetadataJson()));
        if (account.getSiteProfileId() != null) {
            metadata.putIfAbsent("siteProfileId", account.getSiteProfileId());
        }
        return new ResolvedCredentialMaterial(
                credential.getId(),
                credential.getSiteProfileId() == null ? account.getSiteProfileId() : credential.getSiteProfileId(),
                authKindForAccount(account.getProviderType(), credential.getAuthKind()),
                secret,
                credentialCryptoService.fingerprint(secret),
                metadata,
                account.getId(),
                "account"
        );
    }

    private CredentialAuthKind authKindForAccount(
            UpstreamAccountProviderType providerType,
            CredentialAuthKind fallback) {
        if (providerType == null) {
            return CredentialAuthKind.defaultValue(fallback);
        }
        return switch (providerType) {
            case GEMINI_OAUTH -> CredentialAuthKind.GOOGLE_ACCESS_TOKEN;
            case OPENAI_OAUTH, CLAUDE_ACCOUNT -> CredentialAuthKind.ACCESS_TOKEN;
        };
    }

    private CredentialAuthKind defaultAuthKind(
            CredentialAuthKind authKind,
            ProviderType providerType,
            String baseUrl,
            Map<String, Object> metadata) {
        if (authKind != null) {
            return authKind;
        }
        if (providerType == ProviderType.GEMINI_DIRECT
                && baseUrl != null
                && baseUrl.toLowerCase().contains("aiplatform.googleapis.com")) {
            return CredentialAuthKind.GOOGLE_ACCESS_TOKEN;
        }
        if (metadata.containsKey("projectId") && metadata.containsKey("location")) {
            return CredentialAuthKind.GOOGLE_ACCESS_TOKEN;
        }
        return CredentialAuthKind.API_KEY;
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法解析凭证 metadata。", exception);
        }
    }
}
