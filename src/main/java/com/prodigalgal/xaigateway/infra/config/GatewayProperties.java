package com.prodigalgal.xaigateway.infra.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private final Routing routing = new Routing();
    private final Cache cache = new Cache();
    private final Security security = new Security();
    private final Storage storage = new Storage();
    private final Web web = new Web();
    private final Oauth oauth = new Oauth();
    private final Cli cli = new Cli();
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

    public Cli getCli() {
        return cli;
    }

    public AdminConsole getAdminConsole() {
        return adminConsole;
    }

    public Observability getObservability() {
        return observability;
    }

    public static class Routing {

        private final RuntimeStore runtimeStore = new RuntimeStore();
        private boolean interopPlanEnabled = true;
        private boolean routeDecisionLoggingEnabled = true;
        private int maxFallbackAttempts = 3;

        public RuntimeStore getRuntimeStore() {
            return runtimeStore;
        }

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

        public static class RuntimeStore {

            private String type = "memory";
            private String keyPrefix;
            private boolean fallbackToMemory = true;

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public String getKeyPrefix() {
                return keyPrefix;
            }

            public void setKeyPrefix(String keyPrefix) {
                this.keyPrefix = keyPrefix;
            }

            public boolean isFallbackToMemory() {
                return fallbackToMemory;
            }

            public void setFallbackToMemory(boolean fallbackToMemory) {
                this.fallbackToMemory = fallbackToMemory;
            }
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
        private String googleSocialClientId;
        private String googleSocialClientSecret;
        private String googleSocialTokenEndpoint = "https://oauth2.googleapis.com/token";
        private String googleSocialUserInfoEndpoint = "https://openidconnect.googleapis.com/v1/userinfo";
        private String googleSocialJwksUri = "https://www.googleapis.com/oauth2/v3/certs";
        private String githubSocialClientId;
        private String githubSocialClientSecret;
        private String githubSocialTokenEndpoint = "https://github.com/login/oauth/access_token";
        private String githubSocialUserEndpoint = "https://api.github.com/user";
        private String githubSocialEmailsEndpoint = "https://api.github.com/user/emails";
        private Duration socialJwksCacheTtl = Duration.ofMinutes(30);
        private String qqSocialClientId;
        private String qqSocialClientSecret;
        private String qqSocialTokenEndpoint = "https://graph.qq.com/oauth2.0/token";
        private String qqSocialOpenIdEndpoint = "https://graph.qq.com/oauth2.0/me";
        private String qqSocialUserInfoEndpoint = "https://graph.qq.com/user/get_user_info";
        private String wechatSocialClientId;
        private String wechatSocialClientSecret;
        private String wechatSocialTokenEndpoint = "https://api.weixin.qq.com/sns/oauth2/access_token";
        private String wechatSocialUserInfoEndpoint = "https://api.weixin.qq.com/sns/userinfo";
        private String metaSocialClientId;
        private String metaSocialClientSecret;
        private String metaSocialTokenEndpoint = "https://graph.facebook.com/v20.0/oauth/access_token";
        private String metaSocialUserInfoEndpoint = "https://graph.facebook.com/v20.0/me?fields=id,name,email";
        private String xSocialClientId;
        private String xSocialClientSecret;
        private String xSocialTokenEndpoint = "https://api.x.com/2/oauth2/token";
        private String xSocialUserInfoEndpoint = "https://api.x.com/2/users/me?user.fields=id,name,username,verified";

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
        public String getGoogleSocialClientId() { return googleSocialClientId; }
        public void setGoogleSocialClientId(String googleSocialClientId) { this.googleSocialClientId = googleSocialClientId; }
        public String getGoogleSocialClientSecret() { return googleSocialClientSecret; }
        public void setGoogleSocialClientSecret(String googleSocialClientSecret) { this.googleSocialClientSecret = googleSocialClientSecret; }
        public String getGoogleSocialTokenEndpoint() { return googleSocialTokenEndpoint; }
        public void setGoogleSocialTokenEndpoint(String googleSocialTokenEndpoint) { this.googleSocialTokenEndpoint = googleSocialTokenEndpoint; }
        public String getGoogleSocialUserInfoEndpoint() { return googleSocialUserInfoEndpoint; }
        public void setGoogleSocialUserInfoEndpoint(String googleSocialUserInfoEndpoint) { this.googleSocialUserInfoEndpoint = googleSocialUserInfoEndpoint; }
        public String getGoogleSocialJwksUri() { return googleSocialJwksUri; }
        public void setGoogleSocialJwksUri(String googleSocialJwksUri) { this.googleSocialJwksUri = googleSocialJwksUri; }
        public String getGithubSocialClientId() { return githubSocialClientId; }
        public void setGithubSocialClientId(String githubSocialClientId) { this.githubSocialClientId = githubSocialClientId; }
        public String getGithubSocialClientSecret() { return githubSocialClientSecret; }
        public void setGithubSocialClientSecret(String githubSocialClientSecret) { this.githubSocialClientSecret = githubSocialClientSecret; }
        public String getGithubSocialTokenEndpoint() { return githubSocialTokenEndpoint; }
        public void setGithubSocialTokenEndpoint(String githubSocialTokenEndpoint) { this.githubSocialTokenEndpoint = githubSocialTokenEndpoint; }
        public String getGithubSocialUserEndpoint() { return githubSocialUserEndpoint; }
        public void setGithubSocialUserEndpoint(String githubSocialUserEndpoint) { this.githubSocialUserEndpoint = githubSocialUserEndpoint; }
        public String getGithubSocialEmailsEndpoint() { return githubSocialEmailsEndpoint; }
        public void setGithubSocialEmailsEndpoint(String githubSocialEmailsEndpoint) { this.githubSocialEmailsEndpoint = githubSocialEmailsEndpoint; }
        public Duration getSocialJwksCacheTtl() { return socialJwksCacheTtl; }
        public void setSocialJwksCacheTtl(Duration socialJwksCacheTtl) { this.socialJwksCacheTtl = socialJwksCacheTtl; }
        public String getQqSocialClientId() { return qqSocialClientId; }
        public void setQqSocialClientId(String qqSocialClientId) { this.qqSocialClientId = qqSocialClientId; }
        public String getQqSocialClientSecret() { return qqSocialClientSecret; }
        public void setQqSocialClientSecret(String qqSocialClientSecret) { this.qqSocialClientSecret = qqSocialClientSecret; }
        public String getQqSocialTokenEndpoint() { return qqSocialTokenEndpoint; }
        public void setQqSocialTokenEndpoint(String qqSocialTokenEndpoint) { this.qqSocialTokenEndpoint = qqSocialTokenEndpoint; }
        public String getQqSocialOpenIdEndpoint() { return qqSocialOpenIdEndpoint; }
        public void setQqSocialOpenIdEndpoint(String qqSocialOpenIdEndpoint) { this.qqSocialOpenIdEndpoint = qqSocialOpenIdEndpoint; }
        public String getQqSocialUserInfoEndpoint() { return qqSocialUserInfoEndpoint; }
        public void setQqSocialUserInfoEndpoint(String qqSocialUserInfoEndpoint) { this.qqSocialUserInfoEndpoint = qqSocialUserInfoEndpoint; }
        public String getWechatSocialClientId() { return wechatSocialClientId; }
        public void setWechatSocialClientId(String wechatSocialClientId) { this.wechatSocialClientId = wechatSocialClientId; }
        public String getWechatSocialClientSecret() { return wechatSocialClientSecret; }
        public void setWechatSocialClientSecret(String wechatSocialClientSecret) { this.wechatSocialClientSecret = wechatSocialClientSecret; }
        public String getWechatSocialTokenEndpoint() { return wechatSocialTokenEndpoint; }
        public void setWechatSocialTokenEndpoint(String wechatSocialTokenEndpoint) { this.wechatSocialTokenEndpoint = wechatSocialTokenEndpoint; }
        public String getWechatSocialUserInfoEndpoint() { return wechatSocialUserInfoEndpoint; }
        public void setWechatSocialUserInfoEndpoint(String wechatSocialUserInfoEndpoint) { this.wechatSocialUserInfoEndpoint = wechatSocialUserInfoEndpoint; }
        public String getMetaSocialClientId() { return metaSocialClientId; }
        public void setMetaSocialClientId(String metaSocialClientId) { this.metaSocialClientId = metaSocialClientId; }
        public String getMetaSocialClientSecret() { return metaSocialClientSecret; }
        public void setMetaSocialClientSecret(String metaSocialClientSecret) { this.metaSocialClientSecret = metaSocialClientSecret; }
        public String getMetaSocialTokenEndpoint() { return metaSocialTokenEndpoint; }
        public void setMetaSocialTokenEndpoint(String metaSocialTokenEndpoint) { this.metaSocialTokenEndpoint = metaSocialTokenEndpoint; }
        public String getMetaSocialUserInfoEndpoint() { return metaSocialUserInfoEndpoint; }
        public void setMetaSocialUserInfoEndpoint(String metaSocialUserInfoEndpoint) { this.metaSocialUserInfoEndpoint = metaSocialUserInfoEndpoint; }
        public String getXSocialClientId() { return xSocialClientId; }
        public void setXSocialClientId(String xSocialClientId) { this.xSocialClientId = xSocialClientId; }
        public String getXSocialClientSecret() { return xSocialClientSecret; }
        public void setXSocialClientSecret(String xSocialClientSecret) { this.xSocialClientSecret = xSocialClientSecret; }
        public String getXSocialTokenEndpoint() { return xSocialTokenEndpoint; }
        public void setXSocialTokenEndpoint(String xSocialTokenEndpoint) { this.xSocialTokenEndpoint = xSocialTokenEndpoint; }
        public String getXSocialUserInfoEndpoint() { return xSocialUserInfoEndpoint; }
        public void setXSocialUserInfoEndpoint(String xSocialUserInfoEndpoint) { this.xSocialUserInfoEndpoint = xSocialUserInfoEndpoint; }
    }

    public static class Cli {
        private final RequestFilter requestFilter = new RequestFilter();

        public RequestFilter getRequestFilter() {
            return requestFilter;
        }

        public static class RequestFilter {
            private boolean enabled;
            private List<Rule> rules = new ArrayList<>();

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public List<Rule> getRules() {
                return rules;
            }

            public void setRules(List<Rule> rules) {
                this.rules = rules == null ? new ArrayList<>() : rules;
            }
        }

        public static class Rule {
            private String id;
            private String action;
            private List<String> clientFamilies = new ArrayList<>();
            private String role = "all";
            private String contains;
            private String replacement;

            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            public String getAction() {
                return action;
            }

            public void setAction(String action) {
                this.action = action;
            }

            public List<String> getClientFamilies() {
                return clientFamilies;
            }

            public void setClientFamilies(List<String> clientFamilies) {
                this.clientFamilies = clientFamilies == null ? new ArrayList<>() : clientFamilies;
            }

            public String getRole() {
                return role;
            }

            public void setRole(String role) {
                this.role = role;
            }

            public String getContains() {
                return contains;
            }

            public void setContains(String contains) {
                this.contains = contains;
            }

            public String getReplacement() {
                return replacement;
            }

            public void setReplacement(String replacement) {
                this.replacement = replacement;
            }
        }
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
