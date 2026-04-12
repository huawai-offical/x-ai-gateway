package com.prodigalgal.xaigateway.gateway.core.routing;

import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.cache.AffinityBindingType;
import com.prodigalgal.xaigateway.gateway.core.cache.AffinityCacheService;
import com.prodigalgal.xaigateway.gateway.core.cache.PromptFingerprintService;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryService;
import com.prodigalgal.xaigateway.gateway.core.catalog.ResolvedModelView;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.infra.persistence.repository.NetworkProxyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GatewayRouteSelectionService {

    private final DistributedKeyQueryService distributedKeyQueryService;
    private final ModelCatalogQueryService modelCatalogQueryService;
    private final PromptFingerprintService promptFingerprintService;
    private final AffinityCacheService affinityCacheService;
    private final DistributedKeyGovernanceService distributedKeyGovernanceService;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final NetworkProxyRepository networkProxyRepository;
    private final AccountSelectionService accountSelectionService;
    private final GatewayRequestFeatureService gatewayRequestFeatureService;
    private final SiteCapabilityTruthService siteCapabilityTruthService;

    public GatewayRouteSelectionService(
            DistributedKeyQueryService distributedKeyQueryService,
            ModelCatalogQueryService modelCatalogQueryService,
            PromptFingerprintService promptFingerprintService,
            AffinityCacheService affinityCacheService,
            DistributedKeyGovernanceService distributedKeyGovernanceService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            NetworkProxyRepository networkProxyRepository,
            AccountSelectionService accountSelectionService,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            SiteCapabilityTruthService siteCapabilityTruthService) {
        this.distributedKeyQueryService = distributedKeyQueryService;
        this.modelCatalogQueryService = modelCatalogQueryService;
        this.promptFingerprintService = promptFingerprintService;
        this.affinityCacheService = affinityCacheService;
        this.distributedKeyGovernanceService = distributedKeyGovernanceService;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.networkProxyRepository = networkProxyRepository;
        this.accountSelectionService = accountSelectionService;
        this.gatewayRequestFeatureService = gatewayRequestFeatureService;
        this.siteCapabilityTruthService = siteCapabilityTruthService;
    }

    public RouteSelectionResult select(RouteSelectionRequest request) {
        DistributedKeyView distributedKey = distributedKeyQueryService.findActiveByKeyPrefix(request.distributedKeyPrefix())
                .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"));

        String normalizedProtocol = normalize(request.protocol());
        if (!isProtocolAllowed(distributedKey, normalizedProtocol)) {
            throw new IllegalArgumentException("当前 DistributedKey 不允许访问该协议。");
        }
        if (distributedKey.expiresAt() != null && distributedKey.expiresAt().isBefore(java.time.Instant.now())) {
            throw new IllegalArgumentException("当前 DistributedKey 已过期。");
        }

        ResolvedModelView resolved = modelCatalogQueryService
                .resolveRequestedModel(request.requestedModel(), normalizedProtocol)
                .orElseThrow(() -> new IllegalArgumentException("当前请求模型没有可用候选。"));

        if (!isModelAllowed(distributedKey, request.requestedModel(), resolved.publicModel(), resolved.resolvedModelKey())) {
            throw new IllegalArgumentException("当前 DistributedKey 不允许访问该模型。");
        }

        GatewayClientFamily clientFamily = request.clientFamily() == null ? GatewayClientFamily.GENERIC_OPENAI : request.clientFamily();
        DistributedKeyGovernanceService.GovernanceDecision governanceDecision =
                distributedKeyGovernanceService.evaluate(distributedKey, clientFamily, request.requestBody(), request.reserveGovernance());
        if (!governanceDecision.blockers().isEmpty()) {
            throw new IllegalArgumentException(String.join("；", governanceDecision.blockers()));
        }

        List<RouteCandidateView> mergedCandidates = mergeCandidates(distributedKey, resolved);
        List<RouteCandidateView> providerFilteredCandidates = mergedCandidates.stream()
                .filter(candidate -> isProviderAllowed(distributedKey, candidate))
                .toList();
        if (mergedCandidates.isEmpty()) {
            throw new IllegalArgumentException("当前 DistributedKey 绑定的上游凭证中没有可用候选。");
        }
        if (providerFilteredCandidates.isEmpty()) {
            throw new IllegalArgumentException("当前 DistributedKey 不允许所选 provider。");
        }
        List<RouteCandidateView> networkFilteredCandidates = providerFilteredCandidates.stream()
                .filter(this::isNetworkHealthy)
                .toList();
        if (networkFilteredCandidates.isEmpty()) {
            throw new IllegalArgumentException("当前 provider 候选均被网络治理策略阻断。");
        }
        List<InteropFeature> requiredFeatures = gatewayRequestFeatureService.detectRequiredFeatures(
                request.requestPath(),
                request.requestBody() instanceof com.fasterxml.jackson.databind.JsonNode jsonNode ? jsonNode : null
        );
        List<RouteCandidateView> featureCompatibleCandidates = networkFilteredCandidates.stream()
                .filter(candidate -> supportsRequiredFeatures(candidate, requiredFeatures))
                .toList();
        if (featureCompatibleCandidates.isEmpty()) {
            throw new IllegalArgumentException("当前 provider 候选无法满足请求特征。");
        }
        List<RouteCandidateView> candidates = featureCompatibleCandidates.stream()
                .filter(candidate -> hasHealthyAccountIfBound(distributedKey.id(), candidate.candidate().providerType(), clientFamily))
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("当前 provider 候选没有健康账号池可用。");
        }

        String prefixHash = promptFingerprintService.buildPrefixHash(
                normalizedProtocol,
                request.requestPath(),
                request.requestBody()
        );
        String fingerprint = promptFingerprintService.buildFingerprint(
                normalizedProtocol,
                request.requestPath(),
                request.requestBody()
        );
        String modelGroup = resolved.resolvedModelKey();

        RouteCandidateView selected = findByAffinity(distributedKey.id(), modelGroup, prefixHash, candidates, AffinityBindingType.PREFIX);
        RouteSelectionSource source = RouteSelectionSource.PREFIX_AFFINITY;

        if (selected == null) {
            selected = findByAffinity(distributedKey.id(), modelGroup, fingerprint, candidates, AffinityBindingType.FINGERPRINT);
            source = RouteSelectionSource.FINGERPRINT_AFFINITY;
        }

        if (selected == null) {
            selected = findByAffinity(distributedKey.id(), modelGroup, null, candidates, AffinityBindingType.MODEL_GROUP);
            source = RouteSelectionSource.MODEL_AFFINITY;
        }

        if (selected == null) {
            selected = weightedHashSelect(candidates, fingerprint);
            source = RouteSelectionSource.WEIGHTED_HASH;
        }

        return new RouteSelectionResult(
                distributedKey.id(),
                distributedKey.keyPrefix(),
                request.requestedModel(),
                resolved.publicModel(),
                resolved.resolvedModelKey(),
                normalizedProtocol,
                prefixHash,
                fingerprint,
                modelGroup,
                clientFamily,
                governanceDecision.notes(),
                governanceDecision.concurrencyReservationKey(),
                source,
                selected,
                candidates
        );
    }

    public void recordSuccessfulSelection(RouteSelectionResult selectionResult) {
        RouteCandidateView selected = selectionResult.selectedCandidate();
        affinityCacheService.bindPrefixAffinity(
                selectionResult.distributedKeyId(),
                selected.candidate().providerType().name(),
                selectionResult.modelGroup(),
                selectionResult.prefixHash(),
                selected.candidate().credentialId()
        );
        affinityCacheService.bindFingerprintAffinity(
                selectionResult.distributedKeyId(),
                selected.candidate().providerType().name(),
                selectionResult.modelGroup(),
                selectionResult.fingerprint(),
                selected.candidate().credentialId()
        );
        affinityCacheService.bindModelAffinity(
                selectionResult.distributedKeyId(),
                selected.candidate().providerType().name(),
                selectionResult.modelGroup(),
                selected.candidate().credentialId()
        );
    }

    public void invalidateSelection(RouteSelectionResult selectionResult) {
        RouteCandidateView selected = selectionResult.selectedCandidate();
        String provider = selected.candidate().providerType().name();
        Long credentialId = selected.candidate().credentialId();

        affinityCacheService.invalidateIfMatches(
                AffinityBindingType.PREFIX,
                selectionResult.distributedKeyId(),
                provider,
                selectionResult.modelGroup(),
                selectionResult.prefixHash(),
                credentialId
        );
        affinityCacheService.invalidateIfMatches(
                AffinityBindingType.FINGERPRINT,
                selectionResult.distributedKeyId(),
                provider,
                selectionResult.modelGroup(),
                selectionResult.fingerprint(),
                credentialId
        );
        affinityCacheService.invalidateIfMatches(
                AffinityBindingType.MODEL_GROUP,
                selectionResult.distributedKeyId(),
                provider,
                selectionResult.modelGroup(),
                null,
                credentialId
        );
    }

    private RouteCandidateView findByAffinity(
            Long distributedKeyId,
            String modelGroup,
            String hash,
            List<RouteCandidateView> candidates,
            AffinityBindingType type) {
        for (RouteCandidateView candidate : candidates) {
            String provider = candidate.candidate().providerType().name();
            String matched = switch (type) {
                case PREFIX -> affinityCacheService.getPrefixAffinity(distributedKeyId, provider, modelGroup, hash);
                case FINGERPRINT -> affinityCacheService.getFingerprintAffinity(distributedKeyId, provider, modelGroup, hash);
                case MODEL_GROUP -> affinityCacheService.getModelAffinity(distributedKeyId, provider, modelGroup);
            };

            if (matched != null && matched.equals(String.valueOf(candidate.candidate().credentialId()))) {
                return candidate;
            }
        }
        return null;
    }

    private RouteCandidateView weightedHashSelect(List<RouteCandidateView> candidates, String seed) {
        return candidates.stream()
                .max(Comparator.comparingDouble(candidate -> score(candidate, seed)))
                .orElseThrow();
    }

    private double score(RouteCandidateView candidate, String seed) {
        long hash = stablePositiveHash(seed + ":" + candidate.candidate().credentialId());
        double normalized = (double) hash / Long.MAX_VALUE;
        return normalized * Math.max(candidate.bindingWeight(), 1) / Math.max(candidate.bindingPriority(), 1);
    }

    private long stablePositiveHash(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            byte[] firstEight = HexFormat.of().parseHex(HexFormat.of().formatHex(digest).substring(0, 16));
            long value = 0L;
            for (byte part : firstEight) {
                value = (value << 8) | (part & 0xff);
            }
            return Math.abs(value == Long.MIN_VALUE ? Long.MAX_VALUE : value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境缺少 SHA-256。", exception);
        }
    }

    private List<RouteCandidateView> mergeCandidates(DistributedKeyView distributedKey, ResolvedModelView resolved) {
        Map<Long, DistributedCredentialBindingView> bindingMap = distributedKey.bindings().stream()
                .collect(Collectors.toMap(DistributedCredentialBindingView::credentialId, Function.identity()));

        return resolved.candidates().stream()
                .filter(candidate -> bindingMap.containsKey(candidate.credentialId()))
                .map(candidate -> {
                    DistributedCredentialBindingView binding = bindingMap.get(candidate.credentialId());
                    return new RouteCandidateView(
                            candidate,
                            binding.bindingId(),
                            binding.priority(),
                            binding.weight()
                    );
                })
                .sorted(Comparator
                        .comparing(RouteCandidateView::bindingPriority)
                        .thenComparing(RouteCandidateView::bindingWeight, Comparator.reverseOrder())
                        .thenComparing(item -> item.candidate().credentialId()))
                .toList();
    }

    private boolean isProtocolAllowed(DistributedKeyView distributedKey, String protocol) {
        return distributedKey.allowedProtocols().isEmpty() || distributedKey.allowedProtocols().contains(protocol);
    }

    private boolean isModelAllowed(DistributedKeyView distributedKey, String requestedModel, String publicModel, String resolvedModelKey) {
        if (distributedKey.allowedModels().isEmpty()) {
            return true;
        }

        String requested = ModelIdNormalizer.normalize(requestedModel);
        String publicNormalized = ModelIdNormalizer.normalize(publicModel);
        return distributedKey.allowedModels().contains(requested)
                || distributedKey.allowedModels().contains(publicNormalized)
                || distributedKey.allowedModels().contains(resolvedModelKey);
    }

    private boolean isProviderAllowed(DistributedKeyView distributedKey, RouteCandidateView candidate) {
        if (distributedKey.allowedProviderTypes().isEmpty()) {
            return true;
        }
        return distributedKey.allowedProviderTypes().contains(candidate.candidate().providerType().name());
    }

    private boolean isNetworkHealthy(RouteCandidateView candidate) {
        return upstreamCredentialRepository.findById(candidate.candidate().credentialId())
                .map(credential -> {
                    if (credential.getProxyId() == null) {
                        return true;
                    }
                    return networkProxyRepository.findById(credential.getProxyId())
                            .map(proxy -> proxy.isActive() && !"FAILED".equalsIgnoreCase(proxy.getLastStatus()))
                            .orElse(false);
                })
                .orElse(false);
    }

    private boolean hasHealthyAccountIfBound(Long distributedKeyId, com.prodigalgal.xaigateway.gateway.core.shared.ProviderType providerType, GatewayClientFamily clientFamily) {
        return accountSelectionService.hasHealthyAccountBinding(distributedKeyId, providerType, clientFamily);
    }

    private boolean supportsRequiredFeatures(RouteCandidateView candidate, List<InteropFeature> requiredFeatures) {
        if (candidate.candidate().capabilityLevel() == InteropCapabilityLevel.UNSUPPORTED) {
            return false;
        }
        for (InteropFeature feature : requiredFeatures) {
            if (siteCapabilityTruthService.capabilityLevel(candidate.candidate(), feature) == InteropCapabilityLevel.UNSUPPORTED) {
                return false;
            }
        }
        return true;
    }

    private String normalize(String protocol) {
        return protocol == null ? "openai" : protocol.trim().toLowerCase();
    }
}
