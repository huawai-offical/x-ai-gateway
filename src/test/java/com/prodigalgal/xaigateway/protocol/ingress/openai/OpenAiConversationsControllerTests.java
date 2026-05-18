package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@WebFluxTest(controllers = OpenAiConversationsController.class)
@Import(PermitAllSecurityTestConfig.class)
class OpenAiConversationsControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    @MockitoBean
    private GatewayAsyncResourceService gatewayAsyncResourceService;

    @Test
    void shouldCreateRetrieveUpdateAndDeleteConversation() throws Exception {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        JsonNode conversation = objectMapper.readTree("""
                {
                  "id": "conv_1",
                  "object": "conversation",
                  "created_at": 1778803200,
                  "metadata": {"topic": "demo"}
                }
                """);
        Mockito.when(gatewayAsyncResourceService.createConversation(Mockito.eq(1L), Mockito.any(JsonNode.class)))
                .thenReturn(conversation);
        Mockito.when(gatewayAsyncResourceService.getConversation("conv_1", 1L)).thenReturn(conversation);
        Mockito.when(gatewayAsyncResourceService.updateConversation(Mockito.eq("conv_1"), Mockito.eq(1L), Mockito.any(JsonNode.class)))
                .thenReturn(objectMapper.readTree("""
                        {
                          "id": "conv_1",
                          "object": "conversation",
                          "created_at": 1778803200,
                          "metadata": {"topic": "updated"}
                        }
                        """));
        Mockito.when(gatewayAsyncResourceService.deleteConversation("conv_1", 1L))
                .thenReturn(objectMapper.readTree("""
                        {"id":"conv_1","object":"conversation.deleted","deleted":true}
                        """));

        webTestClient.post()
                .uri("/v1/conversations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"metadata":{"topic":"demo"}}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("conv_1")
                .jsonPath("$.metadata.topic").isEqualTo("demo");

        webTestClient.get()
                .uri("/v1/conversations/conv_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("conversation");

        webTestClient.post()
                .uri("/v1/conversations/conv_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"metadata":{"topic":"updated"}}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.metadata.topic").isEqualTo("updated");

        webTestClient.delete()
                .uri("/v1/conversations/conv_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("conversation.deleted")
                .jsonPath("$.deleted").isEqualTo(true);
    }

    @Test
    void shouldCreateListRetrieveAndDeleteConversationItems() throws Exception {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        JsonNode list = objectMapper.readTree("""
                {
                  "object": "list",
                  "data": [
                    {"id":"msg_1","type":"message","role":"user","content":"hello","status":"completed"}
                  ],
                  "has_more": false,
                  "first_id": "msg_1",
                  "last_id": "msg_1"
                }
                """);
        Mockito.when(gatewayAsyncResourceService.createConversationItems(
                        Mockito.eq("conv_1"),
                        Mockito.eq(1L),
                        Mockito.any(JsonNode.class),
                        Mockito.eq(List.of("reasoning.encrypted_content"))
                ))
                .thenReturn(list);
        Mockito.when(gatewayAsyncResourceService.listConversationItems(
                        "conv_1",
                        1L,
                        "msg_prev",
                        List.of("message.input_image.image_url"),
                        10,
                        "asc"
                ))
                .thenReturn(list);
        Mockito.when(gatewayAsyncResourceService.getConversationItem("conv_1", "msg_1", 1L, List.of("message.input_image.image_url")))
                .thenReturn(list.path("data").path(0));
        Mockito.when(gatewayAsyncResourceService.deleteConversationItem("conv_1", "msg_1", 1L))
                .thenReturn(objectMapper.readTree("""
                        {"id":"conv_1","object":"conversation","created_at":1778803200,"metadata":{}}
                        """));

        webTestClient.post()
                .uri("/v1/conversations/conv_1/items?include=reasoning.encrypted_content")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"items":[{"role":"user","content":"hello"}]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.data[0].id").isEqualTo("msg_1");

        webTestClient.get()
                .uri("/v1/conversations/conv_1/items?after=msg_prev&limit=10&order=asc&include=message.input_image.image_url")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.first_id").isEqualTo("msg_1");

        webTestClient.get()
                .uri("/v1/conversations/conv_1/items/msg_1?include=message.input_image.image_url")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("msg_1");

        webTestClient.delete()
                .uri("/v1/conversations/conv_1/items/msg_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("conversation");
    }

    @Test
    void shouldReturnOpenAiStyleErrorForConversationPath() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayAsyncResourceService.listConversationItems(
                        Mockito.eq("conv_1"),
                        Mockito.eq(1L),
                        Mockito.<String>nullable(String.class),
                        Mockito.any(),
                        Mockito.eq(0),
                        Mockito.<String>nullable(String.class)
                ))
                .thenThrow(new IllegalArgumentException("limit 必须在 1 到 100 之间。"));

        webTestClient.get()
                .uri("/v1/conversations/conv_1/items?limit=0")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.code").isEqualTo("invalid_argument")
                .jsonPath("$.error.message").isEqualTo("limit 必须在 1 到 100 之间。");
    }
}
