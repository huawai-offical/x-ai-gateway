package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryService;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Transactional
public class CredentialModelDiscoveryService {

    private static final Duration DISCOVERY_TIMEOUT = Duration.ofSeconds(20);
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final ProviderSiteRegistryService providerSiteRegistryService;
    private final UpstreamSitePolicyService upstreamSitePolicyService;
    private final CredentialMaterialResolver credentialMaterialResolver;
    private final WebClient.Builder webClientBuilder;

    public CredentialModelDiscoveryService(
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            ProviderSiteRegistryService providerSiteRegistryService,
            WebClient.Builder webClientBuilder) {
        this(
                upstreamCredentialRepository,
                credentialCryptoService,
                providerSiteRegistryService,
                new UpstreamSitePolicyService(),
                new CredentialMaterialResolver(new com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService(
                        null,
                        null,
                        null,
                        null
                ), credentialCryptoService, new com.fasterxml.jackson.databind.ObjectMapper()),
                webClientBuilder
        );
    }

    @Autowired
    public CredentialModelDiscoveryService(
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            ProviderSiteRegistryService providerSiteRegistryService,
            UpstreamSitePolicyService upstreamSitePolicyService,
            CredentialMaterialResolver credentialMaterialResolver,
            WebClient.Builder webClientBuilder) {
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.providerSiteRegistryService = providerSiteRegistryService;
        this.upstreamSitePolicyService = upstreamSitePolicyService;
        this.credentialMaterialResolver = credentialMaterialResolver;
        this.webClientBuilder = webClientBuilder;
    }

    @Transactional(readOnly = true)
    public CredentialConnectivityProbe probe(ProviderType providerType, String baseUrl, String apiKey) {
        return probe(providerType, baseUrl, CredentialAuthKind.API_KEY, apiKey, Map.of());
    }

    @Transactional(readOnly = true)
    public CredentialConnectivityProbe probe(
            ProviderType providerType,
            String baseUrl,
            CredentialAuthKind authKind,
            String secret,
            Map<String, Object> metadata) {
        Instant startedAt = Instant.now();
        ResolvedCredentialMaterial credentialMaterial = credentialMaterialResolver.resolveTransient(
                providerType,
                baseUrl,
                authKind,
                secret,
                metadata
        );
        List<DiscoveredModelDefinition> models = discover(providerType, baseUrl, credentialMaterial);
        long latency = Duration.between(startedAt, Instant.now()).toMillis();
        return new CredentialConnectivityProbe(providerType, baseUrl, latency, models);
    }

    public CredentialRefreshResult refreshCredential(Long credentialId) {
        UpstreamCredentialEntity credential = getRequiredCredential(credentialId);
        ResolvedCredentialMaterial credentialMaterial = credentialMaterialResolver.resolveStored(credential);
        List<DiscoveredModelDefinition> models = discover(credential.getProviderType(), credential.getBaseUrl(), credentialMaterial);
        if (credential.getSiteProfileId() == null) {
            credential.setSiteProfileId(providerSiteRegistryService.ensureSiteProfile(
                    credential.getProviderType(),
                    credential.getBaseUrl(),
                    null
            ).getId());
        }
        providerSiteRegistryService.refreshCapabilities(
                providerSiteRegistryService.ensureSiteProfile(
                        credential.getProviderType(),
                        credential.getBaseUrl(),
                        credential.getSiteProfileId()
                ),
                models
        );
        credential.setLastErrorAt(null);
        credential.setLastErrorCode(null);
        credential.setLastErrorMessage(null);
        credential.setCooldownUntil(null);
        credential.setLastUsedAt(Instant.now());
        upstreamCredentialRepository.save(credential);

        return new CredentialRefreshResult(credentialId, models, Instant.now());
    }

    private UpstreamCredentialEntity getRequiredCredential(Long credentialId) {
        Optional<UpstreamCredentialEntity> credential = upstreamCredentialRepository.findById(credentialId);
        if (credential.isEmpty() || credential.get().isDeleted()) {
            throw new IllegalArgumentException("未找到指定的上游凭证。");
        }
        return credential.get();
    }

    private List<DiscoveredModelDefinition> discover(
            ProviderType providerType,
            String baseUrl,
            ResolvedCredentialMaterial credentialMaterial) {
        UpstreamSiteKind siteKind = upstreamSitePolicyService.inferSiteKind(providerType, baseUrl);
        return switch (providerType) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE -> discoverOpenAiCompatible(siteKind, baseUrl, credentialMaterial);
            case ANTHROPIC_DIRECT -> discoverAnthropic(baseUrl, credentialMaterial.secret());
            case GEMINI_DIRECT -> siteKind == UpstreamSiteKind.VERTEX_AI
                    ? discoverVertex(baseUrl, credentialMaterial)
                    : discoverGemini(baseUrl, credentialMaterial.secret());
            case OLLAMA_DIRECT -> discoverOllama(baseUrl);
        };
    }

    private List<DiscoveredModelDefinition> discoverOpenAiCompatible(
            UpstreamSiteKind siteKind,
            String baseUrl,
            ResolvedCredentialMaterial credentialMaterial) {
        JsonNode body = buildOpenAiCompatibleClient(baseUrl, siteKind, credentialMaterial)
                .get()
                .uri(resolveOpenAiModelsPath(baseUrl, siteKind))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(DISCOVERY_TIMEOUT)
                .block();

        return deduplicate(body.path("data")).stream()
                .map(modelId -> new DiscoveredModelDefinition(
                        modelId,
                        ModelIdNormalizer.normalize(modelId),
                        upstreamSitePolicyService.policy(siteKind).supportedProtocols(),
                        !looksLikeEmbeddingModel(modelId),
                        !looksLikeEmbeddingModel(modelId),
                        !looksLikeEmbeddingModel(modelId),
                        looksLikeEmbeddingModel(modelId),
                        !looksLikeEmbeddingModel(modelId),
                        inferOpenAiThinking(modelId),
                        inferOpenAiThinking(modelId),
                        inferOpenAiThinking(modelId),
                        inferOpenAiThinking(modelId) ? ReasoningTransport.OPENAI_CHAT : ReasoningTransport.NONE
                ))
                .toList();
    }

    private List<DiscoveredModelDefinition> discoverAnthropic(String baseUrl, String apiKey) {
        JsonNode body = buildClient(baseUrl)
                .get()
                .uri("/v1/models")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(DISCOVERY_TIMEOUT)
                .block();

        return deduplicate(body.path("data")).stream()
                .map(modelId -> new DiscoveredModelDefinition(
                        modelId,
                        ModelIdNormalizer.normalize(modelId),
                        List.of("anthropic_native"),
                        true,
                        true,
                        true,
                        false,
                        true,
                        inferAnthropicThinking(modelId),
                        inferAnthropicThinking(modelId),
                        inferAnthropicThinking(modelId),
                        inferAnthropicThinking(modelId) ? ReasoningTransport.ANTHROPIC : ReasoningTransport.NONE
                ))
                .toList();
    }

    private List<DiscoveredModelDefinition> discoverGemini(String baseUrl, String apiKey) {
        JsonNode body = buildClient(baseUrl)
                .get()
                .uri(uriBuilder -> uriBuilder.path("/v1beta/models").queryParam("key", apiKey).build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(DISCOVERY_TIMEOUT)
                .block();

        List<DiscoveredModelDefinition> result = new ArrayList<>();
        for (JsonNode modelNode : body.path("models")) {
            String modelId = modelNode.path("name").asText();
            if (modelId == null || modelId.isBlank()) {
                continue;
            }
            List<String> actions = new ArrayList<>();
            modelNode.path("supportedGenerationMethods").forEach(node -> actions.add(node.asText()));
            boolean chat = actions.contains("generateContent");
            boolean embeddings = actions.contains("embedContent") || actions.contains("batchEmbedContents");
            boolean thinking = modelId.toLowerCase(Locale.ROOT).contains("2.5");
            result.add(new DiscoveredModelDefinition(
                    modelId,
                    ModelIdNormalizer.normalize(modelId),
                    List.of("google_native", "openai"),
                    chat,
                    chat,
                    chat,
                    embeddings,
                    true,
                    thinking,
                    thinking,
                    thinking,
                    thinking ? ReasoningTransport.GEMINI_THOUGHTS : ReasoningTransport.NONE
            ));
        }
        return result;
    }

    private List<DiscoveredModelDefinition> discoverVertex(
            String baseUrl,
            ResolvedCredentialMaterial credentialMaterial) {
        String projectId = requireMetadata(credentialMaterial.projectId(), "projectId");
        String location = requireMetadata(credentialMaterial.location(), "location");
        JsonNode body = buildClient(resolveVertexDiscoveryBaseUrl(baseUrl, location))
                .get()
                .uri("/v1beta1/publishers/google/models")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + credentialMaterial.secret())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(DISCOVERY_TIMEOUT)
                .block();

        JsonNode models = body == null || body.path("publisherModels").isMissingNode()
                ? body == null ? null : body.path("models")
                : body.path("publisherModels");
        if (models == null || !models.isArray()) {
            return List.of();
        }

        List<DiscoveredModelDefinition> result = new ArrayList<>();
        for (JsonNode modelNode : models) {
            String rawName = modelNode.path("name").asText();
            String modelId = extractVertexModelId(rawName);
            if (modelId == null || modelId.isBlank()) {
                continue;
            }
            boolean embeddings = modelId.toLowerCase(Locale.ROOT).contains("embedding");
            boolean chat = !embeddings;
            boolean thinking = modelId.toLowerCase(Locale.ROOT).contains("2.5");
            result.add(new DiscoveredModelDefinition(
                    modelId,
                    ModelIdNormalizer.normalize(modelId),
                    List.of("google_native"),
                    chat,
                    chat,
                    chat,
                    false,
                    true,
                    thinking,
                    thinking,
                    thinking,
                    thinking ? ReasoningTransport.GEMINI_THOUGHTS : ReasoningTransport.NONE
            ));
        }
        return result;
    }

    private List<DiscoveredModelDefinition> discoverOllama(String baseUrl) {
        JsonNode body = buildClient(baseUrl)
                .get()
                .uri("/api/tags")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(DISCOVERY_TIMEOUT)
                .block();

        List<DiscoveredModelDefinition> result = new ArrayList<>();
        for (JsonNode modelNode : body.path("models")) {
            String modelId = modelNode.path("name").asText(modelNode.path("model").asText());
            if (modelId == null || modelId.isBlank()) {
                continue;
            }
            OllamaModelCapability capability = inspectOllamaModel(baseUrl, modelId);
            result.add(new DiscoveredModelDefinition(
                    modelId,
                    ModelIdNormalizer.normalize(modelId),
                    List.of("openai", "responses", "anthropic_native", "google_native"),
                    true,
                    capability.supportsTools(),
                    capability.supportsImageInput(),
                    false,
                    false,
                    capability.supportsThinking(),
                    capability.supportsVisibleReasoning(),
                    false,
                    capability.supportsThinking() ? ReasoningTransport.OLLAMA_THINKING : ReasoningTransport.NONE
            ));
        }
        return result;
    }

    private OllamaModelCapability inspectOllamaModel(String baseUrl, String modelId) {
        JsonNode body = buildClient(baseUrl)
                .post()
                .uri("/api/show")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("model", modelId))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(DISCOVERY_TIMEOUT)
                .onErrorResume(error -> Mono.empty())
                .block();
        if (body == null || body.isMissingNode() || body.isNull()) {
            return inferOllamaModelCapability(modelId, Set.of());
        }

        Set<String> capabilities = new HashSet<>();
        for (JsonNode capability : body.path("capabilities")) {
            String value = capability.asText(null);
            if (value != null && !value.isBlank()) {
                capabilities.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return inferOllamaModelCapability(modelId, capabilities);
    }

    private WebClient buildClient(String baseUrl) {
        return webClientBuilder.clone()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .build();
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.replaceAll("/+$", "");
    }

    private WebClient buildOpenAiCompatibleClient(
            String baseUrl,
            UpstreamSiteKind siteKind,
            ResolvedCredentialMaterial credentialMaterial) {
        WebClient.Builder builder = webClientBuilder.clone()
                .baseUrl(normalizeBaseUrl(baseUrl));
        switch (upstreamSitePolicyService.policy(siteKind).authStrategy()) {
            case BEARER -> builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + credentialMaterial.secret());
            case API_KEY_HEADER -> builder.defaultHeader("x-api-key", credentialMaterial.secret());
            case AZURE_API_KEY -> builder.defaultHeader("api-key", credentialMaterial.secret());
            case API_KEY_QUERY, UNSUPPORTED -> {
            }
        }
        return builder.build();
    }

    private String resolveOpenAiModelsPath(String baseUrl, UpstreamSiteKind siteKind) {
        if (siteKind == UpstreamSiteKind.AZURE_OPENAI) {
            return "/openai/models?api-version=2024-10-21";
        }
        URI uri = URI.create(normalizeBaseUrl(baseUrl));
        return uri.getPath() != null && uri.getPath().endsWith("/v1") ? "/models" : "/v1/models";
    }

    private List<String> deduplicate(JsonNode arrayNode) {
        Map<String, String> values = new LinkedHashMap<>();
        arrayNode.forEach(node -> {
            String id = node.path("id").asText(node.path("name").asText());
            if (id != null && !id.isBlank()) {
                values.putIfAbsent(id, id);
            }
        });
        return List.copyOf(values.values());
    }

    private boolean inferOpenAiThinking(String modelId) {
        String normalized = modelId.toLowerCase(Locale.ROOT);
        return normalized.startsWith("o1")
                || normalized.startsWith("o3")
                || normalized.startsWith("o4")
                || normalized.startsWith("gpt-5");
    }

    private boolean looksLikeEmbeddingModel(String modelId) {
        String normalized = modelId.toLowerCase(Locale.ROOT);
        return normalized.contains("embedding")
                || normalized.startsWith("embed-")
                || normalized.startsWith("embed_")
                || normalized.equals("embed");
    }

    private boolean inferAnthropicThinking(String modelId) {
        String normalized = modelId.toLowerCase(Locale.ROOT);
        return normalized.contains("claude-3-7")
                || normalized.contains("claude-4")
                || normalized.contains("sonnet-4")
                || normalized.contains("opus-4");
    }

    private String resolveVertexDiscoveryBaseUrl(String baseUrl, String location) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (normalized.contains("://") && normalized.contains("aiplatform.googleapis.com")) {
            URI uri = URI.create(normalized);
            return uri.getScheme() + "://" + uri.getHost();
        }
        return "https://" + location + "-aiplatform.googleapis.com";
    }

    private String extractVertexModelId(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        int index = rawName.lastIndexOf('/');
        return index >= 0 ? rawName.substring(index + 1) : rawName;
    }

    private String requireMetadata(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Vertex 凭证缺少必需 metadata：" + key);
        }
        return value;
    }

    private OllamaModelCapability inferOllamaModelCapability(String modelId, Set<String> capabilities) {
        String normalized = modelId.toLowerCase(Locale.ROOT);
        boolean supportsTools = capabilities.contains("tools")
                || normalized.contains("gpt-oss")
                || normalized.contains("qwen")
                || normalized.contains("llama3")
                || normalized.contains("llama-3");
        boolean supportsImageInput = capabilities.contains("vision")
                || normalized.contains("vision")
                || normalized.contains("llava")
                || normalized.contains("bakllava")
                || normalized.contains("moondream")
                || normalized.contains("gemma3");
        boolean supportsThinking = capabilities.contains("thinking")
                || normalized.contains("gpt-oss")
                || normalized.contains("qwen3")
                || normalized.contains("deepseek-r1")
                || normalized.contains("deepseek-v3.1");
        return new OllamaModelCapability(supportsTools, supportsImageInput, supportsThinking, supportsThinking);
    }

    public record CredentialConnectivityProbe(
            ProviderType providerType,
            String baseUrl,
            long latencyMs,
            List<DiscoveredModelDefinition> models
    ) {
    }

    public record CredentialRefreshResult(
            Long credentialId,
            List<DiscoveredModelDefinition> models,
            Instant refreshedAt
    ) {
    }

    private record OllamaModelCapability(
            boolean supportsTools,
            boolean supportsImageInput,
            boolean supportsThinking,
            boolean supportsVisibleReasoning
    ) {
    }
}
