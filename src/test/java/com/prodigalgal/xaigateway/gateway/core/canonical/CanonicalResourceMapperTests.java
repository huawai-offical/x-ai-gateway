package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.SupportStatus;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanonicalResourceMapperTests {

    private static final List<String> FAMILIES = List.of(
            "embeddings",
            "audio",
            "images",
            "moderations",
            "files",
            "uploads",
            "realtime"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultCanonicalResourceMapper mapper = new DefaultCanonicalResourceMapper();

    @Test
    void shouldMapFixtureCasesToCanonicalSummary() throws Exception {
        for (FixtureCase item : loadFixtures()) {
            CanonicalResourceResponse response = "binary".equals(item.mode())
                    ? mapper.mapBinary(
                    request(item.operation()),
                    plan(item),
                    item.binaryText() == null ? new byte[0] : item.binaryText().getBytes(StandardCharsets.UTF_8),
                    item.contentType())
                    : mapper.mapJson(
                    request(item.operation()),
                    plan(item),
                    item.rawJson());

            assertEquals(item.expectedResponseKind(), response.responseKind(), item.operation().name());
            assertEquals(item.expectedObjectType(), response.objectType(), item.operation().name());
            assertEquals(item.expectedStatus(), response.status(), item.operation().name());
            assertEquals(item.expectedObjectId(), response.objectId(), item.operation().name());
            assertEquals(item.expectedEventCount(), response.events().size(), item.operation().name());
            assertEquals(item.expectedDegradationCount(), response.degradations().size(), item.operation().name());
            if (item.expectedDegradationCode() != null && !response.degradations().isEmpty()) {
                assertEquals(item.expectedDegradationCode(), response.degradations().get(0).code(), item.operation().name());
            }
            if ("binary".equals(item.mode())) {
                assertEquals(item.binaryText().getBytes(StandardCharsets.UTF_8).length, response.binaryLength(), item.operation().name());
            }
        }
    }

    private List<FixtureCase> loadFixtures() throws Exception {
        List<FixtureCase> cases = new ArrayList<>();
        for (String family : FAMILIES) {
            try (InputStream stream = getClass().getClassLoader()
                    .getResourceAsStream("fixtures/canonical-resource/" + family + "/cases.json")) {
                if (stream == null) {
                    throw new IllegalStateException("缺少 fixture: " + family);
                }
                JsonNode root = objectMapper.readTree(stream);
                for (JsonNode item : root) {
                    cases.add(new FixtureCase(
                            TranslationOperation.valueOf(item.path("operation").asText()),
                            item.path("mode").asText("json"),
                            item.path("rawJson"),
                            item.path("binaryText").isMissingNode() ? null : item.path("binaryText").asText(),
                            item.path("contentType").isMissingNode() ? null : item.path("contentType").asText(),
                            SupportStatus.valueOf(item.path("supportStatus").asText("NATIVE")),
                            InteropCapabilityLevel.valueOf(item.path("degradationLevel").asText("NATIVE")),
                            readBlockerReasons(item.path("blockerReasons")),
                            item.path("expectedResponseKind").asText(),
                            item.path("expectedObjectType").asText(),
                            item.path("expectedStatus").asText(),
                            item.path("expectedObjectId").isMissingNode() || item.path("expectedObjectId").isNull()
                                    ? null
                                    : item.path("expectedObjectId").asText(),
                            item.path("expectedEventCount").asInt(),
                            item.path("expectedDegradationCount").asInt(0),
                            item.path("expectedDegradationCode").isMissingNode() || item.path("expectedDegradationCode").isNull()
                                    ? null
                                    : item.path("expectedDegradationCode").asText()
                    ));
                }
            }
        }
        return cases;
    }

    private List<String> readBlockerReasons(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isNull() && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        }
        return List.copyOf(values);
    }

    private CanonicalResourceRequest request(TranslationOperation operation) {
        return new CanonicalResourceRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "GET",
                defaultPath(operation),
                defaultPath(operation),
                defaultPathParams(operation),
                "model-x",
                resourceType(operation),
                operation,
                null,
                Map.of(),
                List.of(),
                operation == TranslationOperation.AUDIO_SPEECH || operation == TranslationOperation.FILE_CONTENT_GET,
                false
        );
    }

    private CanonicalExecutionPlan plan(FixtureCase item) {
        return new CanonicalExecutionPlan(
                true,
                CanonicalIngressProtocol.OPENAI,
                defaultPath(item.operation()),
                defaultPath(item.operation()),
                defaultSurface(item.operation()),
                "model-x",
                "model-x",
                "model-x",
                resourceType(item.operation()),
                item.operation(),
                ExecutionKind.NATIVE,
                ExecutionBackend.NATIVE,
                item.supportStatus(),
                null,
                List.of(ExecutionBackend.NATIVE),
                "test",
                item.degradationLevel(),
                item.degradationLevel(),
                item.degradationLevel(),
                item.degradationLevel(),
                item.blockerReasons(),
                List.of(),
                Map.of(),
                item.blockerReasons(),
                item.blockerReasons()
        );
    }

    private TranslationResourceType resourceType(TranslationOperation operation) {
        return switch (operation) {
            case EMBEDDING_CREATE -> TranslationResourceType.EMBEDDING;
            case AUDIO_TRANSCRIPTION, AUDIO_TRANSLATION, AUDIO_SPEECH -> TranslationResourceType.AUDIO;
            case IMAGE_GENERATION, IMAGE_EDIT, IMAGE_VARIATION -> TranslationResourceType.IMAGE;
            case MODERATION_CREATE -> TranslationResourceType.MODERATION;
            case FILE_CREATE, FILE_LIST, FILE_GET, FILE_CONTENT_GET, FILE_DELETE -> TranslationResourceType.FILE;
            case UPLOAD_CREATE, UPLOAD_GET, UPLOAD_PART_ADD, UPLOAD_COMPLETE, UPLOAD_CANCEL -> TranslationResourceType.UPLOAD;
            case REALTIME_CLIENT_SECRET_CREATE -> TranslationResourceType.REALTIME;
            default -> TranslationResourceType.UNKNOWN;
        };
    }

    private String defaultSurface(TranslationOperation operation) {
        return switch (operation) {
            case EMBEDDING_CREATE -> "embeddings";
            case AUDIO_TRANSCRIPTION, AUDIO_TRANSLATION, AUDIO_SPEECH -> "audio";
            case IMAGE_GENERATION, IMAGE_EDIT, IMAGE_VARIATION -> "images";
            case MODERATION_CREATE -> "moderations";
            case FILE_CREATE, FILE_LIST, FILE_GET, FILE_CONTENT_GET, FILE_DELETE -> "files";
            case UPLOAD_CREATE, UPLOAD_GET, UPLOAD_PART_ADD, UPLOAD_COMPLETE, UPLOAD_CANCEL -> "uploads";
            case REALTIME_CLIENT_SECRET_CREATE -> "realtime";
            default -> "unknown";
        };
    }

    private String defaultPath(TranslationOperation operation) {
        return switch (operation) {
            case EMBEDDING_CREATE -> "/v1/embeddings";
            case AUDIO_TRANSCRIPTION -> "/v1/audio/transcriptions";
            case AUDIO_TRANSLATION -> "/v1/audio/translations";
            case AUDIO_SPEECH -> "/v1/audio/speech";
            case IMAGE_GENERATION -> "/v1/images/generations";
            case MODERATION_CREATE -> "/v1/moderations";
            case FILE_CREATE, FILE_LIST -> "/v1/files";
            case FILE_GET, FILE_DELETE -> "/v1/files/file_123";
            case FILE_CONTENT_GET -> "/v1/files/file_123/content";
            case UPLOAD_CREATE -> "/v1/uploads";
            case UPLOAD_GET -> "/v1/uploads/upload_1";
            case UPLOAD_PART_ADD -> "/v1/uploads/upload_1/parts";
            case UPLOAD_COMPLETE -> "/v1/uploads/upload_1/complete";
            case UPLOAD_CANCEL -> "/v1/uploads/upload_1/cancel";
            case REALTIME_CLIENT_SECRET_CREATE -> "/v1/realtime/client_secrets";
            default -> "/unknown";
        };
    }

    private Map<String, String> defaultPathParams(TranslationOperation operation) {
        return switch (operation) {
            case FILE_GET, FILE_DELETE, FILE_CONTENT_GET -> Map.of("fileId", "file_123");
            case UPLOAD_GET, UPLOAD_PART_ADD, UPLOAD_COMPLETE, UPLOAD_CANCEL -> Map.of("uploadId", "upload_1");
            default -> Map.of();
        };
    }

    private record FixtureCase(
            TranslationOperation operation,
            String mode,
            JsonNode rawJson,
            String binaryText,
            String contentType,
            SupportStatus supportStatus,
            InteropCapabilityLevel degradationLevel,
            List<String> blockerReasons,
            String expectedResponseKind,
            String expectedObjectType,
            String expectedStatus,
            String expectedObjectId,
            int expectedEventCount,
            int expectedDegradationCount,
            String expectedDegradationCode
    ) {
    }
}
