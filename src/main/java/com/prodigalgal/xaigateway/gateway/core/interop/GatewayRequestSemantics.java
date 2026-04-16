package com.prodigalgal.xaigateway.gateway.core.interop;

import java.util.List;

public record GatewayRequestSemantics(
        TranslationResourceType resourceType,
        TranslationOperation operation,
        String surface,
        String normalizedPath,
        List<InteropFeature> requiredFeatures,
        boolean requiresRouteSelection
) {
    public GatewayRequestSemantics(
            TranslationResourceType resourceType,
            TranslationOperation operation,
            List<InteropFeature> requiredFeatures,
            boolean requiresRouteSelection
    ) {
        this(
                resourceType,
                operation,
                defaultSurface(resourceType, operation),
                defaultNormalizedPath(operation),
                requiredFeatures,
                requiresRouteSelection
        );
    }

    public GatewayRequestSemantics {
        resourceType = resourceType == null ? TranslationResourceType.UNKNOWN : resourceType;
        operation = operation == null ? TranslationOperation.UNKNOWN : operation;
        surface = surface == null || surface.isBlank() ? defaultSurface(resourceType, operation) : surface;
        normalizedPath = normalizedPath == null || normalizedPath.isBlank()
                ? defaultNormalizedPath(operation)
                : normalizedPath;
        requiredFeatures = requiredFeatures == null ? List.of() : List.copyOf(requiredFeatures);
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
            case BATCH_CREATE, BATCH_GET, BATCH_CANCEL -> "batches";
            case ANTHROPIC_MESSAGE_BATCH_CREATE, ANTHROPIC_MESSAGE_BATCH_GET, ANTHROPIC_MESSAGE_BATCH_CANCEL -> "messages.batches";
            case TUNING_CREATE, TUNING_GET, TUNING_CANCEL -> "fine_tuning";
            case REALTIME_CLIENT_SECRET_CREATE -> "realtime";
            case UNKNOWN -> resourceType == null ? "unknown" : resourceType.wireName();
        };
    }

    private static String defaultNormalizedPath(TranslationOperation operation) {
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
            case BATCH_CREATE -> "/v1/batches";
            case BATCH_GET -> "/v1/batches/{batchId}";
            case BATCH_CANCEL -> "/v1/batches/{batchId}/cancel";
            case ANTHROPIC_MESSAGE_BATCH_CREATE -> "/v1/messages/batches";
            case ANTHROPIC_MESSAGE_BATCH_GET -> "/v1/messages/batches/{messageBatchId}";
            case ANTHROPIC_MESSAGE_BATCH_CANCEL -> "/v1/messages/batches/{messageBatchId}/cancel";
            case TUNING_CREATE -> "/v1/fine_tuning/jobs";
            case TUNING_GET -> "/v1/fine_tuning/jobs/{jobId}";
            case TUNING_CANCEL -> "/v1/fine_tuning/jobs/{jobId}/cancel";
            case REALTIME_CLIENT_SECRET_CREATE -> "/v1/realtime/client_secrets";
            case UNKNOWN -> null;
        };
    }
}
