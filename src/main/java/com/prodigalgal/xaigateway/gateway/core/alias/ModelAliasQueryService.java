package com.prodigalgal.xaigateway.gateway.core.alias;

import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.infra.persistence.entity.ModelAliasEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.ModelAliasRuleEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ModelAliasRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.ModelAliasRuleRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ModelAliasQueryService {

    private final ModelAliasRepository modelAliasRepository;
    private final ModelAliasRuleRepository modelAliasRuleRepository;

    public ModelAliasQueryService(
            ModelAliasRepository modelAliasRepository,
            ModelAliasRuleRepository modelAliasRuleRepository) {
        this.modelAliasRepository = modelAliasRepository;
        this.modelAliasRuleRepository = modelAliasRuleRepository;
    }

    public Optional<ModelAliasView> findEnabledAlias(String aliasName) {
        String aliasKey = ModelIdNormalizer.normalize(aliasName);
        return modelAliasRepository.findByAliasKeyAndEnabledTrue(aliasKey)
                .map(this::toView);
    }

    public List<ModelAliasView> listEnabledAliases() {
        return modelAliasRepository.findAllByEnabledTrueOrderByAliasNameAsc().stream()
                .map(this::toView)
                .toList();
    }

    private ModelAliasView toView(ModelAliasEntity alias) {
        List<ModelAliasRuleView> rules = modelAliasRuleRepository
                .findAllByAliasIdAndEnabledTrueOrderByPriorityAscCreatedAtAsc(alias.getId())
                .stream()
                .map(this::toRuleView)
                .toList();

        return new ModelAliasView(alias.getId(), alias.getAliasName(), alias.getAliasKey(), rules);
    }

    private ModelAliasRuleView toRuleView(ModelAliasRuleEntity rule) {
        return new ModelAliasRuleView(
                rule.getId(),
                rule.getProtocol(),
                rule.getTargetModelName(),
                rule.getTargetModelKey(),
                rule.getProviderType(),
                rule.getBaseUrlPattern(),
                rule.getPriority()
        );
    }
}
