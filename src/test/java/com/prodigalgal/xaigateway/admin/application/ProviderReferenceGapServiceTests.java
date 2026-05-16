package com.prodigalgal.xaigateway.admin.application;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderReferenceGapServiceTests {

    @Test
    void shouldExposeReferenceProviderMediaAndPricingGaps() {
        ProviderReferenceGapService service = new ProviderReferenceGapService(new ProviderCatalogLoader(new ObjectMapper()));

        var response = service.get();

        assertEquals("new-api relay/channel", response.referenceName());
        assertTrue(response.providers().stream().anyMatch(row ->
                row.referenceChannel().equals("openai") && row.supportStatus().equals("SUPPORTED")));
        assertTrue(response.providers().stream().anyMatch(row ->
                row.referenceChannel().equals("ali")
                        && row.catalogPresetCode().equals("qwen")
                        && row.supportStatus().equals("SUPPORTED")));
        assertTrue(response.providers().stream().anyMatch(row ->
                row.referenceChannel().equals("codex")
                        && row.supportStatus().equals("COMPATIBLE")
                        && row.adapterBoundary().contains("not a provider catalog preset")));
        assertTrue(response.providers().stream().anyMatch(row ->
                row.referenceChannel().equals("xai")
                        && row.catalogPresetCode().equals("xai")
                        && row.supportStatus().equals("SUPPORTED")));
        assertTrue(response.providers().stream().anyMatch(row ->
                row.referenceChannel().equals("perplexity")
                        && row.catalogPresetCode().equals("perplexity")
                        && row.supportMode().equals("web-search-openai-compatible")));
        assertTrue(response.providers().stream().anyMatch(row ->
                row.referenceChannel().equals("vertex")
                        && row.catalogPresetCode().equals("vertex")
                        && row.supportMode().equals("vertex-google-native")));
        assertTrue(response.mediaCapabilities().stream().anyMatch(row ->
                row.capability().equals("video")
                        && row.supportStatus().equals("PROVIDER_ADAPTER")
                        && row.providerPresets().contains("gemini")));
        assertTrue(response.mediaCapabilities().stream().anyMatch(row ->
                row.capability().equals("music")
                        && row.supportStatus().equals("PROVIDER_ADAPTER")
                        && row.providerPresets().contains("suno-like")
                        && row.smokeHint().contains("XAG_SMOKE_SUNO")));
        assertTrue(response.mediaCapabilities().stream().anyMatch(row ->
                row.capability().equals("rerank")
                        && row.providerPresets().contains("cohere")
                        && row.providerPresets().contains("jina")));
        assertTrue(response.mediaCapabilities().stream().anyMatch(row ->
                row.capability().equals("web_search")
                        && row.supportStatus().equals("PROVIDER_ADAPTER")
                        && row.providerPresets().contains("openai")
                        && row.providerPresets().contains("perplexity")));
        assertTrue(response.pricingSync().stream().anyMatch(row ->
                row.providerCode().equals("gemini")
                        && row.requiresRealKey()
                        && row.failureClasses().contains("QUOTA_EXCEEDED")
                        && row.approvalStatus().equals("APPROVED")
                        && row.productionEligible()
                        && row.snapshotVersion().startsWith(response.catalogVersion() + ":gemini:")
                        && row.checksum().length() == 64));
        assertTrue(response.pricingSync().stream().anyMatch(row ->
                row.providerCode().equals("qwen")
                        && row.approvalStatus().equals("PENDING_REVIEW")
                        && row.driftStatus().equals("PENDING_REVIEW")
                        && !row.productionEligible()));
        assertTrue(response.pricingSync().stream().anyMatch(row ->
                row.providerCode().equals("perplexity")
                        && row.syncStatus().equals("PUBLIC_SOURCE_TRACKED")
                        && row.requiresRealKey()));
        assertTrue(response.recommendedActions().stream().anyMatch(item -> item.contains("真实 smoke")));
    }
}
