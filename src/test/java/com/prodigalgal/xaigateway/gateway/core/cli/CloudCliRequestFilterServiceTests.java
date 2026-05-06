package com.prodigalgal.xaigateway.gateway.core.cli;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

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
