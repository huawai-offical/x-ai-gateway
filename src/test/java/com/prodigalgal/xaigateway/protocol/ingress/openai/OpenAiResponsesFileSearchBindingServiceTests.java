package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiResponsesFileSearchBindingServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBindLocalVectorStoreResultsAndStripHostedFileSearchTool() throws Exception {
        GatewayAsyncResourceService asyncResourceService = Mockito.mock(GatewayAsyncResourceService.class);
        OpenAiResponsesFileSearchBindingService service = new OpenAiResponsesFileSearchBindingService(
                asyncResourceService,
                objectMapper
        );
        Mockito.when(asyncResourceService.searchVectorStore(
                Mockito.eq("vs_1"),
                Mockito.eq(1L),
                Mockito.argThat(request -> request.path("query").asText().contains("refund policy")
                        && request.path("max_num_results").asInt() == 2
                        && "finance".equals(request.path("filters").path("value").asText()))
        )).thenReturn(searchPage());

        JsonNode bound = service.bindLocalVectorStores(1L, objectMapper.readTree("""
                {
                  "model": "gpt-4o-mini",
                  "instructions": "Answer from company docs.",
                  "input": "What is the refund policy?",
                  "tools": [
                    {
                      "type": "file_search",
                      "vector_store_ids": ["vs_1"],
                      "max_num_results": 2,
                      "filters": {"type": "eq", "key": "category", "value": "finance"}
                    },
                    {
                      "type": "function",
                      "name": "lookup_weather",
                      "parameters": {"type": "object"}
                    }
                  ],
                  "tool_choice": "auto"
                }
                """));

        assertTrue(bound.path("instructions").asText().contains("Local file_search context"));
        assertTrue(bound.path("instructions").asText().contains("refund policy allows quarterly credits"));
        assertEquals(1, bound.path("tools").size());
        assertEquals("function", bound.path("tools").path(0).path("type").asText());
        assertFalse(bound.toString().contains("\"type\":\"file_search\""));
    }

    @Test
    void shouldRejectMissingVectorStoreIds() throws Exception {
        OpenAiResponsesFileSearchBindingService service = new OpenAiResponsesFileSearchBindingService(
                Mockito.mock(GatewayAsyncResourceService.class),
                objectMapper
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.bindLocalVectorStores(1L, objectMapper.readTree("""
                        {
                          "model": "gpt-4o-mini",
                          "input": "search docs",
                          "tools": [{"type": "file_search"}]
                        }
                        """)));

        assertEquals("file_search.vector_store_ids 必须是非空数组。", exception.getMessage());
    }

    @Test
    void shouldRejectForcedFileSearchToolChoice() throws Exception {
        OpenAiResponsesFileSearchBindingService service = new OpenAiResponsesFileSearchBindingService(
                Mockito.mock(GatewayAsyncResourceService.class),
                objectMapper
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.bindLocalVectorStores(1L, objectMapper.readTree("""
                        {
                          "model": "gpt-4o-mini",
                          "input": "search docs",
                          "tools": [{"type": "file_search", "vector_store_ids": ["vs_1"]}],
                          "tool_choice": {"type": "file_search"}
                        }
                        """)));

        assertTrue(exception.getMessage().contains("不支持 tool_choice 强制 file_search"));
    }

    private JsonNode searchPage() {
        ObjectNode page = objectMapper.createObjectNode();
        page.put("object", "vector_store.search_results.page");
        page.putArray("data")
                .addObject()
                .put("file_id", "file_finance")
                .put("filename", "finance.txt")
                .put("score", 1.0d)
                .putArray("content")
                .addObject()
                .put("type", "text")
                .put("text", "The refund policy allows quarterly credits.");
        page.put("has_more", false);
        page.putNull("next_page");
        return page;
    }
}
