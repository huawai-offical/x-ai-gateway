package com.prodigalgal.xaigateway.gateway.core.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
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
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    private final SiteCapabilityTruthService siteCapabilityTruthService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final WebClient.Builder webClientBuilder;

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
        this.gatewayAsyncResourceRepository = gatewayAsyncResourceRepository;
        this.distributedKeyQueryService = distributedKeyQueryService;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.upstreamSiteProfileRepository = upstreamSiteProfileRepository;
        this.siteCapabilitySnapshotRepository = siteCapabilitySnapshotRepository;
        this.gatewayFileRepository = gatewayFileRepository;
        this.gatewayFileBindingRepository = gatewayFileBindingRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.siteCapabilityTruthService = siteCapabilityTruthService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.webClientBuilder = webClientBuilder;
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
        ObjectNode payload = requireObject(requestBody);
        UpstreamTarget target = resolveUpstreamTarget(distributedKeyId, InteropFeature.UPLOAD_CREATE);
        JsonNode upstreamResponse = invokeUpstreamJson(target, "/v1/uploads", rewriteFileRefs(payload, distributedKeyId));
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
                .map(upstreamResponse -> persistUploadPart(entity, uploadId, dataPart, upstreamResponse));
    }

    public JsonNode completeUpload(String uploadId, Long distributedKeyId) {
        return completeRemoteStatus(uploadId, distributedKeyId, GatewayAsyncResourceType.UPLOAD, InteropFeature.UPLOAD_CREATE, "/complete");
    }

    public JsonNode cancelUpload(String uploadId, Long distributedKeyId) {
        return completeRemoteStatus(uploadId, distributedKeyId, GatewayAsyncResourceType.UPLOAD, InteropFeature.UPLOAD_CREATE, "/cancel");
    }

    public JsonNode createBatch(Long distributedKeyId, JsonNode requestBody) {
        ObjectNode payload = rewriteFileRefs(requireObject(requestBody), distributedKeyId);
        UpstreamTarget target = resolveUpstreamTarget(distributedKeyId, InteropFeature.BATCH_CREATE);
        JsonNode upstreamResponse = invokeUpstreamJson(target, "/v1/batches", payload);
        return persistUpstreamBackedResource(distributedKeyId, GatewayAsyncResourceType.BATCH, "batch_", payload, upstreamResponse, "batch", target);
    }

    public JsonNode getBatch(String batchId, Long distributedKeyId) {
        return readOrSyncResource(batchId, distributedKeyId, GatewayAsyncResourceType.BATCH, "batch");
    }

    public JsonNode cancelBatch(String batchId, Long distributedKeyId) {
        return completeRemoteStatus(batchId, distributedKeyId, GatewayAsyncResourceType.BATCH, InteropFeature.BATCH_CREATE, "/cancel");
    }

    public JsonNode createTuning(Long distributedKeyId, JsonNode requestBody) {
        ObjectNode payload = rewriteFileRefs(requireObject(requestBody), distributedKeyId);
        UpstreamTarget target = resolveUpstreamTarget(distributedKeyId, InteropFeature.TUNING_CREATE);
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
        ObjectNode payload = requireObject(requestBody);
        UpstreamTarget target = resolveUpstreamTarget(distributedKeyId, InteropFeature.REALTIME_CLIENT_SECRET);
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
        UpstreamTarget target = resolveUpstreamTargetForEntity(entity, metadata);
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
        UpstreamTarget target = resolveUpstreamTargetForEntity(entity, metadata);
        JsonNode upstreamResponse = invokeUpstreamJson(target, target.path() + "/" + upstreamId + suffix, objectMapper.createObjectNode());
        return syncPersistedResource(entity, upstreamResponse, inferObjectName(resourceType));
    }

    private JsonNode persistUpstreamBackedResource(
            Long distributedKeyId,
            GatewayAsyncResourceType type,
            String idPrefix,
            JsonNode requestPayload,
            JsonNode upstreamResponse,
            String objectName,
            UpstreamTarget target) {
        String resourceKey = idPrefix + UUID.randomUUID().toString().replace("-", "");
        ObjectNode response = copyObject(upstreamResponse);
        response.put("id", resourceKey);
        if (!response.has("object")) {
            response.put("object", objectName);
        }
        String upstreamId = upstreamResponse.path("id").asText(null);
        String status = response.path("status").asText("created");

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("object_mode", "upstream_object_with_local_lineage");
        if (upstreamId != null) {
            metadata.put("upstream_object_id", upstreamId);
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
        metadata.put("upstream_status", upstreamResponse.path("status").asText(status));
        metadata.put("upstream_synced_at", now().getEpochSecond());
        entity.setMetadataJson(writeJson(appendEvent(metadata, "synced", status)));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    private JsonNode persistUploadPart(
            GatewayAsyncResourceEntity entity,
            String uploadId,
            FilePart dataPart,
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
                .put("filename", dataPart.filename())
                .put("synced_at", now().getEpochSecond());
        metadata.put("partsCount", metadata.withArray("parts").size());
        metadata.put("upstream_synced_at", now().getEpochSecond());
        entity.setMetadataJson(writeJson(appendEvent(metadata, "part_added", entity.getStatus())));
        gatewayAsyncResourceRepository.save(entity);
        return response;
    }

    private JsonNode addLocalUploadPart(GatewayAsyncResourceEntity entity) {
        ObjectNode metadata = readObject(entity.getMetadataJson());
        String partId = "part_" + UUID.randomUUID().toString().replace("-", "");
        metadata.withArray("parts").add(partId);
        metadata.put("partsCount", metadata.withArray("parts").size());
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

    private UpstreamTarget resolveUpstreamTarget(Long distributedKeyId, InteropFeature feature) {
        DistributedKeyView distributedKey = distributedKeyQueryService.findActiveById(distributedKeyId)
                .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"));
        Map<Long, UpstreamCredentialEntity> credentials = new LinkedHashMap<>();
        for (UpstreamCredentialEntity credential : upstreamCredentialRepository.findAllByIdInAndDeletedFalse(
                distributedKey.bindings().stream().map(DistributedCredentialBindingView::credentialId).toList())) {
            if (credential.isActive()) {
                credentials.put(credential.getId(), credential);
            }
        }

        for (DistributedCredentialBindingView binding : distributedKey.bindings()) {
            UpstreamCredentialEntity credential = credentials.get(binding.credentialId());
            if (credential == null || credential.getSiteProfileId() == null) {
                continue;
            }
            UpstreamSiteProfileEntity siteProfile = upstreamSiteProfileRepository.findById(credential.getSiteProfileId()).orElse(null);
            SiteCapabilitySnapshotEntity snapshot = siteCapabilitySnapshotRepository.findBySiteProfile_Id(credential.getSiteProfileId())
                    .orElse(null);
            if (siteProfile == null || !siteCapabilityTruthService.supportsFeature(siteProfile, snapshot, feature)) {
                continue;
            }
            return new UpstreamTarget(credential, siteProfile, buildClient(credential, siteProfile, basePath(feature)));
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
        UpstreamSiteProfileEntity siteProfile = upstreamSiteProfileRepository.findById(credential.getSiteProfileId())
                .orElseThrow(() -> new IllegalArgumentException("未找到异步资源绑定的站点档案。"));
        return new UpstreamTarget(credential, siteProfile, buildClient(credential, siteProfile, basePath(featureFor(entity.getResourceType()))));
    }

    private SiteClient buildClient(
            UpstreamCredentialEntity credential,
            UpstreamSiteProfileEntity siteProfile,
            String requestPath) {
        String apiKey = credentialCryptoService.decrypt(credential.getApiKeyCiphertext());
        WebClient.Builder builder = webClientBuilder.clone().baseUrl(credential.getBaseUrl().replaceAll("/+$", ""));
        String path = resolvePath(credential.getBaseUrl(), requestPath);
        if (siteProfile.getAuthStrategy() == AuthStrategy.BEARER) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        } else if (siteProfile.getAuthStrategy() == AuthStrategy.API_KEY_HEADER) {
            builder.defaultHeader("x-api-key", apiKey);
        } else if (siteProfile.getAuthStrategy() == AuthStrategy.AZURE_API_KEY) {
            builder.defaultHeader("api-key", apiKey);
        } else {
            throw new IllegalArgumentException("当前站点鉴权策略不支持异步资源编排。");
        }
        if (siteProfile.getPathStrategy() != PathStrategy.OPENAI_V1) {
            throw new IllegalArgumentException("当前站点路径策略不支持异步资源编排。");
        }
        return new SiteClient(builder.build(), path);
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

    private ObjectNode rewriteFileRefs(ObjectNode payload, Long distributedKeyId) {
        if (payload.hasNonNull("input_file_id")) {
            payload.put("input_file_id", resolveExternalFileId(payload.path("input_file_id").asText(), distributedKeyId));
        }
        if (payload.hasNonNull("training_file")) {
            payload.put("training_file", resolveExternalFileId(payload.path("training_file").asText(), distributedKeyId));
        }
        return payload;
    }

    private String resolveExternalFileId(String fileKey, Long distributedKeyId) {
        GatewayFileEntity file = gatewayFileRepository.findByFileKeyAndDeletedFalse(fileKey)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的网关文件对象。"));
        if (!file.getDistributedKeyId().equals(distributedKeyId)) {
            throw new IllegalArgumentException("文件对象不属于当前 DistributedKey。");
        }
        return gatewayFileBindingRepository.findAllByGatewayFileIdOrderByCreatedAtDesc(file.getId()).stream()
                .findFirst()
                .map(GatewayFileBindingEntity::getExternalFileId)
                .orElseThrow(() -> new IllegalArgumentException("文件对象尚未完成 upstream binding。"));
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

    private JsonNode readJson(String json) {
        try {
            return json == null || json.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
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
        } catch (JsonProcessingException exception) {
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

    private record SiteClient(
            WebClient client,
            String path
    ) {
    }

    private record UpstreamTarget(
            UpstreamCredentialEntity credential,
            UpstreamSiteProfileEntity siteProfile,
            SiteClient siteClient
    ) {
        private WebClient client() {
            return siteClient.client();
        }

        private String path() {
            return siteClient.path();
        }
    }
}
