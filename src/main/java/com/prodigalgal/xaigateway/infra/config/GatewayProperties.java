package com.prodigalgal.xaigateway.infra.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private final Routing routing = new Routing();
    private final Cache cache = new Cache();
    private final Security security = new Security();
    private final Storage storage = new Storage();

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

    public static class Routing {

        private boolean interopPlanEnabled = true;
        private boolean routeDecisionLoggingEnabled = true;

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
    }

    public static class Cache {

        private boolean enabled = true;
        private boolean stickyByDistributedKey = true;
        private boolean prefixAffinityEnabled = true;
        private boolean fingerprintAffinityEnabled = true;
        private Duration affinityTtl = Duration.ofMinutes(30);
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
}
