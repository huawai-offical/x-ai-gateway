package com.prodigalgal.xaigateway.gateway.core.resource;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceArtifact;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceLineage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceLifecycle;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCacheReferenceEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayAsyncResourceRepository;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
@Transactional
public class GatewayPublicResourceService {

    private final GatewayAsyncResourceRepository gatewayAsyncResourceRepository;
    private final GatewayAsyncResourceService gatewayAsyncResourceService;
    private final GatewayAsyncResourceCanonicalizer gatewayAsyncResourceCanonicalizer;
    private final GatewayCacheResourceService gatewayCacheResourceService;
    private final ObjectMapper objectMapper;

    public GatewayPublicResourceService(
            GatewayAsyncResourceRepository gatewayAsyncResourceRepository,
            GatewayAsyncResourceService gatewayAsyncResourceService,
            GatewayAsyncResourceCanonicalizer gatewayAsyncResourceCanonicalizer,
            GatewayCacheResourceService gatewayCacheResourceService,
            ObjectMapper objectMapper) {
        this.gatewayAsyncResourceRepository = gatewayAsyncResourceRepository;
        this.gatewayAsyncResourceService = gatewayAsyncResourceService;
        this.gatewayAsyncResourceCanonicalizer = gatewayAsyncResourceCanonicalizer;
        this.gatewayCacheResourceService = gatewayCacheResourceService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ObjectNode listOperations(Long distributedKeyId, GatewayAsyncResourceType resourceType, String status) {
        return listOperations(distributedKeyId, resourceType, status, 100);
    }

    @Transactional(readOnly = true)
    public ObjectNode listOperations(Long distributedKeyId, String resourceType, String status) {
        return listOperations(distributedKeyId, toAsyncResourceType(normalizeResourceType(resourceType)), status, 100);
    }

    private ObjectNode listOperations(
            Long distributedKeyId,
            GatewayAsyncResourceType resourceType,
            String status,
            int limit) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "list");
        ArrayNode data = response.putArray("data");
        gatewayAsyncResourceRepository.search(
                        distributedKeyId,
                resourceType,
                normalizeStatus(status),
                PageRequest.of(0, limit))
                .stream()
                .filter(entity -> isPublicOperationType(entity.getResourceType()))
                .forEach(entity -> data.add(toOperationResponse(entity)));
        response.put("has_more", false);
        return response;
    }

    @Transactional(readOnly = true)
    public ObjectNode getOperation(Long distributedKeyId, String operationName) {
        return toOperationResponse(resolvePublicAsyncResource(distributedKeyId, operationName, null));
    }

    public ObjectNode cancelOperation(Long distributedKeyId, String operationName) {
        GatewayAsyncResourceEntity entity = resolvePublicAsyncResource(distributedKeyId, operationName, null);
        JsonNode payload = switch (entity.getResourceType()) {
            case UPLOAD -> gatewayAsyncResourceService.cancelUpload(entity.getResourceKey(), distributedKeyId);
            case VIDEO -> gatewayAsyncResourceService.cancelVideoTask(entity.getResourceKey(), distributedKeyId);
            case MUSIC -> gatewayAsyncResourceService.cancelMusicTask(entity.getResourceKey(), distributedKeyId);
            default -> throw new IllegalArgumentException("当前 operation 类型不支持 cancel。");
        };
        return toOperationResponse(resolveAsyncResource(distributedKeyId, payload.path("id").asText(entity.getResourceKey()), null));
    }

    public ObjectNode waitOperation(Long distributedKeyId, String operationName) {
        return waitOperation(distributedKeyId, operationName, null);
    }

    public ObjectNode waitOperation(Long distributedKeyId, String operationName, JsonNode requestBody) {
        long waitMillis = boundedWaitMillis(requestBody);
        if (waitMillis <= 0) {
            ObjectNode response = getOperation(distributedKeyId, operationName);
            response.put("waited", true);
            response.put("wait_mode", "immediate");
            response.put("timeout", false);
            return response;
        }

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
        ObjectNode response = getOperation(distributedKeyId, operationName);
        while (!response.path("done").asBoolean(false) && System.nanoTime() < deadline) {
            sleepUntilNextPoll(deadline);
            response = getOperation(distributedKeyId, operationName);
        }
        response.put("waited", true);
        response.put("wait_mode", "polling");
        response.put("timeout", !response.path("done").asBoolean(false));
        response.put("max_wait_ms", waitMillis);
        return response;
    }

    public ObjectNode deleteOperation(Long distributedKeyId, String operationName) {
        GatewayAsyncResourceEntity entity = resolvePublicAsyncResource(distributedKeyId, operationName, null);
        entity.setDeleted(true);
        ObjectNode metadata = readObject(entity.getMetadataJson());
        metadata.put("deleted_at", Instant.now().getEpochSecond());
        appendEvent(metadata, "operation_deleted", entity.getStatus());
        entity.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(entity);
        ObjectNode response = toOperationResponse(entity);
        response.put("deleted", true);
        return response;
    }

    public JsonNode createVideo(Long distributedKeyId, JsonNode requestBody) {
        return gatewayAsyncResourceService.createVideoTask(distributedKeyId, requestBody);
    }

    public JsonNode mediaProviderMatrix() {
        return gatewayAsyncResourceService.mediaProviderMatrix();
    }

    public JsonNode getVideo(Long distributedKeyId, String videoId) {
        return gatewayAsyncResourceService.getVideoTask(normalizeResourceName(videoId), distributedKeyId);
    }

    public JsonNode cancelVideo(Long distributedKeyId, String videoId) {
        return gatewayAsyncResourceService.cancelVideoTask(normalizeResourceName(videoId), distributedKeyId);
    }

    public JsonNode downloadVideo(Long distributedKeyId, String videoId) {
        return gatewayAsyncResourceService.downloadVideoTaskArtifact(normalizeResourceName(videoId), distributedKeyId);
    }

    public JsonNode createMusic(Long distributedKeyId, JsonNode requestBody) {
        return gatewayAsyncResourceService.createMusicTask(distributedKeyId, requestBody);
    }

    public JsonNode getMusic(Long distributedKeyId, String musicId) {
        return gatewayAsyncResourceService.getMusicTask(normalizeResourceName(musicId), distributedKeyId);
    }

    public JsonNode cancelMusic(Long distributedKeyId, String musicId) {
        return gatewayAsyncResourceService.cancelMusicTask(normalizeResourceName(musicId), distributedKeyId);
    }

    public JsonNode downloadMusic(Long distributedKeyId, String musicId) {
        return gatewayAsyncResourceService.downloadMusicTaskArtifact(normalizeResourceName(musicId), distributedKeyId);
    }

    @Transactional(readOnly = true)
    public ObjectNode lineage(Long distributedKeyId, String resourceType, String resourceId) {
        String normalizedType = resourceType == null ? "" : resourceType.trim().toLowerCase(Locale.ROOT);
        if (normalizedType.equals("cache") || normalizedType.equals("caches")) {
            return cacheLineage(distributedKeyId, resourceId);
        }
        GatewayAsyncResourceType asyncType = toAsyncResourceType(normalizedType);
        GatewayAsyncResourceEntity entity = resolvePublicAsyncResource(distributedKeyId, resourceId, asyncType);
        CanonicalResourceLifecycle lifecycle = gatewayAsyncResourceCanonicalizer.toLifecycle(entity);
        CanonicalResourceLineage lineage = gatewayAsyncResourceCanonicalizer.toLineage(entity);
        List<CanonicalResourceArtifact> artifacts = gatewayAsyncResourceCanonicalizer.toArtifacts(entity);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "resource.lineage");
        response.put("resource_type", entity.getResourceType().name().toLowerCase(Locale.ROOT));
        response.put("resource_id", entity.getResourceKey());
        response.put("root", "gateway:" + entity.getResourceKey());
        ArrayNode nodes = response.putArray("nodes");
        ArrayNode edges = response.putArray("edges");
        nodes.add(node("gateway:" + entity.getResourceKey(), entity.getResourceType().name().toLowerCase(Locale.ROOT), entity.getResourceKey(), Map.of(
                "status", lifecycle.normalizedStatus() == null ? "" : lifecycle.normalizedStatus(),
                "objectMode", lineage.objectMode() == null ? "" : lineage.objectMode()
        )));
        nodes.add(node("distributed_key:" + entity.getDistributedKeyId(), "distributed_key", String.valueOf(entity.getDistributedKeyId()), Map.of()));
        edges.add(edge("gateway:" + entity.getResourceKey(), "distributed_key:" + entity.getDistributedKeyId(), "scoped_to_key"));
        if (lineage.requestModel() != null && !lineage.requestModel().isBlank()) {
            nodes.add(node("model:" + lineage.requestModel(), "model", lineage.requestModel(), Map.of()));
            edges.add(edge("gateway:" + entity.getResourceKey(), "model:" + lineage.requestModel(), "requested_model"));
        }
        nodes.add(payloadNode("request:" + entity.getResourceKey(), "request_payload", readObject(entity.getRequestPayloadJson())));
        edges.add(edge("gateway:" + entity.getResourceKey(), "request:" + entity.getResourceKey(), "created_from_request"));
        nodes.add(payloadNode("response:" + entity.getResourceKey(), "response_payload", readObject(entity.getResponsePayloadJson())));
        edges.add(edge("gateway:" + entity.getResourceKey(), "response:" + entity.getResourceKey(), "has_response"));
        if (lineage.upstreamObjectId() != null && !lineage.upstreamObjectId().isBlank()) {
            nodes.add(node("upstream:" + lineage.upstreamObjectId(), "upstream_object", lineage.upstreamObjectId(), Map.of()));
            edges.add(edge("gateway:" + entity.getResourceKey(), "upstream:" + lineage.upstreamObjectId(), "backed_by_upstream_object"));
        }
        if (lineage.credentialId() != null) {
            nodes.add(node("credential:" + lineage.credentialId(), "credential", String.valueOf(lineage.credentialId()), Map.of()));
            edges.add(edge("gateway:" + entity.getResourceKey(), "credential:" + lineage.credentialId(), "uses_credential"));
        }
        if (lineage.siteProfileId() != null) {
            nodes.add(node("site_profile:" + lineage.siteProfileId(), "site_profile", String.valueOf(lineage.siteProfileId()), Map.of()));
            edges.add(edge("gateway:" + entity.getResourceKey(), "site_profile:" + lineage.siteProfileId(), "uses_site_profile"));
        }
        for (String part : lineage.parts()) {
            nodes.add(node("part:" + part, "upload_part", part, Map.of()));
            edges.add(edge("gateway:" + entity.getResourceKey(), "part:" + part, "contains_part"));
        }
        for (CanonicalResourceArtifact artifact : artifacts) {
            String nodeId = "artifact:" + artifact.artifactId();
            nodes.add(node(nodeId, artifact.artifactKind(), artifact.displayName(), artifact.attributes()));
            edges.add(edge("gateway:" + entity.getResourceKey(), nodeId, "references_artifact"));
        }
        response.set("lifecycle", objectMapper.valueToTree(lifecycle));
        response.set("summary", summary(entity.getResourceType().name().toLowerCase(Locale.ROOT), entity.getResourceKey(), lifecycle.normalizedStatus(), nodes, edges));
        return response;
    }

    private ObjectNode cacheLineage(Long distributedKeyId, String resourceId) {
        UpstreamCacheReferenceEntity entity = gatewayCacheResourceService.resolve(distributedKeyId, resourceId);
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "resource.lineage");
        response.put("resource_type", "cache");
        response.put("resource_id", "cache_" + entity.getId());
        response.put("root", "cache:" + entity.getId());
        ArrayNode nodes = response.putArray("nodes");
        ArrayNode edges = response.putArray("edges");
        nodes.add(node("cache:" + entity.getId(), "cache", entity.getExternalCacheRef(), Map.of(
                "status", entity.getStatus(),
                "modelGroup", entity.getModelGroup(),
                "prefixHash", entity.getPrefixHash()
        )));
        nodes.add(node("distributed_key:" + entity.getDistributedKeyId(), "distributed_key", String.valueOf(entity.getDistributedKeyId()), Map.of()));
        edges.add(edge("cache:" + entity.getId(), "distributed_key:" + entity.getDistributedKeyId(), "scoped_to_key"));
        nodes.add(node("provider:" + entity.getProviderType().name(), "provider", entity.getProviderType().name(), Map.of()));
        edges.add(edge("cache:" + entity.getId(), "provider:" + entity.getProviderType().name(), "provided_by"));
        nodes.add(node("model:" + entity.getModelGroup(), "model", entity.getModelGroup(), Map.of()));
        edges.add(edge("cache:" + entity.getId(), "model:" + entity.getModelGroup(), "for_model"));
        nodes.add(node("prefix:" + entity.getPrefixHash(), "cache_prefix", entity.getPrefixHash(), Map.of()));
        edges.add(edge("cache:" + entity.getId(), "prefix:" + entity.getPrefixHash(), "indexes_prefix"));
        nodes.add(node("external_cache:" + entity.getExternalCacheRef(), "external_cache_ref", entity.getExternalCacheRef(), Map.of()));
        edges.add(edge("cache:" + entity.getId(), "external_cache:" + entity.getExternalCacheRef(), "maps_to_external_ref"));
        nodes.add(node("credential:" + entity.getCredentialId(), "credential", String.valueOf(entity.getCredentialId()), Map.of()));
        edges.add(edge("cache:" + entity.getId(), "credential:" + entity.getCredentialId(), "uses_credential"));
        response.set("summary", summary("cache", "cache_" + entity.getId(), entity.getStatus(), nodes, edges));
        return response;
    }

    private ObjectNode toOperationResponse(GatewayAsyncResourceEntity entity) {
        CanonicalResourceLifecycle lifecycle = gatewayAsyncResourceCanonicalizer.toLifecycle(entity);
        ObjectNode response = objectMapper.createObjectNode();
        response.put("name", "operations/" + entity.getResourceKey());
        response.put("object", "operation");
        response.put("done", lifecycle.terminal());
        ObjectNode metadata = response.putObject("metadata");
        metadata.put("resource_key", entity.getResourceKey());
        metadata.put("resource_type", entity.getResourceType().name());
        metadata.put("status", entity.getStatus());
        if (entity.getUpstreamObjectId() != null) {
            metadata.put("upstream_object_id", entity.getUpstreamObjectId());
        }
        response.set("response", gatewayAsyncResourceCanonicalizer.readPayload(entity.getResponsePayloadJson()));
        return response;
    }

    private ObjectNode payloadNode(String id, String type, ObjectNode payload) {
        ObjectNode node = node(id, type, type, Map.of(
                "fieldCount", payload.size(),
                "hasError", payload.has("error")
        ));
        ArrayNode fields = node.withArray("sampleFields");
        payload.properties().forEach(entry -> {
            if (fields.size() < 8) {
                fields.add(entry.getKey());
            }
        });
        return node;
    }

    private ObjectNode summary(String resourceType, String resourceId, String status, ArrayNode nodes, ArrayNode edges) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("resource_type", resourceType);
        summary.put("resource_id", resourceId);
        summary.put("status", status == null ? "" : status);
        summary.put("node_count", nodes.size());
        summary.put("edge_count", edges.size());
        return summary;
    }

    private GatewayAsyncResourceEntity resolveAsyncResource(
            Long distributedKeyId,
            String resourceName,
            GatewayAsyncResourceType expectedType) {
        String normalized = normalizeResourceName(resourceName);
        GatewayAsyncResourceEntity entity = gatewayAsyncResourceRepository
                .findByResourceKeyAndDistributedKeyIdAndDeletedFalse(normalized, distributedKeyId)
                .orElseGet(() -> gatewayAsyncResourceRepository
                        .findAllByDistributedKeyIdAndUpstreamObjectIdAndDeletedFalse(distributedKeyId, normalized)
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("未找到指定的资源对象。")));
        if (expectedType != null && entity.getResourceType() != expectedType) {
            throw new IllegalArgumentException("资源类型不匹配。");
        }
        return entity;
    }

    private GatewayAsyncResourceEntity resolvePublicAsyncResource(
            Long distributedKeyId,
            String resourceName,
            GatewayAsyncResourceType expectedType) {
        GatewayAsyncResourceEntity entity = resolveAsyncResource(distributedKeyId, resourceName, expectedType);
        if (!isPublicOperationType(entity.getResourceType())) {
            throw new IllegalArgumentException("该资源类型不属于当前公开功能性服务 API。");
        }
        return entity;
    }

    private GatewayAsyncResourceType toAsyncResourceType(String resourceType) {
        return switch (resourceType) {
            case "response", "responses" -> GatewayAsyncResourceType.RESPONSE;
            case "vector_store", "vector_stores" -> GatewayAsyncResourceType.VECTOR_STORE;
            case "vector_store_file", "vector_store_files" -> GatewayAsyncResourceType.VECTOR_STORE_FILE;
            case "vector_store_file_batch", "vector_store_file_batches" -> GatewayAsyncResourceType.VECTOR_STORE_FILE_BATCH;
            case "upload", "uploads" -> GatewayAsyncResourceType.UPLOAD;
            case "video", "videos" -> GatewayAsyncResourceType.VIDEO;
            case "music", "musics" -> GatewayAsyncResourceType.MUSIC;
            case "operation", "operations", "" -> null;
            default -> throw new IllegalArgumentException("不支持的 resourceType：" + resourceType);
        };
    }

    private boolean isPublicOperationType(GatewayAsyncResourceType resourceType) {
        return true;
    }

    private String normalizeResourceType(String resourceType) {
        return resourceType == null ? "" : resourceType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? null : status.trim().toLowerCase(Locale.ROOT);
    }

    private long boundedWaitMillis(JsonNode requestBody) {
        if (requestBody == null || requestBody.isNull() || requestBody.isMissingNode()) {
            return 0;
        }
        long value = optionalLong(requestBody, "timeoutMs");
        if (value <= 0) {
            value = optionalLong(requestBody, "maxWaitMs");
        }
        if (value <= 0) {
            value = optionalLong(requestBody, "timeout_ms");
        }
        if (value <= 0) {
            value = optionalLong(requestBody, "max_wait_ms");
        }
        if (value <= 0) {
            long seconds = optionalLong(requestBody, "timeout");
            value = seconds <= 0 ? 0 : seconds * 1000;
        }
        return Math.min(value, 2_000);
    }

    private long optionalLong(JsonNode node, String fieldName) {
        Long value = longValue(node, fieldName);
        return value == null ? 0 : value;
    }

    private void sleepUntilNextPoll(long deadline) {
        long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remainingMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(Math.min(50, remainingMillis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String normalizeResourceName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("resource id 不能为空。");
        }
        String normalized = value.trim();
        if (normalized.startsWith("operations/")) {
            normalized = normalized.substring("operations/".length());
        }
        return normalized;
    }

    private ObjectNode node(String id, String type, String label, Map<String, ?> attributes) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("type", type);
        node.put("label", label);
        node.set("attributes", objectMapper.valueToTree(attributes == null ? Map.of() : attributes));
        return node;
    }

    private ObjectNode edge(String from, String to, String relation) {
        ObjectNode edge = objectMapper.createObjectNode();
        edge.put("from", from);
        edge.put("to", to);
        edge.put("relation", relation);
        return edge;
    }

    private void appendEvent(ObjectNode metadata, String eventType, String status) {
        metadata.withArray("events").addObject()
                .put("type", eventType)
                .put("status", status)
                .put("at", Instant.now().getEpochSecond());
    }

    private ObjectNode readObject(String json) {
        try {
            JsonNode node = json == null || json.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(json);
            return node instanceof ObjectNode objectNode ? objectNode : objectMapper.createObjectNode();
        } catch (JacksonException exception) {
            throw new IllegalStateException("解析资源 JSON 失败。", exception);
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化资源 JSON 失败。", exception);
        }
    }

    private Long longValue(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.path(fieldName);
        return value == null || value.isMissingNode() || value.isNull() ? null : value.asLong();
    }
}
