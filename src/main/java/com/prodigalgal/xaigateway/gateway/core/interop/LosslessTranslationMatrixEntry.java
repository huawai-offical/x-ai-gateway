package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;

public record LosslessTranslationMatrixEntry(
        TranslationResourceType resourceType,
        TranslationOperation operation,
        String attributePath,
        CanonicalIngressProtocol sourceProtocol,
        CanonicalIngressProtocol targetProtocol,
        LosslessTranslationSupport support,
        String requirement,
        String failureCode
) {
    public LosslessTranslationMatrixEntry {
        resourceType = resourceType == null ? TranslationResourceType.UNKNOWN : resourceType;
        operation = operation == null ? TranslationOperation.UNKNOWN : operation;
        attributePath = normalize(attributePath);
        sourceProtocol = sourceProtocol == null ? CanonicalIngressProtocol.UNKNOWN : sourceProtocol;
        targetProtocol = targetProtocol == null ? CanonicalIngressProtocol.UNKNOWN : targetProtocol;
        support = support == null ? LosslessTranslationSupport.UNSUPPORTED : support;
        requirement = requirement == null ? "" : requirement;
        failureCode = failureCode == null ? defaultFailureCode(support) : failureCode;
    }

    public boolean canTranslateLosslessly() {
        return support.canTranslateLosslessly();
    }

    public boolean mustFailWhenRequestedAsTranslation() {
        return support.mustFailWhenRequestedAsTranslation();
    }

    private static String normalize(String attributePath) {
        if (attributePath == null || attributePath.isBlank()) {
            return "unknown";
        }
        return attributePath.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String defaultFailureCode(LosslessTranslationSupport support) {
        if (support == LosslessTranslationSupport.NATIVE_REQUIRED) {
            return "native_route_required";
        }
        if (support == LosslessTranslationSupport.UNSUPPORTED) {
            return "unsupported_translation_attribute";
        }
        return "";
    }
}
