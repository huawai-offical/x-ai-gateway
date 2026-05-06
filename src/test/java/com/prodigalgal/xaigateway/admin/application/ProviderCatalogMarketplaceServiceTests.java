package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ProviderCatalogMarketplaceUpdateRequest;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderCatalogMarketplaceServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldDryRunVerifySignatureWithoutWritingCache() {
        GatewayProperties properties = properties();
        ObjectMapper objectMapper = new ObjectMapper();
        ProviderCatalogLoader loader = new ProviderCatalogLoader(objectMapper, properties);
        ProviderCatalogMarketplaceService service = new ProviderCatalogMarketplaceService(loader, objectMapper, properties);
        String catalog = catalog("2026.05.06-marketplace", "openai");
        String signature = hmac(catalog, "secret");

        var response = service.update(new ProviderCatalogMarketplaceUpdateRequest(
                null,
                catalog,
                signature,
                "secret",
                true
        ));

        assertEquals("DRY_RUN_PASS", response.status());
        assertFalse(response.cacheWritten());
        assertFalse(Files.exists(cacheRoot().resolve("current.json")));
    }

    @Test
    void shouldUpdateCacheRejectBadSignatureAndRollbackPreviousVersion() throws Exception {
        GatewayProperties properties = properties();
        ObjectMapper objectMapper = new ObjectMapper();
        ProviderCatalogLoader loader = new ProviderCatalogLoader(objectMapper, properties);
        ProviderCatalogMarketplaceService service = new ProviderCatalogMarketplaceService(loader, objectMapper, properties);
        String firstCatalog = catalog("2026.05.06-marketplace", "openai");
        String secondCatalog = catalog("2026.05.07-marketplace", "deepseek");

        var first = service.update(new ProviderCatalogMarketplaceUpdateRequest(
                null,
                firstCatalog,
                hmac(firstCatalog, "secret"),
                "secret",
                false
        ));
        var rejected = service.update(new ProviderCatalogMarketplaceUpdateRequest(
                null,
                secondCatalog,
                "sha256=bad",
                "secret",
                false
        ));
        var second = service.update(new ProviderCatalogMarketplaceUpdateRequest(
                null,
                secondCatalog,
                hmac(secondCatalog, "secret"),
                "secret",
                false
        ));
        var rollback = service.rollback();
        ProviderCatalogSnapshot snapshot = loader.load();

        assertEquals("UPDATED", first.status());
        assertEquals("SIGNATURE_FAILED", rejected.status());
        assertEquals("2026.05.06-marketplace", loader.loadFromJson(
                Files.readString(cacheRoot().resolve("previous.json"), StandardCharsets.UTF_8),
                "previous"
        ).version());
        assertEquals("UPDATED", second.status());
        assertEquals("ROLLED_BACK", rollback.status());
        assertEquals("2026.05.06-marketplace", snapshot.version());
        assertTrue(service.status().cached());
        assertTrue(service.status().previousAvailable());
    }

    private GatewayProperties properties() {
        GatewayProperties properties = new GatewayProperties();
        properties.getStorage().setFileRoot(tempDir.toString());
        return properties;
    }

    private Path cacheRoot() {
        return tempDir.toAbsolutePath().resolve("provider-catalog-marketplace");
    }

    private String catalog(String version, String code) {
        String siteKind = "deepseek".equals(code) ? "DEEPSEEK" : "OPENAI_DIRECT";
        String displayName = "deepseek".equals(code) ? "DeepSeek Marketplace" : "OpenAI Marketplace";
        String baseUrl = "deepseek".equals(code) ? "https://api.deepseek.com" : "https://api.openai.com";
        return """
                {
                  "catalogVersion": "%s",
                  "catalogSource": "test",
                  "presets": [
                    {
                      "code": "%s",
                      "displayName": "%s",
                      "siteKind": "%s",
                      "defaultBaseUrl": "%s",
                      "description": "marketplace test preset",
                      "capabilityTags": ["chat"],
                      "costProfile": "test",
                      "errorMode": "openai_error",
                      "deprecated": false,
                      "conformanceChecks": ["chat.native"]
                    }
                  ]
                }
                """.formatted(version, code, displayName, siteKind, baseUrl).trim();
    }

    private String hmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
