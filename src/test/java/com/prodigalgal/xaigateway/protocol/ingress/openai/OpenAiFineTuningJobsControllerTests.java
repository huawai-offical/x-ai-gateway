package com.prodigalgal.xaigateway.protocol.ingress.openai;

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

@WebFluxTest(controllers = OpenAiFineTuningJobsController.class)
@Import(PermitAllSecurityTestConfig.class)
class OpenAiFineTuningJobsControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;

    @MockitoBean
    private GatewayResourceExecutionService gatewayResourceExecutionService;

    @Test
    void shouldCreateTuningJob() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", "ftjob_1");
        response.put("object", "fine_tuning.job");
        response.put("status", "queued");

        Mockito.when(gatewayTokenAuthenticationResolver.authenticate("Bearer sk-gw-test.secret", null, null, null))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayResourceExecutionService.executeLifecycleJson(Mockito.eq(1L), Mockito.eq("sk-gw-test"), Mockito.eq("POST"), Mockito.eq("/v1/fine_tuning/jobs"), Mockito.eq("resource-orchestration"), Mockito.any()))
                .thenReturn(response);

        webTestClient.post()
                .uri("/v1/fine_tuning/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"gpt-4o-mini",
                          "training_file":"file_train_1"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("fine_tuning.job")
                .jsonPath("$.status").isEqualTo("queued");
    }

    @Test
    void shouldListTuningJobs() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "list");
        response.putArray("data");

        Mockito.when(gatewayTokenAuthenticationResolver.authenticate("Bearer sk-gw-test.secret", null, null, null))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayResourceExecutionService.executeLifecycleJson(1L, "sk-gw-test", "GET", "/v1/fine_tuning/jobs", "resource-orchestration", null))
                .thenReturn(response);

        webTestClient.get()
                .uri("/v1/fine_tuning/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.data").isArray();
    }

    @Test
    void shouldGetTuningJob() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", "ftjob_1");
        response.put("object", "fine_tuning.job");
        response.put("status", "running");

        Mockito.when(gatewayTokenAuthenticationResolver.authenticate("Bearer sk-gw-test.secret", null, null, null))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayResourceExecutionService.executeLifecycleJson(1L, "sk-gw-test", "GET", "/v1/fine_tuning/jobs/ftjob_1", "resource-orchestration", null))
                .thenReturn(response);

        webTestClient.get()
                .uri("/v1/fine_tuning/jobs/ftjob_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("ftjob_1")
                .jsonPath("$.status").isEqualTo("running");
    }

    @Test
    void shouldCancelTuningJob() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", "ftjob_1");
        response.put("object", "fine_tuning.job");
        response.put("status", "cancelled");

        Mockito.when(gatewayTokenAuthenticationResolver.authenticate("Bearer sk-gw-test.secret", null, null, null))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayResourceExecutionService.executeLifecycleJson(1L, "sk-gw-test", "POST", "/v1/fine_tuning/jobs/ftjob_1/cancel", "resource-orchestration", null))
                .thenReturn(response);

        webTestClient.post()
                .uri("/v1/fine_tuning/jobs/ftjob_1/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("ftjob_1")
                .jsonPath("$.status").isEqualTo("cancelled");
    }
}
