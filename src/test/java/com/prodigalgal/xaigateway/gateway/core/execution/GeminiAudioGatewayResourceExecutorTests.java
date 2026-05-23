package com.prodigalgal.xaigateway.gateway.core.execution;

import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiAudioGatewayResourceExecutorTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSupportOnlyGeminiDirectAudioCandidates() {
        TestGeminiAudioExecutor executor = new TestGeminiAudioExecutor(responseWithText("hello"));

        assertTrue(executor.supports(request(TranslationOperation.AUDIO_TRANSCRIPTION, "/v1/audio/transcriptions"), candidate(UpstreamSiteKind.GEMINI_DIRECT)));
        assertTrue(executor.supports(request(TranslationOperation.AUDIO_TRANSLATION, "/v1/audio/translations"), candidate(UpstreamSiteKind.GEMINI_DIRECT)));
        assertTrue(executor.supports(request(TranslationOperation.AUDIO_SPEECH, "/v1/audio/speech"), candidate(UpstreamSiteKind.GEMINI_DIRECT)));
        assertTrue(executor.supports(request(TranslationOperation.AUDIO_TRANSCRIPTION, "/v1/audio/transcriptions"), candidate(UpstreamSiteKind.VERTEX_AI)));
        assertFalse(executor.supports(request(TranslationOperation.AUDIO_TRANSCRIPTION, "/v1/audio/unknown"), candidate(UpstreamSiteKind.GEMINI_DIRECT)));
    }

    @Test
    void shouldRejectUnsupportedResponseFormatBeforeExecutingMultipart() {
        TestGeminiAudioExecutor executor = new TestGeminiAudioExecutor(responseWithText("hello"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> executor.executeMultipart(
                        context(TranslationOperation.AUDIO_TRANSCRIPTION, "/v1/audio/transcriptions"),
                        "gemini-2.5-flash",
                        Map.of("response_format", "srt"),
                        Map.of())
        );

        assertEquals("Gemini audio 当前仅支持 response_format 为空或 json。", error.getMessage());
    }

    @Test
    void shouldReturnJsonTextForTranscriptionAndForwardTemperature() {
        TestGeminiAudioExecutor executor = new TestGeminiAudioExecutor(responseWithText("  transcript text  "));

        ResponseEntity<tools.jackson.databind.JsonNode> response = executor.executeTextAudio(
                context(TranslationOperation.AUDIO_TRANSCRIPTION, "/v1/audio/transcriptions"),
                TranslationOperation.AUDIO_TRANSCRIPTION,
                Map.of("temperature", "0.25", "language", "zh", "prompt", "保留产品名"),
                new GeminiGatewayResourceSupport.ResolvedBinaryFile("voice.wav", "audio/wav", "audio".getBytes(StandardCharsets.UTF_8))
        );

        assertEquals("transcript text", response.getBody().path("text").asText());
        assertEquals(0.25f, executor.lastConfig.temperature().orElseThrow());
        assertEquals("audio/wav", executor.lastContent.parts().orElseThrow().get(1).inlineData().orElseThrow().mimeType().orElseThrow());
        assertTrue(executor.lastContent.text().contains("spoken language is zh"));
        assertTrue(executor.lastContent.text().contains("Additional instructions: 保留产品名"));
    }

    @Test
    void shouldReturnJsonTextForTranslationAndUseTranslateInstruction() {
        TestGeminiAudioExecutor executor = new TestGeminiAudioExecutor(responseWithText("  translated text  "));

        ResponseEntity<tools.jackson.databind.JsonNode> response = executor.executeTextAudio(
                context(TranslationOperation.AUDIO_TRANSLATION, "/v1/audio/translations"),
                TranslationOperation.AUDIO_TRANSLATION,
                Map.of("temperature", "0.1", "prompt", "keep product names"),
                new GeminiGatewayResourceSupport.ResolvedBinaryFile("voice.wav", "audio/wav", "audio".getBytes(StandardCharsets.UTF_8))
        );

        assertEquals("translated text", response.getBody().path("text").asText());
        assertEquals(0.1f, executor.lastConfig.temperature().orElseThrow());
        assertTrue(executor.lastContent.text().contains("Translate the provided audio to English"));
        assertFalse(executor.lastContent.text().contains("spoken language"));
        assertTrue(executor.lastContent.text().contains("Additional instructions: keep product names"));
    }

    @Test
    void shouldMapAlloyToKoreForSpeech() {
        byte[] audioBytes = "audio-bytes".getBytes(StandardCharsets.UTF_8);
        TestGeminiAudioExecutor executor = new TestGeminiAudioExecutor(responseWithInlineAudio(audioBytes, "audio/wav"));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", "gemini-2.5-flash-preview-tts");
        payload.put("input", "你好");
        payload.put("voice", "alloy");

        ResponseEntity<byte[]> response = executor.executeSpeech(
                context(TranslationOperation.AUDIO_SPEECH, "/v1/audio/speech"),
                payload
        );

        assertEquals("Kore", executor.lastConfig.speechConfig()
                .flatMap(config -> config.voiceConfig())
                .flatMap(config -> config.prebuiltVoiceConfig())
                .flatMap(config -> config.voiceName())
                .orElseThrow());
        assertEquals(MediaType.parseMediaType("audio/wav"), response.getHeaders().getContentType());
        assertArrayEquals(audioBytes, response.getBody());
    }

    @Test
    void shouldRejectUnknownSpeechVoice() {
        TestGeminiAudioExecutor executor = new TestGeminiAudioExecutor(responseWithInlineAudio(new byte[] {1}, "audio/wav"));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", "gemini-2.5-flash-preview-tts");
        payload.put("input", "hello");
        payload.put("voice", "nova");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> executor.executeSpeech(context(TranslationOperation.AUDIO_SPEECH, "/v1/audio/speech"), payload)
        );

        assertEquals("Gemini audio.speech 仅支持 Gemini 原生 voice 名称，alloy 会映射为 Kore。", error.getMessage());
    }

    private GenerateContentResponse responseWithText(String text) {
        return GenerateContentResponse.builder()
                .candidates(List.of(
                        com.google.genai.types.Candidate.builder()
                                .content(Content.fromParts(Part.fromText(text)))
                                .build()
                ))
                .build();
    }

    private GenerateContentResponse responseWithInlineAudio(byte[] bytes, String mimeType) {
        return GenerateContentResponse.builder()
                .candidates(List.of(
                        com.google.genai.types.Candidate.builder()
                                .content(Content.fromParts(Part.fromBytes(bytes, mimeType)))
                                .build()
                ))
                .build();
    }

    private GatewayResourceExecutionContext context(TranslationOperation operation, String path) {
        CatalogCandidateView candidate = candidate(UpstreamSiteKind.GEMINI_DIRECT);
        RouteCandidateView routeCandidateView = new RouteCandidateView(candidate, 11L, 10, 100);
        RouteSelectionResult selectionResult = new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "gemini-2.5-flash",
                "gemini-2.5-flash",
                "gemini-2.5-flash",
                "openai",
                "prefix",
                "fingerprint",
                "gemini-2.5-flash",
                RouteSelectionSource.WEIGHTED_HASH,
                routeCandidateView,
                List.of(routeCandidateView)
        );
        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        credential.setBaseUrl("https://generativelanguage.googleapis.com");
        credential.setProviderType(ProviderType.GEMINI_DIRECT);
        return new GatewayResourceExecutionContext(
                1L,
                selectionResult,
                credential,
                "api-key",
                request(operation, path)
        );
    }

    private CanonicalResourceRequest request(TranslationOperation operation, String path) {
        return new CanonicalResourceRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "POST",
                path,
                path,
                Map.of(),
                "gemini-2.5-flash",
                TranslationResourceType.AUDIO,
                operation,
                objectMapper.createObjectNode(),
                Map.of(),
                List.of(),
                operation == TranslationOperation.AUDIO_SPEECH,
                false
        );
    }

    private CatalogCandidateView candidate(UpstreamSiteKind siteKind) {
        return new CatalogCandidateView(
                101L,
                "gemini",
                ProviderType.GEMINI_DIRECT,
                1L,
                ProviderFamily.GEMINI,
                siteKind,
                siteKind == UpstreamSiteKind.GEMINI_DIRECT ? AuthStrategy.API_KEY_QUERY : AuthStrategy.BEARER,
                PathStrategy.GEMINI_V1BETA_MODELS,
                ErrorSchemaStrategy.GEMINI_ERROR,
                "https://example.com",
                "gemini-2.5-flash",
                "gemini-2.5-flash",
                List.of("google_native"),
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                false,
                ReasoningTransport.GEMINI_THOUGHTS,
                InteropCapabilityLevel.NATIVE
        );
    }

    private final class TestGeminiAudioExecutor extends GeminiAudioGatewayResourceExecutor {

        private final GenerateContentResponse response;
        private Content lastContent;
        private GenerateContentConfig lastConfig;

        private TestGeminiAudioExecutor(GenerateContentResponse response) {
            super(
                    Mockito.mock(GeminiChatModelFactory.class),
                    Mockito.mock(GatewayFileService.class),
                    objectMapper
            );
            this.response = response;
        }

        @Override
        GenerateContentResponse generateContent(
                GatewayResourceExecutionContext context,
                Content content,
                GenerateContentConfig config) {
            this.lastContent = content;
            this.lastConfig = config;
            return response;
        }
    }
}
