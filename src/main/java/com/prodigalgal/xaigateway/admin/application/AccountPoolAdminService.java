package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AccountPoolRequest;
import com.prodigalgal.xaigateway.admin.api.AccountPoolResponse;
import com.prodigalgal.xaigateway.admin.api.CodexRuntimeBatchRecoveryItemResponse;
import com.prodigalgal.xaigateway.admin.api.CodexRuntimeBatchRecoveryRequest;
import com.prodigalgal.xaigateway.admin.api.CodexRuntimeBatchRecoveryResponse;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyAccountPoolBindingRequest;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyAccountPoolBindingResponse;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccountPoolBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountPoolEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountPoolBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountPoolRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class AccountPoolAdminService {
    public static final String DEFAULT_POOL_NAME = "default";

    private final UpstreamAccountPoolRepository upstreamAccountPoolRepository;
    private final UpstreamAccountRepository upstreamAccountRepository;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final DistributedKeyRepository distributedKeyRepository;
    private final DistributedKeyAccountPoolBindingRepository distributedKeyAccountPoolBindingRepository;
    private final SupportedModelCatalogService supportedModelCatalogService;
    private final OpsTimelineService opsTimelineService;
    private final ObjectMapper objectMapper;

    public AccountPoolAdminService(
            UpstreamAccountPoolRepository upstreamAccountPoolRepository,
            UpstreamAccountRepository upstreamAccountRepository,
            UpstreamCredentialRepository upstreamCredentialRepository,
            DistributedKeyRepository distributedKeyRepository,
            DistributedKeyAccountPoolBindingRepository distributedKeyAccountPoolBindingRepository,
            SupportedModelCatalogService supportedModelCatalogService) {
        this(
                upstreamAccountPoolRepository,
                upstreamAccountRepository,
                upstreamCredentialRepository,
                distributedKeyRepository,
                distributedKeyAccountPoolBindingRepository,
                supportedModelCatalogService,
                null,
                new ObjectMapper()
        );
    }

    @Autowired
    public AccountPoolAdminService(
            UpstreamAccountPoolRepository upstreamAccountPoolRepository,
            UpstreamAccountRepository upstreamAccountRepository,
            UpstreamCredentialRepository upstreamCredentialRepository,
            DistributedKeyRepository distributedKeyRepository,
            DistributedKeyAccountPoolBindingRepository distributedKeyAccountPoolBindingRepository,
            SupportedModelCatalogService supportedModelCatalogService,
            OpsTimelineService opsTimelineService,
            ObjectMapper objectMapper) {
        this.upstreamAccountPoolRepository = upstreamAccountPoolRepository;
        this.upstreamAccountRepository = upstreamAccountRepository;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.distributedKeyRepository = distributedKeyRepository;
        this.distributedKeyAccountPoolBindingRepository = distributedKeyAccountPoolBindingRepository;
        this.supportedModelCatalogService = supportedModelCatalogService;
        this.opsTimelineService = opsTimelineService;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public List<AccountPoolResponse> list() {
        ensureDefaultPool();
        return upstreamAccountPoolRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    public AccountPoolResponse get(Long id) {
        ensureDefaultPool();
        return toResponse(getRequired(id));
    }

    @Transactional(readOnly = true)
    public List<String> listSupportedModelCatalog(UpstreamAccountProviderType providerType) {
        ensureDefaultPool();
        return supportedModelCatalogService.listByUpstreamProvider(providerType);
    }

    public AccountPoolResponse create(AccountPoolRequest request) {
        validatePoolNameDuplicated(request.poolName(), null);
        UpstreamAccountPoolEntity entity = new UpstreamAccountPoolEntity();
        apply(entity, request);
        return toResponse(upstreamAccountPoolRepository.save(entity));
    }

    public AccountPoolResponse update(Long id, AccountPoolRequest request) {
        UpstreamAccountPoolEntity entity = getRequired(id);
        validateDefaultPoolUpdate(entity, request.poolName());
        validatePoolNameDuplicated(request.poolName(), id);
        apply(entity, request);
        return toResponse(upstreamAccountPoolRepository.save(entity));
    }

    public AccountPoolResponse toggle(Long id, boolean active) {
        UpstreamAccountPoolEntity entity = getRequired(id);
        entity.setActive(active);
        return toResponse(upstreamAccountPoolRepository.save(entity));
    }

    public void delete(Long id) {
        UpstreamAccountPoolEntity entity = getRequired(id);
        if (isDefaultPool(entity)) {
            throw new IllegalArgumentException("default 账号池为系统内置账号池，不允许删除。");
        }

        Set<Long> affectedDistributedKeyIds = new HashSet<>();
        distributedKeyAccountPoolBindingRepository.findAllByPool_Id(id)
                .forEach(binding -> affectedDistributedKeyIds.add(binding.getDistributedKey().getId()));

        upstreamAccountRepository.clearPoolReferenceByPoolId(id);
        upstreamCredentialRepository.clearPoolReferenceByPoolId(id);
        distributedKeyAccountPoolBindingRepository.deleteAllByPool_Id(id);

        for (Long distributedKeyId : affectedDistributedKeyIds) {
            distributedKeyRepository.findById(distributedKeyId).ifPresent(distributedKey -> {
                if (!distributedKey.isActive()) {
                    return;
                }
                long activeBindingCount =
                        distributedKeyAccountPoolBindingRepository.countByDistributedKey_IdAndActiveTrue(distributedKeyId);
                if (activeBindingCount == 0) {
                    distributedKey.setActive(false);
                    distributedKeyRepository.save(distributedKey);
                }
            });
        }

        upstreamAccountPoolRepository.delete(entity);
    }

    public UpstreamAccountPoolEntity ensureDefaultPool() {
        return upstreamAccountPoolRepository.findByPoolNameIgnoreCase(DEFAULT_POOL_NAME)
                .orElseGet(() -> {
                    UpstreamAccountPoolEntity entity = new UpstreamAccountPoolEntity();
                    entity.setPoolName(DEFAULT_POOL_NAME);
                    entity.setProviderType(com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType.OPENAI_OAUTH);
                    entity.setSupportedModels(List.of());
                    entity.setSupportedProtocols(List.of("openai", "responses"));
                    entity.setAllowedClientFamilies(List.of("GENERIC_OPENAI", "CODEX"));
                    entity.setDescription("系统默认账号池");
                    entity.setActive(true);
                    return upstreamAccountPoolRepository.save(entity);
                });
    }

    public DistributedKeyAccountPoolBindingResponse bindDistributedKey(Long poolId, DistributedKeyAccountPoolBindingRequest request) {
        UpstreamAccountPoolEntity pool = getRequired(poolId);
        DistributedKeyEntity distributedKey = distributedKeyRepository.findById(request.distributedKeyId())
                .orElseThrow(() -> new IllegalArgumentException("未找到 DistributedKey。"));
        DistributedKeyAccountPoolBindingEntity entity = new DistributedKeyAccountPoolBindingEntity();
        entity.setPool(pool);
        entity.setDistributedKey(distributedKey);
        entity.setProviderType(request.providerType());
        entity.setPriority(request.priority() == null ? 100 : request.priority());
        entity.setActive(request.active() == null || request.active());
        return toBindingResponse(distributedKeyAccountPoolBindingRepository.save(entity));
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public CodexRuntimeBatchRecoveryResponse codexRuntimeBatchRecovery(
            Long poolId,
            CodexRuntimeBatchRecoveryRequest request,
            boolean executeEndpoint) {
        UpstreamAccountPoolEntity pool = getRequired(poolId);
        CodexRuntimeBatchRecoveryRequest effectiveRequest = request == null
                ? new CodexRuntimeBatchRecoveryRequest(false, false, List.of(), null)
                : request;
        boolean execute = executeEndpoint || Boolean.TRUE.equals(effectiveRequest.execute());
        boolean refreshQuota = Boolean.TRUE.equals(effectiveRequest.refreshQuota());
        Set<Long> requestedAccountIds = effectiveRequest.accountIds() == null
                ? Set.of()
                : new LinkedHashSet<>(effectiveRequest.accountIds());
        List<UpstreamAccountEntity> accounts = upstreamAccountRepository.findAllByPool_IdOrderByCreatedAtDesc(poolId).stream()
                .filter(account -> requestedAccountIds.isEmpty() || requestedAccountIds.contains(account.getId()))
                .filter(account -> isCodexRuntimeAccount(account, pool))
                .toList();

        Instant generatedAt = Instant.now();
        List<CodexRuntimeBatchRecoveryItemResponse> items = new ArrayList<>();
        for (UpstreamAccountEntity account : accounts) {
            CodexRuntimeBatchRecoveryItemResponse classified = classifyRuntimeRecoveryCandidate(account);
            if (!execute || !"safe".equals(classified.category())) {
                items.add(new CodexRuntimeBatchRecoveryItemResponse(
                        classified.accountId(),
                        classified.accountName(),
                        classified.category(),
                        classified.status(),
                        classified.reason(),
                        classified.recommendedAction(),
                        classified.errorSummary(),
                        execute && !"safe".equals(classified.category()) ? "SKIPPED" : "PREFLIGHT",
                        null
                ));
                continue;
            }

            try {
                resetRuntimeState(account, refreshQuota, generatedAt);
                upstreamAccountRepository.save(account);
                items.add(new CodexRuntimeBatchRecoveryItemResponse(
                        classified.accountId(),
                        classified.accountName(),
                        classified.category(),
                        "可路由",
                        classified.reason(),
                        refreshQuota ? "已重置运行态，并标记本次批量恢复触发 quota 复查。" : "已重置运行态。",
                        classified.errorSummary(),
                        "EXECUTED",
                        null
                ));
            } catch (RuntimeException exception) {
                items.add(new CodexRuntimeBatchRecoveryItemResponse(
                        classified.accountId(),
                        classified.accountName(),
                        classified.category(),
                        classified.status(),
                        classified.reason(),
                        classified.recommendedAction(),
                        classified.errorSummary(),
                        "FAILED",
                        redactRuntimeError(exception.getMessage())
                ));
            }
        }

        CodexRuntimeBatchRecoveryResponse.Totals totals = new CodexRuntimeBatchRecoveryResponse.Totals(
                items.size(),
                countCategory(items, "safe"),
                countCategory(items, "blocked"),
                countCategory(items, "alreadyReady"),
                countExecution(items, "EXECUTED"),
                countExecution(items, "FAILED"),
                countExecution(items, "SKIPPED")
        );
        var auditEvent = recordBatchRecoveryEvent(pool, execute, refreshQuota, effectiveRequest.reason(), totals, items, generatedAt);
        return new CodexRuntimeBatchRecoveryResponse(
                "codex-runtime-recovery",
                generatedAt,
                !execute,
                execute,
                refreshQuota,
                totals,
                items,
                auditEvent == null ? null : auditEvent.id(),
                auditEvent == null ? null : auditEvent.title()
        );
    }

    private UpstreamAccountPoolEntity getRequired(Long id) {
        return upstreamAccountPoolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的账号池。"));
    }

    private void apply(UpstreamAccountPoolEntity entity, AccountPoolRequest request) {
        entity.setPoolName(request.poolName().trim());
        entity.setProviderType(request.providerType());
        entity.setSupportedModels(supportedModelCatalogService.normalize(request.supportedModels()));
        entity.setSupportedProtocols(request.supportedProtocols() == null ? List.of() : request.supportedProtocols());
        entity.setAllowedClientFamilies(request.allowedClientFamilies() == null ? List.of() : request.allowedClientFamilies());
        entity.setDescription(request.description());
        entity.setActive(request.active() == null || request.active());
    }

    private boolean isCodexRuntimeAccount(UpstreamAccountEntity account, UpstreamAccountPoolEntity pool) {
        return account.getProviderType() == UpstreamAccountProviderType.CODEX_OAUTH
                || pool.getProviderType() == UpstreamAccountProviderType.CODEX_OAUTH
                || containsIgnoreCase(pool.getAllowedClientFamilies(), "CODEX");
    }

    private CodexRuntimeBatchRecoveryItemResponse classifyRuntimeRecoveryCandidate(UpstreamAccountEntity account) {
        String status = runtimeStatusLabel(account);
        String errorSummary = redactRuntimeError(account.getLastErrorMessage());
        if (!account.isActive()) {
            return runtimeRecoveryItem(
                    account,
                    "blocked",
                    status,
                    "账号已停用，批量恢复不会自动启用账号。",
                    "人工确认账号来源和授权状态后，再单独启用并恢复。",
                    errorSummary
            );
        }
        if (isSecurityBlockedRuntimeError(account.getLastErrorMessage())) {
            return runtimeRecoveryItem(
                    account,
                    "blocked",
                    status,
                    "最近错误包含权限、策略、安全或禁用语义，批量恢复前需要人工复核。",
                    "人工核验账号授权、组织策略和 auth.json 来源后再单独处理。",
                    errorSummary
            );
        }

        List<String> recoveryReasons = new ArrayList<>();
        if (account.isFrozen()) {
            recoveryReasons.add("账号已隔离");
        }
        if (account.getCooldownUntil() != null) {
            recoveryReasons.add("冷却至 " + account.getCooldownUntil());
        }
        if (!account.isHealthy()) {
            recoveryReasons.add("健康状态异常");
        }
        if (account.getRefreshFailureCount() > 0) {
            recoveryReasons.add("刷新失败 " + account.getRefreshFailureCount() + " 次");
        }
        if (isFailedRefreshStatus(account.getRefreshStatus())) {
            recoveryReasons.add("刷新状态 " + account.getRefreshStatus());
        }

        if (!recoveryReasons.isEmpty()) {
            return runtimeRecoveryItem(
                    account,
                    "safe",
                    status,
                    String.join("；", recoveryReasons),
                    "可按批量恢复策略重置运行态、解除隔离并重新进入路由候选。",
                    errorSummary
            );
        }

        return runtimeRecoveryItem(
                account,
                "alreadyReady",
                status,
                "账号当前健康、未隔离且未处于冷却。",
                "无需批量恢复。",
                errorSummary
        );
    }

    private CodexRuntimeBatchRecoveryItemResponse runtimeRecoveryItem(
            UpstreamAccountEntity account,
            String category,
            String status,
            String reason,
            String recommendedAction,
            String errorSummary) {
        return new CodexRuntimeBatchRecoveryItemResponse(
                account.getId(),
                account.getAccountName(),
                category,
                status,
                reason,
                recommendedAction,
                errorSummary,
                null,
                null
        );
    }

    private void resetRuntimeState(UpstreamAccountEntity account, boolean refreshQuota, Instant now) {
        account.setFrozen(false);
        account.setHealthy(true);
        account.setLastErrorMessage(null);
        account.setRefreshFailureCount(0);
        account.setCooldownUntil(null);
        account.setNextRefreshAfter(null);
        if (account.getRefreshStatus() == null || account.getRefreshStatus().isBlank()
                || isFailedRefreshStatus(account.getRefreshStatus())) {
            account.setRefreshStatus("READY");
        }
        if (refreshQuota) {
            account.setLastRefreshAt(now);
            account.setLastRefreshResultJson("{\"status\":\"READY\",\"source\":\"codex-runtime-batch-recovery\",\"externalQuotaRefresh\":\"deferred\"}");
        }
    }

    private com.prodigalgal.xaigateway.admin.api.OpsSystemEventResponse recordBatchRecoveryEvent(
            UpstreamAccountPoolEntity pool,
            boolean execute,
            boolean refreshQuota,
            String reason,
            CodexRuntimeBatchRecoveryResponse.Totals totals,
            List<CodexRuntimeBatchRecoveryItemResponse> items,
            Instant occurredAt) {
        if (opsTimelineService == null) {
            return null;
        }
        String severity = totals.failed() > 0 || totals.blocked() > 0 ? "WARNING" : "INFO";
        String title = execute ? "Codex Runtime 批量恢复执行" : "Codex Runtime 批量恢复预检";
        return opsTimelineService.recordEvent(
                "CODEX_RUNTIME_BATCH_RECOVERY",
                severity,
                "console",
                "ACCOUNT_POOL",
                "account-pool:" + pool.getId(),
                title,
                writeJson(Map.of(
                        "poolId", pool.getId() == null ? -1L : pool.getId(),
                        "poolName", pool.getPoolName() == null ? "" : pool.getPoolName(),
                        "execute", execute,
                        "refreshQuota", refreshQuota,
                        "reason", reason == null || reason.isBlank() ? "manual-console" : reason.trim(),
                        "totals", totals,
                        "items", items.stream()
                                .map(item -> Map.of(
                                        "accountId", item.accountId() == null ? -1L : item.accountId(),
                                        "category", item.category(),
                                        "executionStatus", item.executionStatus() == null ? "" : item.executionStatus(),
                                        "reason", item.reason()
                                ))
                                .toList()
                )),
                occurredAt
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{\"serialization\":\"failed\"}";
        }
    }

    private int countCategory(List<CodexRuntimeBatchRecoveryItemResponse> items, String category) {
        return (int) items.stream().filter(item -> category.equals(item.category())).count();
    }

    private int countExecution(List<CodexRuntimeBatchRecoveryItemResponse> items, String executionStatus) {
        return (int) items.stream().filter(item -> executionStatus.equals(item.executionStatus())).count();
    }

    private boolean isFailedRefreshStatus(String refreshStatus) {
        return "FAILED".equalsIgnoreCase(refreshStatus) || "QUOTA_FAILED".equalsIgnoreCase(refreshStatus);
    }

    private String runtimeStatusLabel(UpstreamAccountEntity account) {
        if (account.isFrozen()) {
            return "已隔离";
        }
        if (account.getCooldownUntil() != null) {
            return "冷却中";
        }
        if (!account.isHealthy()) {
            return "异常";
        }
        if (isFailedRefreshStatus(account.getRefreshStatus())) {
            return "刷新失败";
        }
        return "可路由";
    }

    private boolean isSecurityBlockedRuntimeError(String message) {
        return message != null
                && message.toLowerCase(Locale.ROOT)
                        .matches(".*(policy|permission|security|forbidden|disabled|revoked|unauthorized|not\\s+allowed).*");
    }

    private String redactRuntimeError(String message) {
        if (message == null || message.isBlank()) {
            return "无";
        }
        String sanitized = message
                .replaceAll("(sk-[A-Za-z0-9_-]{8})[A-Za-z0-9_-]+", "$1***")
                .replaceAll("Bearer\\s+[A-Za-z0-9._-]+", "Bearer ***");
        return sanitized.substring(0, Math.min(160, sanitized.length()));
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        if (values == null || target == null) {
            return false;
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .anyMatch(target.trim().toUpperCase(Locale.ROOT)::equals);
    }

    private AccountPoolResponse toResponse(UpstreamAccountPoolEntity entity) {
        long oauthCount = upstreamAccountRepository.countByPool_Id(entity.getId());
        long apiCredentialCount = upstreamCredentialRepository.countByPoolIdAndDeletedFalse(entity.getId());
        return new AccountPoolResponse(
                entity.getId(),
                entity.getPoolName(),
                entity.getProviderType(),
                entity.getSupportedModels(),
                entity.getSupportedProtocols(),
                entity.getAllowedClientFamilies(),
                entity.getDescription(),
                isDefaultPool(entity),
                oauthCount,
                apiCredentialCount,
                oauthCount + apiCredentialCount,
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private DistributedKeyAccountPoolBindingResponse toBindingResponse(DistributedKeyAccountPoolBindingEntity entity) {
        return new DistributedKeyAccountPoolBindingResponse(
                entity.getId(),
                entity.getDistributedKey().getId(),
                entity.getPool().getId(),
                entity.getProviderType(),
                entity.getPriority(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private boolean isDefaultPool(UpstreamAccountPoolEntity entity) {
        return entity.getPoolName() != null && DEFAULT_POOL_NAME.equalsIgnoreCase(entity.getPoolName().trim());
    }

    private void validateDefaultPoolUpdate(UpstreamAccountPoolEntity entity, String nextPoolName) {
        if (!isDefaultPool(entity)) {
            return;
        }
        if (nextPoolName == null || !DEFAULT_POOL_NAME.equalsIgnoreCase(nextPoolName.trim())) {
            throw new IllegalArgumentException("default 账号池为系统内置账号池，不允许修改名称。");
        }
    }

    private void validatePoolNameDuplicated(String poolName, Long currentId) {
        String normalized = poolName == null ? "" : poolName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("账号池名称不能为空。");
        }
        upstreamAccountPoolRepository.findByPoolNameIgnoreCase(normalized).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new IllegalArgumentException("账号池名称已存在，请使用其他名称。");
            }
        });
    }
}
