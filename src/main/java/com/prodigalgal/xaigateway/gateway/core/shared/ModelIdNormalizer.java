package com.prodigalgal.xaigateway.gateway.core.shared;

public final class ModelIdNormalizer {

    private ModelIdNormalizer() {
    }

    public static String normalize(String modelId) {
        if (modelId == null) {
            return null;
        }

        return modelId.replaceFirst("^models/", "").trim().toLowerCase();
    }
}
