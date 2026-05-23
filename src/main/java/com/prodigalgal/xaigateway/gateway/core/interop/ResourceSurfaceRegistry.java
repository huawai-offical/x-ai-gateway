package com.prodigalgal.xaigateway.gateway.core.interop;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ResourceSurfaceRegistry {

    private static final List<ResourceSurfaceDefinition> DEFINITIONS = List.of(
            definition("chat_completion", "POST", "/v1/chat/completions", "chat.completions", "openai",
                    TranslationResourceType.CHAT, TranslationOperation.CHAT_COMPLETION,
                    List.of(InteropFeature.CHAT_TEXT), RouteSelectionMode.CATALOG_SELECTION, null, true),
            definition("response_create", "POST", "/v1/responses", "responses", "responses",
                    TranslationResourceType.RESPONSE, TranslationOperation.RESPONSE_CREATE,
                    List.of(InteropFeature.RESPONSE_OBJECT), RouteSelectionMode.CATALOG_SELECTION, null, true),
            definition("embedding_create", "POST", "/v1/embeddings", "embeddings", "openai",
                    TranslationResourceType.EMBEDDING, TranslationOperation.EMBEDDING_CREATE,
                    List.of(InteropFeature.EMBEDDINGS), RouteSelectionMode.CATALOG_SELECTION, null, true),
            definition("google_embedding_create", "POST", "/v1beta/models/{model}:embedContent", "embeddings", "google_native",
                    TranslationResourceType.EMBEDDING, TranslationOperation.EMBEDDING_CREATE,
                    List.of(InteropFeature.EMBEDDINGS), RouteSelectionMode.CATALOG_SELECTION, null, false),
            definition("google_batch_embedding_create", "POST", "/v1beta/models/{model}:batchEmbedContents", "embeddings", "google_native",
                    TranslationResourceType.EMBEDDING, TranslationOperation.EMBEDDING_CREATE,
                    List.of(InteropFeature.EMBEDDINGS), RouteSelectionMode.CATALOG_SELECTION, null, false),
            definition("audio_transcription", "POST", "/v1/audio/transcriptions", "audio", "openai",
                    TranslationResourceType.AUDIO, TranslationOperation.AUDIO_TRANSCRIPTION,
                    List.of(InteropFeature.AUDIO_TRANSCRIPTION), RouteSelectionMode.CATALOG_SELECTION, "gpt-4o-mini-transcribe", true),
            definition("audio_translation", "POST", "/v1/audio/translations", "audio", "openai",
                    TranslationResourceType.AUDIO, TranslationOperation.AUDIO_TRANSLATION,
                    List.of(InteropFeature.AUDIO_TRANSLATION), RouteSelectionMode.CATALOG_SELECTION, "whisper-1", true),
            definition("audio_speech", "POST", "/v1/audio/speech", "audio", "openai",
                    TranslationResourceType.AUDIO, TranslationOperation.AUDIO_SPEECH,
                    List.of(InteropFeature.AUDIO_SPEECH), RouteSelectionMode.CATALOG_SELECTION, "gpt-4o-mini-tts", false),
            definition("image_generation", "POST", "/v1/images/generations", "images", "openai",
                    TranslationResourceType.IMAGE, TranslationOperation.IMAGE_GENERATION,
                    List.of(InteropFeature.IMAGE_GENERATION), RouteSelectionMode.CATALOG_SELECTION, "gpt-image-1", true),
            definition("image_edit", "POST", "/v1/images/edits", "images", "openai",
                    TranslationResourceType.IMAGE, TranslationOperation.IMAGE_EDIT,
                    List.of(InteropFeature.IMAGE_EDIT), RouteSelectionMode.CATALOG_SELECTION, "gpt-image-1", true),
            definition("image_variation", "POST", "/v1/images/variations", "images", "openai",
                    TranslationResourceType.IMAGE, TranslationOperation.IMAGE_VARIATION,
                    List.of(InteropFeature.IMAGE_VARIATION), RouteSelectionMode.CATALOG_SELECTION, "dall-e-2", true),
            definition("moderation_create", "POST", "/v1/moderations", "moderations", "openai",
                    TranslationResourceType.MODERATION, TranslationOperation.MODERATION_CREATE,
                    List.of(InteropFeature.MODERATION), RouteSelectionMode.CATALOG_SELECTION, "omni-moderation-latest", true),
            definition("file_create", "POST", "/v1/files", "files", "openai",
                    TranslationResourceType.FILE, TranslationOperation.FILE_CREATE,
                    List.of(InteropFeature.FILE_OBJECT), RouteSelectionMode.CATALOG_SELECTION, "resource-orchestration", true),
            definition("file_list", "GET", "/v1/files", "files", "openai",
                    TranslationResourceType.FILE, TranslationOperation.FILE_LIST,
                    List.of(InteropFeature.FILE_OBJECT), RouteSelectionMode.LOCAL_CATALOG, "resource-orchestration", true),
            definition("file_get", "GET", "/v1/files/{fileId}", "files", "openai",
                    TranslationResourceType.FILE, TranslationOperation.FILE_GET,
                    List.of(InteropFeature.FILE_OBJECT), RouteSelectionMode.STORED_LINEAGE, "resource-orchestration", true),
            definition("file_content_get", "GET", "/v1/files/{fileId}/content", "files", "openai",
                    TranslationResourceType.FILE, TranslationOperation.FILE_CONTENT_GET,
                    List.of(InteropFeature.FILE_OBJECT), RouteSelectionMode.STORED_LINEAGE, "resource-orchestration", true),
            definition("file_delete", "DELETE", "/v1/files/{fileId}", "files", "openai",
                    TranslationResourceType.FILE, TranslationOperation.FILE_DELETE,
                    List.of(InteropFeature.FILE_OBJECT), RouteSelectionMode.STORED_LINEAGE, "resource-orchestration", true),
            definition("google_file_create", "POST", "/upload/v1beta/files", "files", "google_native",
                    TranslationResourceType.FILE, TranslationOperation.FILE_CREATE,
                    List.of(InteropFeature.FILE_OBJECT), RouteSelectionMode.CATALOG_SELECTION, "resource-orchestration", false),
            definition("google_file_list", "GET", "/v1beta/files", "files", "google_native",
                    TranslationResourceType.FILE, TranslationOperation.FILE_LIST,
                    List.of(InteropFeature.FILE_OBJECT), RouteSelectionMode.LOCAL_CATALOG, "resource-orchestration", false),
            definition("google_file_get", "GET", "/v1beta/files/{fileName}", "files", "google_native",
                    TranslationResourceType.FILE, TranslationOperation.FILE_GET,
                    List.of(InteropFeature.FILE_OBJECT), RouteSelectionMode.STORED_LINEAGE, "resource-orchestration", false),
            definition("google_file_delete", "DELETE", "/v1beta/files/{fileName}", "files", "google_native",
                    TranslationResourceType.FILE, TranslationOperation.FILE_DELETE,
                    List.of(InteropFeature.FILE_OBJECT), RouteSelectionMode.STORED_LINEAGE, "resource-orchestration", false),
            definition("upload_create", "POST", "/v1/uploads", "uploads", "openai",
                    TranslationResourceType.UPLOAD, TranslationOperation.UPLOAD_CREATE,
                    List.of(InteropFeature.UPLOAD_CREATE, InteropFeature.FILE_OBJECT), RouteSelectionMode.CATALOG_SELECTION, "resource-orchestration", true),
            definition("upload_get", "GET", "/v1/uploads/{uploadId}", "uploads", "openai",
                    TranslationResourceType.UPLOAD, TranslationOperation.UPLOAD_GET,
                    List.of(InteropFeature.UPLOAD_CREATE), RouteSelectionMode.STORED_LINEAGE, "resource-orchestration", true),
            definition("upload_part_add", "POST", "/v1/uploads/{uploadId}/parts", "uploads", "openai",
                    TranslationResourceType.UPLOAD, TranslationOperation.UPLOAD_PART_ADD,
                    List.of(InteropFeature.UPLOAD_CREATE, InteropFeature.FILE_OBJECT), RouteSelectionMode.STORED_LINEAGE, "resource-orchestration", true),
            definition("upload_complete", "POST", "/v1/uploads/{uploadId}/complete", "uploads", "openai",
                    TranslationResourceType.UPLOAD, TranslationOperation.UPLOAD_COMPLETE,
                    List.of(InteropFeature.UPLOAD_CREATE), RouteSelectionMode.STORED_LINEAGE, "resource-orchestration", true),
            definition("upload_cancel", "POST", "/v1/uploads/{uploadId}/cancel", "uploads", "openai",
                    TranslationResourceType.UPLOAD, TranslationOperation.UPLOAD_CANCEL,
                    List.of(InteropFeature.UPLOAD_CREATE), RouteSelectionMode.STORED_LINEAGE, "resource-orchestration", true),
            definition("rerank_create", "POST", "/v1/rerank", "rerank", "openai",
                    TranslationResourceType.RERANK, TranslationOperation.RERANK_CREATE,
                    List.of(InteropFeature.RERANK), RouteSelectionMode.CATALOG_SELECTION, null, true),
            definition("video_generation_create", "POST", "/v1/videos/generations", "videos", "openai",
                    TranslationResourceType.VIDEO, TranslationOperation.VIDEO_GENERATION_CREATE,
                    List.of(InteropFeature.VIDEO_GENERATION, InteropFeature.ASYNC_TASK), RouteSelectionMode.CATALOG_SELECTION, null, true),
            definition("video_generation_get", "GET", "/v1/videos/{taskId}", "videos", "openai",
                    TranslationResourceType.VIDEO, TranslationOperation.VIDEO_GENERATION_GET,
                    List.of(InteropFeature.ASYNC_TASK), RouteSelectionMode.STORED_LINEAGE, null, false),
            definition("video_generation_cancel", "POST", "/v1/videos/{taskId}/cancel", "videos", "openai",
                    TranslationResourceType.VIDEO, TranslationOperation.VIDEO_GENERATION_CANCEL,
                    List.of(InteropFeature.ASYNC_TASK), RouteSelectionMode.STORED_LINEAGE, null, false),
            definition("music_generation_create", "POST", "/v1/music/generations", "music", "openai",
                    TranslationResourceType.MUSIC, TranslationOperation.MUSIC_GENERATION_CREATE,
                    List.of(InteropFeature.MUSIC_GENERATION, InteropFeature.ASYNC_TASK), RouteSelectionMode.CATALOG_SELECTION, null, true),
            definition("music_generation_get", "GET", "/v1/music/{taskId}", "music", "openai",
                    TranslationResourceType.MUSIC, TranslationOperation.MUSIC_GENERATION_GET,
                    List.of(InteropFeature.ASYNC_TASK), RouteSelectionMode.STORED_LINEAGE, null, false),
            definition("music_generation_cancel", "POST", "/v1/music/{taskId}/cancel", "music", "openai",
                    TranslationResourceType.MUSIC, TranslationOperation.MUSIC_GENERATION_CANCEL,
                    List.of(InteropFeature.ASYNC_TASK), RouteSelectionMode.STORED_LINEAGE, null, false),
            definition("task_get", "GET", "/v1/tasks/{taskId}", "tasks", "openai",
                    TranslationResourceType.TASK, TranslationOperation.TASK_GET,
                    List.of(InteropFeature.ASYNC_TASK), RouteSelectionMode.STORED_LINEAGE, null, false),
            definition("task_cancel", "POST", "/v1/tasks/{taskId}/cancel", "tasks", "openai",
                    TranslationResourceType.TASK, TranslationOperation.TASK_CANCEL,
                    List.of(InteropFeature.ASYNC_TASK), RouteSelectionMode.STORED_LINEAGE, null, false),
            definition("web_search_create", "POST", "/v1/web_search", "web_search", "openai",
                    TranslationResourceType.WEB_SEARCH, TranslationOperation.WEB_SEARCH_CREATE,
                    List.of(InteropFeature.WEB_SEARCH), RouteSelectionMode.CATALOG_SELECTION, null, true)
    );

    private static final Map<String, ResourceSurfaceDefinition> BY_METHOD_AND_PATH = buildMethodPathIndex();
    private static final Map<TranslationOperation, ResourceSurfaceDefinition> DEFAULT_BY_OPERATION = buildOperationIndex();
    private static final List<ResourceSurfaceDefinition> PROVIDER_SURFACES = DEFINITIONS.stream()
            .filter(ResourceSurfaceDefinition::providerSurface)
            .toList();
    private static final List<InteropFeature> CAPABILITY_OVERVIEW_FEATURES = List.of(
            InteropFeature.CHAT_TEXT,
            InteropFeature.TOOLS,
            InteropFeature.IMAGE_INPUT,
            InteropFeature.FILE_INPUT,
            InteropFeature.RESPONSE_OBJECT,
            InteropFeature.EMBEDDINGS,
            InteropFeature.REASONING,
            InteropFeature.AUDIO_TRANSCRIPTION,
            InteropFeature.AUDIO_TRANSLATION,
            InteropFeature.AUDIO_SPEECH,
            InteropFeature.IMAGE_GENERATION,
            InteropFeature.IMAGE_EDIT,
            InteropFeature.IMAGE_VARIATION,
            InteropFeature.MODERATION,
            InteropFeature.FILE_OBJECT,
            InteropFeature.UPLOAD_CREATE,
            InteropFeature.RERANK,
            InteropFeature.VIDEO_GENERATION,
            InteropFeature.MUSIC_GENERATION,
            InteropFeature.WEB_SEARCH
    );

    private ResourceSurfaceRegistry() {
    }

    public static List<ResourceSurfaceDefinition> definitions() {
        return DEFINITIONS;
    }

    public static List<ResourceSurfaceDefinition> providerSurfaces() {
        return PROVIDER_SURFACES;
    }

    public static List<InteropFeature> capabilityOverviewFeatures() {
        return CAPABILITY_OVERVIEW_FEATURES;
    }

    public static Optional<ResourceSurfaceDefinition> find(String httpMethod, String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_METHOD_AND_PATH.get(key(httpMethod, normalizedPath)));
    }

    public static Optional<ResourceSurfaceDefinition> findByOperation(TranslationOperation operation) {
        return Optional.ofNullable(DEFAULT_BY_OPERATION.get(operation == null ? TranslationOperation.UNKNOWN : operation));
    }

    public static String defaultSurface(TranslationResourceType resourceType, TranslationOperation operation) {
        return findByOperation(operation)
                .map(ResourceSurfaceDefinition::surface)
                .orElseGet(() -> resourceType == null ? "unknown" : resourceType.wireName());
    }

    public static String defaultNormalizedPath(TranslationResourceType resourceType, TranslationOperation operation) {
        return findByOperation(operation)
                .map(ResourceSurfaceDefinition::normalizedPath)
                .orElseGet(() -> resourceType == null || resourceType == TranslationResourceType.UNKNOWN
                        ? null
                        : "/" + resourceType.wireName());
    }

    public static RouteSelectionMode defaultRouteSelectionMode(
            TranslationResourceType resourceType,
            TranslationOperation operation) {
        return findByOperation(operation)
                .map(ResourceSurfaceDefinition::routeSelectionMode)
                .orElseGet(() -> resourceType == TranslationResourceType.FILE
                        ? RouteSelectionMode.LOCAL_CATALOG
                        : RouteSelectionMode.CATALOG_SELECTION);
    }

    public static Optional<String> defaultModel(TranslationOperation operation) {
        return findByOperation(operation).map(ResourceSurfaceDefinition::defaultModel);
    }

    private static ResourceSurfaceDefinition definition(
            String key,
            String httpMethod,
            String normalizedPath,
            String surface,
            String protocol,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            List<InteropFeature> requiredFeatures,
            RouteSelectionMode routeSelectionMode,
            String defaultModel,
            boolean providerSurface) {
        return new ResourceSurfaceDefinition(
                key,
                httpMethod,
                normalizedPath,
                surface,
                protocol,
                resourceType,
                operation,
                requiredFeatures,
                routeSelectionMode,
                defaultModel,
                providerSurface
        );
    }

    private static Map<String, ResourceSurfaceDefinition> buildMethodPathIndex() {
        LinkedHashMap<String, ResourceSurfaceDefinition> index = new LinkedHashMap<>();
        for (ResourceSurfaceDefinition definition : DEFINITIONS) {
            index.put(key(definition.httpMethod(), definition.normalizedPath()), definition);
        }
        return Collections.unmodifiableMap(index);
    }

    private static Map<TranslationOperation, ResourceSurfaceDefinition> buildOperationIndex() {
        EnumMap<TranslationOperation, ResourceSurfaceDefinition> index = new EnumMap<>(TranslationOperation.class);
        for (ResourceSurfaceDefinition definition : DEFINITIONS) {
            index.putIfAbsent(definition.operation(), definition);
        }
        return Collections.unmodifiableMap(index);
    }

    private static String key(String httpMethod, String normalizedPath) {
        String method = httpMethod == null || httpMethod.isBlank() ? "POST" : httpMethod.trim().toUpperCase(Locale.ROOT);
        return method + " " + normalizedPath;
    }
}
