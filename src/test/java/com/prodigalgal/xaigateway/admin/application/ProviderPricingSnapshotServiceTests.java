package com.prodigalgal.xaigateway.admin.application;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderPricingSnapshotServiceTests {

    @Test
    void shouldBuildVersionedChecksummedPricingSnapshotsFromCatalog() {
        ProviderCatalogSnapshot catalog = new ProviderCatalogLoader(new ObjectMapper()).load();
        ProviderPricingSnapshotService service = new ProviderPricingSnapshotService();
        Instant verifiedAt = Instant.parse("2026-05-14T00:00:00Z");

        List<ProviderPricingSnapshotView> snapshots = service.fromCatalog(catalog, verifiedAt);

        ProviderPricingSnapshotView gemini = snapshot(snapshots, "gemini");
        ProviderPricingSnapshotView qwen = snapshot(snapshots, "qwen");
        ProviderPricingSnapshotView xai = snapshot(snapshots, "xai");

        assertEquals("PUBLIC_PRICE_PAGE", gemini.sourceKind());
        assertEquals("APPROVED", gemini.approvalStatus());
        assertEquals("NO_DRIFT", gemini.driftStatus());
        assertTrue(gemini.productionEligible());
        assertTrue(gemini.snapshotVersion().startsWith(catalog.version() + ":gemini:"));
        assertEquals(64, gemini.checksum().length());
        assertEquals(verifiedAt, gemini.effectiveAt());

        assertEquals("OPERATOR_CONFIGURED", qwen.sourceKind());
        assertEquals("PENDING_REVIEW", qwen.approvalStatus());
        assertFalse(qwen.productionEligible());

        assertEquals("PUBLIC_PRICE_PAGE", xai.sourceKind());
        assertEquals("APPROVED", xai.approvalStatus());
        assertTrue(xai.productionEligible());
        assertFalse(snapshots.stream().anyMatch(item -> item.providerCode().equals("openrouter")));
        assertFalse(snapshots.stream().anyMatch(item -> item.providerCode().equals("dify")));
    }

    @Test
    void shouldOnlyExposeApprovedEffectiveSnapshotsForProductionBilling() {
        ProviderCatalogSnapshot catalog = new ProviderCatalogLoader(new ObjectMapper()).load();
        ProviderPricingSnapshotService service = new ProviderPricingSnapshotService();
        Instant now = Instant.parse("2026-05-14T00:00:00Z");

        List<ProviderPricingSnapshotView> snapshots = service.fromCatalog(catalog, now);
        ProviderPricingSnapshotView approvedQwen = service.approve(snapshot(snapshots, "qwen"), now);
        List<ProviderPricingSnapshotView> eligible = service.productionEligible(
                java.util.stream.Stream.concat(snapshots.stream(), java.util.stream.Stream.of(approvedQwen)).toList(),
                now
        );

        assertTrue(eligible.stream().anyMatch(item -> item.providerCode().equals("gemini")));
        assertTrue(eligible.stream().anyMatch(item -> item.providerCode().equals("qwen")
                && item.approvalStatus().equals("APPROVED")));
        assertFalse(eligible.stream().anyMatch(item -> item.providerCode().equals("openrouter")));
        assertFalse(eligible.stream().anyMatch(item -> item.providerCode().equals("dify")));
    }

    private ProviderPricingSnapshotView snapshot(List<ProviderPricingSnapshotView> snapshots, String providerCode) {
        return snapshots.stream()
                .filter(item -> item.providerCode().equals(providerCode))
                .findFirst()
                .orElseThrow();
    }
}
