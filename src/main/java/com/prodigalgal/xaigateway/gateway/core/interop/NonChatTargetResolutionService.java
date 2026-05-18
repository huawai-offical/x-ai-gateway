package com.prodigalgal.xaigateway.gateway.core.interop;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class NonChatTargetResolutionService {

    private final DistributedKeyQueryService distributedKeyQueryService;
    private final GatewayFileRepository gatewayFileRepository;
    private final GatewayFileBindingRepository gatewayFileBindingRepository;
    private final GatewayAsyncResourceRepository gatewayAsyncResourceRepository;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final UpstreamSiteProfileRepository upstreamSiteProfileRepository;
    private final SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository;
    private final SiteCapabilityTruthService siteCapabilityTruthService;
    private final ObjectMapper objectMapper;

    public NonChatTargetResolutionService(
            DistributedKeyQueryService distributedKeyQueryService,
            GatewayFileRepository gatewayFileRepository,
            GatewayFileBindingRepository gatewayFileBindingRepository,
            GatewayAsyncResourceRepository gatewayAsyncResourceRepository,
            UpstreamCredentialRepository upstreamCredentialRepository,
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            SiteCapabilityTruthService siteCapabilityTruthService,
            ObjectMapper objectMapper) {
        this.distributedKeyQueryService = distributedKeyQueryService;
        this.gatewayFileRepository = gatewayFileRepository;
        this.gatewayFileBindingRepository = gatewayFileBindingRepository;
        this.gatewayAsyncResourceRepository = gatewayAsyncResourceRepository;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.upstreamSiteProfileRepository = upstreamSiteProfileRepository;
        this.siteCapabilitySnapshotRepository = siteCapabilitySnapshotRepository;
        this.siteCapabilityTruthService = siteCapabilityTruthService;
        this.objectMapper = objectMapper;
    }

    public static NonChatTargetResolutionService createDefault() {
        return new NonChatTargetResolutionService(null, null, null, null, null, null, null, null, new ObjectMapper());
    }

    public NonChatTargetResolution resolve(
            String distributedKeyPrefix,
            Long distributedKeyId,
            GatewayRequestSemantics semantics,
            Map<String, String> pathParams) {
        if (semantics == null) {
            return new NonChatTargetResolution(
                    RouteSelectionMode.CATALOG_SELECTION,
                    null,
                    "selection_mode=unknown",
                    List.of("未识别请求语义。")
            );
        }
        return switch (semantics.routeSelectionMode()) {
            case LOCAL_CATALOG -> new NonChatTargetResolution(
                    RouteSelectionMode.LOCAL_CATALOG,
                    null,
                    "local_catalog",
                    List.of()
            );
            case STORED_LINEAGE -> resolveStoredLineage(distributedKeyPrefix, distributedKeyId, semantics, pathParams);
            case DISTRIBUTED_TARGET -> resolveDistributedTarget(distributedKeyPrefix, distributedKeyId, semantics);
            case CATALOG_SELECTION -> new NonChatTargetResolution(
                    RouteSelectionMode.CATALOG_SELECTION,
                    null,
                    "catalog_selection",
                    List.of()
            );
        };
    }

    private NonChatTargetResolution resolveStoredLineage(
            String distributedKeyPrefix,
            Long distributedKeyId,
            GatewayRequestSemantics semantics,
            Map<String, String> pathParams) {
        return switch (semantics.operation()) {
            case FILE_GET, FILE_DELETE, FILE_CONTENT_GET -> resolveFileTarget(distributedKeyPrefix, distributedKeyId, pathParams.get("fileId"));
            case UPLOAD_GET, UPLOAD_PART_ADD, UPLOAD_COMPLETE, UPLOAD_CANCEL ->
                    resolveAsyncTarget(distributedKeyId, pathParams.get("uploadId"), GatewayAsyncResourceType.UPLOAD);
            case BATCH_GET, BATCH_CANCEL -> resolveAsyncTarget(distributedKeyId, pathParams.get("batchId"), GatewayAsyncResourceType.BATCH);
            case ANTHROPIC_MESSAGE_BATCH_GET, ANTHROPIC_MESSAGE_BATCH_CANCEL ->
                    resolveAsyncTarget(distributedKeyId, pathParams.get("messageBatchId"), GatewayAsyncResourceType.BATCH);
            case TUNING_GET, TUNING_CANCEL, TUNING_EVENTS_LIST, TUNING_CHECKPOINTS_LIST ->
                    resolveAsyncTarget(distributedKeyId, pathParams.get("jobId"), GatewayAsyncResourceType.TUNING);
            default -> new NonChatTargetResolution(
                    RouteSelectionMode.STORED_LINEAGE,
                    null,
                    "stored_lineage_unhandled",
                    List.of()
            );
        };
    }

    private NonChatTargetResolution resolveFileTarget(
            String distributedKeyPrefix,
            Long distributedKeyId,
            String fileId) {
        if (gatewayFileRepository == null || gatewayFileBindingRepository == null || fileId == null || fileId.isBlank()) {
            return new NonChatTargetResolution(
                    RouteSelectionMode.STORED_LINEAGE,
                    null,
                    "stored_lineage_file_missing",
                    List.of("未提供 fileId。")
            );
        }
        Optional<GatewayFileEntity> file = gatewayFileRepository.findByFileKeyAndDeletedFalse(fileId);
        if (file.isEmpty()) {
            return new NonChatTargetResolution(
                    RouteSelectionMode.STORED_LINEAGE,
                    null,
                    "stored_lineage_file_not_found",
                    List.of("未找到对应的文件对象。")
            );
        }
        GatewayFileEntity entity = file.get();
        if (distributedKeyId != null && !distributedKeyId.equals(entity.getDistributedKeyId())) {
            return new NonChatTargetResolution(
                    RouteSelectionMode.STORED_LINEAGE,
                    null,
                    "stored_lineage_file_mismatched_key",
                    List.of("当前 DistributedKey 与目标文件对象不匹配。")
            );
        }

        DistributedKeyView distributedKey = resolveDistributedKey(distributedKeyPrefix, entity.getDistributedKeyId());
        List<GatewayFileBindingEntity> bindings = gatewayFileBindingRepository
                .findAllByGatewayFileIdOrderByCreatedAtDesc(entity.getId());
        if (bindings.isEmpty()) {
            return new NonChatTargetResolution(
                    RouteSelectionMode.STORED_LINEAGE,
                    null,
                    "stored_lineage_file_local_only",
                    List.of()
            );
        }
        GatewayFileBindingEntity binding = chooseBinding(bindings, distributedKey).orElse(bindings.getFirst());
        CatalogCandidateView candidate = buildCandidate(binding.getCredentialId(), binding.getSiteProfileId());
        return new NonChatTargetResolution(
                RouteSelectionMode.STORED_LINEAGE,
                candidate,
                candidate == null ? "stored_lineage_file_local_only" : "stored_lineage_file_binding",
                candidate == null ? List.of() : List.of()
        );
    }

    private NonChatTargetResolution resolveAsyncTarget(
            Long distributedKeyId,
            String resourceKey,
            GatewayAsyncResourceType resourceType) {
        if (gatewayAsyncResourceRepository == null || resourceKey == null || resourceKey.isBlank()) {
            return new NonChatTargetResolution(
                    RouteSelectionMode.STORED_LINEAGE,
                    null,
                    "stored_lineage_async_missing",
                    List.of("未提供对象标识。")
            );
        }
        Optional<GatewayAsyncResourceEntity> entity = distributedKeyId == null
                ? gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDeletedFalse(resourceKey, resourceType)
                : gatewayAsyncResourceRepository.findByResourceKeyAndResourceTypeAndDistributedKeyIdAndDeletedFalse(resourceKey, resourceType, distributedKeyId);
        if (entity.isEmpty()) {
            return new NonChatTargetResolution(
                    RouteSelectionMode.STORED_LINEAGE,
                    null,
                    "stored_lineage_async_not_found",
                    List.of("未找到对应的对象资源。")
            );
        }

        ObjectNode metadata = readObject(entity.get().getMetadataJson());
        Long credentialId = metadata.hasNonNull("credential_id") ? metadata.path("credential_id").asLong() : null;
        Long siteProfileId = metadata.hasNonNull("site_profile_id") ? metadata.path("site_profile_id").asLong() : null;
        CatalogCandidateView candidate = buildCandidate(credentialId, siteProfileId);
        return new NonChatTargetResolution(
                RouteSelectionMode.STORED_LINEAGE,
                candidate,
                candidate == null ? "stored_lineage_async_local_only" : "stored_lineage_async_binding",
                List.of()
        );
    }

    private NonChatTargetResolution resolveDistributedTarget(
            String distributedKeyPrefix,
            Long distributedKeyId,
            GatewayRequestSemantics semantics) {
        DistributedKeyView distributedKey = resolveDistributedKey(distributedKeyPrefix, distributedKeyId);
        if (distributedKey == null || distributedKey.bindings().isEmpty()) {
            return new NonChatTargetResolution(
                    RouteSelectionMode.DISTRIBUTED_TARGET,
                    null,
                    "distributed_target_missing",
                    List.of("未找到可用的 DistributedKey 绑定。")
            );
        }

        List<CatalogCandidateView> candidates = distributedKey.bindings().stream()
                .map(binding -> buildCandidate(binding.credentialId(), null))
                .filter(java.util.Objects::nonNull)
                .toList();
        CatalogCandidateView preferred = candidates.stream()
                .filter(candidate -> siteCapabilityTruthService != null
                        && siteCapabilityTruthService.resolve(candidate, semantics).overallEffectiveLevel() != InteropCapabilityLevel.UNSUPPORTED)
                .findFirst()
                .orElse(candidates.getFirst());
        return new NonChatTargetResolution(
                RouteSelectionMode.DISTRIBUTED_TARGET,
                preferred,
                "distributed_target_binding",
                List.of()
        );
    }

    private DistributedKeyView resolveDistributedKey(String distributedKeyPrefix, Long distributedKeyId) {
        if (distributedKeyQueryService == null) {
            return null;
        }
        if (distributedKeyId != null) {
            return distributedKeyQueryService.findActiveById(distributedKeyId).orElse(null);
        }
        if (distributedKeyPrefix != null && !distributedKeyPrefix.isBlank()) {
            return distributedKeyQueryService.findActiveByKeyPrefix(distributedKeyPrefix).orElse(null);
        }
        return null;
    }

    private Optional<GatewayFileBindingEntity> chooseBinding(
            List<GatewayFileBindingEntity> bindings,
            DistributedKeyView distributedKey) {
        if (bindings == null || bindings.isEmpty()) {
            return Optional.empty();
        }
        List<Long> allowedCredentialIds = distributedKey == null
                ? List.of()
                : distributedKey.bindings().stream().map(binding -> binding.credentialId()).toList();
        return bindings.stream()
                .filter(binding -> "ACTIVE".equalsIgnoreCase(binding.getStatus()) || binding.getStatus() == null)
                .sorted(Comparator.comparing((GatewayFileBindingEntity binding) -> !allowedCredentialIds.contains(binding.getCredentialId()))
                        .thenComparing(GatewayFileBindingEntity::getCreatedAt, Comparator.reverseOrder()))
                .findFirst();
    }

    private CatalogCandidateView buildCandidate(Long credentialId, Long siteProfileIdHint) {
        if (credentialId == null || upstreamCredentialRepository == null) {
            return null;
        }
        Optional<UpstreamCredentialEntity> credential = upstreamCredentialRepository.findById(credentialId);
        if (credential.isEmpty()) {
            return null;
        }
        Long siteProfileId = siteProfileIdHint != null ? siteProfileIdHint : credential.get().getSiteProfileId();
        UpstreamSiteProfileEntity siteProfile = siteProfileId == null || upstreamSiteProfileRepository == null
                ? null
                : upstreamSiteProfileRepository.findById(siteProfileId).orElse(null);
        SiteCapabilitySnapshotEntity snapshot = siteProfile == null || siteCapabilitySnapshotRepository == null
                ? null
                : siteCapabilitySnapshotRepository.findBySiteProfile_Id(siteProfile.getId()).orElse(null);

        return new CatalogCandidateView(
                credential.get().getId(),
                credential.get().getCredentialName(),
                credential.get().getProviderType(),
                siteProfile == null ? null : siteProfile.getId(),
                siteProfile == null ? providerFamilyFor(credential.get().getProviderType()) : siteProfile.getProviderFamily(),
                siteProfile == null ? null : siteProfile.getSiteKind(),
                siteProfile == null ? null : siteProfile.getAuthStrategy(),
                siteProfile == null ? null : siteProfile.getPathStrategy(),
                siteProfile == null ? null : siteProfile.getErrorSchemaStrategy(),
                credential.get().getBaseUrl(),
                "resolved-target",
                "resolved-target",
                snapshot == null ? List.of() : snapshot.getSupportedProtocols(),
                true,
                true,
                true,
                snapshot != null && snapshot.isSupportsEmbeddings(),
                false,
                true,
                false,
                false,
                null,
                InteropCapabilityLevel.NATIVE
        );
    }

    private ProviderFamily providerFamilyFor(ProviderType providerType) {
        if (providerType == null) {
            return ProviderFamily.OPENAI;
        }
        return switch (providerType) {
            case OPENAI_DIRECT -> ProviderFamily.OPENAI;
            case OPENAI_COMPATIBLE -> ProviderFamily.OPENAI;
            case ANTHROPIC_DIRECT -> ProviderFamily.ANTHROPIC;
            case GEMINI_DIRECT -> ProviderFamily.GEMINI;
            case OLLAMA_DIRECT -> ProviderFamily.OLLAMA;
        };
    }

    private ObjectNode readObject(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            return node != null && node.isObject() ? (ObjectNode) node : objectMapper.createObjectNode();
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }
}
