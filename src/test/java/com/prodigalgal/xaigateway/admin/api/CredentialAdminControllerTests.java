package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.CredentialAdminService;
import com.prodigalgal.xaigateway.admin.application.UpstreamCredentialInventoryService;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = CredentialAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class CredentialAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CredentialAdminService credentialAdminService;

    @MockitoBean
    private UpstreamCredentialInventoryService upstreamCredentialInventoryService;

    @Test
    void shouldListAndGetCredential() {
        CredentialResponse response = credentialResponse(7L, "OpenAI Primary");
        Mockito.when(credentialAdminService.list()).thenReturn(List.of(response));
        Mockito.when(credentialAdminService.get(7L)).thenReturn(response);

        webTestClient.get()
                .uri("/admin/credentials")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].credentialName").isEqualTo("OpenAI Primary");

        webTestClient.get()
                .uri("/admin/credentials/7")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(7)
                .jsonPath("$.providerType").isEqualTo("OPENAI_DIRECT");
    }

    @Test
    void shouldListUnifiedCredentialInventory() {
        Mockito.when(upstreamCredentialInventoryService.list()).thenReturn(List.of(
                inventory("api-key:1", "API_KEY", "Gemini AI Studio 01", "GEMINI_DIRECT", 3L, "Gemini AI Studio"),
                inventory("account:2", "AUTH_JSON_ACCOUNT", "Codex 账号 01", "CODEX_OAUTH", 2L, "Codex")
        ));

        webTestClient.get()
                .uri("/admin/credentials/inventory")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].rowKey").isEqualTo("api-key:1")
                .jsonPath("$[0].sourceType").isEqualTo("API_KEY")
                .jsonPath("$[1].rowKey").isEqualTo("account:2")
                .jsonPath("$[1].groupName").isEqualTo("Codex");
    }

    @Test
    void shouldCreateAndToggleCredential() {
        Mockito.when(credentialAdminService.create(Mockito.any()))
                .thenReturn(credentialResponse(11L, "Gemini Key"));
        Mockito.when(credentialAdminService.toggle(11L, false))
                .thenReturn(credentialResponse(
                        11L,
                        "Gemini Key",
                        false,
                        ProviderType.GEMINI_DIRECT,
                        "https://generativelanguage.googleapis.com"
                ));

        webTestClient.post()
                .uri("/admin/credentials")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "credentialName":"Gemini Key",
                          "providerType":"GEMINI_DIRECT",
                          "baseUrl":"https://generativelanguage.googleapis.com",
                          "authKind":"API_KEY",
                          "secret":"token",
                          "siteProfileId":15,
                          "groupId":15
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.credentialName").isEqualTo("Gemini Key");

        webTestClient.post()
                .uri("/admin/credentials/11/status?active=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.active").isEqualTo(false);
    }

    @Test
    void shouldRunOpenAiDirectCredentialSmoke() {
        Mockito.when(credentialAdminService.openAiDirectSmoke(Mockito.eq(7L), Mockito.any()))
                .thenReturn(new OpenAiDirectSmokeResponse(
                        7L,
                        "DRY_RUN_READY",
                        "SKIPPED",
                        "DRY_RUN",
                        "GET",
                        "/v1/models",
                        "https://api.openai.com",
                        ProviderType.OPENAI_DIRECT,
                        true,
                        true,
                        null,
                        "fp",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        Instant.parse("2026-05-16T00:00:00Z"),
                        "OpenAI Direct credential dry-run smoke 已完成安全预检，未访问真实 OpenAI。",
                        Map.of("headers", Map.of("authorization", "Bearer ***"))
                ));

        webTestClient.post()
                .uri("/admin/credentials/7/openai-direct/smoke")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"dryRun":true,"organization":"org-real","project":"proj-real"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.classification").isEqualTo("SKIPPED")
                .jsonPath("$.skippedReason").isEqualTo("DRY_RUN")
                .jsonPath("$.path").isEqualTo("/v1/models")
                .jsonPath("$.requestPreview.headers.authorization").isEqualTo("Bearer ***");
    }

    @Test
    void shouldRunOpenAiDirectResourceSmoke() {
        Mockito.when(credentialAdminService.openAiDirectResourceSmoke(Mockito.eq(7L), Mockito.any()))
                .thenReturn(new OpenAiDirectResourceSmokeResponse(
                        7L,
                        "DRY_RUN_READY",
                        "SKIPPED",
                        "DRY_RUN",
                        "https://api.openai.com",
                        ProviderType.OPENAI_DIRECT,
                        true,
                        true,
                        null,
                        "fp",
                        Instant.parse("2026-05-16T00:00:00Z"),
                        "OpenAI Direct 资源族 smoke dry-run 已生成分类预览，未访问真实 OpenAI。",
                        Map.of("PASS", 0, "FAIL", 0, "SKIPPED", 1, "UNSUPPORTED", 0, "NO_PERMISSION", 0, "BUDGET_BLOCKED", 0),
                        List.of(new OpenAiDirectResourceSmokeItemResponse(
                                "FILES",
                                "DRY_RUN_READY",
                                "SKIPPED",
                                "DRY_RUN",
                                "GET",
                                "/v1/files?limit=1",
                                false,
                                false,
                                null,
                                null,
                                null,
                                null,
                                null,
                                Map.of("probeKind", "read_only_list"),
                                Map.of("headers", Map.of("authorization", "Bearer ***"))
                        ))
                ));

        webTestClient.post()
                .uri("/admin/credentials/7/openai-direct/resource-smoke")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"dryRun":true,"resourceFamilies":["files"]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.classification").isEqualTo("SKIPPED")
                .jsonPath("$.summary.SKIPPED").isEqualTo(1)
                .jsonPath("$.items[0].resourceFamily").isEqualTo("FILES")
                .jsonPath("$.items[0].requestPreview.headers.authorization").isEqualTo("Bearer ***");
    }

    @Test
    void shouldRunOpenAiDirectResourceSmokeCertification() {
        OpenAiDirectResourceSmokeResponse smoke = new OpenAiDirectResourceSmokeResponse(
                7L,
                "DRY_RUN_READY",
                "SKIPPED",
                "DRY_RUN",
                "https://api.openai.com",
                ProviderType.OPENAI_DIRECT,
                true,
                true,
                null,
                "fp",
                Instant.parse("2026-05-16T00:00:00Z"),
                "OpenAI Direct 资源族 smoke dry-run 已生成分类预览，未访问真实 OpenAI。",
                Map.of("PASS", 0, "FAIL", 0, "SKIPPED", 1, "UNSUPPORTED", 0, "NO_PERMISSION", 0, "BUDGET_BLOCKED", 0),
                List.of(new OpenAiDirectResourceSmokeItemResponse(
                        "FILES",
                        "DRY_RUN_READY",
                        "SKIPPED",
                        "DRY_RUN",
                        "GET",
                        "/v1/files?limit=1",
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of("probeKind", "read_only_list"),
                        Map.of("headers", Map.of("authorization", "Bearer ***"))
                ))
        );
        List<OpenAiDirectSmokeCertificationFixture> fixtureSnapshots = List.of(new OpenAiDirectSmokeCertificationFixture(
                "FILES",
                "DRY_RUN_READY",
                "SKIPPED",
                "DRY_RUN",
                "GET",
                "/v1/files?limit=1",
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                Map.of("probeKind", "read_only_list"),
                Map.of("headers", Map.of("authorization", "Bearer ***"))
        ));
        Mockito.when(credentialAdminService.openAiDirectResourceSmokeCertification(Mockito.eq(7L), Mockito.any()))
                .thenReturn(new OpenAiDirectSmokeCertificationResponse(
                        7L,
                        "DRY_RUN",
                        true,
                        Instant.parse("2026-05-16T00:00:00Z"),
                        smoke.summary(),
                        fixtureSnapshots,
                        new OpenAiDirectSmokeRecordReplayFixture(
                                "2026-05-16.openai-direct-smoke-record-replay.v1",
                                "record_replay",
                                "OPENAI_DIRECT",
                                "https://api.openai.com",
                                "DRY_RUN",
                                true,
                                Instant.parse("2026-05-16T00:00:00Z"),
                                smoke.summary(),
                                Map.of("network", "disabled_by_default", "billableOperations", "replay_only"),
                                fixtureSnapshots
                        ),
                        smoke
                ));

        webTestClient.post()
                .uri("/admin/credentials/7/openai-direct/resource-smoke/certification")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"dryRun":true,"resourceFamilies":["files"]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.certificationStatus").isEqualTo("DRY_RUN")
                .jsonPath("$.recordReplayFixture.schemaVersion").isEqualTo("2026-05-16.openai-direct-smoke-record-replay.v1")
                .jsonPath("$.fixtureSnapshots[0].resourceFamily").isEqualTo("FILES")
                .jsonPath("$.fixtureSnapshots[0].requestPreview.headers.authorization").isEqualTo("Bearer ***");
    }

    private CredentialResponse credentialResponse(Long id, String name) {
        return credentialResponse(id, name, true, ProviderType.OPENAI_DIRECT, "https://api.openai.com");
    }

    private CredentialResponse credentialResponse(
            Long id,
            String name,
            boolean active,
            ProviderType providerType,
            String baseUrl) {
        Instant now = Instant.now();
        return new CredentialResponse(
                id,
                name,
                providerType,
                baseUrl,
                CredentialAuthKind.API_KEY,
                List.of("gpt-4o"),
                "fp",
                Map.of("tenant", "prod"),
                active,
                null,
                null,
                null,
                null,
                now,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0D,
                0D,
                0L,
                0L,
                0D,
                0L,
                0L,
                0D,
                null,
                null,
                null,
                null,
                null,
                15L,
                21L,
                15L,
                "default",
                now,
                now
        );
    }

    private UpstreamCredentialInventoryResponse inventory(
            String rowKey,
            String sourceType,
            String displayName,
            String providerType,
            Long groupId,
            String groupName) {
        Instant now = Instant.now();
        return new UpstreamCredentialInventoryResponse(
                sourceType,
                rowKey.endsWith(":1") ? 1L : 2L,
                rowKey,
                displayName,
                providerType,
                sourceType.equals("API_KEY") ? "API_KEY" : "OAUTH_TOKEN",
                sourceType.equals("API_KEY") ? "https://example.com" : null,
                List.of("gpt-5.4"),
                sourceType.equals("API_KEY") ? "fp" : null,
                sourceType.equals("API_KEY") ? null : "codex:user",
                Map.of(),
                true,
                sourceType.equals("API_KEY") ? null : false,
                sourceType.equals("API_KEY") ? null : true,
                sourceType.equals("API_KEY") ? null : "READY",
                sourceType.equals("API_KEY") ? null : 0,
                null,
                null,
                null,
                null,
                now,
                sourceType.equals("API_KEY") ? null : now,
                sourceType.equals("API_KEY") ? null : now,
                null,
                null,
                null,
                null,
                null,
                groupId,
                groupName,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0D,
                0D,
                0L,
                0L,
                0D,
                0L,
                0L,
                0D,
                null,
                null,
                null,
                now,
                now
        );
    }
}
