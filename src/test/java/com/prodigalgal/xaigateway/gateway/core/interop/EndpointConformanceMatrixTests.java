package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.alias.ModelAliasQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlanCompilation;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
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
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class EndpointConformanceMatrixTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path MATRIX_ACTUAL_PATH = Path.of("build", "conformance", "endpoint-conformance-matrix.actual.json");
    private static final Path EXCEPTIONS_ACTUAL_PATH = Path.of("build", "conformance", "accepted-exceptions.actual.json");

    @Test
    void shouldMatchEndpointConformanceMatrixBaseline() throws IOException {
        List<EndpointConformanceRow> actualRows = endpointDefinitions().stream()
                .map(this::materializeRow)
                .toList();
        writeActual(MATRIX_ACTUAL_PATH, actualRows);

        List<EndpointConformanceRow> expectedRows = readResource(
                "conformance/endpoint-conformance-matrix.json",
                new TypeReference<>() {
                }
        );
        assertEquals(expectedRows, actualRows);
    }

    @Test
    void shouldMatchAcceptedExceptionsBaseline() throws IOException {
        List<AcceptedExceptionRecord> actualExceptions = acceptedExceptions();
        writeActual(EXCEPTIONS_ACTUAL_PATH, actualExceptions);

        List<AcceptedExceptionRecord> expectedExceptions = readResource(
                "conformance/accepted-exceptions.json",
                new TypeReference<>() {
                }
        );
        assertEquals(expectedExceptions, actualExceptions);
    }

    @Test
    void shouldKeepEvidenceSourcesResolvableAndMatrixRowsUnique() {
        List<EndpointConformanceRow> rows = endpointDefinitions().stream()
                .map(this::materializeRow)
                .toList();
        Map<String, AcceptedExceptionRecord> exceptionsById = acceptedExceptions().stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.id(), item), Map::putAll);

        LinkedHashSet<String> uniqueKeys = new LinkedHashSet<>();
        for (EndpointConformanceRow row : rows) {
            String key = String.join("|",
                    Objects.toString(row.protocol(), ""),
                    Objects.toString(row.requestPath(), ""),
                    Objects.toString(row.operation(), ""),
                    Objects.toString(row.providerType(), ""),
                    Objects.toString(row.siteKind(), "")
            );
            assertTrue(uniqueKeys.add(key), "发现重复的 conformance row: " + key);
            assertTrue(Files.exists(Path.of(row.evidenceSource())), "evidenceSource 不存在: " + row.evidenceSource());
            if (StringUtils.hasText(row.acceptedException())) {
                assertTrue(
                        exceptionsById.containsKey(row.acceptedException()),
                        "matrix row 引用了不存在的 accepted exception: " + row.acceptedException()
                );
            }
        }
    }

    private EndpointConformanceRow materializeRow(EndpointDefinition definition) {
        if (definition.staticRow()) {
            return new EndpointConformanceRow(
                    definition.protocol(),
                    definition.requestPath(),
                    definition.operation(),
                    definition.providerFamily() == null ? null : definition.providerFamily().name(),
                    definition.providerType() == null ? null : definition.providerType().name(),
                    definition.siteKind() == null ? null : definition.siteKind().name(),
                    definition.staticSupportStatus(),
                    definition.staticExecutionBackend(),
                    definition.staticExecutionKind(),
                    definition.staticDegradationLevel(),
                    definition.staticObjectMode(),
                    definition.staticRenderCapability(),
                    definition.staticRouteSelectionMode(),
                    definition.expectedResponseKind(),
                    definition.evidenceSource(),
                    definition.acceptedException(),
                    definition.blockerReason()
            );
        }

        ScenarioDefinition scenario = Objects.requireNonNull(
                scenarios().get(definition.scenarioId()),
                "未找到 conformance scenario: " + definition.scenarioId()
        );
        GatewayRequestFeatureService featureService = new GatewayRequestFeatureService();
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteCapabilitySnapshotEntity snapshot = snapshot(scenario);
        Mockito.when(snapshotRepository.findBySiteProfile_Id(1L)).thenReturn(Optional.of(snapshot));

        SiteCapabilityTruthService truthService = new SiteCapabilityTruthService(
                new UpstreamSitePolicyService(),
                snapshotRepository,
                new ExecutionSupportMatrixService()
        );

        CatalogCandidateView candidate = candidate(scenario, definition.requestedModel());
        RouteSelectionResult selectionResult = selectionResult(definition, candidate);
        GatewayRouteSelectionService routeSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
        Mockito.when(routeSelectionService.select(any())).thenReturn(selectionResult);

        NonChatTargetResolutionService targetResolutionService = Mockito.mock(NonChatTargetResolutionService.class);
        Mockito.when(targetResolutionService.resolve(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    GatewayRequestSemantics semantics = invocation.getArgument(2);
                    return switch (semantics.routeSelectionMode()) {
                        case LOCAL_CATALOG -> new NonChatTargetResolution(
                                RouteSelectionMode.LOCAL_CATALOG,
                                null,
                                "local_catalog",
                                List.of()
                        );
                        case STORED_LINEAGE -> new NonChatTargetResolution(
                                RouteSelectionMode.STORED_LINEAGE,
                                candidate,
                                "stored_lineage_binding",
                                List.of()
                        );
                        case DISTRIBUTED_TARGET -> new NonChatTargetResolution(
                                RouteSelectionMode.DISTRIBUTED_TARGET,
                                candidate,
                                "distributed_target_binding",
                                List.of()
                        );
                        case CATALOG_SELECTION -> new NonChatTargetResolution(
                                RouteSelectionMode.CATALOG_SELECTION,
                                null,
                                "catalog_selection",
                                List.of()
                        );
                    };
                });

        TranslationExecutionPlanCompiler compiler = new TranslationExecutionPlanCompiler(
                routeSelectionService,
                featureService,
                truthService,
                NonChatRoutePolicyService.forTests(truthService, new com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendPolicyService()),
                targetResolutionService,
                new NonChatDegradationPolicyService()
        );

        CanonicalExecutionPlanCompilation compilation = compiler.compilePreview(
                "sk-gw-test",
                definition.protocol(),
                definition.httpMethod(),
                definition.runtimePath(),
                definition.requestedModel(),
                GatewayDegradationPolicy.ALLOW_LOSSY,
                GatewayClientFamily.GENERIC_OPENAI,
                requestBody(definition)
        );

        var plan = compilation.canonicalPlan();
        String blockerReason = plan.blockerReasons().isEmpty()
                ? definition.blockerReason()
                : String.join(" | ", plan.blockerReasons());

        return new EndpointConformanceRow(
                definition.protocol(),
                definition.requestPath(),
                definition.operation(),
                definition.providerFamily().name(),
                definition.providerType().name(),
                definition.siteKind().name(),
                plan.supportStatus().name(),
                plan.executionBackend().name(),
                plan.executionKind().name(),
                plan.degradationLevel().name(),
                plan.objectMode(),
                plan.renderCapabilityLevel().name(),
                plan.routeSelectionMode().name(),
                definition.expectedResponseKind(),
                definition.evidenceSource(),
                definition.acceptedException(),
                blockerReason
        );
    }

    private JsonNode requestBody(EndpointDefinition definition) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        if (StringUtils.hasText(definition.requestedModel())) {
            root.put("model", definition.requestedModel());
        }
        return switch (definition.operation()) {
            case "chat_completion" -> chatBody(definition.protocol(), definition.requestedModel(), definition.runtimePath());
            case "response_create" -> root.put("input", "hello x-ai");
            case "embedding_create" -> {
                if ("google_native".equals(definition.protocol()) && definition.runtimePath().contains(":batchEmbedContents")) {
                    yield OBJECT_MAPPER.createObjectNode()
                            .putArray("requests")
                            .addObject()
                            .putObject("content")
                            .putArray("parts")
                            .addObject()
                            .put("text", "hello x-ai");
                }
                root.putObject("input").put("text", "hello x-ai");
                yield root;
            }
            case "audio_transcription" -> root.put("prompt", "hello x-ai");
            case "audio_speech" -> {
                if ("google_native".equals(definition.protocol())) {
                    yield googleGenerateContentBody("AUDIO");
                }
                yield root.put("input", "hello x-ai");
            }
            case "image_generation" -> {
                if ("google_native".equals(definition.protocol())) {
                    yield googleGenerateContentBody("IMAGE");
                }
                yield root.put("prompt", "hello x-ai");
            }
            case "moderation_create" -> root.put("input", "hello x-ai");
            case "file_create" -> root.put("filename", "doc.txt").put("purpose", "assistants").put("bytes", 12);
            default -> root;
        };
    }

    private ObjectNode chatBody(String protocol, String model, String runtimePath) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        if (StringUtils.hasText(model)) {
            root.put("model", model);
        }
        if ("anthropic_native".equals(protocol) || "/v1/messages".equals(runtimePath)) {
            root.putArray("messages")
                    .addObject()
                    .put("role", "user")
                    .putArray("content")
                    .addObject()
                    .put("type", "text")
                    .put("text", "hello x-ai");
            return root;
        }
        if ("google_native".equals(protocol) && runtimePath.contains(":generateContent")) {
            return googleGenerateContentBody(null);
        }
        root.putArray("messages")
                .addObject()
                .put("role", "user")
                .put("content", "hello x-ai");
        return root;
    }

    private ObjectNode googleGenerateContentBody(String modality) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.putArray("contents")
                .addObject()
                .put("role", "user")
                .putArray("parts")
                .addObject()
                .put("text", "hello x-ai");
        if (modality != null) {
            root.putObject("generationConfig")
                    .putArray("responseModalities")
                    .add(modality);
        }
        return root;
    }

    private void writeActual(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    private <T> T readResource(String resourcePath, TypeReference<T> typeReference) throws IOException {
        try (InputStream inputStream = EndpointConformanceMatrixTests.class.getClassLoader().getResourceAsStream(resourcePath)) {
            return OBJECT_MAPPER.readValue(Objects.requireNonNull(inputStream, resourcePath), typeReference);
        }
    }

    private CatalogCandidateView candidate(ScenarioDefinition scenario, String requestedModel) {
        UpstreamSitePolicyService.SitePolicy policy = new UpstreamSitePolicyService().policy(scenario.siteKind());
        return new CatalogCandidateView(
                101L,
                "conformance-candidate",
                scenario.providerType(),
                1L,
                scenario.providerFamily(),
                scenario.siteKind(),
                policy.authStrategy(),
                policy.pathStrategy(),
                policy.errorSchemaStrategy(),
                "https://example.com",
                requestedModel,
                requestedModel,
                scenario.supportedProtocols(),
                scenario.supportsChat(),
                scenario.supportsTools(),
                scenario.supportsImageInput(),
                scenario.supportsEmbeddings(),
                false,
                scenario.supportsThinking(),
                scenario.supportsThinking(),
                false,
                scenario.supportsThinking() ? ReasoningTransport.OPENAI_CHAT : ReasoningTransport.NONE,
                InteropCapabilityLevel.NATIVE
        );
    }

    private RouteSelectionResult selectionResult(EndpointDefinition definition, CatalogCandidateView candidate) {
        RouteCandidateView routeCandidateView = new RouteCandidateView(candidate, 11L, 10, 100);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                definition.requestedModel(),
                definition.requestedModel(),
                definition.requestedModel(),
                definition.protocol(),
                "prefix",
                "fingerprint",
                definition.requestedModel(),
                RouteSelectionSource.WEIGHTED_HASH,
                routeCandidateView,
                List.of(routeCandidateView)
        );
    }

    private SiteCapabilitySnapshotEntity snapshot(ScenarioDefinition scenario) {
        UpstreamSiteProfileEntity siteProfile = new UpstreamSiteProfileEntity();
        siteProfile.setProfileCode("site:" + scenario.siteKind().name().toLowerCase());
        siteProfile.setDisplayName(scenario.siteKind().name());
        siteProfile.setProviderFamily(scenario.providerFamily());
        siteProfile.setSiteKind(scenario.siteKind());
        UpstreamSitePolicyService.SitePolicy policy = new UpstreamSitePolicyService().policy(scenario.siteKind());
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
        entity.setSupportedProtocols(scenario.supportedProtocols());
        entity.setSupportsResponses(scenario.snapshotResponses());
        entity.setSupportsEmbeddings(scenario.snapshotEmbeddings());
        entity.setSupportsAudio(scenario.snapshotAudio());
        entity.setSupportsImages(scenario.snapshotImages());
        entity.setSupportsModeration(scenario.snapshotModeration());
        entity.setSupportsFiles(scenario.snapshotFiles());
        entity.setSupportsUploads(scenario.snapshotUploads());
        entity.setAuthStrategy(policy.authStrategy());
        entity.setPathStrategy(policy.pathStrategy());
        entity.setErrorSchemaStrategy(policy.errorSchemaStrategy());
        entity.setHealthState("READY");
        entity.setRefreshedAt(Instant.now());
        return entity;
    }

    private void setId(Object target, Long value) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Map<String, ScenarioDefinition> scenarios() {
        Map<String, ScenarioDefinition> scenarios = new LinkedHashMap<>();
        scenarios.put("openai-direct", new ScenarioDefinition(
                ProviderType.OPENAI_DIRECT,
                ProviderFamily.OPENAI,
                UpstreamSiteKind.OPENAI_DIRECT,
                List.of("openai", "responses"),
                true, true, true, true, true,
                true, true, true, true, true, true, true
        ));
        scenarios.put("openai-compatible", new ScenarioDefinition(
                ProviderType.OPENAI_COMPATIBLE,
                ProviderFamily.OPENAI,
                UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC,
                List.of("openai", "responses"),
                true, true, true, true, true,
                true, true, true, true, true, true, true
        ));
        scenarios.put("gemini-direct", new ScenarioDefinition(
                ProviderType.GEMINI_DIRECT,
                ProviderFamily.GEMINI,
                UpstreamSiteKind.GEMINI_DIRECT,
                List.of("google_native", "openai"),
                true, true, true, true, true,
                true, true, true, true, true, true, false
        ));
        scenarios.put("anthropic-direct", new ScenarioDefinition(
                ProviderType.ANTHROPIC_DIRECT,
                ProviderFamily.ANTHROPIC,
                UpstreamSiteKind.ANTHROPIC_DIRECT,
                List.of("anthropic_native", "openai"),
                true, true, true, false, true,
                false, false, false, false, false, true, false
        ));
        scenarios.put("anthropic-files", new ScenarioDefinition(
                ProviderType.ANTHROPIC_DIRECT,
                ProviderFamily.ANTHROPIC,
                UpstreamSiteKind.ANTHROPIC_DIRECT,
                List.of("anthropic_native", "openai"),
                true, true, true, false, true,
                false, false, false, false, false, true, false
        ));
        return scenarios;
    }

    private List<AcceptedExceptionRecord> acceptedExceptions() {
        return List.of(
                new AcceptedExceptionRecord(
                        "google-native-file-download-egress",
                        "provider_protocol",
                        "Google native files 当前不补 file download native egress。",
                        "Google native 不补 file download native egress。",
                        List.of("/v1beta/files/{fileName}:download")
                ),
                new AcceptedExceptionRecord(
                        "anthropic-files-no-native-ingress",
                        "provider_protocol",
                        "Anthropic files 仅通过现有 `/v1/files` surface 暴露，不新增 native ingress。",
                        "Anthropic files 不新增 native ingress。",
                        List.of("/v1/files", "/v1/files/{fileId}", "/v1/files/{fileId}/content")
                ),
                new AcceptedExceptionRecord(
                        "notion-mcp-auth-required",
                        "workflow",
                        "Notion MCP 当前 `Auth required`，最终回写依赖认证恢复。",
                        "Notion MCP 当前 `Auth required`，文档回写依赖认证恢复。",
                        List.of()
                )
        );
    }

    private List<EndpointDefinition> endpointDefinitions() {
        List<EndpointDefinition> definitions = new ArrayList<>();

        definitions.add(provider("openai", "POST", "/v1/chat/completions", "/v1/chat/completions", "chat_completion", ProviderFamily.OPENAI, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT, "gpt-4o-mini", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsControllerTests.java", null, null, "openai-direct"));
        definitions.add(provider("responses", "POST", "/v1/responses", "/v1/responses", "response_create", ProviderFamily.OPENAI, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT, "gpt-4o-mini", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesControllerTests.java", null, null, "openai-direct"));
        definitions.add(provider("openai", "POST", "/v1/embeddings", "/v1/embeddings", "embedding_create", ProviderFamily.OPENAI, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT, "text-embedding-3-small", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiEmbeddingsControllerTests.java", null, null, "openai-direct"));
        definitions.add(provider("openai", "POST", "/v1/audio/transcriptions", "/v1/audio/transcriptions", "audio_transcription", ProviderFamily.OPENAI, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT, "gpt-4o-mini-transcribe", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiAudioControllerTests.java", null, null, "openai-direct"));
        definitions.add(provider("openai", "POST", "/v1/audio/speech", "/v1/audio/speech", "audio_speech", ProviderFamily.OPENAI, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT, "gpt-4o-mini-tts", "binary", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiAudioControllerTests.java", null, null, "openai-direct"));
        definitions.add(provider("openai", "POST", "/v1/images/generations", "/v1/images/generations", "image_generation", ProviderFamily.OPENAI, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT, "gpt-image-1", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiImagesControllerTests.java", null, null, "openai-direct"));
        definitions.add(provider("openai", "POST", "/v1/moderations", "/v1/moderations", "moderation_create", ProviderFamily.OPENAI, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT, "omni-moderation-latest", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiModerationsControllerTests.java", null, null, "openai-direct"));

        definitions.add(provider("openai", "POST", "/v1/files", "/v1/files", "file_create", ProviderFamily.OPENAI, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiFilesControllerTests.java", null, null, "openai-direct"));
        definitions.add(provider("openai", "GET", "/v1/files", "/v1/files", "file_list", ProviderFamily.OPENAI, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiFilesControllerTests.java", null, null, "openai-direct"));
        definitions.add(provider("openai", "GET", "/v1/files/{fileId}", "/v1/files/file_123", "file_get", ProviderFamily.OPENAI, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiFilesControllerTests.java", null, null, "openai-direct"));
        definitions.add(provider("openai", "GET", "/v1/files/{fileId}/content", "/v1/files/file_123/content", "file_content_get", ProviderFamily.OPENAI, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT, "resource-orchestration", "binary", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiFilesControllerTests.java", null, null, "openai-direct"));
        definitions.add(provider("openai", "DELETE", "/v1/files/{fileId}", "/v1/files/file_123", "file_delete", ProviderFamily.OPENAI, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiFilesControllerTests.java", null, null, "openai-direct"));

        definitions.add(provider("openai", "POST", "/v1/uploads", "/v1/uploads", "upload_create", ProviderFamily.OPENAI, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiUploadsControllerTests.java", null, null, "openai-direct"));
        definitions.add(provider("openai", "GET", "/v1/uploads/{uploadId}", "/v1/uploads/upload_1", "upload_get", ProviderFamily.OPENAI, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiUploadsControllerTests.java", null, null, "openai-direct"));
        definitions.add(provider("openai", "POST", "/v1/uploads/{uploadId}/parts", "/v1/uploads/upload_1/parts", "upload_part_add", ProviderFamily.OPENAI, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiUploadsControllerTests.java", null, null, "openai-direct"));
        definitions.add(provider("openai", "POST", "/v1/uploads/{uploadId}/complete", "/v1/uploads/upload_1/complete", "upload_complete", ProviderFamily.OPENAI, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiUploadsControllerTests.java", null, null, "openai-direct"));
        definitions.add(provider("openai", "POST", "/v1/uploads/{uploadId}/cancel", "/v1/uploads/upload_1/cancel", "upload_cancel", ProviderFamily.OPENAI, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiUploadsControllerTests.java", null, null, "openai-direct"));


        definitions.add(provider("openai", "POST", "/v1/audio/translations", "/v1/audio/translations", "audio_translation", ProviderFamily.OPENAI, ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, "whisper-1", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiAudioControllerTests.java", null, null, "openai-compatible"));
        definitions.add(provider("openai", "POST", "/v1/images/edits", "/v1/images/edits", "image_edit", ProviderFamily.OPENAI, ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, "gpt-image-1", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiImagesControllerTests.java", null, null, "openai-compatible"));
        definitions.add(provider("openai", "POST", "/v1/images/variations", "/v1/images/variations", "image_variation", ProviderFamily.OPENAI, ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, "dall-e-2", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiImagesControllerTests.java", null, null, "openai-compatible"));

        definitions.add(provider("openai", "POST", "/v1/files", "/v1/files", "file_create", ProviderFamily.OPENAI, ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/gateway/core/interop/SiteCapabilityTruthServiceTests.java", null, null, "openai-compatible"));
        definitions.add(provider("openai", "GET", "/v1/files", "/v1/files", "file_list", ProviderFamily.OPENAI, ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/gateway/core/interop/SiteCapabilityTruthServiceTests.java", null, null, "openai-compatible"));
        definitions.add(provider("openai", "GET", "/v1/files/{fileId}", "/v1/files/file_123", "file_get", ProviderFamily.OPENAI, ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/gateway/core/interop/SiteCapabilityTruthServiceTests.java", null, null, "openai-compatible"));
        definitions.add(provider("openai", "GET", "/v1/files/{fileId}/content", "/v1/files/file_123/content", "file_content_get", ProviderFamily.OPENAI, ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, "resource-orchestration", "binary", "src/test/java/com/prodigalgal/xaigateway/gateway/core/interop/SiteCapabilityTruthServiceTests.java", null, null, "openai-compatible"));
        definitions.add(provider("openai", "DELETE", "/v1/files/{fileId}", "/v1/files/file_123", "file_delete", ProviderFamily.OPENAI, ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/gateway/core/interop/SiteCapabilityTruthServiceTests.java", null, null, "openai-compatible"));

        definitions.add(provider("openai", "POST", "/v1/uploads", "/v1/uploads", "upload_create", ProviderFamily.OPENAI, ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/gateway/core/interop/SiteCapabilityTruthServiceTests.java", null, null, "openai-compatible"));
        definitions.add(provider("openai", "GET", "/v1/uploads/{uploadId}", "/v1/uploads/upload_1", "upload_get", ProviderFamily.OPENAI, ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/gateway/core/interop/SiteCapabilityTruthServiceTests.java", null, null, "openai-compatible"));
        definitions.add(provider("openai", "POST", "/v1/uploads/{uploadId}/parts", "/v1/uploads/upload_1/parts", "upload_part_add", ProviderFamily.OPENAI, ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/gateway/core/interop/SiteCapabilityTruthServiceTests.java", null, null, "openai-compatible"));
        definitions.add(provider("openai", "POST", "/v1/uploads/{uploadId}/complete", "/v1/uploads/upload_1/complete", "upload_complete", ProviderFamily.OPENAI, ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/gateway/core/interop/SiteCapabilityTruthServiceTests.java", null, null, "openai-compatible"));
        definitions.add(provider("openai", "POST", "/v1/uploads/{uploadId}/cancel", "/v1/uploads/upload_1/cancel", "upload_cancel", ProviderFamily.OPENAI, ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/gateway/core/interop/SiteCapabilityTruthServiceTests.java", null, null, "openai-compatible"));

        definitions.add(provider("google_native", "POST", "/v1beta/models/{model}:generateContent", "/v1beta/models/gemini-2.5-pro:generateContent", "chat_completion", ProviderFamily.GEMINI, ProviderType.GEMINI_DIRECT, UpstreamSiteKind.GEMINI_DIRECT, "gemini-2.5-pro", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/google/GeminiGenerateContentControllerTests.java", null, null, "gemini-direct"));
        definitions.add(provider("google_native", "POST", "/v1beta/models/{model}:generateContent", "/v1beta/models/gemini-2.5-image:generateContent", "image_generation", ProviderFamily.GEMINI, ProviderType.GEMINI_DIRECT, UpstreamSiteKind.GEMINI_DIRECT, "gemini-2.5-image", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/google/GeminiGenerateContentControllerTests.java", null, null, "gemini-direct"));
        definitions.add(provider("openai", "POST", "/v1/images/edits", "/v1/images/edits", "image_edit", ProviderFamily.GEMINI, ProviderType.GEMINI_DIRECT, UpstreamSiteKind.GEMINI_DIRECT, "imagen-3.0-capability-001", "json", "src/test/java/com/prodigalgal/xaigateway/gateway/core/execution/GeminiImagesGatewayResourceExecutorTests.java", null, null, "gemini-direct"));
        definitions.add(provider("google_native", "POST", "/v1beta/models/{model}:generateContent", "/v1beta/models/gemini-2.5-flash-preview-tts:generateContent", "audio_speech", ProviderFamily.GEMINI, ProviderType.GEMINI_DIRECT, UpstreamSiteKind.GEMINI_DIRECT, "gemini-2.5-flash-preview-tts", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/google/GeminiGenerateContentControllerTests.java", null, null, "gemini-direct"));
        definitions.add(provider("google_native", "POST", "/v1beta/models/{model}:embedContent", "/v1beta/models/text-embedding-004:embedContent", "embedding_create", ProviderFamily.GEMINI, ProviderType.GEMINI_DIRECT, UpstreamSiteKind.GEMINI_DIRECT, "text-embedding-004", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/google/GeminiEmbeddingsControllerTests.java", null, null, "gemini-direct"));
        definitions.add(provider("google_native", "POST", "/v1beta/models/{model}:batchEmbedContents", "/v1beta/models/text-embedding-004:batchEmbedContents", "embedding_create", ProviderFamily.GEMINI, ProviderType.GEMINI_DIRECT, UpstreamSiteKind.GEMINI_DIRECT, "text-embedding-004", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/google/GeminiEmbeddingsControllerTests.java", null, null, "gemini-direct"));
        definitions.add(provider("google_native", "POST", "/upload/v1beta/files", "/upload/v1beta/files", "file_create", ProviderFamily.GEMINI, ProviderType.GEMINI_DIRECT, UpstreamSiteKind.GEMINI_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/google/GeminiFilesControllerTests.java", null, null, "gemini-direct"));
        definitions.add(provider("google_native", "GET", "/v1beta/files", "/v1beta/files", "file_list", ProviderFamily.GEMINI, ProviderType.GEMINI_DIRECT, UpstreamSiteKind.GEMINI_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/google/GeminiFilesControllerTests.java", null, null, "gemini-direct"));
        definitions.add(provider("google_native", "GET", "/v1beta/files/{fileName}", "/v1beta/files/file_123", "file_get", ProviderFamily.GEMINI, ProviderType.GEMINI_DIRECT, UpstreamSiteKind.GEMINI_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/google/GeminiFilesControllerTests.java", null, null, "gemini-direct"));
        definitions.add(provider("google_native", "DELETE", "/v1beta/files/{fileName}", "/v1beta/files/file_123", "file_delete", ProviderFamily.GEMINI, ProviderType.GEMINI_DIRECT, UpstreamSiteKind.GEMINI_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/google/GeminiFilesControllerTests.java", null, null, "gemini-direct"));
        definitions.add(provider("google_native", "POST", "/google/v1beta/models/{model}:embedContent", "/google/v1beta/models/text-embedding-004:embedContent", "embedding_create", ProviderFamily.GEMINI, ProviderType.GEMINI_DIRECT, UpstreamSiteKind.GEMINI_DIRECT, "text-embedding-004", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/google/GoogleNativeNamespaceControllerTests.java", null, null, "gemini-direct"));
        definitions.add(provider("google_native", "POST", "/google/upload/v1beta/files", "/google/upload/v1beta/files", "file_create", ProviderFamily.GEMINI, ProviderType.GEMINI_DIRECT, UpstreamSiteKind.GEMINI_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/google/GoogleNativeUploadNamespaceControllerTests.java", null, null, "gemini-direct"));
        definitions.add(provider("google_native", "GET", "/google/v1beta/files", "/google/v1beta/files", "file_list", ProviderFamily.GEMINI, ProviderType.GEMINI_DIRECT, UpstreamSiteKind.GEMINI_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/google/GoogleNativeNamespaceControllerTests.java", null, null, "gemini-direct"));

        definitions.add(provider("anthropic_native", "POST", "/v1/messages", "/v1/messages", "chat_completion", ProviderFamily.ANTHROPIC, ProviderType.ANTHROPIC_DIRECT, UpstreamSiteKind.ANTHROPIC_DIRECT, "claude-sonnet-4", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/anthropic/AnthropicMessagesControllerTests.java", null, null, "anthropic-direct"));
        definitions.add(provider("openai", "POST", "/v1/files", "/v1/files", "file_create", ProviderFamily.ANTHROPIC, ProviderType.ANTHROPIC_DIRECT, UpstreamSiteKind.ANTHROPIC_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiFilesControllerTests.java", null, null, "anthropic-files"));
        definitions.add(provider("openai", "GET", "/v1/files", "/v1/files", "file_list", ProviderFamily.ANTHROPIC, ProviderType.ANTHROPIC_DIRECT, UpstreamSiteKind.ANTHROPIC_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiFilesControllerTests.java", null, null, "anthropic-files"));
        definitions.add(provider("openai", "GET", "/v1/files/{fileId}", "/v1/files/file_123", "file_get", ProviderFamily.ANTHROPIC, ProviderType.ANTHROPIC_DIRECT, UpstreamSiteKind.ANTHROPIC_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiFilesControllerTests.java", null, null, "anthropic-files"));
        definitions.add(provider("openai", "GET", "/v1/files/{fileId}/content", "/v1/files/file_123/content", "file_content_get", ProviderFamily.ANTHROPIC, ProviderType.ANTHROPIC_DIRECT, UpstreamSiteKind.ANTHROPIC_DIRECT, "resource-orchestration", "binary", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiFilesControllerTests.java", null, null, "anthropic-files"));
        definitions.add(provider("openai", "DELETE", "/v1/files/{fileId}", "/v1/files/file_123", "file_delete", ProviderFamily.ANTHROPIC, ProviderType.ANTHROPIC_DIRECT, UpstreamSiteKind.ANTHROPIC_DIRECT, "resource-orchestration", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiFilesControllerTests.java", null, null, "anthropic-files"));

        definitions.add(control("interop", "/api/v1/interop/plan", "interop_plan", "json", "src/test/java/com/prodigalgal/xaigateway/gateway/core/interop/GatewayInteropPlanServiceTests.java"));
        definitions.add(control("public_resource", "/api/v1/caches", "cache_list", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/GatewayPublicResourceControllersTests.java"));
        definitions.add(control("public_resource", "/api/v1/caches/import", "cache_import", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/GatewayPublicResourceControllersTests.java"));
        definitions.add(control("public_resource", "/api/v1/resources/{resourceType}/{resourceId}/lineage", "resource_lineage", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/GatewayPublicResourceControllersTests.java"));
        definitions.add(control("public_resource", "/api/v1/operations", "operation_list", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/GatewayPublicResourceControllersTests.java"));
        definitions.add(control("public_resource", "/api/v1/operations/{operationName}:cancel", "operation_cancel", "json", "src/test/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/GatewayPublicResourceControllersTests.java"));
        definitions.add(control("admin", "/admin/translation/explain", "translation_explain", "json", "src/test/java/com/prodigalgal/xaigateway/admin/application/TranslationExplainServiceTests.java"));
        definitions.add(control("admin", "/admin/resource/execute", "admin_resource_execute", "json", "src/test/java/com/prodigalgal/xaigateway/admin/application/AdminResourceExecutionServiceTests.java"));
        definitions.add(control("admin", "/admin/resource/templates", "admin_resource_templates", "json", "src/test/java/com/prodigalgal/xaigateway/admin/application/AdminResourceExecutionServiceTests.java"));
        definitions.add(control("admin", "/admin/distributed-keys/{id}/client-config/downloads/{grantToken}", "distributed_key_one_time_config_download", "json", "src/test/java/com/prodigalgal/xaigateway/admin/application/DistributedKeyAdminServiceTests.java"));
        definitions.add(control("admin", "/admin/observability/traces/{requestId}", "observability_trace", "json", "src/test/java/com/prodigalgal/xaigateway/admin/application/ObservabilityQueryServiceTests.java"));
        definitions.add(ui("/provider-sites/:id", "provider_site_detail", "ui", "web/src/features/provider-sites/provider-site-detail-page.test.tsx"));
        definitions.add(ui("/capability-matrix", "capability_matrix", "ui", "web/src/features/provider-sites/capability-matrix-page.test.tsx"));
        definitions.add(ui("/translation-debug", "translation_debug", "ui", "web/src/features/provider-sites/translation-debug-page.test.tsx"));
        definitions.add(ui("/ops/logs", "ops_logs", "ui", "web/src/features/ops/ops-logs-page.test.tsx"));

        return List.copyOf(definitions);
    }

    private EndpointDefinition provider(
            String protocol,
            String httpMethod,
            String requestPath,
            String runtimePath,
            String operation,
            ProviderFamily providerFamily,
            ProviderType providerType,
            UpstreamSiteKind siteKind,
            String requestedModel,
            String expectedResponseKind,
            String evidenceSource,
            String acceptedException,
            String blockerReason,
            String scenarioId) {
        return new EndpointDefinition(
                protocol,
                httpMethod,
                requestPath,
                runtimePath,
                operation,
                providerFamily,
                providerType,
                siteKind,
                requestedModel,
                expectedResponseKind,
                evidenceSource,
                acceptedException,
                blockerReason,
                scenarioId,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private EndpointDefinition control(
            String protocol,
            String requestPath,
            String operation,
            String expectedResponseKind,
            String evidenceSource) {
        return new EndpointDefinition(
                protocol,
                "GET",
                requestPath,
                requestPath,
                operation,
                null,
                null,
                null,
                null,
                expectedResponseKind,
                evidenceSource,
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private EndpointDefinition ui(
            String requestPath,
            String operation,
            String expectedResponseKind,
            String evidenceSource) {
        return control("ui", requestPath, operation, expectedResponseKind, evidenceSource);
    }

    private record ScenarioDefinition(
            ProviderType providerType,
            ProviderFamily providerFamily,
            UpstreamSiteKind siteKind,
            List<String> supportedProtocols,
            boolean supportsChat,
            boolean supportsTools,
            boolean supportsImageInput,
            boolean supportsEmbeddings,
            boolean supportsThinking,
            boolean snapshotResponses,
            boolean snapshotEmbeddings,
            boolean snapshotAudio,
            boolean snapshotImages,
            boolean snapshotModeration,
            boolean snapshotFiles,
            boolean snapshotUploads
    ) {
    }

    private record EndpointDefinition(
            String protocol,
            String httpMethod,
            String requestPath,
            String runtimePath,
            String operation,
            ProviderFamily providerFamily,
            ProviderType providerType,
            UpstreamSiteKind siteKind,
            String requestedModel,
            String expectedResponseKind,
            String evidenceSource,
            String acceptedException,
            String blockerReason,
            String scenarioId,
            boolean staticRow,
            String staticSupportStatus,
            String staticExecutionBackend,
            String staticExecutionKind,
            String staticDegradationLevel,
            String staticObjectMode,
            String staticRenderCapability,
            String staticRouteSelectionMode
    ) {
    }

    private record EndpointConformanceRow(
            String protocol,
            String requestPath,
            String operation,
            String providerFamily,
            String providerType,
            String siteKind,
            String supportStatus,
            String executionBackend,
            String executionKind,
            String degradationLevel,
            String objectMode,
            String renderCapability,
            String routeSelectionMode,
            String expectedResponseKind,
            String evidenceSource,
            String acceptedException,
            String blockerReason
    ) {
    }

    private record AcceptedExceptionRecord(
            String id,
            String scope,
            String summary,
            String blockerReason,
            List<String> appliesTo
    ) {
    }
}
