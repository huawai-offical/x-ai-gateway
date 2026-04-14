package com.prodigalgal.xaigateway.gateway.core.canonical;

import java.util.List;

public record CanonicalMessage(
        CanonicalMessageRole role,
        List<CanonicalContentPart> parts
) {
}
