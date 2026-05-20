package com.prodigalgal.xaigateway.gateway.core.interop;

import tools.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.prodigalgal.xaigateway.protocol.ingress.google.GeminiGenerateContentModeResolver;
import com.prodigalgal.xaigateway.protocol.ingress.google.GeminiGenerateContentRequest;
import org.springframework.stereotype.Service;

@Service
public class GatewayRequestFeatureService {

    private final GeminiGenerateContentModeResolver geminiGenerateContentModeResolver;

    public GatewayRequestFeatureService() {
        this(new GeminiGenerateContentModeResolver());
    }

    public GatewayRequestFeatureService(GeminiGenerateContentModeResolver geminiGenerateContentModeResolver) {
        this.geminiGenerateContentModeResolver = geminiGenerateContentModeResolver;
    }

    public GatewayRequestSemantics describe(String requestPath, JsonNode body) {
        return describe("POST", requestPath, body);
    }

    public GatewayRequestSemantics describe(String httpMethod, String requestPath, JsonNode body) {
        String method = normalizeMethod(httpMethod);
        String normalizedPath = normalizePath(requestPath);
        Set<InteropFeature> features = new LinkedHashSet<>();
        if ("/v1/chat/completions".equals(normalizedPath)) {
            features.add(InteropFeature.CHAT_TEXT);
            collectChatFeatures(features, body);
            return semantics("chat.completions", normalizedPath, TranslationResourceType.CHAT, TranslationOperation.CHAT_COMPLETION, features, RouteSelectionMode.CATALOG_SELECTION);
        }
        if ("/v1/responses".equals(normalizedPath)) {
            features.add(InteropFeature.RESPONSE_OBJECT);
            collectResponsesFeatures(features, body);
            return semantics("responses", normalizedPath, TranslationResourceType.RESPONSE, TranslationOperation.RESPONSE_CREATE, features, RouteSelectionMode.CATALOG_SELECTION);
        }
        if ("/v1/messages".equals(normalizedPath)) {
            features.add(InteropFeature.CHAT_TEXT);
            collectAnthropicFeatures(features, body);
            return semantics("messages", normalizedPath, TranslationResourceType.CHAT, TranslationOperation.CHAT_COMPLETION, features, RouteSelectionMode.CATALOG_SELECTION);
        }
        if (normalizedPath != null
                && normalizedPath.startsWith("/v1beta/models/")
                && normalizedPath.contains(":generateContent")) {
            GeminiGenerateContentModeResolver.GeminiGenerateContentMode mode = resolveGeminiGenerateContentMode(body);
            return switch (mode) {
                case IMAGE_GENERATION -> semantics(
                        "generateContent",
                        normalizedPath,
                        TranslationResourceType.IMAGE,
                        TranslationOperation.IMAGE_GENERATION,
                        List.of(InteropFeature.IMAGE_GENERATION),
                        RouteSelectionMode.CATALOG_SELECTION
                );
                case AUDIO_SPEECH -> semantics(
                        "generateContent",
                        normalizedPath,
                        TranslationResourceType.AUDIO,
                        TranslationOperation.AUDIO_SPEECH,
                        List.of(InteropFeature.AUDIO_SPEECH),
                        RouteSelectionMode.CATALOG_SELECTION
                );
                case CHAT -> {
                    features.add(InteropFeature.CHAT_TEXT);
                    collectGeminiFeatures(features, body);
                    yield semantics(
                            "generateContent",
                            normalizedPath,
                            TranslationResourceType.CHAT,
                            TranslationOperation.CHAT_COMPLETION,
                            features,
                            RouteSelectionMode.CATALOG_SELECTION
                    );
                }
            };
        }
        if ("POST".equals(method) && "/v1beta/models/{model}:embedContent".equals(normalizedPath)) {
            return semantics(
                    "embeddings",
                    normalizedPath,
                    TranslationResourceType.EMBEDDING,
                    TranslationOperation.EMBEDDING_CREATE,
                    List.of(InteropFeature.EMBEDDINGS),
                    RouteSelectionMode.CATALOG_SELECTION
            );
        }
        if ("POST".equals(method) && "/v1beta/models/{model}:batchEmbedContents".equals(normalizedPath)) {
            return semantics(
                    "embeddings",
                    normalizedPath,
                    TranslationResourceType.EMBEDDING,
                    TranslationOperation.EMBEDDING_CREATE,
                    List.of(InteropFeature.EMBEDDINGS),
                    RouteSelectionMode.CATALOG_SELECTION
            );
        }
        if ("POST".equals(method) && "/upload/v1beta/files".equals(normalizedPath)) {
            return semantics(
                    "files",
                    normalizedPath,
                    TranslationResourceType.FILE,
                    TranslationOperation.FILE_CREATE,
                    List.of(InteropFeature.FILE_OBJECT),
                    RouteSelectionMode.CATALOG_SELECTION
            );
        }
        if ("GET".equals(method) && "/v1beta/files".equals(normalizedPath)) {
            return semantics(
                    "files",
                    normalizedPath,
                    TranslationResourceType.FILE,
                    TranslationOperation.FILE_LIST,
                    List.of(InteropFeature.FILE_OBJECT),
                    RouteSelectionMode.LOCAL_CATALOG
            );
        }
        if ("GET".equals(method) && "/v1beta/files/{fileName}".equals(normalizedPath)) {
            return semantics(
                    "files",
                    normalizedPath,
                    TranslationResourceType.FILE,
                    TranslationOperation.FILE_GET,
                    List.of(InteropFeature.FILE_OBJECT),
                    RouteSelectionMode.STORED_LINEAGE
            );
        }
        if ("DELETE".equals(method) && "/v1beta/files/{fileName}".equals(normalizedPath)) {
            return semantics(
                    "files",
                    normalizedPath,
                    TranslationResourceType.FILE,
                    TranslationOperation.FILE_DELETE,
                    List.of(InteropFeature.FILE_OBJECT),
                    RouteSelectionMode.STORED_LINEAGE
            );
        }
        if ("POST".equals(method) && "/v1/embeddings".equals(normalizedPath)) {
            return semantics("embeddings", normalizedPath, TranslationResourceType.EMBEDDING, TranslationOperation.EMBEDDING_CREATE, List.of(InteropFeature.EMBEDDINGS), RouteSelectionMode.CATALOG_SELECTION);
        }
        if ("POST".equals(method) && "/v1/files".equals(normalizedPath)) {
            return semantics("files", normalizedPath, TranslationResourceType.FILE, TranslationOperation.FILE_CREATE, List.of(InteropFeature.FILE_OBJECT), RouteSelectionMode.CATALOG_SELECTION);
        }
        if ("GET".equals(method) && "/v1/files".equals(normalizedPath)) {
            return semantics("files", normalizedPath, TranslationResourceType.FILE, TranslationOperation.FILE_LIST, List.of(InteropFeature.FILE_OBJECT), RouteSelectionMode.LOCAL_CATALOG);
        }
        if ("GET".equals(method) && "/v1/files/{fileId}".equals(normalizedPath)) {
            return semantics("files", normalizedPath, TranslationResourceType.FILE, TranslationOperation.FILE_GET, List.of(InteropFeature.FILE_OBJECT), RouteSelectionMode.STORED_LINEAGE);
        }
        if ("GET".equals(method) && "/v1/files/{fileId}/content".equals(normalizedPath)) {
            return semantics("files", normalizedPath, TranslationResourceType.FILE, TranslationOperation.FILE_CONTENT_GET, List.of(InteropFeature.FILE_OBJECT), RouteSelectionMode.STORED_LINEAGE);
        }
        if ("DELETE".equals(method) && "/v1/files/{fileId}".equals(normalizedPath)) {
            return semantics("files", normalizedPath, TranslationResourceType.FILE, TranslationOperation.FILE_DELETE, List.of(InteropFeature.FILE_OBJECT), RouteSelectionMode.STORED_LINEAGE);
        }
        if ("POST".equals(method) && "/v1/audio/transcriptions".equals(normalizedPath)) {
            return semantics("audio", normalizedPath, TranslationResourceType.AUDIO, TranslationOperation.AUDIO_TRANSCRIPTION, List.of(InteropFeature.AUDIO_TRANSCRIPTION), RouteSelectionMode.CATALOG_SELECTION);
        }
        if ("POST".equals(method) && "/v1/audio/translations".equals(normalizedPath)) {
            return semantics("audio", normalizedPath, TranslationResourceType.AUDIO, TranslationOperation.AUDIO_TRANSLATION, List.of(InteropFeature.AUDIO_TRANSLATION), RouteSelectionMode.CATALOG_SELECTION);
        }
        if ("POST".equals(method) && "/v1/audio/speech".equals(normalizedPath)) {
            return semantics("audio", normalizedPath, TranslationResourceType.AUDIO, TranslationOperation.AUDIO_SPEECH, List.of(InteropFeature.AUDIO_SPEECH), RouteSelectionMode.CATALOG_SELECTION);
        }
        if ("POST".equals(method) && "/v1/images/generations".equals(normalizedPath)) {
            return semantics("images", normalizedPath, TranslationResourceType.IMAGE, TranslationOperation.IMAGE_GENERATION, List.of(InteropFeature.IMAGE_GENERATION), RouteSelectionMode.CATALOG_SELECTION);
        }
        if ("POST".equals(method) && "/v1/images/edits".equals(normalizedPath)) {
            return semantics("images", normalizedPath, TranslationResourceType.IMAGE, TranslationOperation.IMAGE_EDIT, List.of(InteropFeature.IMAGE_EDIT), RouteSelectionMode.CATALOG_SELECTION);
        }
        if ("POST".equals(method) && "/v1/images/variations".equals(normalizedPath)) {
            return semantics("images", normalizedPath, TranslationResourceType.IMAGE, TranslationOperation.IMAGE_VARIATION, List.of(InteropFeature.IMAGE_VARIATION), RouteSelectionMode.CATALOG_SELECTION);
        }
        if ("POST".equals(method) && "/v1/moderations".equals(normalizedPath)) {
            return semantics("moderations", normalizedPath, TranslationResourceType.MODERATION, TranslationOperation.MODERATION_CREATE, List.of(InteropFeature.MODERATION), RouteSelectionMode.CATALOG_SELECTION);
        }
        if ("POST".equals(method) && "/v1/rerank".equals(normalizedPath)) {
            return semantics("rerank", normalizedPath, TranslationResourceType.RERANK, TranslationOperation.RERANK_CREATE, List.of(InteropFeature.RERANK), RouteSelectionMode.CATALOG_SELECTION);
        }
        if ("POST".equals(method) && "/v1/videos/generations".equals(normalizedPath)) {
            return semantics("videos", normalizedPath, TranslationResourceType.VIDEO, TranslationOperation.VIDEO_GENERATION_CREATE, List.of(InteropFeature.VIDEO_GENERATION, InteropFeature.ASYNC_TASK), RouteSelectionMode.CATALOG_SELECTION);
        }
        if ("GET".equals(method) && "/v1/videos/{taskId}".equals(normalizedPath)) {
            return semantics("videos", normalizedPath, TranslationResourceType.VIDEO, TranslationOperation.VIDEO_GENERATION_GET, List.of(InteropFeature.ASYNC_TASK), RouteSelectionMode.STORED_LINEAGE);
        }
        if ("POST".equals(method) && "/v1/videos/{taskId}/cancel".equals(normalizedPath)) {
            return semantics("videos", normalizedPath, TranslationResourceType.VIDEO, TranslationOperation.VIDEO_GENERATION_CANCEL, List.of(InteropFeature.ASYNC_TASK), RouteSelectionMode.STORED_LINEAGE);
        }
        if ("POST".equals(method) && "/v1/music/generations".equals(normalizedPath)) {
            return semantics("music", normalizedPath, TranslationResourceType.MUSIC, TranslationOperation.MUSIC_GENERATION_CREATE, List.of(InteropFeature.MUSIC_GENERATION, InteropFeature.ASYNC_TASK), RouteSelectionMode.CATALOG_SELECTION);
        }
        if ("GET".equals(method) && "/v1/music/{taskId}".equals(normalizedPath)) {
            return semantics("music", normalizedPath, TranslationResourceType.MUSIC, TranslationOperation.MUSIC_GENERATION_GET, List.of(InteropFeature.ASYNC_TASK), RouteSelectionMode.STORED_LINEAGE);
        }
        if ("POST".equals(method) && "/v1/music/{taskId}/cancel".equals(normalizedPath)) {
            return semantics("music", normalizedPath, TranslationResourceType.MUSIC, TranslationOperation.MUSIC_GENERATION_CANCEL, List.of(InteropFeature.ASYNC_TASK), RouteSelectionMode.STORED_LINEAGE);
        }
        if ("GET".equals(method) && "/v1/tasks/{taskId}".equals(normalizedPath)) {
            return semantics("tasks", normalizedPath, TranslationResourceType.TASK, TranslationOperation.TASK_GET, List.of(InteropFeature.ASYNC_TASK), RouteSelectionMode.STORED_LINEAGE);
        }
        if ("POST".equals(method) && "/v1/tasks/{taskId}/cancel".equals(normalizedPath)) {
            return semantics("tasks", normalizedPath, TranslationResourceType.TASK, TranslationOperation.TASK_CANCEL, List.of(InteropFeature.ASYNC_TASK), RouteSelectionMode.STORED_LINEAGE);
        }
        if ("POST".equals(method) && "/v1/web_search".equals(normalizedPath)) {
            return semantics("web_search", normalizedPath, TranslationResourceType.WEB_SEARCH, TranslationOperation.WEB_SEARCH_CREATE, List.of(InteropFeature.WEB_SEARCH), RouteSelectionMode.CATALOG_SELECTION);
        }
        if ("POST".equals(method) && "/v1/uploads".equals(normalizedPath)) {
            return semantics("uploads", normalizedPath, TranslationResourceType.UPLOAD, TranslationOperation.UPLOAD_CREATE, List.of(InteropFeature.UPLOAD_CREATE, InteropFeature.FILE_OBJECT), RouteSelectionMode.CATALOG_SELECTION);
        }
        if ("GET".equals(method) && "/v1/uploads/{uploadId}".equals(normalizedPath)) {
            return semantics("uploads", normalizedPath, TranslationResourceType.UPLOAD, TranslationOperation.UPLOAD_GET, List.of(InteropFeature.UPLOAD_CREATE), RouteSelectionMode.STORED_LINEAGE);
        }
        if ("POST".equals(method) && "/v1/uploads/{uploadId}/parts".equals(normalizedPath)) {
            return semantics("uploads", normalizedPath, TranslationResourceType.UPLOAD, TranslationOperation.UPLOAD_PART_ADD, List.of(InteropFeature.UPLOAD_CREATE, InteropFeature.FILE_OBJECT), RouteSelectionMode.STORED_LINEAGE);
        }
        if ("POST".equals(method) && "/v1/uploads/{uploadId}/complete".equals(normalizedPath)) {
            return semantics("uploads", normalizedPath, TranslationResourceType.UPLOAD, TranslationOperation.UPLOAD_COMPLETE, List.of(InteropFeature.UPLOAD_CREATE), RouteSelectionMode.STORED_LINEAGE);
        }
        if ("POST".equals(method) && "/v1/uploads/{uploadId}/cancel".equals(normalizedPath)) {
            return semantics("uploads", normalizedPath, TranslationResourceType.UPLOAD, TranslationOperation.UPLOAD_CANCEL, List.of(InteropFeature.UPLOAD_CREATE), RouteSelectionMode.STORED_LINEAGE);
        }
        if ("POST".equals(method) && "/v1/realtime/client_secrets".equals(normalizedPath)) {
            return semantics("realtime", normalizedPath, TranslationResourceType.REALTIME, TranslationOperation.REALTIME_CLIENT_SECRET_CREATE, List.of(InteropFeature.REALTIME_CLIENT_SECRET), RouteSelectionMode.DISTRIBUTED_TARGET);
        }
        return semantics("unknown", normalizedPath, TranslationResourceType.UNKNOWN, TranslationOperation.UNKNOWN, List.of(InteropFeature.CHAT_TEXT), RouteSelectionMode.CATALOG_SELECTION);
    }

    public String normalizePath(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return requestPath;
        }
        requestPath = normalizeGoogleNativeNamespace(requestPath);
        if (requestPath.matches("^/v1beta/models/[^/:]+:(generateContent|streamGenerateContent)$")) {
            return "/v1beta/models/{model}:generateContent";
        }
        if (requestPath.matches("^/v1beta/models/[^/:]+:(embedContent|batchEmbedContents)$")) {
            return requestPath.contains(":batchEmbedContents")
                    ? "/v1beta/models/{model}:batchEmbedContents"
                    : "/v1beta/models/{model}:embedContent";
        }
        if (requestPath.matches("^/upload/v1beta/files$")) {
            return "/upload/v1beta/files";
        }
        if (requestPath.matches("^/v1beta/files/[^/]+$")) {
            return "/v1beta/files/{fileName}";
        }
        if (requestPath.matches("^/v1/files/[^/]+/content$")) {
            return "/v1/files/{fileId}/content";
        }
        if (requestPath.matches("^/v1/files/[^/]+$")) {
            return "/v1/files/{fileId}";
        }
        if (requestPath.matches("^/v1/uploads/[^/]+/parts$")) {
            return "/v1/uploads/{uploadId}/parts";
        }
        if (requestPath.matches("^/v1/uploads/[^/]+/complete$")) {
            return "/v1/uploads/{uploadId}/complete";
        }
        if (requestPath.matches("^/v1/uploads/[^/]+/cancel$")) {
            return "/v1/uploads/{uploadId}/cancel";
        }
        if (requestPath.matches("^/v1/uploads/[^/]+$")) {
            return "/v1/uploads/{uploadId}";
        }
        if ("/v1/videos/generations".equals(requestPath)) {
            return "/v1/videos/generations";
        }
        if (requestPath.matches("^/v1/videos/[^/]+/cancel$")) {
            return "/v1/videos/{taskId}/cancel";
        }
        if (requestPath.matches("^/v1/videos/[^/]+$")) {
            return "/v1/videos/{taskId}";
        }
        if ("/v1/music/generations".equals(requestPath)) {
            return "/v1/music/generations";
        }
        if (requestPath.matches("^/v1/music/[^/]+/cancel$")) {
            return "/v1/music/{taskId}/cancel";
        }
        if (requestPath.matches("^/v1/music/[^/]+$")) {
            return "/v1/music/{taskId}";
        }
        if (requestPath.matches("^/v1/tasks/[^/]+/cancel$")) {
            return "/v1/tasks/{taskId}/cancel";
        }
        if (requestPath.matches("^/v1/tasks/[^/]+$")) {
            return "/v1/tasks/{taskId}";
        }
        return requestPath;
    }

    public java.util.Map<String, String> extractPathParams(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return java.util.Map.of();
        }
        requestPath = normalizeGoogleNativeNamespace(requestPath);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^/v1beta/models/([^/:]+):(generateContent|streamGenerateContent)$").matcher(requestPath);
        if (matcher.matches()) {
            return java.util.Map.of("model", matcher.group(1));
        }
        matcher = java.util.regex.Pattern.compile("^/v1beta/models/([^/:]+):(embedContent|batchEmbedContents)$").matcher(requestPath);
        if (matcher.matches()) {
            return java.util.Map.of("model", matcher.group(1));
        }
        matcher = java.util.regex.Pattern.compile("^/v1beta/files/([^/]+)$").matcher(requestPath);
        if (matcher.matches()) {
            return java.util.Map.of("fileName", matcher.group(1));
        }
        matcher = java.util.regex.Pattern.compile("^/v1/files/([^/]+)/content$").matcher(requestPath);
        if (matcher.matches()) {
            return java.util.Map.of("fileId", matcher.group(1));
        }
        matcher = java.util.regex.Pattern.compile("^/v1/files/([^/]+)$").matcher(requestPath);
        if (matcher.matches()) {
            return java.util.Map.of("fileId", matcher.group(1));
        }
        matcher = java.util.regex.Pattern.compile("^/v1/uploads/([^/]+)/parts$").matcher(requestPath);
        if (matcher.matches()) {
            return java.util.Map.of("uploadId", matcher.group(1));
        }
        matcher = java.util.regex.Pattern.compile("^/v1/uploads/([^/]+)/(complete|cancel)$").matcher(requestPath);
        if (matcher.matches()) {
            return java.util.Map.of("uploadId", matcher.group(1));
        }
        matcher = java.util.regex.Pattern.compile("^/v1/uploads/([^/]+)$").matcher(requestPath);
        if (matcher.matches()) {
            return java.util.Map.of("uploadId", matcher.group(1));
        }
        matcher = java.util.regex.Pattern.compile("^/v1/videos/([^/]+)(?:/cancel)?$").matcher(requestPath);
        if (matcher.matches()) {
            return java.util.Map.of("taskId", matcher.group(1));
        }
        matcher = java.util.regex.Pattern.compile("^/v1/music/([^/]+)(?:/cancel)?$").matcher(requestPath);
        if (matcher.matches()) {
            return java.util.Map.of("taskId", matcher.group(1));
        }
        matcher = java.util.regex.Pattern.compile("^/v1/tasks/([^/]+)(?:/cancel)?$").matcher(requestPath);
        if (matcher.matches()) {
            return java.util.Map.of("taskId", matcher.group(1));
        }
        return java.util.Map.of();
    }

    private String normalizeGoogleNativeNamespace(String requestPath) {
        if (requestPath.startsWith("/google/upload/v1beta")) {
            return requestPath.substring("/google".length());
        }
        if (requestPath.startsWith("/google/v1beta")) {
            return requestPath.substring("/google".length());
        }
        return requestPath;
    }

    private String normalizeMethod(String httpMethod) {
        if (httpMethod == null || httpMethod.isBlank()) {
            return "POST";
        }
        return httpMethod.trim().toUpperCase();
    }

    public List<InteropFeature> detectRequiredFeatures(String requestPath, JsonNode body) {
        return describe(requestPath, body).requiredFeatures();
    }

    private GatewayRequestSemantics semantics(
            String surface,
            String normalizedPath,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            java.util.Collection<InteropFeature> requiredFeatures,
            RouteSelectionMode routeSelectionMode) {
        return new GatewayRequestSemantics(
                resourceType,
                operation,
                surface,
                normalizedPath,
                requiredFeatures == null ? List.of() : List.copyOf(requiredFeatures),
                routeSelectionMode
        );
    }

    private GeminiGenerateContentModeResolver.GeminiGenerateContentMode resolveGeminiGenerateContentMode(JsonNode body) {
        return geminiGenerateContentModeResolver.resolve(new GeminiGenerateContentRequest(
                body == null ? null : body.path("contents"),
                body == null ? null : body.path("systemInstruction"),
                body == null ? null : body.path("generationConfig"),
                body == null ? null : body.path("tools"),
                body == null ? null : body.path("toolConfig"),
                null
        ));
    }

    private void collectChatFeatures(Set<InteropFeature> features, JsonNode body) {
        if (body == null || !body.isObject()) {
            return;
        }
        if (body.has("tools") && body.get("tools").isArray() && !body.get("tools").isEmpty()) {
            features.add(InteropFeature.TOOLS);
        }
        if (body.has("reasoning") || body.has("reasoning_effort")) {
            features.add(InteropFeature.REASONING);
        }
        JsonNode messages = body.path("messages");
        if (!messages.isArray()) {
            return;
        }
        for (JsonNode message : messages) {
            JsonNode content = message.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode item : content) {
                String type = item.path("type").asText("");
                if ("image_url".equalsIgnoreCase(type) || "input_image".equalsIgnoreCase(type)) {
                    features.add(InteropFeature.IMAGE_INPUT);
                }
                if ("input_file".equalsIgnoreCase(type)) {
                    features.add(InteropFeature.FILE_INPUT);
                }
            }
        }
    }

    private void collectResponsesFeatures(Set<InteropFeature> features, JsonNode body) {
        if (body == null || !body.isObject()) {
            return;
        }
        if (body.has("tools") && body.get("tools").isArray() && !body.get("tools").isEmpty()) {
            features.add(InteropFeature.TOOLS);
        }
        if (body.has("reasoning") || body.has("reasoning_effort")) {
            features.add(InteropFeature.REASONING);
        }
        JsonNode input = body.path("input");
        if (!input.isArray()) {
            return;
        }
        for (JsonNode item : input) {
            String type = item.path("type").asText("");
            if ("input_image".equalsIgnoreCase(type) || "image_url".equalsIgnoreCase(type)) {
                features.add(InteropFeature.IMAGE_INPUT);
            }
            if ("input_file".equalsIgnoreCase(type)) {
                features.add(InteropFeature.FILE_INPUT);
            }
        }
    }

    private void collectAnthropicFeatures(Set<InteropFeature> features, JsonNode body) {
        if (body == null || !body.isObject()) {
            return;
        }
        if (body.has("tools") && body.get("tools").isArray() && !body.get("tools").isEmpty()) {
            features.add(InteropFeature.TOOLS);
        }
        if (body.has("thinking")) {
            features.add(InteropFeature.REASONING);
        }
        JsonNode messages = body.path("messages");
        if (!messages.isArray()) {
            return;
        }
        for (JsonNode message : messages) {
            JsonNode content = message.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode item : content) {
                String type = item.path("type").asText("");
                if ("image".equalsIgnoreCase(type)) {
                    features.add(InteropFeature.IMAGE_INPUT);
                }
                if ("document".equalsIgnoreCase(type)) {
                    features.add(InteropFeature.FILE_INPUT);
                }
            }
        }
    }

    private void collectGeminiFeatures(Set<InteropFeature> features, JsonNode body) {
        if (body == null || !body.isObject()) {
            return;
        }
        if (body.has("tools") && body.get("tools").isArray() && !body.get("tools").isEmpty()) {
            features.add(InteropFeature.TOOLS);
        }
        JsonNode generationConfig = body.path("generationConfig");
        if (generationConfig.isObject() && (generationConfig.has("thinkingConfig")
                || generationConfig.has("thinkingBudget")
                || generationConfig.has("thinkingLevel"))) {
            features.add(InteropFeature.REASONING);
        }
        JsonNode contents = body.path("contents");
        if (!contents.isArray()) {
            return;
        }
        for (JsonNode content : contents) {
            JsonNode parts = content.path("parts");
            if (!parts.isArray()) {
                continue;
            }
            for (JsonNode part : parts) {
                JsonNode fileData = part.path("fileData");
                if (fileData.isObject()) {
                    String mimeType = fileData.path("mimeType").asText("");
                    if (mimeType.startsWith("image/")) {
                        features.add(InteropFeature.IMAGE_INPUT);
                    } else {
                        features.add(InteropFeature.FILE_INPUT);
                    }
                }
            }
        }
    }
}
