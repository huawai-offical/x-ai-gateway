package com.prodigalgal.xaigateway.gateway.core.resource;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.batches.BatchCancelParams;
import com.anthropic.models.beta.messages.batches.BatchCreateParams;
import com.anthropic.models.beta.messages.batches.BatchRetrieveParams;
import com.anthropic.models.beta.messages.batches.BetaMessageBatch;
import com.google.genai.Client;
import com.google.genai.types.BatchJob;
import com.google.genai.types.BatchJobSource;
import com.google.genai.types.CancelBatchJobConfig;
import com.google.genai.types.CancelTuningJobConfig;
import com.google.genai.types.CreateBatchJobConfig;
import com.google.genai.types.CreateTuningJobConfig;
import com.google.genai.types.GetBatchJobConfig;
import com.google.genai.types.GetTuningJobConfig;
import com.google.genai.types.JobState;
import com.google.genai.types.TuningDataset;
import com.google.genai.types.TuningExample;
import com.google.genai.types.TuningJob;
import com.google.genai.types.TuningJobState;
import io.micrometer.observation.ObservationRegistry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.catalog.FineTunedModelRegistrationService;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileContent;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResponse;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayFileBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayFileEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayAsyncResourceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import com.prodigalgal.xaigateway.provider.adapter.anthropic.AnthropicChatModelFactory;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
public class GatewayAsyncResourceService {

    private static final int DEFAULT_LIST_LIMIT = 20;
    private static final int MAX_LIST_LIMIT = 100;
    private static final int CONVERSATION_ITEM_BATCH_LIMIT = 20;
    private static final String STORED_CHAT_RESOURCE_PREFIX = "chatcmpl_";

    private final GatewayAsyncResourceRepository gatewayAsyncResourceRepository;
    private final DistributedKeyQueryService distributedKeyQueryService;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final UpstreamSiteProfileRepository upstreamSiteProfileRepository;
    private final SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository;
    private final GatewayFileRepository gatewayFileRepository;
    private final GatewayFileBindingRepository gatewayFileBindingRepository;
    private final GatewayFileService gatewayFileService;
    private final CredentialCryptoService credentialCryptoService;
    private final CredentialMaterialResolver credentialMaterialResolver;
    private final SiteCapabilityTruthService siteCapabilityTruthService;
    private final FineTunedModelRegistrationService fineTunedModelRegistrationService;
    private final AnthropicChatModelFactory anthropicChatModelFactory;
    private final GeminiChatModelFactory geminiChatModelFactory;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final WebClient.Builder webClientBuilder;
    private final List<MediaProviderAdapter> mediaProviderAdapters;

    @Autowired
    public GatewayAsyncResourceService(
            GatewayAsyncResourceRepository gatewayAsyncResourceRepository,
            DistributedKeyQueryService distributedKeyQueryService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            GatewayFileRepository gatewayFileRepository,
            GatewayFileBindingRepository gatewayFileBindingRepository,
            GatewayFileService gatewayFileService,
            CredentialCryptoService credentialCryptoService,
            CredentialMaterialResolver credentialMaterialResolver,
            SiteCapabilityTruthService siteCapabilityTruthService,
            FineTunedModelRegistrationService fineTunedModelRegistrationService,
            AnthropicChatModelFactory anthropicChatModelFactory,
            GeminiChatModelFactory geminiChatModelFactory,
            ObjectMapper objectMapper,
            Clock clock,
            WebClient.Builder webClientBuilder) {
        this.gatewayAsyncResourceRepository = gatewayAsyncResourceRepository;
        this.distributedKeyQueryService = distributedKeyQueryService;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.upstreamSiteProfileRepository = upstreamSiteProfileRepository;
        this.siteCapabilitySnapshotRepository = siteCapabilitySnapshotRepository;
        this.gatewayFileRepository = gatewayFileRepository;
        this.gatewayFileBindingRepository = gatewayFileBindingRepository;
        this.gatewayFileService = gatewayFileService;
        this.credentialCryptoService = credentialCryptoService;
        this.credentialMaterialResolver = credentialMaterialResolver;
        this.siteCapabilityTruthService = siteCapabilityTruthService;
        this.fineTunedModelRegistrationService = fineTunedModelRegistrationService;
        this.anthropicChatModelFactory = anthropicChatModelFactory;
        this.geminiChatModelFactory = geminiChatModelFactory;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.webClientBuilder = webClientBuilder;
        this.mediaProviderAdapters = List.of(
                new GeminiVeoMediaProviderAdapter(objectMapper, clock),
                new SunoMusicMediaProviderAdapter(objectMapper, clock)
        );
    }

    public GatewayAsyncResourceService(
            GatewayAsyncResourceRepository gatewayAsyncResourceRepository,
            DistributedKeyQueryService distributedKeyQueryService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            GatewayFileRepository gatewayFileRepository,
            GatewayFileBindingRepository gatewayFileBindingRepository,
            CredentialCryptoService credentialCryptoService,
            CredentialMaterialResolver credentialMaterialResolver,
            SiteCapabilityTruthService siteCapabilityTruthService,
            GeminiChatModelFactory geminiChatModelFactory,
            ObjectMapper objectMapper,
            Clock clock,
            WebClient.Builder webClientBuilder) {
        this(
                gatewayAsyncResourceRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                siteCapabilitySnapshotRepository,
                gatewayFileRepository,
                gatewayFileBindingRepository,
                null,
                credentialCryptoService,
                credentialMaterialResolver,
                siteCapabilityTruthService,
                null,
                new AnthropicChatModelFactory(ObservationRegistry.NOOP),
                geminiChatModelFactory,
                objectMapper,
                clock,
                webClientBuilder
        );
    }

    public GatewayAsyncResourceService(
            GatewayAsyncResourceRepository gatewayAsyncResourceRepository,
            DistributedKeyQueryService distributedKeyQueryService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            GatewayFileRepository gatewayFileRepository,
            GatewayFileBindingRepository gatewayFileBindingRepository,
            CredentialCryptoService credentialCryptoService,
            CredentialMaterialResolver credentialMaterialResolver,
            SiteCapabilityTruthService siteCapabilityTruthService,
            AnthropicChatModelFactory anthropicChatModelFactory,
            GeminiChatModelFactory geminiChatModelFactory,
            ObjectMapper objectMapper,
            Clock clock,
            WebClient.Builder webClientBuilder) {
        this(
                gatewayAsyncResourceRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                siteCapabilitySnapshotRepository,
                gatewayFileRepository,
                gatewayFileBindingRepository,
                null,
                credentialCryptoService,
                credentialMaterialResolver,
                siteCapabilityTruthService,
                null,
                anthropicChatModelFactory,
                geminiChatModelFactory,
                objectMapper,
                clock,
                webClientBuilder
        );
    }

    public GatewayAsyncResourceService(
            GatewayAsyncResourceRepository gatewayAsyncResourceRepository,
            DistributedKeyQueryService distributedKeyQueryService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            GatewayFileRepository gatewayFileRepository,
            GatewayFileBindingRepository gatewayFileBindingRepository,
            CredentialCryptoService credentialCryptoService,
            SiteCapabilityTruthService siteCapabilityTruthService,
            ObjectMapper objectMapper,
            Clock clock,
            WebClient.Builder webClientBuilder) {
        this(
                gatewayAsyncResourceRepository,
                distributedKeyQueryService,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                siteCapabilitySnapshotRepository,
                gatewayFileRepository,
                gatewayFileBindingRepository,
                null,
                credentialCryptoService,
                new CredentialMaterialResolver(new com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService(
                        null,
                        null,
                        null,
                        null
                ), credentialCryptoService, objectMapper),
                siteCapabilityTruthService,
                null,
                new AnthropicChatModelFactory(ObservationRegistry.NOOP),
                new GeminiChatModelFactory(ObservationRegistry.NOOP),
                objectMapper,
                clock,
                webClientBuilder
        );
    }

    public JsonNode storeResponse(Long distributedKeyId, String requestModel, JsonNode requestPayload, JsonNode responsePayload) {
        return storeResponse(distributedKeyId, requestModel, requestPayload, responsePayload, null);
    }

    public JsonNode storeResponse(
            Long distributedKeyId,
            String requestModel,
            JsonNode requestPayload,
            JsonNode responsePayload,
            RouteSelectionResult routeSelection) {
        String resourceKey = "resp_" + UUID.randomUUID().toString().replace("-", "");
        ObjectNode storedResponse = copyObject(responsePayload);
        String upstreamObjectId = storedResponse.path("id").asText(null);
        boolean upstreamLineage = shouldPersistOpenAiResponseLineage(routeSelection, upstreamObjectId);
        storedResponse.put("id", resourceKey);
        if (!storedResponse.has("status")) {
            storedResponse.put("status", "completed");
        }

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("object_mode", upstreamLineage ? "upstream_response_with_local_lineage" : "gateway_response_object");
        if (upstreamLineage) {
            var candidate = routeSelection.selectedCandidate().candidate();
            metadata.put("upstream_object_id", upstreamObjectId);
            metadata.put("credential_id", candidate.credentialId());
            if (candidate.siteProfileId() != null) {
                metadata.put("site_profile_id", candidate.siteProfileId());
            }
            metadata.put("provider_type", candidate.providerType().name());
            metadata.put("public_model", routeSelection.publicModel());
            metadata.put("resolved_model_key", routeSelection.resolvedModelKey());
            metadata.put("upstream_status", responsePayload.path("status").asText(storedResponse.path("status").asText("completed")));
            metadata.put("upstream_synced_at", now().getEpochSecond());
        }
        appendEvent(metadata, "stored", storedResponse.path("status").asText("completed"));

        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey(resourceKey);
        entity.setDistributedKeyId(distributedKeyId);
        entity.setResourceType(GatewayAsyncResourceType.RESPONSE);
        entity.setRequestModel(requestModel);
        entity.setStatus(storedResponse.path("status").asText("completed"));
        if (upstreamLineage) {
            entity.setUpstreamObjectId(upstreamObjectId);
        }
        entity.setRequestPayloadJson(writeJson(requestPayload));
        entity.setResponsePayloadJson(writeJson(storedResponse));
        entity.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(entity);
        return storedResponse;
    }

    public JsonNode getResponse(String responseId, Long distributedKeyId) {
        return getResponse(responseId, distributedKeyId, null);
    }

    public JsonNode getResponse(String responseId, Long distributedKeyId, List<String> include) {
        GatewayAsyncResourceEntity entity = getRequired(responseId, GatewayAsyncResourceType.RESPONSE, distributedKeyId);
        ObjectNode metadata = readObject(entity.getMetadataJson());
        if (hasResponseUpstreamLineage(entity, metadata)) {
            return syncRemoteResponse(entity, metadata, include);
        }
        return readJson(entity.getResponsePayloadJson());
    }

    public JsonNode deleteResponse(String responseId, Long distributedKeyId) {
        GatewayAsyncResourceEntity entity = getRequired(responseId, GatewayAsyncResourceType.RESPONSE, distributedKeyId);
        ObjectNode metadata = readObject(entity.getMetadataJson());
        if (hasResponseUpstreamLineage(entity, metadata)) {
            return deleteRemoteResponse(entity, metadata);
        }
        assertStoredResponse(entity);
        entity.setDeleted(true);
        entity.setStatus("deleted");
        entity.setMetadataJson(writeJson(appendEvent(readObject(entity.getMetadataJson()), "deleted", "deleted")));
        gatewayAsyncResourceRepository.save(entity);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", responseId);
        payload.put("object", "response");
        payload.put("deleted", true);
        return payload;
    }

    public JsonNode cancelResponse(String responseId, Long distributedKeyId) {
        GatewayAsyncResourceEntity entity = getRequired(responseId, GatewayAsyncResourceType.RESPONSE, distributedKeyId);
        ObjectNode metadata = readObject(entity.getMetadataJson());
        if (hasResponseUpstreamLineage(entity, metadata)) {
            return cancelRemoteResponse(entity, metadata);
        }
        ObjectNode response = assertStoredResponse(entity);
        String status = entity.getStatus() == null ? response.path("status").asText("completed") : entity.getStatus();
        if ("cancelled".equalsIgnoreCase(status) || "canceled".equalsIgnoreCase(status)) {
            return response;
        }
        if (!readObject(entity.getRequestPayloadJson()).path("background").asBoolean(false)) {
            throw new IllegalArgumentException("只有 background=true 的 Response 支持取消。");
        }
        if (isTerminalResponseStatus(status)) {
            throw new IllegalArgumentException("已完成的 Response 不允许取消。");
        }
        entity.setStatus("cancelled");
        response.put("status", "cancelled");
        response.put("cancelled_at", now().getEpochSecond());
        entity.setResponsePayloadJson(writeJson(response));
        entity.setMetadataJson(writeJson(appendEvent(readObject(entity.getMetadataJson()), "cancelled", "cancelled")));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    @Transactional(readOnly = true)
    public JsonNode listResponseInputItems(String responseId, Long distributedKeyId, String after, Integer limit, String order) {
        return listResponseInputItems(responseId, distributedKeyId, after, null, limit, order);
    }

    @Transactional(readOnly = true)
    public JsonNode listResponseInputItems(String responseId, Long distributedKeyId, String after, List<String> include, Integer limit, String order) {
        GatewayAsyncResourceEntity entity = getRequired(responseId, GatewayAsyncResourceType.RESPONSE, distributedKeyId);
        ObjectNode metadata = readObject(entity.getMetadataJson());
        if (hasResponseUpstreamLineage(entity, metadata)) {
            return listRemoteResponseInputItems(entity, metadata, after, include, limit, order);
        }
        assertStoredResponse(entity);
        int pageSize = normalizeListLimit(limit);
        String normalizedOrder = normalizeResponseInputItemsOrder(order);
        List<JsonNode> ordered = responseInputItems(responseId, readObject(entity.getRequestPayloadJson()).path("input"));
        if ("desc".equals(normalizedOrder)) {
            java.util.Collections.reverse(ordered);
        }
        if (after != null && !after.isBlank()) {
            int cursor = -1;
            for (int index = 0; index < ordered.size(); index++) {
                if (after.equals(ordered.get(index).path("id").asText())) {
                    cursor = index;
                    break;
                }
            }
            ordered = cursor < 0 ? List.of() : ordered.subList(cursor + 1, ordered.size());
        }

        boolean hasMore = ordered.size() > pageSize;
        List<JsonNode> page = ordered.stream().limit(pageSize).toList();
        var data = objectMapper.createArrayNode();
        page.forEach(data::add);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "list");
        response.set("data", data);
        response.put("has_more", hasMore);
        if (!page.isEmpty()) {
            response.put("first_id", page.getFirst().path("id").asText());
            response.put("last_id", page.getLast().path("id").asText());
        }
        return response;
    }

    public JsonNode createConversation(Long distributedKeyId, JsonNode requestBody) {
        ObjectNode request = optionalObject(requestBody);
        List<JsonNode> initialItems = conversationItemBatch(request.path("items"), false);
        ObjectNode metadata = metadataObject(request.path("metadata"));
        String conversationId = "conv_" + UUID.randomUUID().toString().replace("-", "");
        ObjectNode conversation = conversationPayload(conversationId, metadata, now().getEpochSecond());

        ObjectNode resourceMetadata = objectMapper.createObjectNode();
        resourceMetadata.put("object_mode", "gateway_conversation");
        appendEvent(resourceMetadata, "created", "active");

        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey(conversationId);
        entity.setDistributedKeyId(distributedKeyId);
        entity.setResourceType(GatewayAsyncResourceType.CONVERSATION);
        entity.setStatus("active");
        entity.setRequestPayloadJson(writeJson(request));
        entity.setResponsePayloadJson(writeJson(conversation));
        entity.setMetadataJson(writeJson(resourceMetadata));
        gatewayAsyncResourceRepository.save(entity);

        for (JsonNode item : initialItems) {
            saveConversationItem(distributedKeyId, conversationId, item, "created_with_conversation");
        }
        return conversation;
    }

    @Transactional(readOnly = true)
    public JsonNode getConversation(String conversationId, Long distributedKeyId) {
        GatewayAsyncResourceEntity entity = getRequired(conversationId, GatewayAsyncResourceType.CONVERSATION, distributedKeyId);
        return readJson(entity.getResponsePayloadJson());
    }

    public JsonNode updateConversation(String conversationId, Long distributedKeyId, JsonNode requestBody) {
        GatewayAsyncResourceEntity entity = getRequired(conversationId, GatewayAsyncResourceType.CONVERSATION, distributedKeyId);
        ObjectNode request = optionalObject(requestBody);
        ObjectNode response = readObject(entity.getResponsePayloadJson());
        if (request.has("metadata")) {
            response.set("metadata", metadataObject(request.path("metadata")));
        }
        entity.setResponsePayloadJson(writeJson(response));
        entity.setMetadataJson(writeJson(appendEvent(readObject(entity.getMetadataJson()), "metadata_updated", entity.getStatus())));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    public JsonNode deleteConversation(String conversationId, Long distributedKeyId) {
        GatewayAsyncResourceEntity entity = getRequired(conversationId, GatewayAsyncResourceType.CONVERSATION, distributedKeyId);
        entity.setDeleted(true);
        entity.setStatus("deleted");
        entity.setMetadataJson(writeJson(appendEvent(readObject(entity.getMetadataJson()), "deleted", "deleted")));
        gatewayAsyncResourceRepository.save(entity);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", conversationId);
        payload.put("object", "conversation.deleted");
        payload.put("deleted", true);
        return payload;
    }

    public JsonNode createConversationItems(
            String conversationId,
            Long distributedKeyId,
            JsonNode requestBody,
            List<String> include) {
        ensureConversationExists(conversationId, distributedKeyId);
        ObjectNode request = optionalObject(requestBody);
        List<JsonNode> items = conversationItemBatch(request.path("items"), true);
        List<JsonNode> created = new ArrayList<>();
        for (JsonNode item : items) {
            created.add(saveConversationItem(distributedKeyId, conversationId, item, "created"));
        }
        return listEnvelope(created, false);
    }

    @Transactional(readOnly = true)
    public JsonNode listConversationItems(
            String conversationId,
            Long distributedKeyId,
            String after,
            List<String> include,
            Integer limit,
            String order) {
        ensureConversationExists(conversationId, distributedKeyId);
        int pageSize = normalizeListLimit(limit);
        String normalizedOrder = normalizeResponseInputItemsOrder(order);
        ResourceCursor cursor = conversationItemCursor(conversationId, distributedKeyId, after);
        List<GatewayAsyncResourceEntity> entities = fetchConversationItemCandidates(
                distributedKeyId,
                conversationId,
                normalizedOrder,
                cursor,
                pageSize + 1
        );
        boolean hasMore = entities.size() > pageSize;
        List<JsonNode> page = entities.stream()
                .limit(pageSize)
                .map(entity -> readJson(entity.getResponsePayloadJson()))
                .toList();
        return listEnvelope(page, hasMore);
    }

    @Transactional(readOnly = true)
    public JsonNode getConversationItem(
            String conversationId,
            String itemId,
            Long distributedKeyId,
            List<String> include) {
        ensureConversationExists(conversationId, distributedKeyId);
        GatewayAsyncResourceEntity entity = getRequired(itemId, GatewayAsyncResourceType.CONVERSATION_ITEM, distributedKeyId);
        if (!conversationId.equals(entity.getUpstreamObjectId())) {
            throw new IllegalArgumentException("Conversation Item 不属于指定 Conversation。");
        }
        return readJson(entity.getResponsePayloadJson());
    }

    public JsonNode deleteConversationItem(String conversationId, String itemId, Long distributedKeyId) {
        GatewayAsyncResourceEntity conversation = getRequired(conversationId, GatewayAsyncResourceType.CONVERSATION, distributedKeyId);
        GatewayAsyncResourceEntity entity = getRequired(itemId, GatewayAsyncResourceType.CONVERSATION_ITEM, distributedKeyId);
        if (!conversationId.equals(entity.getUpstreamObjectId())) {
            throw new IllegalArgumentException("Conversation Item 不属于指定 Conversation。");
        }
        entity.setDeleted(true);
        entity.setStatus("deleted");
        entity.setMetadataJson(writeJson(appendEvent(readObject(entity.getMetadataJson()), "deleted", "deleted")));
        gatewayAsyncResourceRepository.save(entity);
        conversation.setMetadataJson(writeJson(appendEvent(readObject(conversation.getMetadataJson()), "item_deleted", conversation.getStatus())));
        gatewayAsyncResourceRepository.save(conversation);
        return readJson(conversation.getResponsePayloadJson());
    }

    public JsonNode createVectorStore(Long distributedKeyId, JsonNode requestBody) {
        ObjectNode request = optionalObject(requestBody);
        List<String> fileIds = vectorStoreFileIds(request.path("file_ids"));
        String vectorStoreId = "vs_" + UUID.randomUUID().toString().replace("-", "");
        ObjectNode payload = vectorStorePayload(vectorStoreId, request, now().getEpochSecond(), fileIds.size());

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("object_mode", "gateway_vector_store");
        metadata.put("local_lifecycle_only", true);
        appendEvent(metadata, "created", "completed");

        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey(vectorStoreId);
        entity.setDistributedKeyId(distributedKeyId);
        entity.setResourceType(GatewayAsyncResourceType.VECTOR_STORE);
        entity.setStatus("completed");
        entity.setRequestPayloadJson(writeJson(request));
        entity.setResponsePayloadJson(writeJson(payload));
        entity.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(entity);
        for (String fileId : fileIds) {
            saveVectorStoreFileAttachment(distributedKeyId, vectorStoreId, fileId, request, "created_with_vector_store");
        }
        return payload;
    }

    @Transactional(readOnly = true)
    public JsonNode listVectorStores(Long distributedKeyId, String after, Integer limit, String order) {
        int pageSize = normalizeListLimit(limit);
        String normalizedOrder = normalizeResponseInputItemsOrder(order);
        ResourceCursor cursor = vectorStoreCursor(distributedKeyId, after);
        if (cursor != null && cursor.invalid()) {
            return listEnvelope(List.of(), false);
        }
        List<GatewayAsyncResourceEntity> entities = fetchVectorStoreCandidates(
                distributedKeyId,
                normalizedOrder,
                cursor,
                pageSize + 1
        );
        boolean hasMore = entities.size() > pageSize;
        List<JsonNode> page = entities.stream()
                .limit(pageSize)
                .map(entity -> readJson(entity.getResponsePayloadJson()))
                .toList();
        return listEnvelope(page, hasMore);
    }

    @Transactional(readOnly = true)
    public JsonNode getVectorStore(String vectorStoreId, Long distributedKeyId) {
        GatewayAsyncResourceEntity entity = getRequired(vectorStoreId, GatewayAsyncResourceType.VECTOR_STORE, distributedKeyId);
        return readJson(entity.getResponsePayloadJson());
    }

    public JsonNode updateVectorStore(String vectorStoreId, Long distributedKeyId, JsonNode requestBody) {
        GatewayAsyncResourceEntity entity = getRequired(vectorStoreId, GatewayAsyncResourceType.VECTOR_STORE, distributedKeyId);
        ObjectNode request = optionalObject(requestBody);
        ObjectNode response = readObject(entity.getResponsePayloadJson());
        if (request.has("name")) {
            response.put("name", request.path("name").asText(""));
        }
        if (request.has("metadata")) {
            response.set("metadata", metadataObject(request.path("metadata")));
        }
        if (request.has("expires_after")) {
            response.set("expires_after", vectorStoreExpiresAfter(request.path("expires_after")));
        }
        if (request.has("expires_at")) {
            setNullableLong(response, "expires_at", request.path("expires_at"));
        }
        response.put("last_active_at", now().getEpochSecond());
        entity.setResponsePayloadJson(writeJson(response));
        entity.setMetadataJson(writeJson(appendEvent(readObject(entity.getMetadataJson()), "updated", entity.getStatus())));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    public JsonNode deleteVectorStore(String vectorStoreId, Long distributedKeyId) {
        GatewayAsyncResourceEntity entity = getRequired(vectorStoreId, GatewayAsyncResourceType.VECTOR_STORE, distributedKeyId);
        entity.setDeleted(true);
        entity.setStatus("deleted");
        entity.setMetadataJson(writeJson(appendEvent(readObject(entity.getMetadataJson()), "deleted", "deleted")));
        gatewayAsyncResourceRepository.save(entity);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", vectorStoreId);
        payload.put("object", "vector_store.deleted");
        payload.put("deleted", true);
        return payload;
    }

    public JsonNode createVectorStoreFile(String vectorStoreId, Long distributedKeyId, JsonNode requestBody) {
        GatewayAsyncResourceEntity vectorStore = getRequired(vectorStoreId, GatewayAsyncResourceType.VECTOR_STORE, distributedKeyId);
        ObjectNode request = optionalObject(requestBody);
        String fileId = text(request, "file_id");
        if (fileId == null) {
            throw new IllegalArgumentException("file_id 为必填字段。");
        }
        if (findActiveVectorStoreFileEntity(vectorStoreId, fileId, distributedKeyId).isPresent()) {
            throw new IllegalArgumentException("Vector Store 已关联该 file_id。");
        }
        JsonNode payload = saveVectorStoreFileAttachment(distributedKeyId, vectorStoreId, fileId, request, "created");
        adjustVectorStoreFileCount(vectorStore, 1, "file_attached");
        return payload;
    }

    @Transactional(readOnly = true)
    public JsonNode listVectorStoreFiles(
            String vectorStoreId,
            Long distributedKeyId,
            String after,
            Integer limit,
            String order,
            String filter) {
        getRequired(vectorStoreId, GatewayAsyncResourceType.VECTOR_STORE, distributedKeyId);
        int pageSize = normalizeListLimit(limit);
        String normalizedOrder = normalizeResponseInputItemsOrder(order);
        String normalizedFilter = normalizeNullable(filter);
        ResourceCursor cursor = vectorStoreFileCursor(vectorStoreId, distributedKeyId, after);
        if (cursor != null && cursor.invalid()) {
            return listEnvelope(List.of(), false);
        }

        int batchSize = pageSize + 1;
        List<JsonNode> collected = new ArrayList<>();
        ResourceCursor currentCursor = cursor;
        boolean exhausted = false;
        while (collected.size() <= pageSize && !exhausted) {
            List<GatewayAsyncResourceEntity> candidates = fetchVectorStoreFileCandidates(
                    distributedKeyId,
                    vectorStoreId,
                    normalizedOrder,
                    currentCursor,
                    batchSize
            );
            if (candidates.isEmpty()) {
                break;
            }
            for (GatewayAsyncResourceEntity entity : candidates) {
                currentCursor = ResourceCursor.from(entity);
                ObjectNode payload = readObject(entity.getResponsePayloadJson());
                if (normalizedFilter != null && !normalizedFilter.equals(payload.path("status").asText())) {
                    continue;
                }
                collected.add(payload);
                if (collected.size() > pageSize) {
                    break;
                }
            }
            exhausted = candidates.size() < batchSize;
        }

        boolean hasMore = collected.size() > pageSize;
        return listEnvelope(collected.stream().limit(pageSize).toList(), hasMore);
    }

    @Transactional(readOnly = true)
    public JsonNode getVectorStoreFile(String vectorStoreId, String fileId, Long distributedKeyId) {
        getRequired(vectorStoreId, GatewayAsyncResourceType.VECTOR_STORE, distributedKeyId);
        GatewayAsyncResourceEntity entity = getRequiredVectorStoreFile(vectorStoreId, fileId, distributedKeyId);
        return readJson(entity.getResponsePayloadJson());
    }

    @Transactional(readOnly = true)
    public JsonNode getVectorStoreFileContent(String vectorStoreId, String fileId, Long distributedKeyId) {
        getRequired(vectorStoreId, GatewayAsyncResourceType.VECTOR_STORE, distributedKeyId);
        GatewayAsyncResourceEntity entity = getRequiredVectorStoreFile(vectorStoreId, fileId, distributedKeyId);
        ObjectNode attachment = readObject(entity.getResponsePayloadJson());
        GatewayFileContent fileContent = resolveGatewayFileContent(fileId, distributedKeyId);
        String text = new String(fileContent.bytes(), StandardCharsets.UTF_8);

        ObjectNode item = objectMapper.createObjectNode();
        item.put("type", "text");
        item.put("text", text);

        ObjectNode page = objectMapper.createObjectNode();
        page.put("object", "vector_store.file_content.page");
        page.put("file_id", fileId);
        page.put("filename", fileContent.metadata().filename());
        page.set("attributes", copyObject(attachment.path("attributes")));
        ArrayNode data = page.putArray("data");
        data.add(item);
        page.set("content", data.deepCopy());
        page.put("has_more", false);
        page.putNull("next_page");
        return page;
    }

    @Transactional(readOnly = true)
    public JsonNode searchVectorStore(String vectorStoreId, Long distributedKeyId, JsonNode requestBody) {
        getRequired(vectorStoreId, GatewayAsyncResourceType.VECTOR_STORE, distributedKeyId);
        ObjectNode request = requireObject(requestBody);
        List<String> queries = vectorStoreSearchQueries(request.path("query"));
        int maxResults = vectorStoreSearchMaxResults(request.path("max_num_results"));
        double scoreThreshold = vectorStoreSearchScoreThreshold(request.path("ranking_options"));
        JsonNode filters = request.path("filters");
        validateVectorStoreSearchFilters(filters);
        List<VectorStoreSearchResult> matches = gatewayAsyncResourceRepository
                .findAllByDistributedKeyIdAndUpstreamObjectIdAndDeletedFalse(distributedKeyId, vectorStoreId)
                .stream()
                .filter(entity -> entity.getResourceType() == GatewayAsyncResourceType.VECTOR_STORE_FILE)
                .filter(entity -> "completed".equals(entity.getStatus()))
                .map(entity -> readObject(entity.getResponsePayloadJson()))
                .filter(payload -> vectorStoreSearchFiltersMatch(payload.path("attributes"), filters))
                .flatMap(payload -> vectorStoreSearchResult(payload, distributedKeyId, queries).stream())
                .filter(result -> result.score() >= scoreThreshold)
                .sorted(Comparator.comparingDouble(VectorStoreSearchResult::score).reversed()
                        .thenComparing(VectorStoreSearchResult::fileId))
                .toList();

        ObjectNode page = objectMapper.createObjectNode();
        page.put("object", "vector_store.search_results.page");
        page.set("search_query", vectorStoreSearchQueryResponse(request.path("query")));
        ArrayNode data = page.putArray("data");
        matches.stream().limit(maxResults).map(VectorStoreSearchResult::payload).forEach(data::add);
        boolean hasMore = matches.size() > maxResults;
        page.put("has_more", hasMore);
        if (hasMore) {
            page.put("next_page", matches.get(maxResults - 1).fileId());
        } else {
            page.putNull("next_page");
        }
        if (request.has("ranking_options")) {
            page.set("ranking_options", copyObject(request.path("ranking_options")));
        }
        if (request.has("rewrite_query")) {
            page.put("rewrite_query", request.path("rewrite_query").asBoolean(false));
        }
        return page;
    }

    public JsonNode deleteVectorStoreFile(String vectorStoreId, String fileId, Long distributedKeyId) {
        GatewayAsyncResourceEntity vectorStore = getRequired(vectorStoreId, GatewayAsyncResourceType.VECTOR_STORE, distributedKeyId);
        GatewayAsyncResourceEntity entity = getRequiredVectorStoreFile(vectorStoreId, fileId, distributedKeyId);
        entity.setDeleted(true);
        entity.setStatus("deleted");
        entity.setMetadataJson(writeJson(appendEvent(readObject(entity.getMetadataJson()), "deleted", "deleted")));
        gatewayAsyncResourceRepository.save(entity);
        adjustVectorStoreFileCount(vectorStore, -1, "file_detached");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", fileId);
        payload.put("object", "vector_store.file.deleted");
        payload.put("deleted", true);
        return payload;
    }

    public JsonNode createVectorStoreFileBatch(String vectorStoreId, Long distributedKeyId, JsonNode requestBody) {
        GatewayAsyncResourceEntity vectorStore = getRequired(vectorStoreId, GatewayAsyncResourceType.VECTOR_STORE, distributedKeyId);
        ObjectNode request = optionalObject(requestBody);
        List<ObjectNode> fileRequests = vectorStoreFileBatchRequests(request);
        List<String> fileIds = fileRequests.stream()
                .map(fileRequest -> fileRequest.path("file_id").asText())
                .toList();
        ensureVectorStoreFilesNotAttached(vectorStoreId, distributedKeyId, fileIds);

        String batchId = "vsfb_" + UUID.randomUUID().toString().replace("-", "");
        long createdAt = now().getEpochSecond();
        ObjectNode payload = vectorStoreFileBatchPayload(batchId, vectorStoreId, fileIds.size(), createdAt);
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("object_mode", "gateway_vector_store_file_batch");
        metadata.put("vector_store_id", vectorStoreId);
        metadata.put("local_lifecycle_only", true);
        ArrayNode metadataFileIds = metadata.putArray("file_ids");
        fileIds.forEach(metadataFileIds::add);
        appendEvent(metadata, "created", "completed");

        GatewayAsyncResourceEntity batch = new GatewayAsyncResourceEntity();
        batch.setResourceKey(batchId);
        batch.setDistributedKeyId(distributedKeyId);
        batch.setResourceType(GatewayAsyncResourceType.VECTOR_STORE_FILE_BATCH);
        batch.setUpstreamObjectId(vectorStoreId);
        batch.setStatus("completed");
        batch.setRequestPayloadJson(writeJson(request));
        batch.setResponsePayloadJson(writeJson(payload));
        batch.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(batch);

        for (ObjectNode fileRequest : fileRequests) {
            saveVectorStoreFileAttachment(
                    distributedKeyId,
                    vectorStoreId,
                    fileRequest.path("file_id").asText(),
                    fileRequest,
                    "created_with_file_batch"
            );
        }
        adjustVectorStoreFileCount(vectorStore, fileIds.size(), "file_batch_completed");
        return payload;
    }

    @Transactional(readOnly = true)
    public JsonNode getVectorStoreFileBatch(String vectorStoreId, String batchId, Long distributedKeyId) {
        getRequired(vectorStoreId, GatewayAsyncResourceType.VECTOR_STORE, distributedKeyId);
        return readJson(getRequiredVectorStoreFileBatch(vectorStoreId, batchId, distributedKeyId).getResponsePayloadJson());
    }

    public JsonNode cancelVectorStoreFileBatch(String vectorStoreId, String batchId, Long distributedKeyId) {
        getRequired(vectorStoreId, GatewayAsyncResourceType.VECTOR_STORE, distributedKeyId);
        GatewayAsyncResourceEntity batch = getRequiredVectorStoreFileBatch(vectorStoreId, batchId, distributedKeyId);
        if ("completed".equals(batch.getStatus())) {
            throw new IllegalArgumentException("已完成的 Vector Store File Batch 不能取消。");
        }
        ObjectNode response = readObject(batch.getResponsePayloadJson());
        if (isTerminalStatus(response.path("status").asText(batch.getStatus()))) {
            throw new IllegalArgumentException("终态 Vector Store File Batch 不能取消。");
        }
        response.put("status", "cancelled");
        response.set("file_counts", vectorStoreCancelledFileCounts(response.path("file_counts")));
        batch.setStatus("cancelled");
        batch.setResponsePayloadJson(writeJson(response));
        batch.setMetadataJson(writeJson(appendEvent(readObject(batch.getMetadataJson()), "cancelled", "cancelled")));
        gatewayAsyncResourceRepository.save(batch);
        return response;
    }

    @Transactional(readOnly = true)
    public JsonNode listVectorStoreFileBatchFiles(
            String vectorStoreId,
            String batchId,
            Long distributedKeyId,
            String after,
            Integer limit,
            String order,
            String filter) {
        getRequired(vectorStoreId, GatewayAsyncResourceType.VECTOR_STORE, distributedKeyId);
        GatewayAsyncResourceEntity batch = getRequiredVectorStoreFileBatch(vectorStoreId, batchId, distributedKeyId);
        List<String> fileIds = vectorStoreFileBatchMetadataFileIds(readObject(batch.getMetadataJson()));
        String normalizedOrder = normalizeResponseInputItemsOrder(order);
        List<JsonNode> ordered = new ArrayList<>();
        if ("desc".equals(normalizedOrder)) {
            for (int index = fileIds.size() - 1; index >= 0; index--) {
                findActiveVectorStoreFileEntity(vectorStoreId, fileIds.get(index), distributedKeyId)
                        .map(entity -> readJson(entity.getResponsePayloadJson()))
                        .ifPresent(ordered::add);
            }
        } else {
            for (String fileId : fileIds) {
                findActiveVectorStoreFileEntity(vectorStoreId, fileId, distributedKeyId)
                        .map(entity -> readJson(entity.getResponsePayloadJson()))
                        .ifPresent(ordered::add);
            }
        }

        int startIndex = vectorStoreBatchFileStartIndex(ordered, after);
        if (startIndex < 0) {
            return listEnvelope(List.of(), false);
        }
        String normalizedFilter = normalizeNullable(filter);
        int pageSize = normalizeListLimit(limit);
        List<JsonNode> collected = new ArrayList<>();
        for (int index = startIndex; index < ordered.size(); index++) {
            JsonNode payload = ordered.get(index);
            if (normalizedFilter != null && !normalizedFilter.equals(payload.path("status").asText())) {
                continue;
            }
            collected.add(payload);
            if (collected.size() > pageSize) {
                break;
            }
        }
        boolean hasMore = collected.size() > pageSize;
        return listEnvelope(collected.stream().limit(pageSize).toList(), hasMore);
    }

    private JsonNode syncRemoteResponse(GatewayAsyncResourceEntity entity, ObjectNode metadata, List<String> include) {
        UpstreamTarget target = resolveUpstreamTargetForEntity(entity, metadata);
        String upstreamId = responseUpstreamObjectId(entity, metadata);
        String path = appendRepeatedQueryParams(
                target.path() + "/" + encodePathSegment(upstreamId),
                "include",
                include
        );
        JsonNode upstreamResponse = target.client()
                .get()
                .uri(path)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        return syncPersistedResource(entity, upstreamResponse, "response");
    }

    private JsonNode deleteRemoteResponse(GatewayAsyncResourceEntity entity, ObjectNode metadata) {
        UpstreamTarget target = resolveUpstreamTargetForEntity(entity, metadata);
        String upstreamId = responseUpstreamObjectId(entity, metadata);
        JsonNode upstreamResponse = target.client()
                .delete()
                .uri(target.path() + "/" + encodePathSegment(upstreamId))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        ObjectNode response = copyObject(upstreamResponse);
        response.put("id", entity.getResourceKey());
        if (!response.has("object")) {
            response.put("object", "response");
        }
        response.put("deleted", true);
        entity.setDeleted(true);
        entity.setStatus("deleted");
        entity.setResponsePayloadJson(writeJson(response));
        metadata.put("upstream_status", "deleted");
        metadata.put("upstream_synced_at", now().getEpochSecond());
        entity.setMetadataJson(writeJson(appendEvent(metadata, "deleted", "deleted")));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    private JsonNode cancelRemoteResponse(GatewayAsyncResourceEntity entity, ObjectNode metadata) {
        UpstreamTarget target = resolveUpstreamTargetForEntity(entity, metadata);
        String upstreamId = responseUpstreamObjectId(entity, metadata);
        JsonNode upstreamResponse = invokeUpstreamJson(
                target,
                target.path() + "/" + encodePathSegment(upstreamId) + "/cancel",
                objectMapper.createObjectNode()
        );
        return syncPersistedResource(entity, upstreamResponse, "response");
    }

    private JsonNode listRemoteResponseInputItems(
            GatewayAsyncResourceEntity entity,
            ObjectNode metadata,
            String after,
            List<String> include,
            Integer limit,
            String order) {
        UpstreamTarget target = resolveUpstreamTargetForEntity(entity, metadata);
        String upstreamId = responseUpstreamObjectId(entity, metadata);
        String path = target.path() + "/" + encodePathSegment(upstreamId) + "/input_items";
        path = appendQueryParam(path, "after", after);
        path = appendRepeatedQueryParams(path, "include", include);
        if (limit != null) {
            path = appendQueryParam(path, "limit", String.valueOf(limit));
        }
        path = appendQueryParam(path, "order", order);
        JsonNode upstreamResponse = target.client()
                .get()
                .uri(path)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        return upstreamResponse == null ? objectMapper.createObjectNode() : upstreamResponse;
    }

    private boolean shouldPersistOpenAiResponseLineage(RouteSelectionResult routeSelection, String upstreamObjectId) {
        if (routeSelection == null
                || routeSelection.selectedCandidate() == null
                || routeSelection.selectedCandidate().candidate() == null
                || upstreamObjectId == null
                || upstreamObjectId.isBlank()) {
            return false;
        }
        var candidate = routeSelection.selectedCandidate().candidate();
        return candidate.providerType() == ProviderType.OPENAI_DIRECT
                && candidate.credentialId() != null;
    }

    private boolean hasResponseUpstreamLineage(GatewayAsyncResourceEntity entity, ObjectNode metadata) {
        if (entity == null || entity.getResourceType() != GatewayAsyncResourceType.RESPONSE) {
            return false;
        }
        String upstreamObjectId = responseUpstreamObjectIdOrNull(entity, metadata);
        return upstreamObjectId != null
                && !upstreamObjectId.isBlank()
                && metadata != null
                && metadata.path("credential_id").isNumber();
    }

    private String responseUpstreamObjectId(GatewayAsyncResourceEntity entity, ObjectNode metadata) {
        String upstreamObjectId = responseUpstreamObjectIdOrNull(entity, metadata);
        if (upstreamObjectId == null || upstreamObjectId.isBlank()) {
            throw new IllegalArgumentException("Response 对象缺少 upstream_object_id。");
        }
        return upstreamObjectId;
    }

    private String responseUpstreamObjectIdOrNull(GatewayAsyncResourceEntity entity, ObjectNode metadata) {
        if (metadata != null) {
            String value = metadata.path("upstream_object_id").asText(null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return entity == null ? null : entity.getUpstreamObjectId();
    }

    public JsonNode storeChatCompletion(Long distributedKeyId, String requestModel, JsonNode requestPayload, JsonNode responsePayload) {
        String resourceKey = "chatcmpl_" + UUID.randomUUID().toString().replace("-", "");
        ObjectNode storedResponse = copyObject(responsePayload);
        storedResponse.put("id", resourceKey);
        storedResponse.put("object", "chat.completion");
        if (!storedResponse.has("status")) {
            storedResponse.put("status", "completed");
        }

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("object_mode", "gateway_stored_chat_completion");
        appendEvent(metadata, "stored", storedResponse.path("status").asText("completed"));

        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey(resourceKey);
        entity.setDistributedKeyId(distributedKeyId);
        entity.setResourceType(GatewayAsyncResourceType.RESPONSE);
        entity.setRequestModel(requestModel);
        entity.setStatus(storedResponse.path("status").asText("completed"));
        entity.setRequestPayloadJson(writeJson(requestPayload));
        entity.setResponsePayloadJson(writeJson(storedResponse));
        entity.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(entity);
        return storedResponse;
    }

    @Transactional(readOnly = true)
    public JsonNode listChatCompletions(
            Long distributedKeyId,
            String after,
            Integer limit,
            String model,
            String order,
            Map<String, String> metadataFilter) {
        int pageSize = normalizeListLimit(limit);
        String normalizedOrder = normalizeListOrder(order);
        String modelFilter = normalizeNullable(model);
        Map<String, String> effectiveMetadataFilter = metadataFilter == null ? Map.of() : metadataFilter;
        StoredChatCursor cursor = null;
        if (after != null && !after.isBlank()) {
            Optional<GatewayAsyncResourceEntity> cursorEntity = gatewayAsyncResourceRepository
                    .findByResourceKeyAndResourceTypeAndDistributedKeyIdAndDeletedFalse(
                            after,
                            GatewayAsyncResourceType.RESPONSE,
                            distributedKeyId);
            if (cursorEntity.isEmpty() || !matchesStoredChatListFilter(cursorEntity.get(), modelFilter, effectiveMetadataFilter)) {
                return listEnvelope(List.of(), false);
            }
            cursor = StoredChatCursor.from(cursorEntity.get());
        }

        int batchSize = pageSize + 1;
        List<JsonNode> collected = new ArrayList<>();
        StoredChatCursor currentCursor = cursor;
        boolean exhausted = false;
        while (collected.size() <= pageSize && !exhausted) {
            List<GatewayAsyncResourceEntity> candidates = fetchStoredChatCompletionCandidates(
                    distributedKeyId,
                    modelFilter,
                    normalizedOrder,
                    currentCursor,
                    batchSize);
            if (candidates.isEmpty()) {
                break;
            }
            for (GatewayAsyncResourceEntity entity : candidates) {
                currentCursor = StoredChatCursor.from(entity);
                ObjectNode response = readObject(entity.getResponsePayloadJson());
                if (!isStoredChatCompletionResponse(response)) {
                    continue;
                }
                if (modelFilter != null && !modelFilter.equals(response.path("model").asText())) {
                    continue;
                }
                if (!metadataMatches(response.path("metadata"), effectiveMetadataFilter)) {
                    continue;
                }
                collected.add(response);
                if (collected.size() > pageSize) {
                    break;
                }
            }
            exhausted = candidates.size() < batchSize;
        }

        boolean hasMore = collected.size() > pageSize;
        return listEnvelope(collected.stream().limit(pageSize).toList(), hasMore);
    }

    @Transactional(readOnly = true)
    public JsonNode getChatCompletion(String completionId, Long distributedKeyId) {
        GatewayAsyncResourceEntity entity = getRequired(completionId, GatewayAsyncResourceType.RESPONSE, distributedKeyId);
        ObjectNode response = readObject(entity.getResponsePayloadJson());
        if (!"chat.completion".equals(response.path("object").asText())) {
            throw new IllegalArgumentException("未找到 stored Chat Completion。");
        }
        return response;
    }

    public JsonNode updateChatCompletionMetadata(String completionId, Long distributedKeyId, JsonNode metadataPatch) {
        GatewayAsyncResourceEntity entity = getRequired(completionId, GatewayAsyncResourceType.RESPONSE, distributedKeyId);
        ObjectNode response = readObject(entity.getResponsePayloadJson());
        if (!"chat.completion".equals(response.path("object").asText())) {
            throw new IllegalArgumentException("未找到 stored Chat Completion。");
        }
        if (metadataPatch == null || metadataPatch.isMissingNode() || metadataPatch.isNull()) {
            response.set("metadata", objectMapper.createObjectNode());
        } else if (metadataPatch.isObject()) {
            response.set("metadata", metadataPatch.deepCopy());
        } else {
            throw new IllegalArgumentException("metadata 必须是 JSON object 或 null。");
        }
        entity.setResponsePayloadJson(writeJson(response));
        entity.setMetadataJson(writeJson(appendEvent(readObject(entity.getMetadataJson()), "metadata_updated", entity.getStatus())));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    public JsonNode deleteChatCompletion(String completionId, Long distributedKeyId) {
        GatewayAsyncResourceEntity entity = getRequired(completionId, GatewayAsyncResourceType.RESPONSE, distributedKeyId);
        ObjectNode response = readObject(entity.getResponsePayloadJson());
        if (!"chat.completion".equals(response.path("object").asText())) {
            throw new IllegalArgumentException("未找到 stored Chat Completion。");
        }
        entity.setDeleted(true);
        entity.setStatus("deleted");
        entity.setMetadataJson(writeJson(appendEvent(readObject(entity.getMetadataJson()), "deleted", "deleted")));
        gatewayAsyncResourceRepository.save(entity);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", completionId);
        payload.put("object", "chat.completion.deleted");
        payload.put("deleted", true);
        return payload;
    }

    @Transactional(readOnly = true)
    public JsonNode listChatCompletionMessages(String completionId, Long distributedKeyId, String after, Integer limit, String order) {
        GatewayAsyncResourceEntity entity = getRequired(completionId, GatewayAsyncResourceType.RESPONSE, distributedKeyId);
        ObjectNode responsePayload = readObject(entity.getResponsePayloadJson());
        if (!"chat.completion".equals(responsePayload.path("object").asText())) {
            throw new IllegalArgumentException("未找到 stored Chat Completion。");
        }
        JsonNode messages = readObject(entity.getRequestPayloadJson()).path("messages");
        List<JsonNode> ordered = new ArrayList<>();
        if (messages.isArray()) {
            for (JsonNode message : messages) {
                ordered.add(message.deepCopy());
            }
        }
        String normalizedOrder = normalizeListOrder(order);
        if ("desc".equals(normalizedOrder)) {
            java.util.Collections.reverse(ordered);
        }
        if (after != null && !after.isBlank()) {
            int cursor = -1;
            for (int index = 0; index < ordered.size(); index++) {
                if (after.equals(ordered.get(index).path("id").asText())) {
                    cursor = index;
                    break;
                }
            }
            ordered = cursor < 0 ? List.of() : ordered.subList(cursor + 1, ordered.size());
        }

        int pageSize = normalizeListLimit(limit);
        boolean hasMore = ordered.size() > pageSize;
        List<JsonNode> page = ordered.stream().limit(pageSize).toList();
        var data = objectMapper.createArrayNode();
        page.forEach(data::add);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "list");
        response.set("data", data);
        response.put("has_more", hasMore);
        if (!page.isEmpty()) {
            response.put("first_id", page.getFirst().path("id").asText());
            response.put("last_id", page.getLast().path("id").asText());
        }
        return response;
    }

    private boolean metadataMatches(JsonNode metadata, Map<String, String> metadataFilter) {
        if (metadataFilter == null || metadataFilter.isEmpty()) {
            return true;
        }
        if (metadata == null || !metadata.isObject()) {
            return false;
        }
        return metadataFilter.entrySet().stream()
                .allMatch(entry -> entry.getValue().equals(metadata.path(entry.getKey()).asText(null)));
    }

    private List<GatewayAsyncResourceEntity> fetchStoredChatCompletionCandidates(
            Long distributedKeyId,
            String model,
            String order,
            StoredChatCursor cursor,
            int batchSize) {
        PageRequest pageable = PageRequest.of(0, batchSize);
        Instant cursorCreatedAt = cursor == null ? null : cursor.createdAt();
        Long cursorId = cursor == null ? null : cursor.id();
        if ("desc".equals(order)) {
            return gatewayAsyncResourceRepository.findStoredResourcesAfterCursorDesc(
                    distributedKeyId,
                    GatewayAsyncResourceType.RESPONSE,
                    STORED_CHAT_RESOURCE_PREFIX,
                    model,
                    cursorCreatedAt,
                    cursorId,
                    pageable);
        }
        return gatewayAsyncResourceRepository.findStoredResourcesAfterCursorAsc(
                distributedKeyId,
                GatewayAsyncResourceType.RESPONSE,
                STORED_CHAT_RESOURCE_PREFIX,
                model,
                cursorCreatedAt,
                cursorId,
                pageable);
    }

    private boolean matchesStoredChatListFilter(
            GatewayAsyncResourceEntity entity,
            String modelFilter,
            Map<String, String> metadataFilter) {
        if (entity == null
                || entity.isDeleted()
                || entity.getResourceType() != GatewayAsyncResourceType.RESPONSE
                || entity.getResourceKey() == null
                || !entity.getResourceKey().startsWith(STORED_CHAT_RESOURCE_PREFIX)) {
            return false;
        }
        if (modelFilter != null && !modelFilter.equals(entity.getRequestModel())) {
            return false;
        }
        ObjectNode response = readObject(entity.getResponsePayloadJson());
        return isStoredChatCompletionResponse(response)
                && (modelFilter == null || modelFilter.equals(response.path("model").asText()))
                && metadataMatches(response.path("metadata"), metadataFilter);
    }

    private boolean isStoredChatCompletionResponse(JsonNode response) {
        return response != null && "chat.completion".equals(response.path("object").asText());
    }

    private ObjectNode listEnvelope(List<JsonNode> page, boolean hasMore) {
        return listEnvelope(page, hasMore, true);
    }

    private ObjectNode listEnvelope(List<JsonNode> page, boolean hasMore, boolean includeBoundaryIds) {
        var data = objectMapper.createArrayNode();
        page.forEach(data::add);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "list");
        response.set("data", data);
        response.put("has_more", hasMore);
        if (includeBoundaryIds && !page.isEmpty()) {
            response.put("first_id", page.getFirst().path("id").asText());
            response.put("last_id", page.getLast().path("id").asText());
        }
        return response;
    }

    private ObjectNode listInMemoryItems(
            List<JsonNode> items,
            String after,
            int pageSize,
            boolean includeBoundaryIds) {
        List<JsonNode> normalizedItems = items == null ? List.of() : items;
        int start = 0;
        if (after != null && !after.isBlank()) {
            start = -1;
            for (int index = 0; index < normalizedItems.size(); index++) {
                if (after.equals(normalizedItems.get(index).path("id").asText())) {
                    start = index + 1;
                    break;
                }
            }
            if (start < 0) {
                return listEnvelope(List.of(), false, includeBoundaryIds);
            }
        }
        int end = Math.min(start + pageSize, normalizedItems.size());
        boolean hasMore = end < normalizedItems.size();
        return listEnvelope(normalizedItems.subList(start, end), hasMore, includeBoundaryIds);
    }

    private ObjectNode optionalObject(JsonNode payload) {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!payload.isObject()) {
            throw new IllegalArgumentException("请求体必须是 JSON object。");
        }
        return copyObject(payload);
    }

    private ObjectNode metadataObject(JsonNode metadata) {
        if (metadata == null || metadata.isMissingNode() || metadata.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!metadata.isObject()) {
            throw new IllegalArgumentException("metadata 必须是 JSON object。");
        }
        ObjectNode normalized = copyObject(metadata);
        if (normalized.size() > 16) {
            throw new IllegalArgumentException("metadata 最多支持 16 个键值对。");
        }
        normalized.properties().forEach(entry -> {
            if (entry.getKey().length() > 64) {
                throw new IllegalArgumentException("metadata key 长度不能超过 64。");
            }
            JsonNode value = entry.getValue();
            if (value != null && !value.isNull() && !value.isTextual()) {
                throw new IllegalArgumentException("metadata value 必须是字符串。");
            }
            if (value != null && value.isTextual() && value.asText().length() > 512) {
                throw new IllegalArgumentException("metadata value 长度不能超过 512。");
            }
        });
        return normalized;
    }

    private ObjectNode conversationPayload(String conversationId, ObjectNode metadata, long createdAt) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", conversationId);
        payload.put("object", "conversation");
        payload.put("created_at", createdAt);
        payload.set("metadata", metadata == null ? objectMapper.createObjectNode() : metadata);
        return payload;
    }

    private List<JsonNode> conversationItemBatch(JsonNode itemsNode, boolean required) {
        if (itemsNode == null || itemsNode.isMissingNode() || itemsNode.isNull()) {
            if (required) {
                throw new IllegalArgumentException("items 必须是 JSON array。");
            }
            return List.of();
        }
        if (!itemsNode.isArray()) {
            throw new IllegalArgumentException("items 必须是 JSON array。");
        }
        if (itemsNode.size() > CONVERSATION_ITEM_BATCH_LIMIT) {
            throw new IllegalArgumentException("每次最多添加 20 个 Conversation Item。");
        }
        if (required && itemsNode.isEmpty()) {
            throw new IllegalArgumentException("items 至少需要 1 个元素。");
        }
        List<JsonNode> items = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (JsonNode item : itemsNode) {
            ObjectNode normalized = normalizedConversationItem(item);
            String itemId = text(normalized, "id");
            if (itemId != null && !seenIds.add(itemId)) {
                throw new IllegalArgumentException("Conversation Item id 重复。");
            }
            items.add(normalized);
        }
        return items;
    }

    private JsonNode saveConversationItem(
            Long distributedKeyId,
            String conversationId,
            JsonNode item,
            String eventType) {
        ObjectNode normalized = normalizedConversationItem(item);
        String itemId = ensureConversationItemId(normalized);
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("object_mode", "gateway_conversation_item");
        metadata.put("conversation_id", conversationId);
        appendEvent(metadata, eventType, normalized.path("status").asText("completed"));

        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey(itemId);
        entity.setDistributedKeyId(distributedKeyId);
        entity.setResourceType(GatewayAsyncResourceType.CONVERSATION_ITEM);
        entity.setUpstreamObjectId(conversationId);
        entity.setStatus(normalized.path("status").asText("completed"));
        entity.setRequestPayloadJson(writeJson(item));
        entity.setResponsePayloadJson(writeJson(normalized));
        entity.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(entity);
        return normalized;
    }

    private ObjectNode normalizedConversationItem(JsonNode item) {
        ObjectNode normalized;
        if (item != null && item.isObject()) {
            normalized = copyObject(item);
        } else {
            normalized = objectMapper.createObjectNode();
            normalized.put("type", "message");
            normalized.put("role", "user");
            normalized.put("content", item == null || item.isNull() ? "" : item.asText());
        }
        if (!normalized.has("type") || normalized.path("type").asText().isBlank()) {
            if (normalized.has("role") || normalized.has("content")) {
                normalized.put("type", "message");
            } else {
                normalized.put("type", "item");
            }
        }
        if (!normalized.has("status") || normalized.path("status").asText().isBlank()) {
            normalized.put("status", "completed");
        }
        return normalized;
    }

    private String ensureConversationItemId(ObjectNode item) {
        String supplied = text(item, "id");
        if (supplied != null) {
            if (gatewayAsyncResourceRepository.existsByResourceKey(supplied)) {
                throw new IllegalArgumentException("Conversation Item id 已存在。");
            }
            return supplied;
        }
        String prefix = "message".equals(item.path("type").asText()) ? "msg_" : "item_";
        for (int attempt = 0; attempt < 8; attempt++) {
            String generated = prefix + UUID.randomUUID().toString().replace("-", "");
            if (!gatewayAsyncResourceRepository.existsByResourceKey(generated)) {
                item.put("id", generated);
                return generated;
            }
        }
        throw new IllegalStateException("无法生成 Conversation Item id。");
    }

    private void ensureConversationExists(String conversationId, Long distributedKeyId) {
        getRequired(conversationId, GatewayAsyncResourceType.CONVERSATION, distributedKeyId);
    }

    private ObjectNode vectorStorePayload(String vectorStoreId, ObjectNode request, long createdAt, int fileCount) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", vectorStoreId);
        payload.put("object", "vector_store");
        payload.put("created_at", createdAt);
        payload.put("last_active_at", createdAt);
        payload.put("status", "completed");
        payload.put("usage_bytes", 0L);
        if (request.has("name") && !request.path("name").isNull()) {
            payload.put("name", request.path("name").asText(""));
        } else {
            payload.putNull("name");
        }
        payload.set("metadata", metadataObject(request.path("metadata")));
        if (request.has("expires_after")) {
            payload.set("expires_after", vectorStoreExpiresAfter(request.path("expires_after")));
        } else {
            payload.putNull("expires_after");
        }
        if (request.has("expires_at")) {
            setNullableLong(payload, "expires_at", request.path("expires_at"));
        } else {
            payload.putNull("expires_at");
        }
        payload.set("file_counts", vectorStoreFileCounts(fileCount));
        return payload;
    }

    private List<String> vectorStoreFileIds(JsonNode fileIds) {
        if (fileIds == null || fileIds.isMissingNode() || fileIds.isNull()) {
            return List.of();
        }
        if (!fileIds.isArray()) {
            throw new IllegalArgumentException("file_ids 必须是 JSON array。");
        }
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode fileId : fileIds) {
            if (fileId == null || !fileId.isTextual() || fileId.asText().isBlank()) {
                throw new IllegalArgumentException("file_ids 只能包含非空字符串。");
            }
            String value = fileId.asText().trim();
            if (!seen.add(value)) {
                throw new IllegalArgumentException("file_ids 不能包含重复值。");
            }
            normalized.add(value);
        }
        return normalized;
    }

    private ObjectNode vectorStoreFileCounts(int fileCount) {
        ObjectNode counts = objectMapper.createObjectNode();
        counts.put("in_progress", 0);
        counts.put("completed", fileCount);
        counts.put("failed", 0);
        counts.put("cancelled", 0);
        counts.put("total", fileCount);
        return counts;
    }

    private JsonNode vectorStoreExpiresAfter(JsonNode expiresAfter) {
        if (expiresAfter == null || expiresAfter.isMissingNode() || expiresAfter.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (!expiresAfter.isObject()) {
            throw new IllegalArgumentException("expires_after 必须是 JSON object 或 null。");
        }
        return copyObject(expiresAfter);
    }

    private void setNullableLong(ObjectNode target, String fieldName, JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            target.putNull(fieldName);
            return;
        }
        if (!value.isNumber()) {
            throw new IllegalArgumentException(fieldName + " 必须是 Unix seconds 数字或 null。");
        }
        target.put(fieldName, value.asLong());
    }

    private JsonNode saveVectorStoreFileAttachment(
            Long distributedKeyId,
            String vectorStoreId,
            String fileId,
            ObjectNode request,
            String eventType) {
        ObjectNode payload = vectorStoreFilePayload(vectorStoreId, fileId, request, now().getEpochSecond());
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("object_mode", "gateway_vector_store_file");
        metadata.put("vector_store_id", vectorStoreId);
        appendEvent(metadata, eventType, payload.path("status").asText("completed"));

        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey("vsf_" + UUID.randomUUID().toString().replace("-", ""));
        entity.setDistributedKeyId(distributedKeyId);
        entity.setResourceType(GatewayAsyncResourceType.VECTOR_STORE_FILE);
        entity.setUpstreamObjectId(vectorStoreId);
        entity.setStatus(payload.path("status").asText("completed"));
        entity.setRequestPayloadJson(writeJson(request));
        entity.setResponsePayloadJson(writeJson(payload));
        entity.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(entity);
        return payload;
    }

    private ObjectNode vectorStoreFilePayload(String vectorStoreId, String fileId, ObjectNode request, long createdAt) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", fileId);
        payload.put("object", "vector_store.file");
        payload.put("created_at", createdAt);
        payload.put("vector_store_id", vectorStoreId);
        payload.put("status", "completed");
        payload.put("usage_bytes", 0L);
        payload.putNull("last_error");
        payload.set("attributes", vectorStoreAttributesObject(request.path("attributes")));
        payload.set("chunking_strategy", vectorStoreChunkingStrategy(request.path("chunking_strategy")));
        return payload;
    }

    private ObjectNode vectorStoreAttributesObject(JsonNode attributes) {
        if (attributes == null || attributes.isMissingNode() || attributes.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!attributes.isObject()) {
            throw new IllegalArgumentException("attributes 必须是 JSON object。");
        }
        ObjectNode normalized = copyObject(attributes);
        if (normalized.size() > 16) {
            throw new IllegalArgumentException("attributes 最多支持 16 个键值对。");
        }
        normalized.properties().forEach(entry -> {
            if (entry.getKey().length() > 64) {
                throw new IllegalArgumentException("attributes key 长度不能超过 64。");
            }
            JsonNode value = entry.getValue();
            if (value != null
                    && !value.isNull()
                    && !value.isTextual()
                    && !value.isNumber()
                    && !value.isBoolean()) {
                throw new IllegalArgumentException("attributes value 必须是字符串、数字、布尔值或 null。");
            }
            if (value != null && value.isTextual() && value.asText().length() > 512) {
                throw new IllegalArgumentException("attributes value 长度不能超过 512。");
            }
        });
        return normalized;
    }

    private JsonNode vectorStoreChunkingStrategy(JsonNode chunkingStrategy) {
        if (chunkingStrategy == null || chunkingStrategy.isMissingNode() || chunkingStrategy.isNull()) {
            return objectMapper.createObjectNode().put("type", "auto");
        }
        if (!chunkingStrategy.isObject()) {
            throw new IllegalArgumentException("chunking_strategy 必须是 JSON object。");
        }
        return copyObject(chunkingStrategy);
    }

    private List<ObjectNode> vectorStoreFileBatchRequests(ObjectNode request) {
        boolean hasFileIds = request.has("file_ids") && !request.path("file_ids").isNull();
        boolean hasFiles = request.has("files") && !request.path("files").isNull();
        if (hasFileIds && hasFiles) {
            throw new IllegalArgumentException("file_ids 与 files 不能同时提供。");
        }
        if (hasFiles) {
            return vectorStoreFileBatchFileObjects(request.path("files"));
        }
        if (!hasFileIds) {
            throw new IllegalArgumentException("file_ids 或 files 为必填字段。");
        }
        List<String> fileIds = vectorStoreFileIds(request.path("file_ids"));
        if (fileIds.isEmpty()) {
            throw new IllegalArgumentException("file_ids 至少包含 1 个文件。");
        }
        ensureVectorStoreFileBatchLimit(fileIds.size());
        return fileIds.stream()
                .map(fileId -> {
                    ObjectNode fileRequest = objectMapper.createObjectNode();
                    fileRequest.put("file_id", fileId);
                    if (request.has("attributes")) {
                        fileRequest.set("attributes", vectorStoreAttributesObject(request.path("attributes")));
                    }
                    if (request.has("chunking_strategy")) {
                        fileRequest.set("chunking_strategy", vectorStoreChunkingStrategy(request.path("chunking_strategy")));
                    }
                    return fileRequest;
                })
                .toList();
    }

    private List<ObjectNode> vectorStoreFileBatchFileObjects(JsonNode files) {
        if (!files.isArray()) {
            throw new IllegalArgumentException("files 必须是 JSON array。");
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("files 至少包含 1 个文件。");
        }
        ensureVectorStoreFileBatchLimit(files.size());
        Set<String> seen = new HashSet<>();
        List<ObjectNode> fileRequests = new ArrayList<>();
        for (JsonNode file : files) {
            if (file == null || !file.isObject()) {
                throw new IllegalArgumentException("files 只能包含 JSON object。");
            }
            ObjectNode fileObject = copyObject(file);
            String fileId = text(fileObject, "file_id");
            if (fileId == null) {
                throw new IllegalArgumentException("files[].file_id 为必填字段。");
            }
            fileId = fileId.trim();
            if (!seen.add(fileId)) {
                throw new IllegalArgumentException("files 不能包含重复 file_id。");
            }
            ObjectNode normalized = objectMapper.createObjectNode();
            normalized.put("file_id", fileId);
            if (fileObject.has("attributes")) {
                normalized.set("attributes", vectorStoreAttributesObject(fileObject.path("attributes")));
            }
            if (fileObject.has("chunking_strategy")) {
                normalized.set("chunking_strategy", vectorStoreChunkingStrategy(fileObject.path("chunking_strategy")));
            }
            fileRequests.add(normalized);
        }
        return fileRequests;
    }

    private void ensureVectorStoreFileBatchLimit(int size) {
        if (size > 2_000) {
            throw new IllegalArgumentException("Vector Store File Batch 最多支持 2000 个文件。");
        }
    }

    private void ensureVectorStoreFilesNotAttached(String vectorStoreId, Long distributedKeyId, List<String> fileIds) {
        for (String fileId : fileIds) {
            if (findActiveVectorStoreFileEntity(vectorStoreId, fileId, distributedKeyId).isPresent()) {
                throw new IllegalArgumentException("Vector Store 已关联该 file_id：" + fileId);
            }
        }
    }

    private ObjectNode vectorStoreFileBatchPayload(String batchId, String vectorStoreId, int fileCount, long createdAt) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", batchId);
        payload.put("object", "vector_store.file_batch");
        payload.put("created_at", createdAt);
        payload.put("vector_store_id", vectorStoreId);
        payload.put("status", "completed");
        payload.set("file_counts", vectorStoreFileCounts(fileCount));
        return payload;
    }

    private ObjectNode vectorStoreCancelledFileCounts(JsonNode currentCounts) {
        int completed = currentCounts.path("completed").asInt(0);
        int failed = currentCounts.path("failed").asInt(0);
        int total = currentCounts.path("total").asInt(completed + failed);
        int cancelled = Math.max(0, total - completed - failed);
        ObjectNode counts = objectMapper.createObjectNode();
        counts.put("in_progress", 0);
        counts.put("completed", completed);
        counts.put("failed", failed);
        counts.put("cancelled", cancelled);
        counts.put("total", total);
        return counts;
    }

    private List<String> vectorStoreFileBatchMetadataFileIds(ObjectNode metadata) {
        JsonNode fileIds = metadata.path("file_ids");
        if (!fileIds.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode fileId : fileIds) {
            if (fileId != null && fileId.isTextual() && !fileId.asText().isBlank()) {
                values.add(fileId.asText());
            }
        }
        return values;
    }

    private int vectorStoreBatchFileStartIndex(List<JsonNode> ordered, String after) {
        if (after == null || after.isBlank()) {
            return 0;
        }
        for (int index = 0; index < ordered.size(); index++) {
            if (after.equals(ordered.get(index).path("id").asText())) {
                return index + 1;
            }
        }
        return -1;
    }

    private List<String> vectorStoreSearchQueries(JsonNode query) {
        if (query == null || query.isMissingNode() || query.isNull()) {
            throw new IllegalArgumentException("query 为必填字段。");
        }
        if (query.isTextual()) {
            String value = query.asText("").trim();
            if (value.isBlank()) {
                throw new IllegalArgumentException("query 不能为空。");
            }
            return List.of(value);
        }
        if (!query.isArray()) {
            throw new IllegalArgumentException("query 必须是字符串或字符串数组。");
        }
        if (query.isEmpty()) {
            throw new IllegalArgumentException("query 至少包含 1 个查询。");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : query) {
            if (item == null || !item.isTextual() || item.asText("").isBlank()) {
                throw new IllegalArgumentException("query 数组只能包含非空字符串。");
            }
            values.add(item.asText().trim());
        }
        return List.copyOf(values);
    }

    private JsonNode vectorStoreSearchQueryResponse(JsonNode query) {
        if (query != null && query.isArray()) {
            return query.deepCopy();
        }
        return query == null ? JsonNodeFactory.instance.nullNode() : query.deepCopy();
    }

    private int vectorStoreSearchMaxResults(JsonNode maxNumResults) {
        if (maxNumResults == null || maxNumResults.isMissingNode() || maxNumResults.isNull()) {
            return 10;
        }
        if (!maxNumResults.isInt() && !maxNumResults.isLong()) {
            throw new IllegalArgumentException("max_num_results 必须是整数。");
        }
        int value = maxNumResults.asInt();
        if (value < 1 || value > 50) {
            throw new IllegalArgumentException("max_num_results 必须在 1 到 50 之间。");
        }
        return value;
    }

    private double vectorStoreSearchScoreThreshold(JsonNode rankingOptions) {
        if (rankingOptions == null || rankingOptions.isMissingNode() || rankingOptions.isNull()) {
            return 0.0d;
        }
        if (!rankingOptions.isObject()) {
            throw new IllegalArgumentException("ranking_options 必须是 JSON object。");
        }
        JsonNode scoreThreshold = rankingOptions.path("score_threshold");
        if (scoreThreshold.isMissingNode() || scoreThreshold.isNull()) {
            return 0.0d;
        }
        if (!scoreThreshold.isNumber()) {
            throw new IllegalArgumentException("ranking_options.score_threshold 必须是数字。");
        }
        double value = scoreThreshold.asDouble();
        if (value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException("ranking_options.score_threshold 必须在 0 到 1 之间。");
        }
        return value;
    }

    private boolean vectorStoreSearchFiltersMatch(JsonNode attributes, JsonNode filters) {
        if (filters == null || filters.isMissingNode() || filters.isNull()) {
            return true;
        }
        if (!filters.isObject()) {
            throw new IllegalArgumentException("filters 必须是 JSON object。");
        }
        return vectorStoreSearchFilterMatch(attributes, filters);
    }

    private void validateVectorStoreSearchFilters(JsonNode filters) {
        if (filters == null || filters.isMissingNode() || filters.isNull()) {
            return;
        }
        if (!filters.isObject()) {
            throw new IllegalArgumentException("filters 必须是 JSON object。");
        }
        validateVectorStoreSearchFilter(filters);
    }

    private void validateVectorStoreSearchFilter(JsonNode filter) {
        if (filter == null || !filter.isObject()) {
            throw new IllegalArgumentException("filters 只能包含 JSON object。");
        }
        String type = text(filter, "type");
        if (type == null) {
            throw new IllegalArgumentException("filters.type 为必填字段。");
        }
        if ("and".equals(type) || "or".equals(type)) {
            JsonNode children = filter.path("filters");
            if (!children.isArray() || children.isEmpty()) {
                throw new IllegalArgumentException("compound filters 至少包含 1 个子 filter。");
            }
            if (children.size() > 16) {
                throw new IllegalArgumentException("compound filters 最多包含 16 个子 filter。");
            }
            children.forEach(this::validateVectorStoreSearchFilter);
            return;
        }
        if (!Set.of("eq", "ne", "gt", "gte", "lt", "lte", "in", "nin").contains(type)) {
            throw new IllegalArgumentException("不支持的 filters.type：" + type);
        }
        if (text(filter, "key") == null) {
            throw new IllegalArgumentException("comparison filters.key 为必填字段。");
        }
        JsonNode value = filter.path("value");
        if (value.isMissingNode()) {
            throw new IllegalArgumentException("comparison filters.value 为必填字段。");
        }
        if (Set.of("gt", "gte", "lt", "lte").contains(type) && !value.isNumber()) {
            throw new IllegalArgumentException("gt/gte/lt/lte filters.value 必须是数字。");
        }
        if (Set.of("in", "nin").contains(type) && !value.isArray()) {
            throw new IllegalArgumentException("in/nin filters.value 必须是 JSON array。");
        }
    }

    private boolean vectorStoreSearchFilterMatch(JsonNode attributes, JsonNode filter) {
        if (filter == null || !filter.isObject()) {
            throw new IllegalArgumentException("filters 只能包含 JSON object。");
        }
        String type = text(filter, "type");
        if (type == null) {
            throw new IllegalArgumentException("filters.type 为必填字段。");
        }
        return switch (type) {
            case "and" -> vectorStoreSearchCompoundFilterMatch(attributes, filter, true);
            case "or" -> vectorStoreSearchCompoundFilterMatch(attributes, filter, false);
            case "eq", "ne", "gt", "gte", "lt", "lte", "in", "nin" ->
                    vectorStoreSearchComparisonFilterMatch(attributes, filter, type);
            default -> throw new IllegalArgumentException("不支持的 filters.type：" + type);
        };
    }

    private boolean vectorStoreSearchCompoundFilterMatch(JsonNode attributes, JsonNode filter, boolean andMode) {
        JsonNode filters = filter.path("filters");
        if (!filters.isArray() || filters.isEmpty()) {
            throw new IllegalArgumentException("compound filters 至少包含 1 个子 filter。");
        }
        if (filters.size() > 16) {
            throw new IllegalArgumentException("compound filters 最多包含 16 个子 filter。");
        }
        for (JsonNode child : filters) {
            boolean matched = vectorStoreSearchFilterMatch(attributes, child);
            if (andMode && !matched) {
                return false;
            }
            if (!andMode && matched) {
                return true;
            }
        }
        return andMode;
    }

    private boolean vectorStoreSearchComparisonFilterMatch(JsonNode attributes, JsonNode filter, String type) {
        String key = text(filter, "key");
        if (key == null) {
            throw new IllegalArgumentException("comparison filters.key 为必填字段。");
        }
        JsonNode value = filter.path("value");
        if (value.isMissingNode()) {
            throw new IllegalArgumentException("comparison filters.value 为必填字段。");
        }
        JsonNode actual = attributes == null || attributes.isMissingNode() ? JsonNodeFactory.instance.missingNode() : attributes.path(key);
        return switch (type) {
            case "eq" -> vectorStoreSearchValueEquals(actual, value);
            case "ne" -> !vectorStoreSearchValueEquals(actual, value);
            case "gt" -> vectorStoreSearchNumberComparisonMatches(actual, value, "gt");
            case "gte" -> vectorStoreSearchNumberComparisonMatches(actual, value, "gte");
            case "lt" -> vectorStoreSearchNumberComparisonMatches(actual, value, "lt");
            case "lte" -> vectorStoreSearchNumberComparisonMatches(actual, value, "lte");
            case "in" -> vectorStoreSearchValueIn(actual, value);
            case "nin" -> !vectorStoreSearchValueIn(actual, value);
            default -> false;
        };
    }

    private boolean vectorStoreSearchValueEquals(JsonNode actual, JsonNode expected) {
        if (actual == null || actual.isMissingNode()) {
            return expected == null || expected.isNull() || expected.isMissingNode();
        }
        if (expected == null || expected.isMissingNode()) {
            return actual.isNull();
        }
        if (actual.isNumber() && expected.isNumber()) {
            return Double.compare(actual.asDouble(), expected.asDouble()) == 0;
        }
        if (actual.isBoolean() && expected.isBoolean()) {
            return actual.asBoolean() == expected.asBoolean();
        }
        if (actual.isNull() || expected.isNull()) {
            return actual.isNull() && expected.isNull();
        }
        return actual.asText("").equals(expected.asText(""));
    }

    private boolean vectorStoreSearchNumberComparisonMatches(JsonNode actual, JsonNode expected, String type) {
        if (actual == null || !actual.isNumber() || expected == null || !expected.isNumber()) {
            return false;
        }
        int comparison = Double.compare(actual.asDouble(), expected.asDouble());
        return switch (type) {
            case "gt" -> comparison > 0;
            case "gte" -> comparison >= 0;
            case "lt" -> comparison < 0;
            case "lte" -> comparison <= 0;
            default -> false;
        };
    }

    private boolean vectorStoreSearchValueIn(JsonNode actual, JsonNode values) {
        if (!values.isArray()) {
            throw new IllegalArgumentException("in/nin filters.value 必须是 JSON array。");
        }
        for (JsonNode value : values) {
            if (vectorStoreSearchValueEquals(actual, value)) {
                return true;
            }
        }
        return false;
    }

    private Optional<VectorStoreSearchResult> vectorStoreSearchResult(
            ObjectNode attachment,
            Long distributedKeyId,
            List<String> queries) {
        String fileId = attachment.path("id").asText(null);
        if (fileId == null || fileId.isBlank()) {
            return Optional.empty();
        }
        Optional<GatewayFileContent> maybeContent = tryResolveGatewayFileContent(fileId, distributedKeyId);
        if (maybeContent.isEmpty()) {
            return Optional.empty();
        }
        GatewayFileContent fileContent = maybeContent.get();
        String text = new String(fileContent.bytes(), StandardCharsets.UTF_8);
        double score = vectorStoreSearchScore(text, queries);
        if (score <= 0.0d) {
            return Optional.empty();
        }
        ObjectNode item = objectMapper.createObjectNode();
        item.put("file_id", fileId);
        item.put("filename", fileContent.metadata().filename());
        item.put("score", score);
        item.set("attributes", copyObject(attachment.path("attributes")));
        ArrayNode content = item.putArray("content");
        content.addObject()
                .put("type", "text")
                .put("text", vectorStoreSearchSnippet(text, queries));
        return Optional.of(new VectorStoreSearchResult(fileId, score, item));
    }

    private Optional<GatewayFileContent> tryResolveGatewayFileContent(String fileKey, Long distributedKeyId) {
        try {
            return Optional.of(resolveGatewayFileContent(fileKey, distributedKeyId));
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            return Optional.empty();
        }
    }

    private double vectorStoreSearchScore(String text, List<String> queries) {
        if (text == null || text.isBlank()) {
            return 0.0d;
        }
        String normalizedText = text.toLowerCase(Locale.ROOT);
        double totalScore = 0.0d;
        for (String query : queries) {
            String normalizedQuery = query.toLowerCase(Locale.ROOT);
            if (normalizedText.contains(normalizedQuery)) {
                totalScore += 1.0d;
                continue;
            }
            List<String> terms = vectorStoreSearchTerms(query);
            if (terms.isEmpty()) {
                continue;
            }
            long matchedTerms = terms.stream()
                    .filter(term -> normalizedText.contains(term.toLowerCase(Locale.ROOT)))
                    .count();
            totalScore += ((double) matchedTerms / (double) terms.size()) * 0.8d;
        }
        return Math.min(1.0d, totalScore / queries.size());
    }

    private List<String> vectorStoreSearchTerms(String query) {
        List<String> terms = new ArrayList<>();
        for (String term : query.trim().split("\\s+")) {
            if (!term.isBlank()) {
                terms.add(term);
            }
        }
        return terms;
    }

    private String vectorStoreSearchSnippet(String text, List<String> queries) {
        if (text.length() <= 1024) {
            return text;
        }
        String normalizedText = text.toLowerCase(Locale.ROOT);
        int matchIndex = 0;
        for (String query : queries) {
            int exactIndex = normalizedText.indexOf(query.toLowerCase(Locale.ROOT));
            if (exactIndex >= 0) {
                matchIndex = exactIndex;
                break;
            }
            for (String term : vectorStoreSearchTerms(query)) {
                int termIndex = normalizedText.indexOf(term.toLowerCase(Locale.ROOT));
                if (termIndex >= 0) {
                    matchIndex = termIndex;
                    break;
                }
            }
            if (matchIndex > 0) {
                break;
            }
        }
        int start = Math.max(0, matchIndex - 256);
        int end = Math.min(text.length(), start + 1024);
        return text.substring(start, end);
    }

    private void adjustVectorStoreFileCount(GatewayAsyncResourceEntity vectorStore, int delta, String eventType) {
        ObjectNode response = readObject(vectorStore.getResponsePayloadJson());
        int currentTotal = response.path("file_counts").path("total").asInt(0);
        int nextTotal = Math.max(0, currentTotal + delta);
        response.set("file_counts", vectorStoreFileCounts(nextTotal));
        response.put("last_active_at", now().getEpochSecond());
        vectorStore.setResponsePayloadJson(writeJson(response));
        vectorStore.setMetadataJson(writeJson(appendEvent(readObject(vectorStore.getMetadataJson()), eventType, vectorStore.getStatus())));
        gatewayAsyncResourceRepository.save(vectorStore);
    }

    private ResourceCursor vectorStoreCursor(Long distributedKeyId, String after) {
        if (after == null || after.isBlank()) {
            return null;
        }
        Optional<GatewayAsyncResourceEntity> entity = gatewayAsyncResourceRepository
                .findByResourceKeyAndResourceTypeAndDistributedKeyIdAndDeletedFalse(
                        after,
                        GatewayAsyncResourceType.VECTOR_STORE,
                        distributedKeyId
                );
        return entity.map(ResourceCursor::from).orElseGet(ResourceCursor::invalidCursor);
    }

    private List<GatewayAsyncResourceEntity> fetchVectorStoreCandidates(
            Long distributedKeyId,
            String order,
            ResourceCursor cursor,
            int batchSize) {
        PageRequest pageable = PageRequest.of(0, batchSize);
        Instant cursorCreatedAt = cursor == null ? null : cursor.createdAt();
        Long cursorId = cursor == null ? null : cursor.id();
        if ("desc".equals(order)) {
            return gatewayAsyncResourceRepository.findStoredResourcesAfterCursorDesc(
                    distributedKeyId,
                    GatewayAsyncResourceType.VECTOR_STORE,
                    "vs_",
                    null,
                    cursorCreatedAt,
                    cursorId,
                    pageable);
        }
        return gatewayAsyncResourceRepository.findStoredResourcesAfterCursorAsc(
                distributedKeyId,
                GatewayAsyncResourceType.VECTOR_STORE,
                "vs_",
                null,
                cursorCreatedAt,
                cursorId,
                pageable);
    }

    private ResourceCursor vectorStoreFileCursor(String vectorStoreId, Long distributedKeyId, String after) {
        if (after == null || after.isBlank()) {
            return null;
        }
        Optional<GatewayAsyncResourceEntity> entity = findActiveVectorStoreFileEntity(vectorStoreId, after, distributedKeyId);
        return entity.map(ResourceCursor::from).orElseGet(ResourceCursor::invalidCursor);
    }

    private List<GatewayAsyncResourceEntity> fetchVectorStoreFileCandidates(
            Long distributedKeyId,
            String vectorStoreId,
            String order,
            ResourceCursor cursor,
            int batchSize) {
        PageRequest pageable = PageRequest.of(0, batchSize);
        Instant cursorCreatedAt = cursor == null ? null : cursor.createdAt();
        Long cursorId = cursor == null ? null : cursor.id();
        if ("desc".equals(order)) {
            return gatewayAsyncResourceRepository.findChildResourcesAfterCursorDesc(
                    distributedKeyId,
                    GatewayAsyncResourceType.VECTOR_STORE_FILE,
                    vectorStoreId,
                    cursorCreatedAt,
                    cursorId,
                    pageable);
        }
        return gatewayAsyncResourceRepository.findChildResourcesAfterCursorAsc(
                distributedKeyId,
                GatewayAsyncResourceType.VECTOR_STORE_FILE,
                vectorStoreId,
                cursorCreatedAt,
                cursorId,
                pageable);
    }

    private GatewayAsyncResourceEntity getRequiredVectorStoreFile(String vectorStoreId, String fileId, Long distributedKeyId) {
        return findActiveVectorStoreFileEntity(vectorStoreId, fileId, distributedKeyId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的 Vector Store File。"));
    }

    private GatewayAsyncResourceEntity getRequiredVectorStoreFileBatch(String vectorStoreId, String batchId, Long distributedKeyId) {
        GatewayAsyncResourceEntity batch = getRequired(batchId, GatewayAsyncResourceType.VECTOR_STORE_FILE_BATCH, distributedKeyId);
        if (!vectorStoreId.equals(batch.getUpstreamObjectId())) {
            throw new IllegalArgumentException("Vector Store File Batch 不属于指定 Vector Store。");
        }
        return batch;
    }

    private Optional<GatewayAsyncResourceEntity> findActiveVectorStoreFileEntity(String vectorStoreId, String fileId, Long distributedKeyId) {
        if (fileId == null || fileId.isBlank()) {
            return Optional.empty();
        }
        return gatewayAsyncResourceRepository
                .findAllByDistributedKeyIdAndUpstreamObjectIdAndDeletedFalse(distributedKeyId, vectorStoreId)
                .stream()
                .filter(entity -> entity.getResourceType() == GatewayAsyncResourceType.VECTOR_STORE_FILE)
                .filter(entity -> fileId.equals(readObject(entity.getResponsePayloadJson()).path("id").asText()))
                .findFirst();
    }

    private ResourceCursor conversationItemCursor(String conversationId, Long distributedKeyId, String after) {
        if (after == null || after.isBlank()) {
            return null;
        }
        Optional<GatewayAsyncResourceEntity> entity = gatewayAsyncResourceRepository
                .findByResourceKeyAndResourceTypeAndDistributedKeyIdAndDeletedFalse(
                        after,
                        GatewayAsyncResourceType.CONVERSATION_ITEM,
                        distributedKeyId
                );
        if (entity.isEmpty() || !conversationId.equals(entity.get().getUpstreamObjectId())) {
            return ResourceCursor.invalidCursor();
        }
        return ResourceCursor.from(entity.get());
    }

    private List<GatewayAsyncResourceEntity> fetchConversationItemCandidates(
            Long distributedKeyId,
            String conversationId,
            String order,
            ResourceCursor cursor,
            int batchSize) {
        if (cursor != null && cursor.invalid()) {
            return List.of();
        }
        PageRequest pageable = PageRequest.of(0, batchSize);
        Instant cursorCreatedAt = cursor == null ? null : cursor.createdAt();
        Long cursorId = cursor == null ? null : cursor.id();
        if ("desc".equals(order)) {
            return gatewayAsyncResourceRepository.findChildResourcesAfterCursorDesc(
                    distributedKeyId,
                    GatewayAsyncResourceType.CONVERSATION_ITEM,
                    conversationId,
                    cursorCreatedAt,
                    cursorId,
                    pageable);
        }
        return gatewayAsyncResourceRepository.findChildResourcesAfterCursorAsc(
                distributedKeyId,
                GatewayAsyncResourceType.CONVERSATION_ITEM,
                conversationId,
                cursorCreatedAt,
                cursorId,
                pageable);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ObjectNode assertStoredResponse(GatewayAsyncResourceEntity entity) {
        ObjectNode response = readObject(entity.getResponsePayloadJson());
        if (!"response".equals(response.path("object").asText())) {
            throw new IllegalArgumentException("未找到 stored Response。");
        }
        return response;
    }

    private boolean isTerminalResponseStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return "completed".equals(normalized)
                || "failed".equals(normalized)
                || "cancelled".equals(normalized)
                || "canceled".equals(normalized)
                || "deleted".equals(normalized);
    }

    private List<JsonNode> responseInputItems(String responseId, JsonNode input) {
        if (input == null || input.isMissingNode() || input.isNull()) {
            return List.of();
        }
        List<JsonNode> items = new ArrayList<>();
        if (input.isArray()) {
            int index = 0;
            for (JsonNode item : input) {
                items.add(responseInputItem(responseId, item, index++));
            }
            return items;
        }
        return List.of(responseInputItem(responseId, input, 0));
    }

    private JsonNode responseInputItem(String responseId, JsonNode item, int index) {
        ObjectNode normalized;
        if (item != null && item.isObject()) {
            normalized = copyObject(item);
            if (!normalized.has("type") && normalized.has("role")) {
                normalized.put("type", "message");
            }
        } else {
            normalized = objectMapper.createObjectNode();
            normalized.put("type", "message");
            normalized.put("role", "user");
            var content = objectMapper.createArrayNode();
            content.add(objectMapper.createObjectNode()
                    .put("type", "input_text")
                    .put("text", item == null || item.isNull() ? "" : item.asText()));
            normalized.set("content", content);
        }
        if (!normalized.has("id") || normalized.path("id").asText().isBlank()) {
            normalized.put("id", "msg_" + responseId + "_" + index);
        }
        return normalized;
    }

    private String normalizeResponseInputItemsOrder(String order) {
        if (order == null || order.isBlank()) {
            return "desc";
        }
        return normalizeListOrder(order);
    }

    private int normalizeListLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIST_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIST_LIMIT) {
            throw new IllegalArgumentException("limit 必须在 1 到 100 之间。");
        }
        return limit;
    }

    private Integer parseListLimit(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("limit 必须是整数。");
        }
    }

    private String normalizeListOrder(String order) {
        if (order == null || order.isBlank()) {
            return "asc";
        }
        String normalized = order.trim().toLowerCase();
        if ("asc".equals(normalized) || "desc".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("order 必须是 asc 或 desc。");
    }

    public JsonNode createUpload(Long distributedKeyId, JsonNode requestBody) {
        return createUpload(distributedKeyId, requestBody, null);
    }

    public JsonNode createUpload(Long distributedKeyId, JsonNode requestBody, Long preferredCredentialId) {
        ObjectNode payload = requireObject(requestBody);
        UpstreamTarget target = resolveUploadTarget(distributedKeyId, preferredCredentialId);
        if (target.siteProfile().getSiteKind() == UpstreamSiteKind.GEMINI_DIRECT) {
            return persistLocalUploadResource(distributedKeyId, payload, target);
        }
        JsonNode upstreamResponse = invokeUpstreamJson(target, "/v1/uploads", rewriteFileRefs(payload, distributedKeyId, target));
        return persistUpstreamBackedResource(distributedKeyId, GatewayAsyncResourceType.UPLOAD, "upload_", payload, upstreamResponse, "upload", target);
    }

    public JsonNode getUpload(String uploadId, Long distributedKeyId) {
        return readOrSyncResource(uploadId, distributedKeyId, GatewayAsyncResourceType.UPLOAD, "upload");
    }

    public Mono<JsonNode> addUploadPart(String uploadId, Long distributedKeyId, FilePart dataPart) {
        GatewayAsyncResourceEntity entity = getRequired(uploadId, GatewayAsyncResourceType.UPLOAD, distributedKeyId);
        assertUploadWritable(entity, "追加 part");
        ObjectNode metadata = readObject(entity.getMetadataJson());
        String upstreamId = metadata.path("upstream_object_id").asText(null);
        if (upstreamId == null || upstreamId.isBlank()) {
            MediaType contentType = dataPart.headers() == null ? null : dataPart.headers().getContentType();
            return readPartBytes(dataPart)
                    .map(bytes -> addLocalUploadPart(
                            entity,
                            dataPart.filename(),
                            contentType == null ? null : contentType.toString(),
                            bytes,
                            null
                    ));
        }
        UpstreamTarget target = resolveUpstreamTargetForEntity(entity, metadata);
        return invokeUpstreamMultipart(target, target.path() + "/" + upstreamId + "/parts", dataPart)
                .map(upstreamResponse -> persistUploadPart(entity, uploadId, dataPart.filename(), upstreamResponse));
    }

    public Mono<JsonNode> addUploadPartFromGatewayFile(String uploadId, Long distributedKeyId, String fileKey) {
        GatewayAsyncResourceEntity entity = getRequired(uploadId, GatewayAsyncResourceType.UPLOAD, distributedKeyId);
        assertUploadWritable(entity, "追加 part");
        GatewayFileContent fileContent = getGatewayFileContent(fileKey, distributedKeyId);
        ObjectNode metadata = readObject(entity.getMetadataJson());
        String upstreamId = metadata.path("upstream_object_id").asText(null);
        if (upstreamId == null || upstreamId.isBlank()) {
            return Mono.fromSupplier(() -> addLocalUploadPart(
                    entity,
                    fileContent.metadata().filename(),
                    fileContent.mimeType(),
                    fileContent.bytes(),
                    fileKey
            ));
        }
        UpstreamTarget target = resolveUpstreamTargetForEntity(entity, metadata);
        return invokeUpstreamMultipart(
                target,
                target.path() + "/" + upstreamId + "/parts",
                fileContent.metadata().filename(),
                fileContent.mimeType(),
                fileContent.bytes()
        ).map(upstreamResponse -> persistUploadPart(entity, uploadId, fileContent.metadata().filename(), upstreamResponse));
    }

    public JsonNode completeUpload(String uploadId, Long distributedKeyId) {
        return completeRemoteStatus(uploadId, distributedKeyId, GatewayAsyncResourceType.UPLOAD, InteropFeature.UPLOAD_CREATE, "/complete");
    }

    public JsonNode cancelUpload(String uploadId, Long distributedKeyId) {
        return completeRemoteStatus(uploadId, distributedKeyId, GatewayAsyncResourceType.UPLOAD, InteropFeature.UPLOAD_CREATE, "/cancel");
    }

    public JsonNode createBatch(Long distributedKeyId, JsonNode requestBody) {
        return createBatch(distributedKeyId, requestBody, null);
    }

    public JsonNode createBatch(Long distributedKeyId, JsonNode requestBody, Long preferredCredentialId) {
        ObjectNode sourcePayload = copyObject(requireObject(requestBody));
        UpstreamTarget target = resolveUpstreamTarget(distributedKeyId, InteropFeature.BATCH_CREATE, preferredCredentialId);
        ObjectNode payload = rewriteFileRefs(copyObject(sourcePayload), distributedKeyId, target);
        if (supportsGoogleGenAiBatching(target.siteProfile().getSiteKind())) {
            return createGeminiBatch(distributedKeyId, sourcePayload, payload, target);
        }
        JsonNode upstreamResponse = invokeUpstreamJson(target, "/v1/batches", payload);
        return persistUpstreamBackedResource(distributedKeyId, GatewayAsyncResourceType.BATCH, "batch_", payload, upstreamResponse, "batch", target);
    }

    public JsonNode getBatch(String batchId, Long distributedKeyId) {
        return readOrSyncResource(batchId, distributedKeyId, GatewayAsyncResourceType.BATCH, "batch");
    }

    @Transactional(readOnly = true)
    public JsonNode listBatches(Long distributedKeyId, JsonNode query) {
        return listBatches(
                distributedKeyId,
                text(query, "after"),
                parseListLimit(text(query, "limit"))
        );
    }

    @Transactional(readOnly = true)
    public JsonNode listBatches(Long distributedKeyId, String after, Integer limit) {
        int pageSize = normalizeListLimit(limit);
        StoredChatCursor cursor = null;
        if (after != null && !after.isBlank()) {
            Optional<GatewayAsyncResourceEntity> cursorEntity = gatewayAsyncResourceRepository
                    .findByResourceKeyAndResourceTypeAndDistributedKeyIdAndDeletedFalse(
                            after,
                            GatewayAsyncResourceType.BATCH,
                            distributedKeyId);
            if (cursorEntity.isEmpty()) {
                return listEnvelope(List.of(), false);
            }
            cursor = StoredChatCursor.from(cursorEntity.get());
        }

        int batchSize = pageSize + 1;
        List<JsonNode> collected = new ArrayList<>();
        StoredChatCursor currentCursor = cursor;
        boolean exhausted = false;
        while (collected.size() <= pageSize && !exhausted) {
            List<GatewayAsyncResourceEntity> candidates = gatewayAsyncResourceRepository.findStoredResourcesAfterCursorDesc(
                    distributedKeyId,
                    GatewayAsyncResourceType.BATCH,
                    "batch_",
                    null,
                    currentCursor == null ? null : currentCursor.createdAt(),
                    currentCursor == null ? null : currentCursor.id(),
                    PageRequest.of(0, batchSize));
            if (candidates.isEmpty()) {
                break;
            }
            for (GatewayAsyncResourceEntity entity : candidates) {
                currentCursor = StoredChatCursor.from(entity);
                if (!isOpenAiBatchListCandidate(entity)) {
                    continue;
                }
                collected.add(readObject(entity.getResponsePayloadJson()));
                if (collected.size() > pageSize) {
                    break;
                }
            }
            exhausted = candidates.size() < batchSize;
        }

        boolean hasMore = collected.size() > pageSize;
        return listEnvelope(collected.stream().limit(pageSize).toList(), hasMore);
    }

    @Transactional(readOnly = true)
    public GoogleNativeBatchView getBatchView(String batchId, Long distributedKeyId) {
        GatewayAsyncResourceEntity entity = getRequired(batchId, GatewayAsyncResourceType.BATCH, distributedKeyId);
        return toGoogleNativeBatchView(entity);
    }

    public GoogleNativeBatchView getBatchByUpstreamObjectId(String upstreamObjectId, Long distributedKeyId) {
        GatewayAsyncResourceEntity entity = gatewayAsyncResourceRepository
                .findByDistributedKeyIdAndResourceTypeAndUpstreamObjectIdAndDeletedFalse(
                        distributedKeyId,
                        GatewayAsyncResourceType.BATCH,
                        upstreamObjectId
                )
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的 Google batch 对象。"));
        getBatch(entity.getResourceKey(), distributedKeyId);
        return toGoogleNativeBatchView(entity);
    }

    public JsonNode cancelBatch(String batchId, Long distributedKeyId) {
        return completeRemoteStatus(batchId, distributedKeyId, GatewayAsyncResourceType.BATCH, InteropFeature.BATCH_CREATE, "/cancel");
    }

    public JsonNode createAnthropicMessageBatch(Long distributedKeyId, JsonNode requestBody, Long preferredCredentialId) {
        ObjectNode payload = copyObject(requireObject(requestBody));
        UpstreamTarget target = resolveAnthropicMessageBatchTarget(distributedKeyId, preferredCredentialId);
        AnthropicClient client = createAnthropicClient(target);
        try {
            BetaMessageBatch batch = client.beta().messages().batches().create(toAnthropicBatchCreateParams(payload));
            return persistAnthropicMessageBatchResource(distributedKeyId, payload, batch, target);
        } finally {
            client.close();
        }
    }

    public JsonNode getAnthropicMessageBatch(String messageBatchId, Long distributedKeyId) {
        GatewayAsyncResourceEntity entity = gatewayAsyncResourceRepository
                .findByDistributedKeyIdAndResourceTypeAndUpstreamObjectIdAndDeletedFalse(
                        distributedKeyId,
                        GatewayAsyncResourceType.BATCH,
                        messageBatchId
                )
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的 Anthropic message batch 对象。"));
        ObjectNode metadata = readObject(entity.getMetadataJson());
        UpstreamTarget target = resolveAnthropicMessageBatchTargetForEntity(entity, metadata);
        return syncAnthropicMessageBatchResource(entity, fetchAnthropicMessageBatch(metadata, target));
    }

    public JsonNode cancelAnthropicMessageBatch(String messageBatchId, Long distributedKeyId) {
        GatewayAsyncResourceEntity entity = gatewayAsyncResourceRepository
                .findByDistributedKeyIdAndResourceTypeAndUpstreamObjectIdAndDeletedFalse(
                        distributedKeyId,
                        GatewayAsyncResourceType.BATCH,
                        messageBatchId
                )
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的 Anthropic message batch 对象。"));
        ObjectNode metadata = readObject(entity.getMetadataJson());
        UpstreamTarget target = resolveAnthropicMessageBatchTargetForEntity(entity, metadata);
        cancelAnthropicMessageBatch(metadata, target);
        return syncAnthropicMessageBatchResource(entity, fetchAnthropicMessageBatch(metadata, target));
    }

    public GoogleNativeBatchView cancelBatchByUpstreamObjectId(String upstreamObjectId, Long distributedKeyId) {
        GatewayAsyncResourceEntity entity = gatewayAsyncResourceRepository
                .findByDistributedKeyIdAndResourceTypeAndUpstreamObjectIdAndDeletedFalse(
                        distributedKeyId,
                        GatewayAsyncResourceType.BATCH,
                        upstreamObjectId
                )
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的 Google batch 对象。"));
        cancelBatch(entity.getResourceKey(), distributedKeyId);
        return toGoogleNativeBatchView(entity);
    }

    public JsonNode createTuning(Long distributedKeyId, JsonNode requestBody) {
        return createTuning(distributedKeyId, requestBody, null);
    }

    public JsonNode createTuning(Long distributedKeyId, JsonNode requestBody, Long preferredCredentialId) {
        ObjectNode sourcePayload = copyObject(requireObject(requestBody));
        UpstreamTarget target = resolveUpstreamTarget(distributedKeyId, InteropFeature.TUNING_CREATE, preferredCredentialId);
        ObjectNode payload = rewriteFileRefs(copyObject(sourcePayload), distributedKeyId, target);
        if (supportsGoogleGenAiBatching(target.siteProfile().getSiteKind())) {
            return createGeminiTuning(distributedKeyId, sourcePayload, payload, target);
        }
        JsonNode upstreamResponse = invokeUpstreamJson(target, "/v1/fine_tuning/jobs", payload);
        return persistUpstreamBackedResource(distributedKeyId, GatewayAsyncResourceType.TUNING, "ftjob_", payload, upstreamResponse, "fine_tuning.job", target);
    }

    public JsonNode getTuning(String tuningId, Long distributedKeyId) {
        return readOrSyncResource(tuningId, distributedKeyId, GatewayAsyncResourceType.TUNING, "fine_tuning.job");
    }

    @Transactional(readOnly = true)
    public JsonNode listTuningEvents(String tuningId, Long distributedKeyId, JsonNode query) {
        return listTuningEvents(
                tuningId,
                distributedKeyId,
                text(query, "after"),
                parseListLimit(text(query, "limit"))
        );
    }

    @Transactional(readOnly = true)
    public JsonNode listTuningEvents(String tuningId, Long distributedKeyId, String after, Integer limit) {
        GatewayAsyncResourceEntity entity = getRequired(tuningId, GatewayAsyncResourceType.TUNING, distributedKeyId);
        ObjectNode metadata = readObject(entity.getMetadataJson());
        List<JsonNode> events = tuningEventItems(entity, metadata);
        return listInMemoryItems(events, after, normalizeListLimit(limit), false);
    }

    @Transactional(readOnly = true)
    public JsonNode listTuningCheckpoints(String tuningId, Long distributedKeyId, JsonNode query) {
        return listTuningCheckpoints(
                tuningId,
                distributedKeyId,
                text(query, "after"),
                parseListLimit(text(query, "limit"))
        );
    }

    @Transactional(readOnly = true)
    public JsonNode listTuningCheckpoints(String tuningId, Long distributedKeyId, String after, Integer limit) {
        GatewayAsyncResourceEntity entity = getRequired(tuningId, GatewayAsyncResourceType.TUNING, distributedKeyId);
        ObjectNode metadata = readObject(entity.getMetadataJson());
        ObjectNode response = readObject(entity.getResponsePayloadJson());
        List<JsonNode> checkpoints = tuningCheckpointItems(entity, metadata, response);
        return listInMemoryItems(checkpoints, after, normalizeListLimit(limit), true);
    }

    @Transactional(readOnly = true)
    public JsonNode listTunings(Long distributedKeyId) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "list");
        var data = response.putArray("data");
        gatewayAsyncResourceRepository.search(
                        distributedKeyId,
                        GatewayAsyncResourceType.TUNING,
                        null,
                        PageRequest.of(0, 100))
                .forEach(entity -> data.add(readObject(entity.getResponsePayloadJson())));
        response.put("has_more", false);
        return response;
    }

    public JsonNode cancelTuning(String tuningId, Long distributedKeyId) {
        return completeRemoteStatus(tuningId, distributedKeyId, GatewayAsyncResourceType.TUNING, InteropFeature.TUNING_CREATE, "/cancel");
    }

    public JsonNode createVideoTask(Long distributedKeyId, JsonNode requestBody) {
        return createMediaTask(
                distributedKeyId,
                requestBody,
                GatewayAsyncResourceType.VIDEO,
                "video_",
                "video.generation",
                "video_generation",
                InteropFeature.VIDEO_GENERATION
        );
    }

    @Transactional(readOnly = true)
    public JsonNode mediaProviderMatrix() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("object", "gateway.media_provider_matrix");
        root.put("version", "2026-05-14");
        root.put("generated_at", now().getEpochSecond());
        var video = root.putArray("video");
        video.add(mediaProviderMatrixItem(
                "openai_compatible",
                "OpenAI-compatible Video",
                "SUPPORTED",
                "native_openai_style",
                "/v1/videos/generations",
                "支持 create/get/cancel，适用于 OpenAI direct 和兼容站点。"
        ));
        video.add(mediaProviderMatrixItem(
                "gemini",
                "Gemini / Vertex Video",
                "SUPPORTED",
                "provider_specific_adapter",
                "Gemini Veo provider adapter",
                "支持 provider_mode=adapter 的 create/get/cancel/download 本地生命周期；真实 smoke 需环境变量注入凭证。",
                "video_generation",
                "operator_configured_gemini_veo_pricing",
                "设置 XAG_SMOKE_GEMINI=true 与 Gemini key 后执行真实 smoke；默认跳过。"
        ));
        video.add(mediaProviderMatrixItem(
                "minimax",
                "MiniMax Video",
                "ADAPTER_REQUIRED",
                "provider_specific_adapter_required",
                "MiniMax video API",
                "优先通过 OpenAI-compatible profile 接入，专有 API 需单独适配。"
        ));
        video.add(mediaProviderMatrixItem(
                "midjourney",
                "Midjourney-like Video/Image",
                "NOT_NATIVE",
                "external_async_bridge_required",
                "第三方任务队列",
                "当前不直接保存真实产物，需外部 bridge 提供任务状态。"
        ));

        var music = root.putArray("music");
        music.add(mediaProviderMatrixItem(
                "openai_compatible",
                "OpenAI-compatible Music",
                "SUPPORTED",
                "native_openai_style",
                "/v1/music/generations",
                "支持 create/get/cancel，适用于 OpenAI-compatible 音频生成站点。"
        ));
        music.add(mediaProviderMatrixItem(
                "suno",
                "Suno-like Music",
                "SUPPORTED",
                "provider_specific_adapter",
                "Suno Music provider adapter",
                "支持 provider_mode=adapter, provider_family=suno 的 create/get/cancel/download 本地生命周期；真实 smoke 仅在显式环境变量启用时访问远端。",
                "music_generation",
                "operator_configured_suno_music_pricing",
                "设置 XAG_SMOKE_SUNO=true、XAG_SMOKE_SUNO_BASE_URL 与 Suno key 后执行真实 smoke；默认跳过。"
        ));
        music.add(mediaProviderMatrixItem(
                "minimax",
                "MiniMax Music",
                "ADAPTER_REQUIRED",
                "provider_specific_adapter_required",
                "MiniMax music API",
                "需要 provider-specific adapter 才能覆盖专有任务字段。"
        ));
        music.add(mediaProviderMatrixItem(
                "gemini",
                "Gemini Music",
                "NOT_SUPPORTED",
                "provider_capability_absent",
                "无稳定通用 music task API",
                "当前 capability matrix 不标记为原生 Music 任务。"
        ));
        return root;
    }

    @Transactional(readOnly = true)
    public JsonNode getVideoTask(String videoId, Long distributedKeyId) {
        return readOrSyncResource(videoId, distributedKeyId, GatewayAsyncResourceType.VIDEO, "video.generation");
    }

    public JsonNode cancelVideoTask(String videoId, Long distributedKeyId) {
        return completeRemoteStatus(videoId, distributedKeyId, GatewayAsyncResourceType.VIDEO, InteropFeature.VIDEO_GENERATION, "/cancel");
    }

    public JsonNode downloadVideoTaskArtifact(String videoId, Long distributedKeyId) {
        return downloadMediaTaskArtifact(videoId, distributedKeyId, GatewayAsyncResourceType.VIDEO);
    }

    public JsonNode createMusicTask(Long distributedKeyId, JsonNode requestBody) {
        return createMediaTask(
                distributedKeyId,
                requestBody,
                GatewayAsyncResourceType.MUSIC,
                "music_",
                "music.generation",
                "music_generation",
                InteropFeature.MUSIC_GENERATION
        );
    }

    @Transactional(readOnly = true)
    public JsonNode getMusicTask(String musicId, Long distributedKeyId) {
        return readOrSyncResource(musicId, distributedKeyId, GatewayAsyncResourceType.MUSIC, "music.generation");
    }

    public JsonNode cancelMusicTask(String musicId, Long distributedKeyId) {
        return completeRemoteStatus(musicId, distributedKeyId, GatewayAsyncResourceType.MUSIC, InteropFeature.MUSIC_GENERATION, "/cancel");
    }

    public JsonNode downloadMusicTaskArtifact(String musicId, Long distributedKeyId) {
        return downloadMediaTaskArtifact(musicId, distributedKeyId, GatewayAsyncResourceType.MUSIC);
    }

    public JsonNode createRealtimeClientSecret(Long distributedKeyId, JsonNode requestBody) {
        return createRealtimeClientSecret(distributedKeyId, requestBody, null);
    }

    public JsonNode createRealtimeClientSecret(Long distributedKeyId, JsonNode requestBody, Long preferredCredentialId) {
        ObjectNode payload = requireObject(requestBody);
        UpstreamTarget target;
        try {
            target = resolveUpstreamTarget(distributedKeyId, InteropFeature.REALTIME_CLIENT_SECRET, preferredCredentialId);
        } catch (IllegalArgumentException ex) {
            String blockedReason = geminiRealtimeBlockedReason(distributedKeyId, preferredCredentialId);
            if (blockedReason != null) {
                throw new IllegalArgumentException(blockedReason);
            }
            throw ex;
        }
        JsonNode upstreamResponse = invokeUpstreamJson(target, "/v1/realtime/client_secrets", payload);
        return persistUpstreamBackedResource(
                distributedKeyId,
                GatewayAsyncResourceType.REALTIME_SESSION,
                "sess_",
                payload,
                upstreamResponse,
                "realtime.session",
                target
        );
    }

    private JsonNode readOrSyncResource(
            String resourceKey,
            Long distributedKeyId,
            GatewayAsyncResourceType resourceType,
            String objectName) {
        GatewayAsyncResourceEntity entity = getRequired(resourceKey, resourceType, distributedKeyId);
        ObjectNode metadata = readObject(entity.getMetadataJson());
        if (isProviderSpecificMediaAdapter(metadata, resourceType)) {
            return syncProviderSpecificMediaResource(entity, metadata);
        }
        String upstreamId = metadata.path("upstream_object_id").asText(null);
        if (upstreamId == null || upstreamId.isBlank()) {
            return readJson(entity.getResponsePayloadJson());
        }
        if (isAnthropicNativeBatch(metadata, resourceType)) {
            UpstreamTarget target = resolveAnthropicMessageBatchTargetForEntity(entity, metadata);
            return syncAnthropicMessageBatchResource(entity, fetchAnthropicMessageBatch(metadata, target));
        }
        UpstreamTarget target = resolveUpstreamTargetForEntity(entity, metadata);
        if (supportsGoogleGenAiBatching(target.siteProfile().getSiteKind()) && resourceType == GatewayAsyncResourceType.BATCH) {
            return syncPersistedResource(entity, fetchGeminiBatch(entity, metadata, target), objectName);
        }
        if (supportsGoogleGenAiBatching(target.siteProfile().getSiteKind()) && resourceType == GatewayAsyncResourceType.TUNING) {
            return syncGeminiTuningResource(entity, fetchGeminiTuning(entity, metadata, target), target, metadata, objectName);
        }
        JsonNode upstreamResponse = target.client()
                .get()
                .uri(target.path() + "/" + upstreamId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        return syncPersistedResource(entity, upstreamResponse, objectName);
    }

    private JsonNode completeRemoteStatus(
            String resourceKey,
            Long distributedKeyId,
            GatewayAsyncResourceType resourceType,
            InteropFeature feature,
            String suffix) {
        GatewayAsyncResourceEntity entity = getRequired(resourceKey, resourceType, distributedKeyId);
        ObjectNode metadata = readObject(entity.getMetadataJson());
        String upstreamId = metadata.path("upstream_object_id").asText(null);
        if (upstreamId == null || upstreamId.isBlank()) {
            if (resourceType == GatewayAsyncResourceType.UPLOAD) {
                return suffix.contains("cancel")
                        ? cancelLocalUpload(entity)
                        : completeLocalUpload(entity, distributedKeyId);
            }
            if (suffix.contains("cancel")
                    && (resourceType == GatewayAsyncResourceType.VIDEO || resourceType == GatewayAsyncResourceType.MUSIC)) {
                return cancelLocalMediaTask(resourceKey, distributedKeyId, resourceType);
            }
            return updateLocalStatus(resourceKey, distributedKeyId, resourceType, suffix.contains("cancel") ? "cancelled" : "completed");
        }
        if (resourceType == GatewayAsyncResourceType.UPLOAD) {
            Optional<JsonNode> terminalResponse = terminalUploadResponse(entity, suffix);
            if (terminalResponse.isPresent()) {
                return terminalResponse.get();
            }
        }
        if (isProviderSpecificMediaAdapter(metadata, resourceType)) {
            return cancelProviderSpecificMediaResource(entity, metadata);
        }
        if (isAnthropicNativeBatch(metadata, resourceType)) {
            UpstreamTarget target = resolveAnthropicMessageBatchTargetForEntity(entity, metadata);
            cancelAnthropicMessageBatch(metadata, target);
            return syncAnthropicMessageBatchResource(entity, fetchAnthropicMessageBatch(metadata, target));
        }
        UpstreamTarget target = resolveUpstreamTargetForEntity(entity, metadata);
        if (supportsGoogleGenAiBatching(target.siteProfile().getSiteKind()) && resourceType == GatewayAsyncResourceType.BATCH) {
            cancelGeminiBatch(metadata, target);
            return syncPersistedResource(entity, fetchGeminiBatch(entity, metadata, target), inferObjectName(resourceType));
        }
        if (supportsGoogleGenAiBatching(target.siteProfile().getSiteKind()) && resourceType == GatewayAsyncResourceType.TUNING) {
            cancelGeminiTuning(metadata, target);
            return syncGeminiTuningResource(entity, fetchGeminiTuning(entity, metadata, target), target, metadata, inferObjectName(resourceType));
        }
        JsonNode upstreamResponse = invokeUpstreamJson(target, target.path() + "/" + upstreamId + suffix, objectMapper.createObjectNode());
        return syncPersistedResource(entity, upstreamResponse, inferObjectName(resourceType));
    }

    private JsonNode createGeminiBatch(
            Long distributedKeyId,
            ObjectNode sourcePayload,
            ObjectNode payload,
            UpstreamTarget target) {
        String model = requireText(payload, "model", "Gemini batch_create 需要显式 model。");
        String inputFileId = requireText(payload, "input_file_id", "Gemini batch_create 需要 input_file_id。");
        BatchJobSource source = BatchJobSource.builder()
                .fileName(inputFileId)
                .build();
        CreateBatchJobConfig.Builder configBuilder = CreateBatchJobConfig.builder();
        if (payload.hasNonNull("metadata") && payload.path("metadata").isObject()) {
            String displayName = payload.path("metadata").path("display_name").asText(null);
            if (displayName != null && !displayName.isBlank()) {
                configBuilder.displayName(displayName);
            }
        }
        try (Client client = createGeminiClient(target)) {
            BatchJob batchJob = client.batches.create(model, source, configBuilder.build());
            JsonNode response = mapGeminiBatchJob(batchJob, payload);
            String upstreamObjectId = batchJob.name()
                    .orElseThrow(() -> new IllegalStateException("Gemini batch 响应缺少 name。"));
            return persistUpstreamBackedResource(
                    distributedKeyId,
                    GatewayAsyncResourceType.BATCH,
                    "batch_",
                    payload,
                    response,
                    "batch",
                    target,
                    upstreamObjectId
            );
        }
    }

    private JsonNode fetchGeminiBatch(
            GatewayAsyncResourceEntity entity,
            ObjectNode metadata,
            UpstreamTarget target) {
        String upstreamObjectId = requireUpstreamObjectId(metadata, "Gemini batch 对象缺少 upstream_object_id。");
        try (Client client = createGeminiClient(target)) {
            BatchJob batchJob = client.batches.get(upstreamObjectId, GetBatchJobConfig.builder().build());
            return mapGeminiBatchJob(batchJob, readObject(entity.getRequestPayloadJson()));
        }
    }

    private void cancelGeminiBatch(ObjectNode metadata, UpstreamTarget target) {
        String upstreamObjectId = requireUpstreamObjectId(metadata, "Gemini batch 对象缺少 upstream_object_id。");
        try (Client client = createGeminiClient(target)) {
            client.batches.cancel(upstreamObjectId, CancelBatchJobConfig.builder().build());
        }
    }

    private JsonNode createGeminiTuning(
            Long distributedKeyId,
            ObjectNode sourcePayload,
            ObjectNode payload,
            UpstreamTarget target) {
        if (sourcePayload.hasNonNull("validation_file")) {
            throw new IllegalArgumentException("Gemini tuning 暂不支持 validation_file。");
        }
        String model = requireText(payload, "model", "Gemini tuning_create 需要显式 model。");
        String trainingFileKey = requireText(sourcePayload, "training_file", "Gemini tuning_create 需要 training_file。");
        TuningDataset dataset = TuningDataset.builder()
                .examples(parseTuningExamples(distributedKeyId, trainingFileKey))
                .build();
        CreateTuningJobConfig.Builder configBuilder = CreateTuningJobConfig.builder();
        if (sourcePayload.hasNonNull("suffix")) {
            String suffix = sourcePayload.path("suffix").asText(null);
            if (suffix != null && !suffix.isBlank()) {
                configBuilder.tunedModelDisplayName(suffix);
            }
        }
        try (Client client = createGeminiClient(target)) {
            TuningJob tuningJob = client.tunings.tune(model, dataset, configBuilder.build());
            JsonNode response = mapGeminiTuningJob(tuningJob, payload);
            String upstreamObjectId = tuningJob.name()
                    .orElseThrow(() -> new IllegalStateException("Gemini tuning 响应缺少 name。"));
            return persistUpstreamBackedResource(
                    distributedKeyId,
                    GatewayAsyncResourceType.TUNING,
                    "ftjob_",
                    payload,
                    response,
                    "fine_tuning.job",
                    target,
                    upstreamObjectId
            );
        }
    }

    private BetaMessageBatch fetchAnthropicMessageBatch(
            ObjectNode metadata,
            UpstreamTarget target) {
        String upstreamObjectId = requireUpstreamObjectId(metadata, "Anthropic message batch 对象缺少 upstream_object_id。");
        AnthropicClient client = createAnthropicClient(target);
        try {
            return client.beta().messages().batches().retrieve(
                    upstreamObjectId,
                    BatchRetrieveParams.builder()
                            .messageBatchId(upstreamObjectId)
                            .addBeta(AnthropicBeta.MESSAGE_BATCHES_2024_09_24)
                            .build()
            );
        } finally {
            client.close();
        }
    }

    private void cancelAnthropicMessageBatch(
            ObjectNode metadata,
            UpstreamTarget target) {
        String upstreamObjectId = requireUpstreamObjectId(metadata, "Anthropic message batch 对象缺少 upstream_object_id。");
        AnthropicClient client = createAnthropicClient(target);
        try {
            client.beta().messages().batches().cancel(
                    upstreamObjectId,
                    BatchCancelParams.builder()
                            .messageBatchId(upstreamObjectId)
                            .addBeta(AnthropicBeta.MESSAGE_BATCHES_2024_09_24)
                            .build()
            );
        } finally {
            client.close();
        }
    }

    private JsonNode fetchGeminiTuning(
            GatewayAsyncResourceEntity entity,
            ObjectNode metadata,
            UpstreamTarget target) {
        String upstreamObjectId = requireUpstreamObjectId(metadata, "Gemini tuning 对象缺少 upstream_object_id。");
        try (Client client = createGeminiClient(target)) {
            TuningJob tuningJob = client.tunings.get(upstreamObjectId, GetTuningJobConfig.builder().build());
            return mapGeminiTuningJob(tuningJob, readObject(entity.getRequestPayloadJson()));
        }
    }

    private JsonNode syncGeminiTuningResource(
            GatewayAsyncResourceEntity entity,
            JsonNode upstreamResponse,
            UpstreamTarget target,
            ObjectNode metadata,
            String objectName) {
        JsonNode synced = syncPersistedResource(entity, upstreamResponse, objectName);
        registerFineTunedModelIfReady(entity, target, metadata, synced);
        unregisterFineTunedModelIfTerminated(entity, target, synced);
        return synced;
    }

    private void cancelGeminiTuning(ObjectNode metadata, UpstreamTarget target) {
        String upstreamObjectId = requireUpstreamObjectId(metadata, "Gemini tuning 对象缺少 upstream_object_id。");
        try (Client client = createGeminiClient(target)) {
            client.tunings.cancel(upstreamObjectId, CancelTuningJobConfig.builder().build());
        }
    }

    private JsonNode mapGeminiBatchJob(BatchJob batchJob, JsonNode requestPayload) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "batch");
        putIfPresent(response, "model", batchJob.model().orElse(text(requestPayload, "model")));
        putIfPresent(response, "endpoint", text(requestPayload, "endpoint"));
        putIfPresent(response, "completion_window", text(requestPayload, "completion_window"));
        putIfPresent(response, "input_file_id", text(requestPayload, "input_file_id"));
        response.put("created_at", epochSeconds(batchJob.createTime().orElse(now())));
        response.put("status", geminiBatchStatus(batchJob));
        if (batchJob.error().flatMap(error -> error.message()).isPresent()) {
            ObjectNode error = response.putObject("error");
            error.put("message", batchJob.error().flatMap(com.google.genai.types.JobError::message).orElse(""));
            error.put("type", "gemini_batch_error");
        }
        return response;
    }

    private JsonNode mapGeminiTuningJob(TuningJob tuningJob, JsonNode requestPayload) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "fine_tuning.job");
        putIfPresent(response, "model", text(requestPayload, "model"));
        putIfPresent(response, "training_file", text(requestPayload, "training_file"));
        putIfPresent(response, "validation_file", text(requestPayload, "validation_file"));
        response.put("created_at", epochSeconds(tuningJob.createTime().orElse(now())));
        response.put("status", geminiTuningStatus(tuningJob));
        putIfPresent(response, "fine_tuned_model", tuningJob.tunedModel().flatMap(model -> model.model()).orElse(null));
        if (tuningJob.error().flatMap(error -> error.message()).isPresent()) {
            ObjectNode error = response.putObject("error");
            error.put("message", tuningJob.error().flatMap(com.google.genai.types.GoogleRpcStatus::message).orElse(""));
            error.put("type", "gemini_tuning_error");
        }
        return response;
    }

    private void registerFineTunedModelIfReady(
            GatewayAsyncResourceEntity entity,
            UpstreamTarget target,
            ObjectNode originalMetadata,
            JsonNode syncedResponse) {
        if (fineTunedModelRegistrationService == null
                || target == null
                || target.siteProfile() == null
                || target.credential() == null
                || syncedResponse == null
                || !"succeeded".equalsIgnoreCase(syncedResponse.path("status").asText())) {
            return;
        }
        String tunedModelName = syncedResponse.path("fine_tuned_model").asText(null);
        if (tunedModelName == null || tunedModelName.isBlank()) {
            return;
        }

        JsonNode requestPayload = readObject(entity.getRequestPayloadJson());
        String aliasName = requestedTuningAlias(requestPayload, tunedModelName);
        FineTunedModelRegistrationService.RegistrationResult registration = fineTunedModelRegistrationService.register(
                target.siteProfile().getId(),
                target.credential().getProviderType(),
                text(requestPayload, "model"),
                tunedModelName,
                aliasName,
                entity.getResourceKey()
        );

        ObjectNode response = readObject(entity.getResponsePayloadJson());
        ObjectNode metadata = readObject(entity.getMetadataJson());
        if (registration.modelKey() != null) {
            metadata.put("registered_model_key", registration.modelKey());
        }
        if (registration.modelName() != null) {
            metadata.put("registered_model_name", registration.modelName());
        }
        metadata.put("registered_at", now().getEpochSecond());
        metadata.put("registered_alias_key", ModelIdNormalizer.normalize(firstNonBlank(aliasName, tunedModelName)));
        metadata.remove("registered_aliases");
        response.remove("registered_aliases");
        if (!registration.aliases().isEmpty()) {
            var aliasArray = metadata.putArray("registered_aliases");
            var responseAliasArray = response.putArray("registered_aliases");
            registration.aliases().forEach(alias -> {
                aliasArray.add(alias);
                responseAliasArray.add(alias);
            });
        }
        entity.setResponsePayloadJson(writeJson(response));
        entity.setMetadataJson(writeJson(appendEvent(metadata, "model_registered", entity.getStatus())));
        gatewayAsyncResourceRepository.save(entity);
        if (syncedResponse instanceof ObjectNode responseNode && !registration.aliases().isEmpty()) {
            var array = responseNode.putArray("registered_aliases");
            registration.aliases().forEach(array::add);
        }
    }

    private void unregisterFineTunedModelIfTerminated(
            GatewayAsyncResourceEntity entity,
            UpstreamTarget target,
            JsonNode syncedResponse) {
        if (fineTunedModelRegistrationService == null
                || entity == null
                || target == null
                || target.siteProfile() == null
                || syncedResponse == null) {
            return;
        }
        String status = syncedResponse.path("status").asText("");
        if (!"failed".equalsIgnoreCase(status) && !"cancelled".equalsIgnoreCase(status)) {
            return;
        }

        ObjectNode metadata = readObject(entity.getMetadataJson());
        String registeredModelKey = text(metadata, "registered_model_key");
        List<String> aliases = registeredAliases(metadata);
        if ((registeredModelKey == null || registeredModelKey.isBlank()) && aliases.isEmpty()) {
            return;
        }

        fineTunedModelRegistrationService.unregister(
                target.siteProfile().getId(),
                registeredModelKey,
                aliases,
                entity.getResourceKey()
        );
        metadata.remove("registered_model_key");
        metadata.remove("registered_model_name");
        metadata.remove("registered_alias_key");
        metadata.remove("registered_aliases");
        metadata.put("deregistered_at", now().getEpochSecond());
        ObjectNode response = readObject(entity.getResponsePayloadJson());
        response.remove("registered_aliases");
        entity.setResponsePayloadJson(writeJson(response));
        entity.setMetadataJson(writeJson(appendEvent(metadata, "model_unregistered", entity.getStatus())));
        gatewayAsyncResourceRepository.save(entity);
    }

    private List<String> registeredAliases(ObjectNode metadata) {
        JsonNode array = metadata.path("registered_aliases");
        if (!array.isArray()) {
            String aliasKey = text(metadata, "registered_alias_key");
            return aliasKey == null ? List.of() : List.of(aliasKey);
        }
        List<String> aliases = new ArrayList<>();
        for (JsonNode item : array) {
            String alias = item.asText(null);
            if (alias != null && !alias.isBlank()) {
                aliases.add(alias);
            }
        }
        return List.copyOf(aliases);
    }

    private String requestedTuningAlias(JsonNode requestPayload, String tunedModelName) {
        String suffix = text(requestPayload, "suffix");
        if (suffix != null && !suffix.isBlank()) {
            return suffix;
        }
        int index = tunedModelName.lastIndexOf('/');
        return index >= 0 ? tunedModelName.substring(index + 1) : tunedModelName;
    }

    private String geminiBatchStatus(BatchJob batchJob) {
        JobState.Known state = batchJob.state().map(JobState::knownEnum).orElse(null);
        if (state == null) {
            return "queued";
        }
        return switch (state) {
            case JOB_STATE_QUEUED, JOB_STATE_PENDING -> "validating";
            case JOB_STATE_RUNNING, JOB_STATE_UPDATING, JOB_STATE_CANCELLING, JOB_STATE_PAUSED -> "running";
            case JOB_STATE_SUCCEEDED, JOB_STATE_PARTIALLY_SUCCEEDED -> "completed";
            case JOB_STATE_FAILED, JOB_STATE_EXPIRED -> "failed";
            case JOB_STATE_CANCELLED -> "cancelled";
            case JOB_STATE_UNSPECIFIED -> "queued";
        };
    }

    private String geminiTuningStatus(TuningJob tuningJob) {
        JobState.Known jobState = tuningJob.state().map(JobState::knownEnum).orElse(null);
        if (jobState != null) {
            return switch (jobState) {
                case JOB_STATE_QUEUED, JOB_STATE_PENDING -> "queued";
                case JOB_STATE_RUNNING, JOB_STATE_UPDATING, JOB_STATE_CANCELLING, JOB_STATE_PAUSED -> "running";
                case JOB_STATE_SUCCEEDED, JOB_STATE_PARTIALLY_SUCCEEDED -> "succeeded";
                case JOB_STATE_FAILED, JOB_STATE_EXPIRED -> "failed";
                case JOB_STATE_CANCELLED -> "cancelled";
                case JOB_STATE_UNSPECIFIED -> "queued";
            };
        }
        TuningJobState.Known tuningState = tuningJob.tuningJobState().map(TuningJobState::knownEnum).orElse(null);
        if (tuningState == null) {
            return "queued";
        }
        return switch (tuningState) {
            case TUNING_JOB_STATE_WAITING_FOR_QUOTA, TUNING_JOB_STATE_WAITING_FOR_CAPACITY -> "queued";
            case TUNING_JOB_STATE_PROCESSING_DATASET, TUNING_JOB_STATE_TUNING, TUNING_JOB_STATE_POST_PROCESSING -> "running";
            case TUNING_JOB_STATE_UNSPECIFIED -> "queued";
        };
    }

    private List<TuningExample> parseTuningExamples(Long distributedKeyId, String trainingFileKey) {
        GatewayFileContent fileContent = getGatewayFileContent(trainingFileKey, distributedKeyId);
        String content = new String(fileContent.bytes(), StandardCharsets.UTF_8);
        List<TuningExample> examples = new ArrayList<>();
        int lineNumber = 0;
        for (String rawLine : content.split("\\r?\\n")) {
            lineNumber++;
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            JsonNode node;
            try {
                node = objectMapper.readTree(line);
            } catch (JacksonException exception) {
                throw new IllegalArgumentException("Gemini tuning 训练文件第 " + lineNumber + " 行不是有效 JSON。", exception);
            }
            String input = extractTuningInput(node);
            String output = extractTuningOutput(node);
            if (input == null || input.isBlank() || output == null || output.isBlank()) {
                throw new IllegalArgumentException("Gemini tuning 训练文件第 " + lineNumber + " 行缺少可映射的 input/output。");
            }
            examples.add(TuningExample.builder()
                    .textInput(input)
                    .output(output)
                    .build());
        }
        if (examples.isEmpty()) {
            throw new IllegalArgumentException("Gemini tuning 训练文件不能为空。");
        }
        return List.copyOf(examples);
    }

    private String extractTuningInput(JsonNode node) {
        String direct = firstText(node, "text_input", "input", "prompt");
        if (direct != null) {
            return direct;
        }
        return joinMessagesByRole(node.path("messages"), "user");
    }

    private String extractTuningOutput(JsonNode node) {
        String direct = firstText(node, "output", "completion");
        if (direct != null) {
            return direct;
        }
        return joinMessagesByRole(node.path("messages"), "assistant");
    }

    private String joinMessagesByRole(JsonNode messages, String role) {
        if (messages == null || !messages.isArray()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode message : messages) {
            if (!role.equals(text(message, "role"))) {
                continue;
            }
            String content = extractMessageContent(message.path("content"));
            if (content != null && !content.isBlank()) {
                parts.add(content);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join("\n\n", parts);
    }

    private String extractMessageContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return null;
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode part : contentNode) {
                String text = firstText(part, "text", "input_text");
                if (text != null && !text.isBlank()) {
                    parts.add(text);
                }
            }
            return parts.isEmpty() ? null : String.join("\n", parts);
        }
        return firstText(contentNode, "text", "input_text");
    }

    private String requireUpstreamObjectId(ObjectNode metadata, String message) {
        String upstreamObjectId = metadata.path("upstream_object_id").asText(null);
        if (upstreamObjectId == null || upstreamObjectId.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return upstreamObjectId;
    }

    private BatchCreateParams toAnthropicBatchCreateParams(ObjectNode payload) {
        JsonNode requests = payload.path("requests");
        if (!requests.isArray() || requests.isEmpty()) {
            throw new IllegalArgumentException("Anthropic message batches 至少需要一条 request。");
        }
        BatchCreateParams.Builder builder = BatchCreateParams.builder()
                .addBeta(AnthropicBeta.MESSAGE_BATCHES_2024_09_24);
        int index = 0;
        for (JsonNode requestNode : requests) {
            index++;
            builder.addRequest(toAnthropicBatchRequest(requestNode, index));
        }
        return builder.build();
    }

    private BatchCreateParams.Request toAnthropicBatchRequest(JsonNode requestNode, int index) {
        if (requestNode == null || !requestNode.isObject()) {
            throw new IllegalArgumentException("Anthropic message batch request 必须是 JSON object。");
        }
        JsonNode paramsNode = requestNode.path("params");
        if (!paramsNode.isObject()) {
            throw new IllegalArgumentException("Anthropic message batch request.params 必须是 JSON object。");
        }
        String customId = text(requestNode, "custom_id");
        if (customId == null || customId.isBlank()) {
            customId = "request-" + index;
        }
        return BatchCreateParams.Request.builder()
                .customId(customId)
                .params(toAnthropicMessageBatchParams((ObjectNode) paramsNode))
                .build();
    }

    private BatchCreateParams.Request.Params toAnthropicMessageBatchParams(ObjectNode paramsNode) {
        BatchCreateParams.Request.Params.Builder builder = BatchCreateParams.Request.Params.builder()
                .model(requireText(paramsNode, "model", "Anthropic message batch request.params.model 不能为空。"));
        long maxTokens = paramsNode.path("max_tokens").asLong(0L);
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("Anthropic message batch request.params.max_tokens 必须大于 0。");
        }
        builder.maxTokens(maxTokens);
        String system = extractAnthropicTextContent(paramsNode.path("system"));
        if (system != null && !system.isBlank()) {
            builder.system(system);
        }
        if (paramsNode.hasNonNull("temperature")) {
            builder.temperature(paramsNode.path("temperature").asDouble());
        }
        if (paramsNode.path("stream").isBoolean()) {
            builder.stream(paramsNode.path("stream").asBoolean(false));
        }
        JsonNode stopSequences = paramsNode.path("stop_sequences");
        if (stopSequences.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode item : stopSequences) {
                String value = item.asText(null);
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }
            if (!values.isEmpty()) {
                builder.stopSequences(values);
            }
        }
        JsonNode messages = paramsNode.path("messages");
        if (!messages.isArray() || messages.isEmpty()) {
            throw new IllegalArgumentException("Anthropic message batch request.params.messages 不能为空。");
        }
        for (JsonNode messageNode : messages) {
            builder.addMessage(toAnthropicBetaMessageParam(messageNode));
        }
        return builder.build();
    }

    private BetaMessageParam toAnthropicBetaMessageParam(JsonNode messageNode) {
        if (messageNode == null || !messageNode.isObject()) {
            throw new IllegalArgumentException("Anthropic message batch message 必须是 JSON object。");
        }
        String role = requireText(messageNode, "role", "Anthropic message batch message.role 不能为空。");
        BetaMessageParam.Builder builder = BetaMessageParam.builder()
                .role(switch (role) {
                    case "user" -> BetaMessageParam.Role.USER;
                    case "assistant" -> BetaMessageParam.Role.ASSISTANT;
                    default -> throw new IllegalArgumentException("Anthropic message batch 目前仅支持 user/assistant role。");
                });
        String content = extractAnthropicTextContent(messageNode.path("content"));
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Anthropic message batch 目前仅支持 text content。");
        }
        builder.content(content);
        return builder.build();
    }

    private String extractAnthropicTextContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return null;
        }
        if (contentNode.isTextual()) {
            String value = contentNode.asText(null);
            return value == null || value.isBlank() ? null : value;
        }
        if (contentNode.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode part : contentNode) {
                if ("text".equalsIgnoreCase(text(part, "type"))) {
                    String value = text(part, "text");
                    if (value != null && !value.isBlank()) {
                        parts.add(value);
                    }
                }
            }
            return parts.isEmpty() ? null : String.join("\n", parts);
        }
        return firstText(contentNode, "text");
    }

    private JsonNode persistAnthropicMessageBatchResource(
            Long distributedKeyId,
            ObjectNode requestPayload,
            BetaMessageBatch batch,
            UpstreamTarget target) {
        ObjectNode response = mapAnthropicMessageBatch(batch);
        String resourceKey = "batch_" + UUID.randomUUID().toString().replace("-", "");
        String upstreamObjectId = batch.id();
        String status = response.path("status").asText("created");

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("object_mode", "upstream_object_with_local_lineage");
        metadata.put("batch_protocol", "anthropic_native");
        metadata.put("upstream_object_id", upstreamObjectId);
        metadata.put("credential_id", target.credential().getId());
        metadata.put("site_profile_id", target.siteProfile().getId());
        metadata.put("upstream_status", response.path("processing_status").asText(status));
        metadata.put("upstream_synced_at", now().getEpochSecond());
        appendEvent(metadata, "created", status);

        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey(resourceKey);
        entity.setDistributedKeyId(distributedKeyId);
        entity.setResourceType(GatewayAsyncResourceType.BATCH);
        entity.setRequestModel(extractAnthropicBatchModel(requestPayload));
        entity.setStatus(status);
        entity.setUpstreamObjectId(upstreamObjectId);
        entity.setRequestPayloadJson(writeJson(requestPayload));
        entity.setResponsePayloadJson(writeJson(response));
        entity.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    private JsonNode syncAnthropicMessageBatchResource(
            GatewayAsyncResourceEntity entity,
            BetaMessageBatch batch) {
        ObjectNode response = mapAnthropicMessageBatch(batch);
        String status = response.path("status").asText(entity.getStatus());
        entity.setStatus(status);
        entity.setUpstreamObjectId(batch.id());
        entity.setResponsePayloadJson(writeJson(response));
        ObjectNode metadata = readObject(entity.getMetadataJson());
        metadata.put("upstream_object_id", batch.id());
        metadata.put("upstream_status", response.path("processing_status").asText(status));
        metadata.put("upstream_synced_at", now().getEpochSecond());
        entity.setMetadataJson(writeJson(appendEvent(metadata, "synced", status)));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    private ObjectNode mapAnthropicMessageBatch(BetaMessageBatch batch) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", batch.id());
        response.put("object", "message_batch");
        String processingStatus = batch.processingStatus().toString().toLowerCase(java.util.Locale.ROOT);
        response.put("processing_status", processingStatus);
        response.put("status", normalizeAnthropicMessageBatchStatus(processingStatus, batch));
        response.put("created_at", batch.createdAt().toInstant().getEpochSecond());
        response.put("expires_at", batch.expiresAt().toInstant().getEpochSecond());
        batch.endedAt().ifPresent(value -> response.put("ended_at", value.toInstant().getEpochSecond()));
        batch.cancelInitiatedAt().ifPresent(value -> response.put("cancel_initiated_at", value.toInstant().getEpochSecond()));
        batch.archivedAt().ifPresent(value -> response.put("archived_at", value.toInstant().getEpochSecond()));
        batch.resultsUrl().ifPresent(value -> response.put("results_url", value));
        ObjectNode requestCounts = response.putObject("request_counts");
        requestCounts.put("processing", batch.requestCounts().processing());
        requestCounts.put("succeeded", batch.requestCounts().succeeded());
        requestCounts.put("errored", batch.requestCounts().errored());
        requestCounts.put("canceled", batch.requestCounts().canceled());
        requestCounts.put("expired", batch.requestCounts().expired());
        return response;
    }

    private String normalizeAnthropicMessageBatchStatus(String processingStatus, BetaMessageBatch batch) {
        if (processingStatus == null || processingStatus.isBlank()) {
            return "queued";
        }
        return switch (processingStatus) {
            case "in_progress" -> "running";
            case "canceling" -> "cancelling";
            case "ended" -> batch.requestCounts().succeeded() > 0 ? "completed"
                    : batch.requestCounts().errored() > 0 ? "failed" : "completed";
            case "canceled" -> "cancelled";
            case "archived" -> "completed";
            default -> processingStatus;
        };
    }

    private String extractAnthropicBatchModel(ObjectNode requestPayload) {
        JsonNode requests = requestPayload.path("requests");
        if (!requests.isArray() || requests.isEmpty()) {
            return null;
        }
        return text(requests.get(0).path("params"), "model");
    }

    private boolean isAnthropicNativeBatch(
            ObjectNode metadata,
            GatewayAsyncResourceType resourceType) {
        return resourceType == GatewayAsyncResourceType.BATCH
                && "anthropic_native".equalsIgnoreCase(text(metadata, "batch_protocol"));
    }

    private boolean isOpenAiBatchListCandidate(GatewayAsyncResourceEntity entity) {
        if (entity == null
                || entity.isDeleted()
                || entity.getResourceType() != GatewayAsyncResourceType.BATCH
                || entity.getResourceKey() == null
                || !entity.getResourceKey().startsWith("batch_")) {
            return false;
        }
        ObjectNode metadata = readObject(entity.getMetadataJson());
        if (isAnthropicNativeBatch(metadata, GatewayAsyncResourceType.BATCH)) {
            return false;
        }
        JsonNode response = readJson(entity.getResponsePayloadJson());
        String objectName = response.path("object").asText(null);
        return objectName == null || objectName.isBlank() || "batch".equals(objectName);
    }

    private List<JsonNode> tuningEventItems(GatewayAsyncResourceEntity entity, ObjectNode metadata) {
        if (metadata == null || !metadata.path("events").isArray()) {
            return List.of();
        }
        List<JsonNode> events = new ArrayList<>();
        int index = 0;
        for (JsonNode item : metadata.path("events")) {
            ObjectNode event = objectMapper.createObjectNode();
            String id = firstText(item, "id", "event_id");
            event.put("id", id == null ? "ftevent_" + stableIdSegment(entity.getResourceKey()) + "_" + index : id);
            event.put("object", "fine_tuning.job.event");
            event.put("created_at", tuningItemEpoch(item, entity));
            event.put("level", tuningEventLevel(item));
            event.put("message", tuningEventMessage(item));
            if (item != null && item.has("data")) {
                event.set("data", item.path("data").deepCopy());
            } else {
                event.putNull("data");
            }
            event.put("type", tuningEventType(item));
            events.add(event);
            index++;
        }
        return List.copyOf(events);
    }

    private String tuningEventLevel(JsonNode item) {
        String level = text(item, "level");
        if ("info".equals(level) || "warn".equals(level) || "error".equals(level)) {
            return level;
        }
        String status = firstText(item, "status", "state");
        String type = text(item, "type");
        if (containsAny(status, "failed", "error", "errored") || containsAny(type, "failed", "error")) {
            return "error";
        }
        if (containsAny(status, "cancelled", "canceled", "warning") || containsAny(type, "warn")) {
            return "warn";
        }
        return "info";
    }

    private String tuningEventMessage(JsonNode item) {
        String message = text(item, "message");
        if (message != null) {
            return message;
        }
        String status = firstText(item, "status", "state");
        if (status != null) {
            return "Fine-tuning job status changed to " + status + ".";
        }
        String type = text(item, "type");
        return type == null ? "Fine-tuning job event." : "Fine-tuning job event: " + type + ".";
    }

    private String tuningEventType(JsonNode item) {
        String type = text(item, "type");
        if ("metrics".equals(type)) {
            return "metrics";
        }
        if (item != null && (item.has("metrics") || item.path("data").has("metrics"))) {
            return "metrics";
        }
        return "message";
    }

    private List<JsonNode> tuningCheckpointItems(
            GatewayAsyncResourceEntity entity,
            ObjectNode metadata,
            ObjectNode response) {
        List<JsonNode> checkpoints = new ArrayList<>();
        if (metadata != null && metadata.path("checkpoints").isArray()) {
            int index = 0;
            for (JsonNode item : metadata.path("checkpoints")) {
                checkpoints.add(tuningCheckpointItem(entity, item, metadata, response, index++));
            }
            return List.copyOf(checkpoints);
        }

        String checkpointModel = firstText(
                response,
                "fine_tuned_model_checkpoint",
                "fine_tuned_model",
                "registered_model_name"
        );
        if (checkpointModel == null) {
            checkpointModel = firstText(metadata, "registered_model_name", "registered_model_key");
        }
        if (checkpointModel == null || !hasCompletedTuningCheckpointEvidence(entity, metadata, response)) {
            return List.of();
        }

        ObjectNode synthetic = objectMapper.createObjectNode();
        synthetic.put("fine_tuned_model_checkpoint", checkpointModel);
        synthetic.put("step_number", firstAvailableStepNumber(metadata, response, null, 0));
        checkpoints.add(tuningCheckpointItem(entity, synthetic, metadata, response, 0));
        return List.copyOf(checkpoints);
    }

    private JsonNode tuningCheckpointItem(
            GatewayAsyncResourceEntity entity,
            JsonNode source,
            ObjectNode metadata,
            ObjectNode response,
            int index) {
        ObjectNode checkpoint = source != null && source.isObject()
                ? copyObject(source)
                : objectMapper.createObjectNode();
        String checkpointModel = source != null && source.isTextual() ? source.asText(null) : firstText(
                checkpoint,
                "fine_tuned_model_checkpoint",
                "fine_tuned_model"
        );
        if (checkpointModel == null) {
            checkpointModel = firstText(response, "fine_tuned_model_checkpoint", "fine_tuned_model");
        }
        if (checkpointModel == null) {
            checkpointModel = firstText(metadata, "registered_model_name", "registered_model_key");
        }
        int stepNumber = firstAvailableStepNumber(checkpoint, metadata, response, index);
        if (!checkpoint.has("id") || checkpoint.path("id").asText().isBlank()) {
            checkpoint.put("id", "ftckpt_" + stableIdSegment(entity.getResourceKey()) + "_" + stepNumber);
        }
        checkpoint.put("object", "fine_tuning.job.checkpoint");
        if (!checkpoint.has("created_at")) {
            checkpoint.put("created_at", tuningItemEpoch(checkpoint, entity));
        }
        if (checkpointModel != null) {
            checkpoint.put("fine_tuned_model_checkpoint", checkpointModel);
        }
        checkpoint.put("fine_tuning_job_id", entity.getResourceKey());
        if (!checkpoint.path("metrics").isObject()) {
            if (response != null && response.path("metrics").isObject()) {
                checkpoint.set("metrics", response.path("metrics").deepCopy());
            } else if (metadata != null && metadata.path("metrics").isObject()) {
                checkpoint.set("metrics", metadata.path("metrics").deepCopy());
            } else {
                checkpoint.set("metrics", objectMapper.createObjectNode());
            }
        }
        checkpoint.put("step_number", stepNumber);
        return checkpoint;
    }

    private boolean hasCompletedTuningCheckpointEvidence(
            GatewayAsyncResourceEntity entity,
            ObjectNode metadata,
            ObjectNode response) {
        if (firstText(metadata, "registered_model_name", "registered_model_key") != null) {
            return true;
        }
        if (firstText(response, "fine_tuned_model", "fine_tuned_model_checkpoint") != null) {
            return true;
        }
        String status = response == null ? null : text(response, "status");
        if (status == null && entity != null) {
            status = entity.getStatus();
        }
        return containsAny(status, "succeeded", "completed", "success", "done");
    }

    private int firstAvailableStepNumber(JsonNode first, JsonNode second, JsonNode third, int fallback) {
        for (JsonNode node : new JsonNode[]{first, second, third}) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                continue;
            }
            JsonNode step = node.path("step_number");
            if (step.isNumber()) {
                return step.asInt();
            }
            step = node.path("step");
            if (step.isNumber()) {
                return step.asInt();
            }
            step = node.path("metrics").path("step");
            if (step.isNumber()) {
                return step.asInt();
            }
        }
        return Math.max(0, fallback);
    }

    private long tuningItemEpoch(JsonNode node, GatewayAsyncResourceEntity entity) {
        if (node != null) {
            JsonNode createdAt = node.path("created_at");
            if (!createdAt.isMissingNode() && !createdAt.isNull()) {
                return createdAt.asLong(epochSeconds(entity == null ? null : entity.getCreatedAt()));
            }
            JsonNode created = node.path("created");
            if (!created.isMissingNode() && !created.isNull()) {
                return created.asLong(epochSeconds(entity == null ? null : entity.getCreatedAt()));
            }
            JsonNode at = node.path("at");
            if (!at.isMissingNode() && !at.isNull()) {
                return at.asLong(epochSeconds(entity == null ? null : entity.getCreatedAt()));
            }
        }
        if (entity != null && entity.getUpdatedAt() != null) {
            return epochSeconds(entity.getUpdatedAt());
        }
        return epochSeconds(entity == null ? null : entity.getCreatedAt());
    }

    private boolean containsAny(String value, String... needles) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (normalized.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String stableIdSegment(String value) {
        if (value == null || value.isBlank()) {
            return "local";
        }
        String normalized = value.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            return "local";
        }
        return normalized.length() <= 48 ? normalized : normalized.substring(0, 48);
    }

    private String requireText(JsonNode payload, String fieldName, String message) {
        String value = text(payload, fieldName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private long epochSeconds(Instant instant) {
        return (instant == null ? now() : instant).getEpochSecond();
    }

    private void putIfPresent(ObjectNode node, String fieldName, String value) {
        if (value != null && !value.isBlank()) {
            node.put(fieldName, value);
        }
    }

    private JsonNode persistUpstreamBackedResource(
            Long distributedKeyId,
            GatewayAsyncResourceType type,
            String idPrefix,
            JsonNode requestPayload,
            JsonNode upstreamResponse,
            String objectName,
            UpstreamTarget target) {
        return persistUpstreamBackedResource(
                distributedKeyId,
                type,
                idPrefix,
                requestPayload,
                upstreamResponse,
                objectName,
                target,
                upstreamResponse.path("id").asText(null)
        );
    }

    private JsonNode persistUpstreamBackedResource(
            Long distributedKeyId,
            GatewayAsyncResourceType type,
            String idPrefix,
            JsonNode requestPayload,
            JsonNode upstreamResponse,
            String objectName,
            UpstreamTarget target,
            String upstreamObjectId) {
        String resourceKey = idPrefix + UUID.randomUUID().toString().replace("-", "");
        ObjectNode response = copyObject(upstreamResponse);
        response.put("id", resourceKey);
        if (!response.has("object")) {
            response.put("object", objectName);
        }
        String status = response.path("status").asText("created");

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("object_mode", "upstream_object_with_local_lineage");
        if (upstreamObjectId != null && !upstreamObjectId.isBlank()) {
            metadata.put("upstream_object_id", upstreamObjectId);
        }
        metadata.put("credential_id", target.credential().getId());
        metadata.put("site_profile_id", target.siteProfile().getId());
        metadata.put("upstream_status", upstreamResponse.path("status").asText(status));
        metadata.put("upstream_synced_at", now().getEpochSecond());
        enrichMediaProviderMetadata(metadata, type, target.siteProfile());
        appendEvent(metadata, "created", status);

        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey(resourceKey);
        entity.setDistributedKeyId(distributedKeyId);
        entity.setResourceType(type);
        entity.setRequestModel(requestPayload.path("model").asText(null));
        entity.setStatus(status);
        entity.setUpstreamObjectId(upstreamObjectId);
        entity.setRequestPayloadJson(writeJson(requestPayload));
        entity.setResponsePayloadJson(writeJson(response));
        entity.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    private JsonNode createLocalMediaTask(
            Long distributedKeyId,
            JsonNode requestBody,
            GatewayAsyncResourceType type,
            String idPrefix,
            String objectName,
            String taskKind) {
        ObjectNode payload = copyObject(requireObject(requestBody));
        String resourceKey = idPrefix + UUID.randomUUID().toString().replace("-", "");
        String status = text(payload, "status");
        status = status == null ? "queued" : status.trim().toLowerCase(Locale.ROOT);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", resourceKey);
        response.put("object", objectName);
        response.put("status", status);
        response.put("created", now().getEpochSecond());
        response.put("task_kind", taskKind);
        putIfPresent(response, "model", text(payload, "model"));
        if (payload.has("metadata")) {
            response.set("metadata", payload.path("metadata").deepCopy());
        }

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("object_mode", "gateway_local_async_task");
        metadata.put("task_kind", taskKind);
        metadata.put("provider_mode", "local_contract");
        metadata.put("provider_support_tier", "local_contract");
        metadata.put("provider_support_status", "LOCAL_ONLY");
        metadata.put("provider_smoke_hint", "本地 contract smoke，不访问真实 provider。");
        appendEvent(metadata, "created", status);

        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey(resourceKey);
        entity.setDistributedKeyId(distributedKeyId);
        entity.setResourceType(type);
        entity.setRequestModel(text(payload, "model"));
        entity.setStatus(status);
        entity.setRequestPayloadJson(writeJson(payload));
        entity.setResponsePayloadJson(writeJson(response));
        entity.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    private JsonNode createMediaTask(
            Long distributedKeyId,
            JsonNode requestBody,
            GatewayAsyncResourceType type,
            String idPrefix,
            String objectName,
            String taskKind,
            InteropFeature feature) {
        ObjectNode sourcePayload = copyObject(requireObject(requestBody));
        Optional<MediaProviderAdapter> adapter = mediaProviderAdapterFor(type, sourcePayload);
        if (adapter.isPresent()) {
            return createProviderSpecificMediaTask(distributedKeyId, sourcePayload, type, idPrefix, adapter.get());
        }
        if (!useUpstreamMediaProvider(sourcePayload)) {
            return createLocalMediaTask(distributedKeyId, sourcePayload, type, idPrefix, objectName, taskKind);
        }
        Long preferredCredentialId = sourcePayload.hasNonNull("preferred_credential_id")
                ? sourcePayload.path("preferred_credential_id").asLong()
                : null;
        sourcePayload.remove(List.of("provider_mode", "preferred_credential_id"));
        UpstreamTarget target = resolveUpstreamTarget(distributedKeyId, feature, preferredCredentialId);
        JsonNode upstreamResponse = invokeUpstreamJson(target, basePath(feature), sourcePayload);
        return persistUpstreamBackedResource(distributedKeyId, type, idPrefix, sourcePayload, upstreamResponse, objectName, target);
    }

    private boolean useUpstreamMediaProvider(ObjectNode payload) {
        String providerMode = text(payload, "provider_mode");
        return providerMode != null && ("upstream".equalsIgnoreCase(providerMode) || "provider".equalsIgnoreCase(providerMode))
                || payload.hasNonNull("preferred_credential_id");
    }

    private JsonNode createProviderSpecificMediaTask(
            Long distributedKeyId,
            ObjectNode requestPayload,
            GatewayAsyncResourceType type,
            String idPrefix,
            MediaProviderAdapter adapter) {
        String resourceKey = idPrefix + UUID.randomUUID().toString().replace("-", "");
        MediaProviderCreateResult result = adapter.create(resourceKey, distributedKeyId, requestPayload, now());

        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey(resourceKey);
        entity.setDistributedKeyId(distributedKeyId);
        entity.setResourceType(type);
        entity.setRequestModel(text(requestPayload, "model"));
        entity.setStatus(result.status());
        entity.setUpstreamObjectId(result.providerTaskId());
        entity.setRequestPayloadJson(writeJson(requestPayload));
        entity.setResponsePayloadJson(writeJson(result.response()));
        entity.setMetadataJson(writeJson(result.metadata()));
        gatewayAsyncResourceRepository.save(entity);
        return result.response();
    }

    private JsonNode syncProviderSpecificMediaResource(GatewayAsyncResourceEntity entity, ObjectNode metadata) {
        MediaProviderAdapter adapter = mediaProviderAdapterForMetadata(entity.getResourceType(), metadata);
        ObjectNode response = adapter.get(entity, metadata, readObject(entity.getResponsePayloadJson()), now());
        String status = response.path("status").asText(entity.getStatus());
        entity.setStatus(status);
        entity.setResponsePayloadJson(writeJson(response));
        entity.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    private JsonNode cancelProviderSpecificMediaResource(GatewayAsyncResourceEntity entity, ObjectNode metadata) {
        MediaProviderAdapter adapter = mediaProviderAdapterForMetadata(entity.getResourceType(), metadata);
        ObjectNode response = adapter.cancel(entity, metadata, readObject(entity.getResponsePayloadJson()), now());
        String status = response.path("status").asText(entity.getStatus());
        entity.setStatus(status);
        entity.setResponsePayloadJson(writeJson(response));
        entity.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    private JsonNode downloadMediaTaskArtifact(
            String resourceKey,
            Long distributedKeyId,
            GatewayAsyncResourceType type) {
        GatewayAsyncResourceEntity entity = getRequired(resourceKey, type, distributedKeyId);
        ObjectNode metadata = readObject(entity.getMetadataJson());
        ObjectNode response = readObject(entity.getResponsePayloadJson());
        ObjectNode download;
        if (isProviderSpecificMediaAdapter(metadata, type)) {
            download = mediaProviderAdapterForMetadata(type, metadata).download(entity, metadata, response, now());
        } else {
            String outputUrl = firstText(response, "output_url", "download_url", "url");
            if (outputUrl == null || outputUrl.isBlank()) {
                throw new IllegalStateException("当前媒体任务没有可下载产物。");
            }
            download = objectMapper.createObjectNode();
            download.put("id", entity.getResourceKey() + "_download");
            download.put("object", "media.artifact_download");
            download.put("resource_id", entity.getResourceKey());
            download.put("download_url", outputUrl);
            download.put("status", entity.getStatus());
            appendEvent(metadata, "downloaded", entity.getStatus());
        }
        entity.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(entity);
        return download;
    }

    private Optional<MediaProviderAdapter> mediaProviderAdapterFor(GatewayAsyncResourceType type, ObjectNode requestPayload) {
        return mediaProviderAdapters.stream()
                .filter(adapter -> adapter.supports(type, requestPayload))
                .findFirst();
    }

    private boolean isProviderSpecificMediaAdapter(ObjectNode metadata, GatewayAsyncResourceType type) {
        return (type == GatewayAsyncResourceType.VIDEO || type == GatewayAsyncResourceType.MUSIC)
                && "provider_specific_media_adapter".equalsIgnoreCase(text(metadata, "object_mode"));
    }

    private MediaProviderAdapter mediaProviderAdapterForMetadata(GatewayAsyncResourceType type, ObjectNode metadata) {
        String adapterName = text(metadata, "provider_adapter");
        String providerFamily = text(metadata, "provider_family");
        return mediaProviderAdapters.stream()
                .filter(adapter -> adapter.resourceType() == type)
                .filter(adapter -> adapter.adapterName().equalsIgnoreCase(defaultString(adapterName, ""))
                        || adapter.providerFamily().equalsIgnoreCase(defaultString(providerFamily, "")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到媒体 provider adapter：" + defaultString(adapterName, providerFamily)));
    }

    private ObjectNode mediaProviderMatrixItem(
            String providerFamily,
            String displayName,
            String supportStatus,
            String supportTier,
            String nativePath,
            String note) {
        return mediaProviderMatrixItem(providerFamily, displayName, supportStatus, supportTier, nativePath, note, null, null, null);
    }

    private ObjectNode mediaProviderMatrixItem(
            String providerFamily,
            String displayName,
            String supportStatus,
            String supportTier,
            String nativePath,
            String note,
            String capability,
            String pricingSource,
            String smokeHint) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("provider_family", providerFamily);
        item.put("display_name", displayName);
        item.put("support_status", supportStatus);
        item.put("support_tier", supportTier);
        item.put("native_path", nativePath);
        item.put("note", note);
        putIfPresent(item, "capability", capability);
        putIfPresent(item, "pricing_source", pricingSource);
        putIfPresent(item, "smoke_hint", smokeHint);
        return item;
    }

    private void enrichMediaProviderMetadata(
            ObjectNode metadata,
            GatewayAsyncResourceType type,
            UpstreamSiteProfileEntity siteProfile) {
        if (type != GatewayAsyncResourceType.VIDEO && type != GatewayAsyncResourceType.MUSIC) {
            return;
        }
        UpstreamSiteKind siteKind = siteProfile.getSiteKind();
        metadata.put("provider_family", mediaProviderFamily(siteKind));
        metadata.put("site_kind", siteKind == null ? "UNKNOWN" : siteKind.name());
        metadata.put("provider_support_tier", mediaSupportTier(type, siteKind));
        metadata.put("provider_support_status", mediaSupportStatus(type, siteKind));
        metadata.put("provider_smoke_hint", mediaSmokeHint(type, siteKind));
    }

    private String mediaProviderFamily(UpstreamSiteKind siteKind) {
        if (siteKind == null) {
            return "unknown";
        }
        return switch (siteKind) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE_GENERIC, AZURE_OPENAI -> "openai_compatible";
            case MINIMAX -> "minimax";
            case GEMINI_DIRECT, VERTEX_AI -> "gemini";
            case ANTHROPIC_DIRECT -> "anthropic";
            case OLLAMA_DIRECT -> "ollama";
            default -> siteKind.name().toLowerCase(Locale.ROOT);
        };
    }

    private String mediaSupportTier(GatewayAsyncResourceType type, UpstreamSiteKind siteKind) {
        if (siteKind == UpstreamSiteKind.OPENAI_DIRECT
                || siteKind == UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC
                || siteKind == UpstreamSiteKind.AZURE_OPENAI) {
            return "native_openai_style";
        }
        if (siteKind == UpstreamSiteKind.GEMINI_DIRECT || siteKind == UpstreamSiteKind.VERTEX_AI) {
            return type == GatewayAsyncResourceType.VIDEO
                    ? "provider_specific_adapter_required"
                    : "provider_capability_absent";
        }
        return "provider_specific_adapter_required";
    }

    private String mediaSupportStatus(GatewayAsyncResourceType type, UpstreamSiteKind siteKind) {
        String tier = mediaSupportTier(type, siteKind);
        return switch (tier) {
            case "native_openai_style" -> "SUPPORTED";
            case "provider_capability_absent" -> "NOT_SUPPORTED";
            default -> "ADAPTER_REQUIRED";
        };
    }

    private String mediaSmokeHint(GatewayAsyncResourceType type, UpstreamSiteKind siteKind) {
        if ("SUPPORTED".equals(mediaSupportStatus(type, siteKind))) {
            return "使用 provider_mode=upstream 或 preferred_credential_id 执行 create/get/cancel smoke。";
        }
        if (type == GatewayAsyncResourceType.VIDEO && (siteKind == UpstreamSiteKind.GEMINI_DIRECT || siteKind == UpstreamSiteKind.VERTEX_AI)) {
            return "需要先接入 Gemini/Veo 专有 adapter，再执行真实 Video smoke。";
        }
        return "需要 provider-specific adapter 或外部 async bridge。";
    }

    private JsonNode getLocalMediaTask(
            String resourceKey,
            Long distributedKeyId,
            GatewayAsyncResourceType type) {
        GatewayAsyncResourceEntity entity = getRequired(resourceKey, type, distributedKeyId);
        return readJson(entity.getResponsePayloadJson());
    }

    private JsonNode cancelLocalMediaTask(
            String resourceKey,
            Long distributedKeyId,
            GatewayAsyncResourceType type) {
        GatewayAsyncResourceEntity entity = getRequired(resourceKey, type, distributedKeyId);
        ObjectNode response = readObject(entity.getResponsePayloadJson());
        if (!isTerminalStatus(entity.getStatus())) {
            entity.setStatus("cancelled");
            response.put("status", "cancelled");
            response.put("cancelled_at", now().getEpochSecond());
            ObjectNode metadata = readObject(entity.getMetadataJson());
            metadata.put("cancel_reason", "user_cancelled");
            appendEvent(metadata, "cancelled", "cancelled");
            entity.setMetadataJson(writeJson(metadata));
            entity.setResponsePayloadJson(writeJson(response));
            gatewayAsyncResourceRepository.save(entity);
        }
        return response;
    }

    private JsonNode syncPersistedResource(
            GatewayAsyncResourceEntity entity,
            JsonNode upstreamResponse,
            String objectName) {
        ObjectNode response = copyObject(upstreamResponse);
        response.put("id", entity.getResourceKey());
        if (!response.has("object")) {
            response.put("object", objectName);
        }
        String status = response.path("status").asText(entity.getStatus());
        entity.setStatus(status);
        entity.setResponsePayloadJson(writeJson(response));
        ObjectNode metadata = readObject(entity.getMetadataJson());
        String upstreamObjectId = metadata.path("upstream_object_id").asText(entity.getUpstreamObjectId());
        if (upstreamObjectId != null && !upstreamObjectId.isBlank()) {
            entity.setUpstreamObjectId(upstreamObjectId);
        }
        metadata.put("upstream_status", upstreamResponse.path("status").asText(status));
        metadata.put("upstream_synced_at", now().getEpochSecond());
        entity.setMetadataJson(writeJson(appendEvent(metadata, "synced", status)));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    private JsonNode persistUploadPart(
            GatewayAsyncResourceEntity entity,
            String uploadId,
            String filename,
            JsonNode upstreamResponse) {
        ObjectNode response = copyObject(upstreamResponse);
        if (!response.has("object")) {
            response.put("object", "upload.part");
        }
        response.put("upload_id", uploadId);
        String upstreamPartId = response.path("id").asText("part_" + UUID.randomUUID().toString().replace("-", ""));

        ObjectNode metadata = readObject(entity.getMetadataJson());
        metadata.withArray("parts").add(upstreamPartId);
        metadata.withArray("part_bindings").addObject()
                .put("upstream_part_id", upstreamPartId)
                .put("filename", filename)
                .put("synced_at", now().getEpochSecond());
        metadata.put("partsCount", metadata.withArray("parts").size());
        metadata.put("upstream_synced_at", now().getEpochSecond());
        entity.setMetadataJson(writeJson(appendEvent(metadata, "part_added", entity.getStatus())));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    private GoogleNativeBatchView toGoogleNativeBatchView(GatewayAsyncResourceEntity entity) {
        return new GoogleNativeBatchView(
                entity,
                readObject(entity.getResponsePayloadJson()),
                readObject(entity.getMetadataJson())
        );
    }

    private JsonNode addLocalUploadPart(GatewayAsyncResourceEntity entity) {
        return addLocalUploadPart(entity, null, null, new byte[0], null);
    }

    private JsonNode addLocalUploadPart(GatewayAsyncResourceEntity entity, String filename) {
        return addLocalUploadPart(entity, filename, null, new byte[0], null);
    }

    private JsonNode addLocalUploadPart(
            GatewayAsyncResourceEntity entity,
            String filename,
            String mimeType,
            byte[] bytes,
            String sourceGatewayFileKey) {
        assertUploadWritable(entity, "追加 part");
        ObjectNode metadata = readObject(entity.getMetadataJson());
        String partId = "part_" + UUID.randomUUID().toString().replace("-", "");
        Path storagePath = persistLocalUploadPartFile(entity.getResourceKey(), partId, filename, bytes);
        long bytesLength = bytes == null ? 0L : bytes.length;
        metadata.withArray("parts").add(partId);
        metadata.put("partsCount", metadata.withArray("parts").size());
        metadata.put("bytesReceived", metadata.path("bytesReceived").asLong(0L) + bytesLength);
        metadata.withArray("part_bindings").addObject()
                .put("part_id", partId)
                .put("filename", firstNonBlank(filename, "upload.bin"))
                .put("mime_type", firstNonBlank(mimeType, "application/octet-stream"))
                .put("size_bytes", bytesLength)
                .put("storage_path", storagePath.toAbsolutePath().toString())
                .put("sha256", sha256Hex(bytes == null ? new byte[0] : bytes))
                .put("source_gateway_file_key", sourceGatewayFileKey)
                .put("part_order", metadata.path("partsCount").asInt())
                .put("synced_at", now().getEpochSecond());
        ObjectNode uploadResponse = readObject(entity.getResponsePayloadJson());
        uploadResponse.put("status", "in_progress");
        uploadResponse.put("parts_count", metadata.path("partsCount").asInt());
        uploadResponse.put("bytes_received", metadata.path("bytesReceived").asLong());
        entity.setStatus("in_progress");
        entity.setResponsePayloadJson(writeJson(uploadResponse));
        entity.setMetadataJson(writeJson(appendEvent(metadata, "part_added", entity.getStatus())));
        gatewayAsyncResourceRepository.save(entity);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", partId);
        response.put("object", "upload.part");
        response.put("created_at", now().getEpochSecond());
        response.put("upload_id", entity.getResourceKey());
        response.put("bytes", bytesLength);
        if (filename != null && !filename.isBlank()) {
            response.put("filename", filename);
        }
        if (mimeType != null && !mimeType.isBlank()) {
            response.put("mime_type", mimeType);
        }
        return response;
    }

    private JsonNode updateLocalStatus(
            String resourceKey,
            Long distributedKeyId,
            GatewayAsyncResourceType type,
            String status) {
        GatewayAsyncResourceEntity entity = getRequired(resourceKey, type, distributedKeyId);
        entity.setStatus(status);
        ObjectNode response = readObject(entity.getResponsePayloadJson());
        response.put("status", status);
        entity.setResponsePayloadJson(writeJson(response));
        entity.setMetadataJson(writeJson(appendEvent(readObject(entity.getMetadataJson()), "status_changed", status)));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    private JsonNode completeLocalUpload(GatewayAsyncResourceEntity entity, Long distributedKeyId) {
        if ("completed".equalsIgnoreCase(entity.getStatus())) {
            return readJson(entity.getResponsePayloadJson());
        }
        assertUploadWritable(entity, "完成 Upload");
        ObjectNode metadata = readObject(entity.getMetadataJson());
        List<LocalUploadPart> parts = localUploadParts(metadata);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("Upload 尚未添加任何 part。");
        }

        byte[] merged = mergeUploadParts(parts);
        long declaredBytes = metadata.path("declaredBytes").asLong(0L);
        if (declaredBytes > 0 && declaredBytes != merged.length) {
            throw new IllegalArgumentException("Upload part 字节总量与声明 bytes 不一致。");
        }

        Long preferredCredentialId = metadata.path("credential_id").isNumber() ? metadata.path("credential_id").asLong() : null;
        ObjectNode currentResponse = readObject(entity.getResponsePayloadJson());
        String filename = firstNonBlank(text(metadata, "filename"), text(currentResponse, "filename"), "upload.bin");
        String mimeType = inferUploadMimeType(parts, currentResponse);
        String purpose = firstNonBlank(text(metadata, "purpose"), text(currentResponse, "purpose"), null);
        GatewayFileResponse gatewayFile = createUploadOutputFile(distributedKeyId, filename, mimeType, purpose, merged, preferredCredentialId);

        metadata.put("produced_file_key", gatewayFile.id());
        metadata.put("produced_file_bytes", gatewayFile.bytes());
        metadata.put("produced_file_status", gatewayFile.status());
        metadata.put("completed_at", now().getEpochSecond());
        cleanupUploadPartFiles(metadata);
        metadata.put("part_files_cleaned", true);
        currentResponse.put("status", "completed");
        currentResponse.put("parts_count", metadata.path("partsCount").asInt());
        currentResponse.put("bytes_received", merged.length);
        currentResponse.put("file_id", gatewayFile.id());
        currentResponse.put("completed_at", now().getEpochSecond());
        entity.setStatus("completed");
        entity.setResponsePayloadJson(writeJson(currentResponse));
        entity.setMetadataJson(writeJson(appendEvent(metadata, "status_changed", "completed")));
        gatewayAsyncResourceRepository.save(entity);
        return currentResponse;
    }

    private JsonNode cancelLocalUpload(GatewayAsyncResourceEntity entity) {
        if ("cancelled".equalsIgnoreCase(entity.getStatus())) {
            return readJson(entity.getResponsePayloadJson());
        }
        if ("completed".equalsIgnoreCase(entity.getStatus())) {
            throw new IllegalArgumentException("已完成的 Upload 不允许取消。");
        }
        ObjectNode metadata = readObject(entity.getMetadataJson());
        cleanupUploadPartFiles(metadata);
        metadata.put("part_files_cleaned", true);
        metadata.put("cancelled_at", now().getEpochSecond());
        ObjectNode response = readObject(entity.getResponsePayloadJson());
        response.put("status", "cancelled");
        entity.setStatus("cancelled");
        entity.setResponsePayloadJson(writeJson(response));
        entity.setMetadataJson(writeJson(appendEvent(metadata, "status_changed", "cancelled")));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    private JsonNode persistLocalUploadResource(
            Long distributedKeyId,
            ObjectNode requestPayload,
            UpstreamTarget target) {
        String resourceKey = "upload_" + UUID.randomUUID().toString().replace("-", "");
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", resourceKey);
        response.put("object", "upload");
        response.put("status", "created");
        response.put("created_at", now().getEpochSecond());
        copyIfPresent(requestPayload, response, "filename");
        copyIfPresent(requestPayload, response, "purpose");
        copyIfPresent(requestPayload, response, "bytes");
        copyIfPresent(requestPayload, response, "mime_type");

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("object_mode", "gateway_upload_object");
        metadata.put("credential_id", target.credential().getId());
        metadata.put("site_profile_id", target.siteProfile().getId());
        copyIfPresent(requestPayload, metadata, "filename");
        copyIfPresent(requestPayload, metadata, "purpose");
        copyIfPresent(requestPayload, metadata, "mime_type");
        metadata.put("declaredBytes", requestPayload.path("bytes").asLong(0L));
        metadata.put("bytesReceived", 0L);
        metadata.putArray("parts");
        metadata.putArray("part_bindings");
        metadata.put("partsCount", 0);
        appendEvent(metadata, "created", "created");

        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey(resourceKey);
        entity.setDistributedKeyId(distributedKeyId);
        entity.setResourceType(GatewayAsyncResourceType.UPLOAD);
        entity.setStatus("created");
        entity.setRequestPayloadJson(writeJson(requestPayload));
        entity.setResponsePayloadJson(writeJson(response));
        entity.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    private void assertUploadWritable(GatewayAsyncResourceEntity entity, String action) {
        if ("cancelled".equalsIgnoreCase(entity.getStatus())) {
            throw new IllegalArgumentException("已取消的 Upload 不允许继续" + action + "。");
        }
        if ("completed".equalsIgnoreCase(entity.getStatus())) {
            throw new IllegalArgumentException("已完成的 Upload 不允许继续" + action + "。");
        }
    }

    private Optional<JsonNode> terminalUploadResponse(GatewayAsyncResourceEntity entity, String suffix) {
        boolean cancelAction = suffix != null && suffix.contains("cancel");
        boolean completeAction = suffix != null && suffix.contains("complete");
        if (cancelAction && "cancelled".equalsIgnoreCase(entity.getStatus())) {
            return Optional.of(readJson(entity.getResponsePayloadJson()));
        }
        if (completeAction && "completed".equalsIgnoreCase(entity.getStatus())) {
            return Optional.of(readJson(entity.getResponsePayloadJson()));
        }
        if (cancelAction && "completed".equalsIgnoreCase(entity.getStatus())) {
            throw new IllegalArgumentException("已完成的 Upload 不允许取消。");
        }
        if (completeAction && "cancelled".equalsIgnoreCase(entity.getStatus())) {
            throw new IllegalArgumentException("已取消的 Upload 不允许继续完成 Upload。");
        }
        return Optional.empty();
    }

    private Path persistLocalUploadPartFile(String uploadKey, String partId, String filename, byte[] bytes) {
        try {
            Path directory = uploadPartDirectory(uploadKey);
            Files.createDirectories(directory);
            Path target = directory.resolve(partId + "-" + sanitizeFilename(filename));
            Files.write(target, bytes == null ? new byte[0] : bytes);
            return target;
        } catch (IOException exception) {
            throw new IllegalStateException("写入 Upload part 文件失败。", exception);
        }
    }

    private Path uploadPartDirectory(String uploadKey) throws IOException {
        Path root;
        if (gatewayFileService != null) {
            root = gatewayFileService.ensureStorageDirectoryForSync().resolve("upload-parts");
        } else {
            root = Path.of(System.getProperty("java.io.tmpdir"), "x-ai-gateway", "upload-parts");
        }
        Path directory = root.resolve(uploadKey);
        Files.createDirectories(directory);
        return directory;
    }

    private List<LocalUploadPart> localUploadParts(ObjectNode metadata) {
        List<LocalUploadPart> parts = new ArrayList<>();
        JsonNode bindings = metadata.path("part_bindings");
        if (!bindings.isArray()) {
            return List.of();
        }
        for (JsonNode binding : bindings) {
            String partId = firstNonBlank(text(binding, "part_id"), text(binding, "upstream_part_id"), null);
            String storagePath = text(binding, "storage_path");
            if (partId == null || storagePath == null) {
                continue;
            }
            parts.add(new LocalUploadPart(
                    partId,
                    firstNonBlank(text(binding, "filename"), partId),
                    firstNonBlank(text(binding, "mime_type"), "application/octet-stream"),
                    Path.of(storagePath),
                    binding.path("size_bytes").asLong(0L)
            ));
        }
        return List.copyOf(parts);
    }

    private byte[] mergeUploadParts(List<LocalUploadPart> parts) {
        try {
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            for (LocalUploadPart part : parts) {
                outputStream.write(Files.readAllBytes(part.storagePath()));
            }
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("合并 Upload parts 失败。", exception);
        }
    }

    private void cleanupUploadPartFiles(ObjectNode metadata) {
        for (LocalUploadPart part : localUploadParts(metadata)) {
            try {
                Files.deleteIfExists(part.storagePath());
            } catch (IOException ignored) {
                // ignore cleanup failures for local temp parts
            }
        }
    }

    private String inferUploadMimeType(List<LocalUploadPart> parts, ObjectNode response) {
        String preferred = text(response, "mime_type");
        if (preferred != null) {
            return preferred;
        }
        return parts.stream()
                .map(LocalUploadPart::mimeType)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("application/octet-stream");
    }

    private GatewayFileResponse createUploadOutputFile(
            Long distributedKeyId,
            String filename,
            String mimeType,
            String purpose,
            byte[] bytes,
            Long preferredCredentialId) {
        if (gatewayFileService != null) {
            return gatewayFileService.createFileFromBytes(distributedKeyId, filename, mimeType, purpose, bytes, preferredCredentialId);
        }
        return createLocalOnlyGatewayFile(distributedKeyId, filename, mimeType, purpose, bytes);
    }

    private GatewayFileResponse createLocalOnlyGatewayFile(
            Long distributedKeyId,
            String filename,
            String mimeType,
            String purpose,
            byte[] bytes) {
        try {
            String safeFilename = sanitizeFilename(filename);
            String fileKey = "file-" + UUID.randomUUID().toString().replace("-", "");
            Path root = Path.of(System.getProperty("java.io.tmpdir"), "x-ai-gateway", "files");
            Files.createDirectories(root);
            Path storagePath = root.resolve(fileKey + "-" + safeFilename);
            Files.write(storagePath, bytes == null ? new byte[0] : bytes);

            GatewayFileEntity file = new GatewayFileEntity();
            file.setFileKey(fileKey);
            file.setDistributedKeyId(distributedKeyId);
            file.setFilename(firstNonBlank(filename, safeFilename));
            file.setMimeType(firstNonBlank(mimeType, "application/octet-stream"));
            file.setPurpose(purpose);
            file.setSizeBytes(bytes == null ? 0L : bytes.length);
            file.setSha256(sha256Hex(bytes == null ? new byte[0] : bytes));
            file.setStoragePath(storagePath.toAbsolutePath().toString());
            file.setStatus("staged_local");
            GatewayFileEntity saved = gatewayFileRepository.save(file);
            return GatewayFileResponse.from(
                    saved.getFileKey(),
                    saved.getFilename(),
                    saved.getPurpose(),
                    saved.getSizeBytes(),
                    saved.getCreatedAt(),
                    saved.getStatus()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("创建 Upload 输出文件失败。", exception);
        }
    }

    private Mono<byte[]> readPartBytes(FilePart dataPart) {
        return DataBufferUtils.join(dataPart.content())
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                });
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload.bin";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback == null || fallback.isBlank() ? null : fallback.trim();
    }

    private String firstNonBlank(String primary, String secondary, String fallback) {
        String resolved = firstNonBlank(primary, secondary);
        return resolved != null ? resolved : firstNonBlank(fallback, null);
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境缺少 SHA-256。", exception);
        }
    }

    private void copyIfPresent(ObjectNode source, ObjectNode target, String fieldName) {
        if (source.has(fieldName) && !source.path(fieldName).isNull()) {
            target.set(fieldName, source.get(fieldName).deepCopy());
        }
    }

    private UpstreamTarget resolveUploadTarget(Long distributedKeyId, Long preferredCredentialId) {
        DistributedKeyView distributedKey = distributedKeyQueryService.findActiveById(distributedKeyId)
                .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"));
        Map<Long, UpstreamCredentialEntity> credentials = new LinkedHashMap<>();
        for (UpstreamCredentialEntity credential : upstreamCredentialRepository.findAllByIdInAndDeletedFalse(
                distributedKey.bindings().stream().map(DistributedCredentialBindingView::credentialId).toList())) {
            if (credential.isActive()) {
                credentials.put(credential.getId(), credential);
            }
        }

        if (preferredCredentialId != null) {
            UpstreamTarget preferred = resolveUploadTarget(distributedKey, credentials, preferredCredentialId);
            if (preferred != null) {
                return preferred;
            }
        }

        for (DistributedCredentialBindingView binding : distributedKey.bindings()) {
            UpstreamTarget candidate = resolveUploadTarget(distributedKey, credentials, binding.credentialId());
            if (candidate != null) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("当前 DistributedKey 没有可用的上传编排站点。");
    }

    private UpstreamTarget resolveUploadTarget(
            DistributedKeyView distributedKey,
            Map<Long, UpstreamCredentialEntity> credentials,
            Long credentialId) {
        UpstreamCredentialEntity credential = credentials.get(credentialId);
        if (credential == null || credential.getSiteProfileId() == null) {
            return null;
        }
        UpstreamSiteProfileEntity siteProfile = resolveSiteProfile(credential.getSiteProfileId()).orElse(null);
        SiteCapabilitySnapshotEntity snapshot = siteCapabilitySnapshotRepository.findBySiteProfile_Id(credential.getSiteProfileId())
                .orElse(null);
        if (siteProfile == null || (!siteCapabilityTruthService.supportsFeature(siteProfile, snapshot, InteropFeature.UPLOAD_CREATE)
                && !supportsLocalGeminiUploadSurface(siteProfile, snapshot))) {
            return null;
        }
        return buildUpstreamTarget(credential, siteProfile, basePath(InteropFeature.UPLOAD_CREATE));
    }

    private UpstreamTarget resolveAnthropicMessageBatchTarget(Long distributedKeyId, Long preferredCredentialId) {
        DistributedKeyView distributedKey = distributedKeyQueryService.findActiveById(distributedKeyId)
                .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"));
        Map<Long, UpstreamCredentialEntity> credentials = new LinkedHashMap<>();
        for (UpstreamCredentialEntity credential : upstreamCredentialRepository.findAllByIdInAndDeletedFalse(
                distributedKey.bindings().stream().map(DistributedCredentialBindingView::credentialId).toList())) {
            if (credential.isActive()) {
                credentials.put(credential.getId(), credential);
            }
        }

        if (preferredCredentialId != null) {
            UpstreamTarget preferred = resolveAnthropicMessageBatchTarget(credentials, preferredCredentialId);
            if (preferred != null) {
                return preferred;
            }
        }

        for (DistributedCredentialBindingView binding : distributedKey.bindings()) {
            UpstreamTarget candidate = resolveAnthropicMessageBatchTarget(credentials, binding.credentialId());
            if (candidate != null) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("当前 DistributedKey 没有可用的 Anthropic message batch 上游站点。");
    }

    private UpstreamTarget resolveAnthropicMessageBatchTarget(
            Map<Long, UpstreamCredentialEntity> credentials,
            Long credentialId) {
        UpstreamCredentialEntity credential = credentials.get(credentialId);
        if (credential == null || credential.getSiteProfileId() == null) {
            return null;
        }
        UpstreamSiteProfileEntity siteProfile = resolveSiteProfile(credential.getSiteProfileId()).orElse(null);
        SiteCapabilitySnapshotEntity snapshot = siteCapabilitySnapshotRepository.findBySiteProfile_Id(credential.getSiteProfileId())
                .orElse(null);
        if (siteProfile == null
                || !supportsAnthropicMessageBatches(siteProfile.getSiteKind())
                || !siteCapabilityTruthService.supportsFeature(siteProfile, snapshot, InteropFeature.ANTHROPIC_MESSAGE_BATCH)) {
            return null;
        }
        ResolvedCredentialMaterial credentialMaterial = credentialMaterialResolver.resolveStored(credential);
        return new UpstreamTarget(credential, siteProfile, credentialMaterial, null);
    }

    private UpstreamTarget resolveAnthropicMessageBatchTargetForEntity(
            GatewayAsyncResourceEntity entity,
            ObjectNode metadata) {
        Long credentialId = metadata.has("credential_id") ? metadata.path("credential_id").asLong() : null;
        if (credentialId == null) {
            throw new IllegalArgumentException("Anthropic message batch 缺少 credential_id。");
        }
        UpstreamCredentialEntity credential = upstreamCredentialRepository.findById(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("未找到 Anthropic message batch 绑定的上游凭证。"));
        Long siteProfileId = metadata.has("site_profile_id") && !metadata.path("site_profile_id").isNull()
                ? metadata.path("site_profile_id").asLong()
                : credential.getSiteProfileId();
        UpstreamSiteProfileEntity siteProfile = resolveSiteProfile(siteProfileId)
                .orElseThrow(() -> new IllegalArgumentException("未找到 Anthropic message batch 绑定的站点档案。"));
        if (!supportsAnthropicMessageBatches(siteProfile.getSiteKind())) {
            throw new IllegalArgumentException("当前站点不支持 Anthropic message batches。");
        }
        ResolvedCredentialMaterial credentialMaterial = credentialMaterialResolver.resolveStored(credential);
        return new UpstreamTarget(credential, siteProfile, credentialMaterial, null);
    }

    private boolean supportsLocalGeminiUploadSurface(
            UpstreamSiteProfileEntity siteProfile,
            SiteCapabilitySnapshotEntity snapshot) {
        return siteProfile != null
                && siteProfile.getSiteKind() == UpstreamSiteKind.GEMINI_DIRECT
                && siteCapabilityTruthService.supportsFeature(siteProfile, snapshot, InteropFeature.FILE_OBJECT);
    }

    private String geminiRealtimeBlockedReason(Long distributedKeyId, Long preferredCredentialId) {
        DistributedKeyView distributedKey = distributedKeyQueryService.findActiveById(distributedKeyId)
                .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"));
        Map<Long, UpstreamCredentialEntity> credentials = new LinkedHashMap<>();
        for (UpstreamCredentialEntity credential : upstreamCredentialRepository.findAllByIdInAndDeletedFalse(
                distributedKey.bindings().stream().map(DistributedCredentialBindingView::credentialId).toList())) {
            if (credential.isActive()) {
                credentials.put(credential.getId(), credential);
            }
        }

        if (preferredCredentialId != null) {
            String preferredReason = geminiRealtimeBlockedReason(credentials.get(preferredCredentialId));
            if (preferredReason != null) {
                return preferredReason;
            }
        }
        for (DistributedCredentialBindingView binding : distributedKey.bindings()) {
            String reason = geminiRealtimeBlockedReason(credentials.get(binding.credentialId()));
            if (reason != null) {
                return reason;
            }
        }
        return null;
    }

    private String geminiRealtimeBlockedReason(UpstreamCredentialEntity credential) {
        if (credential == null || credential.getSiteProfileId() == null) {
            return null;
        }
        UpstreamSiteProfileEntity siteProfile = resolveSiteProfile(credential.getSiteProfileId()).orElse(null);
        if (siteProfile == null || siteProfile.getSiteKind() != UpstreamSiteKind.GEMINI_DIRECT) {
            return null;
        }
        return "Gemini ephemeral/live token 不等价于 OpenAI realtime client_secret object，因此当前不开放。";
    }

    private UpstreamTarget resolveUpstreamTarget(Long distributedKeyId, InteropFeature feature) {
        return resolveUpstreamTarget(distributedKeyId, feature, null);
    }

    private UpstreamTarget resolveUpstreamTarget(Long distributedKeyId, InteropFeature feature, Long preferredCredentialId) {
        DistributedKeyView distributedKey = distributedKeyQueryService.findActiveById(distributedKeyId)
                .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"));
        Map<Long, UpstreamCredentialEntity> credentials = new LinkedHashMap<>();
        for (UpstreamCredentialEntity credential : upstreamCredentialRepository.findAllByIdInAndDeletedFalse(
                distributedKey.bindings().stream().map(DistributedCredentialBindingView::credentialId).toList())) {
            if (credential.isActive()) {
                credentials.put(credential.getId(), credential);
            }
        }

        if (preferredCredentialId != null) {
            UpstreamCredentialEntity preferred = credentials.get(preferredCredentialId);
            if (preferred != null && preferred.getSiteProfileId() != null) {
                UpstreamSiteProfileEntity siteProfile = resolveSiteProfile(preferred.getSiteProfileId()).orElse(null);
                SiteCapabilitySnapshotEntity snapshot = siteCapabilitySnapshotRepository.findBySiteProfile_Id(preferred.getSiteProfileId())
                        .orElse(null);
                if (siteProfile != null && siteCapabilityTruthService.supportsFeature(siteProfile, snapshot, feature)) {
                    return buildUpstreamTarget(preferred, siteProfile, basePath(feature));
                }
            }
        }

        for (DistributedCredentialBindingView binding : distributedKey.bindings()) {
            UpstreamCredentialEntity credential = credentials.get(binding.credentialId());
            if (credential == null || credential.getSiteProfileId() == null) {
                continue;
            }
            UpstreamSiteProfileEntity siteProfile = resolveSiteProfile(credential.getSiteProfileId()).orElse(null);
            SiteCapabilitySnapshotEntity snapshot = siteCapabilitySnapshotRepository.findBySiteProfile_Id(credential.getSiteProfileId())
                    .orElse(null);
            if (siteProfile == null || !siteCapabilityTruthService.supportsFeature(siteProfile, snapshot, feature)) {
                continue;
            }
            return buildUpstreamTarget(credential, siteProfile, basePath(feature));
        }
        throw new IllegalArgumentException("当前 DistributedKey 没有可用的异步资源上游编排站点。");
    }

    private UpstreamTarget resolveUpstreamTargetForEntity(GatewayAsyncResourceEntity entity, ObjectNode metadata) {
        Long credentialId = metadata.has("credential_id") ? metadata.path("credential_id").asLong() : null;
        if (credentialId == null) {
            return resolveUpstreamTarget(entity.getDistributedKeyId(), featureFor(entity.getResourceType()));
        }
        UpstreamCredentialEntity credential = upstreamCredentialRepository.findById(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("未找到异步资源绑定的上游凭证。"));
        Long siteProfileId = metadata.has("site_profile_id") && !metadata.path("site_profile_id").isNull()
                ? metadata.path("site_profile_id").asLong()
                : credential.getSiteProfileId();
        UpstreamSiteProfileEntity siteProfile = resolveSiteProfile(siteProfileId)
                .orElseThrow(() -> new IllegalArgumentException("未找到异步资源绑定的站点档案。"));
        return buildUpstreamTarget(credential, siteProfile, basePath(featureFor(entity.getResourceType())));
    }

    private Optional<UpstreamSiteProfileEntity> resolveSiteProfile(Long siteProfileId) {
        if (siteProfileId == null) {
            return Optional.empty();
        }
        return upstreamSiteProfileRepository.findById(siteProfileId);
    }

    private UpstreamTarget buildUpstreamTarget(
            UpstreamCredentialEntity credential,
            UpstreamSiteProfileEntity siteProfile,
            String requestPath) {
        ResolvedCredentialMaterial credentialMaterial = credentialMaterialResolver.resolveStored(credential);
        SiteClient request = supportsGoogleGenAiBatching(siteProfile.getSiteKind())
                ? null
                : buildClient(credential, siteProfile, requestPath, credentialMaterial);
        return new UpstreamTarget(credential, siteProfile, credentialMaterial, request);
    }

    private SiteClient buildClient(
            UpstreamCredentialEntity credential,
            UpstreamSiteProfileEntity siteProfile,
            String requestPath,
            ResolvedCredentialMaterial credentialMaterial) {
        WebClient.Builder builder = webClientBuilder.clone().baseUrl(credential.getBaseUrl().replaceAll("/+$", ""));
        String path = resolvePath(credential.getBaseUrl(), requestPath);
        if (siteProfile.getAuthStrategy() == AuthStrategy.BEARER) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + credentialMaterial.secret());
        } else if (siteProfile.getAuthStrategy() == AuthStrategy.API_KEY_HEADER) {
            builder.defaultHeader("x-api-key", credentialMaterial.secret());
        } else if (siteProfile.getAuthStrategy() == AuthStrategy.AZURE_API_KEY) {
            builder.defaultHeader("api-key", credentialMaterial.secret());
        } else {
            throw new IllegalArgumentException("当前站点鉴权策略不支持异步资源编排。");
        }
        if (siteProfile.getPathStrategy() != PathStrategy.OPENAI_V1) {
            throw new IllegalArgumentException("当前站点路径策略不支持异步资源编排。");
        }
        return new SiteClient(builder.build(), path);
    }

    private Client createGeminiClient(UpstreamTarget target) {
        return geminiChatModelFactory.createClient(
                target.siteProfile().getSiteKind(),
                target.credential().getBaseUrl(),
                target.credentialMaterial()
        );
    }

    private AnthropicClient createAnthropicClient(UpstreamTarget target) {
        return anthropicChatModelFactory.createClient(
                target.credential().getBaseUrl(),
                target.credentialMaterial().secret()
        );
    }

    private boolean supportsGoogleGenAiBatching(UpstreamSiteKind siteKind) {
        return siteKind == UpstreamSiteKind.GEMINI_DIRECT || siteKind == UpstreamSiteKind.VERTEX_AI;
    }

    private boolean supportsAnthropicMessageBatches(UpstreamSiteKind siteKind) {
        return siteKind == UpstreamSiteKind.ANTHROPIC_DIRECT;
    }

    private JsonNode invokeUpstreamJson(UpstreamTarget target, String path, JsonNode payload) {
        return target.client().post()
                .uri(path.startsWith("/v1/") ? resolvePath(target.credential().getBaseUrl(), path) : path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    private Mono<JsonNode> invokeUpstreamMultipart(UpstreamTarget target, String path, FilePart dataPart) {
        return DataBufferUtils.join(dataPart.content())
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    MultiValueMap<String, HttpEntity<?>> body = new LinkedMultiValueMap<>();
                    HttpHeaders fileHeaders = new HttpHeaders();
                    fileHeaders.setContentType(dataPart.headers().getContentType() == null
                            ? MediaType.APPLICATION_OCTET_STREAM
                            : dataPart.headers().getContentType());
                    body.add("data", new HttpEntity<>(new ByteArrayResource(bytes) {
                        @Override
                        public String getFilename() {
                            return dataPart.filename();
                        }
                    }, fileHeaders));
                    return body;
                })
                .flatMap(body -> target.client().post()
                        .uri(path.startsWith("/v1/") ? resolvePath(target.credential().getBaseUrl(), path) : path)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(BodyInserters.fromMultipartData(body))
                        .retrieve()
                        .bodyToMono(JsonNode.class));
    }

    private Mono<JsonNode> invokeUpstreamMultipart(
            UpstreamTarget target,
            String path,
            String filename,
            String mimeType,
            byte[] bytes) {
        MultiValueMap<String, HttpEntity<?>> body = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(mimeType == null || mimeType.isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(mimeType));
        body.add("data", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }, fileHeaders));
        return target.client().post()
                .uri(path.startsWith("/v1/") ? resolvePath(target.credential().getBaseUrl(), path) : path)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body))
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    private ObjectNode rewriteFileRefs(ObjectNode payload, Long distributedKeyId, UpstreamTarget target) {
        if (payload.hasNonNull("input_file_id")) {
            payload.put("input_file_id", resolveExternalFileId(payload.path("input_file_id").asText(), distributedKeyId, target));
        }
        if (payload.hasNonNull("training_file")) {
            payload.put("training_file", resolveExternalFileId(payload.path("training_file").asText(), distributedKeyId, target));
        }
        if (payload.hasNonNull("validation_file")) {
            payload.put("validation_file", resolveExternalFileId(payload.path("validation_file").asText(), distributedKeyId, target));
        }
        return payload;
    }

    private String resolveExternalFileId(String fileKey, Long distributedKeyId, UpstreamTarget target) {
        GatewayFileEntity file = gatewayFileRepository.findByFileKeyAndDeletedFalse(fileKey)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的网关文件对象。"));
        if (!file.getDistributedKeyId().equals(distributedKeyId)) {
            throw new IllegalArgumentException("文件对象不属于当前 DistributedKey。");
        }
        return resolveBindingForTarget(file.getId(), target).stream()
                .findFirst()
                .map(GatewayFileBindingEntity::getExternalFileId)
                .orElseThrow(() -> new IllegalArgumentException("文件对象尚未完成 upstream binding。"));
    }

    private List<GatewayFileBindingEntity> resolveBindingForTarget(Long gatewayFileId, UpstreamTarget target) {
        if (target == null) {
            return gatewayFileBindingRepository.findAllByGatewayFileIdOrderByCreatedAtDesc(gatewayFileId);
        }
        if (target.credential().getId() != null) {
            List<GatewayFileBindingEntity> byCredential = gatewayFileBindingRepository
                    .findAllByGatewayFileIdAndCredentialIdOrderByCreatedAtDesc(gatewayFileId, target.credential().getId());
            if (!byCredential.isEmpty()) {
                return byCredential;
            }
        }
        if (target.siteProfile().getId() != null) {
            List<GatewayFileBindingEntity> bySiteProfile = gatewayFileBindingRepository
                    .findAllByGatewayFileIdAndSiteProfileIdOrderByCreatedAtDesc(gatewayFileId, target.siteProfile().getId());
            if (!bySiteProfile.isEmpty()) {
                return bySiteProfile;
            }
        }
        return gatewayFileBindingRepository.findAllByGatewayFileIdOrderByCreatedAtDesc(gatewayFileId).stream()
                .filter(binding -> binding.getProviderType() == target.credential().getProviderType())
                .toList();
    }

    private GatewayFileContent getGatewayFileContent(String fileKey, Long distributedKeyId) {
        GatewayFileEntity file = gatewayFileRepository.findByFileKeyAndDeletedFalse(fileKey)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的网关文件对象。"));
        if (!file.getDistributedKeyId().equals(distributedKeyId)) {
            throw new IllegalArgumentException("文件对象不属于当前 DistributedKey。");
        }
        try {
            return new GatewayFileContent(
                    com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResponse.from(
                            file.getFileKey(),
                            file.getFilename(),
                            file.getPurpose(),
                            file.getSizeBytes(),
                            file.getCreatedAt(),
                            file.getStatus()
                    ),
                    java.nio.file.Files.readAllBytes(java.nio.file.Path.of(file.getStoragePath())),
                    file.getMimeType()
            );
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("读取网关文件失败。", exception);
        }
    }

    private GatewayFileContent resolveGatewayFileContent(String fileKey, Long distributedKeyId) {
        if (gatewayFileService != null) {
            return gatewayFileService.getFileContent(fileKey, distributedKeyId);
        }
        return getGatewayFileContent(fileKey, distributedKeyId);
    }

    private GatewayAsyncResourceEntity getRequired(String resourceKey, GatewayAsyncResourceType type, Long distributedKeyId) {
        Optional<GatewayAsyncResourceEntity> entity = gatewayAsyncResourceRepository
                .findByResourceKeyAndResourceTypeAndDeletedFalse(resourceKey, type);
        if (entity.isEmpty() || !entity.get().getDistributedKeyId().equals(distributedKeyId)) {
            throw new IllegalArgumentException("未找到指定的异步资源对象。");
        }
        return entity.get();
    }

    private ObjectNode requireObject(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("请求体必须是 JSON object。");
        }
        return (ObjectNode) payload;
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private ObjectNode appendEvent(ObjectNode metadata, String eventType, String status) {
        metadata.withArray("events").addObject()
                .put("type", eventType)
                .put("status", status)
                .put("at", now().getEpochSecond());
        return metadata;
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private JsonNode readJson(String json) {
        try {
            return json == null || json.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(json);
        } catch (JacksonException exception) {
            throw new IllegalStateException("解析异步资源 JSON 失败。", exception);
        }
    }

    private ObjectNode readObject(String json) {
        JsonNode node = readJson(json);
        return node instanceof ObjectNode objectNode ? objectNode : objectMapper.createObjectNode();
    }

    private ObjectNode copyObject(JsonNode node) {
        JsonNode copied = node == null ? objectMapper.createObjectNode() : node.deepCopy();
        return copied instanceof ObjectNode objectNode ? objectNode : objectMapper.createObjectNode();
    }

    private String writeJson(JsonNode node) {
        try {
            return node == null ? null : objectMapper.writeValueAsString(node);
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化异步资源 JSON 失败。", exception);
        }
    }

    private String resolvePath(String baseUrl, String requestPath) {
        String normalizedBaseUrl = baseUrl.replaceAll("/+$", "");
        String normalizedPath = requestPath.startsWith("/") ? requestPath : "/" + requestPath;
        if (normalizedBaseUrl.endsWith("/v1") && normalizedPath.startsWith("/v1/")) {
            return normalizedPath.substring(3);
        }
        return normalizedPath;
    }

    private String appendRepeatedQueryParams(String path, String key, List<String> values) {
        String current = path;
        if (values == null || values.isEmpty()) {
            return current;
        }
        for (String value : values) {
            current = appendQueryParam(current, key, value);
        }
        return current;
    }

    private String appendQueryParam(String path, String key, String value) {
        if (value == null || value.isBlank()) {
            return path;
        }
        String separator = path.contains("?") ? "&" : "?";
        return path + separator + encodeQueryParam(key) + "=" + encodeQueryParam(value);
    }

    private String encodeQueryParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String basePath(InteropFeature feature) {
        return switch (feature) {
            case RESPONSE_OBJECT -> "/v1/responses";
            case UPLOAD_CREATE -> "/v1/uploads";
            case BATCH_CREATE -> "/v1/batches";
            case TUNING_CREATE -> "/v1/fine_tuning/jobs";
            case REALTIME_CLIENT_SECRET -> "/v1/realtime/client_secrets";
            case VIDEO_GENERATION -> "/v1/videos/generations";
            case MUSIC_GENERATION -> "/v1/music/generations";
            default -> throw new IllegalArgumentException("当前 feature 不支持异步资源编排。");
        };
    }

    private InteropFeature featureFor(GatewayAsyncResourceType resourceType) {
        return switch (resourceType) {
            case RESPONSE -> InteropFeature.RESPONSE_OBJECT;
            case UPLOAD -> InteropFeature.UPLOAD_CREATE;
            case BATCH -> InteropFeature.BATCH_CREATE;
            case TUNING -> InteropFeature.TUNING_CREATE;
            case REALTIME_SESSION -> InteropFeature.REALTIME_CLIENT_SECRET;
            case VIDEO -> InteropFeature.VIDEO_GENERATION;
            case MUSIC -> InteropFeature.MUSIC_GENERATION;
            default -> throw new IllegalArgumentException("当前资源类型不支持 upstream feature 推断。");
        };
    }

    private String inferObjectName(GatewayAsyncResourceType resourceType) {
        return switch (resourceType) {
            case UPLOAD -> "upload";
            case BATCH -> "batch";
            case TUNING -> "fine_tuning.job";
            case REALTIME_SESSION -> "realtime.session";
            case VIDEO -> "video.generation";
            case MUSIC -> "music.generation";
            case RESPONSE -> "response";
            case CONVERSATION -> "conversation";
            case CONVERSATION_ITEM -> "conversation.item";
            case VECTOR_STORE -> "vector_store";
            case VECTOR_STORE_FILE -> "vector_store.file";
            case VECTOR_STORE_FILE_BATCH -> "vector_store.file_batch";
            case WEBHOOK_EVENT -> "event";
        };
    }

    private boolean isTerminalStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return "completed".equals(normalized)
                || "succeeded".equals(normalized)
                || "success".equals(normalized)
                || "done".equals(normalized)
                || "cancelled".equals(normalized)
                || "canceled".equals(normalized)
                || "failed".equals(normalized)
                || "error".equals(normalized)
                || "errored".equals(normalized)
                || "deleted".equals(normalized);
    }

    public record GoogleNativeBatchView(
            GatewayAsyncResourceEntity entity,
            ObjectNode responsePayload,
            ObjectNode metadata
    ) {
    }

    private record LocalUploadPart(
            String partId,
            String filename,
            String mimeType,
            Path storagePath,
            long sizeBytes
    ) {
    }

    private record VectorStoreSearchResult(
            String fileId,
            double score,
            ObjectNode payload
    ) {
    }

    private record SiteClient(
            WebClient client,
            String path
    ) {
    }

    private record UpstreamTarget(
            UpstreamCredentialEntity credential,
            UpstreamSiteProfileEntity siteProfile,
            ResolvedCredentialMaterial credentialMaterial,
            SiteClient siteClient
    ) {
        private WebClient client() {
            if (siteClient == null) {
                throw new IllegalStateException("当前 UpstreamTarget 不支持 WebClient 调用。");
            }
            return siteClient.client();
        }

        private String path() {
            if (siteClient == null) {
                throw new IllegalStateException("当前 UpstreamTarget 不支持路径解析。");
            }
            return siteClient.path();
        }
    }

    private record StoredChatCursor(Long id, Instant createdAt) {
        private static StoredChatCursor from(GatewayAsyncResourceEntity entity) {
            if (entity.getId() == null || entity.getCreatedAt() == null) {
                throw new IllegalStateException("Stored Chat cursor 缺少数据库 id 或 createdAt。");
            }
            return new StoredChatCursor(entity.getId(), entity.getCreatedAt());
        }
    }

    private record ResourceCursor(Long id, Instant createdAt, boolean invalid) {
        private static ResourceCursor from(GatewayAsyncResourceEntity entity) {
            if (entity.getId() == null || entity.getCreatedAt() == null) {
                throw new IllegalStateException("资源游标缺少数据库 id 或 createdAt。");
            }
            return new ResourceCursor(entity.getId(), entity.getCreatedAt(), false);
        }

        private static ResourceCursor invalidCursor() {
            return new ResourceCursor(null, null, true);
        }
    }
}
