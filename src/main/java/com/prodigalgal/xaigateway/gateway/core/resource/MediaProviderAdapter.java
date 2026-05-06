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
