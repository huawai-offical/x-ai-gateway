package com.prodigalgal.xaigateway.admin.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public class CodexAuthJsonParser {

    private final ObjectMapper objectMapper;

    public CodexAuthJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedCodexAuthJson parse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("Codex auth.json 不能为空。");
        }
        Map<String, Object> root = readRoot(rawJson);
        List<Map<String, Object>> candidates = candidates(root);
        String accessToken = pickString(candidates, List.of(
                "access_token", "accessToken", "OPENAI_API_KEY", "api_key", "apiKey", "token", "session_key", "sessionKey"
        ));
        if (isBlank(accessToken)) {
            throw new IllegalArgumentException("Codex auth.json 中未找到可用 access token。");
        }
        String refreshToken = pickString(candidates, List.of("refresh_token", "refreshToken"));
        String idToken = pickString(candidates, List.of("id_token", "idToken"));
        List<Map<String, Object>> jwtCandidates = jwtCandidates(accessToken, idToken);
        String accountId = pickString(candidates, List.of(
                "account_id", "accountId", "external_account_id", "externalAccountId"
        ));
        String identitySubject = firstNonBlank(
                pickString(jwtCandidates, List.of("sub", "subject", "user_id", "uid")),
                pickString(candidates, List.of("subject", "user_subject", "userId", "user_id", "uid"))
        );
        String email = normalizeEmail(firstNonBlank(
                pickString(jwtCandidates, List.of("email", "preferred_username")),
                pickString(candidates, List.of("email", "user_email", "userEmail"))
        ));
        String accountName = pickString(candidates, List.of("account_name", "accountName", "name", "display_name", "displayName"));
        String authMode = firstNonBlank(
                pickString(root, List.of("auth_mode", "authMode")),
                pickString(candidates, List.of("auth_mode", "authMode"))
        );
        Instant expiresAt = pickInstant(candidates, List.of(
                "expires_at", "expiresAt", "expiry", "expiry_date", "expiryDate", "token_expires_at", "tokenExpiresAt"
        ));
        Identity identity = resolveIdentity(identitySubject, email, accountId, accessToken);
        Map<String, Object> safeSummary = new LinkedHashMap<>();
        safeSummary.put("source", "codex_auth_json");
        safeSummary.put("authMode", defaultString(authMode, "unknown"));
        safeSummary.put("accountId", defaultString(accountId, "unknown"));
        safeSummary.put("identityKey", identity.key());
        safeSummary.put("identitySource", identity.source());
        safeSummary.put("identityStrength", identity.strength());
        safeSummary.put("hasAccessToken", true);
        safeSummary.put("hasRefreshToken", !isBlank(refreshToken));
        safeSummary.put("hasIdToken", !isBlank(idToken));
        safeSummary.put("accessTokenFingerprint", fingerprint(accessToken));
        if (!isBlank(identitySubject)) {
            safeSummary.put("subjectFingerprint", fingerprint(identitySubject));
        }
        if (!isBlank(email)) {
            safeSummary.put("emailFingerprint", fingerprint(email));
        }
        if (!isBlank(refreshToken)) {
            safeSummary.put("refreshTokenFingerprint", fingerprint(refreshToken));
        }
        if (expiresAt != null) {
            safeSummary.put("tokenExpiresAt", expiresAt.toString());
        }
        return new ParsedCodexAuthJson(
                accessToken.trim(),
                normalizeBlank(refreshToken),
                normalizeBlank(idToken),
                normalizeBlank(accountId),
                normalizeBlank(identitySubject),
                normalizeBlank(email),
                identity.key(),
                identity.source(),
                identity.strength(),
                defaultString(accountName, fallbackAccountName(identity, accountId, accessToken)),
                expiresAt,
                defaultString(authMode, "unknown"),
                safeSummary
        );
    }

    private Map<String, Object> readRoot(String rawJson) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(rawJson, new TypeReference<>() {
            });
            if (parsed == null) {
                throw new IllegalArgumentException("Codex auth.json 根节点必须是 JSON 对象。");
            }
            return new LinkedHashMap<>(parsed);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Codex auth.json 不是合法 JSON。", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> candidates(Map<String, Object> root) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(root);
        for (String key : List.of(
                "auth", "oauth", "session", "tokens", "token", "openai", "codex",
                "openai_oauth", "codex_oauth", "profile", "user", "account", "current_account", "currentAccount"
        )) {
            Object value = root.get(key);
            if (value instanceof Map<?, ?> map) {
                candidates.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        Object accounts = root.get("accounts");
        if (accounts instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    candidates.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
        }
        List<Map<String, Object>> nested = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            for (String key : List.of("profile", "user", "account", "identity")) {
                Object value = candidate.get(key);
                if (value instanceof Map<?, ?> map) {
                    nested.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
        }
        candidates.addAll(nested);
        return candidates;
    }

    private List<Map<String, Object>> jwtCandidates(String accessToken, String idToken) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        Map<String, Object> idTokenClaims = parseJwtPayload(idToken);
        if (idTokenClaims != null) {
            candidates.add(idTokenClaims);
        }
        Map<String, Object> accessTokenClaims = parseJwtPayload(accessToken);
        if (accessTokenClaims != null) {
            candidates.add(accessTokenClaims);
        }
        return candidates;
    }

    private Map<String, Object> parseJwtPayload(String token) {
        if (isBlank(token)) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            String payload = parts[1];
            int padding = (4 - payload.length() % 4) % 4;
            byte[] decoded = Base64.getUrlDecoder().decode(payload + "=".repeat(padding));
            Map<String, Object> parsed = objectMapper.readValue(new String(decoded, StandardCharsets.UTF_8), new TypeReference<>() {
            });
            return parsed == null ? null : new LinkedHashMap<>(parsed);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String pickString(List<Map<String, Object>> candidates, List<String> keys) {
        for (String key : keys) {
            for (Map<String, Object> candidate : candidates) {
                String value = pickString(candidate, List.of(key));
                if (!isBlank(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private String pickString(Map<String, Object> source, List<String> keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text.trim();
            }
            if (value instanceof Number || value instanceof Boolean) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private Instant pickInstant(List<Map<String, Object>> candidates, List<String> keys) {
        for (Map<String, Object> candidate : candidates) {
            for (String key : keys) {
                Instant instant = parseInstant(candidate.get(key));
                if (instant != null) {
                    return instant;
                }
            }
        }
        return null;
    }

    private Instant parseInstant(Object value) {
        if (value instanceof Number number) {
            long epoch = number.longValue();
            return epoch > 10_000_000_000L ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
        }
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        try {
            long epoch = Long.parseLong(trimmed);
            return epoch > 10_000_000_000L ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
        } catch (NumberFormatException ignored) {
            try {
                return Instant.parse(trimmed);
            } catch (DateTimeParseException ignoredInstant) {
                return null;
            }
        }
    }

    private String fingerprint(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hex = HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
            return hex.substring(0, 16).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境缺少 SHA-256。", exception);
        }
    }

    private Identity resolveIdentity(String identitySubject, String email, String accountId, String accessToken) {
        if (!isBlank(identitySubject)) {
            return new Identity("codex:subject:" + fingerprint(identitySubject), "subject", "STRONG");
        }
        if (!isBlank(email)) {
            return new Identity("codex:email:" + fingerprint(email.toLowerCase(Locale.ROOT)), "email", "STRONG");
        }
        if (!isBlank(accountId)) {
            return new Identity("codex:account:" + fingerprint(accountId), "account_id", "STRONG");
        }
        return new Identity("codex:weak-token:" + fingerprint(accessToken), "access_token_fingerprint", "WEAK_TOKEN");
    }

    private String fallbackAccountName(Identity identity, String accountId, String accessToken) {
        if (identity != null && !isBlank(identity.key())) {
            return identity.key().replace(':', '-');
        }
        if (!isBlank(accountId)) {
            return "codex-" + accountId.replaceAll("[^A-Za-z0-9_.-]", "-");
        }
        return "codex-" + fingerprint(accessToken);
    }

    private String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalizeBlank(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String normalizeEmail(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("@") ? normalized : null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ParsedCodexAuthJson(
            String accessToken,
            String refreshToken,
            String idToken,
            String accountId,
            String identitySubject,
            String email,
            String identityKey,
            String identitySource,
            String identityStrength,
            String accountName,
            Instant tokenExpiresAt,
            String authMode,
            Map<String, Object> safeSummary) {
    }

    private record Identity(String key, String source, String strength) {
    }
}
