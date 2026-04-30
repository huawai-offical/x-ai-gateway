package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.*;
import com.prodigalgal.xaigateway.infra.persistence.entity.DashboardExternalAppEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DashboardExternalAppRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class DashboardExternalAppService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final DashboardExternalAppRepository repository;
    private final CredentialCryptoService credentialCryptoService;
    private final ObjectMapper objectMapper;
    private final OpsAuditService opsAuditService;

    public DashboardExternalAppService(
            DashboardExternalAppRepository repository,
            CredentialCryptoService credentialCryptoService,
            ObjectMapper objectMapper,
            OpsAuditService opsAuditService) {
        this.repository = repository;
        this.credentialCryptoService = credentialCryptoService;
        this.objectMapper = objectMapper;
        this.opsAuditService = opsAuditService;
    }

    @Transactional(readOnly = true)
    public List<DashboardExternalAppResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DashboardExternalAppResponse> navApps() {
        return repository.findAllByEnabledTrueAndNavEnabledTrueOrderByAppNameAsc().stream().map(this::toResponse).toList();
    }

    public DashboardExternalAppResponse create(DashboardExternalAppRequest request) {
        String slug = normalizeSlug(request.slug());
        if (repository.existsBySlug(slug)) {
            throw new IllegalArgumentException("扩展应用 slug 已存在。");
        }
        DashboardExternalAppEntity entity = new DashboardExternalAppEntity();
        apply(entity, request, true);
        DashboardExternalAppEntity saved = repository.save(entity);
        opsAuditService.record("EXTERNAL_APP", "CREATE", "dashboard_external_app", String.valueOf(saved.getId()), "{\"slug\":\"" + saved.getSlug() + "\"}");
        return toResponse(saved);
    }

    public DashboardExternalAppResponse update(Long id, DashboardExternalAppRequest request) {
        DashboardExternalAppEntity entity = getRequired(id);
        apply(entity, request, false);
        DashboardExternalAppEntity saved = repository.save(entity);
        opsAuditService.record("EXTERNAL_APP", "UPDATE", "dashboard_external_app", String.valueOf(saved.getId()), "{\"slug\":\"" + saved.getSlug() + "\"}");
        return toResponse(saved);
    }

    public void delete(Long id) {
        DashboardExternalAppEntity entity = getRequired(id);
        repository.delete(entity);
        opsAuditService.record("EXTERNAL_APP", "DELETE", "dashboard_external_app", String.valueOf(id), "{\"slug\":\"" + entity.getSlug() + "\"}");
    }

    @Transactional(readOnly = true)
    public ExternalAppSignedContextResponse preview(Long id, String origin, String actor, long ttlSeconds) {
        DashboardExternalAppEntity entity = getRequired(id);
        ensureActive(entity);
        String normalizedOrigin = resolveOrigin(origin, entity);
        ensureOrigin(entity, normalizedOrigin);
        return buildSignedContext(entity, normalizedOrigin, actor, ttlSeconds);
    }

    @Transactional(readOnly = true)
    public ExternalAppRuntimeResponse runtime(String slug, String origin, String actor, long ttlSeconds) {
        DashboardExternalAppEntity entity = repository.findBySlug(normalizeSlug(slug))
                .orElseThrow(() -> new IllegalArgumentException("未找到指定扩展应用。"));
        DashboardExternalAppResponse app = toResponse(entity);
        String actualOrigin = extractOrigin(entity.getIframeUrl());
        if (!entity.isEnabled()) {
            return runtimeBlocked(app, "APP_DISABLED", "应用已停用，不能挂载 iframe。", actualOrigin);
        }
        if (!entity.isNavEnabled()) {
            return runtimeBlocked(app, "NAV_DISABLED", "应用未启用导航展示，运行页暂不开放。", actualOrigin);
        }
        if (actualOrigin == null) {
            return runtimeBlocked(app, "INVALID_IFRAME_URL", "iframe URL 无法解析为有效来源。", null);
        }
        if (!entity.getAllowedOrigin().equals(actualOrigin)) {
            return runtimeBlocked(
                    app,
                    "ORIGIN_MISMATCH",
                    "iframe URL 来源 " + actualOrigin + " 与允许来源 " + entity.getAllowedOrigin() + " 不匹配。",
                    actualOrigin
            );
        }
        String normalizedOrigin = resolveOrigin(origin, entity);
        ensureOrigin(entity, normalizedOrigin);
        return new ExternalAppRuntimeResponse(
                app,
                buildSignedContext(entity, normalizedOrigin, actor, ttlSeconds),
                true,
                "READY",
                "扩展应用可以安全挂载。",
                actualOrigin
        );
    }

    private ExternalAppRuntimeResponse runtimeBlocked(
            DashboardExternalAppResponse app,
            String status,
            String message,
            String actualOrigin) {
        return new ExternalAppRuntimeResponse(app, null, false, status, message, actualOrigin);
    }

    private ExternalAppSignedContextResponse buildSignedContext(
            DashboardExternalAppEntity entity,
            String normalizedOrigin,
            String actor,
            long ttlSeconds) {
        Instant expiresAt = Instant.now().plusSeconds(Math.max(60, ttlSeconds <= 0 ? 300 : ttlSeconds));
        String payload = writeJson(Map.of(
                "slug", entity.getSlug(),
                "actor", actor == null || actor.isBlank() ? "console" : actor,
                "origin", normalizedOrigin,
                "exp", expiresAt.getEpochSecond()
        ));
        String context = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = sign(entity, context);
        String separator = entity.getIframeUrl().contains("?") ? "&" : "?";
        String launchUrl = entity.getIframeUrl() + separator + "x_context=" + context + "&x_signature=" + signature;
        return new ExternalAppSignedContextResponse(entity.getSlug(), normalizedOrigin, context, signature, launchUrl, expiresAt);
    }

    @Transactional(readOnly = true)
    public ExternalAppVerifyResponse verify(String slug, ExternalAppVerifyRequest request) {
        DashboardExternalAppEntity entity = repository.findBySlug(normalizeSlug(slug))
                .orElseThrow(() -> new IllegalArgumentException("未找到指定扩展应用。"));
        try {
            ensureActive(entity);
            ensureOrigin(entity, request.origin());
            if (!constantTimeEquals(sign(entity, request.context()), request.signature())) {
                return new ExternalAppVerifyResponse(false, "SIGNATURE_MISMATCH", entity.getSlug());
            }
            JsonNode payload = objectMapper.readTree(new String(Base64.getUrlDecoder().decode(request.context()), StandardCharsets.UTF_8));
            long expiresAt = payload.path("exp").asLong(0L);
            if (expiresAt <= Instant.now().getEpochSecond()) {
                return new ExternalAppVerifyResponse(false, "EXPIRED", entity.getSlug());
            }
            if (!entity.getSlug().equals(payload.path("slug").asText())) {
                return new ExternalAppVerifyResponse(false, "SLUG_MISMATCH", entity.getSlug());
            }
            return new ExternalAppVerifyResponse(true, "OK", entity.getSlug());
        } catch (IllegalArgumentException exception) {
            return new ExternalAppVerifyResponse(false, exception.getMessage(), entity.getSlug());
        } catch (Exception exception) {
            return new ExternalAppVerifyResponse(false, "INVALID_CONTEXT", entity.getSlug());
        }
    }

    private void apply(DashboardExternalAppEntity entity, DashboardExternalAppRequest request, boolean create) {
        entity.setAppName(required(request.appName(), "扩展应用名称不能为空。"));
        entity.setSlug(normalizeSlug(request.slug()));
        entity.setIframeUrl(required(request.iframeUrl(), "iframe URL 不能为空。"));
        entity.setAllowedOrigin(normalizeOrigin(required(request.allowedOrigin(), "允许来源不能为空。")));
        entity.setSandboxPermissions(defaultString(request.sandboxPermissions(), "allow-scripts allow-forms allow-popups"));
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setNavEnabled(request.navEnabled() == null || request.navEnabled());
        entity.setDescription(blankToNull(request.description()));
        if (StringUtils.hasText(request.signingSecret())) {
            String secret = request.signingSecret().trim();
            entity.setSigningSecretCiphertext(credentialCryptoService.encrypt(secret));
            entity.setSigningSecretFingerprint(credentialCryptoService.fingerprint(secret));
        } else if (create) {
            String fallback = entity.getSlug() + ":" + Instant.now().toEpochMilli();
            entity.setSigningSecretCiphertext(credentialCryptoService.encrypt(fallback));
            entity.setSigningSecretFingerprint(credentialCryptoService.fingerprint(fallback));
        }
    }

    private DashboardExternalAppEntity getRequired(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到指定扩展应用。"));
    }

    private void ensureActive(DashboardExternalAppEntity entity) {
        if (!entity.isEnabled()) {
            throw new IllegalArgumentException("APP_DISABLED");
        }
    }

    private String resolveOrigin(String origin, DashboardExternalAppEntity entity) {
        return StringUtils.hasText(origin) ? normalizeOrigin(origin) : entity.getAllowedOrigin();
    }

    private void ensureOrigin(DashboardExternalAppEntity entity, String origin) {
        if (!entity.getAllowedOrigin().equals(normalizeOrigin(origin))) {
            throw new IllegalArgumentException("ORIGIN_MISMATCH");
        }
    }

    private String extractOrigin(String iframeUrl) {
        try {
            URI uri = URI.create(required(iframeUrl, "iframe URL 不能为空。"));
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getAuthority())) {
                return null;
            }
            return normalizeOrigin(uri.getScheme() + "://" + uri.getAuthority());
        } catch (Exception exception) {
            return null;
        }
    }

    private String sign(DashboardExternalAppEntity entity, String context) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(credentialCryptoService.decrypt(entity.getSigningSecretCiphertext()).getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(context.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("扩展应用签名失败。", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeSlug(String slug) {
        String value = required(slug, "slug 不能为空。").toLowerCase(Locale.ROOT).trim();
        if (!value.matches("[a-z0-9][a-z0-9-]{1,94}")) {
            throw new IllegalArgumentException("slug 只能包含小写字母、数字和短横线。");
        }
        return value;
    }

    private String normalizeOrigin(String origin) {
        String value = required(origin, "origin 不能为空。").replaceAll("/+$", "");
        if (!value.startsWith("https://") && !value.startsWith("http://localhost") && !value.startsWith("http://127.0.0.1")) {
            throw new IllegalArgumentException("ORIGIN_NOT_ALLOWED");
        }
        return value;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("序列化 signed context 失败。", exception);
        }
    }

    private DashboardExternalAppResponse toResponse(DashboardExternalAppEntity entity) {
        return new DashboardExternalAppResponse(
                entity.getId(),
                entity.getAppName(),
                entity.getSlug(),
                entity.getIframeUrl(),
                entity.getAllowedOrigin(),
                entity.getSandboxPermissions(),
                entity.getSigningSecretFingerprint(),
                entity.isEnabled(),
                entity.isNavEnabled(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
