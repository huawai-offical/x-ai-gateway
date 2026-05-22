package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ProviderSitePresetResponse;
import com.prodigalgal.xaigateway.gateway.core.catalog.DiscoveredModelDefinition;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyScopeType;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.SiteProfileSource;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.ModelPolicyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteModelCapabilityEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ModelPolicyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteModelCapabilityRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProviderSiteRegistryService {

    private final UpstreamSiteProfileRepository upstreamSiteProfileRepository;
    private final SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository;
    private final SiteModelCapabilityRepository siteModelCapabilityRepository;
    private final ModelPolicyRepository modelPolicyRepository;
    private final UpstreamSitePolicyService upstreamSitePolicyService;
    private final ProviderCatalogLoader providerCatalogLoader;

    @Autowired
    public ProviderSiteRegistryService(
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            SiteModelCapabilityRepository siteModelCapabilityRepository,
            ModelPolicyRepository modelPolicyRepository,
            UpstreamSitePolicyService upstreamSitePolicyService,
            ProviderCatalogLoader providerCatalogLoader) {
        this.upstreamSiteProfileRepository = upstreamSiteProfileRepository;
        this.siteCapabilitySnapshotRepository = siteCapabilitySnapshotRepository;
        this.siteModelCapabilityRepository = siteModelCapabilityRepository;
        this.modelPolicyRepository = modelPolicyRepository;
        this.upstreamSitePolicyService = upstreamSitePolicyService;
        this.providerCatalogLoader = providerCatalogLoader;
    }

    public ProviderSiteRegistryService(
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            SiteModelCapabilityRepository siteModelCapabilityRepository,
            UpstreamSitePolicyService upstreamSitePolicyService,
            ProviderCatalogLoader providerCatalogLoader) {
        this(
                upstreamSiteProfileRepository,
                siteCapabilitySnapshotRepository,
                siteModelCapabilityRepository,
                null,
                upstreamSitePolicyService,
                providerCatalogLoader
        );
    }

    public UpstreamSiteProfileEntity ensureSiteProfile(ProviderType providerType, String baseUrl, Long siteProfileId) {
        if (siteProfileId != null) {
            return upstreamSiteProfileRepository.findById(siteProfileId)
                    .orElseThrow(() -> new IllegalArgumentException("未找到指定的站点档案。"));
        }

        UpstreamSiteKind siteKind = upstreamSitePolicyService.inferSiteKind(providerType, baseUrl);
        String profileCode = "site:" + siteKind.name().toLowerCase(Locale.ROOT);
        return upstreamSiteProfileRepository.findByProfileCode(profileCode)
                .orElseGet(() -> upstreamSiteProfileRepository.save(createProfile(siteKind, profileCode, baseUrl)));
    }

    public UpstreamSitePolicyService.SitePolicy policy(UpstreamSiteKind siteKind) {
        return upstreamSitePolicyService.policy(siteKind);
    }

    @Transactional(readOnly = true)
    public List<ProviderSitePresetResponse> listPresets() {
        return providerCatalogLoader.load().presets().stream()
                .map(this::toPresetResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProviderSitePresetResponse getPreset(String code) {
        return toPresetResponse(findPreset(code));
    }

    public UpstreamSiteProfileEntity importPreset(String code, boolean active, boolean refreshCapabilities) {
        ProviderPresetDefinition preset = findPreset(code);
        String profileCode = preset.profileCode();
        Optional<UpstreamSiteProfileEntity> existing = upstreamSiteProfileRepository.findByProfileCode(profileCode);
        UpstreamSiteProfileEntity entity = existing.orElseGet(() -> upstreamSiteProfileRepository.save(createProfile(preset, active)));
        importPresetModelPolicies(preset, entity);
        if (refreshCapabilities) {
            refreshCapabilities(entity, List.of());
        }
        return entity;
    }

    public SiteCapabilitySnapshotEntity refreshCapabilities(
            UpstreamSiteProfileEntity siteProfile,
            List<DiscoveredModelDefinition> models) {
        UpstreamSiteProfileEntity lockedSiteProfile = lockSiteProfile(siteProfile);
        List<DiscoveredModelDefinition> normalizedModels = normalizeDiscoveredModels(models);
        Instant refreshedAt = Instant.now();

        UpstreamSitePolicyService.SitePolicy policy = upstreamSitePolicyService.policy(lockedSiteProfile.getSiteKind());
        SiteCapabilitySnapshotEntity snapshot = siteCapabilitySnapshotRepository.findBySiteProfile_Id(lockedSiteProfile.getId())
                .orElseGet(SiteCapabilitySnapshotEntity::new);
        snapshot.setSiteProfile(lockedSiteProfile);
        snapshot.setSupportedProtocols(normalizedModels.isEmpty() ? policy.supportedProtocols() : collectProtocols(normalizedModels, policy));
        snapshot.setSupportsResponses(policy.supportsResponses());
        snapshot.setSupportsEmbeddings(policy.supportsEmbeddings());
        snapshot.setSupportsAudio(policy.supportsAudio());
        snapshot.setSupportsImages(policy.supportsImages());
        snapshot.setSupportsModeration(policy.supportsModeration());
        snapshot.setSupportsFiles(policy.supportsFiles());
        snapshot.setSupportsUploads(policy.supportsUploads());
        snapshot.setAuthStrategy(lockedSiteProfile.getAuthStrategy());
        snapshot.setPathStrategy(lockedSiteProfile.getPathStrategy());
        snapshot.setErrorSchemaStrategy(lockedSiteProfile.getErrorSchemaStrategy());
        snapshot.setStreamTransport(policy.streamTransport());
        snapshot.setFallbackStrategy(policy.fallbackStrategy());
        snapshot.setHealthState(policy.blockedReason() == null ? "READY" : "BLOCKED");
        snapshot.setBlockedReason(policy.blockedReason());
        snapshot.setRefreshedAt(refreshedAt);
        SiteCapabilitySnapshotEntity savedSnapshot = siteCapabilitySnapshotRepository.save(snapshot);

        if (!normalizedModels.isEmpty()) {
            refreshModelCapabilities(lockedSiteProfile, policy, normalizedModels, refreshedAt);
        }
        return savedSnapshot;
    }

    private UpstreamSiteProfileEntity createProfile(UpstreamSiteKind siteKind, String profileCode, String baseUrl) {
        UpstreamSitePolicyService.SitePolicy policy = upstreamSitePolicyService.policy(siteKind);
        UpstreamSiteProfileEntity entity = new UpstreamSiteProfileEntity();
        entity.setProfileCode(profileCode);
        entity.setDisplayName(siteKind.name());
        entity.setVendorCode(siteKind.name().toLowerCase(Locale.ROOT));
        entity.setVendorName(siteKind.name());
        entity.setProviderFamily(policy.providerFamily());
        entity.setSiteKind(siteKind);
        entity.setAuthStrategy(policy.authStrategy());
        entity.setPathStrategy(policy.pathStrategy());
        entity.setModelAddressingStrategy(policy.modelAddressingStrategy());
        entity.setErrorSchemaStrategy(policy.errorSchemaStrategy());
        entity.setBaseUrlPattern(baseUrl == null || baseUrl.isBlank() ? null : baseUrl.trim());
        entity.setDescription("由凭证自动推断生成的站点档案。");
        entity.setProfileSource(SiteProfileSource.AUTO_DISCOVERED);
        entity.setActive(true);
        return entity;
    }

    private UpstreamSiteProfileEntity createProfile(ProviderPresetDefinition preset, boolean active) {
        UpstreamSitePolicyService.SitePolicy policy = upstreamSitePolicyService.policy(preset.siteKind());
        UpstreamSiteProfileEntity entity = new UpstreamSiteProfileEntity();
        entity.setProfileCode(preset.profileCode());
        entity.setDisplayName(preset.displayName());
        entity.setVendorCode(preset.vendorCode());
        entity.setVendorName(preset.vendorName());
        entity.setProviderFamily(policy.providerFamily());
        entity.setSiteKind(preset.siteKind());
        entity.setAuthStrategy(policy.authStrategy());
        entity.setPathStrategy(policy.pathStrategy());
        entity.setModelAddressingStrategy(policy.modelAddressingStrategy());
        entity.setErrorSchemaStrategy(policy.errorSchemaStrategy());
        entity.setBaseUrlPattern(preset.defaultBaseUrl());
        entity.setDescription(preset.description());
        entity.setConversationProfileJson(writeJson(preset.conversationProfile()));
        entity.setProfileSource(SiteProfileSource.MANUAL);
        entity.setActive(active);
        return entity;
    }

    private ProviderPresetDefinition findPreset(String code) {
        String normalizedCode = normalizePresetCode(code);
        return providerCatalogLoader.load().presets().stream()
                .filter(preset -> preset.code().equals(normalizedCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的供应商预设。"));
    }

    private ProviderSitePresetResponse toPresetResponse(ProviderPresetDefinition preset) {
        UpstreamSitePolicyService.SitePolicy policy = upstreamSitePolicyService.policy(preset.siteKind());
        Optional<UpstreamSiteProfileEntity> existing = upstreamSiteProfileRepository.findByProfileCode(preset.profileCode());
        return new ProviderSitePresetResponse(
                preset.code(),
                preset.profileCode(),
                preset.displayName(),
                preset.vendorCode(),
                preset.vendorName(),
                preset.siteKind(),
                policy.providerFamily(),
                policy.authStrategy(),
                policy.pathStrategy(),
                policy.modelAddressingStrategy(),
                policy.errorSchemaStrategy(),
                preset.defaultBaseUrl(),
                preset.description(),
                policy.supportedProtocols(),
                policy.streamTransport(),
                policy.fallbackStrategy(),
                preset.capabilityTags(),
                preset.costProfile(),
                preset.errorMode(),
                preset.catalogVersion(),
                preset.catalogSource(),
                preset.deprecated(),
                preset.conformanceChecks(),
                preset.compatibilitySurface(),
                preset.supportStrategy(),
                preset.modelFamilies(),
                preset.pricingMetadata(),
                preset.unsupportedFeatures(),
                preset.conversationProfile(),
                preset.modelPolicies(),
                existing.isPresent(),
                existing.map(UpstreamSiteProfileEntity::getId).orElse(null)
        );
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return providerCatalogLoader.objectMapper().writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("站点档案对话画像无法序列化。", exception);
        }
    }

    private void importPresetModelPolicies(ProviderPresetDefinition preset, UpstreamSiteProfileEntity siteProfile) {
        if (modelPolicyRepository == null || preset.modelPolicies().isEmpty()) {
            return;
        }
        for (java.util.Map<String, Object> item : preset.modelPolicies()) {
            String publicModel = textValue(item.get("publicModel"));
            if (publicModel == null || publicModel.isBlank()) {
                continue;
            }
            String upstreamModel = textValue(item.get("upstreamModel"));
            ModelPolicyEntity policy = new ModelPolicyEntity();
            policy.setScopeType(ModelPolicyScopeType.SITE_PROFILE);
            policy.setScopeId(siteProfile.getId());
            policy.setPolicyKind(textValue(item.get("policyKind")) == null ? "MAP" : textValue(item.get("policyKind")).trim().toUpperCase(Locale.ROOT));
            policy.setPublicModel(publicModel.trim());
            policy.setPublicModelKey(ModelIdNormalizer.normalize(publicModel));
            policy.setUpstreamModel(upstreamModel == null || upstreamModel.isBlank() ? publicModel.trim() : upstreamModel.trim());
            policy.setUpstreamModelKey(ModelIdNormalizer.normalize(policy.getUpstreamModel()));
            policy.setModelFamily(textValue(item.get("modelFamily")));
            policy.setSupportedProtocols(listValue(item.get("supportedProtocols")));
            policy.setEnabled(true);
            policy.setDeny(false);
            policy.setPriority(intValue(item.get("priority"), 100));
            policy.setWeight(intValue(item.get("weight"), 100));
            policy.setCapabilityJson(writeJson(item.get("capability")));
            policy.setRequestOverridesJson(writeJson(item.get("requestOverrides")));
            policy.setResponseOverridesJson(writeJson(item.get("responseOverrides")));
            policy.setRuntimePolicyJson(writeJson(item.get("runtimePolicy")));
            policy.setMappingSource("preset");
            policy.setDescription("由 provider preset 导入：" + preset.code());
            ModelPolicyEntity existing = modelPolicyRepository
                    .findAllByScopeTypeAndScopeIdAndEnabledTrueOrderByPriorityAscCreatedAtAsc(
                            ModelPolicyScopeType.SITE_PROFILE,
                            siteProfile.getId()
                    )
                    .stream()
                    .filter(candidate -> policy.getPublicModelKey().equals(candidate.getPublicModelKey()))
                    .filter(candidate -> policy.getUpstreamModelKey().equals(candidate.getUpstreamModelKey()))
                    .filter(candidate -> "preset".equalsIgnoreCase(candidate.getMappingSource()))
                    .min(Comparator.comparing(ModelPolicyEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);
            if (existing == null) {
                modelPolicyRepository.save(policy);
            }
        }
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private List<String> listValue(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(this::textValue)
                .filter(item -> item != null && !item.isBlank())
                .map(item -> item.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private String normalizePresetCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("供应商预设 code 不能为空。");
        }
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private UpstreamSiteProfileEntity lockSiteProfile(UpstreamSiteProfileEntity siteProfile) {
        if (siteProfile == null || siteProfile.getId() == null) {
            return siteProfile;
        }
        Optional<UpstreamSiteProfileEntity> locked = upstreamSiteProfileRepository.findByIdForUpdate(siteProfile.getId());
        return locked == null ? siteProfile : locked.orElse(siteProfile);
    }

    private void refreshModelCapabilities(
            UpstreamSiteProfileEntity siteProfile,
            UpstreamSitePolicyService.SitePolicy policy,
            List<DiscoveredModelDefinition> models,
            Instant refreshedAt) {
        Map<String, SiteModelCapabilityEntity> existingByKey = new LinkedHashMap<>();
        for (SiteModelCapabilityEntity existing : siteModelCapabilityRepository.findAllBySiteProfile_IdOrderByModelKeyAsc(siteProfile.getId())) {
            existingByKey.putIfAbsent(existing.getModelKey(), existing);
        }

        List<SiteModelCapabilityEntity> writes = new ArrayList<>();
        Set<String> refreshedKeys = new LinkedHashSet<>();
        for (DiscoveredModelDefinition model : models) {
            SiteModelCapabilityEntity entity = existingByKey.getOrDefault(model.modelKey(), new SiteModelCapabilityEntity());
            applySiteModelCapability(entity, siteProfile, policy, model, refreshedAt);
            refreshedKeys.add(model.modelKey());
            writes.add(entity);
        }

        for (SiteModelCapabilityEntity existing : existingByKey.values()) {
            if (!refreshedKeys.contains(existing.getModelKey()) && existing.isActive()) {
                existing.setActive(false);
                existing.setSourceRefreshedAt(refreshedAt);
                writes.add(existing);
            }
        }

        if (!writes.isEmpty()) {
            siteModelCapabilityRepository.saveAll(writes);
        }
    }

    private List<DiscoveredModelDefinition> normalizeDiscoveredModels(List<DiscoveredModelDefinition> models) {
        if (models == null || models.isEmpty()) {
            return List.of();
        }
        Map<String, DiscoveredModelDefinition> values = new LinkedHashMap<>();
        for (DiscoveredModelDefinition model : models) {
            String modelKey = normalizeModelKey(model);
            if (modelKey == null) {
                continue;
            }
            DiscoveredModelDefinition normalized = new DiscoveredModelDefinition(
                    model.modelName() == null || model.modelName().isBlank() ? modelKey : model.modelName().trim(),
                    modelKey,
                    normalizeProtocols(model.supportedProtocols()),
                    model.supportsChat(),
                    model.supportsTools(),
                    model.supportsImageInput(),
                    model.supportsEmbeddings(),
                    model.supportsCache(),
                    model.supportsThinking(),
                    model.supportsVisibleReasoning(),
                    model.supportsReasoningReuse(),
                    model.reasoningTransport() == null ? ReasoningTransport.NONE : model.reasoningTransport()
            );
            values.merge(modelKey, normalized, this::mergeModel);
        }
        return List.copyOf(values.values());
    }

    private DiscoveredModelDefinition mergeModel(DiscoveredModelDefinition left, DiscoveredModelDefinition right) {
        return new DiscoveredModelDefinition(
                firstText(left.modelName(), right.modelName()),
                left.modelKey(),
                mergeProtocols(left.supportedProtocols(), right.supportedProtocols()),
                left.supportsChat() || right.supportsChat(),
                left.supportsTools() || right.supportsTools(),
                left.supportsImageInput() || right.supportsImageInput(),
                left.supportsEmbeddings() || right.supportsEmbeddings(),
                left.supportsCache() || right.supportsCache(),
                left.supportsThinking() || right.supportsThinking(),
                left.supportsVisibleReasoning() || right.supportsVisibleReasoning(),
                left.supportsReasoningReuse() || right.supportsReasoningReuse(),
                mergeReasoningTransport(left.reasoningTransport(), right.reasoningTransport())
        );
    }

    private String normalizeModelKey(DiscoveredModelDefinition model) {
        if (model == null) {
            return null;
        }
        String value = model.modelKey() == null || model.modelKey().isBlank()
                ? model.modelName()
                : model.modelKey();
        String normalized = ModelIdNormalizer.normalize(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private String firstText(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left.trim();
        }
        return right == null ? null : right.trim();
    }

    private ReasoningTransport mergeReasoningTransport(ReasoningTransport left, ReasoningTransport right) {
        if (left != null && left != ReasoningTransport.NONE) {
            return left;
        }
        return right == null ? ReasoningTransport.NONE : right;
    }

    private List<String> normalizeProtocols(List<String> protocols) {
        if (protocols == null || protocols.isEmpty()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String protocol : protocols) {
            if (protocol != null && !protocol.isBlank()) {
                values.add(protocol.trim().toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(values);
    }

    private List<String> mergeProtocols(List<String> left, List<String> right) {
        Set<String> values = new LinkedHashSet<>(normalizeProtocols(left));
        values.addAll(normalizeProtocols(right));
        return List.copyOf(values);
    }

    private void applySiteModelCapability(
            SiteModelCapabilityEntity entity,
            UpstreamSiteProfileEntity siteProfile,
            UpstreamSitePolicyService.SitePolicy policy,
            DiscoveredModelDefinition model,
            Instant refreshedAt) {
        entity.setSiteProfile(siteProfile);
        entity.setModelName(model.modelName());
        entity.setModelKey(model.modelKey());
        entity.setSupportedProtocols(collectProtocols(List.of(model), policy));
        entity.setSupportsChat(model.supportsChat());
        entity.setSupportsTools(model.supportsTools());
        entity.setSupportsImageInput(model.supportsImageInput());
        entity.setSupportsEmbeddings(model.supportsEmbeddings());
        entity.setSupportsCache(model.supportsCache());
        entity.setSupportsThinking(model.supportsThinking());
        entity.setSupportsVisibleReasoning(model.supportsVisibleReasoning());
        entity.setSupportsReasoningReuse(model.supportsReasoningReuse());
        entity.setReasoningTransport(model.reasoningTransport());
        entity.setCapabilityLevel(policy.blockedReason() == null ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED);
        entity.setActive(true);
        entity.setSourceRefreshedAt(refreshedAt);
    }

    private List<String> collectProtocols(List<DiscoveredModelDefinition> models, UpstreamSitePolicyService.SitePolicy policy) {
        Set<String> values = new LinkedHashSet<>(policy.supportedProtocols());
        for (DiscoveredModelDefinition model : models) {
            values.addAll(model.supportedProtocols());
        }
        return List.copyOf(values);
    }

}
