package com.prodigalgal.xaigateway.protocol.ingress.publicapi;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayCacheResourceService;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayPublicResourceService;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import org.junit.jupiter.api.BeforeEach;
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
import tools.jackson.databind.node.ObjectNode;

@WebFluxTest(controllers = {
        GatewayCachesController.class,
        GatewayResourceLineageController.class,
        GatewayOperationsController.class,
        GatewayTuningsController.class
})
@Import(PermitAllSecurityTestConfig.class)
class GatewayPublicResourceControllersTests {

    private static final String AUTHORIZATION = "Bearer sk-gw-test.secret";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;

    @MockitoBean
    private GatewayCacheResourceService gatewayCacheResourceService;

    @MockitoBean
    private GatewayPublicResourceService gatewayPublicResourceService;

    @BeforeEach
    void setUp() {
        Mockito.when(gatewayTokenAuthenticationResolver.authenticate(AUTHORIZATION, null, null, null))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
    }

    @Test
    void shouldListGatewayCaches() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "list");
        response.putArray("data").add(cacheNode("cache_7"));

        Mockito.when(gatewayCacheResourceService.list(1L, ProviderType.GEMINI_DIRECT, "ACTIVE"))
                .thenReturn(response);

        webTestClient.get()
                .uri("/api/v1/caches?providerType=GEMINI_DIRECT&status=ACTIVE")
                .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.data[0].id").isEqualTo("cache_7");
    }

    @Test
    void shouldImportGatewayCache() {
        ObjectNode response = cacheNode("cache_8");

        Mockito.when(gatewayCacheResourceService.importCache(Mockito.eq(1L), Mockito.any(JsonNode.class)))
                .thenReturn(response);

        webTestClient.post()
                .uri("/api/v1/caches/import")
                .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "providerType": "GEMINI_DIRECT",
                          "credentialId": 11,
                          "modelGroup": "gemini-2.5-pro",
                          "prefixHash": "prefix-1",
                          "externalCacheRef": "cachedContents/abc"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("gateway.cache")
                .jsonPath("$.id").isEqualTo("cache_8");
    }

    @Test
    void shouldDeleteGatewayCache() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "gateway.cache.deleted");
        response.put("id", "cache_8");
        response.put("deleted", true);

        Mockito.when(gatewayCacheResourceService.delete(1L, "cache_8"))
                .thenReturn(response);

        webTestClient.delete()
                .uri("/api/v1/caches/cache_8")
                .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.deleted").isEqualTo(true);
    }

    @Test
    void shouldReturnResourceLineage() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "resource.lineage");
        response.put("root", "gateway:ftjob_1");
        response.putArray("nodes").addObject().put("id", "gateway:ftjob_1");
        response.putArray("edges");

        Mockito.when(gatewayPublicResourceService.lineage(1L, "tunings", "ftjob_1"))
                .thenReturn(response);

        webTestClient.get()
                .uri("/api/v1/resources/tunings/ftjob_1/lineage")
                .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("resource.lineage")
                .jsonPath("$.root").isEqualTo("gateway:ftjob_1");
    }

    @Test
    void shouldListOperations() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "list");
        response.putArray("data").addObject()
                .put("name", "operations/ftjob_1")
                .put("done", false);

        Mockito.when(gatewayPublicResourceService.listOperations(1L, "tunings", "running"))
                .thenReturn(response);

        webTestClient.get()
                .uri("/api/v1/operations?resourceType=tunings&status=running")
                .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].name").isEqualTo("operations/ftjob_1");
    }

    @Test
    void shouldCancelOperation() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "operation");
        response.put("name", "operations/ftjob_1");
        response.put("done", true);

        Mockito.when(gatewayPublicResourceService.cancelOperation(1L, "ftjob_1"))
                .thenReturn(response);

        webTestClient.post()
                .uri("/api/v1/operations/ftjob_1:cancel")
                .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.done").isEqualTo(true);
    }

    @Test
    void shouldWaitOperation() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "operation");
        response.put("name", "operations/ftjob_1");
        response.put("waited", true);

        Mockito.when(gatewayPublicResourceService.waitOperation(1L, "ftjob_1"))
                .thenReturn(response);

        webTestClient.post()
                .uri("/api/v1/operations/ftjob_1:wait")
                .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.waited").isEqualTo(true);
    }

    @Test
    void shouldDeleteOperation() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "operation");
        response.put("name", "operations/ftjob_1");
        response.put("deleted", true);

        Mockito.when(gatewayPublicResourceService.deleteOperation(1L, "ftjob_1"))
                .thenReturn(response);

        webTestClient.delete()
                .uri("/api/v1/operations/ftjob_1")
                .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.deleted").isEqualTo(true);
    }

    @Test
    void shouldInvalidateGatewayCache() {
        ObjectNode response = cacheNode("cache_8");
        response.put("invalidated", true);

        Mockito.when(gatewayCacheResourceService.invalidate(1L, "cache_8"))
                .thenReturn(response);

        webTestClient.post()
                .uri("/api/v1/caches/cache_8:invalidate")
                .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.invalidated").isEqualTo(true);
    }

    @Test
    void shouldTouchGatewayCache() {
        ObjectNode response = cacheNode("cache_8");
        response.put("active", true);

        Mockito.when(gatewayCacheResourceService.touch(1L, "cache_8"))
                .thenReturn(response);

        webTestClient.post()
                .uri("/api/v1/caches/cache_8:touch")
                .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.active").isEqualTo(true);
    }

    @Test
    void shouldCreateTuning() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", "ftjob_1");
        response.put("object", "fine_tuning.job");
        response.put("status", "queued");

        Mockito.when(gatewayPublicResourceService.createTuning(Mockito.eq(1L), Mockito.any(JsonNode.class)))
                .thenReturn(response);

        webTestClient.post()
                .uri("/api/v1/tunings")
                .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "gemini-2.5-pro",
                          "training_file": "file_train_1"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("ftjob_1")
                .jsonPath("$.status").isEqualTo("queued");
    }

    @Test
    void shouldImportTuningModel() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "tuning.import_result");
        response.put("fine_tuned_model", "tunedModels/demo");
        response.putArray("aliases").add("demo");

        Mockito.when(gatewayPublicResourceService.importTuning(Mockito.eq(1L), Mockito.eq("ftjob_1"), Mockito.any(JsonNode.class)))
                .thenReturn(response);

        webTestClient.post()
                .uri("/api/v1/tunings/ftjob_1:import")
                .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"alias\":\"demo\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("tuning.import_result")
                .jsonPath("$.aliases[0]").isEqualTo("demo");
    }

    private ObjectNode cacheNode(String id) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("object", "gateway.cache");
        node.put("external_cache_ref", "cachedContents/abc");
        node.put("status", "ACTIVE");
        return node;
    }
}
