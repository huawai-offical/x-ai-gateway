package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.CostEstimateRequest;
import com.prodigalgal.xaigateway.admin.api.CostEstimateResponse;
import com.prodigalgal.xaigateway.admin.api.CostModelRequest;
import com.prodigalgal.xaigateway.admin.api.CostModelResponse;
import com.prodigalgal.xaigateway.admin.api.CostSummaryResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.CostModelEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserBalanceLedgerEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.CostModelRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserBalanceLedgerRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CostRoutingService {

    private static final long SAMPLE_INPUT_TOKENS = 1_000_000;
    private static final long SAMPLE_OUTPUT_TOKENS = 1_000_000;

    private final CostModelRepository costModelRepository;
    private final DistributedKeyRepository distributedKeyRepository;
    private final GatewayUserBalanceLedgerRepository balanceLedgerRepository;

    public CostRoutingService(
            CostModelRepository costModelRepository,
            DistributedKeyRepository distributedKeyRepository,
            GatewayUserBalanceLedgerRepository balanceLedgerRepository) {
        this.costModelRepository = costModelRepository;
        this.distributedKeyRepository = distributedKeyRepository;
        this.balanceLedgerRepository = balanceLedgerRepository;
    }

    @Transactional(readOnly = true)
    public List<CostModelResponse> listModels() {
        return costModelRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toModelResponse).toList();
    }

    public CostModelResponse saveModel(Long id, CostModelRequest request) {
        CostModelEntity entity = id == null
                ? new CostModelEntity()
                : costModelRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到成本模型。"));
        entity.setProviderType(required(request.providerType(), "providerType").toUpperCase(Locale.ROOT));
        entity.setModelName(required(request.modelName(), "modelName"));
        entity.setCurrency(defaultString(request.currency(), "USD").toUpperCase(Locale.ROOT));
        entity.setInputTokenMicros(nonNegative(request.inputTokenMicros(), 100L));
        entity.setOutputTokenMicros(nonNegative(request.outputTokenMicros(), 300L));
        entity.setCacheHitTokenMicros(nonNegative(request.cacheHitTokenMicros(), 20L));
        entity.setActive(request.active() == null || request.active());
        entity.setNotes(request.notes());
        return toModelResponse(costModelRepository.save(entity));
    }

    public void deleteModel(Long id) {
        costModelRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public CostEstimateResponse estimate(CostEstimateRequest request) {
        String providerType = required(request.providerType(), "providerType").toUpperCase(Locale.ROOT);
        String modelName = required(request.modelName(), "modelName");
        CostModelEntity model = costModelRepository
                .findFirstByProviderTypeAndModelNameAndActiveTrueOrderByUpdatedAtDesc(providerType, modelName)
                .orElseThrow(() -> new IllegalArgumentException("未找到可用成本模型。"));
        return estimateWithModel(
                model,
                tokens(request.inputTokens()),
                tokens(request.outputTokens()),
                tokens(request.cacheHitTokens()),
                resolveDistributedKey(request),
                request.userId(),
                nonNegativeOrNull(request.singleRequestBudgetMicros()));
    }

    @Transactional(readOnly = true)
    public CostSummaryResponse summary() {
        List<CostModelEntity> models = costModelRepository.findAllByOrderByCreatedAtDesc();
        List<CostEstimateResponse> distribution = models.stream()
                .filter(CostModelEntity::isActive)
                .map(model -> estimateWithModel(model, SAMPLE_INPUT_TOKENS, SAMPLE_OUTPUT_TOKENS, 0L, Optional.empty(), null, null))
                .toList();
        long totalMicros = distribution.stream().mapToLong(CostEstimateResponse::estimatedMicros).sum();
        String currency = distribution.isEmpty() ? "USD" : distribution.get(0).currency();
        return new CostSummaryResponse(
                models.size(),
                models.stream().filter(CostModelEntity::isActive).count(),
                currency,
                totalMicros,
                display(currency, totalMicros),
                distribution
        );
    }

    private CostEstimateResponse estimateWithModel(
            CostModelEntity model,
            long inputTokens,
            long outputTokens,
            long cacheHitTokens,
            Optional<DistributedKeyEntity> distributedKey,
            Long requestedUserId,
            Long singleRequestBudgetMicros) {
        long estimatedMicros = inputTokens * model.getInputTokenMicros()
                + outputTokens * model.getOutputTokenMicros()
                + cacheHitTokens * model.getCacheHitTokenMicros();
        DistributedKeyEntity key = distributedKey.orElse(null);
        Long ownerUserId = resolveOwnerUserId(key, requestedUserId);
        Long currentTokenCredits = ownerUserId == null ? null : currentTokenCredits(ownerUserId);
        Long budgetLimitMicros = key == null ? null : key.getBudgetLimitMicros();
        Integer budgetWindowSeconds = key == null ? null : key.getBudgetWindowSeconds();
        List<String> rejectionReasons = rejectionReasons(estimatedMicros, budgetLimitMicros, singleRequestBudgetMicros, currentTokenCredits);
        return new CostEstimateResponse(
                model.getProviderType(),
                model.getModelName(),
                model.getCurrency(),
                inputTokens,
                outputTokens,
                cacheHitTokens,
                estimatedMicros,
                display(model.getCurrency(), estimatedMicros),
                model.getInputTokenMicros(),
                model.getOutputTokenMicros(),
                model.getCacheHitTokenMicros(),
                key == null ? null : key.getId(),
                key == null ? null : key.getKeyName(),
                ownerUserId,
                budgetLimitMicros,
                budgetWindowSeconds,
                singleRequestBudgetMicros,
                currentTokenCredits,
                rejectionReasons.isEmpty(),
                rejectionReasons
        );
    }

    private Optional<DistributedKeyEntity> resolveDistributedKey(CostEstimateRequest request) {
        if (request.distributedKeyId() != null) {
            return distributedKeyRepository.findById(request.distributedKeyId());
        }
        String prefix = request.distributedKeyPrefix() == null ? "" : request.distributedKeyPrefix().trim();
        if (!prefix.isEmpty()) {
            return distributedKeyRepository.findByKeyPrefixAndActiveTrue(prefix);
        }
        return Optional.empty();
    }

    private Long resolveOwnerUserId(DistributedKeyEntity key, Long requestedUserId) {
        if (requestedUserId != null) {
            return requestedUserId;
        }
        if (key == null || key.getOwnerUser() == null) {
            return null;
        }
        return key.getOwnerUser().getId();
    }

    private Long currentTokenCredits(Long ownerUserId) {
        return balanceLedgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(ownerUserId)
                .map(GatewayUserBalanceLedgerEntity::getBalanceAfterTokenCredits)
                .orElse(null);
    }

    private List<String> rejectionReasons(
            long estimatedMicros,
            Long budgetLimitMicros,
            Long singleRequestBudgetMicros,
            Long currentTokenCredits) {
        List<String> reasons = new ArrayList<>();
        if (budgetLimitMicros != null && estimatedMicros > budgetLimitMicros) {
            reasons.add("超过分发 Key 预算上限。");
        }
        if (singleRequestBudgetMicros != null && estimatedMicros > singleRequestBudgetMicros) {
            reasons.add("超过单请求预算上限。");
        }
        if (currentTokenCredits != null && estimatedMicros > currentTokenCredits) {
            reasons.add("用户余额不足。");
        }
        return List.copyOf(reasons);
    }

    private String display(String currency, long micros) {
        BigDecimal amount = BigDecimal.valueOf(micros, 6).setScale(6, RoundingMode.HALF_UP);
        return currency + " " + amount;
    }

    private long tokens(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private long nonNegative(Long value, long fallback) {
        return value == null ? fallback : Math.max(0L, value);
    }

    private Long nonNegativeOrNull(Long value) {
        return value == null ? null : Math.max(0L, value);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空。");
        }
        return value.trim();
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private CostModelResponse toModelResponse(CostModelEntity entity) {
        return new CostModelResponse(
                entity.getId(),
                entity.getProviderType(),
                entity.getModelName(),
                entity.getCurrency(),
                entity.getInputTokenMicros(),
                entity.getOutputTokenMicros(),
                entity.getCacheHitTokenMicros(),
                entity.isActive(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
