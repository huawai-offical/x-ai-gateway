package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OpenAiDirectSmokeRequest;
import com.prodigalgal.xaigateway.admin.api.OpenAiDirectResourceSmokeRequest;
import com.prodigalgal.xaigateway.gateway.core.catalog.CredentialModelDiscoveryService;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountPoolRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialAdminServiceTests {

    @Test
    void shouldBuildOpenAiDirectSmokeDryRunWithoutDecryptingSecret() {
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        CredentialAdminService service = service(credentialRepository, cryptoService);
        UpstreamCredentialEntity credential = credential(7L, ProviderType.OPENAI_DIRECT);
        Mockito.when(credentialRepository.findById(7L)).thenReturn(Optional.of(credential));

        var response = service.openAiDirectSmoke(7L, new OpenAiDirectSmokeRequest(
                null,
                null,
                null,
                "org-real",
                "proj-real"
        ));

        assertEquals("DRY_RUN_READY", response.status());
        assertEquals("SKIPPED", response.classification());
        assertEquals("DRY_RUN", response.skippedReason());
        assertEquals("/v1/models", response.path());
        assertEquals("https://api.openai.com", response.baseUrl());
        assertTrue(response.routeEligible());
        assertEquals("fingerprint-openai", response.credentialFingerprint());
        String preview = response.requestPreview().toString();
        assertTrue(preview.contains("Bearer ***"));
        assertFalse(preview.contains("org-real"));
        assertFalse(preview.contains("proj-real"));
        Mockito.verify(cryptoService, Mockito.never()).decrypt(Mockito.anyString());
        Mockito.verify(credentialRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void shouldExecuteOpenAiDirectModelsProbeAndPersistSafeSuccess() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/models", exchange -> {
            exchange.getResponseHeaders().add("x-request-id", "req-openai-models");
            sendJson(exchange, 200, """
                    {"object":"list","data":[{"id":"gpt-5.4","object":"model"}]}
                    """);
        });
        server.start();
        try {
            UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
            CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
            CredentialAdminService service = service(credentialRepository, cryptoService);
            UpstreamCredentialEntity credential = credential(8L, ProviderType.OPENAI_DIRECT);
            credential.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            Mockito.when(credentialRepository.findById(8L)).thenReturn(Optional.of(credential));
            Mockito.when(credentialRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
            Mockito.when(cryptoService.decrypt("cipher-openai")).thenReturn("sk-live-secret");

            var response = service.openAiDirectSmoke(8L, new OpenAiDirectSmokeRequest(
                    false,
                    null,
                    3,
                    null,
                    null
            ));

            assertEquals("LIVE_SMOKE_OK", response.status());
            assertEquals("PASS", response.classification());
            assertEquals(200, response.httpStatus());
            assertEquals("req-openai-models", response.upstreamRequestId());
            assertEquals(1, response.modelsCount());
            assertEquals("gpt-5.4", response.sampleModels().getFirst());
            assertNotNull(credential.getLastUsedAt());
            assertEquals(null, credential.getLastErrorCode());
            Mockito.verify(cryptoService).decrypt("cipher-openai");
            Mockito.verify(credentialRepository).save(credential);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldClassifyOpenAiDirectRateLimitAsBudgetBlockedAndRedactSecret() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/models", exchange -> sendJson(exchange, 429, """
                {"error":{"type":"rate_limit_error","message":"rate limited Bearer sk-live-secret"}}
                """));
        server.start();
        try {
            UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
            CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
            CredentialAdminService service = service(credentialRepository, cryptoService);
            UpstreamCredentialEntity credential = credential(9L, ProviderType.OPENAI_DIRECT);
            credential.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            Mockito.when(credentialRepository.findById(9L)).thenReturn(Optional.of(credential));
            Mockito.when(credentialRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
            Mockito.when(cryptoService.decrypt("cipher-openai")).thenReturn("sk-live-secret");

            var response = service.openAiDirectSmoke(9L, new OpenAiDirectSmokeRequest(false, null, 3, null, null));

            assertEquals("LIVE_SMOKE_FAILED", response.status());
            assertEquals("BUDGET_BLOCKED", response.classification());
            assertEquals("rate_limit_error", response.skippedReason());
            assertEquals(429, response.httpStatus());
            assertFalse(response.failureMessage().contains("sk-live-secret"));
            assertFalse(credential.getLastErrorMessage().contains("sk-live-secret"));
            assertEquals("rate_limit_error", credential.getLastErrorCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRejectNonOpenAiDirectCredentialWithoutDecryptingSecret() {
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        CredentialAdminService service = service(credentialRepository, cryptoService);
        UpstreamCredentialEntity credential = credential(10L, ProviderType.GEMINI_DIRECT);
        Mockito.when(credentialRepository.findById(10L)).thenReturn(Optional.of(credential));

        var response = service.openAiDirectSmoke(10L, new OpenAiDirectSmokeRequest(false, null, 3, null, null));

        assertEquals("ROUTE_BLOCKED", response.status());
        assertEquals("UNSUPPORTED", response.classification());
        assertEquals("PROVIDER_NOT_OPENAI_DIRECT", response.skippedReason());
        assertFalse(response.routeEligible());
        Mockito.verify(cryptoService, Mockito.never()).decrypt(Mockito.anyString());
    }

    @Test
    void shouldBuildOpenAiDirectResourceSmokeDryRunForAllFamiliesWithoutDecryptingSecret() {
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        CredentialAdminService service = service(credentialRepository, cryptoService);
        UpstreamCredentialEntity credential = credential(12L, ProviderType.OPENAI_DIRECT);
        Mockito.when(credentialRepository.findById(12L)).thenReturn(Optional.of(credential));

        var response = service.openAiDirectResourceSmoke(12L, new OpenAiDirectResourceSmokeRequest(
                true,
                null,
                null,
                "org-real",
                "proj-real",
                null
        ));

        assertEquals("DRY_RUN_READY", response.status());
        assertEquals("SKIPPED", response.classification());
        assertEquals("DRY_RUN", response.skippedReason());
        assertEquals(6, response.items().size());
        assertEquals(6, response.summary().get("SKIPPED"));
        assertTrue(response.items().stream().anyMatch(item -> "CHAT_COMPLETIONS".equals(item.resourceFamily()) && item.billable()));
        assertTrue(response.items().stream().anyMatch(item -> "REALTIME_CLIENT_SECRET".equals(item.resourceFamily()) && item.writeOperation()));
        assertFalse(response.toString().contains("org-real"));
        assertFalse(response.toString().contains("proj-real"));
        Mockito.verify(cryptoService, Mockito.never()).decrypt(Mockito.anyString());
        Mockito.verify(credentialRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void shouldExecuteOpenAiDirectResourceReadOnlyProbesAndBlockBillableFamilies() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/files", exchange -> sendJson(exchange, 200, """
                {"object":"list","data":[{"id":"file_1","object":"file"}]}
                """));
        server.createContext("/v1/batches", exchange -> sendJson(exchange, 429, """
                {"error":{"type":"rate_limit_error","message":"limited Bearer sk-live-secret"}}
                """));
        server.createContext("/v1/vector_stores", exchange -> sendJson(exchange, 403, """
                {"error":{"type":"permission_denied","message":"missing vector permission"}}
                """));
        server.start();
        try {
            UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
            CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
            CredentialAdminService service = service(credentialRepository, cryptoService);
            UpstreamCredentialEntity credential = credential(13L, ProviderType.OPENAI_DIRECT);
            credential.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            Mockito.when(credentialRepository.findById(13L)).thenReturn(Optional.of(credential));
            Mockito.when(credentialRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
            Mockito.when(cryptoService.decrypt("cipher-openai")).thenReturn("sk-live-secret");

            var response = service.openAiDirectResourceSmoke(13L, new OpenAiDirectResourceSmokeRequest(
                    false,
                    null,
                    3,
                    null,
                    null,
                    null
            ));

            assertEquals("LIVE_SMOKE_COMPLETED", response.status());
            assertEquals("NO_PERMISSION", response.classification());
            assertEquals(1, response.summary().get("PASS"));
            assertEquals(4, response.summary().get("BUDGET_BLOCKED"));
            assertEquals(1, response.summary().get("NO_PERMISSION"));
            assertTrue(response.items().stream().anyMatch(item -> "FILES".equals(item.resourceFamily()) && "PASS".equals(item.classification())));
            assertTrue(response.items().stream().anyMatch(item -> "BATCHES".equals(item.resourceFamily()) && "BUDGET_BLOCKED".equals(item.classification())));
            assertTrue(response.items().stream().anyMatch(item -> "VECTOR_STORES".equals(item.resourceFamily()) && "NO_PERMISSION".equals(item.classification())));
            assertFalse(response.toString().contains("sk-live-secret"));
            assertFalse(credential.getLastErrorMessage().contains("sk-live-secret"));
            Mockito.verify(cryptoService).decrypt("cipher-openai");
            Mockito.verify(credentialRepository).save(credential);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPropagateExplicitBillableAndWriteProbeAllowFlags() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> chatBody = new AtomicReference<>();
        AtomicReference<String> realtimeBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            chatBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, 200, """
                    {"id":"chatcmpl_1","object":"chat.completion","model":"gpt-4o-mini","usage":{"completion_tokens":1}}
                    """);
        });
        server.createContext("/v1/realtime/client_secrets", exchange -> {
            realtimeBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, 200, """
                    {"client_secret":{"value":"ek_secret","expires_at":1893456000},"session":{"type":"realtime","model":"gpt-realtime-mini"}}
                    """);
        });
        server.start();
        try {
            UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
            CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
            CredentialAdminService service = service(credentialRepository, cryptoService);
            UpstreamCredentialEntity credential = credential(16L, ProviderType.OPENAI_DIRECT);
            credential.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            Mockito.when(credentialRepository.findById(16L)).thenReturn(Optional.of(credential));
            Mockito.when(credentialRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
            Mockito.when(cryptoService.decrypt("cipher-openai")).thenReturn("sk-live-secret");

            var response = service.openAiDirectResourceSmoke(16L, new OpenAiDirectResourceSmokeRequest(
                    false,
                    null,
                    3,
                    null,
                    null,
                    List.of("chat_completions", "realtime_client_secret"),
                    true,
                    true
            ));

            assertEquals("LIVE_SMOKE_COMPLETED", response.status());
            assertEquals("PASS", response.classification());
            assertEquals(2, response.summary().get("PASS"));
            assertNotNull(chatBody.get());
            assertNotNull(realtimeBody.get());
            assertTrue(chatBody.get().contains("\"max_completion_tokens\":1"));
            assertTrue(realtimeBody.get().contains("\"seconds\":60"));
            assertFalse(response.toString().contains("sk-live-secret"));
            Mockito.verify(cryptoService).decrypt("cipher-openai");
            Mockito.verify(credentialRepository).save(credential);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPersistOpenAiDirectResourceSmokeCertificationMetadataForLiveRun() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/files", exchange -> sendJson(exchange, 200, """
                {"object":"list","data":[{"id":"file_1","object":"file"}]}
                """));
        server.start();
        try {
            UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
            CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
            CredentialAdminService service = service(credentialRepository, cryptoService);
            UpstreamCredentialEntity credential = credential(14L, ProviderType.OPENAI_DIRECT);
            credential.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            Mockito.when(credentialRepository.findById(14L)).thenReturn(Optional.of(credential));
            Mockito.when(credentialRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
            Mockito.when(cryptoService.decrypt("cipher-openai")).thenReturn("sk-live-secret");

            var response = service.openAiDirectResourceSmokeCertification(14L, new OpenAiDirectResourceSmokeRequest(
                    false,
                    null,
                    3,
                    "org-real",
                    "proj-real",
                    List.of("files", "chat_completions")
            ));

            assertEquals("PARTIAL_CERTIFIED", response.certificationStatus());
            assertEquals(2, response.fixtureSnapshots().size());
            assertEquals(OpenAiDirectSmokeCertificationService.RECORD_REPLAY_SCHEMA_VERSION,
                    response.recordReplayFixture().schemaVersion());
            assertEquals(2, response.recordReplayFixture().fixtures().size());
            assertTrue(credential.getCredentialMetadataJson().contains("openai_direct_smoke_certification"));
            assertTrue(credential.getCredentialMetadataJson().contains("recordReplayFixture"));
            assertTrue(credential.getCredentialMetadataJson().contains("PARTIAL_CERTIFIED"));
            assertFalse(credential.getCredentialMetadataJson().contains("sk-live-secret"));
            assertFalse(credential.getCredentialMetadataJson().contains("org-real"));
            assertFalse(credential.getCredentialMetadataJson().contains("proj-real"));
            Mockito.verify(cryptoService).decrypt("cipher-openai");
            Mockito.verify(credentialRepository, Mockito.atLeastOnce()).save(credential);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldNotPersistOpenAiDirectResourceSmokeCertificationForDryRun() {
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        CredentialAdminService service = service(credentialRepository, cryptoService);
        UpstreamCredentialEntity credential = credential(15L, ProviderType.OPENAI_DIRECT);
        Mockito.when(credentialRepository.findById(15L)).thenReturn(Optional.of(credential));

        var response = service.openAiDirectResourceSmokeCertification(15L, new OpenAiDirectResourceSmokeRequest(
                true,
                null,
                null,
                null,
                null,
                List.of("files")
        ));

        assertEquals("DRY_RUN", response.certificationStatus());
        assertEquals(OpenAiDirectSmokeCertificationService.RECORD_REPLAY_SCHEMA_VERSION,
                response.recordReplayFixture().schemaVersion());
        assertEquals(null, credential.getCredentialMetadataJson());
        Mockito.verify(credentialRepository, Mockito.never()).save(Mockito.any());
        Mockito.verify(cryptoService, Mockito.never()).decrypt(Mockito.anyString());
    }

    private CredentialAdminService service(
            UpstreamCredentialRepository credentialRepository,
            CredentialCryptoService cryptoService) {
        return new CredentialAdminService(
                credentialRepository,
                cryptoService,
                Mockito.mock(CredentialModelDiscoveryService.class),
                Mockito.mock(ProviderSiteRegistryService.class),
                Mockito.mock(UpstreamAccountPoolRepository.class),
                new ObjectMapper(),
                Mockito.mock(SupportedModelCatalogService.class)
        );
    }

    private UpstreamCredentialEntity credential(Long id, ProviderType providerType) {
        UpstreamCredentialEntity entity = new UpstreamCredentialEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-05-16T00:00:00Z"));
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-05-16T00:00:00Z"));
        entity.setCredentialName("credential-" + id);
        entity.setProviderType(providerType);
        entity.setBaseUrl("https://api.openai.com");
        entity.setApiKeyCiphertext("cipher-openai");
        entity.setApiKeyFingerprint("fingerprint-openai");
        entity.setActive(true);
        entity.setDeleted(false);
        return entity;
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("content-type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
