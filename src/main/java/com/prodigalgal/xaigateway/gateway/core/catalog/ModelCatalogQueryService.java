package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.prodigalgal.xaigateway.gateway.core.alias.ModelAliasQueryService;
import com.prodigalgal.xaigateway.gateway.core.alias.ModelAliasRuleView;
import com.prodigalgal.xaigateway.gateway.core.alias.ModelAliasView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.infra.persistence.entity.CredentialModelCatalogEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.CredentialModelCatalogRepository;
import java.util.Comparator;
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

    private final CredentialModelCatalogRepository credentialModelCatalogRepository;
    private final ModelAliasQueryService modelAliasQueryService;

    public ModelCatalogQueryService(
            CredentialModelCatalogRepository credentialModelCatalogRepository,
            ModelAliasQueryService modelAliasQueryService) {
        this.credentialModelCatalogRepository = credentialModelCatalogRepository;
        this.modelAliasQueryService = modelAliasQueryService;
    }

    public List<CatalogCandidateView> listCandidatesByModelKey(String modelKey) {
        return credentialModelCatalogRepository.findAllByModelKeyAndActiveTrue(modelKey).stream()
                .map(this::toCandidateView)
                .sorted(Comparator.comparing(CatalogCandidateView::credentialId))
                .toList();
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

        List<CatalogCandidateView> accessibleCandidates = credentialModelCatalogRepository
                .findAllByCredentialIdInAndActiveTrue(boundCredentialIds)
                .stream()
                .map(this::toCandidateView)
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
                    false
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
                models.putIfAbsent(alias.aliasName(), new GatewayPublicModelView(
                        alias.aliasName(),
                        alias.aliasKey(),
                        true
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

    private CatalogCandidateView toCandidateView(CredentialModelCatalogEntity entity) {
        return new CatalogCandidateView(
                entity.getCredential().getId(),
                entity.getCredential().getCredentialName(),
                entity.getCredential().getProviderType(),
                entity.getCredential().getBaseUrl(),
                entity.getModelName(),
                entity.getModelKey(),
                entity.getSupportedProtocols(),
                entity.isSupportsChat(),
                entity.isSupportsEmbeddings(),
                entity.isSupportsCache(),
                entity.isSupportsThinking(),
                entity.isSupportsVisibleReasoning(),
                entity.isSupportsReasoningReuse(),
                entity.getReasoningTransport()
        );
    }
}
