package com.prodigalgal.xaigateway.gateway.core.model;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryService;
import com.prodigalgal.xaigateway.gateway.core.catalog.ResolvedModelView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccountGroupBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.ModelPolicyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountGroupBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.ModelPolicyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class ModelPolicyResolver {

    private final ModelPolicyRepository modelPolicyRepository;
    private final ModelCatalogQueryService modelCatalogQueryService;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final UpstreamAccountGroupRepository upstreamAccountGroupRepository;
    private final UpstreamAccountRepository upstreamAccountRepository;
    private final UpstreamSiteProfileRepository upstreamSiteProfileRepository;
    private final DistributedKeyAccountGroupBindingRepository accountGroupBindingRepository;
    private final ModelPolicyRuntimeStateService runtimeStateService;
    private final ObjectMapper objectMapper;

    public ModelPolicyResolver(
            ModelPolicyRepository modelPolicyRepository,
            ModelCatalogQueryService modelCatalogQueryService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            UpstreamAccountGroupRepository upstreamAccountGroupRepository,
            UpstreamAccountRepository upstreamAccountRepository,
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            DistributedKeyAccountGroupBindingRepository accountGroupBindingRepository,
            ModelPolicyRuntimeStateService runtimeStateService,
            ObjectMapper objectMapper) {
        this.modelPolicyRepository = modelPolicyRepository;
        this.modelCatalogQueryService = modelCatalogQueryService;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.upstreamAccountGroupRepository = upstreamAccountGroupRepository;
        this.upstreamAccountRepository = upstreamAccountRepository;
        this.upstreamSiteProfileRepository = upstreamSiteProfileRepository;
        this.accountGroupBindingRepository = accountGroupBindingRepository;
        this.runtimeStateService = runtimeStateService;
        this.objectMapper = objectMapper;
    }

    public boolean hasEnabledPolicies() {
        return !modelPolicyRepository.findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc().isEmpty();
    }

    public ModelPolicyResolvedModel resolveRequestedModel(
            DistributedKeyView distributedKey,
            String protocol,
            String requestedModel) {
        String normalizedProtocol = normalizeProtocol(protocol);
        String requestedKey = ModelIdNormalizer.normalize(requestedModel);
        List<ModelPolicyEntity> mappings = mappingPolicies(distributedKey, normalizedProtocol, requestedKey);
        if (!mappings.isEmpty()) {
            List<CatalogCandidateView> candidates = new ArrayList<>();
            LinkedHashSet<String> upstreamKeys = new LinkedHashSet<>();
            for (ModelPolicyEntity mapping : mappings) {
                String upstreamKey = normalizedUpstreamKey(mapping, requestedKey);
                if (upstreamKey == null || upstreamKey.isBlank() || !upstreamKeys.add(upstreamKey)) {
                    continue;
                }
                candidates.addAll(modelCatalogQueryService.listCandidatesByModelKey(upstreamKey));
            }
            List<CatalogCandidateView> distinctCandidates = distinctCandidates(candidates);
            if (!distinctCandidates.isEmpty()) {
                String defaultResolved = distinctCandidates.get(0).modelKey();
                return new ModelPolicyResolvedModel(
                        requestedModel,
                        displayPublicModel(mappings.get(0), requestedModel),
                        defaultResolved,
                        requestedKey,
                        true,
                        distinctCandidates,
                        mappings.stream()
                                .map(policy -> "model_policy_mapping:" + policy.getScopeType() + ":" + policy.getId())
                                .toList()
                );
            }
        }

        ResolvedModelView resolved = modelCatalogQueryService
                .resolveRequestedModel(requestedModel, normalizedProtocol)
                .orElseThrow(() -> new IllegalArgumentException("当前请求模型没有可用候选。"));
        return new ModelPolicyResolvedModel(
                resolved.requestedModel(),
                resolved.publicModel(),
                resolved.resolvedModelKey(),
                resolved.resolvedModelKey(),
                resolved.alias(),
                resolved.candidates(),
                resolved.alias() ? List.of("legacy_model_alias") : List.of("catalog_direct")
        );
    }

    public ModelPolicyCandidateDecision evaluateCandidate(
            DistributedKeyView distributedKey,
            String protocol,
            String requestedModel,
            String publicModel,
            RouteCandidateView candidate) {
        String normalizedProtocol = normalizeProtocol(protocol);
        String requestedKey = ModelIdNormalizer.normalize(requestedModel);
        String publicKey = ModelIdNormalizer.normalize(publicModel);
        String upstreamKey = candidate.candidate().modelKey();
        List<String> modelKeys = modelKeys(requestedKey, publicKey, upstreamKey);
        List<String> exclusions = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        UpstreamCredentialEntity credential = upstreamCredentialRepository.findById(candidate.candidate().credentialId())
                .orElse(null);
        UpstreamSiteProfileEntity siteProfile = candidate.candidate().siteProfileId() == null
                ? null
                : upstreamSiteProfileRepository.findById(candidate.candidate().siteProfileId()).orElse(null);
        List<UpstreamAccountGroupEntity> groups = accountGroups(distributedKey, candidate, credential);
        List<UpstreamAccountEntity> accounts = accounts(groups);

        applyLegacyAllowlists(credential, groups, accounts, modelKeys, exclusions);

        ScopeContext context = new ScopeContext(distributedKey, credential, siteProfile, groups, accounts);
        List<ModelPolicyEntity> applicablePolicies = applicablePolicies(context, normalizedProtocol);
        for (ModelPolicyScopeType scopeType : ModelPolicyScopeType.values()) {
            List<ModelPolicyEntity> scopePolicies = applicablePolicies.stream()
                    .filter(policy -> policy.getScopeType() == scopeType)
                    .toList();
            applyScopePolicies(scopeType, scopePolicies, modelKeys, upstreamKey, exclusions, notes);
        }

        RuntimeAdjustment adjustment = applyRuntimePolicies(applicablePolicies, credential, accounts, upstreamKey, exclusions, notes);
        RouteCandidateView adjustedCandidate = adjustCandidate(candidate, adjustment);
        return new ModelPolicyCandidateDecision(
                adjustedCandidate,
                exclusions.isEmpty(),
                List.copyOf(exclusions),
                List.copyOf(notes)
        );
    }

    public void recordSuccess(RouteSelectionResult selectionResult) {
        if (selectionResult == null || selectionResult.selectedCandidate() == null) {
            return;
        }
        RouteCandidateView candidate = selectionResult.selectedCandidate();
        UpstreamCredentialEntity credential = upstreamCredentialRepository.findById(candidate.candidate().credentialId())
                .orElse(null);
        UpstreamSiteProfileEntity siteProfile = candidate.candidate().siteProfileId() == null
                ? null
                : upstreamSiteProfileRepository.findById(candidate.candidate().siteProfileId()).orElse(null);
        DistributedKeyView distributedKey = new DistributedKeyView(
                selectionResult.distributedKeyId(),
                null,
                selectionResult.distributedKeyPrefix(),
                null,
                List.of(),
                List.of(),
                List.of()
        );
        List<UpstreamAccountGroupEntity> groups = accountGroups(distributedKey, candidate, credential);
        ScopeContext context = new ScopeContext(distributedKey, credential, siteProfile, groups, accounts(groups));
        for (ModelPolicyEntity policy : applicablePolicies(context, selectionResult.protocol())) {
            JsonNode runtimePolicy = parse(policy.getRuntimePolicyJson());
            int rpm = firstPositiveInt(runtimePolicy, 0, "rpm", "requestsPerMinute");
            JsonNode rateLimit = runtimePolicy == null ? null : runtimePolicy.path("rateLimit");
            if (rpm <= 0 && rateLimit != null && !rateLimit.isMissingNode()) {
                rpm = firstPositiveInt(rateLimit, 0, "rpm", "requestsPerMinute");
            }
            if (rpm > 0 && modelMatches(policy, modelKeys(selectionResult.requestedModel(), selectionResult.publicModel(), candidate.candidate().modelKey()), candidate.candidate().modelKey())) {
                runtimeStateService.recordSuccess(policy.getId(), candidate.candidate().credentialId(), candidate.candidate().modelKey());
            }
        }
    }

    public List<ModelPolicyConflict> detectConflicts() {
        List<ModelPolicyEntity> policies = modelPolicyRepository.findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc();
        List<ModelPolicyConflict> conflicts = new ArrayList<>();
        Map<String, List<ModelPolicyEntity>> duplicateGroups = policies.stream()
                .collect(Collectors.groupingBy(policy -> policy.getScopeType()
                        + "|" + Objects.toString(policy.getScopeId(), "")
                        + "|" + Objects.toString(policy.getScopeRef(), "")
                        + "|" + policy.getPublicModelKey()
                        + "|" + Objects.toString(policy.getUpstreamModelKey(), "")
                        + "|" + normalizedKind(policy)));
        duplicateGroups.values().stream()
                .filter(group -> group.size() > 1)
                .forEach(group -> group.forEach(policy -> conflicts.add(new ModelPolicyConflict(
                        "ERROR",
                        "duplicate_policy",
                        "同一 scope/public/upstream/kind 存在重复启用策略。",
                        policy.getId()
                ))));

        for (ModelPolicyEntity policy : policies) {
            JsonNode runtime = parse(policy.getRuntimePolicyJson());
            int canaryWeight = canaryWeight(policy, runtime);
            if (canaryWeight < 0 || canaryWeight > 10_000) {
                conflicts.add(new ModelPolicyConflict("ERROR", "invalid_canary_weight", "灰度权重必须位于 0-10000。", policy.getId()));
            }
            JsonNode fallbackChain = runtime == null ? null : runtime.path("fallbackChain");
            if (fallbackChain != null && fallbackChain.isArray()) {
                for (JsonNode item : fallbackChain) {
                    String modelKey = ModelIdNormalizer.normalize(item.asText(null));
                    if (modelKey != null && !modelKey.isBlank() && modelCatalogQueryService.listCandidatesByModelKey(modelKey).isEmpty()) {
                        conflicts.add(new ModelPolicyConflict("WARN", "fallback_target_unreachable", "fallback chain 目标模型没有 catalog 候选：" + modelKey, policy.getId()));
                    }
                }
            }
            String upstreamKey = policy.getUpstreamModelKey();
            if (upstreamKey != null && !upstreamKey.isBlank() && modelCatalogQueryService.listCandidatesByModelKey(upstreamKey).isEmpty()) {
                conflicts.add(new ModelPolicyConflict("WARN", "mapping_target_unreachable", "映射目标没有 catalog 候选：" + upstreamKey, policy.getId()));
            }
        }
        return conflicts;
    }

    private List<ModelPolicyEntity> mappingPolicies(DistributedKeyView distributedKey, String protocol, String requestedKey) {
        AccessibleScopes scopes = accessibleScopes(distributedKey);
        List<ModelPolicyEntity> policies = modelPolicyRepository.findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc().stream()
                .filter(policy -> protocolMatches(policy, protocol))
                .filter(policy -> requestedKey != null && requestedKey.equals(policy.getPublicModelKey()))
                .filter(policy -> !policy.isDeny())
                .filter(policy -> policy.getUpstreamModelKey() != null && !policy.getUpstreamModelKey().isBlank())
                .filter(policy -> scopeAccessible(policy, scopes))
                .sorted(policyComparator())
                .toList();
        if (policies.isEmpty()) {
            return List.of();
        }
        int bestRank = policies.stream().mapToInt(policy -> scopeRank(policy.getScopeType())).min().orElse(Integer.MAX_VALUE);
        return policies.stream()
                .filter(policy -> scopeRank(policy.getScopeType()) == bestRank)
                .toList();
    }

    private List<ModelPolicyEntity> applicablePolicies(ScopeContext context, String protocol) {
        List<ModelPolicyEntity> all = new ArrayList<>();
        if (context.distributedKey() != null && context.distributedKey().id() != null) {
            all.addAll(modelPolicyRepository.findAllByScopeTypeAndScopeIdAndEnabledTrueOrderByPriorityAscCreatedAtAsc(
                    ModelPolicyScopeType.DISTRIBUTED_KEY,
                    context.distributedKey().id()
            ));
        }
        addByIds(all, ModelPolicyScopeType.ACCOUNT_GROUP, context.groups().stream().map(UpstreamAccountGroupEntity::getId).toList());
        addByIds(all, ModelPolicyScopeType.ACCOUNT, context.accounts().stream().map(UpstreamAccountEntity::getId).toList());
        if (context.credential() != null && context.credential().getId() != null) {
            all.addAll(modelPolicyRepository.findAllByScopeTypeAndScopeIdAndEnabledTrueOrderByPriorityAscCreatedAtAsc(
                    ModelPolicyScopeType.CREDENTIAL,
                    context.credential().getId()
            ));
        }
        if (context.siteProfile() != null && context.siteProfile().getId() != null) {
            all.addAll(modelPolicyRepository.findAllByScopeTypeAndScopeIdAndEnabledTrueOrderByPriorityAscCreatedAtAsc(
                    ModelPolicyScopeType.SITE_PROFILE,
                    context.siteProfile().getId()
            ));
        }
        String vendorCode = context.siteProfile() == null ? null : normalizeRef(context.siteProfile().getVendorCode());
        if (vendorCode != null) {
            all.addAll(modelPolicyRepository.findAllByScopeTypeAndScopeRefInAndEnabledTrueOrderByPriorityAscCreatedAtAsc(
                    ModelPolicyScopeType.VENDOR,
                    List.of(vendorCode)
            ));
        }
        return all.stream()
                .filter(policy -> protocolMatches(policy, protocol))
                .sorted(policyComparator())
                .toList();
    }

    private void applyScopePolicies(
            ModelPolicyScopeType scopeType,
            List<ModelPolicyEntity> scopePolicies,
            List<String> modelKeys,
            String upstreamKey,
            List<String> exclusions,
            List<String> notes) {
        if (scopePolicies.isEmpty()) {
            return;
        }
        List<ModelPolicyEntity> denyPolicies = scopePolicies.stream()
                .filter(ModelPolicyEntity::isDeny)
                .filter(policy -> modelMatches(policy, modelKeys, upstreamKey))
                .toList();
        if (!denyPolicies.isEmpty()) {
            exclusions.add("model_policy_denied:" + scopeType.name().toLowerCase(Locale.ROOT));
            denyPolicies.forEach(policy -> notes.add("deny_policy=" + policy.getId()));
            return;
        }

        List<ModelPolicyEntity> allowPolicies = scopePolicies.stream()
                .filter(policy -> !policy.isDeny())
                .filter(policy -> List.of("ALLOW", "MAP", "DISCOVERED").contains(normalizedKind(policy)))
                .toList();
        if (allowPolicies.isEmpty()) {
            return;
        }
        boolean matched = allowPolicies.stream().anyMatch(policy -> modelMatches(policy, modelKeys, upstreamKey));
        if (!matched) {
            exclusions.add("model_policy_not_allowed:" + scopeType.name().toLowerCase(Locale.ROOT));
        } else {
            allowPolicies.stream()
                    .filter(policy -> modelMatches(policy, modelKeys, upstreamKey))
                    .findFirst()
                    .ifPresent(policy -> notes.add("allow_policy=" + policy.getId()));
        }
    }

    private RuntimeAdjustment applyRuntimePolicies(
            List<ModelPolicyEntity> policies,
            UpstreamCredentialEntity credential,
            List<UpstreamAccountEntity> accounts,
            String upstreamKey,
            List<String> exclusions,
            List<String> notes) {
        int priorityOffset = 0;
        int weightOverride = 0;
        for (ModelPolicyEntity policy : policies) {
            JsonNode runtimePolicy = parse(policy.getRuntimePolicyJson());
            if (runtimePolicy == null) {
                continue;
            }
            int rpm = firstPositiveInt(runtimePolicy, 0, "rpm", "requestsPerMinute");
            JsonNode rateLimit = runtimePolicy.path("rateLimit");
            if (rpm <= 0 && !rateLimit.isMissingNode()) {
                rpm = firstPositiveInt(rateLimit, 0, "rpm", "requestsPerMinute");
            }
            if (rpm > 0 && credential != null
                    && !runtimeStateService.requestRateAvailable(policy.getId(), credential.getId(), upstreamKey, rpm)) {
                exclusions.add("model_policy_rate_limited");
                notes.add("rate_limit_policy=" + policy.getId());
            }

            JsonNode quota = runtimePolicy.path("quota");
            long maxRequests = firstPositiveLong(quota, firstPositiveLong(runtimePolicy, 0, "maxRequests"), "maxRequests", "requestLimit");
            long maxTokens = firstPositiveLong(quota, firstPositiveLong(runtimePolicy, 0, "maxTokens"), "maxTokens", "tokenLimit");
            if (credential != null && maxRequests > 0 && credential.getTotalRequestCount() >= maxRequests) {
                exclusions.add("model_policy_request_quota_exhausted");
                notes.add("quota_policy=" + policy.getId());
            }
            if (credential != null && maxTokens > 0 && credential.getTotalTokenCount() >= maxTokens) {
                exclusions.add("model_policy_token_quota_exhausted");
                notes.add("quota_policy=" + policy.getId());
            }

            if (!accounts.isEmpty() && accounts.stream().allMatch(this::accountQuotaExhausted)) {
                exclusions.add("account_model_quota_exhausted");
            }

            JsonNode health = runtimePolicy.path("health");
            double minSuccessRate = firstPositiveDouble(health, firstPositiveDouble(runtimePolicy, 0D, "minSuccessRate"), "minSuccessRate");
            long minRequests = firstPositiveLong(health, 10L, "minRequests");
            if (credential != null && minSuccessRate > 0D && credential.getTotalRequestCount() >= minRequests) {
                double successRate = credential.getTotalRequestCount() == 0
                        ? 1D
                        : credential.getSuccessfulRequestCount() / (double) credential.getTotalRequestCount();
                if (successRate < minSuccessRate) {
                    exclusions.add("model_policy_health_pruned");
                    notes.add("health_policy=" + policy.getId());
                }
            }

            FallbackAdjustment fallback = fallbackAdjustment(runtimePolicy, upstreamKey);
            priorityOffset += fallback.priorityOffset();
            fallback.note().ifPresent(notes::add);

            int canaryWeight = canaryWeight(policy, runtimePolicy);
            if (canaryWeight > 0) {
                weightOverride = Math.max(weightOverride, canaryWeight);
                notes.add("canary_weight=" + canaryWeight);
            }
        }
        return new RuntimeAdjustment(priorityOffset, weightOverride);
    }

    private RouteCandidateView adjustCandidate(RouteCandidateView candidate, RuntimeAdjustment adjustment) {
        int priority = Math.max(1, candidate.bindingPriority() + adjustment.priorityOffset());
        int weight = adjustment.weightOverride() > 0 ? adjustment.weightOverride() : candidate.bindingWeight();
        return new RouteCandidateView(
                candidate.candidate(),
                candidate.bindingId(),
                priority,
                weight,
                candidate.capabilityLevel(),
                candidate.capabilityRank()
        );
    }

    private FallbackAdjustment fallbackAdjustment(JsonNode runtimePolicy, String upstreamKey) {
        JsonNode fallbackChain = runtimePolicy.path("fallbackChain");
        if (!fallbackChain.isArray()) {
            return new FallbackAdjustment(0, Optional.empty());
        }
        int index = 0;
        for (JsonNode item : fallbackChain) {
            String modelKey = ModelIdNormalizer.normalize(item.asText(null));
            if (modelKey != null && modelKey.equals(upstreamKey)) {
                return new FallbackAdjustment(index * 20, Optional.of("fallback_chain_index=" + index));
            }
            index++;
        }
        return new FallbackAdjustment(1000, Optional.of("fallback_chain_unlisted"));
    }

    private void applyLegacyAllowlists(
            UpstreamCredentialEntity credential,
            List<UpstreamAccountGroupEntity> groups,
            List<UpstreamAccountEntity> accounts,
            List<String> modelKeys,
            List<String> exclusions) {
        if (credential != null && !allowsAny(credential.getSupportedModels(), modelKeys)) {
            exclusions.add("credential_model_not_allowed");
        }
        List<List<String>> groupModels = groups.stream()
                .map(UpstreamAccountGroupEntity::getSupportedModels)
                .filter(models -> models != null && !models.isEmpty())
                .toList();
        if (!groupModels.isEmpty() && groupModels.stream().noneMatch(models -> allowsAny(models, modelKeys))) {
            exclusions.add("account_group_model_not_allowed");
        }
        List<List<String>> accountModels = accounts.stream()
                .map(UpstreamAccountEntity::getSupportedModels)
                .filter(models -> models != null && !models.isEmpty())
                .toList();
        if (!accountModels.isEmpty() && accountModels.stream().noneMatch(models -> allowsAny(models, modelKeys))) {
            exclusions.add("account_model_not_allowed");
        }
    }

    private boolean allowsAny(List<String> allowlist, List<String> modelKeys) {
        if (allowlist == null || allowlist.isEmpty()) {
            return true;
        }
        Set<String> normalized = allowlist.stream()
                .map(ModelIdNormalizer::normalize)
                .collect(Collectors.toSet());
        return modelKeys.stream().anyMatch(normalized::contains);
    }

    private List<UpstreamAccountGroupEntity> accountGroups(
            DistributedKeyView distributedKey,
            RouteCandidateView candidate,
            UpstreamCredentialEntity credential) {
        LinkedHashMap<Long, UpstreamAccountGroupEntity> groups = new LinkedHashMap<>();
        if (credential != null && credential.getGroupId() != null) {
            upstreamAccountGroupRepository.findById(credential.getGroupId()).ifPresent(group -> groups.put(group.getId(), group));
        }
        Set<Long> bindingGroupIds = boundAccountGroupIds(distributedKey, candidate == null ? null : candidate.candidate().providerType());
        if (!bindingGroupIds.isEmpty()) {
            upstreamAccountGroupRepository.findAllById(bindingGroupIds).forEach(group -> groups.put(group.getId(), group));
        }
        return List.copyOf(groups.values());
    }

    private Set<Long> boundAccountGroupIds(DistributedKeyView distributedKey, ProviderType providerType) {
        if (distributedKey == null || distributedKey.id() == null) {
            return Set.of();
        }
        List<DistributedKeyAccountGroupBindingEntity> bindings = providerType == null
                ? accountGroupBindingRepository.findAllByDistributedKey_IdAndActiveTrueOrderByPriorityAscCreatedAtAsc(distributedKey.id())
                : accountGroupBindingRepository.findAllByDistributedKey_IdAndProviderTypeAndActiveTrueOrderByPriorityAscCreatedAtAsc(distributedKey.id(), providerType);
        return bindings.stream()
                .map(DistributedKeyAccountGroupBindingEntity::getGroup)
                .filter(Objects::nonNull)
                .map(UpstreamAccountGroupEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<UpstreamAccountEntity> accounts(List<UpstreamAccountGroupEntity> groups) {
        if (groups.isEmpty()) {
            return List.of();
        }
        return groups.stream()
                .flatMap(group -> upstreamAccountRepository
                        .findAllByGroup_IdAndActiveTrueAndFrozenFalseAndHealthyTrueOrderByUpdatedAtDesc(group.getId())
                        .stream())
                .distinct()
                .toList();
    }

    private void addByIds(List<ModelPolicyEntity> target, ModelPolicyScopeType scopeType, Collection<Long> ids) {
        List<Long> values = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (!values.isEmpty()) {
            target.addAll(modelPolicyRepository.findAllByScopeTypeAndScopeIdInAndEnabledTrueOrderByPriorityAscCreatedAtAsc(scopeType, values));
        }
    }

    private boolean modelMatches(ModelPolicyEntity policy, List<String> modelKeys, String upstreamKey) {
        if (policy.getPublicModelKey() == null || !modelKeys.contains(policy.getPublicModelKey())) {
            return false;
        }
        String policyUpstream = policy.getUpstreamModelKey();
        return policyUpstream == null || policyUpstream.isBlank() || policyUpstream.equals(upstreamKey);
    }

    private List<String> modelKeys(String requestedKey, String publicKey, String upstreamKey) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (requestedKey != null && !requestedKey.isBlank()) {
            values.add(ModelIdNormalizer.normalize(requestedKey));
        }
        if (publicKey != null && !publicKey.isBlank()) {
            values.add(ModelIdNormalizer.normalize(publicKey));
        }
        if (upstreamKey != null && !upstreamKey.isBlank()) {
            values.add(ModelIdNormalizer.normalize(upstreamKey));
        }
        return List.copyOf(values);
    }

    private List<CatalogCandidateView> distinctCandidates(List<CatalogCandidateView> candidates) {
        return candidates.stream()
                .collect(Collectors.toMap(
                        candidate -> candidate.credentialId() + ":" + candidate.modelKey(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
    }

    private boolean protocolMatches(ModelPolicyEntity policy, String protocol) {
        return policy.getSupportedProtocols() == null
                || policy.getSupportedProtocols().isEmpty()
                || policy.getSupportedProtocols().stream()
                .map(this::normalizeProtocol)
                .anyMatch(protocol::equals);
    }

    private String normalizedUpstreamKey(ModelPolicyEntity policy, String fallback) {
        String upstream = policy.getUpstreamModelKey();
        return upstream == null || upstream.isBlank() ? fallback : upstream;
    }

    private String displayPublicModel(ModelPolicyEntity policy, String fallback) {
        return policy.getPublicModel() == null || policy.getPublicModel().isBlank() ? fallback : policy.getPublicModel();
    }

    private Comparator<ModelPolicyEntity> policyComparator() {
        return Comparator
                .comparingInt((ModelPolicyEntity policy) -> scopeRank(policy.getScopeType()))
                .thenComparingInt(ModelPolicyEntity::getPriority)
                .thenComparing(ModelPolicyEntity::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private int scopeRank(ModelPolicyScopeType scopeType) {
        if (scopeType == null) {
            return 99;
        }
        return switch (scopeType) {
            case DISTRIBUTED_KEY -> 10;
            case ACCOUNT_GROUP -> 20;
            case ACCOUNT, CREDENTIAL -> 30;
            case SITE_PROFILE -> 40;
            case VENDOR -> 50;
        };
    }

    private String normalizedKind(ModelPolicyEntity policy) {
        return policy.getPolicyKind() == null || policy.getPolicyKind().isBlank()
                ? (policy.isDeny() ? "DENY" : "ALLOW")
                : policy.getPolicyKind().trim().toUpperCase(Locale.ROOT);
    }

    private boolean accountQuotaExhausted(UpstreamAccountEntity account) {
        Long remainingRequests = account.getQuotaRemainingRequests();
        Long remainingTokens = account.getQuotaRemainingTokens();
        boolean requestsExhausted = remainingRequests != null && remainingRequests <= 0;
        boolean tokensExhausted = remainingTokens != null && remainingTokens <= 0;
        return requestsExhausted || tokensExhausted;
    }

    private int canaryWeight(ModelPolicyEntity policy, JsonNode runtimePolicy) {
        if (runtimePolicy == null) {
            return 0;
        }
        JsonNode canary = runtimePolicy.path("canary");
        return firstPositiveInt(canary, firstPositiveInt(runtimePolicy, 0, "canaryWeight"), "weight", "trafficWeight");
    }

    private AccessibleScopes accessibleScopes(DistributedKeyView distributedKey) {
        if (distributedKey == null) {
            return AccessibleScopes.empty();
        }
        Set<Long> credentialIds = distributedKey.bindings() == null
                ? Set.of()
                : distributedKey.bindings().stream()
                .map(binding -> binding.credentialId())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<UpstreamCredentialEntity> credentials = credentialIds.isEmpty()
                ? List.of()
                : upstreamCredentialRepository.findAllByIdInAndDeletedFalse(credentialIds);
        Set<Long> siteProfileIds = credentials.stream()
                .map(UpstreamCredentialEntity::getSiteProfileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<UpstreamSiteProfileEntity> siteProfiles = siteProfileIds.isEmpty()
                ? List.of()
                : upstreamSiteProfileRepository.findAllById(siteProfileIds);
        Set<String> vendorRefs = siteProfiles.stream()
                .map(UpstreamSiteProfileEntity::getVendorCode)
                .map(this::normalizeRef)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> groupIds = boundAccountGroupIds(distributedKey, null);
        List<UpstreamAccountGroupEntity> groups = groupIds.isEmpty()
                ? List.of()
                : upstreamAccountGroupRepository.findAllById(groupIds);
        Set<Long> accountIds = accounts(groups).stream()
                .map(UpstreamAccountEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new AccessibleScopes(
                distributedKey.id(),
                credentialIds,
                siteProfileIds,
                vendorRefs,
                groupIds,
                accountIds
        );
    }

    private boolean scopeAccessible(ModelPolicyEntity policy, AccessibleScopes scopes) {
        if (policy == null || scopes == null) {
            return false;
        }
        return switch (policy.getScopeType()) {
            case DISTRIBUTED_KEY -> policy.getScopeId() != null && policy.getScopeId().equals(scopes.distributedKeyId());
            case ACCOUNT_GROUP -> policy.getScopeId() != null && scopes.accountGroupIds().contains(policy.getScopeId());
            case ACCOUNT -> policy.getScopeId() != null && scopes.accountIds().contains(policy.getScopeId());
            case CREDENTIAL -> policy.getScopeId() != null && scopes.credentialIds().contains(policy.getScopeId());
            case SITE_PROFILE -> policy.getScopeId() != null && scopes.siteProfileIds().contains(policy.getScopeId());
            case VENDOR -> policy.getScopeRef() != null && scopes.vendorRefs().contains(normalizeRef(policy.getScopeRef()));
        };
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node == null || node.isNull() ? null : node;
        } catch (Exception ignored) {
            return null;
        }
    }

    private int firstPositiveInt(JsonNode node, int fallback, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.canConvertToInt() && value.asInt() > 0) {
                return value.asInt();
            }
        }
        return fallback;
    }

    private long firstPositiveLong(JsonNode node, long fallback, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.canConvertToLong() && value.asLong() > 0) {
                return value.asLong();
            }
        }
        return fallback;
    }

    private double firstPositiveDouble(JsonNode node, double fallback, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isNumber() && value.asDouble() > 0D) {
                return value.asDouble();
            }
        }
        return fallback;
    }

    private String normalizeProtocol(String protocol) {
        return protocol == null ? "openai" : protocol.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRef(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private record ScopeContext(
            DistributedKeyView distributedKey,
            UpstreamCredentialEntity credential,
            UpstreamSiteProfileEntity siteProfile,
            List<UpstreamAccountGroupEntity> groups,
            List<UpstreamAccountEntity> accounts
    ) {
        ScopeContext {
            groups = groups == null ? List.of() : List.copyOf(groups);
            accounts = accounts == null ? List.of() : List.copyOf(accounts);
        }
    }

    private record RuntimeAdjustment(int priorityOffset, int weightOverride) {
    }

    private record FallbackAdjustment(int priorityOffset, Optional<String> note) {
    }

    private record AccessibleScopes(
            Long distributedKeyId,
            Set<Long> credentialIds,
            Set<Long> siteProfileIds,
            Set<String> vendorRefs,
            Set<Long> accountGroupIds,
            Set<Long> accountIds
    ) {
        private static AccessibleScopes empty() {
            return new AccessibleScopes(null, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        }
    }
}
