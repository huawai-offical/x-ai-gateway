package com.prodigalgal.xaigateway.gateway.core.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayAsyncResourceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GatewayAsyncResourceService {

    private final GatewayAsyncResourceRepository gatewayAsyncResourceRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GatewayAsyncResourceService(
            GatewayAsyncResourceRepository gatewayAsyncResourceRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.gatewayAsyncResourceRepository = gatewayAsyncResourceRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public JsonNode storeResponse(Long distributedKeyId, String requestModel, JsonNode requestPayload, JsonNode responsePayload) {
        String resourceKey = "resp_" + UUID.randomUUID().toString().replace("-", "");
        ObjectNode storedResponse = copyObject(responsePayload);
        storedResponse.put("id", resourceKey);
        if (!storedResponse.has("status")) {
            storedResponse.put("status", "completed");
        }

        ObjectNode metadata = objectMapper.createObjectNode();
        appendEvent(metadata, "stored", storedResponse.path("status").asText("completed"));

        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey(resourceKey);
        entity.setDistributedKeyId(distributedKeyId);
        entity.setResourceType(GatewayAsyncResourceType.RESPONSE);
        entity.setRequestModel(requestModel);
        entity.setStatus(storedResponse.path("status").asText("completed"));
        entity.setRequestPayloadJson(writeJson(requestPayload));
        entity.setResponsePayloadJson(writeJson(storedResponse));
        entity.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(entity);
        return storedResponse;
    }

    @Transactional(readOnly = true)
    public JsonNode getResponse(String responseId, Long distributedKeyId) {
        GatewayAsyncResourceEntity entity = getRequired(responseId, GatewayAsyncResourceType.RESPONSE, distributedKeyId);
        return readJson(entity.getResponsePayloadJson());
    }

    public JsonNode deleteResponse(String responseId, Long distributedKeyId) {
        GatewayAsyncResourceEntity entity = getRequired(responseId, GatewayAsyncResourceType.RESPONSE, distributedKeyId);
        entity.setDeleted(true);
        entity.setStatus("deleted");
        entity.setMetadataJson(writeJson(appendEvent(readObject(entity.getMetadataJson()), "deleted", "deleted")));
        gatewayAsyncResourceRepository.save(entity);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", responseId);
        payload.put("object", "response.deleted");
        payload.put("deleted", true);
        return payload;
    }

    public JsonNode createUpload(Long distributedKeyId, JsonNode requestBody) {
        ObjectNode payload = requireObject(requestBody);
        String resourceKey = "upload_" + UUID.randomUUID().toString().replace("-", "");
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("filename", readRequiredText(payload, "filename"));
        metadata.put("bytes", payload.path("bytes").asLong());
        metadata.put("purpose", readRequiredText(payload, "purpose"));
        metadata.put("partsCount", 0);
        appendEvent(metadata, "created", "created");

        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", resourceKey);
        response.put("object", "upload");
        response.put("created_at", now().getEpochSecond());
        response.put("filename", metadata.path("filename").asText());
        response.put("bytes", metadata.path("bytes").asLong());
        response.put("purpose", metadata.path("purpose").asText());
        response.put("status", "created");

        return persistLocalResource(distributedKeyId, GatewayAsyncResourceType.UPLOAD, payload.path("model").asText(null), "created", payload, response, metadata);
    }

    @Transactional(readOnly = true)
    public JsonNode getUpload(String uploadId, Long distributedKeyId) {
        return readJson(getRequired(uploadId, GatewayAsyncResourceType.UPLOAD, distributedKeyId).getResponsePayloadJson());
    }

    public JsonNode addUploadPart(String uploadId, Long distributedKeyId) {
        GatewayAsyncResourceEntity entity = getRequired(uploadId, GatewayAsyncResourceType.UPLOAD, distributedKeyId);
        ObjectNode metadata = readObject(entity.getMetadataJson());
        ArrayNode parts = metadata.withArray("parts");
        String partId = "part_" + UUID.randomUUID().toString().replace("-", "");
        parts.add(partId);
        metadata.put("partsCount", parts.size());
        appendEvent(metadata, "part_added", entity.getStatus());
        entity.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(entity);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", partId);
        response.put("object", "upload.part");
        response.put("created_at", now().getEpochSecond());
        response.put("upload_id", uploadId);
        return response;
    }

    public JsonNode completeUpload(String uploadId, Long distributedKeyId) {
        return updateStatus(uploadId, distributedKeyId, GatewayAsyncResourceType.UPLOAD, "completed");
    }

    public JsonNode cancelUpload(String uploadId, Long distributedKeyId) {
        return updateStatus(uploadId, distributedKeyId, GatewayAsyncResourceType.UPLOAD, "cancelled");
    }

    public JsonNode createBatch(Long distributedKeyId, JsonNode requestBody) {
        ObjectNode payload = requireObject(requestBody);
        String resourceKey = "batch_" + UUID.randomUUID().toString().replace("-", "");
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("input_file_id", readRequiredText(payload, "input_file_id"));
        metadata.put("endpoint", readRequiredText(payload, "endpoint"));
        metadata.put("completion_window", payload.path("completion_window").asText("24h"));
        appendEvent(metadata, "created", "validating");

        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", resourceKey);
        response.put("object", "batch");
        response.put("created_at", now().getEpochSecond());
        response.put("status", "validating");
        response.put("input_file_id", metadata.path("input_file_id").asText());
        response.put("endpoint", metadata.path("endpoint").asText());
        response.put("completion_window", metadata.path("completion_window").asText());
        response.putNull("output_file_id");
        response.putNull("error_file_id");

        return persistLocalResource(distributedKeyId, GatewayAsyncResourceType.BATCH, payload.path("model").asText(null), "validating", payload, response, metadata);
    }

    @Transactional(readOnly = true)
    public JsonNode getBatch(String batchId, Long distributedKeyId) {
        return readJson(getRequired(batchId, GatewayAsyncResourceType.BATCH, distributedKeyId).getResponsePayloadJson());
    }

    public JsonNode cancelBatch(String batchId, Long distributedKeyId) {
        return updateStatus(batchId, distributedKeyId, GatewayAsyncResourceType.BATCH, "cancelled");
    }

    public JsonNode createTuning(Long distributedKeyId, JsonNode requestBody) {
        ObjectNode payload = requireObject(requestBody);
        String resourceKey = "ftjob_" + UUID.randomUUID().toString().replace("-", "");
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("training_file", readRequiredText(payload, "training_file"));
        appendEvent(metadata, "created", "queued");

        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", resourceKey);
        response.put("object", "fine_tuning.job");
        response.put("created_at", now().getEpochSecond());
        response.put("status", "queued");
        response.put("model", readRequiredText(payload, "model"));
        response.put("training_file", metadata.path("training_file").asText());

        return persistLocalResource(distributedKeyId, GatewayAsyncResourceType.TUNING, response.path("model").asText(), "queued", payload, response, metadata);
    }

    @Transactional(readOnly = true)
    public JsonNode getTuning(String tuningId, Long distributedKeyId) {
        return readJson(getRequired(tuningId, GatewayAsyncResourceType.TUNING, distributedKeyId).getResponsePayloadJson());
    }

    public JsonNode cancelTuning(String tuningId, Long distributedKeyId) {
        return updateStatus(tuningId, distributedKeyId, GatewayAsyncResourceType.TUNING, "cancelled");
    }

    public JsonNode createRealtimeClientSecret(Long distributedKeyId, JsonNode requestBody) {
        ObjectNode payload = requireObject(requestBody);
        String resourceKey = "sess_" + UUID.randomUUID().toString().replace("-", "");
        String clientSecret = "rt_" + UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = now().plusSeconds(3600);

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("client_secret_preview", clientSecret.substring(0, Math.min(clientSecret.length(), 12)));
        appendEvent(metadata, "created", "created");

        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", resourceKey);
        response.put("object", "realtime.session");
        response.put("model", readRequiredText(payload, "model"));
        response.put("created_at", now().getEpochSecond());
        response.put("status", "created");
        ObjectNode secret = response.putObject("client_secret");
        secret.put("value", clientSecret);
        secret.put("expires_at", expiresAt.getEpochSecond());

        return persistLocalResource(distributedKeyId, GatewayAsyncResourceType.REALTIME_SESSION, response.path("model").asText(), "created", payload, response, metadata);
    }

    private JsonNode updateStatus(
            String resourceKey,
            Long distributedKeyId,
            GatewayAsyncResourceType type,
            String status) {
        GatewayAsyncResourceEntity entity = getRequired(resourceKey, type, distributedKeyId);
        entity.setStatus(status);
        ObjectNode response = readObject(entity.getResponsePayloadJson());
        response.put("status", status);
        entity.setResponsePayloadJson(writeJson(response));
        entity.setMetadataJson(writeJson(appendEvent(readObject(entity.getMetadataJson()), "status_changed", status)));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    private JsonNode persistLocalResource(
            Long distributedKeyId,
            GatewayAsyncResourceType type,
            String requestModel,
            String status,
            JsonNode requestPayload,
            JsonNode responsePayload,
            JsonNode metadata) {
        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey(responsePayload.path("id").asText());
        entity.setDistributedKeyId(distributedKeyId);
        entity.setResourceType(type);
        entity.setRequestModel(requestModel);
        entity.setStatus(status);
        entity.setRequestPayloadJson(writeJson(requestPayload));
        entity.setResponsePayloadJson(writeJson(responsePayload));
        entity.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(entity);
        return responsePayload;
    }

    private GatewayAsyncResourceEntity getRequired(String resourceKey, GatewayAsyncResourceType type, Long distributedKeyId) {
        Optional<GatewayAsyncResourceEntity> entity = gatewayAsyncResourceRepository
                .findByResourceKeyAndResourceTypeAndDeletedFalse(resourceKey, type);
        if (entity.isEmpty() || !entity.get().getDistributedKeyId().equals(distributedKeyId)) {
            throw new IllegalArgumentException("未找到指定的异步资源对象。");
        }
        return entity.get();
    }

    private ObjectNode requireObject(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("请求体必须是 JSON object。");
        }
        return (ObjectNode) payload;
    }

    private String readRequiredText(ObjectNode payload, String field) {
        String value = payload.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("请求缺少 " + field + "。");
        }
        return value;
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private ObjectNode appendEvent(ObjectNode metadata, String eventType, String status) {
        metadata.withArray("events").addObject()
                .put("type", eventType)
                .put("status", status)
                .put("at", now().getEpochSecond());
        return metadata;
    }

    private JsonNode readJson(String json) {
        try {
            return json == null || json.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("解析异步资源 JSON 失败。", exception);
        }
    }

    private ObjectNode readObject(String json) {
        JsonNode node = readJson(json);
        return node instanceof ObjectNode objectNode ? objectNode : objectMapper.createObjectNode();
    }

    private ObjectNode copyObject(JsonNode node) {
        JsonNode copied = node == null ? objectMapper.createObjectNode() : node.deepCopy();
        return copied instanceof ObjectNode objectNode ? objectNode : objectMapper.createObjectNode();
    }

    private String writeJson(JsonNode node) {
        try {
            return node == null ? null : objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化异步资源 JSON 失败。", exception);
        }
    }
}
