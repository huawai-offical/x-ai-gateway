package com.prodigalgal.xaigateway.gateway.core.resource;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceArtifact;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceLifecycle;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceLineage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceTransition;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayFileBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayFileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GatewayAsyncResourceCanonicalizer {

    private final GatewayFileBindingRepository gatewayFileBindingRepository;
    private final GatewayFileRepository gatewayFileRepository;
    private final ObjectMapper objectMapper;

    public GatewayAsyncResourceCanonicalizer(
            GatewayFileBindingRepository gatewayFileBindingRepository,
            GatewayFileRepository gatewayFileRepository,
            ObjectMapper objectMapper) {
        this.gatewayFileBindingRepository = gatewayFileBindingRepository;
        this.gatewayFileRepository = gatewayFileRepository;
        this.objectMapper = objectMapper;
    }

    public CanonicalResourceLifecycle toLifecycle(GatewayAsyncResourceEntity entity) {
        JsonNode metadata = readJson(entity.getMetadataJson());
        JsonNode response = readJson(entity.getResponsePayloadJson());
        List<CanonicalResourceTransition> transitions = toTransitions(entity);
        String normalizedStatus = normalizeStatus(entity.getStatus());
        String failureReason = firstText(metadata, "failure_reason", "error_message", "last_error");
        if (failureReason == null) {
            failureReason = nestedText(response, "error", "message");
        }
        String cancelReason = firstText(metadata, "cancel_reason", "cancellation_reason");
        if (cancelReason == null) {
            cancelReason = firstText(response, "cancel_reason", "cancellation_reason");
        }
        return new CanonicalResourceLifecycle(
                entity.getResourceKey(),
                entity.getResourceType(),
                entity.getStatus(),
                normalizedStatus,
                isTerminal(normalizedStatus),
                entity.isDeleted() || "deleted".equals(normalizedStatus),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                transitions.size(),
                transitions.isEmpty() ? null : transitions.get(transitions.size() - 1),
                failureReason,
                cancelReason
        );
    }

    public List<CanonicalResourceTransition> toTransitions(GatewayAsyncResourceEntity entity) {
        return transitions(readJson(entity.getMetadataJson()));
    }

    public CanonicalResourceLineage toLineage(GatewayAsyncResourceEntity entity) {
        JsonNode metadata = readJson(entity.getMetadataJson());
        return new CanonicalResourceLineage(
                text(metadata, "object_mode"),
                entity.getResourceKey(),
                text(metadata, "upstream_object_id"),
                longValue(metadata, "credential_id"),
                longValue(metadata, "site_profile_id"),
                entity.getRequestModel(),
                textList(metadata.path("parts")),
                partBindings(metadata.path("part_bindings"))
        );
    }

    public List<CanonicalResourceArtifact> toArtifacts(GatewayAsyncResourceEntity entity) {
        List<CanonicalResourceArtifact> artifacts = new ArrayList<>();
        JsonNode metadata = readJson(entity.getMetadataJson());
        JsonNode requestPayload = readJson(entity.getRequestPayloadJson());
        JsonNode responsePayload = readJson(entity.getResponsePayloadJson());
        Long credentialId = longValue(metadata, "credential_id");

        artifacts.addAll(uploadPartArtifacts(metadata));
        artifacts.addAll(fileBindingArtifacts(requestPayload, responsePayload, credentialId));

        return List.copyOf(artifacts);
    }

    public JsonNode readPayload(String json) {
        return readJson(json);
    }

    private List<CanonicalResourceTransition> transitions(JsonNode metadata) {
        if (metadata == null || metadata.isMissingNode() || !metadata.path("events").isArray()) {
            return List.of();
        }
        List<CanonicalResourceTransition> transitions = new ArrayList<>();
        for (JsonNode item : metadata.path("events")) {
            transitions.add(new CanonicalResourceTransition(
                    text(item, "type"),
                    normalizeStatus(text(item, "status")),
                    epochSeconds(item.path("at"))
            ));
        }
        return List.copyOf(transitions);
    }

    private List<CanonicalResourceArtifact> uploadPartArtifacts(JsonNode metadata) {
        if (metadata == null || metadata.isMissingNode() || !metadata.path("parts").isArray()) {
            return List.of();
        }
        Map<String, Map<String, Object>> bindingByPartId = new LinkedHashMap<>();
        for (Map<String, Object> binding : partBindings(metadata.path("part_bindings"))) {
            Object upstreamPartId = binding.get("upstreamPartId");
            if (upstreamPartId instanceof String partId && !partId.isBlank()) {
                bindingByPartId.put(partId, binding);
            }
        }

        List<CanonicalResourceArtifact> artifacts = new ArrayList<>();
        for (JsonNode partNode : metadata.path("parts")) {
            String partId = partNode.asText(null);
            if (partId == null || partId.isBlank()) {
                continue;
            }
            Map<String, Object> attributes = new LinkedHashMap<>();
            Map<String, Object> binding = bindingByPartId.get(partId);
            if (binding != null) {
                attributes.putAll(binding);
            }
            artifacts.add(new CanonicalResourceArtifact(
                    "upload_part",
                    partId,
                    binding != null && binding.get("filename") instanceof String filename ? filename : partId,
                    attributes
            ));
        }
        return List.copyOf(artifacts);
    }

    private List<CanonicalResourceArtifact> fileBindingArtifacts(
            JsonNode requestPayload,
            JsonNode responsePayload,
            Long credentialId) {
        if (credentialId == null) {
            return List.of();
        }
        List<CanonicalResourceArtifact> artifacts = new ArrayList<>();
        collectFileArtifacts(artifacts, credentialId, requestPayload, "input_file_id");
        collectFileArtifacts(artifacts, credentialId, responsePayload, "output_file_id");
        collectFileArtifacts(artifacts, credentialId, responsePayload, "error_file_id");
        collectFileArtifacts(artifacts, credentialId, responsePayload, "result_files");
        return List.copyOf(artifacts);
    }

    private void collectFileArtifacts(
            List<CanonicalResourceArtifact> artifacts,
            Long credentialId,
            JsonNode payload,
            String fieldName) {
        if (payload == null || payload.isMissingNode()) {
            return;
        }
        JsonNode valueNode = payload.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return;
        }
        if (valueNode.isArray()) {
            int sourceIndex = 0;
            for (JsonNode item : valueNode) {
                String externalFileId = extractExternalFileId(item);
                if (externalFileId != null) {
                    collectResolvedFileArtifact(artifacts, credentialId, fieldName, externalFileId, sourceIndex);
                }
                sourceIndex++;
            }
            return;
        }
        String externalFileId = extractExternalFileId(valueNode);
        if (externalFileId != null) {
            collectResolvedFileArtifact(artifacts, credentialId, fieldName, externalFileId, null);
        }
    }

    private void collectResolvedFileArtifact(
            List<CanonicalResourceArtifact> artifacts,
            Long credentialId,
            String fieldName,
            String externalFileId,
            Integer sourceIndex) {
        List<GatewayFileBindingEntity> bindings = gatewayFileBindingRepository
                .findAllByCredentialIdAndExternalFileIdOrderByCreatedAtDesc(credentialId, externalFileId);
        if (bindings.isEmpty()) {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("fieldName", fieldName);
            attributes.put("externalFileId", externalFileId);
            if (sourceIndex != null) {
                attributes.put("sourceIndex", sourceIndex);
            }
            artifacts.add(new CanonicalResourceArtifact(
                    "file_ref",
                    externalFileId,
                    externalFileId,
                    attributes
            ));
            return;
        }

        GatewayFileBindingEntity binding = bindings.get(0);
        GatewayFileEntity gatewayFile = gatewayFileRepository.findById(binding.getGatewayFileId()).orElse(null);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("fieldName", fieldName);
        attributes.put("externalFileId", binding.getExternalFileId());
        attributes.put("credentialId", binding.getCredentialId());
        attributes.put("bindingStatus", binding.getStatus());
        if (sourceIndex != null) {
            attributes.put("sourceIndex", sourceIndex);
        }
        if (gatewayFile != null) {
            attributes.put("gatewayFileKey", gatewayFile.getFileKey());
            attributes.put("mimeType", gatewayFile.getMimeType());
            attributes.put("sizeBytes", gatewayFile.getSizeBytes());
        }
        artifacts.add(new CanonicalResourceArtifact(
                "gateway_file_binding",
                gatewayFile == null ? binding.getExternalFileId() : gatewayFile.getFileKey(),
                gatewayFile == null ? defaultDisplayName(binding) : gatewayFile.getFilename(),
                attributes
        ));
    }

    private String extractExternalFileId(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            String value = node.asText(null);
            return value == null || value.isBlank() ? null : value;
        }
        String id = text(node, "id");
        if (id != null) {
            return id;
        }
        return firstText(node, "file_id", "external_file_id");
    }

    private List<Map<String, Object>> partBindings(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> bindings = new ArrayList<>();
        for (JsonNode item : node) {
            Map<String, Object> binding = new LinkedHashMap<>();
            if (text(item, "upstream_part_id") != null) {
                binding.put("upstreamPartId", text(item, "upstream_part_id"));
            }
            if (text(item, "filename") != null) {
                binding.put("filename", text(item, "filename"));
            }
            if (!item.path("synced_at").isMissingNode() && !item.path("synced_at").isNull()) {
                Instant syncedAt = epochSeconds(item.path("synced_at"));
                if (syncedAt != null) {
                    binding.put("syncedAt", syncedAt);
                }
            }
            bindings.add(Map.copyOf(binding));
        }
        return List.copyOf(bindings);
    }

    private List<String> textList(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText(null);
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private String normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        String normalized = rawStatus.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "new", "created" -> "created";
            case "queued", "pending" -> "queued";
            case "in_progress", "processing", "running", "validating" -> "in_progress";
            case "completed", "succeeded", "success", "done", "processed" -> "completed";
            case "cancelled", "canceled" -> "cancelled";
            case "failed", "error", "errored" -> "failed";
            case "deleted" -> "deleted";
            default -> normalized;
        };
    }

    private boolean isTerminal(String status) {
        return "completed".equals(status)
                || "cancelled".equals(status)
                || "failed".equals(status)
                || "deleted".equals(status);
    }

    private JsonNode readJson(String json) {
        try {
            return json == null || json.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(json);
        } catch (JacksonException exception) {
            throw new IllegalStateException("解析异步资源 JSON 失败。", exception);
        }
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text;
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

    private String nestedText(JsonNode node, String parentField, String childField) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode parent = node.path(parentField);
        if (parent.isMissingNode() || parent.isNull()) {
            return null;
        }
        return text(parent, childField);
    }

    private Long longValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asLong();
    }

    private Instant epochSeconds(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return Instant.ofEpochSecond(node.asLong());
    }

    private String defaultDisplayName(GatewayFileBindingEntity binding) {
        return binding.getExternalFilename() == null || binding.getExternalFilename().isBlank()
                ? binding.getExternalFileId()
                : binding.getExternalFilename();
    }
}
