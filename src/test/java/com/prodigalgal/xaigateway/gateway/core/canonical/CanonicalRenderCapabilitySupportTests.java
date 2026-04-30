package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.RouteSelectionMode;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanonicalRenderCapabilitySupportTests {

    @Test
    void shouldTreatGoogleNamespaceAliasesAsNativeRenderedPaths() {
        GatewayRequestSemantics embeddingSemantics = new GatewayRequestSemantics(
                TranslationResourceType.EMBEDDING,
                TranslationOperation.EMBEDDING_CREATE,
                "embeddings",
                "/v1beta/models/{model}:embedContent",
                List.of(InteropFeature.EMBEDDINGS),
                RouteSelectionMode.CATALOG_SELECTION
        );
        GatewayRequestSemantics fileSemantics = new GatewayRequestSemantics(
                TranslationResourceType.FILE,
                TranslationOperation.FILE_CREATE,
                "files",
                "/upload/v1beta/files",
                List.of(InteropFeature.FILE_OBJECT),
                RouteSelectionMode.CATALOG_SELECTION
        );

        assertEquals(
                InteropCapabilityLevel.NATIVE,
                CanonicalRenderCapabilitySupport.renderLevel("google_native", "/google/v1beta/models/text-embedding-004:embedContent", embeddingSemantics)
        );
        assertEquals(
                InteropCapabilityLevel.NATIVE,
                CanonicalRenderCapabilitySupport.renderLevel("google_native", "/google/upload/v1beta/files", fileSemantics)
        );
    }
}
