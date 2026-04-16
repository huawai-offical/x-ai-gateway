package com.prodigalgal.xaigateway.gateway.core.interop;

import tools.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRenderCapabilitySupport;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.NonChatCanonicalRenderService;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendDecision;
import com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendPolicyService;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NonChatRoutePolicyService {

    private final SiteCapabilityTruthService siteCapabilityTruthService;
    private final ExecutionBackendPolicyService executionBackendPolicyService;
    private final NonChatCanonicalRenderService nonChatCanonicalRenderService;
    private final UpstreamSiteProfileRepository upstreamSiteProfileRepository;
    private final SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository;

    public NonChatRoutePolicyService(
            SiteCapabilityTruthService siteCapabilityTruthService,
            ExecutionBackendPolicyService executionBackendPolicyService,
            NonChatCanonicalRenderService nonChatCanonicalRenderService,
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository) {
        this.siteCapabilityTruthService = siteCapabilityTruthService;
        this.executionBackendPolicyService = executionBackendPolicyService;
        this.nonChatCanonicalRenderService = nonChatCanonicalRenderService;
        this.upstreamSiteProfileRepository = upstreamSiteProfileRepository;
        this.siteCapabilitySnapshotRepository = siteCapabilitySnapshotRepository;
    }

    public static NonChatRoutePolicyService forTests(
            SiteCapabilityTruthService siteCapabilityTruthService,
            ExecutionBackendPolicyService executionBackendPolicyService) {
        return new NonChatRoutePolicyService(
                siteCapabilityTruthService,
                executionBackendPolicyService,
                null,
                null,
                null
        );
    }

    public NonChatRoutePolicyDecision evaluateCandidate(
            String protocol,
            String requestPath,
            GatewayRequestSemantics semantics,
            CatalogCandidateView candidate,
            CanonicalRequest canonicalRequest,
            JsonNode requestBody) {
        return evaluate(
                protocol,
                requestPath,
                semantics,
                candidate,
                canonicalRequest,
                requestBody,
                "catalog_selection"
        );
    }

    public NonChatRoutePolicyDecision evaluateResolvedTarget(
            String protocol,
            String requestPath,
            GatewayRequestSemantics semantics,
            CatalogCandidateView candidate,
            CanonicalRequest canonicalRequest,
            JsonNode requestBody,
            String resolutionReason,
            List<String> resolutionBlockedReasons) {
        return evaluate(
                protocol,
                requestPath,
                semantics,
                candidate,
                canonicalRequest,
                requestBody,
                appendReason(selectionModeReason(semantics), resolutionReason),
                resolutionBlockedReasons
        );
    }

    public NonChatRoutePolicyDecision evaluateWithoutCandidate(
            String protocol,
            String requestPath,
            GatewayRequestSemantics semantics,
            CanonicalRequest canonicalRequest,
            JsonNode requestBody,
            String resolutionReason,
            List<String> resolutionBlockedReasons) {
        ExecutionBackendDecision backendDecision = executionBackendPolicyService.forSemantics(semantics);
        InteropCapabilityLevel executionCapabilityLevel = resolutionBlockedReasons == null || resolutionBlockedReasons.isEmpty()
                ? InteropCapabilityLevel.NATIVE
                : InteropCapabilityLevel.UNSUPPORTED;
        InteropCapabilityLevel renderCapabilityLevel = renderCapabilityLevel(protocol, requestPath, semantics);
        InteropCapabilityLevel overallCapabilityLevel = CanonicalRenderCapabilitySupport.minimum(
                executionCapabilityLevel,
                renderCapabilityLevel
        );
        List<String> blockedReasons = mergeBlockedReasons(
                resolutionBlockedReasons,
                overallCapabilityLevel == InteropCapabilityLevel.UNSUPPORTED && renderCapabilityLevel == InteropCapabilityLevel.UNSUPPORTED
                        ? List.of("当前 ingress 尚无可用 render shape。")
                        : List.of()
        );
        return new NonChatRoutePolicyDecision(
                semantics.routeSelectionMode(),
                backendDecision.preferredBackend(),
                backendDecision.supportedBackends(),
                executionCapabilityLevel,
                renderCapabilityLevel,
                overallCapabilityLevel,
                SupportStatus.resolve(backendDecision.preferredBackend(), overallCapabilityLevel, blockedReasons),
                objectMode(semantics, backendDecision.preferredBackend(), "gateway-object-lineage"),
                blockedReasons,
                List.of(),
                appendReason(selectionModeReason(semantics), appendReason(resolutionReason, backendDecision.reason()))
        );
    }

    private NonChatRoutePolicyDecision evaluate(
            String protocol,
            String requestPath,
            GatewayRequestSemantics semantics,
            CatalogCandidateView candidate,
            CanonicalRequest canonicalRequest,
            JsonNode requestBody,
            String resolutionReason) {
        return evaluate(protocol, requestPath, semantics, candidate, canonicalRequest, requestBody, resolutionReason, List.of());
    }

    private NonChatRoutePolicyDecision evaluate(
            String protocol,
            String requestPath,
            GatewayRequestSemantics semantics,
            CatalogCandidateView candidate,
            CanonicalRequest canonicalRequest,
            JsonNode requestBody,
            String resolutionReason,
            List<String> resolutionBlockedReasons) {
        if (candidate == null) {
            return evaluateWithoutCandidate(
                    protocol,
                    requestPath,
                    semantics,
                    canonicalRequest,
                    requestBody,
                    resolutionReason,
                    resolutionBlockedReasons
            );
        }

        CapabilityResolutionReport report = siteCapabilityTruthService.resolve(candidate, semantics);
        ExecutionBackendDecision backendDecision = executionBackendPolicyService.forCandidate(
                candidate,
                semantics,
                canonicalRequest,
                requestBody
        );
        SurfaceCompatibilityReport surfaceReport = resolveSurfaceReport(candidate, semantics, backendDecision);
        boolean surfaceLifted = shouldLiftSurface(report, surfaceReport, backendDecision);

        InteropCapabilityLevel executionCapabilityLevel = surfaceLifted
                ? surfaceReport.executionCapabilityLevel()
                : report.overallEffectiveLevel();
        InteropCapabilityLevel renderCapabilityLevel = renderCapabilityLevel(protocol, requestPath, semantics);
        InteropCapabilityLevel overallCapabilityLevel = CanonicalRenderCapabilitySupport.minimum(
                executionCapabilityLevel,
                renderCapabilityLevel
        );

        List<String> blockedReasons = mergeBlockedReasons(
                resolutionBlockedReasons,
                surfaceLifted ? surfaceReport.blockedReasons() : report.blockedReasons(),
                overallCapabilityLevel == InteropCapabilityLevel.UNSUPPORTED && renderCapabilityLevel == InteropCapabilityLevel.UNSUPPORTED
                        ? List.of("当前 ingress 尚无可用 render shape。")
                        : List.of()
        );
        List<String> lossReasons = surfaceLifted ? surfaceReport.lossReasons() : report.lossReasons();
        String upstreamObjectMode = surfaceLifted ? "gateway-object-lineage" : report.upstreamObjectMode();

        return new NonChatRoutePolicyDecision(
                semantics.routeSelectionMode(),
                backendDecision.preferredBackend(),
                backendDecision.supportedBackends(),
                executionCapabilityLevel,
                renderCapabilityLevel,
                overallCapabilityLevel,
                SupportStatus.resolve(backendDecision.preferredBackend(), overallCapabilityLevel, blockedReasons),
                objectMode(semantics, backendDecision.preferredBackend(), upstreamObjectMode),
                blockedReasons,
                lossReasons,
                appendReason(selectionModeReason(semantics), appendReason(resolutionReason, backendDecision.reason()))
        );
    }

    private SurfaceCompatibilityReport resolveSurfaceReport(
            CatalogCandidateView candidate,
            GatewayRequestSemantics semantics,
            ExecutionBackendDecision backendDecision) {
        if (candidate == null) {
            return new SurfaceCompatibilityReport(
                    java.util.Map.of(),
                    InteropCapabilityLevel.UNSUPPORTED,
                    List.of(),
                    List.of()
            );
        }
        UpstreamSiteProfileEntity siteProfile = null;
        SiteCapabilitySnapshotEntity snapshot = null;
        if (candidate.siteProfileId() != null
                && upstreamSiteProfileRepository != null
                && siteCapabilitySnapshotRepository != null) {
            siteProfile = upstreamSiteProfileRepository.findById(candidate.siteProfileId()).orElse(null);
            snapshot = siteCapabilitySnapshotRepository.findBySiteProfile_Id(candidate.siteProfileId()).orElse(null);
        }
        if (siteProfile == null) {
            siteProfile = syntheticSiteProfile(candidate);
        }
        if (snapshot == null) {
            snapshot = syntheticSnapshot(candidate, siteProfile);
        }
        SurfaceCompatibilityReport surfaceReport = siteCapabilityTruthService.evaluateSurface(
                siteProfile,
                snapshot,
                semantics,
                backendDecision
        );
        return surfaceReport == null ? fallbackSurfaceReport(candidate, semantics, backendDecision) : surfaceReport;
    }

    private UpstreamSiteProfileEntity syntheticSiteProfile(CatalogCandidateView candidate) {
        if (candidate == null) {
            return null;
        }
        UpstreamSiteProfileEntity siteProfile = new UpstreamSiteProfileEntity();
        siteProfile.setProfileCode("synthetic");
        siteProfile.setDisplayName("synthetic");
        siteProfile.setProviderFamily(candidate.providerFamily() == null
                ? providerFamilyFor(candidate.providerType())
                : candidate.providerFamily());
        siteProfile.setSiteKind(effectiveSiteKind(candidate));
        siteProfile.setAuthStrategy(candidate.authStrategy());
        siteProfile.setPathStrategy(candidate.pathStrategy());
        siteProfile.setErrorSchemaStrategy(candidate.errorSchemaStrategy());
        siteProfile.setActive(true);
        return siteProfile;
    }

    private SiteCapabilitySnapshotEntity syntheticSnapshot(
            CatalogCandidateView candidate,
            UpstreamSiteProfileEntity siteProfile) {
        if (candidate == null || siteProfile == null) {
            return null;
        }
        SiteCapabilitySnapshotEntity snapshot = new SiteCapabilitySnapshotEntity();
        snapshot.setSiteProfile(siteProfile);
        snapshot.setSupportedProtocols(candidate.supportedProtocols());
        snapshot.setSupportsResponses(candidate.supportsChat());
        snapshot.setSupportsEmbeddings(candidate.supportsEmbeddings());
        snapshot.setSupportsAudio(candidate.providerType() == ProviderType.OPENAI_DIRECT
                || candidate.providerType() == ProviderType.OPENAI_COMPATIBLE
                || candidate.providerType() == ProviderType.GEMINI_DIRECT);
        snapshot.setSupportsImages(candidate.providerType() == ProviderType.OPENAI_DIRECT
                || candidate.providerType() == ProviderType.OPENAI_COMPATIBLE
                || candidate.providerType() == ProviderType.GEMINI_DIRECT);
        snapshot.setSupportsModeration(candidate.providerType() == ProviderType.OPENAI_DIRECT
                || candidate.providerType() == ProviderType.OPENAI_COMPATIBLE
                || candidate.providerType() == ProviderType.GEMINI_DIRECT);
        snapshot.setSupportsFiles(true);
        snapshot.setSupportsUploads(true);
        snapshot.setSupportsBatches(true);
        snapshot.setSupportsTuning(true);
        snapshot.setSupportsRealtime(true);
        snapshot.setAuthStrategy(candidate.authStrategy());
        snapshot.setPathStrategy(candidate.pathStrategy());
        snapshot.setErrorSchemaStrategy(candidate.errorSchemaStrategy());
        snapshot.setHealthState("HEALTHY");
        return snapshot;
    }

    private boolean shouldLiftSurface(
            CapabilityResolutionReport report,
            SurfaceCompatibilityReport surfaceReport,
            ExecutionBackendDecision backendDecision) {
        return report != null
                && surfaceReport != null
                && backendDecision != null
                && backendDecision.preferredBackend() == ExecutionBackend.ORCHESTRATION
                && report.overallEffectiveLevel() == InteropCapabilityLevel.UNSUPPORTED
                && surfaceReport.executionCapabilityLevel() != InteropCapabilityLevel.UNSUPPORTED
                && surfaceReport.blockedReasons().isEmpty();
    }

    private List<String> mergeBlockedReasons(List<String>... groups) {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        for (List<String> group : groups) {
            if (group == null) {
                continue;
            }
            for (String item : group) {
                if (item != null && !item.isBlank()) {
                    merged.add(item);
                }
            }
        }
        return List.copyOf(merged);
    }

    private String selectionModeReason(GatewayRequestSemantics semantics) {
        if (semantics == null || semantics.routeSelectionMode() == null) {
            return "";
        }
        return switch (semantics.routeSelectionMode()) {
            case CATALOG_SELECTION -> "selection_mode=catalog_selection";
            case LOCAL_CATALOG -> "selection_mode=local_catalog";
            case STORED_LINEAGE -> "selection_mode=stored_lineage";
            case DISTRIBUTED_TARGET -> "selection_mode=distributed_target";
        };
    }

    private String objectMode(
            GatewayRequestSemantics semantics,
            ExecutionBackend backend,
            String upstreamObjectMode) {
        if (semantics == null || backend == null) {
            return upstreamObjectMode;
        }
        return switch (backend) {
            case NATIVE -> semantics.resourceType() == TranslationResourceType.CHAT
                    || semantics.resourceType() == TranslationResourceType.RESPONSE
                    ? upstreamObjectMode
                    : "native-resource";
            case PASSTHROUGH -> "passthrough-resource";
            case ORCHESTRATION -> "gateway-object-lineage";
            case SPRING_AI -> upstreamObjectMode;
        };
    }

    private String appendReason(String left, String right) {
        if (left == null || left.isBlank()) {
            return right == null ? "" : right;
        }
        if (right == null || right.isBlank()) {
            return left;
        }
        return left + " | " + right;
    }

    private InteropCapabilityLevel renderCapabilityLevel(
            String protocol,
            String requestPath,
            GatewayRequestSemantics semantics
    ) {
        if (nonChatCanonicalRenderService != null) {
            return nonChatCanonicalRenderService.renderLevel(protocol, requestPath, semantics);
        }
        return CanonicalRenderCapabilitySupport.renderLevel(protocol, requestPath, semantics);
    }

    private SurfaceCompatibilityReport fallbackSurfaceReport(
            CatalogCandidateView candidate,
            GatewayRequestSemantics semantics,
            ExecutionBackendDecision backendDecision) {
        if (candidate == null || semantics == null || backendDecision == null) {
            return new SurfaceCompatibilityReport(java.util.Map.of(), InteropCapabilityLevel.UNSUPPORTED, List.of(), List.of());
        }
        UpstreamSiteKind siteKind = effectiveSiteKind(candidate);
        boolean orchestrationSurface = backendDecision.preferredBackend() == ExecutionBackend.ORCHESTRATION
                && switch (siteKind) {
                    case GEMINI_DIRECT, VERTEX_AI -> semantics.resourceType() == TranslationResourceType.FILE
                            || semantics.resourceType() == TranslationResourceType.UPLOAD
                            || semantics.resourceType() == TranslationResourceType.BATCH
                            || semantics.resourceType() == TranslationResourceType.TUNING;
                    case ANTHROPIC_DIRECT -> semantics.resourceType() == TranslationResourceType.FILE
                            || semantics.operation() == TranslationOperation.ANTHROPIC_MESSAGE_BATCH_CREATE
                            || semantics.operation() == TranslationOperation.ANTHROPIC_MESSAGE_BATCH_GET
                            || semantics.operation() == TranslationOperation.ANTHROPIC_MESSAGE_BATCH_CANCEL;
                    default -> false;
                };
        return orchestrationSurface
                ? new SurfaceCompatibilityReport(java.util.Map.of(), InteropCapabilityLevel.NATIVE, List.of(), List.of())
                : new SurfaceCompatibilityReport(java.util.Map.of(), InteropCapabilityLevel.UNSUPPORTED, List.of(), List.of());
    }

    private UpstreamSiteKind effectiveSiteKind(CatalogCandidateView candidate) {
        if (candidate == null) {
            return UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC;
        }
        if (candidate.siteKind() != null) {
            return candidate.siteKind();
        }
        return fallbackSiteKind(candidate.providerType());
    }

    private UpstreamSiteKind fallbackSiteKind(ProviderType providerType) {
        if (providerType == null) {
            return UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC;
        }
        return switch (providerType) {
            case OPENAI_DIRECT -> UpstreamSiteKind.OPENAI_DIRECT;
            case OPENAI_COMPATIBLE -> UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC;
            case ANTHROPIC_DIRECT -> UpstreamSiteKind.ANTHROPIC_DIRECT;
            case GEMINI_DIRECT -> UpstreamSiteKind.GEMINI_DIRECT;
            case OLLAMA_DIRECT -> UpstreamSiteKind.OLLAMA_DIRECT;
        };
    }

    private ProviderFamily providerFamilyFor(ProviderType providerType) {
        if (providerType == null) {
            return ProviderFamily.OPENAI;
        }
        return switch (providerType) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE -> ProviderFamily.OPENAI;
            case ANTHROPIC_DIRECT -> ProviderFamily.ANTHROPIC;
            case GEMINI_DIRECT -> ProviderFamily.GEMINI;
            case OLLAMA_DIRECT -> ProviderFamily.OLLAMA;
        };
    }
}
