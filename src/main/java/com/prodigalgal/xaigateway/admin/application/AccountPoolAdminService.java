package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AccountPoolRequest;
import com.prodigalgal.xaigateway.admin.api.AccountPoolResponse;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyAccountPoolBindingRequest;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyAccountPoolBindingResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccountPoolBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountPoolEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountPoolBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountPoolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AccountPoolAdminService {

    private final UpstreamAccountPoolRepository upstreamAccountPoolRepository;
    private final UpstreamAccountRepository upstreamAccountRepository;
    private final DistributedKeyRepository distributedKeyRepository;
    private final DistributedKeyAccountPoolBindingRepository distributedKeyAccountPoolBindingRepository;

    public AccountPoolAdminService(
            UpstreamAccountPoolRepository upstreamAccountPoolRepository,
            UpstreamAccountRepository upstreamAccountRepository,
            DistributedKeyRepository distributedKeyRepository,
            DistributedKeyAccountPoolBindingRepository distributedKeyAccountPoolBindingRepository) {
        this.upstreamAccountPoolRepository = upstreamAccountPoolRepository;
        this.upstreamAccountRepository = upstreamAccountRepository;
        this.distributedKeyRepository = distributedKeyRepository;
        this.distributedKeyAccountPoolBindingRepository = distributedKeyAccountPoolBindingRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountPoolResponse> list() {
        return upstreamAccountPoolRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AccountPoolResponse get(Long id) {
        return toResponse(getRequired(id));
    }

    public AccountPoolResponse create(AccountPoolRequest request) {
        UpstreamAccountPoolEntity entity = new UpstreamAccountPoolEntity();
        apply(entity, request);
        return toResponse(upstreamAccountPoolRepository.save(entity));
    }

    public AccountPoolResponse update(Long id, AccountPoolRequest request) {
        UpstreamAccountPoolEntity entity = getRequired(id);
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
        if (upstreamAccountRepository.countByPool_Id(id) > 0) {
            throw new IllegalArgumentException("账号池下仍有账号，请先迁移或清理账号后再删除。");
        }
        distributedKeyAccountPoolBindingRepository.deleteAllByPool_Id(id);
        upstreamAccountPoolRepository.delete(entity);
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
        entity.setSupportedModels(request.supportedModels() == null ? List.of() : request.supportedModels());
        entity.setSupportedProtocols(request.supportedProtocols() == null ? List.of() : request.supportedProtocols());
        entity.setAllowedClientFamilies(request.allowedClientFamilies() == null ? List.of() : request.allowedClientFamilies());
        entity.setDescription(request.description());
        entity.setActive(request.active() == null || request.active());
    }

    private AccountPoolResponse toResponse(UpstreamAccountPoolEntity entity) {
        return new AccountPoolResponse(
                entity.getId(),
                entity.getPoolName(),
                entity.getProviderType(),
                entity.getSupportedModels(),
                entity.getSupportedProtocols(),
                entity.getAllowedClientFamilies(),
                entity.getDescription(),
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
}
