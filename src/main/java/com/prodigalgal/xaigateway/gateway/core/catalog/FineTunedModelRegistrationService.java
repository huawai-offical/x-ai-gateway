package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.persistence.entity.ModelAliasEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.ModelAliasRuleEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteModelCapabilityEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ModelAliasRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.ModelAliasRuleRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteModelCapabilityRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FineTunedModelRegistrationService {

    private final SiteModelCapabilityRepository siteModelCapabilityRepository;
    private final UpstreamSiteProfileRepository upstreamSiteProfileRepository;
    private final ModelAliasRepository modelAliasRepository;
    private final ModelAliasRuleRepository modelAliasRuleRepository;

    public FineTunedModelRegistrationService(
            SiteModelCapabilityRepository siteModelCapabilityRepository,
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            ModelAliasRepository modelAliasRepository,
            ModelAliasRuleRepository modelAliasRuleRepository) {
        this.siteModelCapabilityRepository = siteModelCapabilityRepository;
        this.upstreamSiteProfileRepository = upstreamSiteProfileRepository;
        this.modelAliasRepository = modelAliasRepository;
        this.modelAliasRuleRepository = modelAliasRuleRepository;
    }

    public RegistrationResult register(
            Long siteProfileId,
            ProviderType providerType,
            String baseModelName,
            String tunedModelName,
            String requestedAliasName,
            String jobResourceKey) {
        if (siteProfileId == null || tunedModelName == null || tunedModelName.isBlank()) {
            return new RegistrationResult(null, null, List.of());
        }

        UpstreamSiteProfileEntity siteProfile = upstreamSiteProfileRepository.findById(siteProfileId)
                .orElseThrow(() -> new IllegalArgumentException("未找到 fine-tuned model 对应的站点档案。"));
        String tunedModel = tunedModelName.trim();
        String tunedModelKey = ModelIdNormalizer.normalize(tunedModel);

        SiteModelCapabilityEntity capability = existingCapability(siteProfileId, tunedModelKey)
                .orElseGet(SiteModelCapabilityEntity::new);
        SiteModelCapabilityEntity template = resolveTemplate(siteProfileId, baseModelName).orElse(null);
        applyCapability(capability, siteProfile, tunedModel, tunedModelKey, template);
        siteModelCapabilityRepository.save(capability);

        List<String> aliases = new ArrayList<>();
        String aliasName = normalizeAliasName(requestedAliasName, tunedModel);
        if (aliasName != null && !ModelIdNormalizer.normalize(aliasName).equals(tunedModelKey)) {
            aliases.add(upsertAlias(aliasName, tunedModel, providerType, siteProfile.getSiteKind(), jobResourceKey));
        }
        return new RegistrationResult(tunedModel, tunedModelKey, List.copyOf(aliases));
    }

    public CleanupResult unregister(
            Long siteProfileId,
            String tunedModelKey,
            List<String> aliasNames,
            String jobResourceKey) {
        int removedCapabilities = 0;
        int disabledAliases = 0;
        if (siteProfileId != null && tunedModelKey != null && !tunedModelKey.isBlank()) {
            List<SiteModelCapabilityEntity> capabilities = siteModelCapabilityRepository
                    .findAllBySiteProfile_IdOrderByModelKeyAsc(siteProfileId).stream()
                    .filter(item -> tunedModelKey.equals(item.getModelKey()))
                    .toList();
            if (!capabilities.isEmpty()) {
                siteModelCapabilityRepository.deleteAll(capabilities);
                removedCapabilities = capabilities.size();
            }
        }

        for (String aliasName : aliasNames == null ? List.<String>of() : aliasNames) {
            String aliasKey = ModelIdNormalizer.normalize(aliasName);
            ModelAliasEntity alias = modelAliasRepository.findAll().stream()
                    .filter(item -> aliasKey.equals(item.getAliasKey()))
                    .findFirst()
                    .orElse(null);
            if (alias == null || !shouldDisableAlias(alias, jobResourceKey)) {
                continue;
            }
            modelAliasRuleRepository.deleteAll(modelAliasRuleRepository.findAllByAliasIdAndEnabledTrueOrderByPriorityAscCreatedAtAsc(alias.getId()));
            alias.setEnabled(false);
            alias.setDescription("Auto unregistered from fine-tuning job " + jobResourceKey);
            modelAliasRepository.save(alias);
            disabledAliases++;
        }
        return new CleanupResult(removedCapabilities, disabledAliases);
    }

    private Optional<SiteModelCapabilityEntity> existingCapability(Long siteProfileId, String modelKey) {
        return siteModelCapabilityRepository.findAllBySiteProfile_IdOrderByModelKeyAsc(siteProfileId).stream()
                .filter(item -> modelKey.equals(item.getModelKey()))
                .findFirst();
    }

    private Optional<SiteModelCapabilityEntity> resolveTemplate(Long siteProfileId, String baseModelName) {
        if (baseModelName == null || baseModelName.isBlank()) {
            return Optional.empty();
        }
        String baseModelKey = ModelIdNormalizer.normalize(baseModelName);
        return siteModelCapabilityRepository.findAllBySiteProfile_IdOrderByModelKeyAsc(siteProfileId).stream()
                .filter(item -> baseModelKey.equals(item.getModelKey()))
                .findFirst()
                .or(() -> siteModelCapabilityRepository.findAllByModelKeyAndActiveTrue(baseModelKey).stream().findFirst());
    }

    private void applyCapability(
            SiteModelCapabilityEntity capability,
            UpstreamSiteProfileEntity siteProfile,
            String tunedModelName,
            String tunedModelKey,
            SiteModelCapabilityEntity template) {
        capability.setSiteProfile(siteProfile);
        capability.setModelName(tunedModelName);
        capability.setModelKey(tunedModelKey);
        capability.setSupportedProtocols(template != null ? template.getSupportedProtocols() : defaultProtocols(siteProfile.getSiteKind()));
        capability.setSupportsChat(template == null || template.isSupportsChat());
        capability.setSupportsTools(template == null || template.isSupportsTools());
        capability.setSupportsImageInput(template != null && template.isSupportsImageInput());
        capability.setSupportsEmbeddings(template != null && template.isSupportsEmbeddings());
        capability.setSupportsCache(template == null || template.isSupportsCache());
        boolean supportsThinking = template != null
                ? template.isSupportsThinking()
                : tunedModelName.toLowerCase(Locale.ROOT).contains("2.5");
        capability.setSupportsThinking(supportsThinking);
        capability.setSupportsVisibleReasoning(template != null ? template.isSupportsVisibleReasoning() : supportsThinking);
        capability.setSupportsReasoningReuse(template != null ? template.isSupportsReasoningReuse() : supportsThinking);
        capability.setReasoningTransport(template != null ? template.getReasoningTransport() : defaultReasoningTransport(siteProfile.getSiteKind(), supportsThinking));
        capability.setCapabilityLevel(template != null ? template.getCapabilityLevel() : InteropCapabilityLevel.NATIVE);
        capability.setActive(true);
        capability.setSourceRefreshedAt(Instant.now());
    }

    private String upsertAlias(
            String aliasName,
            String tunedModelName,
            ProviderType providerType,
            UpstreamSiteKind siteKind,
            String jobResourceKey) {
        String aliasKey = ModelIdNormalizer.normalize(aliasName);
        ModelAliasEntity alias = modelAliasRepository.findAll().stream()
                .filter(item -> aliasKey.equals(item.getAliasKey()))
                .findFirst()
                .orElseGet(ModelAliasEntity::new);
        alias.setAliasName(aliasName);
        alias.setAliasKey(aliasKey);
        alias.setEnabled(true);
        alias.setDescription("Auto registered from fine-tuning job " + jobResourceKey);
        ModelAliasEntity savedAlias = modelAliasRepository.save(alias);

        List<ModelAliasRuleEntity> existingRules = modelAliasRuleRepository
                .findAllByAliasIdAndEnabledTrueOrderByPriorityAscCreatedAtAsc(savedAlias.getId());
        if (!existingRules.isEmpty()) {
            modelAliasRuleRepository.deleteAll(existingRules);
        }

        List<ModelAliasRuleEntity> rules = defaultProtocols(siteKind).stream()
                .map(protocol -> {
                    ModelAliasRuleEntity rule = new ModelAliasRuleEntity();
                    rule.setAlias(savedAlias);
                    rule.setProtocol(protocol);
                    rule.setTargetModelName(tunedModelName);
                    rule.setTargetModelKey(ModelIdNormalizer.normalize(tunedModelName));
                    rule.setProviderType(providerType);
                    rule.setPriority(100);
                    rule.setEnabled(true);
                    rule.setDescription("Auto generated from fine-tuning job " + jobResourceKey);
                    return rule;
                })
                .toList();
        modelAliasRuleRepository.saveAll(rules);
        return aliasName;
    }

    private List<String> defaultProtocols(UpstreamSiteKind siteKind) {
        if (siteKind == UpstreamSiteKind.GEMINI_DIRECT || siteKind == UpstreamSiteKind.VERTEX_AI) {
            return List.of("openai", "google_native");
        }
        return List.of("openai");
    }

    private ReasoningTransport defaultReasoningTransport(UpstreamSiteKind siteKind, boolean supportsThinking) {
        if (!supportsThinking) {
            return ReasoningTransport.NONE;
        }
        if (siteKind == UpstreamSiteKind.GEMINI_DIRECT || siteKind == UpstreamSiteKind.VERTEX_AI) {
            return ReasoningTransport.GEMINI_THOUGHTS;
        }
        return ReasoningTransport.NONE;
    }

    private String normalizeAliasName(String requestedAliasName, String tunedModelName) {
        if (requestedAliasName != null && !requestedAliasName.isBlank()) {
            return requestedAliasName.trim();
        }
        int index = tunedModelName.lastIndexOf('/');
        String derived = index >= 0 ? tunedModelName.substring(index + 1) : tunedModelName;
        if (derived == null || derived.isBlank()) {
            return null;
        }
        return derived.trim();
    }

    private boolean shouldDisableAlias(ModelAliasEntity alias, String jobResourceKey) {
        if (alias == null || jobResourceKey == null || jobResourceKey.isBlank()) {
            return false;
        }
        String description = alias.getDescription();
        return description != null && description.contains(jobResourceKey);
    }

    public record RegistrationResult(
            String modelName,
            String modelKey,
            List<String> aliases
    ) {
    }

    public record CleanupResult(
            int removedCapabilities,
            int disabledAliases
    ) {
    }
}
