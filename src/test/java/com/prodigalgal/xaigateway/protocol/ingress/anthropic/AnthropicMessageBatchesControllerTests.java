package com.prodigalgal.xaigateway.protocol.ingress.anthropic;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.canonical.AnthropicNativeNonChatCanonicalRenderer;
import com.prodigalgal.xaigateway.gateway.core.canonical.NonChatCanonicalRenderService;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionResult;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@WebFluxTest(controllers = AnthropicMessageBatchesController.class)
@Import({
        PermitAllSecurityTestConfig.class,
        AnthropicMessageBatchesRequestMapper.class,
        AnthropicMessageBatchesEncoder.class,
        AnthropicNativeNonChatCanonicalRenderer.class,
        NonChatCanonicalRenderService.class
})
class AnthropicMessageBatchesControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    @MockitoBean
    private GatewayResourceExecutionService gatewayResourceExecutionService;

    @Test
    void shouldCreateAnthropicNativeMessageBatch() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("id", "msgbatch_123");
        response.put("object", "message_batch");
        response.put("processing_status", "in_progress");
        response.put("status", "running");
        Mockito.when(gatewayResourceExecutionService.executeDetailedJson(Mockito.any(), Mockito.eq(1L), Mockito.eq("claude-sonnet-4")))
                .thenReturn(GatewayResourceExecutionResult.json(
                        ResponseEntity.ok(response),
                        new CanonicalResourceResponse(null, null, null, null, null, null, List.of(), List.of(), response, null, java.util.Map.of())
                ));

        webTestClient.post()
                .uri("/v1/messages/batches")
                .header("x-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "requests": [
                            {
                              "custom_id": "req-1",
                              "params": {
                                "model": "claude-sonnet-4",
                                "max_tokens": 256,
                                "messages": [
                                  {"role":"user","content":"hello"}
                                ]
                              }
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("msgbatch_123")
                .jsonPath("$.object").isEqualTo("message_batch")
                .jsonPath("$.status").isEqualTo("running");
    }

    @Test
    void shouldGetAndCancelAnthropicNativeMessageBatch() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode getResponse = mapper.createObjectNode();
        getResponse.put("id", "msgbatch_123");
        getResponse.put("object", "message_batch");
        getResponse.put("processing_status", "ended");
        getResponse.put("status", "completed");
        ObjectNode cancelResponse = mapper.createObjectNode();
        cancelResponse.put("id", "msgbatch_123");
        cancelResponse.put("object", "message_batch");
        cancelResponse.put("processing_status", "canceled");
        cancelResponse.put("status", "cancelled");

        Mockito.when(gatewayResourceExecutionService.executeDetailedJson(Mockito.any(), Mockito.eq(1L), Mockito.eq("resource-orchestration")))
                .thenReturn(
                        GatewayResourceExecutionResult.json(
                                ResponseEntity.ok(getResponse),
                                new CanonicalResourceResponse(null, null, null, null, null, null, List.of(), List.of(), getResponse, null, java.util.Map.of())
                        ),
                        GatewayResourceExecutionResult.json(
                                ResponseEntity.ok(cancelResponse),
                                new CanonicalResourceResponse(null, null, null, null, null, null, List.of(), List.of(), cancelResponse, null, java.util.Map.of())
                        )
                );

        webTestClient.get()
                .uri("/v1/messages/batches/msgbatch_123")
                .header("x-api-key", "sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("msgbatch_123")
                .jsonPath("$.status").isEqualTo("completed");

        webTestClient.post()
                .uri("/v1/messages/batches/msgbatch_123/cancel")
                .header("x-api-key", "sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("msgbatch_123")
                .jsonPath("$.status").isEqualTo("cancelled");
    }
}
