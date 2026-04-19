package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.RecoveryCheckpointService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.List;

@WebFluxTest(controllers = OperationsRecoveryCheckpointAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class OperationsRecoveryCheckpointAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private RecoveryCheckpointService recoveryCheckpointService;

    @Test
    void shouldListAndVerifyCheckpoints() {
        Mockito.when(recoveryCheckpointService.list()).thenReturn(List.of(sampleResponse("READY")));
        Mockito.when(recoveryCheckpointService.verify(Mockito.eq(7L), Mockito.eq("ops"))).thenReturn(sampleResponse("VERIFIED"));

        webTestClient.get()
                .uri("/admin/operations/recovery-checkpoints")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].checkpointName").isEqualTo("cp-7");

        webTestClient.post()
                .uri("/admin/operations/recovery-checkpoints/7/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "verifiedBy":"ops"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.verificationStatus").isEqualTo("VERIFIED");
    }

    private RecoveryCheckpointResponse sampleResponse(String verificationStatus) {
        return new RecoveryCheckpointResponse(
                7L,
                "cp-7",
                2L,
                "READY",
                "meta.json",
                "runtime.json",
                "data.json",
                "{}",
                verificationStatus,
                "ok",
                Instant.now(),
                "ops",
                Instant.now(),
                Instant.now()
        );
    }
}
