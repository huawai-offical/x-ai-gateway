package com.prodigalgal.xaigateway.infra.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private final Routing routing = new Routing();
    private final Cache cache = new Cache();
    private final Security security = new Security();
    private final Storage storage = new Storage();
    private final Web web = new Web();
    private final Oauth oauth = new Oauth();

    public Routing getRouting() {
        return routing;
    }

    public Cache getCache() {
        return cache;
    }

    public Security getSecurity() {
        return security;
    }

    public Storage getStorage() {
        return storage;
    }

    public Web getWeb() {
        return web;
    }

    public Oauth getOauth() {
        return oauth;
    }

    public static class Routing {

        private boolean interopPlanEnabled = true;
        private boolean routeDecisionLoggingEnabled = true;
        private int maxFallbackAttempts = 3;

        public boolean isInteropPlanEnabled() {
            return interopPlanEnabled;
        }

        public void setInteropPlanEnabled(boolean interopPlanEnabled) {
            this.interopPlanEnabled = interopPlanEnabled;
        }

        public boolean isRouteDecisionLoggingEnabled() {
            return routeDecisionLoggingEnabled;
        }

        public void setRouteDecisionLoggingEnabled(boolean routeDecisionLoggingEnabled) {
            this.routeDecisionLoggingEnabled = routeDecisionLoggingEnabled;
        }

        public int getMaxFallbackAttempts() {
            return maxFallbackAttempts;
        }

        public void setMaxFallbackAttempts(int maxFallbackAttempts) {
            this.maxFallbackAttempts = maxFallbackAttempts;
        }
    }

    public static class Cache {

        private boolean enabled = true;
        private boolean stickyByDistributedKey = true;
        private boolean prefixAffinityEnabled = true;
        private boolean fingerprintAffinityEnabled = true;
        private Duration affinityTtl = Duration.ofMinutes(30);
        private Duration authTtl = Duration.ofMinutes(10);
        private Duration routeTtl = Duration.ofMinutes(2);
        private Duration healthCooldownTtl = Duration.ofMinutes(5);
        private int fingerprintMaxPrefixTokens = 1024;
        private String keyPrefix = "xag";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isStickyByDistributedKey() {
            return stickyByDistributedKey;
        }

        public void setStickyByDistributedKey(boolean stickyByDistributedKey) {
            this.stickyByDistributedKey = stickyByDistributedKey;
        }

        public boolean isPrefixAffinityEnabled() {
            return prefixAffinityEnabled;
        }

        public void setPrefixAffinityEnabled(boolean prefixAffinityEnabled) {
            this.prefixAffinityEnabled = prefixAffinityEnabled;
        }

        public boolean isFingerprintAffinityEnabled() {
            return fingerprintAffinityEnabled;
        }

        public void setFingerprintAffinityEnabled(boolean fingerprintAffinityEnabled) {
            this.fingerprintAffinityEnabled = fingerprintAffinityEnabled;
        }

        public Duration getAffinityTtl() {
            return affinityTtl;
        }

        public void setAffinityTtl(Duration affinityTtl) {
            this.affinityTtl = affinityTtl;
        }

        public Duration getAuthTtl() {
            return authTtl;
        }

        public void setAuthTtl(Duration authTtl) {
            this.authTtl = authTtl;
        }

        public Duration getRouteTtl() {
            return routeTtl;
        }

        public void setRouteTtl(Duration routeTtl) {
            this.routeTtl = routeTtl;
        }

        public Duration getHealthCooldownTtl() {
            return healthCooldownTtl;
        }

        public void setHealthCooldownTtl(Duration healthCooldownTtl) {
            this.healthCooldownTtl = healthCooldownTtl;
        }

        public int getFingerprintMaxPrefixTokens() {
            return fingerprintMaxPrefixTokens;
        }

        public void setFingerprintMaxPrefixTokens(int fingerprintMaxPrefixTokens) {
            this.fingerprintMaxPrefixTokens = fingerprintMaxPrefixTokens;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }

    public static class Security {

        private boolean distributedKeyHashOnly = true;
        private boolean encryptUpstreamApiKey = true;
        private String encryptionKey = "change-me-local-only";

        public boolean isDistributedKeyHashOnly() {
            return distributedKeyHashOnly;
        }

        public void setDistributedKeyHashOnly(boolean distributedKeyHashOnly) {
            this.distributedKeyHashOnly = distributedKeyHashOnly;
        }

        public boolean isEncryptUpstreamApiKey() {
            return encryptUpstreamApiKey;
        }

        public void setEncryptUpstreamApiKey(boolean encryptUpstreamApiKey) {
            this.encryptUpstreamApiKey = encryptUpstreamApiKey;
        }

        public String getEncryptionKey() {
            return encryptionKey;
        }

        public void setEncryptionKey(String encryptionKey) {
            this.encryptionKey = encryptionKey;
        }
    }

    public static class Storage {

        private String fileRoot = ".data/files";

        public String getFileRoot() {
            return fileRoot;
        }

        public void setFileRoot(String fileRoot) {
            this.fileRoot = fileRoot;
        }
    }

    public static class Web {
        private String publicBaseUrl = "http://localhost:3000";

        public String getPublicBaseUrl() { return publicBaseUrl; }
        public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    }

    public static class Oauth {
        private String openaiAuthBaseUrl = "https://auth.openai.com/oauth/authorize";
        private String geminiAuthBaseUrl = "https://accounts.google.com/o/oauth2/v2/auth";
        private String claudeAuthBaseUrl = "https://claude.ai/oauth/authorize";
        private String openaiClientId = "openai-local-client";
        private String geminiClientId = "gemini-local-client";
        private String claudeClientId = "claude-local-client";

        public String getOpenaiAuthBaseUrl() { return openaiAuthBaseUrl; }
        public void setOpenaiAuthBaseUrl(String openaiAuthBaseUrl) { this.openaiAuthBaseUrl = openaiAuthBaseUrl; }
        public String getGeminiAuthBaseUrl() { return geminiAuthBaseUrl; }
        public void setGeminiAuthBaseUrl(String geminiAuthBaseUrl) { this.geminiAuthBaseUrl = geminiAuthBaseUrl; }
        public String getClaudeAuthBaseUrl() { return claudeAuthBaseUrl; }
        public void setClaudeAuthBaseUrl(String claudeAuthBaseUrl) { this.claudeAuthBaseUrl = claudeAuthBaseUrl; }
        public String getOpenaiClientId() { return openaiClientId; }
        public void setOpenaiClientId(String openaiClientId) { this.openaiClientId = openaiClientId; }
        public String getGeminiClientId() { return geminiClientId; }
        public void setGeminiClientId(String geminiClientId) { this.geminiClientId = geminiClientId; }
        public String getClaudeClientId() { return claudeClientId; }
        public void setClaudeClientId(String claudeClientId) { this.claudeClientId = claudeClientId; }
    }
}
