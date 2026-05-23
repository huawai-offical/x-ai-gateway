package com.prodigalgal.xaigateway.gateway.core.execution;

import com.google.genai.Client;
import com.google.genai.types.EditImageConfig;
import com.google.genai.types.EditImageResponse;
import com.google.genai.types.EditMode;
import com.google.genai.types.GenerateImagesConfig;
import com.google.genai.types.GenerateImagesResponse;
import com.google.genai.types.GeneratedImage;
import com.google.genai.types.Image;
import com.google.genai.types.MaskReferenceConfig;
import com.google.genai.types.MaskReferenceImage;
import com.google.genai.types.MaskReferenceMode;
import com.google.genai.types.RawReferenceImage;
import com.google.genai.types.ReferenceImage;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class GeminiImagesGatewayResourceExecutor implements GatewayResourceExecutor {

    private final GeminiChatModelFactory geminiChatModelFactory;
    private final GatewayFileService gatewayFileService;
    private final ObjectMapper objectMapper;

    public GeminiImagesGatewayResourceExecutor(
            GeminiChatModelFactory geminiChatModelFactory,
            GatewayFileService gatewayFileService,
            ObjectMapper objectMapper) {
        this.geminiChatModelFactory = geminiChatModelFactory;
        this.gatewayFileService = gatewayFileService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExecutionBackend backend() {
        return ExecutionBackend.NATIVE;
    }

    @Override
    public boolean supports(CanonicalResourceRequest request, CatalogCandidateView candidate) {
        return GeminiGatewayResourceSupport.supportsGoogleGenAiCandidate(
                request,
                candidate,
                "/v1/images/generations",
                "/v1/images/edits"
        );
    }

    @Override
    public ResponseEntity<JsonNode> executeJson(
            GatewayResourceExecutionContext context,
            JsonNode requestBody,
            String defaultModel) {
        if (context.request().operation() != TranslationOperation.IMAGE_GENERATION) {
            throw new IllegalArgumentException("Gemini images executor 当前仅支持 /v1/images/generations。");
        }
        ObjectNode payload = requireObjectPayload(requestBody, defaultModel);
        String prompt = requiredText(payload, "prompt", "images.generation 请求缺少 prompt。");
        validateResponseFormat(payload.path("response_format").asText(null));
        GenerateImagesConfig.Builder configBuilder = GenerateImagesConfig.builder()
                .outputMimeType("image/png");
        Integer numberOfImages = parsePositiveInteger(payload.path("n").asText(null));
        if (numberOfImages != null) {
            configBuilder.numberOfImages(numberOfImages);
        }
        GenerateImagesResponse response = generateImages(context, prompt, configBuilder.build());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(toOpenAiResponse(context, response));
    }

    @Override
    public Mono<ResponseEntity<JsonNode>> executeMultipart(
            GatewayResourceExecutionContext context,
            String requestedModel,
            Map<String, String> formFields,
            Map<String, FilePart> files) {
        if (context.request().operation() != TranslationOperation.IMAGE_EDIT) {
            return Mono.error(new IllegalArgumentException("Gemini images executor 当前 multipart 仅支持 /v1/images/edits。"));
        }
        validateResponseFormat(valueOf(formFields, "response_format"));
        String prompt = requiredText(formFields, "prompt", "images.edit 请求缺少 prompt。");
        return GeminiGatewayResourceSupport.resolveBinaryFile(context, files, gatewayFileService, "image")
                .flatMap(image -> {
                    if (hasMultipartOrGatewayFile(context, files, "mask")) {
                        return GeminiGatewayResourceSupport.resolveBinaryFile(context, files, gatewayFileService, "mask")
                                .map(mask -> executeEdit(context, prompt, formFields, image, mask));
                    }
                    return Mono.just(executeEdit(context, prompt, formFields, image, null));
                });
    }

    GenerateImagesResponse generateImages(
            GatewayResourceExecutionContext context,
            String prompt,
            GenerateImagesConfig config) {
        try (Client client = GeminiGatewayResourceSupport.createClient(geminiChatModelFactory, context)) {
            return client.models.generateImages(
                    context.selectionResult().resolvedModelKey(),
                    prompt,
                    config
            );
        }
    }

    EditImageResponse editImage(
            GatewayResourceExecutionContext context,
            String prompt,
            List<ReferenceImage> referenceImages,
            EditImageConfig config) {
        try (Client client = GeminiGatewayResourceSupport.createClient(geminiChatModelFactory, context)) {
            return client.models.editImage(
                    context.selectionResult().resolvedModelKey(),
                    prompt,
                    referenceImages,
                    config
            );
        }
    }

    ResponseEntity<JsonNode> executeEdit(
            GatewayResourceExecutionContext context,
            String prompt,
            Map<String, String> formFields,
            GeminiGatewayResourceSupport.ResolvedBinaryFile image,
            GeminiGatewayResourceSupport.ResolvedBinaryFile mask) {
        EditImageResponse response = editImage(
                context,
                prompt,
                referenceImages(image, mask),
                buildEditConfig(formFields, mask != null)
        );
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(toOpenAiResponse(context, response.generatedImages().orElse(List.of()), "Gemini image edit 未返回可用图片。"));
    }

    private ObjectNode toOpenAiResponse(GatewayResourceExecutionContext context, GenerateImagesResponse response) {
        return toOpenAiResponse(context, response.generatedImages().orElse(List.of()), "Gemini image generation 未返回可用图片。");
    }

    private ObjectNode toOpenAiResponse(
            GatewayResourceExecutionContext context,
            List<GeneratedImage> generatedImages,
            String emptyMessage) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("created", Instant.now().getEpochSecond());
        body.put("model", GeminiGatewayResourceSupport.responseModel(context));
        ArrayNode data = body.putArray("data");
        for (GeneratedImage generatedImage : generatedImages) {
            Image image = generatedImage.image().orElse(null);
            if (image == null || image.imageBytes().isEmpty()) {
                continue;
            }
            data.addObject()
                    .put("b64_json", Base64.getEncoder().encodeToString(image.imageBytes().get()));
        }
        if (data.isEmpty()) {
            throw new IllegalStateException(emptyMessage);
        }
        return body;
    }

    private EditImageConfig buildEditConfig(Map<String, String> formFields, boolean hasMask) {
        EditImageConfig.Builder configBuilder = EditImageConfig.builder()
                .outputMimeType(resolveOutputMimeType(formFields))
                .editMode(hasMask
                        ? EditMode.Known.EDIT_MODE_INPAINT_INSERTION
                        : EditMode.Known.EDIT_MODE_DEFAULT);
        Integer numberOfImages = parsePositiveInteger(valueOf(formFields, "n"));
        if (numberOfImages != null) {
            configBuilder.numberOfImages(numberOfImages);
        }
        Integer compression = parsePositiveInteger(valueOf(formFields, "output_compression"));
        if (compression != null) {
            configBuilder.outputCompressionQuality(compression);
        }
        return configBuilder.build();
    }

    private List<ReferenceImage> referenceImages(
            GeminiGatewayResourceSupport.ResolvedBinaryFile image,
            GeminiGatewayResourceSupport.ResolvedBinaryFile mask) {
        RawReferenceImage rawReference = RawReferenceImage.builder()
                .referenceId(1)
                .referenceImage(toImage(image))
                .build();
        if (mask == null) {
            return List.of(rawReference);
        }
        MaskReferenceImage maskReference = MaskReferenceImage.builder()
                .referenceId(2)
                .referenceImage(toImage(mask))
                .config(MaskReferenceConfig.builder()
                        .maskMode(MaskReferenceMode.Known.MASK_MODE_USER_PROVIDED)
                        .build())
                .build();
        return List.of(rawReference, maskReference);
    }

    private Image toImage(GeminiGatewayResourceSupport.ResolvedBinaryFile file) {
        return Image.builder()
                .imageBytes(file.bytes())
                .mimeType(file.mimeType())
                .build();
    }

    private void validateResponseFormat(String responseFormat) {
        String normalized = trimToNull(responseFormat);
        if (normalized == null || "b64_json".equalsIgnoreCase(normalized)) {
            return;
        }
        throw new IllegalArgumentException("Gemini image generation 当前仅返回 b64_json。");
    }

    private String resolveOutputMimeType(Map<String, String> formFields) {
        String outputFormat = trimToNull(valueOf(formFields, "output_format"));
        if (outputFormat == null) {
            return "image/png";
        }
        return switch (outputFormat.toLowerCase(Locale.ROOT)) {
            case "png" -> "image/png";
            case "jpeg", "jpg" -> "image/jpeg";
            case "webp" -> "image/webp";
            default -> throw new IllegalArgumentException("Gemini image edit 当前仅支持 output_format 为 png、jpeg 或 webp。");
        };
    }

    private ObjectNode requireObjectPayload(JsonNode requestBody, String defaultModel) {
        if (requestBody == null || !requestBody.isObject()) {
            throw new IllegalArgumentException("请求体必须是 JSON object。");
        }
        ObjectNode payload = (ObjectNode) requestBody;
        if (!payload.hasNonNull("model") && defaultModel != null && !defaultModel.isBlank()) {
            payload.put("model", defaultModel);
        }
        return payload;
    }

    private String requiredText(Map<String, String> formFields, String fieldName, String message) {
        String value = trimToNull(valueOf(formFields, fieldName));
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String requiredText(ObjectNode payload, String fieldName, String message) {
        String value = trimToNull(payload.path(fieldName).asText(null));
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private Integer parsePositiveInteger(String rawValue) {
        String value = trimToNull(rawValue);
        if (value == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException("n 必须大于 0。");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("n 必须是整数。", exception);
        }
    }

    private boolean hasMultipartOrGatewayFile(
            GatewayResourceExecutionContext context,
            Map<String, FilePart> files,
            String fieldName) {
        if (files != null && files.containsKey(fieldName)) {
            return true;
        }
        if (context == null || context.request() == null || context.request().fileRefs() == null) {
            return false;
        }
        return context.request().fileRefs().stream().anyMatch(fileRef -> fieldName.equals(fileRef.fieldName()));
    }

    private String valueOf(Map<String, String> formFields, String fieldName) {
        return formFields == null ? null : formFields.get(fieldName);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
