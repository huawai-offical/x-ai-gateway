package com.prodigalgal.xaigateway.integration;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeySecretService;
import com.prodigalgal.xaigateway.gateway.core.cache.AffinityBindingStore;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamChunk;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatRuntime;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatRuntimeContext;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatRuntimeResult;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.routing.CredentialHealthState;
import com.prodigalgal.xaigateway.gateway.core.routing.HealthStateStore;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelAddressingStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import com.prodigalgal.xaigateway.infra.persistence.entity.AuditLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteModelCapabilityEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AuditLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.CacheHitLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteDecisionLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteModelCapabilityRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCacheReferenceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UsageRecordRepository;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureWebTestClient
@Import(PermitAllSecurityTestConfig.class)
class GatewayEndToEndSmokeTests {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DistributedKeySecretService distributedKeySecretService;

    @Autowired
    private DistributedKeyRepository distributedKeyRepository;

    @Autowired
    private DistributedKeyBindingRepository distributedKeyBindingRepository;

    @Autowired
    private UpstreamCredentialRepository upstreamCredentialRepository;

    @Autowired
    private UpstreamSiteProfileRepository upstreamSiteProfileRepository;

    @Autowired
    private SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository;

    @Autowired
    private SiteModelCapabilityRepository siteModelCapabilityRepository;

    @Autowired
    private RequestLogRepository requestLogRepository;

    @Autowired
    private UsageRecordRepository usageRecordRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private RouteDecisionLogRepository routeDecisionLogRepository;

    @Autowired
    private CacheHitLogRepository cacheHitLogRepository;

    @Autowired
    private UpstreamCacheReferenceRepository upstreamCacheReferenceRepository;

    @Autowired
    private FakeGatewayChatRuntime fakeGatewayChatRuntime;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AccountSelectionService accountSelectionService;

    @MockitoBean
    private CredentialMaterialResolver credentialMaterialResolver;

    private String openAiKey;
    private String anthropicKey;
    private String geminiKey;
    private String distributedKeyPrefix;
    private Long distributedKeyId;
    private Long secondOpenAiCredentialId;

    @BeforeEach
    void setUp() {
        ensureMissingTables();
        requestLogRepository.deleteAll();
        usageRecordRepository.deleteAll();
        auditLogRepository.deleteAll();
        routeDecisionLogRepository.deleteAll();
        cacheHitLogRepository.deleteAll();
        upstreamCacheReferenceRepository.deleteAll();
        distributedKeyBindingRepository.deleteAll();
        distributedKeyRepository.deleteAll();
        siteModelCapabilityRepository.deleteAll();
        siteCapabilitySnapshotRepository.deleteAll();
        upstreamCredentialRepository.deleteAll();
        upstreamSiteProfileRepository.deleteAll();

        fakeGatewayChatRuntime.reset();
        Mockito.when(accountSelectionService.hasHealthyAccountBinding(Mockito.anyLong(), Mockito.any(), Mockito.any()))
                .thenReturn(true);
        Mockito.when(credentialMaterialResolver.resolve(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    UpstreamCredentialEntity credential = invocation.getArgument(1);
                    return new ResolvedCredentialMaterial(
                            credential.getId(),
                            credential.getSiteProfileId(),
                            CredentialAuthKind.API_KEY,
                            "test-secret",
                            credential.getApiKeyFingerprint(),
                            Map.of(),
                            null,
                            "test"
                    );
                });

        SeedData seedData = seed();
        this.distributedKeyPrefix = seedData.distributedKeyPrefix();
        this.openAiKey = seedData.openAiKey();
        this.anthropicKey = seedData.anthropicKey();
        this.geminiKey = seedData.geminiKey();
        this.distributedKeyId = seedData.distributedKeyId();
        this.secondOpenAiCredentialId = seedData.secondOpenAiCredentialId();
    }

    private void ensureMissingTables() {
        jdbcTemplate.execute("""
                create table if not exists site_model_capability (
                    id bigint generated by default as identity primary key,
                    site_profile_id bigint not null,
                    model_name varchar(256) not null,
                    model_key varchar(256) not null,
                    supported_protocols_json text not null,
                    supports_chat boolean not null,
                    supports_tools boolean not null,
                    supports_image_input boolean not null,
                    supports_embeddings boolean not null,
                    supports_cache boolean not null,
                    supports_thinking boolean not null,
                    supports_visible_reasoning boolean not null,
                    supports_reasoning_reuse boolean not null,
                    reasoning_transport varchar(32) not null,
                    capability_level varchar(32) not null,
                    is_active boolean not null,
                    source_refreshed_at timestamp with time zone not null
                )
                """);
    }

    @Test
    void shouldPersistRequestUsageAndSmokeThreeProtocols() {
        callOpenAiChat("hello openai");
        callAnthropic("hello anthropic");
        callGemini("hello gemini");

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/admin/observability/summary")
                        .queryParam("distributedKeyId", distributedKeyId)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sampledRouteDecisionCount").isEqualTo(3)
                .jsonPath("$.sampledUsageRecordCount").isEqualTo(3)
                .jsonPath("$.sampledFinalUsageRecordCount").isEqualTo(3);

        assertEquals(3, requestLogRepository.count());
        assertEquals(3, usageRecordRepository.count());
        RequestLogEntity latest = latestRequestLog();
        assertNotNull(latest);
        assertEquals("COMPLETED", latest.getStatus().name());
    }

    @Test
    void shouldReusePrefixAffinityInvalidateAfterFailureAndSwitchOnCooldown() {
        callOpenAiChat("sticky prefix");
        Long initialCredentialId = latestRequestLog().getCredentialId();

        webTestClient.post()
                .uri("/admin/routing/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "distributedKeyPrefix":"%s",
                          "protocol":"openai",
                          "requestPath":"/v1/chat/completions",
                          "requestedModel":"gpt-4o",
                          "requestBody":{"messages":[{"role":"user","content":"sticky prefix"}]}
                        }
                        """.formatted(distributedKeyPrefix))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.selectionSource").isEqualTo("PREFIX_AFFINITY");

        fakeGatewayChatRuntime.failNext();
        webTestClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"gpt-4o",
                          "messages":[{"role":"user","content":"sticky prefix"}]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.choices[0].message.content").isEqualTo("openai ok");

        webTestClient.post()
                .uri("/admin/routing/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "distributedKeyPrefix":"%s",
                          "protocol":"openai",
                          "requestPath":"/v1/chat/completions",
                          "requestedModel":"gpt-4o",
                          "requestBody":{"messages":[{"role":"user","content":"sticky prefix"}]}
                        }
                        """.formatted(distributedKeyPrefix))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.selectedCandidate.candidate.credentialId").value(value -> assertNotEquals(initialCredentialId.intValue(), value));

        Long selectedCredentialId = latestRequestLog().getCredentialId();
        assertNotEquals(initialCredentialId, selectedCredentialId);
        distributedKeyBindingRepository.findAll().stream()
                .filter(binding -> binding.getCredential().getId().equals(selectedCredentialId))
                .findFirst()
                .ifPresent(binding -> {
                    binding.setActive(false);
                    distributedKeyBindingRepository.save(binding);
                });

        webTestClient.post()
                .uri("/admin/routing/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "distributedKeyPrefix":"%s",
                          "protocol":"openai",
                          "requestPath":"/v1/chat/completions",
                          "requestedModel":"gpt-4o",
                          "requestBody":{"messages":[{"role":"user","content":"sticky prefix"}]}
                        }
                        """.formatted(distributedKeyPrefix))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void shouldKeepPreviewAndExplainConsistentAndAuditAdminPosts() {
        var previewBody = """
                {
                  "distributedKeyPrefix":"%s",
                  "protocol":"openai",
                  "requestPath":"/v1/chat/completions",
                  "requestedModel":"gpt-4o",
                  "requestBody":{"messages":[{"role":"user","content":"preview me"}]}
                }
                """.formatted(distributedKeyPrefix);

        webTestClient.post()
                .uri("/admin/routing/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(previewBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.resolvedModelKey").isEqualTo("gpt-4o")
                .jsonPath("$.selectedCandidate.candidate.providerType").isEqualTo("OPENAI_DIRECT");

        webTestClient.post()
                .uri("/admin/translation/explain")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "distributedKeyPrefix":"%s",
                          "protocol":"openai",
                          "requestPath":"/v1/chat/completions",
                          "requestedModel":"gpt-4o",
                          "degradationPolicy":"allow_lossy",
                          "body":{"messages":[{"role":"user","content":"preview me"}]}
                        }
                        """.formatted(distributedKeyPrefix))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.resolvedModelKey").isEqualTo("gpt-4o")
                .jsonPath("$.selectionSource").exists()
                .jsonPath("$.executionKind").exists();

        List<AuditLogEntity> audits = auditLogRepository.findAll().stream()
                .filter(entity -> "ADMIN_API".equals(entity.getAuditType()))
                .toList();
        assertTrue(audits.size() >= 2);
    }

    private RequestLogEntity latestRequestLog() {
        return requestLogRepository.findAll().stream()
                .max(Comparator.comparing(RequestLogEntity::getCreatedAt))
                .orElse(null);
    }

    private void callOpenAiChat(String content) {
        webTestClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"gpt-4o",
                          "messages":[{"role":"user","content":"%s"}]
                        }
                        """.formatted(content))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.choices[0].message.content").isEqualTo("openai ok");
    }

    private void callAnthropic(String content) {
        webTestClient.post()
                .uri("/v1/messages")
                .header("x-api-key", anthropicKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"claude-sonnet-4",
                          "messages":[{"role":"user","content":"%s"}],
                          "maxTokens":128
                        }
                        """.formatted(content))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].text").isEqualTo("anthropic ok");
    }

    private void callGemini(String content) {
        webTestClient.post()
                .uri("/v1beta/models/gemini-2.5-pro:generateContent")
                .header("x-goog-api-key", geminiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "contents":[{"role":"user","parts":[{"text":"%s"}]}]
                        }
                        """.formatted(content))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.candidates[0].content.parts[0].text").isEqualTo("gemini ok");
    }

    private SeedData seed() {
        UpstreamSiteProfileEntity openAiProfile = saveSiteProfile("site:openai_direct", "OPENAI", ProviderFamily.OPENAI, UpstreamSiteKind.OPENAI_DIRECT, AuthStrategy.BEARER, PathStrategy.OPENAI_V1, ErrorSchemaStrategy.OPENAI_ERROR);
        UpstreamSiteProfileEntity anthropicProfile = saveSiteProfile("site:anthropic_direct", "ANTHROPIC", ProviderFamily.ANTHROPIC, UpstreamSiteKind.ANTHROPIC_DIRECT, AuthStrategy.API_KEY_HEADER, PathStrategy.ANTHROPIC_V1_MESSAGES, ErrorSchemaStrategy.ANTHROPIC_ERROR);
        UpstreamSiteProfileEntity geminiProfile = saveSiteProfile("site:gemini_direct", "GEMINI", ProviderFamily.GEMINI, UpstreamSiteKind.GEMINI_DIRECT, AuthStrategy.API_KEY_QUERY, PathStrategy.GEMINI_V1BETA_MODELS, ErrorSchemaStrategy.GEMINI_ERROR);

        saveSnapshot(openAiProfile, List.of("openai", "responses"), true, true);
        saveSnapshot(anthropicProfile, List.of("anthropic_native"), false, false);
        saveSnapshot(geminiProfile, List.of("google_native"), false, false);

        saveModelCapability(openAiProfile, "gpt-4o", List.of("openai", "responses"), ProviderFamily.OPENAI, ReasoningTransport.OPENAI_CHAT);
        saveModelCapability(anthropicProfile, "claude-sonnet-4", List.of("anthropic_native"), ProviderFamily.ANTHROPIC, ReasoningTransport.ANTHROPIC);
        saveModelCapability(geminiProfile, "gemini-2.5-pro", List.of("google_native"), ProviderFamily.GEMINI, ReasoningTransport.GEMINI_THOUGHTS);

        UpstreamCredentialEntity openAiPrimary = saveCredential("openai-primary", ProviderType.OPENAI_DIRECT, "https://api.openai.com", openAiProfile.getId());
        UpstreamCredentialEntity openAiSecondary = saveCredential("openai-secondary", ProviderType.OPENAI_DIRECT, "https://api.openai.com", openAiProfile.getId());
        UpstreamCredentialEntity anthropicPrimary = saveCredential("anthropic-primary", ProviderType.ANTHROPIC_DIRECT, "https://api.anthropic.com", anthropicProfile.getId());
        UpstreamCredentialEntity geminiPrimary = saveCredential("gemini-primary", ProviderType.GEMINI_DIRECT, "https://generativelanguage.googleapis.com", geminiProfile.getId());

        String openAiSecret = "openai-secret";
        String anthropicSecret = "anthropic-secret";
        String geminiSecret = "gemini-secret";
        DistributedKeyEntity distributedKey = new DistributedKeyEntity();
        distributedKey.setKeyName("test-key");
        distributedKey.setKeyPrefix("sk-gw-test");
        distributedKey.setSecretHash(distributedKeySecretService.hashSecret(openAiSecret));
        distributedKey.setMaskedKey("sk-gw-test...masked");
        distributedKey.setAllowedProtocols(List.of("openai", "responses", "anthropic_native", "google_native"));
        distributedKey.setAllowedModels(List.of("gpt-4o", "claude-sonnet-4", "gemini-2.5-pro"));
        distributedKey.setActive(true);
        distributedKey = distributedKeyRepository.save(distributedKey);

        saveBinding(distributedKey, openAiPrimary, 10, 100);
        saveBinding(distributedKey, openAiSecondary, 20, 100);
        saveBinding(distributedKey, anthropicPrimary, 10, 100);
        saveBinding(distributedKey, geminiPrimary, 10, 100);

        return new SeedData(
                distributedKey.getKeyPrefix(),
                distributedKey.getKeyPrefix() + "." + openAiSecret,
                distributedKey.getKeyPrefix() + "." + openAiSecret,
                distributedKey.getKeyPrefix() + "." + openAiSecret,
                distributedKey.getId(),
                openAiSecondary.getId()
        );
    }

    private UpstreamSiteProfileEntity saveSiteProfile(
            String profileCode,
            String displayName,
            ProviderFamily providerFamily,
            UpstreamSiteKind siteKind,
            AuthStrategy authStrategy,
            PathStrategy pathStrategy,
            ErrorSchemaStrategy errorSchemaStrategy) {
        UpstreamSiteProfileEntity entity = new UpstreamSiteProfileEntity();
        entity.setProfileCode(profileCode);
        entity.setDisplayName(displayName);
        entity.setProviderFamily(providerFamily);
        entity.setSiteKind(siteKind);
        entity.setAuthStrategy(authStrategy);
        entity.setPathStrategy(pathStrategy);
        entity.setModelAddressingStrategy(ModelAddressingStrategy.MODEL_NAME);
        entity.setErrorSchemaStrategy(errorSchemaStrategy);
        entity.setBaseUrlPattern("https://.*");
        entity.setDescription("test");
        entity.setActive(true);
        return upstreamSiteProfileRepository.save(entity);
    }

    private void saveSnapshot(UpstreamSiteProfileEntity siteProfile, List<String> protocols, boolean responses, boolean embeddings) {
        SiteCapabilitySnapshotEntity entity = new SiteCapabilitySnapshotEntity();
        entity.setSiteProfile(siteProfile);
        entity.setSupportedProtocols(protocols);
        entity.setSupportsResponses(responses);
        entity.setSupportsEmbeddings(embeddings);
        entity.setSupportsAudio(false);
        entity.setSupportsImages(false);
        entity.setSupportsModeration(false);
        entity.setSupportsFiles(false);
        entity.setSupportsUploads(false);
        entity.setSupportsBatches(false);
        entity.setSupportsTuning(false);
        entity.setSupportsRealtime(false);
        entity.setAuthStrategy(siteProfile.getAuthStrategy());
        entity.setPathStrategy(siteProfile.getPathStrategy());
        entity.setErrorSchemaStrategy(siteProfile.getErrorSchemaStrategy());
        entity.setHealthState("READY");
        entity.setStreamTransport("sse");
        entity.setFallbackStrategy("provider-native");
        entity.setRefreshedAt(Instant.now());
        siteCapabilitySnapshotRepository.save(entity);
    }

    private void saveModelCapability(
            UpstreamSiteProfileEntity siteProfile,
            String modelKey,
            List<String> protocols,
            ProviderFamily providerFamily,
            ReasoningTransport reasoningTransport) {
        SiteModelCapabilityEntity entity = new SiteModelCapabilityEntity();
        entity.setSiteProfile(siteProfile);
        entity.setModelName(modelKey);
        entity.setModelKey(modelKey);
        entity.setSupportedProtocols(protocols);
        entity.setSupportsChat(true);
        entity.setSupportsTools(true);
        entity.setSupportsImageInput(false);
        entity.setSupportsEmbeddings(providerFamily == ProviderFamily.OPENAI);
        entity.setSupportsCache(true);
        entity.setSupportsThinking(true);
        entity.setSupportsVisibleReasoning(true);
        entity.setSupportsReasoningReuse(false);
        entity.setReasoningTransport(reasoningTransport);
        entity.setCapabilityLevel(InteropCapabilityLevel.NATIVE);
        entity.setActive(true);
        entity.setSourceRefreshedAt(Instant.now());
        siteModelCapabilityRepository.save(entity);
    }

    private UpstreamCredentialEntity saveCredential(String name, ProviderType providerType, String baseUrl, Long siteProfileId) {
        UpstreamCredentialEntity entity = new UpstreamCredentialEntity();
        entity.setCredentialName(name);
        entity.setProviderType(providerType);
        entity.setBaseUrl(baseUrl);
        entity.setApiKeyCiphertext("cipher");
        entity.setApiKeyFingerprint(name + "-fp");
        entity.setSiteProfileId(siteProfileId);
        entity.setActive(true);
        return upstreamCredentialRepository.save(entity);
    }

    private void saveBinding(DistributedKeyEntity distributedKey, UpstreamCredentialEntity credential, int priority, int weight) {
        DistributedKeyBindingEntity entity = new DistributedKeyBindingEntity();
        entity.setDistributedKey(distributedKey);
        entity.setCredential(credential);
        entity.setPriority(priority);
        entity.setWeight(weight);
        entity.setActive(true);
        distributedKeyBindingRepository.save(entity);
    }

    private record SeedData(
            String distributedKeyPrefix,
            String openAiKey,
            String anthropicKey,
            String geminiKey,
            Long distributedKeyId,
            Long secondOpenAiCredentialId
    ) {
    }

    @TestConfiguration
    static class SmokeTestConfig {

        @Bean
        @Primary
        AffinityBindingStore inMemoryAffinityBindingStore() {
            return new AffinityBindingStore() {
                private final Map<String, String> values = new ConcurrentHashMap<>();

                @Override
                public String get(String key) {
                    return values.get(key);
                }

                @Override
                public void put(String key, String value, java.time.Duration ttl) {
                    values.put(key, value);
                }

                @Override
                public void invalidateIfMatches(String key, String expectedValue) {
                    values.computeIfPresent(key, (ignored, current) -> current.equals(expectedValue) ? null : current);
                }
            };
        }

        @Bean
        @Primary
        HealthStateStore inMemoryHealthStateStore() {
            return new HealthStateStore() {
                private final Map<Long, CredentialHealthState> values = new ConcurrentHashMap<>();

                @Override
                public Optional<CredentialHealthState> getCredentialState(Long credentialId) {
                    CredentialHealthState state = values.get(credentialId);
                    if (state == null) {
                        return Optional.empty();
                    }
                    if (state.cooldownUntil() != null && state.cooldownUntil().isBefore(Instant.now())) {
                        values.remove(credentialId);
                        return Optional.empty();
                    }
                    return Optional.of(state);
                }

                @Override
                public void markCooldown(Long credentialId, String reason, java.time.Duration ttl) {
                    values.put(credentialId, new CredentialHealthState(
                            "COOLDOWN",
                            reason,
                            Instant.now().plus(ttl == null ? java.time.Duration.ofMinutes(5) : ttl)
                    ));
                }

                @Override
                public void clear(Long credentialId) {
                    values.remove(credentialId);
                }
            };
        }

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        FakeGatewayChatRuntime fakeGatewayChatRuntime() {
            return new FakeGatewayChatRuntime();
        }
    }

    static class FakeGatewayChatRuntime implements GatewayChatRuntime {

        private final AtomicBoolean failNext = new AtomicBoolean(false);

        void failNext() {
            failNext.set(true);
        }

        void reset() {
            failNext.set(false);
        }

        @Override
        public boolean supports(com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView candidate) {
            return candidate.providerType() == ProviderType.OPENAI_DIRECT
                    || candidate.providerType() == ProviderType.ANTHROPIC_DIRECT
                    || candidate.providerType() == ProviderType.GEMINI_DIRECT;
        }

        @Override
        public GatewayChatRuntimeResult execute(GatewayChatRuntimeContext context) {
            if (failNext.getAndSet(false)) {
                throw new IllegalStateException("forced fake runtime failure");
            }
            String protocol = context.request().protocol();
            if ("anthropic_native".equals(protocol)) {
                return new GatewayChatRuntimeResult(
                        "anthropic ok",
                        new GatewayUsage(900, 900, 120, 0, 300, 60, 300, 60, null, 1020, null),
                        List.of(),
                        "end_turn"
                );
            }
            if ("google_native".equals(protocol)) {
                return new GatewayChatRuntimeResult(
                        "gemini ok",
                        new GatewayUsage(800, 520, 180, 40, 280, 0, 280, 0, "cached-content-1", 980, null),
                        List.of(),
                        "stop"
                );
            }
            return new GatewayChatRuntimeResult(
                    "openai ok",
                    new GatewayUsage(1000, 700, 180, 20, 300, 0, 300, 0, null, 1180, null),
                    List.of(),
                    "stop"
            );
        }

        @Override
        public Flux<ChatExecutionStreamChunk> executeStream(GatewayChatRuntimeContext context) {
            GatewayChatRuntimeResult result = execute(context);
            return Flux.just(
                    new ChatExecutionStreamChunk(result.text(), null, GatewayUsage.empty(), false),
                    new ChatExecutionStreamChunk(null, result.finishReason(), result.usage(), true)
            );
        }
    }
}
