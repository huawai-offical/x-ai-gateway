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
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileContent;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBufferUtils;
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

    private final GatewayAsyncResourceRepository gatewayAsyncResourceRepository;
    private final DistributedKeyQueryService distributedKeyQueryService;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final UpstreamSiteProfileRepository upstreamSiteProfileRepository;
    private final SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository;
    private final GatewayFileRepository gatewayFileRepository;
    private final GatewayFileBindingRepository gatewayFileBindingRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final CredentialMaterialResolver credentialMaterialResolver;
    private final SiteCapabilityTruthService siteCapabilityTruthService;
    private final AnthropicChatModelFactory anthropicChatModelFactory;
    private final GeminiChatModelFactory geminiChatModelFactory;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final WebClient.Builder webClientBuilder;

    @Autowired
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
        this.gatewayAsyncResourceRepository = gatewayAsyncResourceRepository;
        this.distributedKeyQueryService = distributedKeyQueryService;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.upstreamSiteProfileRepository = upstreamSiteProfileRepository;
        this.siteCapabilitySnapshotRepository = siteCapabilitySnapshotRepository;
        this.gatewayFileRepository = gatewayFileRepository;
        this.gatewayFileBindingRepository = gatewayFileBindingRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.credentialMaterialResolver = credentialMaterialResolver;
        this.siteCapabilityTruthService = siteCapabilityTruthService;
        this.anthropicChatModelFactory = anthropicChatModelFactory;
        this.geminiChatModelFactory = geminiChatModelFactory;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.webClientBuilder = webClientBuilder;
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
                credentialCryptoService,
                credentialMaterialResolver,
                siteCapabilityTruthService,
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
                credentialCryptoService,
                new CredentialMaterialResolver(new com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService(
                        null,
                        null,
                        null,
                        null
                ), credentialCryptoService, objectMapper),
                siteCapabilityTruthService,
                new AnthropicChatModelFactory(ObservationRegistry.NOOP),
                new GeminiChatModelFactory(ObservationRegistry.NOOP),
                objectMapper,
                clock,
                webClientBuilder
        );
    }

    public JsonNode storeResponse(Long distributedKeyId, String requestModel, JsonNode requestPayload, JsonNode responsePayload) {
        String resourceKey = "resp_" + UUID.randomUUID().toString().replace("-", "");
        ObjectNode storedResponse = copyObject(responsePayload);
        storedResponse.put("id", resourceKey);
        if (!storedResponse.has("status")) {
            storedResponse.put("status", "completed");
        }

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("object_mode", "gateway_response_object");
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
    public JsonNode getResponse(String responseId, Long distributedKeyId) {
        GatewayAsyncResourceEntity entity = getRequired(responseId, GatewayAsyncResourceType.RESPONSE, distributedKeyId);
        return readJson(entity.getResponsePayloadJson());
    }

    public JsonNode deleteResponse(String responseId, Long distributedKeyId) {
        GatewayAsyncResourceEntity entity = getRequired(responseId, GatewayAsyncResourceType.RESPONSE, distributedKeyId);
        entity.setDeleted(true);
        entity.setStatus("deleted");
        entity.setMetadataJson(writeJson(appendEvent(readObject(entity.getMetadataJson()), "deleted", "deleted")));
        gatewayAsyncResourceRepository.save(entity);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", responseId);
        payload.put("object", "response.deleted");
        payload.put("deleted", true);
        return payload;
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
        ObjectNode metadata = readObject(entity.getMetadataJson());
        String upstreamId = metadata.path("upstream_object_id").asText(null);
        if (upstreamId == null || upstreamId.isBlank()) {
            return Mono.fromSupplier(() -> addLocalUploadPart(entity));
        }
        UpstreamTarget target = resolveUpstreamTargetForEntity(entity, metadata);
        return invokeUpstreamMultipart(target, target.path() + "/" + upstreamId + "/parts", dataPart)
                .map(upstreamResponse -> persistUploadPart(entity, uploadId, dataPart.filename(), upstreamResponse));
    }

    public Mono<JsonNode> addUploadPartFromGatewayFile(String uploadId, Long distributedKeyId, String fileKey) {
        GatewayAsyncResourceEntity entity = getRequired(uploadId, GatewayAsyncResourceType.UPLOAD, distributedKeyId);
        GatewayFileContent fileContent = getGatewayFileContent(fileKey, distributedKeyId);
        ObjectNode metadata = readObject(entity.getMetadataJson());
        String upstreamId = metadata.path("upstream_object_id").asText(null);
        if (upstreamId == null || upstreamId.isBlank()) {
            return Mono.fromSupplier(() -> addLocalUploadPart(entity, fileContent.metadata().filename()));
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

    public JsonNode cancelTuning(String tuningId, Long distributedKeyId) {
        return completeRemoteStatus(tuningId, distributedKeyId, GatewayAsyncResourceType.TUNING, InteropFeature.TUNING_CREATE, "/cancel");
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
            return syncPersistedResource(entity, fetchGeminiTuning(entity, metadata, target), objectName);
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
            return updateLocalStatus(resourceKey, distributedKeyId, resourceType, suffix.contains("cancel") ? "cancelled" : "completed");
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
            return syncPersistedResource(entity, fetchGeminiTuning(entity, metadata, target), inferObjectName(resourceType));
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
        return addLocalUploadPart(entity, null);
    }

    private JsonNode addLocalUploadPart(GatewayAsyncResourceEntity entity, String filename) {
        ObjectNode metadata = readObject(entity.getMetadataJson());
        String partId = "part_" + UUID.randomUUID().toString().replace("-", "");
        metadata.withArray("parts").add(partId);
        metadata.put("partsCount", metadata.withArray("parts").size());
        if (filename != null && !filename.isBlank()) {
            metadata.withArray("part_bindings").addObject()
                    .put("filename", filename)
                    .put("synced_at", now().getEpochSecond());
        }
        entity.setMetadataJson(writeJson(appendEvent(metadata, "part_added", entity.getStatus())));
        gatewayAsyncResourceRepository.save(entity);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", partId);
        response.put("object", "upload.part");
        response.put("created_at", now().getEpochSecond());
        response.put("upload_id", entity.getResourceKey());
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

    private String basePath(InteropFeature feature) {
        return switch (feature) {
            case UPLOAD_CREATE -> "/v1/uploads";
            case BATCH_CREATE -> "/v1/batches";
            case TUNING_CREATE -> "/v1/fine_tuning/jobs";
            case REALTIME_CLIENT_SECRET -> "/v1/realtime/client_secrets";
            default -> throw new IllegalArgumentException("当前 feature 不支持异步资源编排。");
        };
    }

    private InteropFeature featureFor(GatewayAsyncResourceType resourceType) {
        return switch (resourceType) {
            case UPLOAD -> InteropFeature.UPLOAD_CREATE;
            case BATCH -> InteropFeature.BATCH_CREATE;
            case TUNING -> InteropFeature.TUNING_CREATE;
            case REALTIME_SESSION -> InteropFeature.REALTIME_CLIENT_SECRET;
            default -> throw new IllegalArgumentException("当前资源类型不支持 upstream feature 推断。");
        };
    }

    private String inferObjectName(GatewayAsyncResourceType resourceType) {
        return switch (resourceType) {
            case UPLOAD -> "upload";
            case BATCH -> "batch";
            case TUNING -> "fine_tuning.job";
            case REALTIME_SESSION -> "realtime.session";
            case RESPONSE -> "response";
        };
    }

    public record GoogleNativeBatchView(
            GatewayAsyncResourceEntity entity,
            ObjectNode responsePayload,
            ObjectNode metadata
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
}
