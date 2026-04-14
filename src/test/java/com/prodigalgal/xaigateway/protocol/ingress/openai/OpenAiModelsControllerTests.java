package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.catalog.GatewayPublicModelView;
import com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = OpenAiModelsController.class)
@Import(PermitAllSecurityTestConfig.class)
class OpenAiModelsControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    @MockitoBean
    private DistributedKeyQueryService distributedKeyQueryService;

    @MockitoBean
    private ModelCatalogQueryService modelCatalogQueryService;

    @Test
    void shouldListAccessibleModels() {
        DistributedKeyView distributedKeyView = new DistributedKeyView(
                1L,
                "test-key",
                "sk-gw-test",
                "masked",
                List.of("openai"),
                List.of(),
                List.of()
        );
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(distributedKeyQueryService.findActiveByKeyPrefix("sk-gw-test"))
                .thenReturn(Optional.of(distributedKeyView));
        Mockito.when(modelCatalogQueryService.listAccessiblePublicModels(distributedKeyView, "openai"))
                .thenReturn(List.of(
                        new GatewayPublicModelView("gpt-4o", "gpt-4o", false),
                        new GatewayPublicModelView("writer-fast", "writer-fast", true)
                ));

        webTestClient.get()
                .uri("/v1/models")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.data[0].id").isEqualTo("gpt-4o")
                .jsonPath("$.data[0].owned_by").isEqualTo("x-ai-gateway")
                .jsonPath("$.data[1].id").isEqualTo("writer-fast")
                .jsonPath("$.data[1].owned_by").isEqualTo("x-ai-gateway-alias");
    }

    @Test
    void shouldGetAccessibleModelDetail() {
        DistributedKeyView distributedKeyView = new DistributedKeyView(
                1L,
                "test-key",
                "sk-gw-test",
                "masked",
                List.of("openai"),
                List.of(),
                List.of()
        );
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(distributedKeyQueryService.findActiveByKeyPrefix("sk-gw-test"))
                .thenReturn(Optional.of(distributedKeyView));
        Mockito.when(modelCatalogQueryService.findAccessiblePublicModel(distributedKeyView, "openai", "writer-fast"))
                .thenReturn(Optional.of(new GatewayPublicModelView("writer-fast", "writer-fast", true)));

        webTestClient.get()
                .uri("/v1/models/writer-fast")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("writer-fast")
                .jsonPath("$.object").isEqualTo("model")
                .jsonPath("$.owned_by").isEqualTo("x-ai-gateway-alias");
    }

    @Test
    void shouldReturn404WhenModelIsNotAccessible() {
        DistributedKeyView distributedKeyView = new DistributedKeyView(
                1L,
                "test-key",
                "sk-gw-test",
                "masked",
                List.of("openai"),
                List.of(),
                List.of()
        );
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(distributedKeyQueryService.findActiveByKeyPrefix("sk-gw-test"))
                .thenReturn(Optional.of(distributedKeyView));
        Mockito.when(modelCatalogQueryService.findAccessiblePublicModel(distributedKeyView, "openai", "missing-model"))
                .thenReturn(Optional.empty());

        webTestClient.get()
                .uri("/v1/models/missing-model")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.message").isEqualTo("未找到指定模型。");
    }
}
