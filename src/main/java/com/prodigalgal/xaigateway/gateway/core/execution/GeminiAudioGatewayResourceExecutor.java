package com.prodigalgal.xaigateway.gateway.core.execution;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.PrebuiltVoiceConfig;
import com.google.genai.types.SpeechConfig;
import com.google.genai.types.VoiceConfig;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class GeminiAudioGatewayResourceExecutor implements GatewayResourceExecutor {

    private static final Set<String> SUPPORTED_PATHS = Set.of(
            "/v1/audio/transcriptions",
            "/v1/audio/speech"
    );

    private static final Map<String, String> SUPPORTED_VOICES = Map.ofEntries(
            Map.entry("zephyr", "Zephyr"),
            Map.entry("puck", "Puck"),
            Map.entry("charon", "Charon"),
            Map.entry("kore", "Kore"),
            Map.entry("fenrir", "Fenrir"),
            Map.entry("leda", "Leda"),
            Map.entry("orus", "Orus"),
            Map.entry("aoede", "Aoede"),
            Map.entry("callirrhoe", "Callirrhoe"),
            Map.entry("autonoe", "Autonoe"),
            Map.entry("enceladus", "Enceladus"),
            Map.entry("iapetus", "Iapetus"),
            Map.entry("umbriel", "Umbriel"),
            Map.entry("algieba", "Algieba"),
            Map.entry("despina", "Despina"),
            Map.entry("erinome", "Erinome"),
            Map.entry("algenib", "Algenib"),
            Map.entry("rasalgethi", "Rasalgethi"),
            Map.entry("laomedeia", "Laomedeia"),
            Map.entry("achernar", "Achernar"),
            Map.entry("alnilam", "Alnilam"),
            Map.entry("schedar", "Schedar"),
            Map.entry("gacrux", "Gacrux"),
            Map.entry("pulcherrima", "Pulcherrima"),
            Map.entry("achird", "Achird"),
            Map.entry("zubenelgenubi", "Zubenelgenubi"),
            Map.entry("vindemiatrix", "Vindemiatrix"),
            Map.entry("sadachbia", "Sadachbia"),
            Map.entry("sadaltager", "Sadaltager"),
            Map.entry("sulafat", "Sulafat"),
            Map.entry("alloy", "Kore")
    );

    private final GeminiChatModelFactory geminiChatModelFactory;
    private final GatewayFileService gatewayFileService;
    private final ObjectMapper objectMapper;

    public GeminiAudioGatewayResourceExecutor(
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
                SUPPORTED_PATHS.toArray(String[]::new)
        );
    }

    @Override
    public Mono<ResponseEntity<JsonNode>> executeMultipart(
            GatewayResourceExecutionContext context,
            String requestedModel,
            Map<String, String> formFields,
            Map<String, FilePart> files) {
        TranslationOperation operation = context.request().operation();
        if (operation != TranslationOperation.AUDIO_TRANSCRIPTION) {
            return Mono.error(new IllegalArgumentException("当前 Gemini audio executor 仅支持 transcriptions multipart 请求。"));
        }
        validateResponseFormat(formFields == null ? null : formFields.get("response_format"));
        return GeminiGatewayResourceSupport.resolveBinaryFile(context, files, gatewayFileService, "file")
                .map(file -> executeTextAudio(context, operation, formFields, file));
    }

    @Override
    public ResponseEntity<byte[]> executeBinary(
            GatewayResourceExecutionContext context,
            JsonNode requestBody,
            String defaultModel) {
        if (context.request().operation() != TranslationOperation.AUDIO_SPEECH) {
            throw new IllegalArgumentException("当前 Gemini audio executor 仅支持 /v1/audio/speech 二进制执行。");
        }
        ObjectNode payload = requireObjectPayload(requestBody, defaultModel);
        return executeSpeech(context, payload);
    }

    ResponseEntity<JsonNode> executeTextAudio(
            GatewayResourceExecutionContext context,
            TranslationOperation operation,
            Map<String, String> formFields,
            GeminiGatewayResourceSupport.ResolvedBinaryFile file) {
        String instruction = buildAudioInstruction(operation, formFields);
        GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder()
                .responseMimeType("text/plain");
        Float temperature = parseTemperature(formFields == null ? null : formFields.get("temperature"));
        if (temperature != null) {
            configBuilder.temperature(temperature);
        }
        GenerateContentResponse response = generateContent(
                context,
                GeminiGatewayResourceSupport.contentWithTextAndBinary(instruction, file),
                configBuilder.build()
        );
        String text = requireResponseText(response);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("text", text);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    ResponseEntity<byte[]> executeSpeech(GatewayResourceExecutionContext context, ObjectNode payload) {
        String input = requiredText(payload, "input", "audio.speech 请求缺少 input。");
        String voice = resolveVoice(payload.path("voice").asText(null));
        GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder()
                .responseModalities("AUDIO")
                .speechConfig(SpeechConfig.builder()
                        .voiceConfig(VoiceConfig.builder()
                                .prebuiltVoiceConfig(PrebuiltVoiceConfig.builder().voiceName(voice).build())
                                .build())
                        .build());
        Float temperature = parseTemperature(payload.path("temperature").asText(null));
        if (temperature != null) {
            configBuilder.temperature(temperature);
        }
        GenerateContentResponse response = generateContent(
                context,
                Content.fromParts(Part.fromText(input)),
                configBuilder.build()
        );
        Part audioPart = response.parts().stream()
                .filter(part -> part.inlineData().isPresent())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Gemini TTS 未返回音频数据。"));
        byte[] bytes = audioPart.inlineData()
                .map(blob -> blob.data().orElse(null))
                .orElseThrow(() -> new IllegalStateException("Gemini TTS 音频数据为空。"));
        String mimeType = audioPart.inlineData()
                .flatMap(blob -> blob.mimeType())
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .body(bytes);
    }

    GenerateContentResponse generateContent(
            GatewayResourceExecutionContext context,
            Content content,
            GenerateContentConfig config) {
        try (Client client = GeminiGatewayResourceSupport.createClient(geminiChatModelFactory, context)) {
            return client.models.generateContent(
                    context.selectionResult().resolvedModelKey(),
                    content,
                    config
            );
        }
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

    private String buildAudioInstruction(TranslationOperation operation, Map<String, String> formFields) {
        StringBuilder instruction = new StringBuilder();
        instruction.append("Transcribe the provided audio and return only the transcription text.");
        String language = formFields == null ? null : trimToNull(formFields.get("language"));
        if (language != null) {
            instruction.append(" The spoken language is ").append(language).append('.');
        }
        String prompt = formFields == null ? null : trimToNull(formFields.get("prompt"));
        if (prompt != null) {
            instruction.append(" Additional instructions: ").append(prompt);
        }
        return instruction.toString();
    }

    private void validateResponseFormat(String responseFormat) {
        String normalized = trimToNull(responseFormat);
        if (normalized == null || "json".equalsIgnoreCase(normalized)) {
            return;
        }
        throw new IllegalArgumentException("Gemini audio 当前仅支持 response_format 为空或 json。");
    }

    private Float parseTemperature(String rawValue) {
        String value = trimToNull(rawValue);
        if (value == null) {
            return null;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("temperature 必须是数字。", exception);
        }
    }

    private String requireResponseText(GenerateContentResponse response) {
        String text = response == null ? null : response.text();
        String normalized = trimToNull(text);
        if (normalized == null) {
            throw new IllegalStateException("Gemini audio 未返回可用文本。");
        }
        return normalized;
    }

    private String resolveVoice(String requestedVoice) {
        String normalized = trimToNull(requestedVoice);
        if (normalized == null) {
            throw new IllegalArgumentException("audio.speech 请求缺少 voice。");
        }
        String resolved = SUPPORTED_VOICES.get(normalized.toLowerCase(Locale.ROOT));
        if (resolved == null) {
            throw new IllegalArgumentException("Gemini audio.speech 仅支持 Gemini 原生 voice 名称，alloy 会映射为 Kore。");
        }
        return resolved;
    }

    private String requiredText(ObjectNode payload, String fieldName, String message) {
        String value = trimToNull(payload.path(fieldName).asText(null));
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
