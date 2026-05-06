package com.prodigalgal.xaigateway.docs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionManifestSchemaTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldDocumentCompanionManifestPrivacyBoundary() throws IOException {
        JsonNode schema = OBJECT_MAPPER.readTree(Files.readString(Path.of("docs/companion/companion-manifest.schema.json")));
        JsonNode privacy = schema.path("properties").path("privacy").path("properties");

        assertEquals(false, privacy.path("uploadsWorkspaceFiles").path("const").asBoolean(true));
        assertEquals(false, privacy.path("uploadsSecrets").path("const").asBoolean(true));
        assertEquals(false, privacy.path("uploadsSessionContent").path("const").asBoolean(true));
    }

    @Test
    void shouldProvideCompanionManifestExample() throws IOException {
        JsonNode example = OBJECT_MAPPER.readTree(Files.readString(Path.of("docs/companion/companion-manifest.example.json")));

        assertEquals("1.0", example.path("manifestVersion").asText());
        assertFalse(example.path("clientInstance").path("instanceId").asText().isBlank());
        assertTrue(example.path("capabilities").path("deepLinkAuthorization").asBoolean());
        assertFalse(example.path("privacy").path("uploadsWorkspaceFiles").asBoolean(true));
        assertFalse(example.path("privacy").path("uploadsSecrets").asBoolean(true));
        assertFalse(example.path("privacy").path("uploadsSessionContent").asBoolean(true));
    }
}
