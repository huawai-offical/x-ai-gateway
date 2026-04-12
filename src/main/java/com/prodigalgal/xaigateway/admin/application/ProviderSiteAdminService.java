package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.CapabilityMatrixRowResponse;
import com.prodigalgal.xaigateway.admin.api.ProviderSiteRequest;
import com.prodigalgal.xaigateway.admin.api.ProviderSiteResponse;
import com.prodigalgal.xaigateway.admin.api.SiteModelCapabilityResponse;
import com.prodigalgal.xaigateway.gateway.core.catalog.CredentialModelDiscoveryService;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteModelCapabilityEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteModelCapabilityRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProviderSiteAdminService {

    private final UpstreamSiteProfileRepository upstreamSiteProfileRepository;
    private final SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository;
    private final SiteModelCapabilityRepository siteModelCapabilityRepository;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final ProviderSiteRegistryService providerSiteRegistryService;
    private final CredentialModelDiscoveryService credentialModelDiscoveryService;
    private final SiteCapabilityTruthService siteCapabilityTruthService;

    public ProviderSiteAdminService(
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            SiteModelCapabilityRepository siteModelCapabilityRepository,
            UpstreamCredentialRepository upstreamCredentialRepository,
            ProviderSiteRegistryService providerSiteRegistryService,
            CredentialModelDiscoveryService credentialModelDiscoveryService,
            SiteCapabilityTruthService siteCapabilityTruthService) {
        this.upstreamSiteProfileRepository = upstreamSiteProfileRepository;
        this.siteCapabilitySnapshotRepository = siteCapabilitySnapshotRepository;
        this.siteModelCapabilityRepository = siteModelCapabilityRepository;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.providerSiteRegistryService = providerSiteRegistryService;
        this.credentialModelDiscoveryService = credentialModelDiscoveryService;
        this.siteCapabilityTruthService = siteCapabilityTruthService;
    }

    @Transactional(readOnly = true)
    public List<ProviderSiteResponse> list() {
        return upstreamSiteProfileRepository.findAll().stream()
                .sorted(Comparator.comparing(UpstreamSiteProfileEntity::getDisplayName))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProviderSiteResponse get(Long id) {
        return toResponse(getRequired(id));
    }

    public ProviderSiteResponse create(ProviderSiteRequest request) {
        UpstreamSiteProfileEntity entity = new UpstreamSiteProfileEntity();
        apply(entity, request);
        return toResponse(upstreamSiteProfileRepository.save(entity));
    }

    public ProviderSiteResponse update(Long id, ProviderSiteRequest request) {
        UpstreamSiteProfileEntity entity = getRequired(id);
        apply(entity, request);
        return toResponse(upstreamSiteProfileRepository.save(entity));
    }

    public ProviderSiteResponse refreshCapabilities(Long id) {
        UpstreamSiteProfileEntity entity = getRequired(id);
        List<UpstreamCredentialEntity> credentials = upstreamCredentialRepository.findAllBySiteProfileIdAndDeletedFalseOrderByCreatedAtDesc(id);
        if (credentials.isEmpty()) {
            providerSiteRegistryService.refreshCapabilities(entity, List.of());
            return toResponse(entity);
        }
        for (UpstreamCredentialEntity credential : credentials) {
            credentialModelDiscoveryService.refreshCredential(credential.getId());
        }
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<SiteModelCapabilityResponse> listCapabilities(Long id) {
        return siteModelCapabilityRepository.findAllBySiteProfile_IdOrderByModelKeyAsc(id).stream()
                .map(item -> new SiteModelCapabilityResponse(
                        item.getId(),
                        item.getModelName(),
                        item.getModelKey(),
                        item.getSupportedProtocols(),
                        item.isSupportsChat(),
                        item.isSupportsTools(),
                        item.isSupportsImageInput(),
                        item.isSupportsEmbeddings(),
                        item.isSupportsCache(),
                        item.isSupportsThinking(),
                        item.isSupportsVisibleReasoning(),
                        item.isSupportsReasoningReuse(),
                        item.getReasoningTransport(),
                        item.getCapabilityLevel(),
                        item.getSourceRefreshedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CapabilityMatrixRowResponse> capabilityMatrix() {
        return upstreamSiteProfileRepository.findAll().stream()
                .sorted(Comparator.comparing(UpstreamSiteProfileEntity::getDisplayName))
                .map(this::toCapabilityMatrixRow)
                .toList();
    }

    private CapabilityMatrixRowResponse toCapabilityMatrixRow(UpstreamSiteProfileEntity entity) {
        SiteCapabilitySnapshotEntity snapshot = siteCapabilitySnapshotRepository.findBySiteProfile_Id(entity.getId()).orElse(null);
        return new CapabilityMatrixRowResponse(
                entity.getId(),
                entity.getProfileCode(),
                entity.getDisplayName(),
                entity.getProviderFamily(),
                entity.getSiteKind(),
                entity.getAuthStrategy(),
                entity.getPathStrategy(),
                entity.getErrorSchemaStrategy(),
                snapshot == null ? "UNKNOWN" : snapshot.getHealthState(),
                snapshot == null ? null : snapshot.getBlockedReason(),
                snapshot == null ? List.of() : snapshot.getSupportedProtocols(),
                siteCapabilityTruthService.supportsFeature(entity, snapshot, InteropFeature.RESPONSE_OBJECT),
                siteCapabilityTruthService.supportsFeature(entity, snapshot, InteropFeature.EMBEDDINGS),
                siteCapabilityTruthService.supportsFeature(entity, snapshot, InteropFeature.AUDIO_TRANSCRIPTION),
                siteCapabilityTruthService.supportsFeature(entity, snapshot, InteropFeature.IMAGE_GENERATION),
                siteCapabilityTruthService.supportsFeature(entity, snapshot, InteropFeature.MODERATION),
                siteCapabilityTruthService.supportsFeature(entity, snapshot, InteropFeature.FILE_OBJECT),
                siteCapabilityTruthService.supportsFeature(entity, snapshot, InteropFeature.UPLOAD_CREATE),
                siteCapabilityTruthService.supportsFeature(entity, snapshot, InteropFeature.BATCH_CREATE),
                siteCapabilityTruthService.supportsFeature(entity, snapshot, InteropFeature.TUNING_CREATE),
                siteCapabilityTruthService.supportsFeature(entity, snapshot, InteropFeature.REALTIME_CLIENT_SECRET)
        );
    }

    private ProviderSiteResponse toResponse(UpstreamSiteProfileEntity entity) {
        SiteCapabilitySnapshotEntity snapshot = siteCapabilitySnapshotRepository.findBySiteProfile_Id(entity.getId()).orElse(null);
        int modelCount = siteModelCapabilityRepository.findAllBySiteProfile_IdOrderByModelKeyAsc(entity.getId()).size();
        return new ProviderSiteResponse(
                entity.getId(),
                entity.getProfileCode(),
                entity.getDisplayName(),
                entity.getProviderFamily(),
                entity.getSiteKind(),
                entity.getAuthStrategy(),
                entity.getPathStrategy(),
                entity.getModelAddressingStrategy(),
                entity.getErrorSchemaStrategy(),
                entity.getBaseUrlPattern(),
                entity.getDescription(),
                entity.isActive(),
                snapshot == null ? "UNKNOWN" : snapshot.getHealthState(),
                snapshot == null ? null : snapshot.getBlockedReason(),
                snapshot == null ? List.of() : snapshot.getSupportedProtocols(),
                modelCount,
                snapshot == null ? null : snapshot.getRefreshedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private UpstreamSiteProfileEntity getRequired(Long id) {
        Optional<UpstreamSiteProfileEntity> entity = upstreamSiteProfileRepository.findById(id);
        if (entity.isEmpty()) {
            throw new IllegalArgumentException("未找到指定的站点档案。");
        }
        return entity.get();
    }

    private void apply(UpstreamSiteProfileEntity entity, ProviderSiteRequest request) {
        var policy = providerSiteRegistryService.policy(request.siteKind());
        entity.setProfileCode(request.profileCode().trim());
        entity.setDisplayName(request.displayName().trim());
        entity.setProviderFamily(policy.providerFamily());
        entity.setSiteKind(request.siteKind());
        entity.setAuthStrategy(policy.authStrategy());
        entity.setPathStrategy(policy.pathStrategy());
        entity.setModelAddressingStrategy(policy.modelAddressingStrategy());
        entity.setErrorSchemaStrategy(policy.errorSchemaStrategy());
        entity.setBaseUrlPattern(request.baseUrlPattern() == null ? null : request.baseUrlPattern().trim());
        entity.setDescription(request.description() == null ? null : request.description().trim());
        entity.setActive(request.active() == null || request.active());
    }
}
