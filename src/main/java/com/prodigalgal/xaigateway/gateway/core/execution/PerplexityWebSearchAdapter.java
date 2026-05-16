package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

public class PerplexityWebSearchAdapter {

    private static final String GATEWAY_WEB_SEARCH_PATH = "/v1/web_search";
    private static final String UPSTREAM_SONAR_PATH = "/v1/sonar";
    private static final List<String> PASSTHROUGH_FIELDS = List.of(
            "max_tokens",
            "temperature",
            "top_p",
            "stream",
            "stop",
            "response_format",
            "web_search_options",
            "search_domain_filter",
            "search_language_filter",
            "search_recency_filter",
            "search_after_date_filter",
            "search_before_date_filter",
            "last_updated_before_filter",
            "last_updated_after_filter",
            "image_format_filter",
            "image_domain_filter",
            "search_mode",
            "return_images",
            "return_related_questions",
            "enable_search_classifier",
            "disable_search",
            "stream_mode",
            "reasoning_effort",
            "language_preference"
    );

    public boolean supports(UpstreamSiteKind siteKind, String requestPath) {
        return siteKind == UpstreamSiteKind.PERPLEXITY && GATEWAY_WEB_SEARCH_PATH.equals(requestPath);
    }

    public String upstreamPath() {
        return UPSTREAM_SONAR_PATH;
    }

    public ObjectNode toUpstreamPayload(JsonNode gatewayPayload, String resolvedModelKey) {
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        output.put("model", firstNonBlank(resolvedModelKey, gatewayPayload.path("model").asText(null)));
        JsonNode messages = gatewayPayload.path("messages");
        if (messages.isArray() && !messages.isEmpty()) {
            output.set("messages", messages.deepCopy());
        } else {
            String query = firstNonBlank(gatewayPayload.path("query").asText(null), gatewayPayload.path("input").asText(null));
            output.putArray("messages")
                    .addObject()
                    .put("role", "user")
                    .put("content", query);
        }
        for (String field : PASSTHROUGH_FIELDS) {
            JsonNode value = gatewayPayload.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                output.set(field, value.deepCopy());
            }
        }
        return output;
    }

    private String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        throw new IllegalArgumentException("Perplexity web_search 请求缺少 model 或 query。");
    }
}
