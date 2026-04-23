package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AccountPoolRequest;
import com.prodigalgal.xaigateway.admin.api.AccountPoolResponse;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyAccountPoolBindingRequest;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyAccountPoolBindingResponse;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccountPoolBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountPoolEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountPoolBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountPoolRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public AccountPoolAdminService(
            UpstreamAccountPoolRepository upstreamAccountPoolRepository,
            UpstreamAccountRepository upstreamAccountRepository,
            UpstreamCredentialRepository upstreamCredentialRepository,
            DistributedKeyRepository distributedKeyRepository,
            DistributedKeyAccountPoolBindingRepository distributedKeyAccountPoolBindingRepository,
            SupportedModelCatalogService supportedModelCatalogService) {
        this.upstreamAccountPoolRepository = upstreamAccountPoolRepository;
        this.upstreamAccountRepository = upstreamAccountRepository;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.distributedKeyRepository = distributedKeyRepository;
        this.distributedKeyAccountPoolBindingRepository = distributedKeyAccountPoolBindingRepository;
        this.supportedModelCatalogService = supportedModelCatalogService;
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
