package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.UserAdminService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = UserAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class UserAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private UserAdminService userAdminService;

    @Test
    void shouldListGetAndCreateUser() {
        GatewayUserResponse response = userResponse(9L, "alpha@example.com", true);
        Mockito.when(userAdminService.list("alpha", true)).thenReturn(List.of(response));
        Mockito.when(userAdminService.get(9L)).thenReturn(response);
        Mockito.when(userAdminService.create(Mockito.any())).thenReturn(response);

        webTestClient.get()
                .uri("/admin/users?keyword=alpha&active=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].email").isEqualTo("alpha@example.com")
                .jsonPath("$[0].subscriptionCount").isEqualTo(3);

        webTestClient.get()
                .uri("/admin/users/9")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(9);

        webTestClient.post()
                .uri("/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "email":"alpha@example.com",
                          "displayName":"Alpha",
                          "active":true
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.displayName").isEqualTo("Alpha");
    }

    @Test
    void shouldUpdateAndDeleteUser() {
        GatewayUserResponse response = userResponse(10L, "beta@example.com", false);
        Mockito.when(userAdminService.update(Mockito.eq(10L), Mockito.any())).thenReturn(response);

        webTestClient.put()
                .uri("/admin/users/10")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "email":"beta@example.com",
                          "displayName":"Beta",
                          "active":false
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.active").isEqualTo(false);

        webTestClient.delete()
                .uri("/admin/users/10")
                .exchange()
                .expectStatus().isOk();
        Mockito.verify(userAdminService).delete(10L);
    }

    private GatewayUserResponse userResponse(Long id, String email, boolean active) {
        Instant now = Instant.now();
        return new GatewayUserResponse(
                id,
                email,
                active ? "Alpha" : "Beta",
                active,
                3L,
                now,
                "notes",
                now,
                now
        );
    }
}
