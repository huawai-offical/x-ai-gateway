package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryService;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    private final WebClient.Builder webClientBuilder;

    public CredentialModelDiscoveryService(
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            ProviderSiteRegistryService providerSiteRegistryService,
            WebClient.Builder webClientBuilder) {
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.providerSiteRegistryService = providerSiteRegistryService;
        this.webClientBuilder = webClientBuilder;
    }

    @Transactional(readOnly = true)
    public CredentialConnectivityProbe probe(ProviderType providerType, String baseUrl, String apiKey) {
        Instant startedAt = Instant.now();
        List<DiscoveredModelDefinition> models = discover(providerType, baseUrl, apiKey);
        long latency = Duration.between(startedAt, Instant.now()).toMillis();
        return new CredentialConnectivityProbe(providerType, baseUrl, latency, models);
    }

    public CredentialRefreshResult refreshCredential(Long credentialId) {
        UpstreamCredentialEntity credential = getRequiredCredential(credentialId);
        String apiKey = credentialCryptoService.decrypt(credential.getApiKeyCiphertext());
        List<DiscoveredModelDefinition> models = discover(credential.getProviderType(), credential.getBaseUrl(), apiKey);
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

    private List<DiscoveredModelDefinition> discover(ProviderType providerType, String baseUrl, String apiKey) {
        return switch (providerType) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE -> discoverOpenAiCompatible(baseUrl, apiKey);
            case ANTHROPIC_DIRECT -> discoverAnthropic(baseUrl, apiKey);
            case GEMINI_DIRECT -> discoverGemini(baseUrl, apiKey);
            case OLLAMA_DIRECT -> discoverOllama(baseUrl);
        };
    }

    private List<DiscoveredModelDefinition> discoverOpenAiCompatible(String baseUrl, String apiKey) {
        JsonNode body = buildClient(baseUrl)
                .get()
                .uri(resolveOpenAiModelsPath(baseUrl))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(DISCOVERY_TIMEOUT)
                .block();

        return deduplicate(body.path("data")).stream()
                .map(modelId -> new DiscoveredModelDefinition(
                        modelId,
                        ModelIdNormalizer.normalize(modelId),
                        List.of("openai", "responses"),
                        !modelId.toLowerCase(Locale.ROOT).contains("embedding"),
                        modelId.toLowerCase(Locale.ROOT).contains("embedding"),
                        !modelId.toLowerCase(Locale.ROOT).contains("embedding"),
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
            result.add(new DiscoveredModelDefinition(
                    modelId,
                    ModelIdNormalizer.normalize(modelId),
                    List.of("ollama_native"),
                    true,
                    false,
                    false,
                    false,
                    false,
                    false,
                    ReasoningTransport.NONE
            ));
        }
        return result;
    }

    private WebClient buildClient(String baseUrl) {
        return webClientBuilder.clone()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .build();
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.replaceAll("/+$", "");
    }

    private String resolveOpenAiModelsPath(String baseUrl) {
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

    private boolean inferAnthropicThinking(String modelId) {
        String normalized = modelId.toLowerCase(Locale.ROOT);
        return normalized.contains("claude-3-7")
                || normalized.contains("claude-4")
                || normalized.contains("sonnet-4")
                || normalized.contains("opus-4");
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
}
