package com.prodigalgal.xaigateway.gateway.core.execution;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalFileRef;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileContent;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

final class GeminiGatewayResourceSupport {

    private GeminiGatewayResourceSupport() {
    }

    static boolean supportsGoogleGenAiCandidate(CanonicalResourceRequest request, CatalogCandidateView candidate, String... normalizedPaths) {
        if (request == null || candidate == null || request.normalizedPath() == null) {
            return false;
        }
        if (candidate.providerType() != ProviderType.GEMINI_DIRECT
                || candidate.pathStrategy() != PathStrategy.GEMINI_V1BETA_MODELS
                || !supportsGoogleGenAiSite(candidate.siteKind(), candidate.authStrategy())) {
            return false;
        }
        return Arrays.stream(normalizedPaths).anyMatch(path -> path.equals(request.normalizedPath()));
    }

    static boolean supportsGoogleGenAiSite(UpstreamSiteKind siteKind, AuthStrategy authStrategy) {
        if (siteKind == UpstreamSiteKind.GEMINI_DIRECT) {
            return authStrategy == AuthStrategy.API_KEY_QUERY;
        }
        if (siteKind == UpstreamSiteKind.VERTEX_AI) {
            return authStrategy == AuthStrategy.BEARER;
        }
        return false;
    }

    static Client createClient(GeminiChatModelFactory geminiChatModelFactory, GatewayResourceExecutionContext context) {
        return geminiChatModelFactory.createClient(
                context.selectionResult().selectedCandidate().candidate().siteKind(),
                context.credential().getBaseUrl(),
                context.credentialMaterial()
        );
    }

    static String responseModel(GatewayResourceExecutionContext context) {
        if (context.selectionResult() == null) {
            return null;
        }
        String publicModel = context.selectionResult().publicModel();
        return publicModel == null || publicModel.isBlank()
                ? context.selectionResult().resolvedModelKey()
                : publicModel;
    }

    static Mono<ResolvedBinaryFile> resolveBinaryFile(
            GatewayResourceExecutionContext context,
            Map<String, FilePart> files,
            GatewayFileService gatewayFileService,
            String preferredFieldName) {
        FilePart uploadedPart = pickUploadedFile(files, preferredFieldName);
        if (uploadedPart != null) {
            return DataBufferUtils.join(uploadedPart.content())
                    .map(buffer -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        DataBufferUtils.release(buffer);
                        MediaType contentType = uploadedPart.headers().getContentType();
                        return new ResolvedBinaryFile(
                                uploadedPart.filename(),
                                contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType.toString(),
                                bytes
                        );
                    });
        }
        return Mono.fromCallable(() -> {
            CanonicalFileRef fileRef = pickGatewayFile(context.request(), preferredFieldName);
            if (fileRef == null) {
                throw new IllegalArgumentException("请求缺少必需文件。");
            }
            GatewayFileContent fileContent = gatewayFileService.getFileContent(fileRef.fileKey(), context.distributedKeyId());
            String mimeType = fileContent.mimeType();
            if ((mimeType == null || mimeType.isBlank()) && fileRef.mimeType() != null && !fileRef.mimeType().isBlank()) {
                mimeType = fileRef.mimeType();
            }
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
            return new ResolvedBinaryFile(
                    fileContent.metadata().filename(),
                    mimeType,
                    fileContent.bytes()
            );
        });
    }

    static Content contentWithTextAndBinary(String instruction, ResolvedBinaryFile file) {
        return Content.fromParts(
                Part.fromText(instruction),
                Part.fromBytes(file.bytes(), file.mimeType())
        );
    }

    static Map<String, Boolean> defaultModerationCategories() {
        Map<String, Boolean> categories = new LinkedHashMap<>();
        categories.put("sexual", false);
        categories.put("hate", false);
        categories.put("harassment", false);
        categories.put("self-harm", false);
        categories.put("sexual/minors", false);
        categories.put("hate/threatening", false);
        categories.put("violence/graphic", false);
        categories.put("self-harm/intent", false);
        categories.put("self-harm/instructions", false);
        categories.put("harassment/threatening", false);
        categories.put("violence", false);
        categories.put("illicit", false);
        categories.put("illicit/violent", false);
        return categories;
    }

    static Map<String, Double> defaultModerationScores() {
        Map<String, Double> scores = new LinkedHashMap<>();
        defaultModerationCategories().forEach((key, value) -> scores.put(key, 0.0d));
        return scores;
    }

    private static FilePart pickUploadedFile(Map<String, FilePart> files, String preferredFieldName) {
        if (files == null || files.isEmpty()) {
            return null;
        }
        if (preferredFieldName != null && files.containsKey(preferredFieldName)) {
            return files.get(preferredFieldName);
        }
        return files.values().stream().findFirst().orElse(null);
    }

    private static CanonicalFileRef pickGatewayFile(CanonicalResourceRequest request, String preferredFieldName) {
        if (request == null || request.fileRefs() == null || request.fileRefs().isEmpty()) {
            return null;
        }
        if (preferredFieldName != null) {
            for (CanonicalFileRef fileRef : request.fileRefs()) {
                if (preferredFieldName.equals(fileRef.fieldName())) {
                    return fileRef;
                }
            }
        }
        return request.fileRefs().getFirst();
    }

    record ResolvedBinaryFile(
            String filename,
            String mimeType,
            byte[] bytes
    ) {
    }
}
