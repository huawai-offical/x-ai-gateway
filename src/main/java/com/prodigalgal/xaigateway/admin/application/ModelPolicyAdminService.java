package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ModelPolicyCandidatePreviewResponse;
import com.prodigalgal.xaigateway.admin.api.ModelPolicyConflictResponse;
import com.prodigalgal.xaigateway.admin.api.ModelPolicyPreviewRequest;
import com.prodigalgal.xaigateway.admin.api.ModelPolicyPreviewResponse;
import com.prodigalgal.xaigateway.admin.api.ModelPolicyRequest;
import com.prodigalgal.xaigateway.admin.api.ModelPolicyResponse;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyResolver;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.infra.persistence.entity.ModelPolicyEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ModelPolicyRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class ModelPolicyAdminService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ModelPolicyRepository modelPolicyRepository;
    private final GatewayRouteSelectionService gatewayRouteSelectionService;
    private final ModelPolicyResolver modelPolicyResolver;
    private final ObjectMapper objectMapper;

    public ModelPolicyAdminService(
            ModelPolicyRepository modelPolicyRepository,
            GatewayRouteSelectionService gatewayRouteSelectionService,
            ModelPolicyResolver modelPolicyResolver,
            ObjectMapper objectMapper) {
        this.modelPolicyRepository = modelPolicyRepository;
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.modelPolicyResolver = modelPolicyResolver;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ModelPolicyResponse> list() {
        return modelPolicyRepository.findAll().stream()
                .sorted(Comparator.comparing(ModelPolicyEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    public ModelPolicyResponse create(ModelPolicyRequest request) {
        ModelPolicyEntity entity = new ModelPolicyEntity();
        apply(entity, request);
        return toResponse(modelPolicyRepository.save(entity));
    }

    public ModelPolicyResponse update(Long id, ModelPolicyRequest request) {
        ModelPolicyEntity entity = modelPolicyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定模型策略。"));
        apply(entity, request);
        return toResponse(modelPolicyRepository.save(entity));
    }

    public void delete(Long id) {
        ModelPolicyEntity entity = modelPolicyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定模型策略。"));
        modelPolicyRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public ModelPolicyPreviewResponse preview(ModelPolicyPreviewRequest request) {
        RouteSelectionResult result = gatewayRouteSelectionService.select(new RouteSelectionRequest(
                request.distributedKeyPrefix(),
                request.protocol(),
                request.requestPath(),
                request.requestedModel(),
                request.requestBody(),
                request.clientFamily() == null ? GatewayClientFamily.GENERIC_OPENAI : GatewayClientFamily.from(request.clientFamily()),
                false
        ));
        return new ModelPolicyPreviewResponse(
                result.requestedModel(),
                result.publicModel(),
                result.modelGroup(),
                result.resolvedModelKey(),
                result.protocol(),
                result.governanceNotes(),
                result.candidateEvaluations().stream()
                        .map(ModelPolicyCandidatePreviewResponse::from)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public List<ModelPolicyConflictResponse> conflicts() {
        return modelPolicyResolver.detectConflicts().stream()
                .map(conflict -> new ModelPolicyConflictResponse(
                        conflict.severity(),
                        conflict.code(),
                        conflict.message(),
                        conflict.policyId()
                ))
                .toList();
    }

    private void apply(ModelPolicyEntity entity, ModelPolicyRequest request) {
        entity.setScopeType(request.scopeType());
        entity.setScopeId(request.scopeId());
        entity.setScopeRef(blankToNull(request.scopeRef()) == null ? null : request.scopeRef().trim().toLowerCase(Locale.ROOT));
        entity.setPolicyKind(normalizeKind(request.policyKind(), request.deny()));
        entity.setPublicModel(request.publicModel().trim());
        entity.setPublicModelKey(ModelIdNormalizer.normalize(request.publicModel()));
        entity.setUpstreamModel(blankToNull(request.upstreamModel()));
        entity.setUpstreamModelKey(ModelIdNormalizer.normalize(blankToNull(request.upstreamModel())));
        entity.setModelFamily(blankToNull(request.modelFamily()));
        entity.setSupportedProtocols(request.supportedProtocols() == null ? List.of() : request.supportedProtocols().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList());
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setDeny(Boolean.TRUE.equals(request.deny()) || "DENY".equalsIgnoreCase(entity.getPolicyKind()));
        entity.setPriority(request.priority() == null ? 100 : request.priority());
        entity.setWeight(request.weight() == null ? 100 : request.weight());
        entity.setCapabilityJson(writeJson(request.capability()));
        entity.setRequestOverridesJson(writeJson(request.requestOverrides()));
        entity.setResponseOverridesJson(writeJson(request.responseOverrides()));
        entity.setRuntimePolicyJson(writeJson(request.runtimePolicy()));
        entity.setMappingSource(blankToNull(request.mappingSource()) == null ? "manual" : request.mappingSource().trim().toLowerCase(Locale.ROOT));
        entity.setDescription(blankToNull(request.description()));
    }

    private String normalizeKind(String value, Boolean deny) {
        if (Boolean.TRUE.equals(deny)) {
            return "DENY";
        }
        if (value == null || value.isBlank()) {
            return "ALLOW";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private ModelPolicyResponse toResponse(ModelPolicyEntity entity) {
        return new ModelPolicyResponse(
                entity.getId(),
                entity.getScopeType(),
                entity.getScopeId(),
                entity.getScopeRef(),
                entity.getPolicyKind(),
                entity.getPublicModel(),
                entity.getPublicModelKey(),
                entity.getUpstreamModel(),
                entity.getUpstreamModelKey(),
                entity.getModelFamily(),
                entity.getSupportedProtocols(),
                entity.isEnabled(),
                entity.isDeny(),
                entity.getPriority(),
                entity.getWeight(),
                readJson(entity.getCapabilityJson()),
                readJson(entity.getRequestOverridesJson()),
                readJson(entity.getResponseOverridesJson()),
                readJson(entity.getRuntimePolicyJson()),
                entity.getMappingSource(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String writeJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("模型策略 JSON 字段无法序列化。", exception);
        }
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of("invalidJson", true);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
