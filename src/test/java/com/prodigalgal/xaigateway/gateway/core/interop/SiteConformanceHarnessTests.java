package com.prodigalgal.xaigateway.gateway.core.interop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.admin.api.TranslationExplainRequest;
import com.prodigalgal.xaigateway.admin.application.ErrorRuleService;
import com.prodigalgal.xaigateway.admin.application.TranslationExplainService;
import com.prodigalgal.xaigateway.gateway.core.alias.ModelAliasQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.catalog.GatewayPublicModelView;
import com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryService;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteModelCapabilityEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteModelCapabilityRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.protocol.ingress.interop.InteropPlanRequest;
import com.prodigalgal.xaigateway.protocol.ingress.interop.InteropPlanResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;

class SiteConformanceHarnessTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void shouldKeepPlanExplainModelVisibilityAndExecutionGateInSync(ConformanceFixture fixture) {
        UpstreamSitePolicyService policyService = new UpstreamSitePolicyService();
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteCapabilitySnapshotEntity snapshot = snapshot(fixture);
        Mockito.when(snapshotRepository.findBySiteProfile_Id(1L)).thenReturn(Optional.of(snapshot));

        SiteCapabilityTruthService truthService = new SiteCapabilityTruthService(
                policyService,
                snapshotRepository,
                new ExecutionSupportMatrixService()
        );
        GatewayRequestFeatureService featureService = new GatewayRequestFeatureService();
        CatalogCandidateView candidate = candidate(fixture);
        RouteSelectionResult selectionResult = selectionResult(fixture, candidate);

        GatewayRouteSelectionService routeSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
        Mockito.when(routeSelectionService.select(any())).thenReturn(selectionResult);

        TranslationExecutionPlanCompiler compiler = new TranslationExecutionPlanCompiler(
                routeSelectionService,
                featureService,
                truthService
        );

        TranslationExecutionPlanCompilation preview = compiler.compilePreview(
                "sk-gw-test",
                fixture.protocol(),
                fixture.requestPath(),
                fixture.requestedModel(),
                GatewayDegradationPolicy.ALLOW_LOSSY,
                com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily.GENERIC_OPENAI,
                requestBody(fixture)
        );
        assertEquals(fixture.expectedExecutable(), preview.plan().executable(), fixture.name());
        assertEquals(fixture.expectedEffectiveLevel(), preview.plan().overallEffectiveLevel().name(), fixture.name());
        assertEquals(fixture.expectedExecutionKind(), preview.plan().executionKind().name(), fixture.name());

        TranslationExplainService explainService = new TranslationExplainService(compiler);
        TranslationExecutionPlan explainPlan = explainService.explain(new TranslationExplainRequest(
                "sk-gw-test",
                fixture.protocol(),
                fixture.requestPath(),
                fixture.requestedModel(),
                "allow_lossy",
                requestBody(fixture)
        ));
        assertEquals(preview.plan().executionKind(), explainPlan.executionKind(), fixture.name());
        assertEquals(preview.plan().overallEffectiveLevel(), explainPlan.overallEffectiveLevel(), fixture.name());

        GatewayInteropPlanService interopPlanService = new GatewayInteropPlanService(
                Mockito.mock(ErrorRuleService.class),
                compiler
        );
        InteropPlanResponse interopPlanResponse = interopPlanService.preview("sk-gw-test", new InteropPlanRequest(
                fixture.protocol(),
                fixture.requestPath(),
                fixture.requestedModel(),
                "allow_lossy",
                requestBody(fixture)
        ));
        assertEquals(fixture.expectedExecutable(), interopPlanResponse.executable(), fixture.name());
        assertEquals(fixture.expectedEffectiveLevel().toLowerCase(), interopPlanResponse.overallEffectiveLevel(), fixture.name());
        assertEquals(fixture.expectedExecutionKind(), interopPlanResponse.executionKind().name(), fixture.name());

        SiteModelCapabilityRepository siteModelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        ModelAliasQueryService modelAliasQueryService = Mockito.mock(ModelAliasQueryService.class);
        Mockito.when(modelAliasQueryService.listEnabledAliases()).thenReturn(List.of());
        Mockito.when(modelAliasQueryService.findEnabledAlias(any())).thenReturn(Optional.empty());
        Mockito.when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(any())).thenReturn(List.of(credential(fixture)));
        Mockito.when(siteModelCapabilityRepository.findAllBySiteProfile_IdInAndActiveTrue(any())).thenReturn(List.of(siteModelCapability(fixture)));

        ModelCatalogQueryService modelCatalogQueryService = new ModelCatalogQueryService(
                siteModelCapabilityRepository,
                upstreamCredentialRepository,
                modelAliasQueryService,
                truthService
        );
        DistributedKeyView distributedKeyView = new DistributedKeyView(
                1L,
                "test",
                "sk-gw-test",
                "masked",
                List.of(fixture.protocol()),
                List.of(),
                List.of(new DistributedCredentialBindingView(
                        1L,
                        101L,
                        "credential",
                        fixture.providerType(),
                        "https://example.com",
                        1,
                        100
                ))
        );
        List<GatewayPublicModelView> visibleModels = modelCatalogQueryService.listAccessiblePublicModels(
                distributedKeyView,
                fixture.protocol()
        );
        if (fixture.expectedModelVisible()) {
            assertFalse(visibleModels.isEmpty(), fixture.name());
            assertEquals(fixture.requestedModel(), visibleModels.get(0).publicModelId(), fixture.name());
        } else {
            assertEquals(List.of(), visibleModels, fixture.name());
        }

        FeatureCompatibilityReport executionGate = truthService.evaluate(
                candidate,
                featureService.describe(fixture.requestPath(), requestBody(fixture))
        );
        assertEquals(fixture.expectedExecutionKind(), executionGate.executionKind().name(), fixture.name());
        assertEquals(fixture.expectedEffectiveLevel(), executionGate.capabilityLevel().name(), fixture.name());
    }

    static Stream<ConformanceFixture> fixtures() throws IOException {
        try (InputStream inputStream = SiteConformanceHarnessTests.class.getClassLoader()
                .getResourceAsStream("conformance/site-conformance-fixtures.json")) {
            List<ConformanceFixture> fixtures = OBJECT_MAPPER.readValue(inputStream, new TypeReference<>() {
            });
            return fixtures.stream();
        }
    }

    private static JsonNode requestBody(ConformanceFixture fixture) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("model", fixture.requestedModel());
        if ("/v1/chat/completions".equals(fixture.requestPath())) {
            var content = OBJECT_MAPPER.createArrayNode();
            content.addObject().put("type", "text").put("text", "hi");
            if (fixture.includeImageInput()) {
                content.addObject()
                        .put("type", "image_url")
                        .putObject("image_url")
                        .put("url", "https://example.com/image.png");
            }
            root.putArray("messages").addObject().put("role", "user").set("content", content);
            return root;
        }
        if (fixture.requestPath().contains(":generateContent")) {
            root.remove("model");
            root.putArray("contents").addObject().put("role", "user")
                    .putArray("parts").addObject().put("text", "hi");
            return root;
        }
        if ("/v1/messages".equals(fixture.requestPath())) {
            root.remove("model");
            root.putArray("messages").addObject()
                    .put("role", "user")
                    .putArray("content").addObject().put("type", "text").put("text", "hi");
            return root;
        }
        if ("/v1/moderations".equals(fixture.requestPath())) {
            root.put("input", "hi");
        }
        return root;
    }

    private static CatalogCandidateView candidate(ConformanceFixture fixture) {
        UpstreamSitePolicyService.SitePolicy policy = new UpstreamSitePolicyService().policy(fixture.siteKind());
        return new CatalogCandidateView(
                101L,
                "credential",
                fixture.providerType(),
                1L,
                fixture.providerFamily(),
                fixture.siteKind(),
                policy.authStrategy(),
                policy.pathStrategy(),
                policy.errorSchemaStrategy(),
                "https://example.com",
                fixture.requestedModel(),
                fixture.requestedModel(),
                fixture.supportedProtocols(),
                fixture.supportsChat(),
                fixture.supportsTools(),
                fixture.supportsImageInput(),
                fixture.supportsEmbeddings(),
                false,
                fixture.supportsThinking(),
                fixture.supportsThinking(),
                false,
                fixture.supportsThinking() ? ReasoningTransport.OPENAI_CHAT : ReasoningTransport.NONE,
                InteropCapabilityLevel.NATIVE
        );
    }

    private static RouteSelectionResult selectionResult(ConformanceFixture fixture, CatalogCandidateView candidate) {
        RouteCandidateView routeCandidateView = new RouteCandidateView(candidate, 11L, 10, 100);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                fixture.requestedModel(),
                fixture.requestedModel(),
                fixture.requestedModel(),
                fixture.protocol(),
                "prefix",
                "fingerprint",
                fixture.requestedModel(),
                RouteSelectionSource.WEIGHTED_HASH,
                routeCandidateView,
                List.of(routeCandidateView)
        );
    }

    private static SiteCapabilitySnapshotEntity snapshot(ConformanceFixture fixture) {
        UpstreamSiteProfileEntity siteProfile = new UpstreamSiteProfileEntity();
        siteProfile.setProfileCode("site:" + fixture.siteKind().name().toLowerCase());
        siteProfile.setDisplayName(fixture.siteKind().name());
        siteProfile.setProviderFamily(fixture.providerFamily());
        siteProfile.setSiteKind(fixture.siteKind());
        UpstreamSitePolicyService.SitePolicy policy = new UpstreamSitePolicyService().policy(fixture.siteKind());
        siteProfile.setAuthStrategy(policy.authStrategy());
        siteProfile.setPathStrategy(policy.pathStrategy());
        siteProfile.setModelAddressingStrategy(policy.modelAddressingStrategy());
        siteProfile.setErrorSchemaStrategy(policy.errorSchemaStrategy());
        siteProfile.setBaseUrlPattern("https://example.com");
        siteProfile.setDescription("fixture");
        siteProfile.setActive(true);
        setId(siteProfile, 1L);

        SiteCapabilitySnapshotEntity entity = new SiteCapabilitySnapshotEntity();
        entity.setSiteProfile(siteProfile);
        entity.setSupportedProtocols(fixture.supportedProtocols());
        entity.setSupportsResponses(fixture.snapshotResponses());
        entity.setSupportsEmbeddings(fixture.snapshotEmbeddings());
        entity.setSupportsAudio(fixture.snapshotAudio());
        entity.setSupportsImages(fixture.snapshotImages());
        entity.setSupportsModeration(fixture.snapshotModeration());
        entity.setSupportsFiles(fixture.snapshotFiles());
        entity.setSupportsUploads(fixture.snapshotUploads());
        entity.setSupportsBatches(fixture.snapshotBatches());
        entity.setSupportsTuning(fixture.snapshotTuning());
        entity.setSupportsRealtime(fixture.snapshotRealtime());
        entity.setAuthStrategy(policy.authStrategy());
        entity.setPathStrategy(policy.pathStrategy());
        entity.setErrorSchemaStrategy(policy.errorSchemaStrategy());
        entity.setHealthState("READY");
        entity.setRefreshedAt(Instant.now());
        return entity;
    }

    private static SiteModelCapabilityEntity siteModelCapability(ConformanceFixture fixture) {
        SiteModelCapabilityEntity entity = new SiteModelCapabilityEntity();
        entity.setSiteProfile(snapshot(fixture).getSiteProfile());
        entity.setModelName(fixture.requestedModel());
        entity.setModelKey(fixture.requestedModel());
        entity.setSupportedProtocols(fixture.supportedProtocols());
        entity.setSupportsChat(fixture.supportsChat());
        entity.setSupportsTools(fixture.supportsTools());
        entity.setSupportsImageInput(fixture.supportsImageInput());
        entity.setSupportsEmbeddings(fixture.supportsEmbeddings());
        entity.setSupportsCache(false);
        entity.setSupportsThinking(fixture.supportsThinking());
        entity.setSupportsVisibleReasoning(fixture.supportsThinking());
        entity.setSupportsReasoningReuse(false);
        entity.setReasoningTransport(fixture.supportsThinking() ? ReasoningTransport.OPENAI_CHAT : ReasoningTransport.NONE);
        entity.setCapabilityLevel(InteropCapabilityLevel.NATIVE);
        entity.setActive(true);
        entity.setSourceRefreshedAt(Instant.now());
        return entity;
    }

    private static UpstreamCredentialEntity credential(ConformanceFixture fixture) {
        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        credential.setCredentialName("credential");
        credential.setProviderType(fixture.providerType());
        credential.setBaseUrl("https://example.com");
        credential.setApiKeyCiphertext("cipher");
        credential.setApiKeyFingerprint("fp");
        credential.setActive(true);
        credential.setSiteProfileId(1L);
        setId(credential, 101L);
        return credential;
    }

    private static void setId(Object target, Long value) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record ConformanceFixture(
            String name,
            ProviderType providerType,
            ProviderFamily providerFamily,
            UpstreamSiteKind siteKind,
            String protocol,
            String requestPath,
            String requestedModel,
            List<String> supportedProtocols,
            boolean supportsChat,
            boolean supportsTools,
            boolean supportsImageInput,
            boolean supportsEmbeddings,
            boolean supportsThinking,
            boolean includeImageInput,
            boolean snapshotResponses,
            boolean snapshotEmbeddings,
            boolean snapshotAudio,
            boolean snapshotImages,
            boolean snapshotModeration,
            boolean snapshotFiles,
            boolean snapshotUploads,
            boolean snapshotBatches,
            boolean snapshotTuning,
            boolean snapshotRealtime,
            boolean expectedExecutable,
            String expectedEffectiveLevel,
            String expectedExecutionKind,
            boolean expectedModelVisible
    ) {
        @Override
        public String toString() {
            return name;
        }
    }
}
