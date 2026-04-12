package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.catalog.DiscoveredModelDefinition;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
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
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProviderSiteRegistryService {

    private final UpstreamSiteProfileRepository upstreamSiteProfileRepository;
    private final SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository;
    private final SiteModelCapabilityRepository siteModelCapabilityRepository;
    private final UpstreamSitePolicyService upstreamSitePolicyService;

    public ProviderSiteRegistryService(
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            SiteModelCapabilityRepository siteModelCapabilityRepository,
            UpstreamSitePolicyService upstreamSitePolicyService) {
        this.upstreamSiteProfileRepository = upstreamSiteProfileRepository;
        this.siteCapabilitySnapshotRepository = siteCapabilitySnapshotRepository;
        this.siteModelCapabilityRepository = siteModelCapabilityRepository;
        this.upstreamSitePolicyService = upstreamSitePolicyService;
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
        snapshot.setSupportsBatches(policy.supportsBatches());
        snapshot.setSupportsTuning(policy.supportsTuning());
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
        entity.setActive(true);
        return entity;
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
