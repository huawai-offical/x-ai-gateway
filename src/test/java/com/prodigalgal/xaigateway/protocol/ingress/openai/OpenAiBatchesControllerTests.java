package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = OpenAiBatchesController.class)
@Import(PermitAllSecurityTestConfig.class)
class OpenAiBatchesControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;

    @MockitoBean
    private GatewayResourceExecutionService gatewayResourceExecutionService;

    @Test
    void shouldListBatchesWithPaginationQuery() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "list");
        response.putArray("data")
                .addObject()
                .put("id", "batch_1")
                .put("object", "batch");
        response.put("has_more", false);

        Mockito.when(gatewayTokenAuthenticationResolver.authenticate("Bearer sk-gw-test.secret", null, null, null))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayResourceExecutionService.executeLifecycleJson(Mockito.eq(1L), Mockito.eq("sk-gw-test"), Mockito.eq("GET"), Mockito.eq("/v1/batches"), Mockito.eq("resource-orchestration"), Mockito.any()))
                .thenReturn(response);

        webTestClient.get()
                .uri("/v1/batches?limit=1&after=batch_prev")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.data[0].id").isEqualTo("batch_1")
                .jsonPath("$.has_more").isEqualTo(false);

        var captor = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
        Mockito.verify(gatewayResourceExecutionService).executeLifecycleJson(
                Mockito.eq(1L),
                Mockito.eq("sk-gw-test"),
                Mockito.eq("GET"),
                Mockito.eq("/v1/batches"),
                Mockito.eq("resource-orchestration"),
                captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("1", captor.getValue().path("limit").asText());
        org.junit.jupiter.api.Assertions.assertEquals("batch_prev", captor.getValue().path("after").asText());
    }

    @Test
    void shouldCreateBatch() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", "batch_1");
        response.put("object", "batch");
        response.put("status", "validating");

        Mockito.when(gatewayTokenAuthenticationResolver.authenticate("Bearer sk-gw-test.secret", null, null, null))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayResourceExecutionService.executeLifecycleJson(Mockito.eq(1L), Mockito.eq("sk-gw-test"), Mockito.eq("POST"), Mockito.eq("/v1/batches"), Mockito.eq("resource-orchestration"), Mockito.any()))
                .thenReturn(response);

        webTestClient.post()
                .uri("/v1/batches")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "input_file_id":"file_1",
                          "endpoint":"/v1/chat/completions",
                          "completion_window":"24h"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("batch")
                .jsonPath("$.status").isEqualTo("validating");
    }

    @Test
    void shouldGetBatch() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", "batch_1");
        response.put("object", "batch");
        response.put("status", "in_progress");

        Mockito.when(gatewayTokenAuthenticationResolver.authenticate("Bearer sk-gw-test.secret", null, null, null))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayResourceExecutionService.executeLifecycleJson(1L, "sk-gw-test", "GET", "/v1/batches/batch_1", "resource-orchestration", null))
                .thenReturn(response);

        webTestClient.get()
                .uri("/v1/batches/batch_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("batch_1")
                .jsonPath("$.status").isEqualTo("in_progress");
    }

    @Test
    void shouldCancelBatch() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", "batch_1");
        response.put("object", "batch");
        response.put("status", "cancelling");

        Mockito.when(gatewayTokenAuthenticationResolver.authenticate("Bearer sk-gw-test.secret", null, null, null))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayResourceExecutionService.executeLifecycleJson(1L, "sk-gw-test", "POST", "/v1/batches/batch_1/cancel", "resource-orchestration", null))
                .thenReturn(response);

        webTestClient.post()
                .uri("/v1/batches/batch_1/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("batch_1")
                .jsonPath("$.status").isEqualTo("cancelling");
    }
}
