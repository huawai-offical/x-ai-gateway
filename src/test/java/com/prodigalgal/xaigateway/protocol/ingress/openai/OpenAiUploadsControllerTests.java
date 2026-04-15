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
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = OpenAiUploadsController.class)
@Import(PermitAllSecurityTestConfig.class)
class OpenAiUploadsControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;

    @MockitoBean
    private GatewayResourceExecutionService gatewayResourceExecutionService;

    @Test
    void shouldCreateUpload() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", "upload_1");
        response.put("object", "upload");
        response.put("status", "created");

        Mockito.when(gatewayTokenAuthenticationResolver.authenticate("Bearer sk-gw-test.secret", null, null, null))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayResourceExecutionService.executeLifecycleJson(Mockito.eq(1L), Mockito.eq("sk-gw-test"), Mockito.eq("POST"), Mockito.eq("/v1/uploads"), Mockito.eq("resource-orchestration"), Mockito.any()))
                .thenReturn(response);

        webTestClient.post()
                .uri("/v1/uploads")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "filename":"batch-input.jsonl",
                          "bytes":1234,
                          "purpose":"batch"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("upload")
                .jsonPath("$.status").isEqualTo("created");
    }

    @Test
    void shouldAddUploadPartWithMultipartData() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", "part_upstream_1");
        response.put("object", "upload.part");
        response.put("upload_id", "upload_1");

        Mockito.when(gatewayTokenAuthenticationResolver.authenticate("Bearer sk-gw-test.secret", null, null, null))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayResourceExecutionService.executeLifecycleMultipart(Mockito.eq(1L), Mockito.eq("sk-gw-test"), Mockito.eq("POST"), Mockito.eq("/v1/uploads/upload_1/parts"), Mockito.eq("resource-orchestration"), Mockito.anyMap(), Mockito.anyList(), Mockito.anyMap()))
                .thenReturn(Mono.just(response));

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("data", "hello".getBytes())
                .filename("part.bin")
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        webTestClient.post()
                .uri("/v1/uploads/upload_1/parts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("upload.part")
                .jsonPath("$.upload_id").isEqualTo("upload_1");
    }
}
