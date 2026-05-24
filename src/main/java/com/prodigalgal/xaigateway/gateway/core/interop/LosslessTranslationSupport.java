package com.prodigalgal.xaigateway.gateway.core.interop;

public enum LosslessTranslationSupport {
    LOSSLESS,
    NATIVE_REQUIRED,
    UNSUPPORTED;

    public boolean canTranslateLosslessly() {
        return this == LOSSLESS;
    }

    public boolean mustFailWhenRequestedAsTranslation() {
        return this != LOSSLESS;
    }
}
