package com.prodigalgal.xaigateway.gateway.core.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityService;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Transactional
public class GatewayResourceExecutionService {

    private static final String DEFAULT_AZURE_API_VERSION = "2024-10-21";

    private final GatewayRouteSelectionService gatewayRouteSelectionService;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final GatewayObservabilityService gatewayObservabilityService;
    private final DistributedKeyGovernanceService distributedKeyGovernanceService;
    private final AccountSelectionService accountSelectionService;
    private final GatewayOpenAiPassthroughService gatewayOpenAiPassthroughService;
    private final SiteCapabilityTruthService siteCapabilityTruthService;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    public GatewayResourceExecutionService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            GatewayObservabilityService gatewayObservabilityService,
            DistributedKeyGovernanceService distributedKeyGovernanceService,
            AccountSelectionService accountSelectionService,
            GatewayOpenAiPassthroughService gatewayOpenAiPassthroughService,
            SiteCapabilityTruthService siteCapabilityTruthService,
            ObjectMapper objectMapper,
            WebClient.Builder webClientBuilder) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.gatewayObservabilityService = gatewayObservabilityService;
        this.distributedKeyGovernanceService = distributedKeyGovernanceService;
        this.accountSelectionService = accountSelectionService;
        this.gatewayOpenAiPassthroughService = gatewayOpenAiPassthroughService;
        this.siteCapabilityTruthService = siteCapabilityTruthService;
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    public ResponseEntity<JsonNode> executeJson(
            String distributedKeyPrefix,
            String requestPath,
            JsonNode requestBody,
            String defaultModel) {
        if ("/v1/embeddings".equals(requestPath)) {
            return executeEmbeddings(distributedKeyPrefix, requestBody, defaultModel);
        }
        return gatewayOpenAiPassthroughService.executeJson(distributedKeyPrefix, requestPath, requestBody, defaultModel);
    }

    public ResponseEntity<byte[]> executeBinaryJson(
            String distributedKeyPrefix,
            String requestPath,
            JsonNode requestBody,
            String defaultModel) {
        return gatewayOpenAiPassthroughService.executeBinaryJson(distributedKeyPrefix, requestPath, requestBody, defaultModel);
    }

    public Mono<ResponseEntity<JsonNode>> executeMultipartJson(
            String distributedKeyPrefix,
            String requestPath,
            String requestedModel,
            Map<String, String> formFields,
            Map<String, FilePart> files) {
        return gatewayOpenAiPassthroughService.executeMultipartJson(distributedKeyPrefix, requestPath, requestedModel, formFields, files);
    }

    public ResponseEntity<JsonNode> executeEmbeddings(String distributedKeyPrefix, JsonNode requestBody, String defaultModel) {
        ObjectNode payload = requireObjectPayload(requestBody, defaultModel);
        String requestedModel = payload.path("model").asText();

        String requestId = gatewayObservabilityService.nextRequestId();
        RouteSelectionResult selectionResult = gatewayRouteSelectionService.select(new RouteSelectionRequest(
                distributedKeyPrefix,
                "openai",
                "/v1/embeddings",
                requestedModel,
                payload,
                GatewayClientFamily.GENERIC_OPENAI,
                true
        ));
        gatewayObservabilityService.recordRouteDecision(requestId, selectionResult);

        UpstreamCredentialEntity credential = getRequiredCredential(selectionResult.selectedCandidate().candidate().credentialId());
        String apiKey = accountSelectionService.resolveActiveAccount(
                        selectionResult.distributedKeyId(),
                        selectionResult.selectedCandidate().candidate().providerType(),
                        selectionResult.clientFamily(),
                        300)
                .map(account -> credentialCryptoService.decrypt(account.getAccessTokenCiphertext()))
                .orElseGet(() -> credentialCryptoService.decrypt(credential.getApiKeyCiphertext()));

        try {
            ResponseEntity<JsonNode> response = switch (selectionResult.selectedCandidate().candidate().providerType()) {
                case OPENAI_DIRECT, OPENAI_COMPATIBLE -> executeOpenAiCompatibleEmbeddings(selectionResult, credential, apiKey, payload);
                case GEMINI_DIRECT -> executeGeminiEmbeddings(selectionResult, credential, apiKey, payload);
                case ANTHROPIC_DIRECT, OLLAMA_DIRECT ->
                        throw new IllegalArgumentException("当前站点不支持 embeddings 执行。");
            };
            if (response.getStatusCode().is2xxSuccessful()) {
                gatewayRouteSelectionService.recordSuccessfulSelection(selectionResult);
            } else {
                gatewayRouteSelectionService.invalidateSelection(selectionResult);
            }
            return response;
        } catch (RuntimeException exception) {
            gatewayRouteSelectionService.invalidateSelection(selectionResult);
            throw exception;
        } finally {
            distributedKeyGovernanceService.releaseConcurrency(selectionResult.governanceReservationKey());
        }
    }

    private ResponseEntity<JsonNode> executeOpenAiCompatibleEmbeddings(
            RouteSelectionResult selectionResult,
            UpstreamCredentialEntity credential,
            String apiKey,
            ObjectNode payload) {
        ObjectNode upstreamPayload = payload.deepCopy();
        upstreamPayload.put("model", selectionResult.resolvedModelKey());
        CatalogSiteRequest siteRequest = buildSiteRequest(selectionResult, credential, apiKey);
        ResponseEntity<JsonNode> upstreamResponse = siteRequest.client().post()
                .uri(siteRequest.path())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(upstreamPayload)
                .exchangeToMono(response -> response.toEntity(JsonNode.class))
                .block();
        if (upstreamResponse == null) {
            throw new IllegalStateException("上游 embeddings 响应为空。");
        }
        JsonNode body = upstreamResponse.getBody();
        if (body instanceof ObjectNode objectNode) {
            objectNode.put("model", selectionResult.publicModel());
        }
        return ResponseEntity.status(upstreamResponse.getStatusCode())
                .headers(upstreamResponse.getHeaders())
                .contentType(upstreamResponse.getHeaders().getContentType() == null
                        ? MediaType.APPLICATION_JSON
                        : upstreamResponse.getHeaders().getContentType())
                .body(body);
    }

    private ResponseEntity<JsonNode> executeGeminiEmbeddings(
            RouteSelectionResult selectionResult,
            UpstreamCredentialEntity credential,
            String apiKey,
            ObjectNode payload) {
        List<String> inputs = readEmbeddingInputs(payload.path("input"));
        CatalogSiteRequest siteRequest = buildSiteRequest(selectionResult, credential, apiKey);
        boolean batch = inputs.size() > 1;
        ObjectNode geminiPayload = objectMapper.createObjectNode();
        if (batch) {
            ArrayNode requests = geminiPayload.putArray("requests");
            for (String input : inputs) {
                requests.addObject()
                        .putObject("content")
                        .putArray("parts")
                        .addObject()
                        .put("text", input);
            }
        } else {
            geminiPayload.putObject("content")
                    .putArray("parts")
                    .addObject()
                    .put("text", inputs.isEmpty() ? "" : inputs.get(0));
        }

        String path = batch
                ? "/v1beta/models/" + encodePath(selectionResult.resolvedModelKey()) + ":batchEmbedContents?key=" + encodeQuery(apiKey)
                : "/v1beta/models/" + encodePath(selectionResult.resolvedModelKey()) + ":embedContent?key=" + encodeQuery(apiKey);

        JsonNode response = siteRequest.client().post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(geminiPayload)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null) {
            throw new IllegalStateException("Gemini embeddings 响应为空。");
        }

        ObjectNode openAiResponse = objectMapper.createObjectNode();
        openAiResponse.put("object", "list");
        openAiResponse.put("model", selectionResult.publicModel());
        ArrayNode data = openAiResponse.putArray("data");
        if (batch) {
            int index = 0;
            for (JsonNode item : response.path("embeddings")) {
                data.add(buildEmbeddingItem(index++, item.path("values")));
            }
        } else {
            data.add(buildEmbeddingItem(0, response.path("embedding").path("values")));
        }
        int promptTokens = response.path("usageMetadata").path("promptTokenCount").asInt(0);
        openAiResponse.putObject("usage")
                .put("prompt_tokens", promptTokens)
                .put("total_tokens", promptTokens);
        return ResponseEntity.ok(openAiResponse);
    }

    private ObjectNode buildEmbeddingItem(int index, JsonNode valuesNode) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("object", "embedding");
        item.put("index", index);
        ArrayNode embedding = item.putArray("embedding");
        for (JsonNode value : valuesNode) {
            embedding.add(value.asDouble());
        }
        return item;
    }

    private List<String> readEmbeddingInputs(JsonNode inputNode) {
        List<String> values = new ArrayList<>();
        if (inputNode == null || inputNode.isMissingNode() || inputNode.isNull()) {
            return values;
        }
        if (inputNode.isTextual()) {
            values.add(inputNode.asText());
            return values;
        }
        if (inputNode.isArray()) {
            for (JsonNode item : inputNode) {
                if (item.isTextual()) {
                    values.add(item.asText());
                }
            }
            return values;
        }
        throw new IllegalArgumentException("当前 embeddings 仅支持文本输入。");
    }

    private CatalogSiteRequest buildSiteRequest(RouteSelectionResult selectionResult, UpstreamCredentialEntity credential, String apiKey) {
        var candidate = selectionResult.selectedCandidate().candidate();
        WebClient.Builder builder = webClientBuilder.clone()
                .baseUrl(credential.getBaseUrl().replaceAll("/+$", ""));
        String path = "/v1/embeddings";
        if (candidate.pathStrategy() == PathStrategy.AZURE_OPENAI_DEPLOYMENT) {
            builder.defaultHeader("api-key", apiKey);
            path = "/openai/deployments/" + encodePath(selectionResult.resolvedModelKey()) + "/embeddings?api-version=" + DEFAULT_AZURE_API_VERSION;
        } else if (candidate.authStrategy() == AuthStrategy.BEARER) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        } else if (candidate.authStrategy() == AuthStrategy.API_KEY_HEADER) {
            builder.defaultHeader("x-api-key", apiKey);
        }
        return new CatalogSiteRequest(builder.build(), path);
    }

    private ObjectNode requireObjectPayload(JsonNode requestBody, String defaultModel) {
        if (requestBody == null || !requestBody.isObject()) {
            throw new IllegalArgumentException("请求体必须是 JSON object。");
        }
        ObjectNode payload = (ObjectNode) requestBody;
        if (!payload.hasNonNull("model")) {
            if (defaultModel == null || defaultModel.isBlank()) {
                throw new IllegalArgumentException("请求缺少 model。");
            }
            payload.put("model", defaultModel);
        }
        return payload;
    }

    private UpstreamCredentialEntity getRequiredCredential(Long credentialId) {
        Optional<UpstreamCredentialEntity> credential = upstreamCredentialRepository.findById(credentialId);
        if (credential.isEmpty() || credential.get().isDeleted()) {
            throw new IllegalArgumentException("未找到对应的上游凭证。");
        }
        return credential.get();
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record CatalogSiteRequest(
            WebClient client,
            String path
    ) {
    }
}
