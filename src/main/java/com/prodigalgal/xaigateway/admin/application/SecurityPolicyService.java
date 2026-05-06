package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.SecurityScanResponse;
import com.prodigalgal.xaigateway.admin.api.SystemSettingsResponse;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class SecurityPolicyService {

    private final SystemSettingsAdminService systemSettingsAdminService;

    public SecurityPolicyService(SystemSettingsAdminService systemSettingsAdminService) {
        this.systemSettingsAdminService = systemSettingsAdminService;
    }

    public void assertUrlAllowed(String url) {
        SecurityScanResponse response = scanUrl(url);
        if (!response.allowed()) {
            throw new IllegalArgumentException(response.reason());
        }
    }

    public SecurityScanResponse scanUrl(String url) {
        SystemSettingsResponse.SecuritySettingsResponse security = systemSettingsAdminService.get().security();
        if (!security.ssrfProtectionEnabled() || url == null || url.isBlank()) {
            return new SecurityScanResponse(true, "allowed", List.of(), null);
        }
        if (url.contains("{") || url.contains("}")) {
            return new SecurityScanResponse(true, "templated URL skipped", List.of(), null);
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException exception) {
            return new SecurityScanResponse(false, "URL 格式不合法。", List.of(), null);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return new SecurityScanResponse(false, "URL 缺少 host。", List.of(), null);
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!matchesAllowedHosts(normalizedHost, security.allowedHosts())) {
            return new SecurityScanResponse(false, "URL host 不在安全允许列表。", List.of(), normalizedHost);
        }
        if (!security.allowPrivateNetwork() && isPrivateHost(normalizedHost)) {
            return new SecurityScanResponse(false, "SSRF 防护阻断私网或本机地址。", List.of(), normalizedHost);
        }
        return new SecurityScanResponse(true, "allowed", List.of(), normalizedHost);
    }

    public SecurityScanResponse scanText(String text) {
        SystemSettingsResponse.SecuritySettingsResponse security = systemSettingsAdminService.get().security();
        if (text == null || text.isBlank() || security.sensitiveWords().isEmpty()) {
            return new SecurityScanResponse(true, "allowed", List.of(), null);
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
        List<String> matched = security.sensitiveWords().stream()
                .filter(word -> word != null && !word.isBlank())
                .map(String::trim)
                .filter(word -> lowerText.contains(word.toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();
        if (matched.isEmpty()) {
            return new SecurityScanResponse(true, "allowed", List.of(), null);
        }
        return new SecurityScanResponse(false, "命中敏感词策略。", matched, null);
    }

    private boolean matchesAllowedHosts(String host, List<String> allowedHosts) {
        List<String> normalized = allowedHosts == null ? List.of() : allowedHosts.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .toList();
        if (normalized.isEmpty()) {
            return true;
        }
        for (String pattern : normalized) {
            if (pattern.startsWith("*.")) {
                String suffix = pattern.substring(1);
                if (host.endsWith(suffix)) {
                    return true;
                }
            } else if (host.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPrivateHost(String host) {
        if ("localhost".equals(host) || host.endsWith(".localhost") || host.endsWith(".local")) {
            return true;
        }
        String unbracketed = host.replace("[", "").replace("]", "");
        if ("::1".equals(unbracketed) || unbracketed.startsWith("fc") || unbracketed.startsWith("fd") || unbracketed.startsWith("fe80")) {
            return true;
        }
        String[] parts = unbracketed.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            return first == 10
                    || first == 127
                    || first == 0
                    || first == 169 && second == 254
                    || first == 172 && second >= 16 && second <= 31
                    || first == 192 && second == 168
                    || first == 100 && second >= 64 && second <= 127;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
