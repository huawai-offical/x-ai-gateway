package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.CredentialConnectivityRequest;
import com.prodigalgal.xaigateway.admin.api.CredentialConnectivityResponse;
import com.prodigalgal.xaigateway.admin.api.CredentialModelRefreshResponse;
import com.prodigalgal.xaigateway.admin.api.CredentialRequest;
import com.prodigalgal.xaigateway.admin.api.CredentialResponse;
import com.prodigalgal.xaigateway.admin.api.OpenAiDirectResourceSmokeItemResponse;
import com.prodigalgal.xaigateway.admin.api.OpenAiDirectResourceSmokeRequest;
import com.prodigalgal.xaigateway.admin.api.OpenAiDirectResourceSmokeResponse;
import com.prodigalgal.xaigateway.admin.api.OpenAiDirectSmokeCertificationResponse;
import com.prodigalgal.xaigateway.admin.api.OpenAiDirectSmokeRequest;
import com.prodigalgal.xaigateway.admin.api.OpenAiDirectSmokeResponse;
import com.prodigalgal.xaigateway.gateway.core.catalog.CredentialModelDiscoveryService;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountPoolEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountPoolRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CredentialAdminService {

    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final CredentialModelDiscoveryService credentialModelDiscoveryService;
    private final ProviderSiteRegistryService providerSiteRegistryService;
    private final UpstreamAccountPoolRepository upstreamAccountPoolRepository;
    private final ObjectMapper objectMapper;
    private final SupportedModelCatalogService supportedModelCatalogService;
    private final OpenAiDirectSmokeHttpClient openAiDirectSmokeHttpClient;
    private final OpenAiDirectResourceSmokeHttpClient openAiDirectResourceSmokeHttpClient;
    private final OpenAiDirectSmokeCertificationService openAiDirectSmokeCertificationService;

    public CredentialAdminService(
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            CredentialModelDiscoveryService credentialModelDiscoveryService,
            ProviderSiteRegistryService providerSiteRegistryService,
            UpstreamAccountPoolRepository upstreamAccountPoolRepository,
            ObjectMapper objectMapper,
            SupportedModelCatalogService supportedModelCatalogService) {
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.credentialModelDiscoveryService = credentialModelDiscoveryService;
        this.providerSiteRegistryService = providerSiteRegistryService;
        this.upstreamAccountPoolRepository = upstreamAccountPoolRepository;
        this.objectMapper = objectMapper;
        this.supportedModelCatalogService = supportedModelCatalogService;
        this.openAiDirectSmokeHttpClient = new OpenAiDirectSmokeHttpClient(objectMapper);
        this.openAiDirectResourceSmokeHttpClient = new OpenAiDirectResourceSmokeHttpClient(objectMapper);
        this.openAiDirectSmokeCertificationService = new OpenAiDirectSmokeCertificationService();
    }

    @Transactional(readOnly = true)
    public List<CredentialResponse> list() {
        List<UpstreamCredentialEntity> credentials = upstreamCredentialRepository.findAllByDeletedFalseOrderByCreatedAtDesc();
        Map<Long, String> poolNameMap = resolvePoolNameMap(credentials);
        return credentials.stream()
                .sorted(Comparator.comparing(UpstreamCredentialEntity::getCreatedAt).reversed())
                .map(entity -> toResponse(entity, poolNameMap.get(entity.getPoolId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CredentialResponse> listByPool(Long poolId) {
        List<UpstreamCredentialEntity> credentials = upstreamCredentialRepository.findAllByPoolIdAndDeletedFalseOrderByCreatedAtDesc(poolId);
        Map<Long, String> poolNameMap = resolvePoolNameMap(credentials);
        return credentials.stream()
                .map(entity -> toResponse(entity, poolNameMap.get(entity.getPoolId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CredentialResponse get(Long id) {
        UpstreamCredentialEntity entity = getRequired(id);
        String poolName = entity.getPoolId() == null
                ? null
                : upstreamAccountPoolRepository.findById(entity.getPoolId()).map(UpstreamAccountPoolEntity::getPoolName).orElse(null);
        return toResponse(entity, poolName);
    }

    public CredentialResponse create(CredentialRequest request) {
        String secret = requireSecret(request.resolvedSecret());
        String fingerprint = credentialCryptoService.fingerprint(secret);
        if (upstreamCredentialRepository.findByApiKeyFingerprintAndDeletedFalse(fingerprint).isPresent()) {
            throw new IllegalArgumentException("已存在相同的上游凭证密钥。");
        }

        UpstreamCredentialEntity entity = new UpstreamCredentialEntity();
        apply(entity, request);
        return toResponse(upstreamCredentialRepository.save(entity));
    }

    public CredentialResponse update(Long id, CredentialRequest request) {
        UpstreamCredentialEntity entity = getRequired(id);
        apply(entity, request);
        return toResponse(upstreamCredentialRepository.save(entity));
    }

    public CredentialResponse toggle(Long id, boolean active) {
        UpstreamCredentialEntity entity = getRequired(id);
        entity.setActive(active);
        return toResponse(upstreamCredentialRepository.save(entity));
    }

    public void delete(Long id) {
        UpstreamCredentialEntity entity = getRequired(id);
        entity.setDeleted(true);
        entity.setActive(false);
        upstreamCredentialRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public CredentialConnectivityResponse testConnectivity(CredentialConnectivityRequest request) {
        CredentialModelDiscoveryService.CredentialConnectivityProbe probe =
                credentialModelDiscoveryService.probe(
                        request.providerType(),
                        request.baseUrl().trim(),
                        request.resolvedAuthKind(),
                        requireSecret(request.resolvedSecret()),
                        request.resolvedCredentialMetadata()
                );
        List<String> sampleModels = probe.models().stream()
                .map(model -> model.modelName())
                .limit(10)
                .toList();
        return new CredentialConnectivityResponse(
                probe.providerType(),
                probe.baseUrl(),
                true,
                probe.latencyMs(),
                probe.models().size(),
                sampleModels,
                "联通性测试成功。"
        );
    }

    public OpenAiDirectSmokeResponse openAiDirectSmoke(Long credentialId, OpenAiDirectSmokeRequest request) {
        UpstreamCredentialEntity entity = getRequired(credentialId);
        Instant now = Instant.now();
        boolean dryRun = request == null || request.dryRun() == null || request.dryRun();
        String requestedBaseUrl = firstNonBlank(
                request == null ? null : request.baseUrl(),
                entity.getBaseUrl()
        );
        String organization = normalizeBlank(request == null ? null : request.organization());
        String project = normalizeBlank(request == null ? null : request.project());
        Map<String, Object> requestPreview = openAiDirectSmokeHttpClient.requestPreview(
                requestedBaseUrl,
                organization,
                project
        );
        String path = text(requestPreview.get("path"));
        String baseUrl = text(requestPreview.get("baseUrl"));
        String routeBlockReason = openAiDirectSmokeRouteBlockReason(entity, now);
        boolean routeEligible = routeBlockReason == null;
        String status = routeEligible ? (dryRun ? "DRY_RUN_READY" : "LIVE_SMOKE_PENDING") : "ROUTE_BLOCKED";
        String message = routeEligible
                ? "OpenAI Direct credential smoke dry-run 已具备执行前置条件。"
                : "OpenAI Direct credential 当前不满足 smoke 前置条件。";
        OpenAiDirectSmokeHttpClient.OpenAiDirectSmokeResult liveResult = null;

        if (routeEligible && !dryRun) {
            try {
                liveResult = openAiDirectSmokeHttpClient.execute(
                        credentialCryptoService.decrypt(entity.getApiKeyCiphertext()),
                        requestedBaseUrl,
                        request == null ? null : request.timeoutSeconds(),
                        organization,
                        project
                );
                path = liveResult.path();
                baseUrl = liveResult.baseUrl();
                status = liveResult.success() ? "LIVE_SMOKE_OK" : "LIVE_SMOKE_FAILED";
                message = liveResult.success()
                        ? "OpenAI Direct credential 权限探测成功。"
                        : "OpenAI Direct credential 权限探测失败，可按脱敏 failureType 排查。";
            } catch (RuntimeException exception) {
                liveResult = new OpenAiDirectSmokeHttpClient.OpenAiDirectSmokeResult(
                        false,
                        null,
                        null,
                        0L,
                        baseUrl,
                        path,
                        "CREDENTIAL_DECRYPT_FAILED",
                        truncate(exception.getMessage(), 240),
                        null,
                        List.of()
                );
                status = "LIVE_SMOKE_FAILED";
                message = "OpenAI Direct credential 无法解密，真实 smoke 未发起。";
            }
        }

        String classification = openAiDirectSmokeClassification(routeEligible, dryRun, routeBlockReason, liveResult);
        String skippedReason = openAiDirectSmokeSkippedReason(classification, routeEligible, dryRun, routeBlockReason, liveResult);
        message = openAiDirectSmokeMessage(classification, routeEligible, dryRun, message);
        if (!dryRun) {
            applyOpenAiDirectSmokeResult(entity, liveResult, classification, now);
            upstreamCredentialRepository.save(entity);
        }
        return new OpenAiDirectSmokeResponse(
                entity.getId(),
                status,
                classification,
                skippedReason,
                "GET",
                path,
                baseUrl,
                entity.getProviderType(),
                dryRun,
                routeEligible,
                routeBlockReason,
                entity.getApiKeyFingerprint(),
                liveResult == null ? null : liveResult.httpStatus(),
                liveResult == null ? null : liveResult.upstreamRequestId(),
                liveResult == null ? null : liveResult.durationMs(),
                liveResult == null ? null : liveResult.failureType(),
                liveResult == null ? null : liveResult.failureMessage(),
                liveResult == null ? null : liveResult.modelsCount(),
                liveResult == null ? List.of() : liveResult.sampleModels(),
                now,
                message,
                requestPreview
        );
    }

    public OpenAiDirectResourceSmokeResponse openAiDirectResourceSmoke(
            Long credentialId,
            OpenAiDirectResourceSmokeRequest request) {
        UpstreamCredentialEntity entity = getRequired(credentialId);
        Instant now = Instant.now();
        boolean dryRun = request == null || request.dryRun() == null || request.dryRun();
        String requestedBaseUrl = firstNonBlank(
                request == null ? null : request.baseUrl(),
                entity.getBaseUrl()
        );
        String organization = normalizeBlank(request == null ? null : request.organization());
        String project = normalizeBlank(request == null ? null : request.project());
        boolean allowBillableProbes = request != null && Boolean.TRUE.equals(request.allowBillableProbes());
        boolean allowWriteProbes = request != null && Boolean.TRUE.equals(request.allowWriteProbes());
        Integer timeoutSeconds = request == null ? null : request.timeoutSeconds();
        List<String> families = openAiDirectResourceSmokeHttpClient.normalizeFamilies(
                request == null ? null : request.resourceFamilies()
        );
        String routeBlockReason = openAiDirectSmokeRouteBlockReason(entity, now);
        boolean routeEligible = routeBlockReason == null;
        List<OpenAiDirectResourceSmokeItemResponse> items;
        if (!routeEligible) {
            items = families.stream()
                    .map(family -> blockedRouteResourceSmokeItem(family, requestedBaseUrl, organization, project, routeBlockReason))
                    .toList();
        } else if (dryRun) {
            items = families.stream()
                    .map(family -> openAiDirectResourceSmokeHttpClient.dryRunItem(family, requestedBaseUrl, organization, project))
                    .toList();
        } else {
            String secret = credentialCryptoService.decrypt(entity.getApiKeyCiphertext());
            items = families.stream()
                    .map(family -> openAiDirectResourceSmokeHttpClient.executeProbe(
                            family,
                            secret,
                            requestedBaseUrl,
                            timeoutSeconds,
                            organization,
                            project,
                            allowBillableProbes,
                            allowWriteProbes
                    ))
                    .toList();
            applyOpenAiDirectResourceSmokeResult(entity, items, now);
            upstreamCredentialRepository.save(entity);
        }
        Map<String, Integer> summary = openAiDirectSmokeSummary(items);
        String classification = aggregateOpenAiDirectClassification(summary, routeEligible, dryRun);
        String skippedReason = aggregateOpenAiDirectSkippedReason(classification, routeEligible, dryRun, routeBlockReason, items);
        String status = routeEligible ? (dryRun ? "DRY_RUN_READY" : "LIVE_SMOKE_COMPLETED") : "ROUTE_BLOCKED";
        String message = openAiDirectResourceSmokeMessage(classification, routeEligible, dryRun);
        String baseUrl = items.isEmpty() ? requestedBaseUrl : text(items.getFirst().requestPreview().get("baseUrl"));
        return new OpenAiDirectResourceSmokeResponse(
                entity.getId(),
                status,
                classification,
                skippedReason,
                baseUrl,
                entity.getProviderType(),
                dryRun,
                routeEligible,
                routeBlockReason,
                entity.getApiKeyFingerprint(),
                now,
                message,
                summary,
                items
        );
    }

    public OpenAiDirectSmokeCertificationResponse openAiDirectResourceSmokeCertification(
            Long credentialId,
            OpenAiDirectResourceSmokeRequest request) {
        OpenAiDirectResourceSmokeResponse smoke = openAiDirectResourceSmoke(credentialId, request);
        OpenAiDirectSmokeCertificationResponse certification =
                openAiDirectSmokeCertificationService.certify(smoke, Instant.now());
        if (!certification.dryRun()) {
            UpstreamCredentialEntity entity = getRequired(credentialId);
            Map<String, Object> metadata = new java.util.LinkedHashMap<>(readMetadata(entity.getCredentialMetadataJson()));
            metadata.put("openai_direct_smoke_certification", openAiDirectSmokeCertificationService.metadata(certification));
            entity.setCredentialMetadataJson(writeMetadata(metadata));
            upstreamCredentialRepository.save(entity);
        }
        return certification;
    }

    public CredentialModelRefreshResponse refreshModels(Long credentialId) {
        CredentialModelDiscoveryService.CredentialRefreshResult result =
                credentialModelDiscoveryService.refreshCredential(credentialId);
        return new CredentialModelRefreshResponse(
                result.credentialId(),
                result.models().size(),
                result.models().stream().map(model -> model.modelName()).limit(10).toList(),
                result.refreshedAt()
        );
    }

    private String openAiDirectSmokeRouteBlockReason(UpstreamCredentialEntity entity, Instant now) {
        if (entity.getProviderType() != ProviderType.OPENAI_DIRECT) {
            return "PROVIDER_NOT_OPENAI_DIRECT";
        }
        if (!entity.isActive()) {
            return "CREDENTIAL_INACTIVE";
        }
        if (entity.getCooldownUntil() != null && entity.getCooldownUntil().isAfter(now)) {
            return "CREDENTIAL_COOLDOWN";
        }
        return null;
    }

    private OpenAiDirectResourceSmokeItemResponse blockedRouteResourceSmokeItem(
            String family,
            String requestedBaseUrl,
            String organization,
            String project,
            String routeBlockReason) {
        OpenAiDirectResourceSmokeItemResponse preview =
                openAiDirectResourceSmokeHttpClient.dryRunItem(family, requestedBaseUrl, organization, project);
        String classification = "PROVIDER_NOT_OPENAI_DIRECT".equals(routeBlockReason)
                ? "UNSUPPORTED"
                : ("CREDENTIAL_COOLDOWN".equals(routeBlockReason) ? "BUDGET_BLOCKED" : "SKIPPED");
        return new OpenAiDirectResourceSmokeItemResponse(
                preview.resourceFamily(),
                "ROUTE_BLOCKED",
                classification,
                routeBlockReason,
                preview.method(),
                preview.path(),
                preview.billable(),
                preview.writeOperation(),
                null,
                null,
                null,
                null,
                null,
                Map.of("routeBlockReason", routeBlockReason),
                preview.requestPreview()
        );
    }

    private Map<String, Integer> openAiDirectSmokeSummary(List<OpenAiDirectResourceSmokeItemResponse> items) {
        Map<String, Integer> summary = new java.util.LinkedHashMap<>();
        for (String classification : List.of("PASS", "FAIL", "SKIPPED", "UNSUPPORTED", "NO_PERMISSION", "BUDGET_BLOCKED")) {
            summary.put(classification, 0);
        }
        for (OpenAiDirectResourceSmokeItemResponse item : items) {
            summary.computeIfPresent(item.classification(), (key, value) -> value + 1);
        }
        return summary;
    }

    private String aggregateOpenAiDirectClassification(
            Map<String, Integer> summary,
            boolean routeEligible,
            boolean dryRun) {
        if (!routeEligible) {
            if (summary.getOrDefault("UNSUPPORTED", 0) > 0) {
                return "UNSUPPORTED";
            }
            if (summary.getOrDefault("BUDGET_BLOCKED", 0) > 0) {
                return "BUDGET_BLOCKED";
            }
            return "SKIPPED";
        }
        if (dryRun) {
            return "SKIPPED";
        }
        if (summary.getOrDefault("FAIL", 0) > 0) {
            return "FAIL";
        }
        if (summary.getOrDefault("NO_PERMISSION", 0) > 0) {
            return "NO_PERMISSION";
        }
        if (summary.getOrDefault("PASS", 0) > 0) {
            return "PASS";
        }
        if (summary.getOrDefault("BUDGET_BLOCKED", 0) > 0) {
            return "BUDGET_BLOCKED";
        }
        if (summary.getOrDefault("UNSUPPORTED", 0) > 0) {
            return "UNSUPPORTED";
        }
        return "SKIPPED";
    }

    private String aggregateOpenAiDirectSkippedReason(
            String classification,
            boolean routeEligible,
            boolean dryRun,
            String routeBlockReason,
            List<OpenAiDirectResourceSmokeItemResponse> items) {
        if ("PASS".equals(classification) || "FAIL".equals(classification)) {
            return null;
        }
        if (!routeEligible) {
            return routeBlockReason;
        }
        if (dryRun) {
            return "DRY_RUN";
        }
        return items.stream()
                .map(OpenAiDirectResourceSmokeItemResponse::skippedReason)
                .filter(reason -> reason != null && !reason.isBlank())
                .findFirst()
                .orElse(classification);
    }

    private String openAiDirectResourceSmokeMessage(String classification, boolean routeEligible, boolean dryRun) {
        if (!routeEligible) {
            return "OpenAI Direct credential 当前不满足资源族 smoke 前置条件。";
        }
        if (dryRun) {
            return "OpenAI Direct 资源族 smoke dry-run 已生成分类预览，未访问真实 OpenAI。";
        }
        return switch (classification) {
            case "PASS" -> "OpenAI Direct 资源族只读 smoke 已完成，至少一个只读资源族通过。";
            case "NO_PERMISSION" -> "OpenAI Direct 资源族 smoke 发现认证或权限不足。";
            case "BUDGET_BLOCKED" -> "OpenAI Direct 资源族 smoke 已按预算或写操作保护阻断部分资源族。";
            case "UNSUPPORTED" -> "OpenAI Direct 资源族 smoke 发现部分资源族不支持。";
            case "FAIL" -> "OpenAI Direct 资源族 smoke 存在未归类失败。";
            default -> "OpenAI Direct 资源族 smoke 已完成。";
        };
    }

    private void applyOpenAiDirectResourceSmokeResult(
            UpstreamCredentialEntity entity,
            List<OpenAiDirectResourceSmokeItemResponse> items,
            Instant now) {
        Optional<OpenAiDirectResourceSmokeItemResponse> failure = items.stream()
                .filter(item -> "FAIL".equals(item.classification())
                        || "NO_PERMISSION".equals(item.classification())
                        || "BUDGET_BLOCKED".equals(item.classification()))
                .filter(item -> item.failureType() != null)
                .findFirst();
        if (failure.isEmpty()) {
            entity.setLastErrorCode(null);
            entity.setLastErrorMessage(null);
            entity.setLastErrorAt(null);
            entity.setLastUsedAt(now);
            return;
        }
        OpenAiDirectResourceSmokeItemResponse item = failure.get();
        entity.setLastErrorCode(truncate(firstNonBlank(item.failureType(), item.skippedReason()), 64));
        entity.setLastErrorMessage(truncate(item.failureMessage(), 512));
        entity.setLastErrorAt(now);
    }

    private String openAiDirectSmokeClassification(
            boolean routeEligible,
            boolean dryRun,
            String routeBlockReason,
            OpenAiDirectSmokeHttpClient.OpenAiDirectSmokeResult liveResult) {
        if (!routeEligible) {
            if ("PROVIDER_NOT_OPENAI_DIRECT".equals(routeBlockReason)) {
                return "UNSUPPORTED";
            }
            return "CREDENTIAL_COOLDOWN".equals(routeBlockReason) ? "BUDGET_BLOCKED" : "SKIPPED";
        }
        if (dryRun || liveResult == null) {
            return "SKIPPED";
        }
        if (liveResult.success()) {
            return "PASS";
        }
        if (isOpenAiNoPermissionFailure(liveResult)) {
            return "NO_PERMISSION";
        }
        if (isOpenAiBudgetFailure(liveResult)) {
            return "BUDGET_BLOCKED";
        }
        return "FAIL";
    }

    private String openAiDirectSmokeSkippedReason(
            String classification,
            boolean routeEligible,
            boolean dryRun,
            String routeBlockReason,
            OpenAiDirectSmokeHttpClient.OpenAiDirectSmokeResult liveResult) {
        if ("PASS".equals(classification) || "FAIL".equals(classification)) {
            return null;
        }
        if (!routeEligible) {
            return routeBlockReason;
        }
        if (dryRun) {
            return "DRY_RUN";
        }
        if (liveResult != null && !isBlank(liveResult.failureType())) {
            return liveResult.failureType();
        }
        return classification;
    }

    private String openAiDirectSmokeMessage(String classification, boolean routeEligible, boolean dryRun, String fallback) {
        return switch (classification) {
            case "PASS" -> "OpenAI Direct credential 权限探测成功。";
            case "BUDGET_BLOCKED" -> routeEligible && !dryRun
                    ? "OpenAI Direct credential 权限探测被 rate limit 或额度保护阻断。"
                    : "OpenAI Direct credential 当前处于 cooldown 或预算保护状态。";
            case "NO_PERMISSION" -> "OpenAI Direct credential 认证或权限不足，已跳过后续真实资源族 smoke。";
            case "UNSUPPORTED" -> "该 credential 不是 OPENAI_DIRECT，不能用于 OpenAI Direct smoke。";
            case "SKIPPED" -> dryRun
                    ? "OpenAI Direct credential dry-run smoke 已完成安全预检，未访问真实 OpenAI。"
                    : fallback;
            default -> fallback;
        };
    }

    private void applyOpenAiDirectSmokeResult(
            UpstreamCredentialEntity entity,
            OpenAiDirectSmokeHttpClient.OpenAiDirectSmokeResult liveResult,
            String classification,
            Instant now) {
        if (liveResult == null) {
            return;
        }
        if ("PASS".equals(classification)) {
            entity.setLastErrorCode(null);
            entity.setLastErrorMessage(null);
            entity.setLastErrorAt(null);
            entity.setLastUsedAt(now);
            return;
        }
        entity.setLastErrorCode(truncate(liveResult.failureType(), 64));
        entity.setLastErrorMessage(truncate(liveResult.failureMessage(), 512));
        entity.setLastErrorAt(now);
    }

    private boolean isOpenAiNoPermissionFailure(OpenAiDirectSmokeHttpClient.OpenAiDirectSmokeResult liveResult) {
        if (liveResult.httpStatus() != null && (liveResult.httpStatus() == 401 || liveResult.httpStatus() == 403)) {
            return true;
        }
        String failureType = upper(liveResult.failureType());
        return failureType.contains("AUTH")
                || failureType.contains("PERMISSION")
                || failureType.contains("UNAUTHORIZED")
                || failureType.contains("FORBIDDEN")
                || failureType.contains("INVALID_API_KEY");
    }

    private boolean isOpenAiBudgetFailure(OpenAiDirectSmokeHttpClient.OpenAiDirectSmokeResult liveResult) {
        if (liveResult.httpStatus() != null && liveResult.httpStatus() == 429) {
            return true;
        }
        String failureType = upper(liveResult.failureType());
        return failureType.contains("BUDGET")
                || failureType.contains("QUOTA")
                || failureType.contains("RATE_LIMIT")
                || failureType.contains("RATE")
                || failureType.contains("LIMIT");
    }

    @Transactional(readOnly = true)
    public List<String> listSupportedModelCatalog(ProviderType providerType) {
        return supportedModelCatalogService.listByProvider(providerType);
    }

    private UpstreamCredentialEntity getRequired(Long id) {
        Optional<UpstreamCredentialEntity> entity = upstreamCredentialRepository.findById(id);
        if (entity.isEmpty() || entity.get().isDeleted()) {
            throw new IllegalArgumentException("未找到指定的上游凭证。");
        }
        return entity.get();
    }

    private void apply(UpstreamCredentialEntity entity, CredentialRequest request) {
        String secret = requireSecret(request.resolvedSecret());
        UpstreamAccountPoolEntity pool = resolvePool(request.poolId());
        entity.setCredentialName(request.credentialName().trim());
        entity.setProviderType(request.providerType());
        entity.setBaseUrl(request.baseUrl().trim());
        entity.setAuthKind(request.resolvedAuthKind());
        entity.setApiKeyCiphertext(credentialCryptoService.encrypt(secret));
        entity.setApiKeyFingerprint(credentialCryptoService.fingerprint(secret));
        entity.setCredentialMetadataJson(writeMetadata(request.resolvedCredentialMetadata()));
        entity.setSupportedModels(supportedModelCatalogService.resolveForCredentialImport(
                request.providerType(),
                pool,
                request.resolvedSupportedModels()
        ));
        entity.setActive(request.active() == null || request.active());
        entity.setProxyId(request.proxyId());
        entity.setTlsFingerprintProfileId(request.tlsFingerprintProfileId());
        entity.setSiteProfileId(providerSiteRegistryService.ensureSiteProfile(
                request.providerType(),
                request.baseUrl().trim(),
                request.siteProfileId()
        ).getId());
        entity.setPoolId(pool == null ? null : pool.getId());
    }

    private CredentialResponse toResponse(UpstreamCredentialEntity entity, String poolName) {
        long totalRequests = entity.getTotalRequestCount();
        long successRequests = entity.getSuccessfulRequestCount();
        long totalTokens = entity.getTotalTokenCount();
        long cacheHitTokens = entity.getTotalCacheHitTokenCount();
        long durationSamples = entity.getDurationSampleCount();
        long firstTokenSamples = entity.getFirstTokenSampleCount();
        return new CredentialResponse(
                entity.getId(),
                entity.getCredentialName(),
                entity.getProviderType(),
                entity.getBaseUrl(),
                entity.getAuthKind(),
                supportedModelCatalogService.normalize(entity.getSupportedModels()),
                entity.getApiKeyFingerprint(),
                readMetadata(entity.getCredentialMetadataJson()),
                entity.isActive(),
                entity.getCooldownUntil(),
                entity.getLastErrorCode(),
                entity.getLastErrorMessage(),
                entity.getLastErrorAt(),
                entity.getLastUsedAt(),
                totalRequests,
                successRequests,
                entity.getFailedRequestCount(),
                entity.getCanceledRequestCount(),
                totalTokens,
                cacheHitTokens,
                entity.getTotalCacheWriteTokenCount(),
                entity.getTotalSavedInputTokenCount(),
                ratio(successRequests, totalRequests),
                ratio(cacheHitTokens, totalTokens),
                entity.getTotalDurationMs(),
                durationSamples,
                ratio(entity.getTotalDurationMs(), durationSamples),
                entity.getTotalFirstTokenMs(),
                firstTokenSamples,
                ratio(entity.getTotalFirstTokenMs(), firstTokenSamples),
                entity.getLastFirstTokenMs(),
                entity.getMinFirstTokenMs(),
                entity.getMaxFirstTokenMs(),
                entity.getProxyId(),
                entity.getTlsFingerprintProfileId(),
                entity.getSiteProfileId(),
                entity.getPoolId(),
                poolName,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private CredentialResponse toResponse(UpstreamCredentialEntity entity) {
        String poolName = entity.getPoolId() == null
                ? null
                : upstreamAccountPoolRepository.findById(entity.getPoolId())
                        .map(UpstreamAccountPoolEntity::getPoolName)
                        .orElse(null);
        return toResponse(entity, poolName);
    }

    private UpstreamAccountPoolEntity resolvePool(Long requestPoolId) {
        if (requestPoolId == null) {
            return null;
        }
        return upstreamAccountPoolRepository.findById(requestPoolId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定账号池。"));
    }

    private Map<Long, String> resolvePoolNameMap(List<UpstreamCredentialEntity> credentials) {
        Set<Long> poolIds = credentials.stream()
                .map(UpstreamCredentialEntity::getPoolId)
                .filter(id -> id != null && id > 0)
                .collect(java.util.stream.Collectors.toSet());
        if (poolIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        upstreamAccountPoolRepository.findAllById(poolIds)
                .forEach(pool -> result.put(pool.getId(), pool.getPoolName()));
        return result;
    }

    private String requireSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("凭证 secret 不能为空。");
        }
        return secret.trim();
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String upper(String value) {
        return value == null ? "" : value.toUpperCase(java.util.Locale.ROOT);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return metadata == null || metadata.isEmpty() ? null : objectMapper.writeValueAsString(metadata);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("无法序列化凭证 metadata。", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, Map.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("无法解析凭证 metadata。", exception);
        }
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return ((double) numerator) / denominator;
    }
}
