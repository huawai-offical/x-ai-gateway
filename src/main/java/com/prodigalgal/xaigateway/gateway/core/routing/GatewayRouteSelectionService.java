package com.prodigalgal.xaigateway.gateway.core.routing;

import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
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
import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolutionReport;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.repository.NetworkProxyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final RouteCacheStore routeCacheStore;
    private final HealthStateStore healthStateStore;

    @Autowired
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
            SiteCapabilityTruthService siteCapabilityTruthService,
            RouteCacheStore routeCacheStore,
            HealthStateStore healthStateStore) {
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
        this.routeCacheStore = routeCacheStore;
        this.healthStateStore = healthStateStore;
    }

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
        this(
                distributedKeyQueryService,
                modelCatalogQueryService,
                promptFingerprintService,
                affinityCacheService,
                distributedKeyGovernanceService,
                upstreamCredentialRepository,
                networkProxyRepository,
                accountSelectionService,
                gatewayRequestFeatureService,
                siteCapabilityTruthService,
                new RouteCacheStore() {
                    @Override
                    public Optional<RoutePlanSnapshot> get(Long distributedKeyId, String protocol, String requestPath, String requestedModel, GatewayRequestSemantics semantics) {
                        return Optional.empty();
                    }

                    @Override
                    public void put(Long distributedKeyId, String protocol, String requestPath, String requestedModel, GatewayRequestSemantics semantics, RoutePlanSnapshot snapshot, Duration ttl) {
                    }

                    @Override
                    public void invalidate(Long distributedKeyId, String protocol, String requestPath, String requestedModel, GatewayRequestSemantics semantics) {
                    }
                },
                new HealthStateStore() {
                    @Override
                    public Optional<CredentialHealthState> getCredentialState(Long credentialId) {
                        return Optional.empty();
                    }

                    @Override
                    public void markCooldown(Long credentialId, String reason, Duration ttl) {
                    }

                    @Override
                    public void clear(Long credentialId) {
                    }
                }
        );
    }

    public RouteSelectionResult select(RouteSelectionRequest request) {
        DistributedKeyView distributedKey = distributedKeyQueryService.findActiveByKeyPrefix(request.distributedKeyPrefix())
                .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"));

        String normalizedProtocol = normalize(request.protocol());
        if (!isProtocolAllowed(distributedKey, normalizedProtocol)) {
            throw new IllegalArgumentException("当前 DistributedKey 不允许访问该协议。");
        }
        if (distributedKey.expiresAt() != null && distributedKey.expiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("当前 DistributedKey 已过期。");
        }

        GatewayClientFamily clientFamily = request.clientFamily() == null ? GatewayClientFamily.GENERIC_OPENAI : request.clientFamily();
        DistributedKeyGovernanceService.GovernanceDecision governanceDecision =
                distributedKeyGovernanceService.evaluate(distributedKey, clientFamily, request.requestBody(), request.reserveGovernance());
        if (!governanceDecision.blockers().isEmpty()) {
            throw new IllegalArgumentException(String.join("；", governanceDecision.blockers()));
        }

        GatewayRequestSemantics semantics = gatewayRequestFeatureService.describe(
                request.requestPath(),
                request.requestBody() instanceof com.fasterxml.jackson.databind.JsonNode jsonNode ? jsonNode : null
        );
        RoutePlanSnapshot snapshot = routeCacheStore
                .get(distributedKey.id(), normalizedProtocol, request.requestPath(), request.requestedModel(), semantics)
                .orElseGet(() -> buildAndCacheStaticPlan(distributedKey, normalizedProtocol, request, semantics));

        if (!isModelAllowed(distributedKey, request.requestedModel(), snapshot.publicModel(), snapshot.resolvedModelKey())) {
            throw new IllegalArgumentException("当前 DistributedKey 不允许访问该模型。");
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

        List<RouteCandidateEvaluation> candidateEvaluations = snapshot.candidateEvaluations().stream()
                .map(candidate -> evaluateCandidate(candidate, distributedKey, snapshot.modelGroup(), prefixHash, fingerprint, clientFamily))
                .sorted(candidateEvaluationComparator())
                .toList();

        List<RouteCandidateView> candidates = candidateEvaluations.stream()
                .filter(RouteCandidateEvaluation::eligible)
                .map(RouteCandidateEvaluation::candidate)
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("当前 provider 候选已被健康或冷却策略阻断。");
        }

        RouteCandidateEvaluation selectedEvaluation = candidateEvaluations.stream()
                .filter(RouteCandidateEvaluation::eligible)
                .findFirst()
                .orElseThrow();

        return new RouteSelectionResult(
                distributedKey.id(),
                distributedKey.keyPrefix(),
                request.requestedModel(),
                snapshot.publicModel(),
                snapshot.resolvedModelKey(),
                normalizedProtocol,
                prefixHash,
                fingerprint,
                snapshot.modelGroup(),
                clientFamily,
                governanceDecision.notes(),
                governanceDecision.concurrencyReservationKey(),
                selectedEvaluation.selectionSource(),
                selectedEvaluation.candidate(),
                candidates,
                candidateEvaluations,
                List.of()
        );
    }

    public void recordSuccessfulSelection(RouteSelectionResult selectionResult) {
        RouteCandidateView selected = selectionResult.selectedCandidate();
        healthStateStore.clear(selected.candidate().credentialId());
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

    public void markCredentialCooldown(Long credentialId, String reason) {
        healthStateStore.markCooldown(credentialId, reason, Duration.ofMinutes(5));
    }

    private RoutePlanSnapshot buildAndCacheStaticPlan(
            DistributedKeyView distributedKey,
            String normalizedProtocol,
            RouteSelectionRequest request,
            GatewayRequestSemantics semantics) {
        ResolvedModelView resolved = modelCatalogQueryService
                .resolveRequestedModel(request.requestedModel(), normalizedProtocol)
                .orElseThrow(() -> new IllegalArgumentException("当前请求模型没有可用候选。"));

        if (!isModelAllowed(distributedKey, request.requestedModel(), resolved.publicModel(), resolved.resolvedModelKey())) {
            throw new IllegalArgumentException("当前 DistributedKey 不允许访问该模型。");
        }

        List<RouteCandidateView> mergedCandidates = mergeCandidates(distributedKey, resolved);
        if (mergedCandidates.isEmpty()) {
            throw new IllegalArgumentException("当前 DistributedKey 绑定的上游凭证中没有可用候选。");
        }

        List<StaticCandidateResolution> resolutions = mergedCandidates.stream()
                .map(candidate -> resolveStaticCandidate(distributedKey, candidate, semantics))
                .toList();

        boolean anyProviderAllowed = resolutions.stream().anyMatch(item -> !item.providerBlocked());
        if (!anyProviderAllowed) {
            throw new IllegalArgumentException("当前 DistributedKey 不允许所选 provider。");
        }

        int bestCapabilityRank = resolutions.stream()
                .filter(item -> !item.providerBlocked() && !item.featureBlocked())
                .mapToInt(StaticCandidateResolution::capabilityRank)
                .max()
                .orElse(0);
        if (bestCapabilityRank <= 0) {
            throw new IllegalArgumentException("当前 provider 候选无法满足请求特征。");
        }

        List<RouteCandidateEvaluation> evaluations = resolutions.stream()
                .map(item -> toStaticEvaluation(item, bestCapabilityRank))
                .toList();

        RoutePlanSnapshot snapshot = new RoutePlanSnapshot(
                resolved.publicModel(),
                resolved.resolvedModelKey(),
                resolved.resolvedModelKey(),
                evaluations
        );
        routeCacheStore.put(distributedKey.id(), normalizedProtocol, request.requestPath(), request.requestedModel(), semantics, snapshot, null);
        return snapshot;
    }

    private StaticCandidateResolution resolveStaticCandidate(
            DistributedKeyView distributedKey,
            RouteCandidateView candidate,
            GatewayRequestSemantics semantics) {
        boolean providerBlocked = !isProviderAllowed(distributedKey, candidate);
        CapabilityResolutionReport report = providerBlocked ? null : siteCapabilityTruthService.resolve(candidate.candidate(), semantics);
        boolean featureBlocked = report == null || !supportsRequiredFeatures(report);
        int capabilityRank = featureBlocked ? 0 : capabilityRank(report.overallEffectiveLevel());
        String capabilityLevel = featureBlocked ? null : report.overallEffectiveLevel().name();

        return new StaticCandidateResolution(
                new RouteCandidateView(
                        candidate.candidate(),
                        candidate.bindingId(),
                        candidate.bindingPriority(),
                        candidate.bindingWeight(),
                        capabilityLevel,
                        capabilityRank
                ),
                providerBlocked,
                featureBlocked
        );
    }

    private RouteCandidateEvaluation toStaticEvaluation(StaticCandidateResolution resolution, int bestCapabilityRank) {
        List<String> exclusionReasons = new ArrayList<>();
        if (resolution.providerBlocked()) {
            exclusionReasons.add("provider_not_allowed");
        } else if (resolution.featureBlocked()) {
            exclusionReasons.add("feature_unsupported");
        } else if (resolution.capabilityRank() < bestCapabilityRank) {
            exclusionReasons.add("capability_deprioritized");
        }

        return new RouteCandidateEvaluation(
                resolution.candidate(),
                exclusionReasons.isEmpty(),
                "STATIC_READY",
                null,
                false,
                RouteSelectionSource.WEIGHTED_HASH,
                0.0,
                List.of(),
                List.copyOf(exclusionReasons)
        );
    }

    private RouteCandidateEvaluation evaluateCandidate(
            RouteCandidateEvaluation staticEvaluation,
            DistributedKeyView distributedKey,
            String modelGroup,
            String prefixHash,
            String fingerprint,
            GatewayClientFamily clientFamily) {
        List<String> exclusionReasons = new ArrayList<>(staticEvaluation.exclusionReasons());
        RouteSelectionSource selectionSource = RouteSelectionSource.WEIGHTED_HASH;
        boolean affinityMatched = false;
        Instant cooldownUntil = null;
        String healthState = "HEALTHY";

        if (exclusionReasons.isEmpty() && distributedKey.bindings().stream().noneMatch(binding ->
                binding.credentialId().equals(staticEvaluation.candidate().candidate().credentialId()))) {
            exclusionReasons.add("binding_inactive");
            healthState = "FILTERED";
        }

        if (exclusionReasons.isEmpty()) {
            Optional<CredentialHealthState> storedHealth = healthStateStore.getCredentialState(staticEvaluation.candidate().candidate().credentialId());
            if (storedHealth.isPresent()
                    && storedHealth.get().cooldownUntil() != null
                    && storedHealth.get().cooldownUntil().isAfter(Instant.now())) {
                exclusionReasons.add("cooldown_active");
                healthState = storedHealth.get().state();
                cooldownUntil = storedHealth.get().cooldownUntil();
            }

            if (!isNetworkHealthy(staticEvaluation.candidate())) {
                exclusionReasons.add("network_blocked");
                healthState = "NETWORK_BLOCKED";
            }

            if (!hasHealthyAccountIfBound(distributedKey.id(), staticEvaluation.candidate().candidate().providerType(), clientFamily)) {
                exclusionReasons.add("account_pool_unavailable");
                healthState = "ACCOUNT_POOL_UNAVAILABLE";
            }

            if (exclusionReasons.isEmpty()) {
                selectionSource = matchedAffinitySource(distributedKey.id(), modelGroup, prefixHash, fingerprint, staticEvaluation.candidate());
                affinityMatched = selectionSource != RouteSelectionSource.WEIGHTED_HASH;
            }
        } else {
            healthState = "FILTERED";
        }

        List<String> scoreBreakdown = buildScoreBreakdown(staticEvaluation.candidate(), selectionSource, healthState);
        double totalScore = exclusionReasons.isEmpty() ? totalScore(staticEvaluation.candidate(), selectionSource, fingerprint) : Double.NEGATIVE_INFINITY;

        return new RouteCandidateEvaluation(
                staticEvaluation.candidate(),
                exclusionReasons.isEmpty(),
                healthState,
                cooldownUntil,
                affinityMatched,
                selectionSource,
                totalScore,
                scoreBreakdown,
                List.copyOf(exclusionReasons)
        );
    }

    private List<String> buildScoreBreakdown(RouteCandidateView candidate, RouteSelectionSource selectionSource, String healthState) {
        List<String> breakdown = new ArrayList<>();
        breakdown.add("capability_rank=" + candidate.capabilityRank());
        breakdown.add("binding_priority=" + candidate.bindingPriority());
        breakdown.add("binding_weight=" + candidate.bindingWeight());
        breakdown.add("health_state=" + healthState);
        breakdown.add("selection_source=" + selectionSource.name());
        return List.copyOf(breakdown);
    }

    private double totalScore(RouteCandidateView candidate, RouteSelectionSource selectionSource, String seed) {
        double capabilityScore = candidate.capabilityRank() * 10_000d;
        double priorityScore = 1_000d / Math.max(candidate.bindingPriority(), 1);
        double weightScore = candidate.bindingWeight();
        double affinityBonus = switch (selectionSource) {
            case PREFIX_AFFINITY -> 500d;
            case FINGERPRINT_AFFINITY -> 300d;
            case MODEL_AFFINITY -> 150d;
            case WEIGHTED_HASH -> 0d;
        };
        return capabilityScore + priorityScore + weightScore + affinityBonus + stablePositiveHash(seed + ":" + candidate.candidate().credentialId()) / (double) Long.MAX_VALUE;
    }

    private RouteSelectionSource matchedAffinitySource(
            Long distributedKeyId,
            String modelGroup,
            String prefixHash,
            String fingerprint,
            RouteCandidateView candidate) {
        String provider = candidate.candidate().providerType().name();
        String credentialId = String.valueOf(candidate.candidate().credentialId());

        if (credentialId.equals(affinityCacheService.getPrefixAffinity(distributedKeyId, provider, modelGroup, prefixHash))) {
            return RouteSelectionSource.PREFIX_AFFINITY;
        }
        if (credentialId.equals(affinityCacheService.getFingerprintAffinity(distributedKeyId, provider, modelGroup, fingerprint))) {
            return RouteSelectionSource.FINGERPRINT_AFFINITY;
        }
        if (credentialId.equals(affinityCacheService.getModelAffinity(distributedKeyId, provider, modelGroup))) {
            return RouteSelectionSource.MODEL_AFFINITY;
        }
        return RouteSelectionSource.WEIGHTED_HASH;
    }

    private Comparator<RouteCandidateEvaluation> candidateEvaluationComparator() {
        return Comparator
                .comparing(RouteCandidateEvaluation::eligible, Comparator.reverseOrder())
                .thenComparing(RouteCandidateEvaluation::totalScore, Comparator.reverseOrder())
                .thenComparing(item -> item.candidate().candidate().credentialId());
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

    private boolean hasHealthyAccountIfBound(Long distributedKeyId, ProviderType providerType, GatewayClientFamily clientFamily) {
        return accountSelectionService.hasHealthyAccountBinding(distributedKeyId, providerType, clientFamily);
    }

    private boolean supportsRequiredFeatures(CapabilityResolutionReport report) {
        if (report == null) {
            return false;
        }
        return report.blockedReasons().isEmpty() && report.overallEffectiveLevel() != InteropCapabilityLevel.UNSUPPORTED;
    }

    private int capabilityRank(InteropCapabilityLevel level) {
        if (level == null) {
            return 0;
        }
        return switch (level) {
            case NATIVE -> 3;
            case EMULATED -> 2;
            case LOSSY -> 1;
            case UNSUPPORTED -> 0;
        };
    }

    private String normalize(String protocol) {
        return protocol == null ? "openai" : protocol.trim().toLowerCase();
    }

    private record StaticCandidateResolution(
            RouteCandidateView candidate,
            boolean providerBlocked,
            boolean featureBlocked
    ) {
        int capabilityRank() {
            return candidate.capabilityRank();
        }
    }
}
