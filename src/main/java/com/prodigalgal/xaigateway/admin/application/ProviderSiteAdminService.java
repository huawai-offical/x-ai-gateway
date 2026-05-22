package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.CapabilityMatrixRowResponse;
import com.prodigalgal.xaigateway.admin.api.ProviderSitePresetImportRequest;
import com.prodigalgal.xaigateway.admin.api.ProviderSitePresetResponse;
import com.prodigalgal.xaigateway.admin.api.ProviderProtocolEndpointRequest;
import com.prodigalgal.xaigateway.admin.api.ProviderProtocolEndpointResponse;
import com.prodigalgal.xaigateway.admin.api.ProviderSiteRequest;
import com.prodigalgal.xaigateway.admin.api.ProviderSiteResponse;
import com.prodigalgal.xaigateway.admin.api.SiteModelCapabilityResponse;
import com.prodigalgal.xaigateway.gateway.core.catalog.SurfaceCapabilityView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRenderCapabilitySupport;
import com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendDecision;
import com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendPolicyService;
import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolutionView;
import com.prodigalgal.xaigateway.gateway.core.catalog.CredentialModelDiscoveryService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.interop.SupportStatus;
import com.prodigalgal.xaigateway.gateway.core.interop.SurfaceCompatibilityReport;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelAddressingStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProtocolSuite;
import com.prodigalgal.xaigateway.gateway.core.shared.SiteProfileSource;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import com.prodigalgal.xaigateway.infra.persistence.entity.ProviderProtocolEndpointEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteModelCapabilityEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ProviderProtocolEndpointRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteModelCapabilityRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class ProviderSiteAdminService {

    private final UpstreamSiteProfileRepository upstreamSiteProfileRepository;
    private final SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository;
    private final SiteModelCapabilityRepository siteModelCapabilityRepository;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final ProviderProtocolEndpointRepository providerProtocolEndpointRepository;
    private final ProviderSiteRegistryService providerSiteRegistryService;
    private final CredentialModelDiscoveryService credentialModelDiscoveryService;
    private final SiteCapabilityTruthService siteCapabilityTruthService;
    private final ExecutionBackendPolicyService executionBackendPolicyService;
    private final SecurityPolicyService securityPolicyService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ProviderSiteAdminService(
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            SiteModelCapabilityRepository siteModelCapabilityRepository,
            UpstreamCredentialRepository upstreamCredentialRepository,
            ProviderProtocolEndpointRepository providerProtocolEndpointRepository,
            ProviderSiteRegistryService providerSiteRegistryService,
            CredentialModelDiscoveryService credentialModelDiscoveryService,
            SiteCapabilityTruthService siteCapabilityTruthService,
            ExecutionBackendPolicyService executionBackendPolicyService,
            SecurityPolicyService securityPolicyService) {
        this(
                upstreamSiteProfileRepository,
                siteCapabilitySnapshotRepository,
                siteModelCapabilityRepository,
                upstreamCredentialRepository,
                providerProtocolEndpointRepository,
                providerSiteRegistryService,
                credentialModelDiscoveryService,
                siteCapabilityTruthService,
                executionBackendPolicyService,
                securityPolicyService,
                new ObjectMapper()
        );
    }

    public ProviderSiteAdminService(
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            SiteModelCapabilityRepository siteModelCapabilityRepository,
            UpstreamCredentialRepository upstreamCredentialRepository,
            ProviderProtocolEndpointRepository providerProtocolEndpointRepository,
            ProviderSiteRegistryService providerSiteRegistryService,
            CredentialModelDiscoveryService credentialModelDiscoveryService,
            SiteCapabilityTruthService siteCapabilityTruthService,
            ExecutionBackendPolicyService executionBackendPolicyService,
            SecurityPolicyService securityPolicyService,
            ObjectMapper objectMapper) {
        this.upstreamSiteProfileRepository = upstreamSiteProfileRepository;
        this.siteCapabilitySnapshotRepository = siteCapabilitySnapshotRepository;
        this.siteModelCapabilityRepository = siteModelCapabilityRepository;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.providerProtocolEndpointRepository = providerProtocolEndpointRepository;
        this.providerSiteRegistryService = providerSiteRegistryService;
        this.credentialModelDiscoveryService = credentialModelDiscoveryService;
        this.siteCapabilityTruthService = siteCapabilityTruthService;
        this.executionBackendPolicyService = executionBackendPolicyService;
        this.securityPolicyService = securityPolicyService;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public ProviderSiteAdminService(
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            SiteModelCapabilityRepository siteModelCapabilityRepository,
            UpstreamCredentialRepository upstreamCredentialRepository,
            ProviderSiteRegistryService providerSiteRegistryService,
            CredentialModelDiscoveryService credentialModelDiscoveryService,
            SiteCapabilityTruthService siteCapabilityTruthService,
            ExecutionBackendPolicyService executionBackendPolicyService,
            SecurityPolicyService securityPolicyService) {
        this(
                upstreamSiteProfileRepository,
                siteCapabilitySnapshotRepository,
                siteModelCapabilityRepository,
                upstreamCredentialRepository,
                null,
                providerSiteRegistryService,
                credentialModelDiscoveryService,
                siteCapabilityTruthService,
                executionBackendPolicyService,
                securityPolicyService
        );
    }

    public ProviderSiteAdminService(
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            SiteModelCapabilityRepository siteModelCapabilityRepository,
            UpstreamCredentialRepository upstreamCredentialRepository,
            ProviderSiteRegistryService providerSiteRegistryService,
            CredentialModelDiscoveryService credentialModelDiscoveryService,
            SiteCapabilityTruthService siteCapabilityTruthService) {
        this(
                upstreamSiteProfileRepository,
                siteCapabilitySnapshotRepository,
                siteModelCapabilityRepository,
                upstreamCredentialRepository,
                null,
                providerSiteRegistryService,
                credentialModelDiscoveryService,
                siteCapabilityTruthService,
                new ExecutionBackendPolicyService(),
                null
        );
    }

    public ProviderSiteAdminService(
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            SiteModelCapabilityRepository siteModelCapabilityRepository,
            UpstreamCredentialRepository upstreamCredentialRepository,
            ProviderSiteRegistryService providerSiteRegistryService,
            CredentialModelDiscoveryService credentialModelDiscoveryService,
            SiteCapabilityTruthService siteCapabilityTruthService,
            ExecutionBackendPolicyService executionBackendPolicyService) {
        this(
                upstreamSiteProfileRepository,
                siteCapabilitySnapshotRepository,
                siteModelCapabilityRepository,
                upstreamCredentialRepository,
                null,
                providerSiteRegistryService,
                credentialModelDiscoveryService,
                siteCapabilityTruthService,
                executionBackendPolicyService,
                null
        );
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
        UpstreamSiteProfileEntity saved = upstreamSiteProfileRepository.save(entity);
        ensureManualDefaultEndpoint(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProviderSitePresetResponse> listPresets() {
        return providerSiteRegistryService.listPresets();
    }

    @Transactional(readOnly = true)
    public ProviderSitePresetResponse getPreset(String code) {
        return providerSiteRegistryService.getPreset(code);
    }

    public ProviderSiteResponse importPreset(String code, ProviderSitePresetImportRequest request) {
        boolean active = request == null || request.active() == null || request.active();
        boolean refreshCapabilities = request == null || request.refreshCapabilities() == null || request.refreshCapabilities();
        return toResponse(providerSiteRegistryService.importPreset(code, active, refreshCapabilities));
    }

    public ProviderSiteResponse update(Long id, ProviderSiteRequest request) {
        UpstreamSiteProfileEntity entity = getRequired(id);
        apply(entity, request);
        UpstreamSiteProfileEntity saved = upstreamSiteProfileRepository.save(entity);
        ensureManualDefaultEndpoint(saved);
        return toResponse(saved);
    }

    public void delete(Long id) {
        UpstreamSiteProfileEntity entity = getRequired(id);
        long linkedCredentialCount = upstreamCredentialRepository.countBySiteProfileIdAndDeletedFalse(id);
        if (linkedCredentialCount > 0) {
            throw new IllegalArgumentException("该站点档案仍有绑定凭证，请先解除绑定后再删除。");
        }
        siteModelCapabilityRepository.deleteAllBySiteProfile_Id(id);
        siteCapabilitySnapshotRepository.findBySiteProfile_Id(id).ifPresent(siteCapabilitySnapshotRepository::delete);
        if (providerProtocolEndpointRepository != null) {
            providerProtocolEndpointRepository.deleteAllBySiteProfileId(id);
        }
        upstreamSiteProfileRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public List<ProviderProtocolEndpointResponse> listProtocolEndpoints(Long siteProfileId) {
        getRequired(siteProfileId);
        if (providerProtocolEndpointRepository == null) {
            return List.of();
        }
        return providerProtocolEndpointRepository.findAllBySiteProfileIdOrderByDisplayNameAsc(siteProfileId).stream()
                .map(this::toEndpointResponse)
                .toList();
    }

    public ProviderProtocolEndpointResponse createProtocolEndpoint(
            Long siteProfileId,
            ProviderProtocolEndpointRequest request) {
        getRequired(siteProfileId);
        ProviderProtocolEndpointEntity entity = new ProviderProtocolEndpointEntity();
        entity.setSiteProfileId(siteProfileId);
        applyEndpoint(entity, request);
        return toEndpointResponse(providerProtocolEndpointRepository.save(entity));
    }

    public ProviderProtocolEndpointResponse updateProtocolEndpoint(
            Long siteProfileId,
            Long endpointId,
            ProviderProtocolEndpointRequest request) {
        ProviderProtocolEndpointEntity entity = getEndpointRequired(siteProfileId, endpointId);
        applyEndpoint(entity, request);
        return toEndpointResponse(providerProtocolEndpointRepository.save(entity));
    }

    public void deleteProtocolEndpoint(Long siteProfileId, Long endpointId) {
        ProviderProtocolEndpointEntity entity = getEndpointRequired(siteProfileId, endpointId);
        long linkedCredentialCount = upstreamCredentialRepository.countByProtocolEndpointIdAndDeletedFalse(endpointId);
        if (linkedCredentialCount > 0) {
            throw new IllegalArgumentException("该协议入口仍有绑定凭证，请先解除绑定后再删除。");
        }
        providerProtocolEndpointRepository.delete(entity);
    }

    public ProviderSiteResponse refreshCapabilities(Long id) {
        return refreshCapabilitiesInternal(getRequired(id));
    }

    public List<ProviderSiteResponse> refreshCapabilities(List<Long> siteProfileIds) {
        List<UpstreamSiteProfileEntity> sites = siteProfileIds == null || siteProfileIds.isEmpty()
                ? upstreamSiteProfileRepository.findAllByActiveTrueOrderByDisplayNameAsc()
                : siteProfileIds.stream()
                        .map(this::getRequired)
                        .toList();
        return sites.stream()
                .map(this::refreshCapabilitiesInternal)
                .toList();
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
                        preferredBackend(item),
                        supportedBackends(item),
                        buildModelSurfaces(item),
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
        CooldownSummary cooldown = cooldownSummary(entity.getId());
        int modelCount = siteModelCapabilityRepository.findAllBySiteProfile_IdOrderByModelKeyAsc(entity.getId()).size();
        long linkedCredentialCount = upstreamCredentialRepository.countBySiteProfileIdAndDeletedFalse(entity.getId());
        ExecutionBackendDecision backendDecision = executionBackendPolicyService.forSiteSurface(
                entity,
                snapshot,
                TranslationResourceType.CHAT,
                TranslationOperation.CHAT_COMPLETION
        );
        return new CapabilityMatrixRowResponse(
                entity.getId(),
                entity.getProfileCode(),
                entity.getDisplayName(),
                entity.getProviderFamily(),
                entity.getSiteKind(),
                entity.getProfileSource(),
                entity.getAuthStrategy(),
                entity.getPathStrategy(),
                entity.getErrorSchemaStrategy(),
                snapshot == null ? "UNKNOWN" : snapshot.getHealthState(),
                snapshot == null ? null : snapshot.getBlockedReason(),
                snapshot == null ? List.of() : snapshot.getSupportedProtocols(),
                compatibilitySurface(entity),
                credentialRequirements(entity),
                snapshot == null ? null : snapshot.getStreamTransport(),
                snapshot == null ? null : snapshot.getFallbackStrategy(),
                cooldown.credentialCount(),
                cooldown.cooldownUntil(),
                linkedCredentialCount,
                snapshot != null,
                modelCount,
                snapshot == null ? null : snapshot.getRefreshedAt(),
                backendDecision.preferredBackend(),
                backendDecision.supportedBackends(),
                buildFeatureViews(entity, snapshot),
                buildSurfaceViews(entity, snapshot),
                siteCapabilityTruthService.supportsFeature(entity, snapshot, InteropFeature.RESPONSE_OBJECT),
                siteCapabilityTruthService.supportsFeature(entity, snapshot, InteropFeature.EMBEDDINGS),
                siteCapabilityTruthService.supportsFeature(entity, snapshot, InteropFeature.AUDIO_TRANSCRIPTION),
                siteCapabilityTruthService.supportsFeature(entity, snapshot, InteropFeature.IMAGE_GENERATION),
                siteCapabilityTruthService.supportsFeature(entity, snapshot, InteropFeature.MODERATION),
                siteCapabilityTruthService.supportsFeature(entity, snapshot, InteropFeature.FILE_OBJECT),
                siteCapabilityTruthService.supportsFeature(entity, snapshot, InteropFeature.UPLOAD_CREATE)
        );
    }

    private ProviderSiteResponse toResponse(UpstreamSiteProfileEntity entity) {
        SiteCapabilitySnapshotEntity snapshot = siteCapabilitySnapshotRepository.findBySiteProfile_Id(entity.getId()).orElse(null);
        int modelCount = siteModelCapabilityRepository.findAllBySiteProfile_IdOrderByModelKeyAsc(entity.getId()).size();
        CooldownSummary cooldown = cooldownSummary(entity.getId());
        long linkedCredentialCount = upstreamCredentialRepository.countBySiteProfileIdAndDeletedFalse(entity.getId());
        ExecutionBackendDecision backendDecision = executionBackendPolicyService.forSiteSurface(
                entity,
                snapshot,
                TranslationResourceType.CHAT,
                TranslationOperation.CHAT_COMPLETION
        );
        return new ProviderSiteResponse(
                entity.getId(),
                entity.getProfileCode(),
                entity.getDisplayName(),
                entity.getVendorCode(),
                entity.getVendorName(),
                entity.getProviderFamily(),
                entity.getSiteKind(),
                entity.getAuthStrategy(),
                entity.getPathStrategy(),
                entity.getModelAddressingStrategy(),
                entity.getErrorSchemaStrategy(),
                entity.getBaseUrlPattern(),
                entity.getDescription(),
                readObjectMap(entity.getConversationProfileJson()),
                entity.getProfileSource(),
                entity.isActive(),
                snapshot == null ? "UNKNOWN" : snapshot.getHealthState(),
                snapshot == null ? null : snapshot.getBlockedReason(),
                snapshot == null ? List.of() : snapshot.getSupportedProtocols(),
                compatibilitySurface(entity),
                credentialRequirements(entity),
                protocolEndpoints(entity.getId()),
                snapshot == null ? null : snapshot.getStreamTransport(),
                snapshot == null ? null : snapshot.getFallbackStrategy(),
                cooldown.credentialCount(),
                cooldown.cooldownUntil(),
                linkedCredentialCount,
                snapshot != null,
                backendDecision.preferredBackend(),
                backendDecision.supportedBackends(),
                buildFeatureViews(entity, snapshot),
                buildSurfaceViews(entity, snapshot),
                modelCount,
                snapshot == null ? null : snapshot.getRefreshedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private List<ProviderProtocolEndpointResponse> protocolEndpoints(Long siteProfileId) {
        if (providerProtocolEndpointRepository == null || siteProfileId == null) {
            return List.of();
        }
        return providerProtocolEndpointRepository.findAllBySiteProfileIdOrderByDisplayNameAsc(siteProfileId).stream()
                .map(this::toEndpointResponse)
                .toList();
    }

    private ProviderProtocolEndpointResponse toEndpointResponse(ProviderProtocolEndpointEntity entity) {
        return new ProviderProtocolEndpointResponse(
                entity.getId(),
                entity.getSiteProfileId(),
                entity.getEndpointCode(),
                entity.getDisplayName(),
                entity.getProtocolSuite(),
                entity.getProviderType(),
                entity.getSiteKind(),
                entity.getBaseUrl(),
                entity.getAuthStrategy(),
                entity.getPathStrategy(),
                entity.getModelAddressingStrategy(),
                entity.getErrorSchemaStrategy(),
                entity.getStreamTransport(),
                readObjectMap(entity.getConversationProfileJson()),
                entity.isActive(),
                entity.getId() == null ? 0L : upstreamCredentialRepository.countByProtocolEndpointIdAndDeletedFalse(entity.getId()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String compatibilitySurface(UpstreamSiteProfileEntity entity) {
        return switch (entity.getSiteKind()) {
            case ANTHROPIC_DIRECT -> "anthropic_native";
            case GEMINI_DIRECT, VERTEX_AI -> "google_native";
            case DIFY -> "dify-compatible";
            case COHERE, JINA -> "rerank";
            default -> "openai";
        };
    }

    private List<String> credentialRequirements(UpstreamSiteProfileEntity entity) {
        return switch (entity.getSiteKind()) {
            case VERTEX_AI -> List.of("google_access_token", "projectId", "location");
            case ANTHROPIC_DIRECT -> List.of("api_key_header");
            case GEMINI_DIRECT -> List.of("api_key_query");
            case AZURE_OPENAI -> List.of("azure_api_key");
            default -> List.of("api_key");
        };
    }

    private com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend preferredBackend(SiteModelCapabilityEntity item) {
        return executionBackendPolicyService.preferredBackendForSurface(
                TranslationResourceType.CHAT,
                TranslationOperation.CHAT_COMPLETION,
                executionBackendPolicyService.providerTypeForSite(item.getSiteProfile() == null ? null : item.getSiteProfile().getSiteKind()),
                item.getSiteProfile() == null ? null : item.getSiteProfile().getSiteKind()
        );
    }

    private List<com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend> supportedBackends(SiteModelCapabilityEntity item) {
        return executionBackendPolicyService.supportedBackendsForSurface(
                TranslationResourceType.CHAT,
                TranslationOperation.CHAT_COMPLETION,
                executionBackendPolicyService.providerTypeForSite(item.getSiteProfile() == null ? null : item.getSiteProfile().getSiteKind()),
                item.getSiteProfile() == null ? null : item.getSiteProfile().getSiteKind()
        );
    }

    private Map<String, CapabilityResolutionView> buildFeatureViews(
            UpstreamSiteProfileEntity entity,
            SiteCapabilitySnapshotEntity snapshot) {
        return Map.ofEntries(
                Map.entry(InteropFeature.CHAT_TEXT.wireName(), CapabilityResolutionView.from(siteCapabilityTruthService.resolve(entity, snapshot, InteropFeature.CHAT_TEXT))),
                Map.entry(InteropFeature.TOOLS.wireName(), CapabilityResolutionView.from(siteCapabilityTruthService.resolve(entity, snapshot, InteropFeature.TOOLS))),
                Map.entry(InteropFeature.IMAGE_INPUT.wireName(), CapabilityResolutionView.from(siteCapabilityTruthService.resolve(entity, snapshot, InteropFeature.IMAGE_INPUT))),
                Map.entry(InteropFeature.FILE_INPUT.wireName(), CapabilityResolutionView.from(siteCapabilityTruthService.resolve(entity, snapshot, InteropFeature.FILE_INPUT))),
                Map.entry(InteropFeature.RESPONSE_OBJECT.wireName(), CapabilityResolutionView.from(siteCapabilityTruthService.resolve(entity, snapshot, InteropFeature.RESPONSE_OBJECT))),
                Map.entry(InteropFeature.EMBEDDINGS.wireName(), CapabilityResolutionView.from(siteCapabilityTruthService.resolve(entity, snapshot, InteropFeature.EMBEDDINGS))),
                Map.entry(InteropFeature.REASONING.wireName(), CapabilityResolutionView.from(siteCapabilityTruthService.resolve(entity, snapshot, InteropFeature.REASONING))),
                Map.entry(InteropFeature.AUDIO_TRANSCRIPTION.wireName(), CapabilityResolutionView.from(siteCapabilityTruthService.resolve(entity, snapshot, InteropFeature.AUDIO_TRANSCRIPTION))),
                Map.entry(InteropFeature.AUDIO_SPEECH.wireName(), CapabilityResolutionView.from(siteCapabilityTruthService.resolve(entity, snapshot, InteropFeature.AUDIO_SPEECH))),
                Map.entry(InteropFeature.IMAGE_GENERATION.wireName(), CapabilityResolutionView.from(siteCapabilityTruthService.resolve(entity, snapshot, InteropFeature.IMAGE_GENERATION))),
                Map.entry(InteropFeature.MODERATION.wireName(), CapabilityResolutionView.from(siteCapabilityTruthService.resolve(entity, snapshot, InteropFeature.MODERATION))),
                Map.entry(InteropFeature.FILE_OBJECT.wireName(), CapabilityResolutionView.from(siteCapabilityTruthService.resolve(entity, snapshot, InteropFeature.FILE_OBJECT))),
                Map.entry(InteropFeature.UPLOAD_CREATE.wireName(), CapabilityResolutionView.from(siteCapabilityTruthService.resolve(entity, snapshot, InteropFeature.UPLOAD_CREATE))),
                Map.entry(InteropFeature.RERANK.wireName(), CapabilityResolutionView.from(siteCapabilityTruthService.resolve(entity, snapshot, InteropFeature.RERANK))),
                Map.entry(InteropFeature.VIDEO_GENERATION.wireName(), CapabilityResolutionView.from(siteCapabilityTruthService.resolve(entity, snapshot, InteropFeature.VIDEO_GENERATION))),
                Map.entry(InteropFeature.MUSIC_GENERATION.wireName(), CapabilityResolutionView.from(siteCapabilityTruthService.resolve(entity, snapshot, InteropFeature.MUSIC_GENERATION))),
                Map.entry(InteropFeature.WEB_SEARCH.wireName(), CapabilityResolutionView.from(siteCapabilityTruthService.resolve(entity, snapshot, InteropFeature.WEB_SEARCH)))
        );
    }

    private Map<String, SurfaceCapabilityView> buildSurfaceViews(
            UpstreamSiteProfileEntity entity,
            SiteCapabilitySnapshotEntity snapshot) {
        return Map.ofEntries(
                Map.entry("chat_completion", toSurface(
                        entity,
                        snapshot,
                        "openai",
                        "/v1/chat/completions",
                        TranslationResourceType.CHAT,
                        TranslationOperation.CHAT_COMPLETION,
                        List.of(InteropFeature.CHAT_TEXT)
                )),
                Map.entry("response_create", toSurface(
                        entity,
                        snapshot,
                        "responses",
                        "/v1/responses",
                        TranslationResourceType.RESPONSE,
                        TranslationOperation.RESPONSE_CREATE,
                        List.of(InteropFeature.RESPONSE_OBJECT)
                )),
                Map.entry("embedding_create", toSurface(
                        entity,
                        snapshot,
                        "openai",
                        "/v1/embeddings",
                        TranslationResourceType.EMBEDDING,
                        TranslationOperation.EMBEDDING_CREATE,
                        List.of(InteropFeature.EMBEDDINGS)
                )),
                Map.entry("audio_transcription", toSurface(
                        entity,
                        snapshot,
                        "openai",
                        "/v1/audio/transcriptions",
                        TranslationResourceType.AUDIO,
                        TranslationOperation.AUDIO_TRANSCRIPTION,
                        List.of(InteropFeature.AUDIO_TRANSCRIPTION)
                )),
                Map.entry("image_generation", toSurface(
                        entity,
                        snapshot,
                        "openai",
                        "/v1/images/generations",
                        TranslationResourceType.IMAGE,
                        TranslationOperation.IMAGE_GENERATION,
                        List.of(InteropFeature.IMAGE_GENERATION)
                )),
                Map.entry("moderation_create", toSurface(
                        entity,
                        snapshot,
                        "openai",
                        "/v1/moderations",
                        TranslationResourceType.MODERATION,
                        TranslationOperation.MODERATION_CREATE,
                        List.of(InteropFeature.MODERATION)
                )),
                Map.entry("file_create", toSurface(
                        entity,
                        snapshot,
                        "openai",
                        "/v1/files",
                        TranslationResourceType.FILE,
                        TranslationOperation.FILE_CREATE,
                        List.of(InteropFeature.FILE_OBJECT)
                )),
                Map.entry("upload_create", toSurface(
                        entity,
                        snapshot,
                        "openai",
                        "/v1/uploads",
                        TranslationResourceType.UPLOAD,
                        TranslationOperation.UPLOAD_CREATE,
                        List.of(InteropFeature.UPLOAD_CREATE, InteropFeature.FILE_OBJECT)
                )),
                Map.entry("rerank_create", toSurface(
                        entity,
                        snapshot,
                        "openai",
                        "/v1/rerank",
                        TranslationResourceType.RERANK,
                        TranslationOperation.RERANK_CREATE,
                        List.of(InteropFeature.RERANK)
                )),
                Map.entry("video_generation_create", toSurface(
                        entity,
                        snapshot,
                        "openai",
                        "/v1/videos/generations",
                        TranslationResourceType.VIDEO,
                        TranslationOperation.VIDEO_GENERATION_CREATE,
                        List.of(InteropFeature.VIDEO_GENERATION, InteropFeature.ASYNC_TASK)
                )),
                Map.entry("music_generation_create", toSurface(
                        entity,
                        snapshot,
                        "openai",
                        "/v1/music/generations",
                        TranslationResourceType.MUSIC,
                        TranslationOperation.MUSIC_GENERATION_CREATE,
                        List.of(InteropFeature.MUSIC_GENERATION, InteropFeature.ASYNC_TASK)
                )),
                Map.entry("web_search_create", toSurface(
                        entity,
                        snapshot,
                        "openai",
                        "/v1/web_search",
                        TranslationResourceType.WEB_SEARCH,
                        TranslationOperation.WEB_SEARCH_CREATE,
                        List.of(InteropFeature.WEB_SEARCH)
                ))
        );
    }

    private Map<String, SurfaceCapabilityView> buildModelSurfaces(SiteModelCapabilityEntity item) {
        InteropCapabilityLevel chatLevel = item.isSupportsChat() ? item.getCapabilityLevel() : InteropCapabilityLevel.UNSUPPORTED;
        InteropCapabilityLevel responseLevel = item.getSupportedProtocols().contains("responses") ? item.getCapabilityLevel() : InteropCapabilityLevel.UNSUPPORTED;
        InteropCapabilityLevel embeddingsLevel = item.isSupportsEmbeddings() ? item.getCapabilityLevel() : InteropCapabilityLevel.UNSUPPORTED;
        return Map.of(
                "chat_completion", modelSurface(item.getSiteProfile() == null ? null : item.getSiteProfile().getSiteKind(), "openai", "/v1/chat/completions", TranslationResourceType.CHAT, TranslationOperation.CHAT_COMPLETION, InteropFeature.CHAT_TEXT, chatLevel),
                "response_create", modelSurface(item.getSiteProfile() == null ? null : item.getSiteProfile().getSiteKind(), "responses", "/v1/responses", TranslationResourceType.RESPONSE, TranslationOperation.RESPONSE_CREATE, InteropFeature.RESPONSE_OBJECT, responseLevel),
                "embedding_create", modelSurface(item.getSiteProfile() == null ? null : item.getSiteProfile().getSiteKind(), "openai", "/v1/embeddings", TranslationResourceType.EMBEDDING, TranslationOperation.EMBEDDING_CREATE, InteropFeature.EMBEDDINGS, embeddingsLevel)
        );
    }

    private SurfaceCapabilityView toSurface(
            UpstreamSiteProfileEntity entity,
            SiteCapabilitySnapshotEntity snapshot,
            String protocol,
            String requestPath,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            List<InteropFeature> requiredFeatures) {
        GatewayRequestSemantics semantics = new GatewayRequestSemantics(resourceType, operation, requiredFeatures, true);
        ExecutionBackendDecision backendDecision = executionBackendPolicyService.forSiteSurface(entity, snapshot, resourceType, operation);
        SurfaceCompatibilityReport surfaceReport = siteCapabilityTruthService.evaluateSurface(
                entity,
                snapshot,
                semantics,
                backendDecision
        );
        InteropCapabilityLevel renderLevel = CanonicalRenderCapabilitySupport.renderLevel(
                protocol,
                requestPath,
                semantics
        );
        InteropCapabilityLevel overallCapabilityLevel = CanonicalRenderCapabilitySupport.minimum(
                surfaceReport.executionCapabilityLevel(),
                renderLevel
        );
        SupportStatus supportStatus = surfaceSupportStatus(
                entity.getSiteKind(),
                resourceType,
                operation,
                backendDecision,
                overallCapabilityLevel,
                surfaceReport.blockedReasons()
        );
        return new SurfaceCapabilityView(
                resourceType,
                operation,
                protocol,
                requestPath,
                backendDecision.preferredBackend(),
                backendDecision.supportedBackends(),
                supportStatus,
                SupportStatus.normalizeDegradationLevel(
                        overallCapabilityLevel,
                        surfaceReport.blockedReasons()
                ),
                surfaceReport.executionCapabilityLevel(),
                renderLevel,
                overallCapabilityLevel,
                surfaceReport.blockedReasons(),
                surfaceReport.lossReasons(),
                requiredFeatures.stream().map(InteropFeature::wireName).toList(),
                surfaceReport.featureResolutions().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> CapabilityResolutionView.from(entry.getValue()),
                                (left, right) -> left,
                                java.util.LinkedHashMap::new
                        ))
        );
    }

    private SupportStatus surfaceSupportStatus(
            com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind siteKind,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            ExecutionBackendDecision backendDecision,
            InteropCapabilityLevel overallCapabilityLevel,
            List<String> blockerReasons) {
        if (isNativeWrappedObjectSurface(siteKind, resourceType, operation, backendDecision, overallCapabilityLevel)) {
            return SupportStatus.NATIVE;
        }
        return SupportStatus.resolve(
                backendDecision == null ? null : backendDecision.preferredBackend(),
                overallCapabilityLevel,
                blockerReasons
        );
    }

    private boolean isNativeWrappedObjectSurface(
            com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind siteKind,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            ExecutionBackendDecision backendDecision,
            InteropCapabilityLevel overallCapabilityLevel) {
        if (backendDecision == null
                || backendDecision.preferredBackend() != com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend.ORCHESTRATION
                || overallCapabilityLevel != InteropCapabilityLevel.NATIVE) {
            return false;
        }
        return switch (siteKind) {
            case GEMINI_DIRECT, VERTEX_AI, ANTHROPIC_DIRECT -> resourceType == TranslationResourceType.FILE;
            default -> false;
        };
    }

    private SurfaceCapabilityView modelSurface(
            com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind siteKind,
            String protocol,
            String requestPath,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            InteropFeature feature,
            InteropCapabilityLevel executionLevel) {
        InteropCapabilityLevel renderLevel = CanonicalRenderCapabilitySupport.renderLevel(
                protocol,
                requestPath,
                new GatewayRequestSemantics(resourceType, operation, List.of(feature), true)
        );
        ExecutionBackendDecision backendDecision = executionBackendPolicyService.forSiteSurface(siteProfileForKind(siteKind), null, resourceType, operation);
        return new SurfaceCapabilityView(
                resourceType,
                operation,
                backendDecision.preferredBackend(),
                backendDecision.supportedBackends(),
                executionLevel,
                renderLevel,
                CanonicalRenderCapabilitySupport.minimum(executionLevel, renderLevel),
                List.of(feature.wireName()),
                Map.of(feature.wireName(), new CapabilityResolutionView(null, null, executionLevel.name().toLowerCase(), List.of(), List.of()))
        );
    }

    private UpstreamSiteProfileEntity siteProfileForKind(com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind siteKind) {
        if (siteKind == null) {
            return null;
        }
        UpstreamSiteProfileEntity entity = new UpstreamSiteProfileEntity();
        entity.setSiteKind(siteKind);
        return entity;
    }

    private UpstreamSiteProfileEntity getRequired(Long id) {
        Optional<UpstreamSiteProfileEntity> entity = upstreamSiteProfileRepository.findById(id);
        if (entity.isEmpty()) {
            throw new IllegalArgumentException("未找到指定的站点档案。");
        }
        return entity.get();
    }

    private ProviderProtocolEndpointEntity getEndpointRequired(Long siteProfileId, Long endpointId) {
        if (providerProtocolEndpointRepository == null) {
            throw new IllegalStateException("协议入口仓库未启用。");
        }
        ProviderProtocolEndpointEntity entity = providerProtocolEndpointRepository.findById(endpointId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的协议入口。"));
        if (!java.util.Objects.equals(entity.getSiteProfileId(), siteProfileId)) {
            throw new IllegalArgumentException("协议入口不属于指定站点档案。");
        }
        return entity;
    }

    private void ensureManualDefaultEndpoint(UpstreamSiteProfileEntity siteProfile) {
        if (providerProtocolEndpointRepository == null || siteProfile.getId() == null) {
            return;
        }
        if (providerProtocolEndpointRepository.countBySiteProfileId(siteProfile.getId()) > 0) {
            return;
        }
        UpstreamSitePolicyService.SitePolicy policy = providerSiteRegistryService.policy(siteProfile.getSiteKind());
        ProviderProtocolEndpointEntity endpoint = new ProviderProtocolEndpointEntity();
        endpoint.setSiteProfileId(siteProfile.getId());
        endpoint.setEndpointCode(siteProfile.getProfileCode() + ":default");
        endpoint.setDisplayName(siteProfile.getDisplayName() + " 默认入口");
        endpoint.setProtocolSuite(ProtocolSuite.fromVendorAndSiteKind(siteProfile.getVendorCode(), siteProfile.getSiteKind()));
        endpoint.setProviderType(executionBackendPolicyService.providerTypeForSite(siteProfile.getSiteKind()));
        endpoint.setSiteKind(siteProfile.getSiteKind());
        endpoint.setBaseUrl(baseUrlForEndpoint(siteProfile.getBaseUrlPattern()));
        endpoint.setAuthStrategy(policy.authStrategy());
        endpoint.setPathStrategy(policy.pathStrategy());
        endpoint.setModelAddressingStrategy(policy.modelAddressingStrategy());
        endpoint.setErrorSchemaStrategy(policy.errorSchemaStrategy());
        endpoint.setStreamTransport(policy.streamTransport());
        endpoint.setConversationProfileJson(siteProfile.getConversationProfileJson());
        endpoint.setActive(siteProfile.isActive());
        providerProtocolEndpointRepository.save(endpoint);
    }

    private void apply(UpstreamSiteProfileEntity entity, ProviderSiteRequest request) {
        var policy = providerSiteRegistryService.policy(request.siteKind());
        if (securityPolicyService != null) {
            securityPolicyService.assertUrlAllowed(request.baseUrlPattern());
        }
        entity.setProfileCode(request.profileCode().trim());
        entity.setDisplayName(request.displayName().trim());
        entity.setVendorCode(blankToNull(request.vendorCode()));
        entity.setVendorName(blankToNull(request.vendorName()));
        entity.setProviderFamily(policy.providerFamily());
        entity.setSiteKind(request.siteKind());
        entity.setAuthStrategy(policy.authStrategy());
        entity.setPathStrategy(policy.pathStrategy());
        entity.setModelAddressingStrategy(policy.modelAddressingStrategy());
        entity.setErrorSchemaStrategy(policy.errorSchemaStrategy());
        entity.setBaseUrlPattern(request.baseUrlPattern() == null ? null : request.baseUrlPattern().trim());
        entity.setDescription(request.description() == null ? null : request.description().trim());
        entity.setConversationProfileJson(writeObjectJson(request.conversationProfile()));
        if (entity.getProfileSource() == null) {
            entity.setProfileSource(SiteProfileSource.MANUAL);
        }
        entity.setActive(request.active() == null || request.active());
    }

    private void applyEndpoint(ProviderProtocolEndpointEntity entity, ProviderProtocolEndpointRequest request) {
        if (securityPolicyService != null) {
            securityPolicyService.assertUrlAllowed(request.baseUrl());
        }
        UpstreamSitePolicyService.SitePolicy policy = providerSiteRegistryService.policy(request.siteKind());
        entity.setEndpointCode(request.endpointCode().trim());
        entity.setDisplayName(request.displayName().trim());
        entity.setProtocolSuite(ProtocolSuite.normalize(request.protocolSuite()));
        entity.setProviderType(request.providerType());
        entity.setSiteKind(request.siteKind());
        entity.setBaseUrl(baseUrlForEndpoint(request.baseUrl()));
        entity.setAuthStrategy(defaultValue(request.authStrategy(), policy.authStrategy()));
        entity.setPathStrategy(defaultValue(request.pathStrategy(), policy.pathStrategy()));
        entity.setModelAddressingStrategy(defaultValue(request.modelAddressingStrategy(), policy.modelAddressingStrategy()));
        entity.setErrorSchemaStrategy(defaultValue(request.errorSchemaStrategy(), policy.errorSchemaStrategy()));
        entity.setStreamTransport(blankToNull(request.streamTransport()) == null ? policy.streamTransport() : request.streamTransport().trim());
        entity.setConversationProfileJson(writeObjectJson(request.conversationProfile()));
        entity.setActive(request.active() == null || request.active());
    }

    private String baseUrlForEndpoint(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("协议入口 Base URL 不能为空。");
        }
        return baseUrl.trim();
    }

    private <T> T defaultValue(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> readObjectMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Object value = objectMapper.readValue(json, Object.class);
            if (!(value instanceof Map<?, ?> map)) {
                return Map.of();
            }
            return map.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            entry -> String.valueOf(entry.getKey()),
                            Map.Entry::getValue,
                            (left, right) -> left,
                            java.util.LinkedHashMap::new
                    ));
        } catch (Exception exception) {
            return Map.of("invalidProfileJson", true);
        }
    }

    private String writeObjectJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("conversationProfile 必须是可序列化的 JSON 对象。", exception);
        }
    }

    private ProviderSiteResponse refreshCapabilitiesInternal(UpstreamSiteProfileEntity entity) {
        List<UpstreamCredentialEntity> credentials = upstreamCredentialRepository.findAllBySiteProfileIdAndDeletedFalseOrderByCreatedAtDesc(entity.getId());
        if (credentials.isEmpty()) {
            providerSiteRegistryService.refreshCapabilities(entity, List.of());
            return toResponse(entity);
        }
        for (UpstreamCredentialEntity credential : credentials) {
            credentialModelDiscoveryService.refreshCredential(credential.getId());
        }
        return toResponse(entity);
    }

    private CooldownSummary cooldownSummary(Long siteProfileId) {
        Instant now = Instant.now();
        List<UpstreamCredentialEntity> credentials =
                upstreamCredentialRepository.findAllBySiteProfileIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDesc(siteProfileId);
        List<Instant> cooldowns = credentials.stream()
                .map(UpstreamCredentialEntity::getCooldownUntil)
                .filter(value -> value != null && value.isAfter(now))
                .toList();
        Instant maxCooldownUntil = cooldowns.stream()
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new CooldownSummary(cooldowns.size(), maxCooldownUntil);
    }

    private record CooldownSummary(
            int credentialCount,
            Instant cooldownUntil
    ) {
    }
}
