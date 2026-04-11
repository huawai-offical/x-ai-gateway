package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ModelAliasRequest;
import com.prodigalgal.xaigateway.admin.api.ModelAliasPreviewRequest;
import com.prodigalgal.xaigateway.admin.api.ModelAliasPreviewResponse;
import com.prodigalgal.xaigateway.admin.api.ModelAliasResponse;
import com.prodigalgal.xaigateway.admin.api.ModelAliasRuleRequest;
import com.prodigalgal.xaigateway.admin.api.ModelAliasRuleResponse;
import com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryService;
import com.prodigalgal.xaigateway.gateway.core.catalog.ResolvedModelView;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.infra.persistence.entity.ModelAliasEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.ModelAliasRuleEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ModelAliasRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.ModelAliasRuleRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ModelAliasAdminService {

    private final ModelAliasRepository modelAliasRepository;
    private final ModelAliasRuleRepository modelAliasRuleRepository;
    private final ModelCatalogQueryService modelCatalogQueryService;

    public ModelAliasAdminService(
            ModelAliasRepository modelAliasRepository,
            ModelAliasRuleRepository modelAliasRuleRepository,
            ModelCatalogQueryService modelCatalogQueryService) {
        this.modelAliasRepository = modelAliasRepository;
        this.modelAliasRuleRepository = modelAliasRuleRepository;
        this.modelCatalogQueryService = modelCatalogQueryService;
    }

    @Transactional(readOnly = true)
    public List<ModelAliasResponse> list() {
        return modelAliasRepository.findAll().stream()
                .sorted(Comparator.comparing(ModelAliasEntity::getCreatedAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    public ModelAliasResponse create(ModelAliasRequest request) {
        ModelAliasEntity entity = new ModelAliasEntity();
        apply(entity, request);
        ModelAliasEntity saved = modelAliasRepository.save(entity);
        replaceRules(saved, request.rules());
        return toResponse(saved);
    }

    public ModelAliasResponse update(Long id, ModelAliasRequest request) {
        ModelAliasEntity entity = getRequired(id);
        apply(entity, request);
        ModelAliasEntity saved = modelAliasRepository.save(entity);
        replaceRules(saved, request.rules());
        return toResponse(saved);
    }

    public void delete(Long id) {
        ModelAliasEntity entity = getRequired(id);
        List<ModelAliasRuleEntity> rules = modelAliasRuleRepository
                .findAllByAliasIdAndEnabledTrueOrderByPriorityAscCreatedAtAsc(entity.getId());
        if (!rules.isEmpty()) {
            modelAliasRuleRepository.deleteAll(rules);
        }
        modelAliasRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public ModelAliasPreviewResponse preview(ModelAliasPreviewRequest request) {
        ResolvedModelView resolved = modelCatalogQueryService
                .resolveRequestedModel(request.requestedModel(), request.protocol().trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("当前模型目录中没有匹配到任何候选。"));

        return new ModelAliasPreviewResponse(
                request.requestedModel(),
                request.protocol().trim().toLowerCase(),
                resolved.alias(),
                resolved.publicModel(),
                resolved.resolvedModelKey(),
                resolved.candidates().size(),
                resolved.candidates()
        );
    }

    private ModelAliasEntity getRequired(Long id) {
        Optional<ModelAliasEntity> entity = modelAliasRepository.findById(id);
        if (entity.isEmpty()) {
            throw new IllegalArgumentException("未找到指定的模型别名。");
        }
        return entity.get();
    }

    private void apply(ModelAliasEntity entity, ModelAliasRequest request) {
        entity.setAliasName(request.aliasName().trim());
        entity.setAliasKey(ModelIdNormalizer.normalize(request.aliasName()));
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setDescription(blankToNull(request.description()));
    }

    private void replaceRules(ModelAliasEntity alias, List<ModelAliasRuleRequest> requests) {
        List<ModelAliasRuleEntity> existing = modelAliasRuleRepository
                .findAllByAliasIdAndEnabledTrueOrderByPriorityAscCreatedAtAsc(alias.getId());
        if (!existing.isEmpty()) {
            modelAliasRuleRepository.deleteAll(existing);
        }

        if (requests == null || requests.isEmpty()) {
            return;
        }

        List<ModelAliasRuleEntity> rules = requests.stream()
                .map(request -> toRuleEntity(alias, request))
                .toList();
        modelAliasRuleRepository.saveAll(rules);
    }

    private ModelAliasRuleEntity toRuleEntity(ModelAliasEntity alias, ModelAliasRuleRequest request) {
        ModelAliasRuleEntity entity = new ModelAliasRuleEntity();
        entity.setAlias(alias);
        entity.setProtocol(request.protocol().trim().toLowerCase());
        entity.setTargetModelName(request.targetModelName().trim());
        entity.setTargetModelKey(ModelIdNormalizer.normalize(request.targetModelName()));
        entity.setProviderType(request.providerType());
        entity.setBaseUrlPattern(blankToNull(request.baseUrlPattern()));
        entity.setPriority(request.priority() == null ? 100 : request.priority());
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setDescription(blankToNull(request.description()));
        return entity;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ModelAliasResponse toResponse(ModelAliasEntity entity) {
        List<ModelAliasRuleResponse> rules = modelAliasRuleRepository
                .findAllByAliasIdAndEnabledTrueOrderByPriorityAscCreatedAtAsc(entity.getId())
                .stream()
                .map(this::toRuleResponse)
                .toList();
        return new ModelAliasResponse(
                entity.getId(),
                entity.getAliasName(),
                entity.getAliasKey(),
                entity.isEnabled(),
                entity.getDescription(),
                rules,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ModelAliasRuleResponse toRuleResponse(ModelAliasRuleEntity entity) {
        return new ModelAliasRuleResponse(
                entity.getId(),
                entity.getProtocol(),
                entity.getTargetModelName(),
                entity.getTargetModelKey(),
                entity.getProviderType(),
                entity.getBaseUrlPattern(),
                entity.getPriority(),
                entity.isEnabled(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
