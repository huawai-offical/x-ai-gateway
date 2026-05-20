package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ProviderSitePresetResponse;
import com.prodigalgal.xaigateway.gateway.core.catalog.DiscoveredModelDefinition;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.SiteProfileSource;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteModelCapabilityEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteModelCapabilityRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private final UpstreamSitePolicyService upstreamSitePolicyService;
    private final ProviderCatalogLoader providerCatalogLoader;

    @Autowired
    public ProviderSiteRegistryService(
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            SiteModelCapabilityRepository siteModelCapabilityRepository,
            UpstreamSitePolicyService upstreamSitePolicyService,
            ProviderCatalogLoader providerCatalogLoader) {
        this.upstreamSiteProfileRepository = upstreamSiteProfileRepository;
        this.siteCapabilitySnapshotRepository = siteCapabilitySnapshotRepository;
        this.siteModelCapabilityRepository = siteModelCapabilityRepository;
        this.upstreamSitePolicyService = upstreamSitePolicyService;
        this.providerCatalogLoader = providerCatalogLoader;
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
        if (refreshCapabilities) {
            refreshCapabilities(entity, List.of());
        }
        return entity;
    }

    public SiteCapabilitySnapshotEntity refreshCapabilities(
            UpstreamSiteProfileEntity siteProfile,
            List<DiscoveredModelDefinition> models) {
        UpstreamSitePolicyService.SitePolicy policy = upstreamSitePolicyService.policy(siteProfile.getSiteKind());
        SiteCapabilitySnapshotEntity snapshot = siteCapabilitySnapshotRepository.findBySiteProfile_Id(siteProfile.getId())
                .orElseGet(SiteCapabilitySnapshotEntity::new);
        snapshot.setSiteProfile(siteProfile);
        snapshot.setSupportedProtocols(models.isEmpty() ? policy.supportedProtocols() : collectProtocols(models, policy));
        snapshot.setSupportsResponses(policy.supportsResponses());
        snapshot.setSupportsEmbeddings(policy.supportsEmbeddings());
        snapshot.setSupportsAudio(policy.supportsAudio());
        snapshot.setSupportsImages(policy.supportsImages());
        snapshot.setSupportsModeration(policy.supportsModeration());
        snapshot.setSupportsFiles(policy.supportsFiles());
        snapshot.setSupportsUploads(policy.supportsUploads());
        snapshot.setSupportsRealtime(policy.supportsRealtime());
        snapshot.setAuthStrategy(siteProfile.getAuthStrategy());
        snapshot.setPathStrategy(siteProfile.getPathStrategy());
        snapshot.setErrorSchemaStrategy(siteProfile.getErrorSchemaStrategy());
        snapshot.setStreamTransport(policy.streamTransport());
        snapshot.setFallbackStrategy(policy.fallbackStrategy());
        snapshot.setHealthState(policy.blockedReason() == null ? "READY" : "BLOCKED");
        snapshot.setBlockedReason(policy.blockedReason());
        snapshot.setRefreshedAt(Instant.now());
        SiteCapabilitySnapshotEntity savedSnapshot = siteCapabilitySnapshotRepository.save(snapshot);

        siteModelCapabilityRepository.deleteAllBySiteProfile_Id(siteProfile.getId());
        if (!models.isEmpty()) {
            List<SiteModelCapabilityEntity> capabilities = models.stream()
                    .map(model -> toSiteModelCapability(siteProfile, policy, model))
                    .toList();
            siteModelCapabilityRepository.saveAll(capabilities);
        }
        return savedSnapshot;
    }

    private UpstreamSiteProfileEntity createProfile(UpstreamSiteKind siteKind, String profileCode, String baseUrl) {
        UpstreamSitePolicyService.SitePolicy policy = upstreamSitePolicyService.policy(siteKind);
        UpstreamSiteProfileEntity entity = new UpstreamSiteProfileEntity();
        entity.setProfileCode(profileCode);
        entity.setDisplayName(siteKind.name());
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
        entity.setProviderFamily(policy.providerFamily());
        entity.setSiteKind(preset.siteKind());
        entity.setAuthStrategy(policy.authStrategy());
        entity.setPathStrategy(policy.pathStrategy());
        entity.setModelAddressingStrategy(policy.modelAddressingStrategy());
        entity.setErrorSchemaStrategy(policy.errorSchemaStrategy());
        entity.setBaseUrlPattern(preset.defaultBaseUrl());
        entity.setDescription(preset.description());
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
                existing.isPresent(),
                existing.map(UpstreamSiteProfileEntity::getId).orElse(null)
        );
    }

    private String normalizePresetCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("供应商预设 code 不能为空。");
        }
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private SiteModelCapabilityEntity toSiteModelCapability(
            UpstreamSiteProfileEntity siteProfile,
            UpstreamSitePolicyService.SitePolicy policy,
            DiscoveredModelDefinition model) {
        SiteModelCapabilityEntity entity = new SiteModelCapabilityEntity();
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
        entity.setSourceRefreshedAt(Instant.now());
        return entity;
    }

    private List<String> collectProtocols(List<DiscoveredModelDefinition> models, UpstreamSitePolicyService.SitePolicy policy) {
        Set<String> values = new LinkedHashSet<>(policy.supportedProtocols());
        for (DiscoveredModelDefinition model : models) {
            values.addAll(model.supportedProtocols());
        }
        return List.copyOf(values);
    }

}
