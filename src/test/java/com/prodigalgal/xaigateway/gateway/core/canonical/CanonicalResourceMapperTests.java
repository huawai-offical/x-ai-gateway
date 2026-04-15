package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.SupportStatus;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanonicalResourceMapperTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultCanonicalResourceMapper mapper = new DefaultCanonicalResourceMapper();

    @Test
    void shouldMapJsonResourceFamiliesToCanonicalSummary() throws Exception {
        List<JsonCase> cases = List.of(
                new JsonCase(TranslationOperation.EMBEDDING_CREATE, "{\"object\":\"list\",\"data\":[{\"embedding\":[0.1]}]}", "list", "list", "completed", null),
                new JsonCase(TranslationOperation.AUDIO_TRANSCRIPTION, "{\"text\":\"hello\",\"object\":\"transcription\"}", "object", "transcription", "completed", null),
                new JsonCase(TranslationOperation.AUDIO_TRANSLATION, "{\"text\":\"hello\",\"object\":\"translation\",\"status\":\"completed\"}", "object", "translation", "completed", null),
                new JsonCase(TranslationOperation.IMAGE_GENERATION, "{\"created\":123,\"data\":[{\"url\":\"https://example.com/a.png\"}]}", "list", "image.list", "completed", null),
                new JsonCase(TranslationOperation.MODERATION_CREATE, "{\"id\":\"modr_1\",\"object\":\"moderation\",\"results\":[]}", "object", "moderation", "completed", "modr_1"),
                new JsonCase(TranslationOperation.FILE_CREATE, "{\"id\":\"file_1\",\"object\":\"file\",\"status\":\"processed\"}", "object", "file", "processed", "file_1"),
                new JsonCase(TranslationOperation.FILE_GET, "{\"id\":\"file_1\",\"object\":\"file\"}", "object", "file", "completed", "file_1"),
                new JsonCase(TranslationOperation.FILE_LIST, "{\"object\":\"list\",\"data\":[{\"id\":\"file_1\"}]}", "list", "list", "completed", null),
                new JsonCase(TranslationOperation.FILE_DELETE, "{\"id\":\"file_1\",\"object\":\"file\",\"deleted\":true}", "object", "file", "completed", "file_1"),
                new JsonCase(TranslationOperation.UPLOAD_CREATE, "{\"id\":\"upload_1\",\"object\":\"upload\",\"status\":\"created\"}", "object", "upload", "created", "upload_1"),
                new JsonCase(TranslationOperation.UPLOAD_GET, "{\"id\":\"upload_1\",\"object\":\"upload\",\"status\":\"in_progress\"}", "object", "upload", "in_progress", "upload_1"),
                new JsonCase(TranslationOperation.UPLOAD_PART_ADD, "{\"id\":\"upload_1\",\"object\":\"upload\",\"status\":\"in_progress\"}", "object", "upload", "in_progress", "upload_1"),
                new JsonCase(TranslationOperation.UPLOAD_COMPLETE, "{\"id\":\"upload_1\",\"object\":\"upload\",\"status\":\"completed\"}", "object", "upload", "completed", "upload_1"),
                new JsonCase(TranslationOperation.UPLOAD_CANCEL, "{\"id\":\"upload_1\",\"object\":\"upload\",\"status\":\"cancelled\"}", "object", "upload", "cancelled", "upload_1"),
                new JsonCase(TranslationOperation.BATCH_CREATE, "{\"id\":\"batch_1\",\"object\":\"batch\",\"status\":\"queued\"}", "object", "batch", "queued", "batch_1"),
                new JsonCase(TranslationOperation.BATCH_GET, "{\"id\":\"batch_1\",\"object\":\"batch\",\"status\":\"in_progress\"}", "object", "batch", "in_progress", "batch_1"),
                new JsonCase(TranslationOperation.BATCH_CANCEL, "{\"id\":\"batch_1\",\"object\":\"batch\",\"status\":\"cancelled\"}", "object", "batch", "cancelled", "batch_1"),
                new JsonCase(TranslationOperation.TUNING_CREATE, "{\"id\":\"ftjob_1\",\"object\":\"fine_tuning.job\",\"status\":\"created\"}", "object", "fine_tuning.job", "created", "ftjob_1"),
                new JsonCase(TranslationOperation.TUNING_GET, "{\"id\":\"ftjob_1\",\"object\":\"fine_tuning.job\",\"status\":\"running\"}", "object", "fine_tuning.job", "in_progress", "ftjob_1"),
                new JsonCase(TranslationOperation.TUNING_CANCEL, "{\"id\":\"ftjob_1\",\"object\":\"fine_tuning.job\",\"status\":\"cancelled\"}", "object", "fine_tuning.job", "cancelled", "ftjob_1"),
                new JsonCase(TranslationOperation.REALTIME_CLIENT_SECRET_CREATE, "{\"id\":\"rt_1\",\"object\":\"realtime.client_secret\",\"status\":\"completed\"}", "object", "realtime.client_secret", "completed", "rt_1")
        );

        for (JsonCase item : cases) {
            CanonicalResourceResponse response = mapper.mapJson(
                    request(item.operation()),
                    plan(item.operation(), SupportStatus.NATIVE, InteropCapabilityLevel.NATIVE, List.of()),
                    objectMapper.readTree(item.rawJson())
            );

            assertEquals(item.expectedResponseKind(), response.responseKind(), item.operation().name());
            assertEquals(item.expectedObjectType(), response.objectType(), item.operation().name());
            assertEquals(item.expectedStatus(), response.status(), item.operation().name());
            assertEquals(item.expectedObjectId(), response.objectId(), item.operation().name());
        }
    }

    @Test
    void shouldMapBinaryResponsesAndCarryPlanDegradations() {
        CanonicalResourceResponse audioResponse = mapper.mapBinary(
                request(TranslationOperation.AUDIO_SPEECH),
                plan(TranslationOperation.AUDIO_SPEECH, SupportStatus.NATIVE, InteropCapabilityLevel.NATIVE, List.of()),
                new byte[] {1, 2, 3},
                "audio/mpeg"
        );
        assertEquals("binary", audioResponse.responseKind());
        assertEquals("audio.speech", audioResponse.objectType());
        assertEquals("completed", audioResponse.status());
        assertEquals(3, audioResponse.binaryLength());

        CanonicalResourceResponse fileContentResponse = mapper.mapBinary(
                request(TranslationOperation.FILE_CONTENT_GET),
                plan(TranslationOperation.FILE_CONTENT_GET, SupportStatus.DEGRADED, InteropCapabilityLevel.LOSSY, List.of("content render degraded")),
                new byte[] {9, 8},
                "application/pdf"
        );
        assertEquals("file.content", fileContentResponse.objectType());
        assertEquals("file_123", fileContentResponse.objectId());
        assertEquals(1, fileContentResponse.degradations().size());
        assertEquals("blocker_reason", fileContentResponse.degradations().get(0).code());
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

    private CanonicalExecutionPlan plan(
            TranslationOperation operation,
            SupportStatus supportStatus,
            InteropCapabilityLevel degradationLevel,
            List<String> blockerReasons) {
        return new CanonicalExecutionPlan(
                true,
                CanonicalIngressProtocol.OPENAI,
                defaultPath(operation),
                defaultPath(operation),
                defaultSurface(operation),
                "model-x",
                "model-x",
                "model-x",
                resourceType(operation),
                operation,
                ExecutionKind.NATIVE,
                ExecutionBackend.NATIVE,
                supportStatus,
                null,
                List.of(ExecutionBackend.NATIVE),
                "test",
                degradationLevel,
                degradationLevel,
                degradationLevel,
                degradationLevel,
                blockerReasons,
                List.of(),
                Map.of(),
                blockerReasons,
                blockerReasons
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
            case BATCH_CREATE, BATCH_GET, BATCH_CANCEL -> TranslationResourceType.BATCH;
            case TUNING_CREATE, TUNING_GET, TUNING_CANCEL -> TranslationResourceType.TUNING;
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
            case BATCH_CREATE, BATCH_GET, BATCH_CANCEL -> "batches";
            case TUNING_CREATE, TUNING_GET, TUNING_CANCEL -> "fine_tuning";
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
            case BATCH_CREATE -> "/v1/batches";
            case BATCH_GET -> "/v1/batches/batch_1";
            case BATCH_CANCEL -> "/v1/batches/batch_1/cancel";
            case TUNING_CREATE -> "/v1/fine_tuning/jobs";
            case TUNING_GET -> "/v1/fine_tuning/jobs/ftjob_1";
            case TUNING_CANCEL -> "/v1/fine_tuning/jobs/ftjob_1/cancel";
            case REALTIME_CLIENT_SECRET_CREATE -> "/v1/realtime/client_secrets";
            default -> "/unknown";
        };
    }

    private Map<String, String> defaultPathParams(TranslationOperation operation) {
        return switch (operation) {
            case FILE_GET, FILE_DELETE, FILE_CONTENT_GET -> Map.of("fileId", "file_123");
            case UPLOAD_GET, UPLOAD_PART_ADD, UPLOAD_COMPLETE, UPLOAD_CANCEL -> Map.of("uploadId", "upload_1");
            case BATCH_GET, BATCH_CANCEL -> Map.of("batchId", "batch_1");
            case TUNING_GET, TUNING_CANCEL -> Map.of("jobId", "ftjob_1");
            default -> Map.of();
        };
    }

    private record JsonCase(
            TranslationOperation operation,
            String rawJson,
            String expectedResponseKind,
            String expectedObjectType,
            String expectedStatus,
            String expectedObjectId
    ) {
    }
}
