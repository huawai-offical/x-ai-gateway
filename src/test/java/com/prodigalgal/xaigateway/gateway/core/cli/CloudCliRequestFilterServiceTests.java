package com.prodigalgal.xaigateway.gateway.core.cli;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudCliRequestFilterServiceTests {

    private final CloudCliRequestFilterService service = new CloudCliRequestFilterService();

    @Test
    void shouldReplaceRemoveAndMaskForMatchingClientFamily() {
        CanonicalRequest request = request("hello secret remove-me");

        CloudCliRequestFilterResult result = service.apply(
                request,
                GatewayClientFamily.CODEX,
                List.of(
                        new CloudCliRequestFilterRule("replace-secret", CloudCliRequestFilterAction.REPLACE, List.of("CODEX"), "user", "secret", "public"),
                        new CloudCliRequestFilterRule("mask-public", CloudCliRequestFilterAction.MASK, List.of("CODEX"), "user", "public", null),
                        new CloudCliRequestFilterRule("remove-fragment", CloudCliRequestFilterAction.REMOVE, List.of("CODEX"), "user", "remove-me", null)
                )
        );

        assertEquals(List.of("replace-secret", "mask-public", "remove-fragment"), result.appliedRuleIds());
        assertEquals("hello [FILTERED] ", text(result.request()));
    }

    @Test
    void shouldSkipInvalidAndNonMatchingRulesWithoutFailingRequest() {
        CanonicalRequest request = request("hello secret");

        CloudCliRequestFilterResult result = service.apply(
                request,
                GatewayClientFamily.CURSOR,
                List.of(
                        new CloudCliRequestFilterRule("invalid-action", null, List.of("CURSOR"), "user", "secret", "public"),
                        new CloudCliRequestFilterRule("other-family", CloudCliRequestFilterAction.MASK, List.of("CODEX"), "user", "secret", null)
                )
        );

        assertTrue(result.appliedRuleIds().isEmpty());
        assertEquals(List.of("invalid-action", "other-family"), result.skippedRuleIds());
        assertEquals("hello secret", text(result.request()));
    }

    @Test
    void shouldFilterProviderExtensionsToolSchemaAndFileMetadataWithAuditHits() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        CanonicalRequest request = new CanonicalRequest(
                "sk-gw-cli",
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                "gpt-4o-mini",
                List.of(new CanonicalMessage(CanonicalMessageRole.USER, List.of(
                        CanonicalContentPart.file("application/json", "gateway://file_secret", "secret-plan.json")
                ))),
                List.of(new com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolDefinition(
                        "lookup",
                        "demo",
                        objectMapper.readTree("""
                                {"type":"object","properties":{"apiKey":{"description":"contains secret token"}}}
                                """),
                        true
                )),
                null,
                null,
                null,
                null,
                objectMapper.readTree("""
                        {"providerExtensions":{"metadata":{"api_key":"secret-value","trace":"keep"}}}
                        """)
        );

        CloudCliRequestFilterResult result = service.apply(
                request,
                GatewayClientFamily.CODEX,
                List.of(
                        new CloudCliRequestFilterRule("mask-provider-extension", CloudCliRequestFilterAction.MASK, List.of("CODEX"), "all", "secret", null, "provider_extensions", "$.providerExtensions.metadata.api_key"),
                        new CloudCliRequestFilterRule("redact-tool-schema", CloudCliRequestFilterAction.REDACT, List.of("CODEX"), "all", "secret", null, "tool_schema", "$.properties.apiKey.description"),
                        new CloudCliRequestFilterRule("redact-file-name", CloudCliRequestFilterAction.REDACT, List.of("CODEX"), "all", "secret", null, "file_metadata", "name")
                )
        );

        JsonNode providerExtensions = result.request().providerExtensions();
        JsonNode toolSchema = result.request().tools().getFirst().inputSchema();
        CanonicalContentPart file = result.request().messages().getFirst().parts().getFirst();

        assertEquals(List.of("mask-provider-extension", "redact-tool-schema", "redact-file-name"), result.appliedRuleIds());
        assertEquals("[FILTERED]-value", providerExtensions.path("providerExtensions").path("metadata").path("api_key").asText());
        assertEquals("[REDACTED]", toolSchema.path("properties").path("apiKey").path("description").asText());
        assertEquals("[REDACTED]", file.name());
        assertEquals(3, result.hits().size());
        assertTrue(result.hits().stream().allMatch(hit -> hit.summary().contains("value(s)")));
    }

    @Test
    void shouldDenyStructuredRulesAndSkipInvalidJsonPathRule() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        CanonicalRequest request = new CanonicalRequest(
                "sk-gw-cli",
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                "gpt-4o-mini",
                List.of(new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text("hello")))),
                List.of(),
                null,
                null,
                null,
                null,
                objectMapper.readTree("""
                        {"metadata":{"blocked":"deny-me"}}
                        """)
        );

        CloudCliRequestFilterResult result = service.apply(
                request,
                GatewayClientFamily.CODEX,
                List.of(
                        new CloudCliRequestFilterRule("invalid-json-path", CloudCliRequestFilterAction.MASK, List.of("CODEX"), "all", "x", null, "provider_extensions", null),
                        new CloudCliRequestFilterRule("deny-provider-extension", CloudCliRequestFilterAction.DENY, List.of("CODEX"), "all", "deny-me", null, "provider_extensions", "$.metadata.blocked")
                )
        );

        assertEquals(List.of("invalid-json-path"), result.skippedRuleIds());
        assertEquals(List.of("deny-provider-extension"), result.appliedRuleIds());
        assertTrue(result.denied());
        assertEquals("deny-provider-extension", result.denyRuleId());
        assertTrue(result.denyReason().contains("deny"));
    }

    private CanonicalRequest request(String content) {
        return new CanonicalRequest(
                "sk-gw-cli",
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                "gpt-4o-mini",
                List.of(new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text(content)))),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
    }

    private String text(CanonicalRequest request) {
        return request.messages().getFirst().parts().getFirst().text();
    }
}
