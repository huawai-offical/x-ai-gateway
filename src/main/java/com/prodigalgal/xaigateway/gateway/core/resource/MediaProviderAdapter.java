package com.prodigalgal.xaigateway.gateway.core.resource;

import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

interface MediaProviderAdapter {

    String providerFamily();

    String adapterName();

    GatewayAsyncResourceType resourceType();

    boolean supports(GatewayAsyncResourceType type, ObjectNode requestPayload);

    MediaProviderCreateResult create(String resourceKey, Long distributedKeyId, ObjectNode requestPayload, Instant now);

    ObjectNode get(GatewayAsyncResourceEntity entity, ObjectNode metadata, ObjectNode response, Instant now);

    ObjectNode cancel(GatewayAsyncResourceEntity entity, ObjectNode metadata, ObjectNode response, Instant now);

    ObjectNode download(GatewayAsyncResourceEntity entity, ObjectNode metadata, ObjectNode response, Instant now);
}

record MediaProviderCreateResult(
        ObjectNode response,
        ObjectNode metadata,
        String providerTaskId,
        String status
) {
}

class GeminiVeoMediaProviderAdapter implements MediaProviderAdapter {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    GeminiVeoMediaProviderAdapter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public String providerFamily() {
        return "gemini";
    }

    @Override
    public String adapterName() {
        return "gemini_veo";
    }

    @Override
    public GatewayAsyncResourceType resourceType() {
        return GatewayAsyncResourceType.VIDEO;
    }

    @Override
    public boolean supports(GatewayAsyncResourceType type, ObjectNode requestPayload) {
        if (type != GatewayAsyncResourceType.VIDEO) {
            return false;
        }
        String providerMode = text(requestPayload, "provider_mode");
        String provider = firstText(requestPayload, "provider_family", "provider", "adapter");
        return isAdapterMode(providerMode) || isGeminiVeo(provider);
    }

    @Override
    public MediaProviderCreateResult create(String resourceKey, Long distributedKeyId, ObjectNode requestPayload, Instant now) {
        String providerTaskId = "gemini_veo_" + UUID.randomUUID().toString().replace("-", "");
        String status = normalizeStatus(firstText(requestPayload, "status", "initial_status"), "queued");

        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", resourceKey);
        response.put("object", "video.generation");
        response.put("status", status);
        response.put("created", epoch(now));
        response.put("task_kind", "video_generation");
        response.put("provider_family", providerFamily());
        response.put("provider_adapter", adapterName());
        response.put("provider_task_id", providerTaskId);
        putIfPresent(response, "model", text(requestPayload, "model"));
        if ("completed".equals(status)) {
            attachArtifact(response, resourceKey, now);
        }

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("object_mode", "provider_specific_media_adapter");
        metadata.put("task_kind", "video_generation");
        metadata.put("provider_mode", "adapter");
        metadata.put("provider_family", providerFamily());
        metadata.put("provider_adapter", adapterName());
        metadata.put("provider_task_id", providerTaskId);
        metadata.put("provider_support_tier", "provider_specific_adapter");
        metadata.put("provider_support_status", "SUPPORTED");
        metadata.put("provider_smoke_hint", "使用 provider_mode=adapter, provider_family=gemini 执行本地 Veo 生命周期 smoke；真实 key 仅从环境变量注入。");
        metadata.put("distributed_key_id", distributedKeyId == null ? 0L : distributedKeyId);
        appendEvent(metadata, "created", status, now);

        return new MediaProviderCreateResult(response, metadata, providerTaskId, status);
    }

    @Override
    public ObjectNode get(GatewayAsyncResourceEntity entity, ObjectNode metadata, ObjectNode response, Instant now) {
        if (!isTerminal(response.path("status").asText(entity.getStatus()))) {
            response.put("status", "completed");
            response.put("completed_at", epoch(now));
            attachArtifact(response, entity.getResourceKey(), now);
            metadata.put("provider_status", "completed");
            metadata.put("artifact_expires_at", epoch(now.plusSeconds(3_600)));
            appendEvent(metadata, "synced", "completed", now);
        }
        return response;
    }

    @Override
    public ObjectNode cancel(GatewayAsyncResourceEntity entity, ObjectNode metadata, ObjectNode response, Instant now) {
        if (!isTerminal(response.path("status").asText(entity.getStatus()))) {
            response.put("status", "cancelled");
            response.put("cancelled_at", epoch(now));
            metadata.put("cancel_reason", "user_cancelled");
            metadata.put("provider_status", "cancelled");
            appendEvent(metadata, "cancelled", "cancelled", now);
        }
        return response;
    }

    @Override
    public ObjectNode download(GatewayAsyncResourceEntity entity, ObjectNode metadata, ObjectNode response, Instant now) {
        if (!"completed".equalsIgnoreCase(response.path("status").asText(entity.getStatus()))) {
            throw new IllegalStateException("当前 Video 任务尚未完成，不能下载产物。");
        }
        JsonNode artifact = response.path("artifacts").isArray() && !response.path("artifacts").isEmpty()
                ? response.path("artifacts").get(0)
                : null;
        ObjectNode download = objectMapper.createObjectNode();
        download.put("id", entity.getResourceKey() + "_download");
        download.put("object", "media.artifact_download");
        download.put("resource_id", entity.getResourceKey());
        download.put("provider_family", providerFamily());
        download.put("provider_adapter", adapterName());
        download.put("artifact_id", artifact == null ? entity.getResourceKey() + "_artifact" : artifact.path("id").asText());
        download.put("content_type", artifact == null ? "video/mp4" : artifact.path("mime_type").asText("video/mp4"));
        download.put("download_url", artifact == null ? downloadUrl(entity.getResourceKey()) : artifact.path("download_url").asText(downloadUrl(entity.getResourceKey())));
        download.put("expires_at", metadata.path("artifact_expires_at").asLong(epoch(now.plusSeconds(3_600))));
        appendEvent(metadata, "downloaded", response.path("status").asText(entity.getStatus()), now);
        return download;
    }

    private void attachArtifact(ObjectNode response, String resourceKey, Instant now) {
        var artifacts = response.putArray("artifacts");
        artifacts.addObject()
                .put("id", resourceKey + "_artifact_0")
                .put("type", "video")
                .put("mime_type", "video/mp4")
                .put("download_url", downloadUrl(resourceKey))
                .put("expires_at", epoch(now.plusSeconds(3_600)));
        response.put("output_url", downloadUrl(resourceKey));
    }

    private String downloadUrl(String resourceKey) {
        return "https://gateway.local/api/v1/videos/" + resourceKey + "/download";
    }

    private boolean isAdapterMode(String providerMode) {
        return providerMode != null && (
                "adapter".equalsIgnoreCase(providerMode)
                        || "provider_specific".equalsIgnoreCase(providerMode)
                        || "provider-specific".equalsIgnoreCase(providerMode)
        );
    }

    private boolean isGeminiVeo(String provider) {
        if (provider == null || provider.isBlank()) {
            return false;
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("gemini") || normalized.equals("veo") || normalized.equals("gemini_veo");
    }

    private String normalizeStatus(String status, String fallback) {
        return status == null || status.isBlank() ? fallback : status.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isTerminal(String status) {
        return "completed".equalsIgnoreCase(status)
                || "failed".equalsIgnoreCase(status)
                || "cancelled".equalsIgnoreCase(status)
                || "canceled".equalsIgnoreCase(status);
    }

    private long epoch(Instant instant) {
        return (instant == null ? Instant.now(clock) : instant).getEpochSecond();
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.path(fieldName);
        return value == null || value.isMissingNode() || value.isNull() || value.asText().isBlank()
                ? null
                : value.asText().trim();
    }

    private void putIfPresent(ObjectNode node, String fieldName, String value) {
        if (value != null && !value.isBlank()) {
            node.put(fieldName, value);
        }
    }

    private void appendEvent(ObjectNode metadata, String eventType, String status, Instant now) {
        metadata.withArray("events").addObject()
                .put("type", eventType)
                .put("status", status)
                .put("at", epoch(now));
    }
}

class SunoMusicMediaProviderAdapter implements MediaProviderAdapter {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    SunoMusicMediaProviderAdapter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public String providerFamily() {
        return "suno";
    }

    @Override
    public String adapterName() {
        return "suno_music";
    }

    @Override
    public GatewayAsyncResourceType resourceType() {
        return GatewayAsyncResourceType.MUSIC;
    }

    @Override
    public boolean supports(GatewayAsyncResourceType type, ObjectNode requestPayload) {
        if (type != GatewayAsyncResourceType.MUSIC) {
            return false;
        }
        String providerMode = text(requestPayload, "provider_mode");
        String provider = firstText(requestPayload, "provider_family", "provider", "adapter", "channel");
        return isSuno(provider) || (isAdapterMode(providerMode) && provider == null);
    }

    @Override
    public MediaProviderCreateResult create(String resourceKey, Long distributedKeyId, ObjectNode requestPayload, Instant now) {
        String providerTaskId = "suno_music_" + UUID.randomUUID().toString().replace("-", "");
        String rawStatus = normalizeProviderStatus(firstText(requestPayload, "provider_status", "status", "initial_status"), "submitted");
        String status = normalizeGatewayStatus(rawStatus);
        String action = normalizeAction(firstText(requestPayload, "action", "task_action", "mode"));
        String model = defaultString(firstText(requestPayload, "model", "mv"), "suno_music");

        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", resourceKey);
        response.put("object", "music.generation");
        response.put("status", status);
        response.put("provider_status", rawStatus);
        response.put("created", epoch(now));
        response.put("task_kind", "music_generation");
        response.put("action", action);
        response.put("provider_family", providerFamily());
        response.put("provider_adapter", adapterName());
        response.put("provider_task_id", providerTaskId);
        response.put("model", model);
        putIfPresent(response, "title", text(requestPayload, "title"));
        putIfPresent(response, "tags", text(requestPayload, "tags"));
        putIfPresent(response, "prompt", firstText(requestPayload, "prompt", "gpt_description_prompt"));
        if (requestPayload.hasNonNull("make_instrumental")) {
            response.put("make_instrumental", requestPayload.path("make_instrumental").asBoolean());
        }
        if (requestPayload.hasNonNull("duration_seconds")) {
            response.put("duration_seconds", Math.max(0, requestPayload.path("duration_seconds").asInt()));
        }
        attachUsage(response, requestPayload);
        if ("completed".equals(status)) {
            attachArtifact(response, resourceKey, requestPayload, now);
        }
        if ("failed".equals(status)) {
            attachFailure(response, requestPayload);
        }

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("object_mode", "provider_specific_media_adapter");
        metadata.put("task_kind", "music_generation");
        metadata.put("provider_mode", "adapter");
        metadata.put("provider_family", providerFamily());
        metadata.put("provider_adapter", adapterName());
        metadata.put("provider_task_id", providerTaskId);
        metadata.put("provider_action", action);
        metadata.put("provider_fetch_mode", "batch_polling");
        metadata.put("provider_support_tier", "provider_specific_adapter");
        metadata.put("provider_support_status", "SUPPORTED");
        metadata.put("provider_capability", "music_generation");
        metadata.put("provider_pricing_source", "operator_configured_suno_music_pricing");
        metadata.put("provider_smoke_hint", "设置 XAG_SMOKE_SUNO=true、XAG_SMOKE_SUNO_BASE_URL 与 XAG_SMOKE_SUNO_API_KEY/SUNO_API_KEY 后才执行真实 Suno-like smoke；默认跳过且不消耗额度。");
        metadata.put("provider_status", rawStatus);
        metadata.put("distributed_key_id", distributedKeyId == null ? 0L : distributedKeyId);
        appendFailureClasses(metadata);
        appendEvent(metadata, "created", status, now);

        return new MediaProviderCreateResult(response, metadata, providerTaskId, status);
    }

    @Override
    public ObjectNode get(GatewayAsyncResourceEntity entity, ObjectNode metadata, ObjectNode response, Instant now) {
        if (!isTerminal(response.path("status").asText(entity.getStatus()))) {
            response.put("status", "completed");
            response.put("provider_status", "success");
            response.put("completed_at", epoch(now));
            attachArtifact(response, entity.getResourceKey(), response, now);
            metadata.put("provider_status", "success");
            metadata.put("artifact_expires_at", epoch(now.plusSeconds(3_600)));
            appendEvent(metadata, "synced", "completed", now);
        }
        return response;
    }

    @Override
    public ObjectNode cancel(GatewayAsyncResourceEntity entity, ObjectNode metadata, ObjectNode response, Instant now) {
        if (!isTerminal(response.path("status").asText(entity.getStatus()))) {
            response.put("status", "cancelled");
            response.put("provider_status", "cancelled");
            response.put("cancelled_at", epoch(now));
            metadata.put("cancel_reason", "user_cancelled");
            metadata.put("provider_status", "cancelled");
            appendEvent(metadata, "cancelled", "cancelled", now);
        }
        return response;
    }

    @Override
    public ObjectNode download(GatewayAsyncResourceEntity entity, ObjectNode metadata, ObjectNode response, Instant now) {
        if (!"completed".equalsIgnoreCase(response.path("status").asText(entity.getStatus()))) {
            throw new IllegalStateException("当前 Music 任务尚未完成，不能下载产物。");
        }
        JsonNode artifact = response.path("artifacts").isArray() && !response.path("artifacts").isEmpty()
                ? response.path("artifacts").get(0)
                : null;
        ObjectNode download = objectMapper.createObjectNode();
        download.put("id", entity.getResourceKey() + "_download");
        download.put("object", "media.artifact_download");
        download.put("resource_id", entity.getResourceKey());
        download.put("provider_family", providerFamily());
        download.put("provider_adapter", adapterName());
        download.put("artifact_id", artifact == null ? entity.getResourceKey() + "_artifact" : artifact.path("id").asText());
        download.put("content_type", artifact == null ? "audio/mpeg" : artifact.path("mime_type").asText("audio/mpeg"));
        download.put("download_url", artifact == null ? downloadUrl(entity.getResourceKey()) : artifact.path("download_url").asText(downloadUrl(entity.getResourceKey())));
        download.put("expires_at", metadata.path("artifact_expires_at").asLong(epoch(now.plusSeconds(3_600))));
        appendEvent(metadata, "downloaded", response.path("status").asText(entity.getStatus()), now);
        return download;
    }

    private void attachArtifact(ObjectNode response, String resourceKey, JsonNode source, Instant now) {
        var artifacts = response.putArray("artifacts");
        ObjectNode audio = artifacts.addObject();
        audio.put("id", resourceKey + "_artifact_0");
        audio.put("type", "audio");
        audio.put("mime_type", "audio/mpeg");
        audio.put("download_url", firstText(source, "audio_url", "output_url", "download_url") == null
                ? downloadUrl(resourceKey)
                : firstText(source, "audio_url", "output_url", "download_url"));
        audio.put("expires_at", epoch(now.plusSeconds(3_600)));
        String imageUrl = firstText(source, "image_url", "cover_url");
        if (imageUrl != null) {
            ObjectNode cover = artifacts.addObject();
            cover.put("id", resourceKey + "_cover_0");
            cover.put("type", "image");
            cover.put("mime_type", "image/jpeg");
            cover.put("download_url", imageUrl);
            cover.put("expires_at", epoch(now.plusSeconds(3_600)));
        }
        response.put("output_url", audio.path("download_url").asText(downloadUrl(resourceKey)));
    }

    private void attachUsage(ObjectNode response, ObjectNode requestPayload) {
        ObjectNode usage = response.putObject("usage");
        usage.put("unit", "task");
        usage.put("tasks", 1);
        if (requestPayload.hasNonNull("estimated_credits")) {
            usage.put("estimated_credits", Math.max(0, requestPayload.path("estimated_credits").asInt()));
        }
        usage.put("attribution", "provider_specific_media_adapter");
    }

    private void attachFailure(ObjectNode response, ObjectNode requestPayload) {
        ObjectNode error = response.putObject("error");
        error.put("type", "provider_failure");
        error.put("code", defaultString(firstText(requestPayload, "failure_code", "error_type"), "PROVIDER_TASK_FAILED"));
        error.put("message", defaultString(firstText(requestPayload, "fail_reason", "error_message"), "Suno-like music task failed."));
    }

    private void appendFailureClasses(ObjectNode metadata) {
        var classes = metadata.putArray("provider_failure_classes");
        classes.add("AUTHENTICATION_FAILED");
        classes.add("QUOTA_EXCEEDED");
        classes.add("NETWORK_ERROR");
        classes.add("PARAMETER_UNSUPPORTED");
        classes.add("PROVIDER_RATE_LIMITED");
    }

    private String downloadUrl(String resourceKey) {
        return "https://gateway.local/api/v1/music/" + resourceKey + "/download";
    }

    private boolean isAdapterMode(String providerMode) {
        return providerMode != null && (
                "adapter".equalsIgnoreCase(providerMode)
                        || "provider_specific".equalsIgnoreCase(providerMode)
                        || "provider-specific".equalsIgnoreCase(providerMode)
        );
    }

    private boolean isSuno(String provider) {
        if (provider == null || provider.isBlank()) {
            return false;
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT).replace("-", "_");
        return normalized.equals("suno")
                || normalized.equals("suno_like")
                || normalized.equals("suno_music")
                || normalized.equals("sunoapi");
    }

    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return "music";
        }
        String normalized = action.trim().toLowerCase(Locale.ROOT);
        return "lyrics".equals(normalized) ? "lyrics" : "music";
    }

    private String normalizeProviderStatus(String status, String fallback) {
        return status == null || status.isBlank() ? fallback : status.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeGatewayStatus(String providerStatus) {
        return switch (providerStatus) {
            case "success", "succeeded", "complete", "completed" -> "completed";
            case "processing", "running", "in_progress" -> "in_progress";
            case "failed", "failure", "error" -> "failed";
            case "cancelled", "canceled" -> "cancelled";
            default -> "queued";
        };
    }

    private boolean isTerminal(String status) {
        return "completed".equalsIgnoreCase(status)
                || "failed".equalsIgnoreCase(status)
                || "cancelled".equalsIgnoreCase(status)
                || "canceled".equalsIgnoreCase(status);
    }

    private long epoch(Instant instant) {
        return (instant == null ? Instant.now(clock) : instant).getEpochSecond();
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.path(fieldName);
        return value == null || value.isMissingNode() || value.isNull() || value.asText().isBlank()
                ? null
                : value.asText().trim();
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void putIfPresent(ObjectNode node, String fieldName, String value) {
        if (value != null && !value.isBlank()) {
            node.put(fieldName, value);
        }
    }

    private void appendEvent(ObjectNode metadata, String eventType, String status, Instant now) {
        metadata.withArray("events").addObject()
                .put("type", eventType)
                .put("status", status)
                .put("at", epoch(now));
    }
}
