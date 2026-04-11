package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.SystemSettingsAdminService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = SystemSettingsAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class SystemSettingsAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private SystemSettingsAdminService systemSettingsAdminService;

    @Test
    void shouldReturnCurrentSettings() {
        Mockito.when(systemSettingsAdminService.get()).thenReturn(response());

        webTestClient.get()
                .uri("/admin/settings")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.upstreamCache.enabled").isEqualTo(true)
                .jsonPath("$.upstreamCache.keyPrefix").isEqualTo("xag");
    }

    @Test
    void shouldUpdateSettings() {
        Mockito.when(systemSettingsAdminService.save(Mockito.any())).thenReturn(response());

        webTestClient.put()
                .uri("/admin/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "upstreamCache": {
                            "enabled": true,
                            "stickyByDistributedKey": true,
                            "prefixAffinityEnabled": true,
                            "fingerprintAffinityEnabled": true,
                            "affinityTtl": "PT30M",
                            "fingerprintMaxPrefixTokens": 1024,
                            "keyPrefix": "xag"
                          }
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.upstream.updatedAt").doesNotExist();
    }

    private SystemSettingsResponse response() {
        return new SystemSettingsResponse(
                new SystemSettingsResponse.UpstreamCacheSettingsResponse(
                        true,
                        true,
                        true,
                        true,
                        "PT30M",
                        1024,
                        "xag"
                ),
                new SystemSettingsResponse.UpstreamRuntimeSettingsResponse(
                        180000,
                        600000,
                        180000,
                        600000
                ),
                Instant.parse("2026-04-07T08:00:00Z")
        );
    }
}
