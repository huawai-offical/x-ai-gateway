package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SiteCapabilityTruthService {

    private final UpstreamSitePolicyService upstreamSitePolicyService;
    private final SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository;

    public SiteCapabilityTruthService(
            UpstreamSitePolicyService upstreamSitePolicyService,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository) {
        this.upstreamSitePolicyService = upstreamSitePolicyService;
        this.siteCapabilitySnapshotRepository = siteCapabilitySnapshotRepository;
    }

    public InteropCapabilityLevel capabilityLevel(CatalogCandidateView candidate, InteropFeature feature) {
        if (candidate == null || candidate.siteKind() == null) {
            return InteropCapabilityLevel.UNSUPPORTED;
        }
        SiteCapabilitySnapshotEntity snapshot = candidate.siteProfileId() == null
                ? null
                : siteCapabilitySnapshotRepository.findBySiteProfile_Id(candidate.siteProfileId()).orElse(null);
        return capabilityLevel(
                candidate.siteKind(),
                snapshot,
                candidate.supportsChat(),
                candidate.supportsEmbeddings(),
                candidate.supportsThinking(),
                feature
        );
    }

    public boolean supportsFeature(
            UpstreamSiteProfileEntity siteProfile,
            SiteCapabilitySnapshotEntity snapshot,
            InteropFeature feature) {
        if (siteProfile == null) {
            return false;
        }
        return capabilityLevel(siteProfile.getSiteKind(), snapshot, true, true, true, feature)
                != InteropCapabilityLevel.UNSUPPORTED;
    }

    public TranslationExecutionPlan buildExecutionPlan(
            RouteSelectionResult selectionResult,
            String requestPath,
            List<InteropFeature> requiredFeatures,
            List<String> lossReasons,
            List<String> blockedReasons) {
        String resourceType = resourceType(requestPath);
        String operation = operation(requestPath);
        if (selectionResult == null) {
            return new TranslationExecutionPlan(
                    false,
                    resourceType,
                    operation,
                    null,
                    null,
                    ExecutionKind.BLOCKED,
                    InteropCapabilityLevel.UNSUPPORTED,
                    "blocked",
                    List.copyOf(lossReasons),
                    List.copyOf(blockedReasons),
                    null,
                    null,
                    null,
                    java.util.Map.of(),
                    java.util.Map.of()
            );
        }

        CatalogCandidateView candidate = selectionResult.selectedCandidate().candidate();
        InteropCapabilityLevel level = aggregateCapabilityLevel(candidate, requiredFeatures);
        ExecutionKind executionKind = !blockedReasons.isEmpty()
                ? ExecutionKind.BLOCKED
                : level == InteropCapabilityLevel.NATIVE
                ? ExecutionKind.NATIVE
                : level == InteropCapabilityLevel.EMULATED
                ? ExecutionKind.EMULATED
                : ExecutionKind.TRANSLATED;

        return new TranslationExecutionPlan(
                blockedReasons.isEmpty(),
                resourceType,
                operation,
                candidate.providerFamily(),
                candidate.siteProfileId(),
                executionKind,
                level,
                upstreamObjectMode(resourceType, executionKind),
                List.copyOf(lossReasons),
                List.copyOf(blockedReasons),
                candidate.authStrategy(),
                candidate.pathStrategy(),
                candidate.errorSchemaStrategy(),
                java.util.Map.of(
                        "protocol", selectionResult.protocol(),
                        "requestedModel", selectionResult.requestedModel(),
                        "resolvedModelKey", selectionResult.resolvedModelKey()
                ),
                java.util.Map.of(
                        "publicModel", selectionResult.publicModel(),
                        "selectionSource", selectionResult.selectionSource().name()
                )
        );
    }

    private InteropCapabilityLevel aggregateCapabilityLevel(
            CatalogCandidateView candidate,
            List<InteropFeature> requiredFeatures) {
        if (requiredFeatures == null || requiredFeatures.isEmpty()) {
            return InteropCapabilityLevel.NATIVE;
        }
        InteropCapabilityLevel level = InteropCapabilityLevel.NATIVE;
        for (InteropFeature feature : requiredFeatures) {
            InteropCapabilityLevel featureLevel = capabilityLevel(candidate, feature);
            if (featureLevel == InteropCapabilityLevel.UNSUPPORTED) {
                return InteropCapabilityLevel.UNSUPPORTED;
            }
            if (featureLevel == InteropCapabilityLevel.LOSSY) {
                level = InteropCapabilityLevel.LOSSY;
                continue;
            }
            if (featureLevel == InteropCapabilityLevel.EMULATED && level == InteropCapabilityLevel.NATIVE) {
                level = InteropCapabilityLevel.EMULATED;
            }
        }
        return level;
    }

    private InteropCapabilityLevel capabilityLevel(
            UpstreamSiteKind siteKind,
            SiteCapabilitySnapshotEntity snapshot,
            boolean supportsChat,
            boolean supportsEmbeddings,
            boolean supportsThinking,
            InteropFeature feature) {
        UpstreamSitePolicyService.SitePolicy policy = upstreamSitePolicyService.policy(siteKind);
        if (policy.blockedReason() != null) {
            return InteropCapabilityLevel.UNSUPPORTED;
        }

        return switch (feature) {
            case CHAT_TEXT -> supportsChat ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
            case TOOLS -> supportsChat && siteKind != UpstreamSiteKind.OLLAMA_DIRECT
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case IMAGE_INPUT -> switch (siteKind) {
                case OPENAI_DIRECT, OPENAI_COMPATIBLE_GENERIC, DEEPSEEK, GROK, MISTRAL, TOGETHER, FIREWORKS,
                        OPENROUTER, ANTHROPIC_DIRECT, GEMINI_DIRECT -> InteropCapabilityLevel.NATIVE;
                case AZURE_OPENAI -> InteropCapabilityLevel.LOSSY;
                default -> InteropCapabilityLevel.UNSUPPORTED;
            };
            case FILE_INPUT -> switch (siteKind) {
                case OPENAI_DIRECT, OPENAI_COMPATIBLE_GENERIC, DEEPSEEK, GROK, MISTRAL, TOGETHER, FIREWORKS,
                        OPENROUTER, GEMINI_DIRECT -> InteropCapabilityLevel.NATIVE;
                case ANTHROPIC_DIRECT -> InteropCapabilityLevel.EMULATED;
                default -> InteropCapabilityLevel.UNSUPPORTED;
            };
            case FILE_OBJECT -> hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsFiles)
                    && supportsUpstreamFileObjects(siteKind)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case REASONING -> supportsThinking ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
            case RESPONSE_OBJECT -> policy.supportedProtocols().contains("responses")
                    ? InteropCapabilityLevel.EMULATED
                    : InteropCapabilityLevel.UNSUPPORTED;
            case EMBEDDINGS -> supportsEmbeddings
                    && hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsEmbeddings)
                    && supportsUpstreamEmbeddings(siteKind)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case AUDIO_TRANSCRIPTION, AUDIO_TRANSLATION, AUDIO_SPEECH ->
                    hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsAudio)
                            && supportsUpstreamAudio(siteKind)
                            ? InteropCapabilityLevel.NATIVE
                            : InteropCapabilityLevel.UNSUPPORTED;
            case IMAGE_GENERATION, IMAGE_EDIT, IMAGE_VARIATION ->
                    hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsImages)
                            && supportsUpstreamImages(siteKind)
                            ? InteropCapabilityLevel.NATIVE
                            : InteropCapabilityLevel.UNSUPPORTED;
            case MODERATION -> hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsModeration)
                    && supportsUpstreamModeration(siteKind)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case UPLOAD_CREATE -> hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsUploads)
                    && supportsUpstreamAsyncObjects(siteKind)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case BATCH_CREATE -> hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsBatches)
                    && supportsUpstreamAsyncObjects(siteKind)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case TUNING_CREATE -> hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsTuning)
                    && supportsUpstreamAsyncObjects(siteKind)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case REALTIME_CLIENT_SECRET -> hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsRealtime)
                    && supportsUpstreamAsyncObjects(siteKind)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
        };
    }

    private boolean hasSnapshotCapability(
            SiteCapabilitySnapshotEntity snapshot,
            java.util.function.Predicate<SiteCapabilitySnapshotEntity> predicate) {
        return snapshot != null && predicate.test(snapshot);
    }

    private boolean supportsUpstreamEmbeddings(UpstreamSiteKind siteKind) {
        return switch (siteKind) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE_GENERIC, DEEPSEEK, GROK, MISTRAL, TOGETHER, FIREWORKS,
                    OPENROUTER, GEMINI_DIRECT, AZURE_OPENAI -> true;
            default -> false;
        };
    }

    private boolean supportsUpstreamAudio(UpstreamSiteKind siteKind) {
        return siteKind == UpstreamSiteKind.OPENAI_DIRECT;
    }

    private boolean supportsUpstreamImages(UpstreamSiteKind siteKind) {
        return siteKind == UpstreamSiteKind.OPENAI_DIRECT;
    }

    private boolean supportsUpstreamModeration(UpstreamSiteKind siteKind) {
        return siteKind == UpstreamSiteKind.OPENAI_DIRECT;
    }

    private boolean supportsUpstreamFileObjects(UpstreamSiteKind siteKind) {
        return siteKind == UpstreamSiteKind.OPENAI_DIRECT
                || siteKind == UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC
                || siteKind == UpstreamSiteKind.OPENROUTER
                || siteKind == UpstreamSiteKind.TOGETHER
                || siteKind == UpstreamSiteKind.FIREWORKS
                || siteKind == UpstreamSiteKind.DEEPSEEK
                || siteKind == UpstreamSiteKind.GROK
                || siteKind == UpstreamSiteKind.MISTRAL;
    }

    private boolean supportsUpstreamAsyncObjects(UpstreamSiteKind siteKind) {
        return siteKind == UpstreamSiteKind.OPENAI_DIRECT;
    }

    private String resourceType(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return "unknown";
        }
        if (requestPath.startsWith("/v1/chat/completions")) {
            return "chat";
        }
        if (requestPath.startsWith("/v1/responses")) {
            return "response";
        }
        if (requestPath.startsWith("/v1/embeddings")) {
            return "embedding";
        }
        if (requestPath.startsWith("/v1/audio")) {
            return "audio";
        }
        if (requestPath.startsWith("/v1/images")) {
            return "image";
        }
        if (requestPath.startsWith("/v1/moderations")) {
            return "moderation";
        }
        if (requestPath.startsWith("/v1/files")) {
            return "file";
        }
        if (requestPath.startsWith("/v1/uploads")) {
            return "upload";
        }
        if (requestPath.startsWith("/v1/batches")) {
            return "batch";
        }
        if (requestPath.startsWith("/v1/fine_tuning/jobs")) {
            return "tuning";
        }
        if (requestPath.startsWith("/v1/realtime")) {
            return "realtime";
        }
        return "unknown";
    }

    private String operation(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return "unknown";
        }
        return switch (requestPath) {
            case "/v1/chat/completions" -> "chat_completion";
            case "/v1/responses" -> "response_create";
            case "/v1/embeddings" -> "embedding_create";
            case "/v1/audio/transcriptions" -> "audio_transcription";
            case "/v1/audio/translations" -> "audio_translation";
            case "/v1/audio/speech" -> "audio_speech";
            case "/v1/images/generations" -> "image_generation";
            case "/v1/images/edits" -> "image_edit";
            case "/v1/images/variations" -> "image_variation";
            case "/v1/moderations" -> "moderation_create";
            case "/v1/files" -> "file_create";
            case "/v1/uploads" -> "upload_create";
            case "/v1/batches" -> "batch_create";
            case "/v1/fine_tuning/jobs" -> "tuning_create";
            case "/v1/realtime/client_secrets" -> "realtime_client_secret_create";
            default -> requestPath.replace('/', '_');
        };
    }

    private String upstreamObjectMode(String resourceType, ExecutionKind executionKind) {
        if (executionKind == ExecutionKind.BLOCKED) {
            return "blocked";
        }
        return switch (resourceType) {
            case "file", "upload", "batch", "tuning", "realtime", "response" -> "upstream_object_with_local_lineage";
            case "chat", "embedding", "audio", "image", "moderation" -> executionKind == ExecutionKind.NATIVE
                    ? "direct_upstream_execution"
                    : "translated_execution";
            default -> "direct_upstream_execution";
        };
    }
}
