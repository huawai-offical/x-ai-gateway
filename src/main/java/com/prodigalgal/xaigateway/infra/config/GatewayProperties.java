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
    private final AdminConsole adminConsole = new AdminConsole();
    private final Observability observability = new Observability();

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

    public AdminConsole getAdminConsole() {
        return adminConsole;
    }

    public Observability getObservability() {
        return observability;
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

    public static class AdminConsole {
        private boolean enabled = true;
        private String username = "admin";
        private String password;
        private Duration sessionTtl = Duration.ofHours(8);
        private Duration challengeTtl = Duration.ofMinutes(5);
        private int powDifficulty = 4;
        private int mathMin = 1;
        private int mathMax = 12;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public Duration getSessionTtl() {
            return sessionTtl;
        }

        public void setSessionTtl(Duration sessionTtl) {
            this.sessionTtl = sessionTtl;
        }

        public Duration getChallengeTtl() {
            return challengeTtl;
        }

        public void setChallengeTtl(Duration challengeTtl) {
            this.challengeTtl = challengeTtl;
        }

        public int getPowDifficulty() {
            return powDifficulty;
        }

        public void setPowDifficulty(int powDifficulty) {
            this.powDifficulty = powDifficulty;
        }

        public int getMathMin() {
            return mathMin;
        }

        public void setMathMin(int mathMin) {
            this.mathMin = mathMin;
        }

        public int getMathMax() {
            return mathMax;
        }

        public void setMathMax(int mathMax) {
            this.mathMax = mathMax;
        }
    }

    public static class Observability {

        private final Async async = new Async();

        public Async getAsync() {
            return async;
        }

        public static class Async {
            private boolean enabled = true;
            private String queueKey = "xag:observability:hot-path";
            private Duration flushInterval = Duration.ofSeconds(1);
            private int batchSize = 200;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getQueueKey() {
                return queueKey;
            }

            public void setQueueKey(String queueKey) {
                this.queueKey = queueKey;
            }

            public Duration getFlushInterval() {
                return flushInterval;
            }

            public void setFlushInterval(Duration flushInterval) {
                this.flushInterval = flushInterval;
            }

            public int getBatchSize() {
                return batchSize;
            }

            public void setBatchSize(int batchSize) {
                this.batchSize = batchSize;
            }
        }
    }
}
