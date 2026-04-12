package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.prodigalgal.xaigateway.gateway.core.alias.ModelAliasQueryService;
import com.prodigalgal.xaigateway.gateway.core.alias.ModelAliasRuleView;
import com.prodigalgal.xaigateway.gateway.core.alias.ModelAliasView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteModelCapabilityEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteModelCapabilityRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ModelCatalogQueryService {

    private final SiteModelCapabilityRepository siteModelCapabilityRepository;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final ModelAliasQueryService modelAliasQueryService;
    private final SiteCapabilityTruthService siteCapabilityTruthService;

    public ModelCatalogQueryService(
            SiteModelCapabilityRepository siteModelCapabilityRepository,
            UpstreamCredentialRepository upstreamCredentialRepository,
            ModelAliasQueryService modelAliasQueryService,
            SiteCapabilityTruthService siteCapabilityTruthService) {
        this.siteModelCapabilityRepository = siteModelCapabilityRepository;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.modelAliasQueryService = modelAliasQueryService;
        this.siteCapabilityTruthService = siteCapabilityTruthService;
    }

    public List<CatalogCandidateView> listCandidatesByModelKey(String modelKey) {
        List<SiteModelCapabilityEntity> capabilities = siteModelCapabilityRepository.findAllByModelKeyAndActiveTrue(modelKey);
        return expandCandidates(capabilities);
    }

    public List<GatewayPublicModelView> listAccessiblePublicModels(DistributedKeyView distributedKey, String protocol) {
        String normalizedProtocol = normalizeProtocol(protocol);
        if (!isProtocolAllowed(distributedKey, normalizedProtocol)) {
            throw new IllegalArgumentException("当前 DistributedKey 不允许访问该协议。");
        }

        Set<Long> boundCredentialIds = distributedKey.bindings().stream()
                .map(DistributedCredentialBindingView::credentialId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (boundCredentialIds.isEmpty()) {
            return List.of();
        }

        List<UpstreamCredentialEntity> credentials = upstreamCredentialRepository.findAllByIdInAndDeletedFalse(boundCredentialIds);
        Set<Long> siteProfileIds = credentials.stream()
                .map(UpstreamCredentialEntity::getSiteProfileId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (siteProfileIds.isEmpty()) {
            return List.of();
        }

        List<CatalogCandidateView> accessibleCandidates = expandCandidates(
                siteModelCapabilityRepository.findAllBySiteProfile_IdInAndActiveTrue(siteProfileIds),
                credentials
        ).stream()
                .filter(candidate -> actualCapabilityLevel(candidate) != InteropCapabilityLevel.UNSUPPORTED)
                .filter(candidate -> candidate.supportedProtocols().contains(normalizedProtocol))
                .sorted(Comparator.comparing(CatalogCandidateView::modelKey).thenComparing(CatalogCandidateView::credentialId))
                .toList();

        if (accessibleCandidates.isEmpty()) {
            return List.of();
        }

        Map<String, GatewayPublicModelView> models = new LinkedHashMap<>();
        for (CatalogCandidateView candidate : accessibleCandidates) {
            if (!isModelAllowed(distributedKey, candidate.modelKey())) {
                continue;
            }
            models.putIfAbsent(candidate.modelKey(), new GatewayPublicModelView(
                    candidate.modelKey(),
                    candidate.modelKey(),
                    false,
                    candidate.siteProfileId(),
                    candidate.providerFamily(),
                    candidate.siteKind(),
                    actualCapabilityLevel(candidate),
                    siteCapabilityTruthService.capabilityLevel(candidate, InteropFeature.CHAT_TEXT) != InteropCapabilityLevel.UNSUPPORTED,
                    siteCapabilityTruthService.capabilityLevel(candidate, InteropFeature.EMBEDDINGS) != InteropCapabilityLevel.UNSUPPORTED
            ));
        }

        Map<String, List<CatalogCandidateView>> candidatesByModelKey = accessibleCandidates.stream()
                .collect(java.util.stream.Collectors.groupingBy(CatalogCandidateView::modelKey));
        for (ModelAliasView alias : modelAliasQueryService.listEnabledAliases()) {
            if (!isModelAllowed(distributedKey, alias.aliasKey())) {
                continue;
            }

            boolean aliasReachable = alias.rules().stream()
                    .filter(rule -> rule.protocol() == null || normalizedProtocol.equalsIgnoreCase(rule.protocol()))
                    .anyMatch(rule -> candidatesByModelKey.getOrDefault(rule.targetModelKey(), List.of()).stream()
                            .anyMatch(candidate -> matchesRule(candidate, rule)));

            if (aliasReachable) {
                CatalogCandidateView representative = alias.rules().stream()
                        .filter(rule -> rule.protocol() == null || normalizedProtocol.equalsIgnoreCase(rule.protocol()))
                        .flatMap(rule -> candidatesByModelKey.getOrDefault(rule.targetModelKey(), List.of()).stream()
                                .filter(candidate -> matchesRule(candidate, rule)))
                        .findFirst()
                        .orElse(null);
                models.putIfAbsent(alias.aliasName(), new GatewayPublicModelView(
                        alias.aliasName(),
                        alias.aliasKey(),
                        true,
                        representative == null ? null : representative.siteProfileId(),
                        representative == null ? null : representative.providerFamily(),
                        representative == null ? null : representative.siteKind(),
                        representative == null ? InteropCapabilityLevel.EMULATED : actualCapabilityLevel(representative),
                        representative != null
                                && siteCapabilityTruthService.capabilityLevel(representative, InteropFeature.CHAT_TEXT) != InteropCapabilityLevel.UNSUPPORTED,
                        representative != null
                                && siteCapabilityTruthService.capabilityLevel(representative, InteropFeature.EMBEDDINGS) != InteropCapabilityLevel.UNSUPPORTED
                ));
            }
        }

        return models.values().stream()
                .sorted(Comparator.comparing(GatewayPublicModelView::publicModelId))
                .toList();
    }

    public Optional<GatewayPublicModelView> findAccessiblePublicModel(
            DistributedKeyView distributedKey,
            String protocol,
            String modelId) {
        String normalizedModelId = ModelIdNormalizer.normalize(modelId);
        return listAccessiblePublicModels(distributedKey, protocol).stream()
                .filter(model -> normalizedModelId.equals(ModelIdNormalizer.normalize(model.publicModelId())))
                .findFirst();
    }

    public Optional<ResolvedModelView> resolveRequestedModel(String requestedModel, String protocol) {
        String normalizedRequested = ModelIdNormalizer.normalize(requestedModel);

        Optional<ModelAliasView> alias = modelAliasQueryService.findEnabledAlias(normalizedRequested);
        if (alias.isPresent()) {
            List<CatalogCandidateView> aliasCandidates = alias.get().rules().stream()
                    .filter(rule -> protocol == null || protocol.equalsIgnoreCase(rule.protocol()))
                    .flatMap(rule -> listCandidatesByModelKey(rule.targetModelKey()).stream()
                            .filter(candidate -> matchesRule(candidate, rule)))
                    .distinct()
                    .toList();

            if (!aliasCandidates.isEmpty()) {
                return Optional.of(new ResolvedModelView(
                        requestedModel,
                        alias.get().aliasName(),
                        aliasCandidates.get(0).modelKey(),
                        true,
                        aliasCandidates
                ));
            }
        }

        List<CatalogCandidateView> directCandidates = listCandidatesByModelKey(normalizedRequested).stream()
                .filter(candidate -> protocol == null || candidate.supportedProtocols().contains(protocol))
                .toList();

        if (directCandidates.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new ResolvedModelView(
                requestedModel,
                normalizedRequested,
                normalizedRequested,
                false,
                directCandidates
        ));
    }

    private boolean matchesRule(CatalogCandidateView candidate, ModelAliasRuleView rule) {
        boolean providerMatches = rule.providerType() == null || rule.providerType() == candidate.providerType();
        boolean baseUrlMatches = rule.baseUrlPattern() == null
                || rule.baseUrlPattern().isBlank()
                || candidate.baseUrl().matches(rule.baseUrlPattern());
        return providerMatches && baseUrlMatches;
    }

    private boolean isProtocolAllowed(DistributedKeyView distributedKey, String protocol) {
        return distributedKey.allowedProtocols().isEmpty() || distributedKey.allowedProtocols().contains(protocol);
    }

    private boolean isModelAllowed(DistributedKeyView distributedKey, String modelNameOrAlias) {
        if (distributedKey.allowedModels().isEmpty()) {
            return true;
        }
        return distributedKey.allowedModels().contains(ModelIdNormalizer.normalize(modelNameOrAlias));
    }

    private String normalizeProtocol(String protocol) {
        return protocol == null ? "openai" : protocol.trim().toLowerCase(Locale.ROOT);
    }

    private List<CatalogCandidateView> expandCandidates(List<SiteModelCapabilityEntity> capabilities) {
        Set<Long> siteProfileIds = capabilities.stream()
                .map(item -> item.getSiteProfile().getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<UpstreamCredentialEntity> credentials = upstreamCredentialRepository.findAllBySiteProfileIdInAndDeletedFalseAndActiveTrue(siteProfileIds);
        return expandCandidates(capabilities, credentials);
    }

    private List<CatalogCandidateView> expandCandidates(
            List<SiteModelCapabilityEntity> capabilities,
            List<UpstreamCredentialEntity> credentials) {
        Map<Long, List<UpstreamCredentialEntity>> credentialsBySiteProfile = new HashMap<>();
        for (UpstreamCredentialEntity credential : credentials) {
            if (credential.getSiteProfileId() == null) {
                continue;
            }
            credentialsBySiteProfile.computeIfAbsent(credential.getSiteProfileId(), ignored -> new java.util.ArrayList<>())
                    .add(credential);
        }

        return capabilities.stream()
                .flatMap(capability -> credentialsBySiteProfile
                        .getOrDefault(capability.getSiteProfile().getId(), List.of())
                        .stream()
                        .map(credential -> toCandidateView(credential, capability)))
                .sorted(Comparator.comparing(CatalogCandidateView::credentialId))
                .toList();
    }

    private CatalogCandidateView toCandidateView(UpstreamCredentialEntity credential, SiteModelCapabilityEntity capability) {
        return new CatalogCandidateView(
                credential.getId(),
                credential.getCredentialName(),
                credential.getProviderType(),
                capability.getSiteProfile().getId(),
                capability.getSiteProfile().getProviderFamily(),
                capability.getSiteProfile().getSiteKind(),
                capability.getSiteProfile().getAuthStrategy(),
                capability.getSiteProfile().getPathStrategy(),
                capability.getSiteProfile().getErrorSchemaStrategy(),
                credential.getBaseUrl(),
                capability.getModelName(),
                capability.getModelKey(),
                capability.getSupportedProtocols(),
                capability.isSupportsChat(),
                capability.isSupportsEmbeddings(),
                capability.isSupportsCache(),
                capability.isSupportsThinking(),
                capability.isSupportsVisibleReasoning(),
                capability.isSupportsReasoningReuse(),
                capability.getReasoningTransport(),
                capability.getCapabilityLevel()
        );
    }

    private InteropCapabilityLevel actualCapabilityLevel(CatalogCandidateView candidate) {
        InteropCapabilityLevel chatLevel = siteCapabilityTruthService.capabilityLevel(candidate, InteropFeature.CHAT_TEXT);
        InteropCapabilityLevel embeddingsLevel = siteCapabilityTruthService.capabilityLevel(candidate, InteropFeature.EMBEDDINGS);
        if (chatLevel == InteropCapabilityLevel.NATIVE || embeddingsLevel == InteropCapabilityLevel.NATIVE) {
            return InteropCapabilityLevel.NATIVE;
        }
        if (chatLevel == InteropCapabilityLevel.LOSSY || embeddingsLevel == InteropCapabilityLevel.LOSSY) {
            return InteropCapabilityLevel.LOSSY;
        }
        if (chatLevel == InteropCapabilityLevel.EMULATED || embeddingsLevel == InteropCapabilityLevel.EMULATED) {
            return InteropCapabilityLevel.EMULATED;
        }
        return InteropCapabilityLevel.UNSUPPORTED;
    }
}
