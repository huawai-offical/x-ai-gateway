package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import java.util.Locale;

public final class CanonicalRenderCapabilitySupport {

    private CanonicalRenderCapabilitySupport() {
    }

    public static InteropCapabilityLevel renderLevel(
            String protocol,
            String requestPath,
            GatewayRequestSemantics semantics) {
        String normalizedProtocol = protocol == null ? "openai" : protocol.trim().toLowerCase(Locale.ROOT);
        String normalizedRequestPath = normalizeGoogleNativeNamespace(requestPath);
        TranslationResourceType resourceType = semantics == null ? TranslationResourceType.UNKNOWN : semantics.resourceType();
        TranslationOperation operation = semantics == null ? TranslationOperation.UNKNOWN : semantics.operation();

        return switch (normalizedProtocol) {
            case "openai" -> renderOpenAiLevel(normalizedRequestPath, resourceType);
            case "responses" -> resourceType == TranslationResourceType.RESPONSE
                    || operation == TranslationOperation.RESPONSE_CREATE
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case "anthropic_native" -> isAnthropicNativePath(normalizedRequestPath, resourceType)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case "google_native" -> isGeminiContentPath(normalizedRequestPath)
                    || isGeminiEmbeddingsPath(normalizedRequestPath)
                    || isGeminiFilesPath(normalizedRequestPath)
                    || isGeminiResourceMode(normalizedRequestPath, resourceType)
                    || resourceType == TranslationResourceType.CHAT
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            default -> InteropCapabilityLevel.UNSUPPORTED;
        };
    }

    public static InteropCapabilityLevel minimum(InteropCapabilityLevel left, InteropCapabilityLevel right) {
        if (left == null || right == null) {
            return InteropCapabilityLevel.UNSUPPORTED;
        }
        if (left == InteropCapabilityLevel.UNSUPPORTED || right == InteropCapabilityLevel.UNSUPPORTED) {
            return InteropCapabilityLevel.UNSUPPORTED;
        }
        if (left == InteropCapabilityLevel.LOSSY || right == InteropCapabilityLevel.LOSSY) {
            return InteropCapabilityLevel.LOSSY;
        }
        if (left == InteropCapabilityLevel.EMULATED || right == InteropCapabilityLevel.EMULATED) {
            return InteropCapabilityLevel.EMULATED;
        }
        return InteropCapabilityLevel.NATIVE;
    }

    private static InteropCapabilityLevel renderOpenAiLevel(String requestPath, TranslationResourceType resourceType) {
        if ("/v1/responses".equals(requestPath) || resourceType == TranslationResourceType.RESPONSE) {
            return InteropCapabilityLevel.NATIVE;
        }
        if (requestPath == null || requestPath.isBlank()) {
            return InteropCapabilityLevel.NATIVE;
        }
        if (requestPath.startsWith("/v1/")) {
            return InteropCapabilityLevel.NATIVE;
        }
        return resourceType == TranslationResourceType.UNKNOWN
                ? InteropCapabilityLevel.NATIVE
                : InteropCapabilityLevel.UNSUPPORTED;
    }

    private static boolean isGeminiContentPath(String requestPath) {
        return requestPath != null
                && requestPath.startsWith("/v1beta/models/")
                && (requestPath.contains(":generateContent") || requestPath.contains(":streamGenerateContent"));
    }

    private static boolean isGeminiEmbeddingsPath(String requestPath) {
        return requestPath != null
                && requestPath.startsWith("/v1beta/models/")
                && (requestPath.contains(":embedContent") || requestPath.contains(":batchEmbedContents"));
    }

    private static boolean isGeminiFilesPath(String requestPath) {
        return requestPath != null
                && ("/upload/v1beta/files".equals(requestPath)
                || "/v1beta/files".equals(requestPath)
                || requestPath.startsWith("/v1beta/files/"));
    }

    private static boolean isGeminiResourceMode(String requestPath, TranslationResourceType resourceType) {
        if (!isGeminiContentPath(requestPath)) {
            return false;
        }
        return resourceType == TranslationResourceType.IMAGE || resourceType == TranslationResourceType.AUDIO;
    }

    private static boolean isAnthropicNativePath(String requestPath, TranslationResourceType resourceType) {
        if ("/v1/messages".equals(requestPath) || resourceType == TranslationResourceType.CHAT) {
            return true;
        }
        if (requestPath == null || requestPath.isBlank()) {
            return false;
        }
        return false;
    }

    private static String normalizeGoogleNativeNamespace(String requestPath) {
        if (requestPath == null) {
            return null;
        }
        if (requestPath.startsWith("/google/upload/v1beta")) {
            return requestPath.substring("/google".length());
        }
        if (requestPath.startsWith("/google/v1beta")) {
            return requestPath.substring("/google".length());
        }
        return requestPath;
    }
}
