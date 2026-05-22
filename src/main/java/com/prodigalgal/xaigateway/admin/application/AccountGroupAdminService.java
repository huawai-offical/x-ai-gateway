package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AccountGroupRequest;
import com.prodigalgal.xaigateway.admin.api.AccountGroupResponse;
import com.prodigalgal.xaigateway.admin.api.CodexRuntimeBatchRecoveryItemResponse;
import com.prodigalgal.xaigateway.admin.api.CodexRuntimeBatchRecoveryRequest;
import com.prodigalgal.xaigateway.admin.api.CodexRuntimeBatchRecoveryResponse;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyAccountGroupBindingRequest;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyAccountGroupBindingResponse;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccountGroupBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountGroupBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountGroupRepository;
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
public class AccountGroupAdminService {
    public static final String DEFAULT_GROUP_NAME = "default";

    private final UpstreamAccountGroupRepository upstreamAccountGroupRepository;
    private final UpstreamAccountRepository upstreamAccountRepository;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final DistributedKeyRepository distributedKeyRepository;
    private final DistributedKeyAccountGroupBindingRepository distributedKeyAccountGroupBindingRepository;
    private final SupportedModelCatalogService supportedModelCatalogService;
    private final OpsTimelineService opsTimelineService;
    private final ObjectMapper objectMapper;

    public AccountGroupAdminService(
            UpstreamAccountGroupRepository upstreamAccountGroupRepository,
            UpstreamAccountRepository upstreamAccountRepository,
            UpstreamCredentialRepository upstreamCredentialRepository,
            DistributedKeyRepository distributedKeyRepository,
            DistributedKeyAccountGroupBindingRepository distributedKeyAccountGroupBindingRepository,
            SupportedModelCatalogService supportedModelCatalogService) {
        this(
                upstreamAccountGroupRepository,
                upstreamAccountRepository,
                upstreamCredentialRepository,
                distributedKeyRepository,
                distributedKeyAccountGroupBindingRepository,
                supportedModelCatalogService,
                null,
                new ObjectMapper()
        );
    }

    @Autowired
    public AccountGroupAdminService(
            UpstreamAccountGroupRepository upstreamAccountGroupRepository,
            UpstreamAccountRepository upstreamAccountRepository,
            UpstreamCredentialRepository upstreamCredentialRepository,
            DistributedKeyRepository distributedKeyRepository,
            DistributedKeyAccountGroupBindingRepository distributedKeyAccountGroupBindingRepository,
            SupportedModelCatalogService supportedModelCatalogService,
            OpsTimelineService opsTimelineService,
            ObjectMapper objectMapper) {
        this.upstreamAccountGroupRepository = upstreamAccountGroupRepository;
        this.upstreamAccountRepository = upstreamAccountRepository;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.distributedKeyRepository = distributedKeyRepository;
        this.distributedKeyAccountGroupBindingRepository = distributedKeyAccountGroupBindingRepository;
        this.supportedModelCatalogService = supportedModelCatalogService;
        this.opsTimelineService = opsTimelineService;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public List<AccountGroupResponse> list() {
        ensureDefaultGroup();
        return upstreamAccountGroupRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    public AccountGroupResponse get(Long id) {
        ensureDefaultGroup();
        return toResponse(getRequired(id));
    }

    @Transactional(readOnly = true)
    public List<String> listSupportedModelCatalog(UpstreamAccountProviderType providerType) {
        ensureDefaultGroup();
        return supportedModelCatalogService.listByUpstreamProvider(providerType);
    }

    public AccountGroupResponse create(AccountGroupRequest request) {
        validateGroupNameDuplicated(request.groupName(), null);
        UpstreamAccountGroupEntity entity = new UpstreamAccountGroupEntity();
        apply(entity, request);
        return toResponse(upstreamAccountGroupRepository.save(entity));
    }

    public AccountGroupResponse update(Long id, AccountGroupRequest request) {
        UpstreamAccountGroupEntity entity = getRequired(id);
        validateDefaultGroupUpdate(entity, request.groupName());
        validateGroupNameDuplicated(request.groupName(), id);
        apply(entity, request);
        return toResponse(upstreamAccountGroupRepository.save(entity));
    }

    public AccountGroupResponse toggle(Long id, boolean active) {
        UpstreamAccountGroupEntity entity = getRequired(id);
        entity.setActive(active);
        return toResponse(upstreamAccountGroupRepository.save(entity));
    }

    public void delete(Long id) {
        UpstreamAccountGroupEntity entity = getRequired(id);
        if (isDefaultGroup(entity)) {
            throw new IllegalArgumentException("default 账号分组为系统内置账号分组，不允许删除。");
        }

        Set<Long> affectedDistributedKeyIds = new HashSet<>();
        distributedKeyAccountGroupBindingRepository.findAllByGroup_Id(id)
                .forEach(binding -> affectedDistributedKeyIds.add(binding.getDistributedKey().getId()));

        upstreamAccountRepository.clearGroupReferenceByGroupId(id);
        upstreamCredentialRepository.clearGroupReferenceByGroupId(id);
        distributedKeyAccountGroupBindingRepository.deleteAllByGroup_Id(id);

        for (Long distributedKeyId : affectedDistributedKeyIds) {
            distributedKeyRepository.findById(distributedKeyId).ifPresent(distributedKey -> {
                if (!distributedKey.isActive()) {
                    return;
                }
                long activeBindingCount =
                        distributedKeyAccountGroupBindingRepository.countByDistributedKey_IdAndActiveTrue(distributedKeyId);
                if (activeBindingCount == 0) {
                    distributedKey.setActive(false);
                    distributedKeyRepository.save(distributedKey);
                }
            });
        }

        upstreamAccountGroupRepository.delete(entity);
    }

    public UpstreamAccountGroupEntity ensureDefaultGroup() {
        return upstreamAccountGroupRepository.findByGroupNameIgnoreCase(DEFAULT_GROUP_NAME)
                .orElseGet(() -> {
                    UpstreamAccountGroupEntity entity = new UpstreamAccountGroupEntity();
                    entity.setGroupName(DEFAULT_GROUP_NAME);
                    entity.setProviderType(com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType.OPENAI_OAUTH);
                    entity.setSupportedModels(List.of());
                    entity.setSupportedProtocols(List.of("openai", "responses"));
                    entity.setAllowedClientFamilies(List.of("GENERIC_OPENAI", "CODEX"));
                    entity.setDescription("系统默认账号分组");
                    entity.setActive(true);
                    return upstreamAccountGroupRepository.save(entity);
                });
    }

    public DistributedKeyAccountGroupBindingResponse bindDistributedKey(Long groupId, DistributedKeyAccountGroupBindingRequest request) {
        UpstreamAccountGroupEntity group = getRequired(groupId);
        DistributedKeyEntity distributedKey = distributedKeyRepository.findById(request.distributedKeyId())
                .orElseThrow(() -> new IllegalArgumentException("未找到 DistributedKey。"));
        DistributedKeyAccountGroupBindingEntity entity = new DistributedKeyAccountGroupBindingEntity();
        entity.setGroup(group);
        entity.setDistributedKey(distributedKey);
        entity.setProviderType(request.providerType());
        entity.setPriority(request.priority() == null ? 100 : request.priority());
        entity.setActive(request.active() == null || request.active());
        return toBindingResponse(distributedKeyAccountGroupBindingRepository.save(entity));
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public CodexRuntimeBatchRecoveryResponse codexRuntimeBatchRecovery(
            Long groupId,
            CodexRuntimeBatchRecoveryRequest request,
            boolean executeEndpoint) {
        UpstreamAccountGroupEntity group = getRequired(groupId);
        CodexRuntimeBatchRecoveryRequest effectiveRequest = request == null
                ? new CodexRuntimeBatchRecoveryRequest(false, false, List.of(), null)
                : request;
        boolean execute = executeEndpoint || Boolean.TRUE.equals(effectiveRequest.execute());
        boolean refreshQuota = Boolean.TRUE.equals(effectiveRequest.refreshQuota());
        Set<Long> requestedAccountIds = effectiveRequest.accountIds() == null
                ? Set.of()
                : new LinkedHashSet<>(effectiveRequest.accountIds());
        List<UpstreamAccountEntity> accounts = upstreamAccountRepository.findAllByGroup_IdOrderByCreatedAtDesc(groupId).stream()
                .filter(account -> requestedAccountIds.isEmpty() || requestedAccountIds.contains(account.getId()))
                .filter(account -> isCodexRuntimeAccount(account, group))
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
        var auditEvent = recordBatchRecoveryEvent(group, execute, refreshQuota, effectiveRequest.reason(), totals, items, generatedAt);
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

    private UpstreamAccountGroupEntity getRequired(Long id) {
        return upstreamAccountGroupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的账号分组。"));
    }

    private void apply(UpstreamAccountGroupEntity entity, AccountGroupRequest request) {
        entity.setGroupName(request.groupName().trim());
        entity.setProviderType(request.providerType());
        entity.setSupportedModels(supportedModelCatalogService.normalize(request.supportedModels()));
        entity.setSupportedProtocols(request.supportedProtocols() == null ? List.of() : request.supportedProtocols());
        entity.setAllowedClientFamilies(request.allowedClientFamilies() == null ? List.of() : request.allowedClientFamilies());
        entity.setDescription(request.description());
        entity.setActive(request.active() == null || request.active());
    }

    private boolean isCodexRuntimeAccount(UpstreamAccountEntity account, UpstreamAccountGroupEntity group) {
        return account.getProviderType() == UpstreamAccountProviderType.CODEX_OAUTH
                || group.getProviderType() == UpstreamAccountProviderType.CODEX_OAUTH
                || containsIgnoreCase(group.getAllowedClientFamilies(), "CODEX");
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
            UpstreamAccountGroupEntity group,
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
                "ACCOUNT_GROUP",
                "account-group:" + group.getId(),
                title,
                writeJson(Map.of(
                        "groupId", group.getId() == null ? -1L : group.getId(),
                        "groupName", group.getGroupName() == null ? "" : group.getGroupName(),
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

    private AccountGroupResponse toResponse(UpstreamAccountGroupEntity entity) {
        long oauthCount = upstreamAccountRepository.countByGroup_Id(entity.getId());
        long apiCredentialCount = upstreamCredentialRepository.countByGroupIdAndDeletedFalse(entity.getId());
        return new AccountGroupResponse(
                entity.getId(),
                entity.getGroupName(),
                entity.getProviderType(),
                entity.getSupportedModels(),
                entity.getSupportedProtocols(),
                entity.getAllowedClientFamilies(),
                entity.getDescription(),
                isDefaultGroup(entity),
                oauthCount,
                apiCredentialCount,
                oauthCount + apiCredentialCount,
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private DistributedKeyAccountGroupBindingResponse toBindingResponse(DistributedKeyAccountGroupBindingEntity entity) {
        return new DistributedKeyAccountGroupBindingResponse(
                entity.getId(),
                entity.getDistributedKey().getId(),
                entity.getGroup().getId(),
                entity.getProviderType(),
                entity.getPriority(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private boolean isDefaultGroup(UpstreamAccountGroupEntity entity) {
        return entity.getGroupName() != null && DEFAULT_GROUP_NAME.equalsIgnoreCase(entity.getGroupName().trim());
    }

    private void validateDefaultGroupUpdate(UpstreamAccountGroupEntity entity, String nextGroupName) {
        if (!isDefaultGroup(entity)) {
            return;
        }
        if (nextGroupName == null || !DEFAULT_GROUP_NAME.equalsIgnoreCase(nextGroupName.trim())) {
            throw new IllegalArgumentException("default 账号分组为系统内置账号分组，不允许修改名称。");
        }
    }

    private void validateGroupNameDuplicated(String groupName, Long currentId) {
        String normalized = groupName == null ? "" : groupName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("账号分组名称不能为空。");
        }
        upstreamAccountGroupRepository.findByGroupNameIgnoreCase(normalized).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new IllegalArgumentException("账号分组名称已存在，请使用其他名称。");
            }
        });
    }
}
