package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.FunctionalProviderSmokeRequest;
import com.prodigalgal.xaigateway.admin.api.CredentialRequest;
import com.prodigalgal.xaigateway.admin.api.CredentialResponse;
import com.prodigalgal.xaigateway.admin.api.OpenAiDirectSmokeRequest;
import com.prodigalgal.xaigateway.admin.api.OpenAiDirectResourceSmokeRequest;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.gateway.core.catalog.CredentialModelDiscoveryService;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelAddressingStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.persistence.entity.ProviderProtocolEndpointEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ProviderProtocolEndpointRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialAdminServiceTests {

    @Test
    void shouldRestoreSoftDeletedCredentialWhenFingerprintAlreadyExists() {
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        ProviderSiteRegistryService siteRegistryService = Mockito.mock(ProviderSiteRegistryService.class);
        ProviderProtocolEndpointRepository endpointRepository = Mockito.mock(ProviderProtocolEndpointRepository.class);
        CredentialAdminService service = service(
                credentialRepository,
                cryptoService,
                modelCatalogService,
                groupRepository,
                siteRegistryService,
                endpointRepository
        );
        UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(group, "id", 3L);
        group.setGroupName("Gemini AI Studio");
        group.setProviderType(UpstreamAccountProviderType.GEMINI_OAUTH);
        UpstreamCredentialEntity deleted = credential(31L, ProviderType.GEMINI_DIRECT);
        deleted.setDeleted(true);
        deleted.setActive(false);
        UpstreamSiteProfileEntity siteProfile = siteProfile(
                9L,
                UpstreamSiteKind.GEMINI_DIRECT,
                "https://generativelanguage.googleapis.com"
        );

        Mockito.when(cryptoService.fingerprint("gemini-secret")).thenReturn("fp-gemini");
        Mockito.when(cryptoService.encrypt("gemini-secret")).thenReturn("enc-gemini-secret");
        Mockito.when(credentialRepository.findFirstByApiKeyFingerprintAndProviderTypeAndBaseUrlAndSiteProfileIdAndProtocolEndpointIdOrderByUpdatedAtDesc(
                        "fp-gemini",
                        ProviderType.GEMINI_DIRECT,
                        "https://generativelanguage.googleapis.com",
                        9L,
                        90L
                ))
                .thenReturn(Optional.of(deleted));
        Mockito.when(groupRepository.findById(3L)).thenReturn(Optional.of(group));
        Mockito.when(modelCatalogService.resolveForCredentialImport(
                Mockito.eq(ProviderType.GEMINI_DIRECT),
                Mockito.eq(group),
                Mockito.eq(List.of("gemini-2.5-pro"))
        )).thenReturn(List.of("gemini-2.5-pro"));
        Mockito.when(modelCatalogService.normalize(Mockito.anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(siteRegistryService.ensureSiteProfile(null, null, 9L))
                .thenReturn(siteProfile);
        Mockito.when(endpointRepository.findById(90L))
                .thenReturn(Optional.of(protocolEndpoint(90L, 9L, ProviderType.GEMINI_DIRECT, UpstreamSiteKind.GEMINI_DIRECT, "https://generativelanguage.googleapis.com")));
        Mockito.when(credentialRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        CredentialResponse response = service.create(new CredentialRequest(
                "Gemini AI Studio 01",
                null,
                null,
                CredentialAuthKind.API_KEY,
                "gemini-secret",
                null,
                Map.of("source", "user_import"),
                true,
                null,
                null,
                9L,
                90L,
                null,
                3L,
                List.of("gemini-2.5-pro")
        ));

        assertEquals(31L, response.id());
        assertFalse(deleted.isDeleted());
        assertTrue(deleted.isActive());
        assertEquals("Gemini AI Studio 01", deleted.getCredentialName());
        assertEquals("fp-gemini", deleted.getApiKeyFingerprint());
        Mockito.verify(credentialRepository).save(deleted);
    }

    @Test
    void shouldAllowSameSecretAcrossDifferentProviderSurfaces() {
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        ProviderSiteRegistryService siteRegistryService = Mockito.mock(ProviderSiteRegistryService.class);
        ProviderProtocolEndpointRepository endpointRepository = Mockito.mock(ProviderProtocolEndpointRepository.class);
        CredentialAdminService service = service(
                credentialRepository,
                cryptoService,
                modelCatalogService,
                groupRepository,
                siteRegistryService,
                endpointRepository
        );
        UpstreamAccountGroupEntity openAiGroup = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(openAiGroup, "id", 4L);
        openAiGroup.setProviderType(UpstreamAccountProviderType.OPENAI_OAUTH);
        UpstreamSiteProfileEntity openAiSite = siteProfile(
                41L,
                UpstreamSiteKind.DEEPSEEK,
                "https://api.deepseek.com"
        );

        Mockito.when(cryptoService.fingerprint("shared-secret")).thenReturn("fp-shared");
        Mockito.when(cryptoService.encrypt("shared-secret")).thenReturn("enc-shared");
        Mockito.when(groupRepository.findById(4L)).thenReturn(Optional.of(openAiGroup));
        Mockito.when(siteRegistryService.ensureSiteProfile(null, null, 41L))
                .thenReturn(openAiSite);
        Mockito.when(endpointRepository.findById(410L))
                .thenReturn(Optional.of(protocolEndpoint(410L, 41L, ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.DEEPSEEK, "https://api.deepseek.com")));
        Mockito.when(credentialRepository.findFirstByApiKeyFingerprintAndProviderTypeAndBaseUrlAndSiteProfileIdAndProtocolEndpointIdOrderByUpdatedAtDesc(
                        "fp-shared",
                        ProviderType.OPENAI_COMPATIBLE,
                        "https://api.deepseek.com",
                        41L,
                        410L
                ))
                .thenReturn(Optional.empty());
        Mockito.when(credentialRepository.findByApiKeyFingerprintAndProviderTypeAndBaseUrlAndSiteProfileIdAndProtocolEndpointIdAndDeletedFalse(
                        "fp-shared",
                        ProviderType.OPENAI_COMPATIBLE,
                        "https://api.deepseek.com",
                        41L,
                        410L
                ))
                .thenReturn(Optional.empty());
        Mockito.when(modelCatalogService.resolveForCredentialImport(
                Mockito.eq(ProviderType.OPENAI_COMPATIBLE),
                Mockito.eq(openAiGroup),
                Mockito.eq(List.of("deepseek-chat"))
        )).thenReturn(List.of("deepseek-chat"));
        Mockito.when(modelCatalogService.normalize(Mockito.anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(credentialRepository.save(Mockito.any())).thenAnswer(invocation -> {
            UpstreamCredentialEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 100L);
            return entity;
        });

        CredentialResponse response = service.create(new CredentialRequest(
                "DeepSeek OpenAI",
                null,
                null,
                CredentialAuthKind.API_KEY,
                "shared-secret",
                null,
                Map.of(),
                true,
                null,
                null,
                41L,
                410L,
                null,
                4L,
                List.of("deepseek-chat")
        ));

        assertEquals(100L, response.id());
        Mockito.verify(credentialRepository).findFirstByApiKeyFingerprintAndProviderTypeAndBaseUrlAndSiteProfileIdAndProtocolEndpointIdOrderByUpdatedAtDesc(
                "fp-shared",
                ProviderType.OPENAI_COMPATIBLE,
                "https://api.deepseek.com",
                41L,
                410L
        );
    }

    @Test
    void shouldCreateSameSecretForMultipleProtocolEndpoints() {
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        ProviderSiteRegistryService siteRegistryService = Mockito.mock(ProviderSiteRegistryService.class);
        ProviderProtocolEndpointRepository endpointRepository = Mockito.mock(ProviderProtocolEndpointRepository.class);
        CredentialAdminService service = service(
                credentialRepository,
                cryptoService,
                modelCatalogService,
                groupRepository,
                siteRegistryService,
                endpointRepository
        );
        UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(group, "id", 4L);
        UpstreamSiteProfileEntity site = siteProfile(41L, UpstreamSiteKind.DEEPSEEK, "https://api.deepseek.com");
        ProviderProtocolEndpointEntity openAiEndpoint = protocolEndpoint(
                410L,
                41L,
                ProviderType.OPENAI_COMPATIBLE,
                UpstreamSiteKind.DEEPSEEK,
                "https://api.deepseek.com"
        );
        openAiEndpoint.setDisplayName("OpenAI-compatible");
        ProviderProtocolEndpointEntity anthropicEndpoint = protocolEndpoint(
                411L,
                41L,
                ProviderType.ANTHROPIC_DIRECT,
                UpstreamSiteKind.ANTHROPIC_DIRECT,
                "https://api.deepseek.com/anthropic"
        );
        anthropicEndpoint.setDisplayName("Anthropic-compatible");

        Mockito.when(cryptoService.fingerprint("shared-secret")).thenReturn("fp-shared");
        Mockito.when(cryptoService.encrypt("shared-secret")).thenReturn("enc-shared");
        Mockito.when(groupRepository.findById(4L)).thenReturn(Optional.of(group));
        Mockito.when(siteRegistryService.ensureSiteProfile(null, null, 41L)).thenReturn(site);
        Mockito.when(endpointRepository.findById(410L)).thenReturn(Optional.of(openAiEndpoint));
        Mockito.when(endpointRepository.findById(411L)).thenReturn(Optional.of(anthropicEndpoint));
        Mockito.when(credentialRepository.findFirstByApiKeyFingerprintAndProviderTypeAndBaseUrlAndSiteProfileIdAndProtocolEndpointIdOrderByUpdatedAtDesc(
                Mockito.eq("fp-shared"),
                Mockito.any(),
                Mockito.anyString(),
                Mockito.eq(41L),
                Mockito.anyLong()
        )).thenReturn(Optional.empty());
        Mockito.when(credentialRepository.findByApiKeyFingerprintAndProviderTypeAndBaseUrlAndSiteProfileIdAndProtocolEndpointIdAndDeletedFalse(
                Mockito.eq("fp-shared"),
                Mockito.any(),
                Mockito.anyString(),
                Mockito.eq(41L),
                Mockito.anyLong()
        )).thenReturn(Optional.empty());
        Mockito.when(modelCatalogService.resolveForCredentialImport(Mockito.any(), Mockito.eq(group), Mockito.eq(List.of("deepseek-chat"))))
                .thenAnswer(invocation -> invocation.getArgument(2));
        Mockito.when(modelCatalogService.normalize(Mockito.anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(credentialRepository.save(Mockito.any())).thenAnswer(invocation -> {
            UpstreamCredentialEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", entity.getProtocolEndpointId());
            return entity;
        });

        List<CredentialResponse> responses = service.createForProtocolEndpoints(new CredentialRequest(
                "DeepSeek",
                null,
                null,
                CredentialAuthKind.API_KEY,
                "shared-secret",
                null,
                Map.of(),
                true,
                null,
                null,
                41L,
                null,
                List.of(410L, 411L),
                4L,
                List.of("deepseek-chat")
        ));

        assertEquals(2, responses.size());
        assertEquals(List.of(410L, 411L), responses.stream().map(CredentialResponse::protocolEndpointId).toList());
        assertEquals("DeepSeek - OpenAI-compatible", responses.get(0).credentialName());
        assertEquals("DeepSeek - Anthropic-compatible", responses.get(1).credentialName());
        Mockito.verify(credentialRepository, Mockito.times(2)).save(Mockito.any(UpstreamCredentialEntity.class));
    }

    @Test
    void shouldMergeProtocolEndpointConversationProfileIntoCredentialMetadataOnCreate() {
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        ProviderSiteRegistryService siteRegistryService = Mockito.mock(ProviderSiteRegistryService.class);
        ProviderProtocolEndpointRepository endpointRepository = Mockito.mock(ProviderProtocolEndpointRepository.class);
        CredentialAdminService service = service(
                credentialRepository,
                cryptoService,
                modelCatalogService,
                groupRepository,
                siteRegistryService,
                endpointRepository
        );
        UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(group, "id", 4L);
        UpstreamSiteProfileEntity site = siteProfile(41L, UpstreamSiteKind.DEEPSEEK, "https://api.deepseek.com");
        ProviderProtocolEndpointEntity endpoint = protocolEndpoint(
                411L,
                41L,
                ProviderType.ANTHROPIC_DIRECT,
                UpstreamSiteKind.ANTHROPIC_DIRECT,
                "https://api.deepseek.com/anthropic",
                """
                        {
                          "targetProtocol": "anthropic_messages",
                          "reasoningTransport": "thinking_blocks",
                          "protocolEndpoint": "anthropic_compatible"
                        }
                        """
        );

        Mockito.when(cryptoService.fingerprint("shared-secret")).thenReturn("fp-shared");
        Mockito.when(cryptoService.encrypt("shared-secret")).thenReturn("enc-shared");
        Mockito.when(groupRepository.findById(4L)).thenReturn(Optional.of(group));
        Mockito.when(siteRegistryService.ensureSiteProfile(null, null, 41L)).thenReturn(site);
        Mockito.when(endpointRepository.findById(411L)).thenReturn(Optional.of(endpoint));
        Mockito.when(credentialRepository.findFirstByApiKeyFingerprintAndProviderTypeAndBaseUrlAndSiteProfileIdAndProtocolEndpointIdOrderByUpdatedAtDesc(
                        "fp-shared",
                        ProviderType.ANTHROPIC_DIRECT,
                        "https://api.deepseek.com/anthropic",
                        41L,
                        411L
                ))
                .thenReturn(Optional.empty());
        Mockito.when(credentialRepository.findByApiKeyFingerprintAndProviderTypeAndBaseUrlAndSiteProfileIdAndProtocolEndpointIdAndDeletedFalse(
                        "fp-shared",
                        ProviderType.ANTHROPIC_DIRECT,
                        "https://api.deepseek.com/anthropic",
                        41L,
                        411L
                ))
                .thenReturn(Optional.empty());
        Mockito.when(modelCatalogService.resolveForCredentialImport(
                Mockito.eq(ProviderType.ANTHROPIC_DIRECT),
                Mockito.eq(group),
                Mockito.eq(List.of("deepseek-chat"))
        )).thenReturn(List.of("deepseek-chat"));
        Mockito.when(modelCatalogService.normalize(Mockito.anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(credentialRepository.save(Mockito.any())).thenAnswer(invocation -> {
            UpstreamCredentialEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 101L);
            return entity;
        });

        CredentialResponse response = service.create(new CredentialRequest(
                "DeepSeek Anthropic",
                null,
                null,
                CredentialAuthKind.API_KEY,
                "shared-secret",
                null,
                Map.of(
                        "source", "manual",
                        "conversationProfile", Map.of(
                                "reasoningTransport", "reasoning_content_delta",
                                "customFlag", "user_override"
                        )
                ),
                true,
                null,
                null,
                41L,
                411L,
                null,
                4L,
                List.of("deepseek-chat")
        ));

        assertEquals(101L, response.id());
        assertEquals(411L, response.protocolEndpointId());
        assertEquals("manual", response.credentialMetadata().get("source"));
        Map<?, ?> conversationProfile = (Map<?, ?>) response.credentialMetadata().get("conversationProfile");
        assertNotNull(conversationProfile);
        assertEquals("anthropic_messages", conversationProfile.get("targetProtocol"));
        assertEquals("anthropic_compatible", conversationProfile.get("protocolEndpoint"));
        assertEquals("reasoning_content_delta", conversationProfile.get("reasoningTransport"));
        assertEquals("user_override", conversationProfile.get("customFlag"));
    }

    @Test
    void shouldRefreshConversationProfileWhenCredentialSwitchesProtocolEndpoint() {
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        ProviderSiteRegistryService siteRegistryService = Mockito.mock(ProviderSiteRegistryService.class);
        ProviderProtocolEndpointRepository endpointRepository = Mockito.mock(ProviderProtocolEndpointRepository.class);
        CredentialAdminService service = service(
                credentialRepository,
                cryptoService,
                modelCatalogService,
                groupRepository,
                siteRegistryService,
                endpointRepository
        );
        UpstreamCredentialEntity existing = credential(201L, ProviderType.OPENAI_COMPATIBLE);
        existing.setSiteProfileId(41L);
        existing.setProtocolEndpointId(410L);
        existing.setGroupId(4L);
        existing.setCredentialMetadataJson("""
                {"conversationProfile":{"targetProtocol":"openai_chat_or_responses"}}
                """);
        UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(group, "id", 4L);
        UpstreamSiteProfileEntity site = siteProfile(41L, UpstreamSiteKind.DEEPSEEK, "https://api.deepseek.com");
        ProviderProtocolEndpointEntity endpoint = protocolEndpoint(
                411L,
                41L,
                ProviderType.ANTHROPIC_DIRECT,
                UpstreamSiteKind.ANTHROPIC_DIRECT,
                "https://api.deepseek.com/anthropic",
                """
                        {
                          "targetProtocol": "anthropic_messages",
                          "reasoningTransport": "thinking_blocks"
                        }
                        """
        );

        Mockito.when(credentialRepository.findById(201L)).thenReturn(Optional.of(existing));
        Mockito.when(groupRepository.findById(4L)).thenReturn(Optional.of(group));
        Mockito.when(siteRegistryService.ensureSiteProfile(null, null, 41L)).thenReturn(site);
        Mockito.when(endpointRepository.findById(411L)).thenReturn(Optional.of(endpoint));
        Mockito.when(modelCatalogService.resolveForCredentialImport(
                Mockito.eq(ProviderType.ANTHROPIC_DIRECT),
                Mockito.eq(group),
                Mockito.eq(List.of("deepseek-chat"))
        )).thenReturn(List.of("deepseek-chat"));
        Mockito.when(modelCatalogService.normalize(Mockito.anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(credentialRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        CredentialResponse response = service.update(201L, new CredentialRequest(
                "DeepSeek Anthropic",
                null,
                null,
                CredentialAuthKind.API_KEY,
                null,
                null,
                Map.of("source", "endpoint_switch"),
                true,
                null,
                null,
                41L,
                411L,
                null,
                4L,
                List.of("deepseek-chat")
        ));

        assertEquals(411L, response.protocolEndpointId());
        assertEquals(ProviderType.ANTHROPIC_DIRECT, response.providerType());
        assertEquals("https://api.deepseek.com/anthropic", response.baseUrl());
        assertEquals("endpoint_switch", response.credentialMetadata().get("source"));
        Map<?, ?> conversationProfile = (Map<?, ?>) response.credentialMetadata().get("conversationProfile");
        assertNotNull(conversationProfile);
        assertEquals("anthropic_messages", conversationProfile.get("targetProtocol"));
        assertEquals("thinking_blocks", conversationProfile.get("reasoningTransport"));
    }

    @Test
    void shouldRejectApiKeyCredentialWithoutProviderSite() {
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        ProviderSiteRegistryService siteRegistryService = Mockito.mock(ProviderSiteRegistryService.class);
        CredentialAdminService service = service(
                credentialRepository,
                cryptoService,
                modelCatalogService,
                groupRepository,
                siteRegistryService
        );
        UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(group, "id", 4L);

        Mockito.when(groupRepository.findById(4L)).thenReturn(Optional.of(group));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.create(new CredentialRequest(
                "Missing API Entry",
                null,
                null,
                CredentialAuthKind.API_KEY,
                "shared-secret",
                null,
                Map.of(),
                true,
                null,
                null,
                null,
                null,
                null,
                4L,
                List.of()
        )));

        assertEquals("API Key 上游凭证必须绑定厂商协议入口。", exception.getMessage());
        Mockito.verify(siteRegistryService, Mockito.never()).ensureSiteProfile(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(credentialRepository, Mockito.never()).save(Mockito.any());
    }

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
    void shouldExecuteOpenAiDirectResourceReadOnlyProbesAndBlockBillableFamilies() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/files", exchange -> sendJson(exchange, 200, """
                {"object":"list","data":[{"id":"file_1","object":"file"}]}
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
            assertEquals(2, response.summary().get("BUDGET_BLOCKED"));
            assertEquals(1, response.summary().get("NO_PERMISSION"));
            assertTrue(response.items().stream().anyMatch(item -> "FILES".equals(item.resourceFamily()) && "PASS".equals(item.classification())));
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

    @Test
    void shouldBuildFunctionalProviderSmokeDryRunWithoutDecryptingSecret() {
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        CredentialAdminService service = service(credentialRepository, cryptoService);
        UpstreamCredentialEntity credential = credential(17L, ProviderType.OPENAI_COMPATIBLE);
        credential.setBaseUrl("https://api.mimo-v2.com/v1");
        Mockito.when(credentialRepository.findById(17L)).thenReturn(Optional.of(credential));

        var response = service.functionalProviderSmoke(17L, new FunctionalProviderSmokeRequest(
                true,
                null,
                "mimo_openai",
                null,
                null,
                null,
                null,
                null
        ));

        assertEquals("DRY_RUN_READY", response.status());
        assertEquals("SKIPPED", response.classification());
        assertEquals("DRY_RUN", response.skippedReason());
        assertEquals("OPENAI_COMPATIBLE", response.protocol());
        assertEquals(3, response.items().size());
        assertEquals(3, response.summary().get("SKIPPED"));
        assertTrue(response.items().stream().anyMatch(item -> "CHAT_TOOLS".equals(item.resourceFamily())));
        assertTrue(response.toString().contains("authorization=Bearer ***"));
        assertFalse(response.toString().contains("mimo-secret"));
        Mockito.verify(cryptoService, Mockito.never()).decrypt(Mockito.anyString());
        Mockito.verify(credentialRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void shouldKeepAnthropicCompatibleFunctionalSmokeApiKeyHeaderPreview() {
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        CredentialAdminService service = service(credentialRepository, cryptoService);
        UpstreamCredentialEntity credential = credential(171L, ProviderType.ANTHROPIC_DIRECT);
        credential.setBaseUrl("https://api.deepseek.com/anthropic");
        Mockito.when(credentialRepository.findById(171L)).thenReturn(Optional.of(credential));

        var response = service.functionalProviderSmoke(171L, new FunctionalProviderSmokeRequest(
                true,
                null,
                "anthropic_compatible",
                null,
                null,
                null,
                List.of("messages"),
                null
        ));

        assertEquals("DRY_RUN_READY", response.status());
        assertEquals("ANTHROPIC_COMPATIBLE", response.protocol());
        String preview = response.items().getFirst().requestPreview().toString();
        assertTrue(preview.contains("anthropic-version=2023-06-01"));
        assertTrue(preview.contains("api-key=***"));
        assertFalse(preview.contains("authorization"));
        Mockito.verify(cryptoService, Mockito.never()).decrypt(Mockito.anyString());
        Mockito.verify(credentialRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void shouldBlockFunctionalProviderLiveWithoutAllowLive() {
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        CredentialAdminService service = service(credentialRepository, cryptoService);
        UpstreamCredentialEntity credential = credential(18L, ProviderType.GEMINI_DIRECT);
        credential.setBaseUrl("https://generativelanguage.googleapis.com");
        Mockito.when(credentialRepository.findById(18L)).thenReturn(Optional.of(credential));

        var response = service.functionalProviderSmoke(18L, new FunctionalProviderSmokeRequest(
                false,
                false,
                null,
                null,
                3,
                null,
                List.of("generate_content"),
                true
        ));

        assertEquals("LIVE_GUARD_BLOCKED", response.status());
        assertEquals("SKIPPED", response.classification());
        assertEquals("LIVE_NOT_ALLOWED", response.skippedReason());
        assertEquals(1, response.summary().get("SKIPPED"));
        Mockito.verify(cryptoService, Mockito.never()).decrypt(Mockito.anyString());
        Mockito.verify(credentialRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void shouldRejectOpenAiDirectForFunctionalProviderSmokeWithoutDecryptingSecret() {
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        CredentialAdminService service = service(credentialRepository, cryptoService);
        UpstreamCredentialEntity credential = credential(19L, ProviderType.OPENAI_DIRECT);
        Mockito.when(credentialRepository.findById(19L)).thenReturn(Optional.of(credential));

        var response = service.functionalProviderSmoke(19L, new FunctionalProviderSmokeRequest(
                false,
                true,
                null,
                null,
                3,
                null,
                null,
                true
        ));

        assertEquals("ROUTE_BLOCKED", response.status());
        assertEquals("UNSUPPORTED", response.classification());
        assertEquals("PROVIDER_NOT_FUNCTIONAL_SMOKE_COMPATIBLE", response.skippedReason());
        Mockito.verify(cryptoService, Mockito.never()).decrypt(Mockito.anyString());
        Mockito.verify(credentialRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void shouldExecuteFunctionalProviderSmokeWhenLiveAndBillableAreExplicitlyAllowed() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("authorization"));
            exchange.getResponseHeaders().add("x-request-id", "req-functional-provider");
            sendJson(exchange, 200, """
                    {"id":"chatcmpl_1","object":"chat.completion","model":"mimo-v2-pro","usage":{"completion_tokens":1},"choices":[{"index":0}]}
                    """);
        });
        server.start();
        try {
            UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
            CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
            CredentialAdminService service = service(credentialRepository, cryptoService);
            UpstreamCredentialEntity credential = credential(20L, ProviderType.OPENAI_COMPATIBLE);
            credential.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            Mockito.when(credentialRepository.findById(20L)).thenReturn(Optional.of(credential));
            Mockito.when(credentialRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
            Mockito.when(cryptoService.decrypt("cipher-openai")).thenReturn("mimo-secret");

            var response = service.functionalProviderSmoke(20L, new FunctionalProviderSmokeRequest(
                    false,
                    true,
                    "mimo_openai",
                    null,
                    3,
                    null,
                    List.of("chat_completions"),
                    true
            ));

            assertEquals("LIVE_SMOKE_COMPLETED", response.status());
            assertEquals("PASS", response.classification());
            assertEquals(1, response.summary().get("PASS"));
            assertEquals("req-functional-provider", response.items().getFirst().upstreamRequestId());
            assertEquals("Bearer mimo-secret", authorization.get());
            assertNotNull(credential.getLastUsedAt());
            assertEquals(null, credential.getLastErrorCode());
            assertFalse(response.toString().contains("mimo-secret"));
            Mockito.verify(cryptoService).decrypt("cipher-openai");
            Mockito.verify(credentialRepository).save(credential);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPersistFunctionalProviderSmokeCertificationMetadataForAllowedLiveRun() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getResponseHeaders().add("x-request-id", "req-functional-certification");
            sendJson(exchange, 200, """
                    {"id":"chatcmpl_1","object":"chat.completion","model":"mimo-v2-pro","usage":{"completion_tokens":1},"choices":[{"index":0}]}
                    """);
        });
        server.start();
        try {
            UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
            CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
            CredentialAdminService service = service(credentialRepository, cryptoService);
            UpstreamCredentialEntity credential = credential(21L, ProviderType.OPENAI_COMPATIBLE);
            credential.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            Mockito.when(credentialRepository.findById(21L)).thenReturn(Optional.of(credential));
            Mockito.when(credentialRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
            Mockito.when(cryptoService.decrypt("cipher-openai")).thenReturn("mimo-secret");

            var response = service.functionalProviderSmokeCertification(21L, new FunctionalProviderSmokeRequest(
                    false,
                    true,
                    "mimo_openai",
                    null,
                    3,
                    null,
                    List.of("chat_completions"),
                    true
            ));

            assertEquals("CERTIFIED", response.certificationStatus());
            assertEquals(FunctionalProviderSmokeCertificationService.RECORD_REPLAY_SCHEMA_VERSION,
                    response.recordReplayFixture().schemaVersion());
            assertEquals("functional_provider_smoke_certification",
                    response.recordReplayFixture().replayPolicy().get("fixtureSource"));
            assertTrue(credential.getCredentialMetadataJson().contains("functional_provider_smoke_certification"));
            assertTrue(credential.getCredentialMetadataJson().contains("recordReplayFixture"));
            assertTrue(credential.getCredentialMetadataJson().contains("OPENAI_COMPATIBLE"));
            assertFalse(credential.getCredentialMetadataJson().contains("mimo-secret"));
            Mockito.verify(cryptoService).decrypt("cipher-openai");
            Mockito.verify(credentialRepository, Mockito.atLeastOnce()).save(credential);
        } finally {
            server.stop(0);
        }
    }

    private CredentialAdminService service(
            UpstreamCredentialRepository credentialRepository,
            CredentialCryptoService cryptoService) {
        return service(
                credentialRepository,
                cryptoService,
                Mockito.mock(SupportedModelCatalogService.class),
                Mockito.mock(UpstreamAccountGroupRepository.class),
                Mockito.mock(ProviderSiteRegistryService.class)
        );
    }

    private CredentialAdminService service(
            UpstreamCredentialRepository credentialRepository,
            CredentialCryptoService cryptoService,
            SupportedModelCatalogService modelCatalogService,
            UpstreamAccountGroupRepository accountGroupRepository,
            ProviderSiteRegistryService siteRegistryService) {
        return service(
                credentialRepository,
                cryptoService,
                modelCatalogService,
                accountGroupRepository,
                siteRegistryService,
                Mockito.mock(ProviderProtocolEndpointRepository.class)
        );
    }

    private CredentialAdminService service(
            UpstreamCredentialRepository credentialRepository,
            CredentialCryptoService cryptoService,
            SupportedModelCatalogService modelCatalogService,
            UpstreamAccountGroupRepository accountGroupRepository,
            ProviderSiteRegistryService siteRegistryService,
            ProviderProtocolEndpointRepository endpointRepository) {
        return new CredentialAdminService(
                credentialRepository,
                cryptoService,
                Mockito.mock(CredentialModelDiscoveryService.class),
                siteRegistryService,
                endpointRepository,
                accountGroupRepository,
                new ObjectMapper(),
                modelCatalogService,
                new com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendPolicyService()
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

    private UpstreamSiteProfileEntity siteProfile(Long id, UpstreamSiteKind siteKind, String baseUrlPattern) {
        UpstreamSiteProfileEntity entity = new UpstreamSiteProfileEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setProfileCode("site:" + siteKind.name().toLowerCase(java.util.Locale.ROOT));
        entity.setDisplayName(siteKind.name());
        entity.setSiteKind(siteKind);
        entity.setBaseUrlPattern(baseUrlPattern);
        entity.setActive(true);
        return entity;
    }

    private ProviderProtocolEndpointEntity protocolEndpoint(
            Long id,
            Long siteProfileId,
            ProviderType providerType,
            UpstreamSiteKind siteKind,
            String baseUrl) {
        return protocolEndpoint(id, siteProfileId, providerType, siteKind, baseUrl, null);
    }

    private ProviderProtocolEndpointEntity protocolEndpoint(
            Long id,
            Long siteProfileId,
            ProviderType providerType,
            UpstreamSiteKind siteKind,
            String baseUrl,
            String conversationProfileJson) {
        ProviderProtocolEndpointEntity entity = new ProviderProtocolEndpointEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setSiteProfileId(siteProfileId);
        entity.setEndpointCode("endpoint:" + id);
        entity.setDisplayName("endpoint-" + id);
        entity.setProtocolSuite("suite:" + id);
        entity.setProviderType(providerType);
        entity.setSiteKind(siteKind);
        entity.setBaseUrl(baseUrl);
        entity.setAuthStrategy(AuthStrategy.BEARER);
        entity.setPathStrategy(siteKind == UpstreamSiteKind.ANTHROPIC_DIRECT
                ? PathStrategy.ANTHROPIC_V1_MESSAGES
                : PathStrategy.OPENAI_V1);
        entity.setModelAddressingStrategy(ModelAddressingStrategy.MODEL_NAME);
        entity.setErrorSchemaStrategy(siteKind == UpstreamSiteKind.ANTHROPIC_DIRECT
                ? ErrorSchemaStrategy.ANTHROPIC_ERROR
                : ErrorSchemaStrategy.OPENAI_ERROR);
        entity.setConversationProfileJson(conversationProfileJson);
        entity.setActive(true);
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
