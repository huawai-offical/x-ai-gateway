package com.prodigalgal.xaigateway.portal.application;

import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.SystemSettingEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.SystemSettingRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class PortalSocialOAuthConfigService {

    public static final String SETTING_KEY = "portal.social-oauth";

    private final SystemSettingRepository systemSettingRepository;
    private final GatewayProperties gatewayProperties;
    private final CredentialCryptoService credentialCryptoService;
    private final ObjectMapper objectMapper;

    public PortalSocialOAuthConfigService(
            SystemSettingRepository systemSettingRepository,
            GatewayProperties gatewayProperties,
            CredentialCryptoService credentialCryptoService,
            ObjectMapper objectMapper) {
        this.systemSettingRepository = systemSettingRepository;
        this.gatewayProperties = gatewayProperties;
        this.credentialCryptoService = credentialCryptoService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PortalSocialOAuthRuntimeConfig getRuntimeConfig() {
        PersistedSocialOAuthSettings persisted = readPersisted().orElse(null);
        boolean enabled = persisted == null ? defaultEnabled() : persisted.enabled();
        Map<SocialOAuthProvider, PortalSocialOAuthRuntimeConfig.ProviderConfig> providers =
                new EnumMap<>(SocialOAuthProvider.class);
        for (SocialOAuthProvider provider : SocialOAuthProvider.values()) {
            PersistedSocialOAuthProviderSettings providerSettings = persisted == null || persisted.providers() == null
                    ? null
                    : persisted.providers().get(provider.wireName());
            boolean providerEnabled = providerSettings == null ? defaultProviderEnabled(provider) : providerSettings.enabled();
            String clientId = valueOrDefault(providerSettings == null ? null : providerSettings.clientId(), defaultClientId(provider));
            String clientSecret = decryptOrDefault(
                    providerSettings == null ? null : providerSettings.clientSecretCiphertext(),
                    defaultClientSecret(provider));
            providers.put(provider, new PortalSocialOAuthRuntimeConfig.ProviderConfig(
                    provider,
                    providerEnabled,
                    clientId,
                    clientSecret,
                    defaultTokenEndpoint(provider),
                    defaultUserInfoEndpoint(provider),
                    defaultJwksUri(provider),
                    provider.defaultScopes()
            ));
        }
        return new PortalSocialOAuthRuntimeConfig(enabled, providers);
    }

    @Transactional(readOnly = true)
    public PortalSocialOAuthSettingsView getSettingsView() {
        PersistedSocialOAuthSettings persisted = readPersisted().orElse(null);
        PortalSocialOAuthRuntimeConfig runtime = getRuntimeConfig();
        List<PortalSocialOAuthSettingsView.ProviderView> providers = Arrays.stream(SocialOAuthProvider.values())
                .map(provider -> {
                    PortalSocialOAuthRuntimeConfig.ProviderConfig config = runtime.provider(provider);
                    return new PortalSocialOAuthSettingsView.ProviderView(
                            provider.wireName(),
                            provider.displayName(),
                            config.enabled(),
                            config.clientId(),
                            config.clientSecretConfigured(),
                            config.scopes(),
                            config.configuredForLogin()
                    );
                })
                .toList();
        return new PortalSocialOAuthSettingsView(
                persisted == null ? defaultEnabled() : persisted.enabled(),
                providers,
                socialOAuthUpdatedAt()
        );
    }

    public PortalSocialOAuthSettingsView saveSettings(PortalSocialOAuthSettingsUpdate update) {
        PersistedSocialOAuthSettings current = readPersisted().orElseGet(this::defaultPersisted);
        boolean enabled = update == null || update.enabled() == null ? current.enabled() : update.enabled();
        Map<String, PersistedSocialOAuthProviderSettings> providers = new java.util.LinkedHashMap<>(
                current.providers() == null ? Map.of() : current.providers());
        if (update != null && update.providers() != null) {
            for (PortalSocialOAuthSettingsUpdate.ProviderUpdate providerUpdate : update.providers()) {
                if (providerUpdate == null || providerUpdate.provider() == null || providerUpdate.provider().isBlank()) {
                    continue;
                }
                SocialOAuthProvider provider = SocialOAuthProvider.fromWireName(providerUpdate.provider());
                PersistedSocialOAuthProviderSettings existing = providers.getOrDefault(provider.wireName(), defaultProviderPersisted(provider));
                boolean providerEnabled = providerUpdate.enabled() == null ? existing.enabled() : providerUpdate.enabled();
                String clientId = providerUpdate.clientId() == null ? existing.clientId() : trimToNull(providerUpdate.clientId());
                String secretCiphertext = existing.clientSecretCiphertext();
                if (Boolean.TRUE.equals(providerUpdate.clearClientSecret())) {
                    secretCiphertext = null;
                } else if (providerUpdate.clientSecret() != null && !providerUpdate.clientSecret().isBlank()) {
                    secretCiphertext = credentialCryptoService.encrypt(providerUpdate.clientSecret().trim());
                }
                providers.put(provider.wireName(), new PersistedSocialOAuthProviderSettings(
                        providerEnabled,
                        clientId,
                        secretCiphertext
                ));
            }
        }

        PersistedSocialOAuthSettings next = new PersistedSocialOAuthSettings(enabled, providers);
        write(next);
        return getSettingsView();
    }

    public void resetSettings() {
        systemSettingRepository.findBySettingKey(SETTING_KEY).ifPresent(systemSettingRepository::delete);
    }

    private boolean defaultEnabled() {
        return false;
    }

    private boolean defaultProviderEnabled(SocialOAuthProvider provider) {
        return false;
    }

    private PersistedSocialOAuthSettings defaultPersisted() {
        Map<String, PersistedSocialOAuthProviderSettings> providers = new java.util.LinkedHashMap<>();
        for (SocialOAuthProvider provider : SocialOAuthProvider.values()) {
            providers.put(provider.wireName(), defaultProviderPersisted(provider));
        }
        return new PersistedSocialOAuthSettings(defaultEnabled(), providers);
    }

    private PersistedSocialOAuthProviderSettings defaultProviderPersisted(SocialOAuthProvider provider) {
        return new PersistedSocialOAuthProviderSettings(
                defaultProviderEnabled(provider),
                defaultClientId(provider),
                null
        );
    }

    private Optional<PersistedSocialOAuthSettings> readPersisted() {
        return systemSettingRepository.findBySettingKey(SETTING_KEY)
                .map(SystemSettingEntity::getSettingValue)
                .map(value -> {
                    try {
                        return objectMapper.readValue(value, PersistedSocialOAuthSettings.class);
                    } catch (JacksonException exception) {
                        throw new IllegalStateException("无法读取 Portal 社交 OAuth 配置。", exception);
                    }
                });
    }

    private void write(PersistedSocialOAuthSettings value) {
        SystemSettingEntity entity = systemSettingRepository.findBySettingKey(SETTING_KEY).orElseGet(SystemSettingEntity::new);
        entity.setSettingKey(SETTING_KEY);
        entity.setValueType("json");
        entity.setDescription("Portal 社交 OAuth 登录配置。");
        try {
            entity.setSettingValue(objectMapper.writeValueAsString(value));
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法保存 Portal 社交 OAuth 配置。", exception);
        }
        systemSettingRepository.save(entity);
    }

    private Instant socialOAuthUpdatedAt() {
        return systemSettingRepository.findBySettingKey(SETTING_KEY)
                .map(SystemSettingEntity::getUpdatedAt)
                .orElse(null);
    }

    private String decryptOrDefault(String ciphertext, String defaultValue) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return defaultValue;
        }
        return credentialCryptoService.decrypt(ciphertext);
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultClientId(SocialOAuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> gatewayProperties.getOauth().getGoogleSocialClientId();
            case GITHUB -> gatewayProperties.getOauth().getGithubSocialClientId();
            case QQ -> gatewayProperties.getOauth().getQqSocialClientId();
            case WECHAT -> gatewayProperties.getOauth().getWechatSocialClientId();
            case META -> gatewayProperties.getOauth().getMetaSocialClientId();
            case X -> gatewayProperties.getOauth().getXSocialClientId();
        };
    }

    private String defaultClientSecret(SocialOAuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> gatewayProperties.getOauth().getGoogleSocialClientSecret();
            case GITHUB -> gatewayProperties.getOauth().getGithubSocialClientSecret();
            case QQ -> gatewayProperties.getOauth().getQqSocialClientSecret();
            case WECHAT -> gatewayProperties.getOauth().getWechatSocialClientSecret();
            case META -> gatewayProperties.getOauth().getMetaSocialClientSecret();
            case X -> gatewayProperties.getOauth().getXSocialClientSecret();
        };
    }

    private String defaultTokenEndpoint(SocialOAuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> gatewayProperties.getOauth().getGoogleSocialTokenEndpoint();
            case GITHUB -> gatewayProperties.getOauth().getGithubSocialTokenEndpoint();
            case QQ -> gatewayProperties.getOauth().getQqSocialTokenEndpoint();
            case WECHAT -> gatewayProperties.getOauth().getWechatSocialTokenEndpoint();
            case META -> gatewayProperties.getOauth().getMetaSocialTokenEndpoint();
            case X -> gatewayProperties.getOauth().getXSocialTokenEndpoint();
        };
    }

    private String defaultUserInfoEndpoint(SocialOAuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> gatewayProperties.getOauth().getGoogleSocialUserInfoEndpoint();
            case GITHUB -> gatewayProperties.getOauth().getGithubSocialUserEndpoint();
            case QQ -> gatewayProperties.getOauth().getQqSocialUserInfoEndpoint();
            case WECHAT -> gatewayProperties.getOauth().getWechatSocialUserInfoEndpoint();
            case META -> gatewayProperties.getOauth().getMetaSocialUserInfoEndpoint();
            case X -> gatewayProperties.getOauth().getXSocialUserInfoEndpoint();
        };
    }

    private String defaultJwksUri(SocialOAuthProvider provider) {
        return provider == SocialOAuthProvider.GOOGLE ? gatewayProperties.getOauth().getGoogleSocialJwksUri() : null;
    }

    public record PersistedSocialOAuthSettings(
            boolean enabled,
            Map<String, PersistedSocialOAuthProviderSettings> providers
    ) {
    }

    public record PersistedSocialOAuthProviderSettings(
            boolean enabled,
            String clientId,
            String clientSecretCiphertext
    ) {
    }
}
