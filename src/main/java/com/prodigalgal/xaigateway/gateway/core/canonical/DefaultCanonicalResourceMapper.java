package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.SupportStatus;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class DefaultCanonicalResourceMapper implements CanonicalResourceMapper {

    @Override
    public CanonicalResourceResponse mapJson(
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            JsonNode rawBody) {
        TranslationOperation operation = request == null ? TranslationOperation.UNKNOWN : request.operation();
        String responseKind = resolveJsonResponseKind(operation, rawBody);
        String objectType = resolveObjectType(operation, rawBody, responseKind);
        String objectId = textValue(rawBody, "id");
        String status = normalizeStatus(textValue(rawBody, "status"), operation, rawBody, false);
        List<CanonicalResourceEvent> events = buildEvents(objectType, objectId, status);
        List<CanonicalResourceDegradation> degradations = buildDegradations(plan);
        Map<String, Object> metadata = buildJsonMetadata(rawBody, responseKind);
        return new CanonicalResourceResponse(
                request == null ? null : request.resourceType(),
                operation,
                responseKind,
                objectType,
                objectId,
                status,
                events,
                degradations,
                rawBody,
                null,
                metadata
        );
    }

    @Override
    public CanonicalResourceResponse mapBinary(
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            byte[] rawBody,
            String contentType) {
        TranslationOperation operation = request == null ? TranslationOperation.UNKNOWN : request.operation();
        String objectType = resolveBinaryObjectType(operation);
        String objectId = request == null ? null : request.pathParams().values().stream().findFirst().orElse(null);
        String status = normalizeStatus(null, operation, null, true);
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (contentType != null && !contentType.isBlank()) {
            metadata.put("contentType", contentType);
        }
        metadata.put("binaryLength", rawBody == null ? 0 : rawBody.length);
        return new CanonicalResourceResponse(
                request == null ? null : request.resourceType(),
                operation,
                "binary",
                objectType,
                objectId,
                status,
                buildEvents(objectType, objectId, status),
                buildDegradations(plan),
                null,
                rawBody == null ? 0 : rawBody.length,
                metadata
        );
    }

    private String resolveJsonResponseKind(TranslationOperation operation, JsonNode rawBody) {
        return switch (operation == null ? TranslationOperation.UNKNOWN : operation) {
            case EMBEDDING_CREATE, IMAGE_GENERATION, IMAGE_EDIT, IMAGE_VARIATION, FILE_LIST -> "list";
            default -> rawBody != null && rawBody.path("data").isArray() ? "list" : "object";
        };
    }

    private String resolveObjectType(TranslationOperation operation, JsonNode rawBody, String responseKind) {
        String rawObject = textValue(rawBody, "object");
        if (rawObject != null && !rawObject.isBlank()) {
            return rawObject;
        }
        String defaultObjectType = switch (operation == null ? TranslationOperation.UNKNOWN : operation) {
            case EMBEDDING_CREATE -> "embedding.list";
            case AUDIO_TRANSCRIPTION -> "audio.transcription";
            case AUDIO_TRANSLATION -> "audio.translation";
            case IMAGE_GENERATION, IMAGE_EDIT, IMAGE_VARIATION -> "image.list";
            case MODERATION_CREATE -> "moderation";
            case FILE_CREATE, FILE_GET, FILE_DELETE, FILE_LIST, FILE_CONTENT_GET -> "file";
            case UPLOAD_CREATE, UPLOAD_GET, UPLOAD_PART_ADD, UPLOAD_COMPLETE, UPLOAD_CANCEL -> "upload";
            case BATCH_CREATE, BATCH_GET, BATCH_CANCEL -> "batch";
            case TUNING_CREATE, TUNING_GET, TUNING_CANCEL -> "fine_tuning.job";
            case REALTIME_CLIENT_SECRET_CREATE -> "realtime.client_secret";
            case UNKNOWN, CHAT_COMPLETION, RESPONSE_CREATE, AUDIO_SPEECH -> "unknown";
        };
        if ("list".equals(responseKind)
                && !defaultObjectType.equals("list")
                && !defaultObjectType.endsWith(".list")) {
            return defaultObjectType + ".list";
        }
        return defaultObjectType;
    }

    private String resolveBinaryObjectType(TranslationOperation operation) {
        return switch (operation == null ? TranslationOperation.UNKNOWN : operation) {
            case AUDIO_SPEECH -> "audio.speech";
            case FILE_CONTENT_GET -> "file.content";
            default -> "binary";
        };
    }

    private String normalizeStatus(
            String rawStatus,
            TranslationOperation operation,
            JsonNode rawBody,
            boolean binary) {
        if (binary) {
            return "completed";
        }
        String normalized = normalizeKnownStatus(rawStatus);
        if (normalized != null) {
            return normalized;
        }
        if (rawBody != null && rawBody.path("deleted").asBoolean(false)) {
            return "completed";
        }
        return switch (operation == null ? TranslationOperation.UNKNOWN : operation) {
            case UPLOAD_CREATE, BATCH_CREATE, TUNING_CREATE -> "created";
            case UPLOAD_GET, UPLOAD_PART_ADD, UPLOAD_COMPLETE, UPLOAD_CANCEL, BATCH_GET, BATCH_CANCEL, TUNING_GET, TUNING_CANCEL -> "in_progress";
            case AUDIO_TRANSCRIPTION, AUDIO_TRANSLATION, EMBEDDING_CREATE, IMAGE_GENERATION, IMAGE_EDIT, IMAGE_VARIATION, MODERATION_CREATE,
                    FILE_CREATE, FILE_LIST, FILE_GET, FILE_DELETE, FILE_CONTENT_GET, REALTIME_CLIENT_SECRET_CREATE -> "completed";
            default -> rawBody == null || rawBody.isMissingNode() || rawBody.isNull() ? null : "completed";
        };
    }

    private String normalizeKnownStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        String value = rawStatus.trim().toLowerCase(Locale.ROOT);
        if (value.equals("created")) {
            return "created";
        }
        if (value.equals("queued") || value.equals("pending")) {
            return "queued";
        }
        if (value.equals("in_progress") || value.equals("processing") || value.equals("running")) {
            return "in_progress";
        }
        if (value.equals("completed") || value.equals("succeeded") || value.equals("success") || value.equals("done")) {
            return "completed";
        }
        if (value.equals("cancelled") || value.equals("canceled")) {
            return "cancelled";
        }
        if (value.equals("failed") || value.equals("error") || value.equals("errored")) {
            return "failed";
        }
        return value;
    }

    private List<CanonicalResourceEvent> buildEvents(
            String objectType,
            String objectId,
            String status) {
        if (status == null || status.isBlank()) {
            return List.of();
        }
        List<CanonicalResourceEvent> events = new ArrayList<>();
        events.add(new CanonicalResourceEvent(
                "resource.status",
                objectType,
                objectId,
                lifecyclePhase(status),
                status,
                Map.of("status", status)
        ));
        if (isTerminal(status)) {
            events.add(new CanonicalResourceEvent(
                    "resource.terminal",
                    objectType,
                    objectId,
                    "terminal",
                    status,
                    Map.of("terminalStatus", status)
            ));
        }
        return List.copyOf(events);
    }

    private List<CanonicalResourceDegradation> buildDegradations(CanonicalExecutionPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<CanonicalResourceDegradation> degradations = new ArrayList<>();
        for (String blockerReason : plan.blockerReasons()) {
            degradations.add(new CanonicalResourceDegradation(
                    "blocker_reason",
                    blockerReason,
                    InteropCapabilityLevel.UNSUPPORTED,
                    true
            ));
        }
        if (degradations.isEmpty() && plan.supportStatus() == SupportStatus.DEGRADED && plan.degradationLevel() != null) {
            degradations.add(new CanonicalResourceDegradation(
                    "plan_degradation",
                    "Support status is degraded.",
                    plan.degradationLevel(),
                    false
            ));
        }
        return List.copyOf(degradations);
    }

    private Map<String, Object> buildJsonMetadata(JsonNode rawBody, String responseKind) {
        if (rawBody == null || rawBody.isMissingNode() || rawBody.isNull()) {
            return Map.of();
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if ("list".equals(responseKind)) {
            metadata.put("itemCount", rawBody.path("data").isArray() ? rawBody.path("data").size() : 0);
        }
        if (rawBody.has("deleted")) {
            metadata.put("deleted", rawBody.path("deleted").asBoolean(false));
        }
        return Map.copyOf(metadata);
    }

    private String textValue(JsonNode rawBody, String fieldName) {
        if (rawBody == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        JsonNode value = rawBody.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    private String lifecyclePhase(String status) {
        if (status == null || status.isBlank()) {
            return "unknown";
        }
        if (status.equals("created")) {
            return "created";
        }
        if (status.equals("queued")) {
            return "queued";
        }
        if (status.equals("in_progress")) {
            return "active";
        }
        if (isTerminal(status)) {
            return "terminal";
        }
        return "active";
    }

    private boolean isTerminal(String status) {
        return "completed".equals(status) || "cancelled".equals(status) || "failed".equals(status);
    }
}
