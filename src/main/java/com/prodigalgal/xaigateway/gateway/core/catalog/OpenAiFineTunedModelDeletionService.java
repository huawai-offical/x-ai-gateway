package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.infra.config.web.ApiResourceNotFoundException;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayAsyncResourceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@Transactional
public class OpenAiFineTunedModelDeletionService {

    private final GatewayAsyncResourceRepository gatewayAsyncResourceRepository;
    private final ModelCatalogQueryService modelCatalogQueryService;
    private final FineTunedModelRegistrationService fineTunedModelRegistrationService;
    private final ObjectMapper objectMapper;

    public OpenAiFineTunedModelDeletionService(
            GatewayAsyncResourceRepository gatewayAsyncResourceRepository,
            ModelCatalogQueryService modelCatalogQueryService,
            FineTunedModelRegistrationService fineTunedModelRegistrationService,
            ObjectMapper objectMapper) {
        this.gatewayAsyncResourceRepository = gatewayAsyncResourceRepository;
        this.modelCatalogQueryService = modelCatalogQueryService;
        this.fineTunedModelRegistrationService = fineTunedModelRegistrationService;
        this.objectMapper = objectMapper;
    }

    public DeletedFineTunedModelView deleteRegisteredFineTunedModel(
            DistributedKeyView distributedKey,
            String modelId) {
        if (distributedKey == null) {
            throw new IllegalArgumentException("DistributedKey 不能为空。");
        }
        String requestedModelId = requireModelId(modelId);
        String requestedModelKey = ModelIdNormalizer.normalize(requestedModelId);

        GatewayAsyncResourceEntity entity = findRegisteredTuning(distributedKey.id(), requestedModelKey)
                .orElse(null);
        if (entity == null) {
            if (modelCatalogQueryService.findAccessiblePublicModel(distributedKey, "openai", requestedModelId).isPresent()) {
                throw new IllegalArgumentException("只能删除通过本 gateway fine-tuning/import 登记的 fine-tuned model，公共模型不能删除。");
            }
            throw new ApiResourceNotFoundException("未找到可删除的 fine-tuned model。");
        }

        ObjectNode metadata = readObject(entity.getMetadataJson());
        String registeredModelKey = text(metadata, "registered_model_key");
        List<String> aliases = registeredAliases(metadata);
        FineTunedModelRegistrationService.CleanupResult cleanupResult = fineTunedModelRegistrationService.unregister(
                longValue(metadata, "site_profile_id"),
                registeredModelKey,
                aliases,
                entity.getResourceKey()
        );

        metadata.remove("registered_model_key");
        metadata.remove("registered_model_name");
        metadata.remove("registered_alias_key");
        metadata.remove("registered_aliases");
        metadata.put("model_delete_requested_id", requestedModelId);
        metadata.put("model_deleted_at", Instant.now().getEpochSecond());
        appendEvent(metadata, "model_deleted", entity.getStatus());
        entity.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(entity);

        return new DeletedFineTunedModelView(
                requestedModelId,
                entity.getResourceKey(),
                cleanupResult.removedCapabilities(),
                cleanupResult.disabledAliases()
        );
    }

    private Optional<GatewayAsyncResourceEntity> findRegisteredTuning(Long distributedKeyId, String requestedModelKey) {
        return gatewayAsyncResourceRepository
                .findAllByDistributedKeyIdAndResourceTypeAndDeletedFalse(distributedKeyId, GatewayAsyncResourceType.TUNING)
                .stream()
                .filter(entity -> registeredModelCandidates(readObject(entity.getMetadataJson())).contains(requestedModelKey))
                .findFirst();
    }

    private Set<String> registeredModelCandidates(ObjectNode metadata) {
        Set<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, text(metadata, "registered_model_key"));
        addCandidate(candidates, text(metadata, "registered_model_name"));
        addCandidate(candidates, text(metadata, "registered_alias_key"));
        JsonNode aliases = metadata.path("registered_aliases");
        if (aliases.isArray()) {
            aliases.forEach(alias -> addCandidate(candidates, alias.asText(null)));
        }
        return candidates;
    }

    private void addCandidate(Set<String> candidates, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        candidates.add(ModelIdNormalizer.normalize(value));
    }

    private List<String> registeredAliases(ObjectNode metadata) {
        List<String> aliases = new ArrayList<>();
        JsonNode aliasNode = metadata.path("registered_aliases");
        if (aliasNode.isArray()) {
            aliasNode.forEach(item -> {
                String alias = item.asText(null);
                if (alias != null && !alias.isBlank()) {
                    aliases.add(alias);
                }
            });
        }
        if (aliases.isEmpty()) {
            String alias = text(metadata, "registered_alias_key");
            if (alias != null && !alias.isBlank()) {
                aliases.add(alias);
            }
        }
        return List.copyOf(aliases);
    }

    private String requireModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("model 不能为空。");
        }
        return modelId.trim();
    }

    private ObjectNode readObject(String json) {
        try {
            JsonNode node = json == null || json.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(json);
            return node instanceof ObjectNode objectNode ? objectNode : objectMapper.createObjectNode();
        } catch (JacksonException exception) {
            throw new IllegalStateException("解析 fine-tuned model lineage metadata 失败。", exception);
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化 fine-tuned model lineage metadata 失败。", exception);
        }
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

    private Long longValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        return value.isNumber() ? value.asLong() : null;
    }

    private void appendEvent(ObjectNode metadata, String eventType, String status) {
        metadata.withArray("events").addObject()
                .put("type", eventType)
                .put("status", status)
                .put("at", Instant.now().getEpochSecond());
    }

    public record DeletedFineTunedModelView(
            String modelId,
            String tuningResourceKey,
            int removedCapabilities,
            int disabledAliases
    ) {
    }
}
