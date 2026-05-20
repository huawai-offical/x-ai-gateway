package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.RouteSelectionMode;
import com.prodigalgal.xaigateway.gateway.core.interop.SupportStatus;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import java.util.List;
import java.util.Map;

public record CanonicalExecutionPlan(
        boolean executable,
        CanonicalIngressProtocol ingressProtocol,
        String requestPath,
        String normalizedPath,
        String surface,
        String requestedModel,
        String publicModel,
        String resolvedModel,
        TranslationResourceType resourceType,
        TranslationOperation operation,
        ExecutionKind executionKind,
        ExecutionBackend executionBackend,
        SupportStatus supportStatus,
        String objectMode,
        List<ExecutionBackend> supportedBackends,
        String backendReason,
        InteropCapabilityLevel degradationLevel,
        InteropCapabilityLevel executionCapabilityLevel,
        InteropCapabilityLevel renderCapabilityLevel,
        InteropCapabilityLevel overallCapabilityLevel,
        List<String> blockerReasons,
        List<InteropFeature> requiredFeatures,
        Map<String, InteropCapabilityLevel> featureLevels,
        List<String> degradations,
        List<String> blockers,
        RouteSelectionMode routeSelectionMode,
        String routePolicyReason,
        String renderPolicyReason,
        String fallbackPolicyReason
) {
    public CanonicalExecutionPlan {
        resourceType = resourceType == null ? TranslationResourceType.UNKNOWN : resourceType;
        operation = operation == null ? TranslationOperation.UNKNOWN : operation;
        normalizedPath = normalizedPath == null || normalizedPath.isBlank()
                ? (requestPath == null || requestPath.isBlank() ? defaultNormalizedPath(resourceType, operation) : requestPath)
                : normalizedPath;
        surface = surface == null || surface.isBlank() ? defaultSurface(resourceType, operation) : surface;
        supportedBackends = supportedBackends == null ? List.of() : List.copyOf(supportedBackends);
        blockerReasons = blockerReasons == null ? (blockers == null ? List.of() : List.copyOf(blockers)) : List.copyOf(blockerReasons);
        requiredFeatures = requiredFeatures == null ? List.of() : List.copyOf(requiredFeatures);
        featureLevels = featureLevels == null ? Map.of() : Map.copyOf(featureLevels);
        degradations = degradations == null ? List.of() : List.copyOf(degradations);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        routeSelectionMode = routeSelectionMode == null ? RouteSelectionMode.CATALOG_SELECTION : routeSelectionMode;
        routePolicyReason = routePolicyReason == null ? "" : routePolicyReason;
        renderPolicyReason = renderPolicyReason == null ? "" : renderPolicyReason;
        fallbackPolicyReason = fallbackPolicyReason == null ? "" : fallbackPolicyReason;
        degradationLevel = degradationLevel == null
                ? SupportStatus.normalizeDegradationLevel(overallCapabilityLevel, blockerReasons)
                : degradationLevel;
        supportStatus = supportStatus == null
                ? SupportStatus.resolve(executionBackend, degradationLevel, blockerReasons)
                : supportStatus;
    }

    public CanonicalExecutionPlan(
            boolean executable,
            CanonicalIngressProtocol ingressProtocol,
            String requestPath,
            String requestedModel,
            String publicModel,
            String resolvedModel,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            ExecutionKind executionKind,
            InteropCapabilityLevel executionCapabilityLevel,
            InteropCapabilityLevel renderCapabilityLevel,
            InteropCapabilityLevel overallCapabilityLevel,
            List<InteropFeature> requiredFeatures,
            Map<String, InteropCapabilityLevel> featureLevels,
            List<String> degradations,
            List<String> blockers
    ) {
        this(
                executable,
                ingressProtocol,
                requestPath,
                requestPath,
                defaultSurface(resourceType, operation),
                requestedModel,
                publicModel,
                resolvedModel,
                resourceType,
                operation,
                executionKind,
                ExecutionBackend.SPRING_AI,
                null,
                null,
                List.of(ExecutionBackend.SPRING_AI),
                "legacy_default",
                null,
                executionCapabilityLevel,
                renderCapabilityLevel,
                overallCapabilityLevel,
                blockers,
                requiredFeatures,
                featureLevels,
                degradations,
                blockers,
                RouteSelectionMode.CATALOG_SELECTION,
                "",
                "",
                ""
        );
    }

    public CanonicalExecutionPlan(
            boolean executable,
            CanonicalIngressProtocol ingressProtocol,
            String requestPath,
            String requestedModel,
            String publicModel,
            String resolvedModel,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            ExecutionKind executionKind,
            ExecutionBackend executionBackend,
            List<ExecutionBackend> supportedBackends,
            String backendReason,
            InteropCapabilityLevel executionCapabilityLevel,
            InteropCapabilityLevel renderCapabilityLevel,
            InteropCapabilityLevel overallCapabilityLevel,
            List<InteropFeature> requiredFeatures,
            Map<String, InteropCapabilityLevel> featureLevels,
            List<String> degradations,
            List<String> blockers
    ) {
        this(
                executable,
                ingressProtocol,
                requestPath,
                requestPath,
                defaultSurface(resourceType, operation),
                requestedModel,
                publicModel,
                resolvedModel,
                resourceType,
                operation,
                executionKind,
                executionBackend,
                null,
                null,
                supportedBackends,
                backendReason,
                null,
                executionCapabilityLevel,
                renderCapabilityLevel,
                overallCapabilityLevel,
                blockers,
                requiredFeatures,
                featureLevels,
                degradations,
                blockers,
                RouteSelectionMode.CATALOG_SELECTION,
                "",
                "",
                ""
        );
    }

    public CanonicalExecutionPlan(
            boolean executable,
            CanonicalIngressProtocol ingressProtocol,
            String requestPath,
            String normalizedPath,
            String surface,
            String requestedModel,
            String publicModel,
            String resolvedModel,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            ExecutionKind executionKind,
            ExecutionBackend executionBackend,
            String objectMode,
            List<ExecutionBackend> supportedBackends,
            String backendReason,
            InteropCapabilityLevel executionCapabilityLevel,
            InteropCapabilityLevel renderCapabilityLevel,
            InteropCapabilityLevel overallCapabilityLevel,
            List<InteropFeature> requiredFeatures,
            Map<String, InteropCapabilityLevel> featureLevels,
            List<String> degradations,
            List<String> blockers
    ) {
        this(
                executable,
                ingressProtocol,
                requestPath,
                normalizedPath,
                surface,
                requestedModel,
                publicModel,
                resolvedModel,
                resourceType,
                operation,
                executionKind,
                executionBackend,
                null,
                objectMode,
                supportedBackends,
                backendReason,
                null,
                executionCapabilityLevel,
                renderCapabilityLevel,
                overallCapabilityLevel,
                blockers,
                requiredFeatures,
                featureLevels,
                degradations,
                blockers,
                RouteSelectionMode.CATALOG_SELECTION,
                "",
                "",
                ""
        );
    }

    public CanonicalExecutionPlan(
            boolean executable,
            CanonicalIngressProtocol ingressProtocol,
            String requestPath,
            String normalizedPath,
            String surface,
            String requestedModel,
            String publicModel,
            String resolvedModel,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            ExecutionKind executionKind,
            ExecutionBackend executionBackend,
            SupportStatus supportStatus,
            String objectMode,
            List<ExecutionBackend> supportedBackends,
            String backendReason,
            InteropCapabilityLevel degradationLevel,
            InteropCapabilityLevel executionCapabilityLevel,
            InteropCapabilityLevel renderCapabilityLevel,
            InteropCapabilityLevel overallCapabilityLevel,
            List<String> blockerReasons,
            List<InteropFeature> requiredFeatures,
            Map<String, InteropCapabilityLevel> featureLevels,
            List<String> degradations,
            List<String> blockers
    ) {
        this(
                executable,
                ingressProtocol,
                requestPath,
                normalizedPath,
                surface,
                requestedModel,
                publicModel,
                resolvedModel,
                resourceType,
                operation,
                executionKind,
                executionBackend,
                supportStatus,
                objectMode,
                supportedBackends,
                backendReason,
                degradationLevel,
                executionCapabilityLevel,
                renderCapabilityLevel,
                overallCapabilityLevel,
                blockerReasons,
                requiredFeatures,
                featureLevels,
                degradations,
                blockers,
                RouteSelectionMode.CATALOG_SELECTION,
                "",
                "",
                ""
        );
    }

    private static String defaultSurface(TranslationResourceType resourceType, TranslationOperation operation) {
        return switch (operation == null ? TranslationOperation.UNKNOWN : operation) {
            case CHAT_COMPLETION -> "chat.completions";
            case RESPONSE_CREATE -> "responses";
            case EMBEDDING_CREATE -> "embeddings";
            case AUDIO_TRANSCRIPTION, AUDIO_TRANSLATION, AUDIO_SPEECH -> "audio";
            case IMAGE_GENERATION, IMAGE_EDIT, IMAGE_VARIATION -> "images";
            case MODERATION_CREATE -> "moderations";
            case FILE_CREATE, FILE_LIST, FILE_GET, FILE_CONTENT_GET, FILE_DELETE -> "files";
            case UPLOAD_CREATE, UPLOAD_GET, UPLOAD_PART_ADD, UPLOAD_COMPLETE, UPLOAD_CANCEL -> "uploads";
            case REALTIME_CLIENT_SECRET_CREATE -> "realtime";
            case RERANK_CREATE -> "rerank";
            case VIDEO_GENERATION_CREATE, VIDEO_GENERATION_GET, VIDEO_GENERATION_CANCEL -> "videos";
            case MUSIC_GENERATION_CREATE, MUSIC_GENERATION_GET, MUSIC_GENERATION_CANCEL -> "music";
            case TASK_GET, TASK_CANCEL -> "tasks";
            case WEB_SEARCH_CREATE -> "web_search";
            case UNKNOWN -> resourceType == null ? "unknown" : resourceType.wireName();
        };
    }

    private static String defaultNormalizedPath(TranslationResourceType resourceType, TranslationOperation operation) {
        return switch (operation == null ? TranslationOperation.UNKNOWN : operation) {
            case CHAT_COMPLETION -> "/v1/chat/completions";
            case RESPONSE_CREATE -> "/v1/responses";
            case EMBEDDING_CREATE -> "/v1/embeddings";
            case AUDIO_TRANSCRIPTION -> "/v1/audio/transcriptions";
            case AUDIO_TRANSLATION -> "/v1/audio/translations";
            case AUDIO_SPEECH -> "/v1/audio/speech";
            case IMAGE_GENERATION -> "/v1/images/generations";
            case IMAGE_EDIT -> "/v1/images/edits";
            case IMAGE_VARIATION -> "/v1/images/variations";
            case MODERATION_CREATE -> "/v1/moderations";
            case FILE_CREATE, FILE_LIST -> "/v1/files";
            case FILE_GET, FILE_DELETE -> "/v1/files/{fileId}";
            case FILE_CONTENT_GET -> "/v1/files/{fileId}/content";
            case UPLOAD_CREATE -> "/v1/uploads";
            case UPLOAD_GET -> "/v1/uploads/{uploadId}";
            case UPLOAD_PART_ADD -> "/v1/uploads/{uploadId}/parts";
            case UPLOAD_COMPLETE -> "/v1/uploads/{uploadId}/complete";
            case UPLOAD_CANCEL -> "/v1/uploads/{uploadId}/cancel";
            case REALTIME_CLIENT_SECRET_CREATE -> "/v1/realtime/client_secrets";
            case RERANK_CREATE -> "/v1/rerank";
            case VIDEO_GENERATION_CREATE -> "/v1/videos/generations";
            case VIDEO_GENERATION_GET -> "/v1/videos/{taskId}";
            case VIDEO_GENERATION_CANCEL -> "/v1/videos/{taskId}/cancel";
            case MUSIC_GENERATION_CREATE -> "/v1/music/generations";
            case MUSIC_GENERATION_GET -> "/v1/music/{taskId}";
            case MUSIC_GENERATION_CANCEL -> "/v1/music/{taskId}/cancel";
            case TASK_GET -> "/v1/tasks/{taskId}";
            case TASK_CANCEL -> "/v1/tasks/{taskId}/cancel";
            case WEB_SEARCH_CREATE -> "/v1/web_search";
            case UNKNOWN -> resourceType == null ? null : "/" + resourceType.wireName();
        };
    }
}
