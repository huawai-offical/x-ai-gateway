package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionResult;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.protocol.ingress.google.GeminiEmbeddingsEncoder;
import com.prodigalgal.xaigateway.protocol.ingress.google.GeminiFilesEncoder;
import com.prodigalgal.xaigateway.protocol.ingress.google.GeminiGenerateContentResourceEncoder;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GoogleNativeNonChatCanonicalRenderer implements NonChatCanonicalRenderer {

    private final ObjectMapper objectMapper;
    private final GeminiEmbeddingsEncoder geminiEmbeddingsEncoder;
    private final GeminiGenerateContentResourceEncoder geminiGenerateContentResourceEncoder;
    private final GeminiFilesEncoder geminiFilesEncoder;

    public GoogleNativeNonChatCanonicalRenderer(
            ObjectMapper objectMapper,
            GeminiEmbeddingsEncoder geminiEmbeddingsEncoder,
            GeminiGenerateContentResourceEncoder geminiGenerateContentResourceEncoder,
            GeminiFilesEncoder geminiFilesEncoder) {
        this.objectMapper = objectMapper;
        this.geminiEmbeddingsEncoder = geminiEmbeddingsEncoder;
        this.geminiGenerateContentResourceEncoder = geminiGenerateContentResourceEncoder;
        this.geminiFilesEncoder = geminiFilesEncoder;
    }

    @Override
    public boolean supports(
            CanonicalIngressProtocol ingressProtocol,
            String requestPath,
            GatewayRequestSemantics semantics
    ) {
        if (ingressProtocol != CanonicalIngressProtocol.GOOGLE_NATIVE || semantics == null) {
            return false;
        }
        return switch (semantics.operation()) {
            case EMBEDDING_CREATE,
                    IMAGE_GENERATION,
                    AUDIO_SPEECH,
                    FILE_CREATE,
                    FILE_LIST,
                    FILE_GET,
                    FILE_DELETE -> true;
            default -> false;
        };
    }

    @Override
    public NonChatRenderedResponse render(
            CanonicalResourceRequest request,
            CanonicalExecutionPlan executionPlan,
            GatewayResourceExecutionResult result
    ) {
        JsonNode renderedBody = switch (request.operation()) {
            case EMBEDDING_CREATE -> request.requestPath().contains(":batchEmbedContents")
                    ? geminiEmbeddingsEncoder.encodeBatch(requireJson(result))
                    : geminiEmbeddingsEncoder.encodeSingle(requireJson(result));
            case IMAGE_GENERATION -> objectMapper.valueToTree(geminiGenerateContentResourceEncoder.encodeImageGeneration(result));
            case AUDIO_SPEECH -> objectMapper.valueToTree(geminiGenerateContentResourceEncoder.encodeAudioSpeech(result));
            default -> throw new IllegalArgumentException("当前 Google native renderer 不支持该 execution result。");
        };
        return new NonChatRenderedResponse(ResponseEntity.status(result.statusCode()).body(renderedBody));
    }

    @Override
    public boolean supportsNativeView(
            CanonicalIngressProtocol ingressProtocol,
            String requestPath,
            GatewayRequestSemantics semantics,
            Object nativeView
    ) {
        if (!supports(ingressProtocol, requestPath, semantics)) {
            return false;
        }
        return nativeView instanceof GatewayFileService.GoogleNativeFileView
                || isGoogleFileList(semantics, nativeView);
    }

    @Override
    public NonChatRenderedResponse renderNativeView(
            CanonicalResourceRequest request,
            CanonicalExecutionPlan executionPlan,
            Object nativeView
    ) {
        JsonNode renderedBody;
        if (nativeView instanceof GatewayFileService.GoogleNativeFileView fileView) {
            renderedBody = geminiFilesEncoder.encode(fileView);
        } else if (isGoogleFileList(request.operation(), nativeView)) {
            @SuppressWarnings("unchecked")
            List<GatewayFileService.GoogleNativeFileView> views = (List<GatewayFileService.GoogleNativeFileView>) nativeView;
            renderedBody = geminiFilesEncoder.encodeList(views);
        } else {
            throw new IllegalArgumentException("当前 Google native view 无法渲染。");
        }
        return new NonChatRenderedResponse(ResponseEntity.ok(renderedBody));
    }

    private JsonNode requireJson(GatewayResourceExecutionResult result) {
        JsonNode body = result.responseJson();
        if (body == null) {
            throw new IllegalStateException("Google native render 缺少 JSON body。");
        }
        return body;
    }

    private boolean isGoogleFileList(GatewayRequestSemantics semantics, Object nativeView) {
        return semantics.operation() == TranslationOperation.FILE_LIST && isGoogleFileList(semantics.operation(), nativeView);
    }

    private boolean isGoogleFileList(TranslationOperation operation, Object nativeView) {
        if (operation != TranslationOperation.FILE_LIST || !(nativeView instanceof List<?> list)) {
            return false;
        }
        return list.isEmpty() || list.get(0) instanceof GatewayFileService.GoogleNativeFileView;
    }
}
