package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.InvitationCodeBatchRequest;
import com.prodigalgal.xaigateway.admin.api.InvitationCodeResponse;
import com.prodigalgal.xaigateway.admin.api.InvitationCodeUpdateRequest;
import com.prodigalgal.xaigateway.admin.api.InvitationCodeUsageResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.AuditLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.AccessGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.InvitationCodeEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.InvitationCodeUsageEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SubscriptionPlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AuditLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.AccessGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.InvitationCodeRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.InvitationCodeUsageRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SubscriptionPlanRepository;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InvitationCodeAdminService {

    private final InvitationCodeRepository invitationCodeRepository;
    private final InvitationCodeUsageRepository invitationCodeUsageRepository;
    private final GatewayUserRepository gatewayUserRepository;
    private final AuditLogRepository auditLogRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AccessGroupRepository accessGroupRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public InvitationCodeAdminService(
            InvitationCodeRepository invitationCodeRepository,
            InvitationCodeUsageRepository invitationCodeUsageRepository) {
        this(invitationCodeRepository, invitationCodeUsageRepository, null, null, null, null);
    }

    public InvitationCodeAdminService(
            InvitationCodeRepository invitationCodeRepository,
            InvitationCodeUsageRepository invitationCodeUsageRepository,
            AuditLogRepository auditLogRepository) {
        this(invitationCodeRepository, invitationCodeUsageRepository, null, auditLogRepository, null, null);
    }

    public InvitationCodeAdminService(
            InvitationCodeRepository invitationCodeRepository,
            InvitationCodeUsageRepository invitationCodeUsageRepository,
            GatewayUserRepository gatewayUserRepository,
            AuditLogRepository auditLogRepository) {
        this(invitationCodeRepository, invitationCodeUsageRepository, gatewayUserRepository, auditLogRepository, null, null);
    }

    @Autowired
    public InvitationCodeAdminService(
            InvitationCodeRepository invitationCodeRepository,
            InvitationCodeUsageRepository invitationCodeUsageRepository,
            GatewayUserRepository gatewayUserRepository,
            AuditLogRepository auditLogRepository,
            SubscriptionPlanRepository subscriptionPlanRepository,
            AccessGroupRepository accessGroupRepository) {
        this.invitationCodeRepository = invitationCodeRepository;
        this.invitationCodeUsageRepository = invitationCodeUsageRepository;
        this.gatewayUserRepository = gatewayUserRepository;
        this.auditLogRepository = auditLogRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.accessGroupRepository = accessGroupRepository;
    }

    @Transactional(readOnly = true)
    public List<InvitationCodeResponse> listCodes() {
        return listCodes(null, null);
    }

    @Transactional(readOnly = true)
    public List<InvitationCodeResponse> listCodes(String keyword, Boolean active) {
        String normalizedKeyword = blankToNull(keyword);
        List<InvitationCodeEntity> codes = normalizedKeyword == null && active == null
                ? invitationCodeRepository.findAllByOrderByCreatedAtDesc()
                : invitationCodeRepository.search(normalizedKeyword, active);
        return codes.stream()
                .map(this::toCodeResponse)
                .toList();
    }

    public List<InvitationCodeResponse> createCodes(InvitationCodeBatchRequest request) {
        List<String> codes = normalizeCodes(request);
        int maxUses = request.maxUses() == null ? 1 : Math.max(1, request.maxUses());
        boolean active = request.active() == null || request.active();
        GatewayUserEntity ownerUser = resolveOwnerUser(request.ownerUserId());
        long rewardTokenCredits = normalizeRewardTokenCredits(request.rewardTokenCredits());
        long referrerRewardTokenCredits = normalizeRewardTokenCredits(request.referrerRewardTokenCredits());
        SubscriptionPlanEntity rewardPlan = resolveRewardPlan(request.rewardPlanId());
        Integer rewardPlanDurationDays = normalizeOptionalDurationDays(request.rewardPlanDurationDays(), "赠送套餐天数");
        AccessGroupEntity rewardAccessGroup = resolveRewardAccessGroup(request.rewardAccessGroupId());
        Integer rewardAccessGroupDurationDays = normalizeOptionalDurationDays(request.rewardAccessGroupDurationDays(), "赠送权益组天数");
        String notes = blankToNull(request.notes());
        List<InvitationCodeResponse> created = new ArrayList<>();
        for (String code : codes) {
            if (invitationCodeRepository.existsByCodeIgnoreCase(code)) {
                throw new IllegalArgumentException("邀请码已存在：" + code);
            }
            InvitationCodeEntity entity = new InvitationCodeEntity();
            entity.setCode(code);
            entity.setActive(active);
            entity.setMaxUses(maxUses);
            entity.setExpiresAt(request.expiresAt());
            entity.setOwnerUser(ownerUser);
            entity.setRewardTokenCredits(rewardTokenCredits);
            entity.setReferrerRewardTokenCredits(referrerRewardTokenCredits);
            entity.setRewardPlan(rewardPlan);
            entity.setRewardPlanDurationDays(rewardPlanDurationDays);
            entity.setRewardAccessGroup(rewardAccessGroup);
            entity.setRewardAccessGroupDurationDays(rewardAccessGroupDurationDays);
            entity.setNotes(notes);
            created.add(toCodeResponse(invitationCodeRepository.save(entity)));
        }
        audit("INVITATION_CODES_CREATED", "SUCCESS", "invitation_code", "batch", "{\"count\":" + created.size() + "}");
        return created;
    }

    public InvitationCodeResponse updateCode(Long id, InvitationCodeUpdateRequest request) {
        InvitationCodeEntity entity = getCodeRequired(id);
        if (request.active() != null) {
            entity.setActive(request.active());
        }
        if (request.maxUses() != null) {
            int maxUses = Math.max(1, request.maxUses());
            if (maxUses < entity.getUsedCount()) {
                throw new IllegalArgumentException("最大使用次数不能小于已使用次数。");
            }
            entity.setMaxUses(maxUses);
        }
        entity.setExpiresAt(request.expiresAt());
        entity.setOwnerUser(resolveOwnerUser(request.ownerUserId()));
        if (request.rewardTokenCredits() != null) {
            entity.setRewardTokenCredits(normalizeRewardTokenCredits(request.rewardTokenCredits()));
        }
        if (request.referrerRewardTokenCredits() != null) {
            entity.setReferrerRewardTokenCredits(normalizeRewardTokenCredits(request.referrerRewardTokenCredits()));
        }
        entity.setRewardPlan(resolveRewardPlan(request.rewardPlanId()));
        entity.setRewardPlanDurationDays(normalizeOptionalDurationDays(request.rewardPlanDurationDays(), "赠送套餐天数"));
        entity.setRewardAccessGroup(resolveRewardAccessGroup(request.rewardAccessGroupId()));
        entity.setRewardAccessGroupDurationDays(normalizeOptionalDurationDays(request.rewardAccessGroupDurationDays(), "赠送权益组天数"));
        entity.setNotes(blankToNull(request.notes()));
        InvitationCodeResponse response = toCodeResponse(invitationCodeRepository.save(entity));
        audit("INVITATION_CODE_UPDATED", "SUCCESS", "invitation_code", String.valueOf(id), "{\"active\":" + entity.isActive() + "}");
        return response;
    }

    public void deleteCode(Long id) {
        InvitationCodeEntity entity = getCodeRequired(id);
        if (entity.getUsedCount() > 0) {
            throw new IllegalArgumentException("已被使用的邀请码不能删除，请改为停用。");
        }
        invitationCodeRepository.delete(entity);
        audit("INVITATION_CODE_DELETED", "SUCCESS", "invitation_code", String.valueOf(id), "{}");
    }

    @Transactional(readOnly = true)
    public List<InvitationCodeUsageResponse> listUsages(Long id) {
        getCodeRequired(id);
        return invitationCodeUsageRepository.findAllByInvitationCode_IdOrderByUsedAtDesc(id).stream()
                .map(this::toUsageResponse)
                .toList();
    }

    private InvitationCodeEntity getCodeRequired(Long id) {
        return invitationCodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定邀请码。"));
    }

    private List<String> normalizeCodes(InvitationCodeBatchRequest request) {
        List<String> codes = new ArrayList<>();
        if (request != null && request.codes() != null) {
            for (String raw : request.codes()) {
                String code = normalizeCode(raw);
                if (code != null && !codes.contains(code)) {
                    codes.add(code);
                }
            }
        }
        String rawText = request == null ? null : blankToNull(request.rawText());
        if (rawText != null) {
            for (String raw : rawText.split("\\R")) {
                String code = normalizeCode(raw);
                if (code != null && !codes.contains(code)) {
                    codes.add(code);
                }
            }
        }
        int generateCount = request == null || request.generateCount() == null ? 0 : Math.max(0, request.generateCount());
        String prefix = request == null ? null : blankToNull(request.prefix());
        for (int index = 0; index < generateCount; index += 1) {
            String generated;
            do {
                generated = (prefix == null ? "INV" : prefix.trim().toUpperCase(Locale.ROOT)) + "-" + randomCode();
            } while (codes.contains(generated) || invitationCodeRepository.existsByCodeIgnoreCase(generated));
            codes.add(generated);
        }
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("请粘贴邀请码或指定生成数量。");
        }
        return codes;
    }

    private String randomCode() {
        byte[] bytes = new byte[6];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);
    }

    private String normalizeCode(String raw) {
        String value = blankToNull(raw);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private GatewayUserEntity resolveOwnerUser(Long ownerUserId) {
        if (ownerUserId == null) {
            return null;
        }
        if (ownerUserId <= 0) {
            throw new IllegalArgumentException("邀请码归属用户 ID 不合法。");
        }
        if (gatewayUserRepository == null) {
            throw new IllegalStateException("用户仓储未配置，无法设置邀请码归属人。");
        }
        return gatewayUserRepository.findById(ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定邀请码归属用户。"));
    }

    private long normalizeRewardTokenCredits(Long rewardTokenCredits) {
        long value = rewardTokenCredits == null ? 0L : rewardTokenCredits;
        if (value < 0) {
            throw new IllegalArgumentException("邀请奖励额度不能为负数。");
        }
        return value;
    }

    private SubscriptionPlanEntity resolveRewardPlan(Long rewardPlanId) {
        if (rewardPlanId == null) {
            return null;
        }
        if (rewardPlanId <= 0) {
            throw new IllegalArgumentException("赠送套餐 ID 不合法。");
        }
        if (subscriptionPlanRepository == null) {
            throw new IllegalStateException("套餐仓储未配置，无法设置邀请码套餐赠品。");
        }
        SubscriptionPlanEntity plan = subscriptionPlanRepository.findById(rewardPlanId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定赠送套餐。"));
        if (!plan.isActive()) {
            throw new IllegalArgumentException("赠送套餐必须启用。");
        }
        return plan;
    }

    private AccessGroupEntity resolveRewardAccessGroup(Long rewardAccessGroupId) {
        if (rewardAccessGroupId == null) {
            return null;
        }
        if (rewardAccessGroupId <= 0) {
            throw new IllegalArgumentException("赠送权益组 ID 不合法。");
        }
        if (accessGroupRepository == null) {
            throw new IllegalStateException("权益组仓储未配置，无法设置邀请码权益组赠品。");
        }
        AccessGroupEntity accessGroup = accessGroupRepository.findById(rewardAccessGroupId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定赠送权益组。"));
        if (!accessGroup.isActive()) {
            throw new IllegalArgumentException("赠送权益组必须启用。");
        }
        return accessGroup;
    }

    private Integer normalizeOptionalDurationDays(Integer value, String label) {
        if (value == null || value <= 0) {
            return null;
        }
        if (value > 3650) {
            throw new IllegalArgumentException(label + "不能超过 3650 天。");
        }
        return value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void audit(String action, String status, String targetType, String targetId, String detailJson) {
        if (auditLogRepository == null) {
            return;
        }
        AuditLogEntity entity = new AuditLogEntity();
        entity.setAuditType("ADMIN");
        entity.setAction(action);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setStatus(status);
        entity.setActor("admin");
        entity.setPath("/admin/invitation-codes");
        entity.setDetailJson(detailJson == null || detailJson.isBlank() ? "{}" : detailJson);
        auditLogRepository.save(entity);
    }

    private InvitationCodeResponse toCodeResponse(InvitationCodeEntity entity) {
        GatewayUserEntity ownerUser = entity.getOwnerUser();
        SubscriptionPlanEntity rewardPlan = entity.getRewardPlan();
        AccessGroupEntity rewardAccessGroup = entity.getRewardAccessGroup();
        return new InvitationCodeResponse(
                entity.getId(),
                entity.getCode(),
                entity.isActive(),
                entity.getMaxUses(),
                entity.getUsedCount(),
                entity.getExpiresAt(),
                ownerUser == null ? null : ownerUser.getId(),
                ownerUser == null ? null : ownerUser.getEmail(),
                ownerUser == null ? null : ownerUser.getDisplayName(),
                entity.getRewardTokenCredits(),
                entity.getReferrerRewardTokenCredits(),
                rewardPlan == null ? null : rewardPlan.getId(),
                rewardPlan == null ? null : rewardPlan.getPlanName(),
                entity.getRewardPlanDurationDays(),
                rewardAccessGroup == null ? null : rewardAccessGroup.getId(),
                rewardAccessGroup == null ? null : rewardAccessGroup.getGroupName(),
                entity.getRewardAccessGroupDurationDays(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private InvitationCodeUsageResponse toUsageResponse(InvitationCodeUsageEntity entity) {
        GatewayUserEntity referrer = entity.getReferrerUser();
        SubscriptionPlanEntity rewardPlan = entity.getRewardPlan();
        AccessGroupEntity rewardAccessGroup = entity.getRewardAccessGroup();
        return new InvitationCodeUsageResponse(
                entity.getId(),
                entity.getInvitationCode().getId(),
                entity.getInvitationCode().getCode(),
                entity.getUser().getId(),
                entity.getRegistrationEmail(),
                entity.getRegistrationChannel(),
                entity.getRequestSource(),
                referrer == null ? null : referrer.getId(),
                referrer == null ? null : referrer.getEmail(),
                entity.getRewardTokenCredits(),
                entity.getReferrerRewardTokenCredits(),
                rewardPlan == null ? null : rewardPlan.getId(),
                rewardPlan == null ? null : rewardPlan.getPlanName(),
                entity.getRewardSubscription() == null ? null : entity.getRewardSubscription().getId(),
                rewardAccessGroup == null ? null : rewardAccessGroup.getId(),
                rewardAccessGroup == null ? null : rewardAccessGroup.getGroupName(),
                entity.getRewardAccessGroupGrant() == null ? null : entity.getRewardAccessGroupGrant().getId(),
                entity.getUsedAt(),
                entity.getCreatedAt()
        );
    }
}
