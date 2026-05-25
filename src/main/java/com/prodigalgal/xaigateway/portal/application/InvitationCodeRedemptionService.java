package com.prodigalgal.xaigateway.portal.application;

import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserBalanceLedgerEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.InvitationCodeEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.InvitationRelationshipEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.InvitationCodeUsageEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.AuditLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UserAccessGroupGrantEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UserSubscriptionEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AuditLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserBalanceLedgerRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.InvitationCodeRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.InvitationRelationshipRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.InvitationCodeUsageRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UserAccessGroupGrantRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UserSubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InvitationCodeRedemptionService {

    private final InvitationCodeRepository invitationCodeRepository;
    private final InvitationCodeUsageRepository invitationCodeUsageRepository;
    private final GatewayUserBalanceLedgerRepository balanceLedgerRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final UserAccessGroupGrantRepository userAccessGroupGrantRepository;
    private final InvitationRelationshipRepository invitationRelationshipRepository;

    public InvitationCodeRedemptionService(
            InvitationCodeRepository invitationCodeRepository,
            InvitationCodeUsageRepository invitationCodeUsageRepository) {
        this(invitationCodeRepository, invitationCodeUsageRepository, null, null, null, null, null);
    }

    public InvitationCodeRedemptionService(
            InvitationCodeRepository invitationCodeRepository,
            InvitationCodeUsageRepository invitationCodeUsageRepository,
            AuditLogRepository auditLogRepository) {
        this(invitationCodeRepository, invitationCodeUsageRepository, null, auditLogRepository, null, null, null);
    }

    public InvitationCodeRedemptionService(
            InvitationCodeRepository invitationCodeRepository,
            InvitationCodeUsageRepository invitationCodeUsageRepository,
            GatewayUserBalanceLedgerRepository balanceLedgerRepository,
            AuditLogRepository auditLogRepository) {
        this(invitationCodeRepository, invitationCodeUsageRepository, balanceLedgerRepository, auditLogRepository, null, null, null);
    }

    @Autowired
    public InvitationCodeRedemptionService(
            InvitationCodeRepository invitationCodeRepository,
            InvitationCodeUsageRepository invitationCodeUsageRepository,
            GatewayUserBalanceLedgerRepository balanceLedgerRepository,
            AuditLogRepository auditLogRepository,
            UserSubscriptionRepository userSubscriptionRepository,
            UserAccessGroupGrantRepository userAccessGroupGrantRepository,
            InvitationRelationshipRepository invitationRelationshipRepository) {
        this.invitationCodeRepository = invitationCodeRepository;
        this.invitationCodeUsageRepository = invitationCodeUsageRepository;
        this.balanceLedgerRepository = balanceLedgerRepository;
        this.auditLogRepository = auditLogRepository;
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.userAccessGroupGrantRepository = userAccessGroupGrantRepository;
        this.invitationRelationshipRepository = invitationRelationshipRepository;
    }

    @Transactional(readOnly = true)
    public boolean hasConfiguredInvitationCodes() {
        return invitationCodeRepository.existsUsableCode();
    }

    public void redeemForRegistration(
            String rawCode,
            GatewayUserEntity user,
            String registrationEmail,
            String registrationChannel,
            String requestSource) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("邀请码核销需要已创建的注册用户。");
        }
        String code = normalizeCode(rawCode);
        InvitationCodeEntity invitationCode = invitationCodeRepository.findFirstByCodeIgnoreCase(code)
                .orElseThrow(() -> reject(code, user, "not_found", "注册需要有效邀请码。"));
        if (invitationCodeUsageRepository.existsByInvitationCode_IdAndUser_Id(invitationCode.getId(), user.getId())) {
            audit(user, "INVITATION_CODE_REDEEMED", "SUCCESS", invitationCode.getCode(), "{\"idempotent\":true}");
            return;
        }
        validateInvitationCode(invitationCode, user);

        GatewayUserEntity referrer = resolveReferrer(invitationCode, user);
        InvitationCodeUsageEntity usage = new InvitationCodeUsageEntity();
        usage.setInvitationCode(invitationCode);
        usage.setUser(user);
        usage.setRegistrationEmail(normalizeEmail(registrationEmail));
        usage.setRegistrationChannel(normalizeChannel(registrationChannel));
        usage.setRequestSource(blankToNull(requestSource));
        usage.setReferrerUser(referrer);
        usage.setRewardTokenCredits(invitationCode.getRewardTokenCredits());
        usage.setReferrerRewardTokenCredits(referrer == null ? 0L : invitationCode.getReferrerRewardTokenCredits());
        usage.setRewardPlan(invitationCode.getRewardPlan());
        usage.setRewardAccessGroup(invitationCode.getRewardAccessGroup());
        usage.setUsedAt(Instant.now());
        usage = invitationCodeUsageRepository.save(usage);

        invitationCode.setUsedCount(invitationCode.getUsedCount() + 1);
        invitationCodeRepository.save(invitationCode);
        applyRegisterRewardIfConfigured(invitationCode, user);
        applyReferrerRewardIfConfigured(invitationCode, referrer, user);
        usage.setRewardSubscription(applyRewardPlanIfConfigured(invitationCode, user));
        usage.setRewardAccessGroupGrant(applyRewardAccessGroupIfConfigured(invitationCode, user));
        createInvitationRelationshipIfConfigured(invitationCode, usage, referrer, user, normalizeChannel(registrationChannel));
        audit(user, "INVITATION_CODE_REDEEMED", "SUCCESS", invitationCode.getCode(), "{\"channel\":\"" + normalizeChannel(registrationChannel) + "\",\"rewardTokenCredits\":" + invitationCode.getRewardTokenCredits() + ",\"referrerRewardTokenCredits\":" + usage.getReferrerRewardTokenCredits() + "}");
    }

    private void validateInvitationCode(InvitationCodeEntity invitationCode, GatewayUserEntity user) {
        Instant now = Instant.now();
        if (!invitationCode.isActive()) {
            throw reject(invitationCode.getCode(), user, "inactive", "邀请码已停用。");
        }
        if (invitationCode.getExpiresAt() != null && !invitationCode.getExpiresAt().isAfter(now)) {
            throw reject(invitationCode.getCode(), user, "expired", "邀请码已过期。");
        }
        if (invitationCode.getUsedCount() >= invitationCode.getMaxUses()) {
            throw reject(invitationCode.getCode(), user, "exhausted", "邀请码可用次数已用尽。");
        }
    }

    private String normalizeCode(String rawCode) {
        String value = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (value.isBlank()) {
            throw new IllegalArgumentException("注册需要有效邀请码。");
        }
        return value;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeChannel(String channel) {
        return channel == null || channel.isBlank()
                ? PortalSecurityService.REGISTRATION_CHANNEL_INVITE_CODE
                : channel.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private GatewayUserEntity resolveReferrer(InvitationCodeEntity invitationCode, GatewayUserEntity user) {
        GatewayUserEntity owner = invitationCode.getOwnerUser();
        if (owner == null || owner.getId() == null || owner.getId().equals(user.getId())) {
            return null;
        }
        return owner;
    }

    private void applyRegisterRewardIfConfigured(InvitationCodeEntity invitationCode, GatewayUserEntity user) {
        long rewardTokenCredits = invitationCode.getRewardTokenCredits();
        if (rewardTokenCredits <= 0) {
            return;
        }
        applyTokenRewardIfConfigured(user, rewardTokenCredits, "INVITATION_CODE", invitationCode.getCode() + ":" + user.getId());
    }

    private void applyReferrerRewardIfConfigured(InvitationCodeEntity invitationCode, GatewayUserEntity referrer, GatewayUserEntity invitedUser) {
        if (referrer == null || invitationCode.getReferrerRewardTokenCredits() <= 0) {
            return;
        }
        applyTokenRewardIfConfigured(
                referrer,
                invitationCode.getReferrerRewardTokenCredits(),
                "INVITATION_REFERRER_REWARD",
                invitationCode.getCode() + ":" + invitedUser.getId()
        );
    }

    private void applyTokenRewardIfConfigured(GatewayUserEntity user, long rewardTokenCredits, String referenceType, String referenceId) {
        if (balanceLedgerRepository == null) {
            throw new IllegalStateException("余额流水仓储未配置，无法发放邀请奖励。");
        }
        if (balanceLedgerRepository.findByReferenceTypeAndReferenceId(referenceType, referenceId).isPresent()) {
            return;
        }
        long balanceAfter = balanceLedgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(user.getId())
                .map(GatewayUserBalanceLedgerEntity::getBalanceAfterTokenCredits)
                .orElse(0L) + rewardTokenCredits;
        GatewayUserBalanceLedgerEntity ledger = new GatewayUserBalanceLedgerEntity();
        ledger.setUser(user);
        ledger.setDeltaTokenCredits(rewardTokenCredits);
        ledger.setBalanceAfterTokenCredits(balanceAfter);
        ledger.setReason(referenceType);
        ledger.setReferenceType(referenceType);
        ledger.setReferenceId(referenceId);
        balanceLedgerRepository.save(ledger);
    }

    private UserSubscriptionEntity applyRewardPlanIfConfigured(InvitationCodeEntity invitationCode, GatewayUserEntity user) {
        if (invitationCode.getRewardPlan() == null) {
            return null;
        }
        if (userSubscriptionRepository == null) {
            throw new IllegalStateException("订阅仓储未配置，无法发放邀请码套餐赠品。");
        }
        String sourceType = "INVITATION_REWARD_PLAN";
        String sourceId = invitationCode.getCode() + ":" + user.getId();
        return userSubscriptionRepository.findBySourceTypeAndSourceId(sourceType, sourceId)
                .orElseGet(() -> {
                    Instant startsAt = Instant.now();
                    int durationDays = invitationCode.getRewardPlanDurationDays() == null || invitationCode.getRewardPlanDurationDays() <= 0
                            ? invitationCode.getRewardPlan().getDefaultDurationDays()
                            : invitationCode.getRewardPlanDurationDays();
                    UserSubscriptionEntity subscription = new UserSubscriptionEntity();
                    subscription.setUser(user);
                    subscription.setPlan(invitationCode.getRewardPlan());
                    subscription.setStatus("ACTIVE");
                    subscription.setStartsAt(startsAt);
                    subscription.setExpiresAt(startsAt.plus(durationDays, ChronoUnit.DAYS));
                    subscription.setAutoRenew(false);
                    subscription.setSourceType(sourceType);
                    subscription.setSourceId(sourceId);
                    subscription.setNotes("邀请码赠送套餐：" + invitationCode.getCode());
                    return userSubscriptionRepository.save(subscription);
                });
    }

    private UserAccessGroupGrantEntity applyRewardAccessGroupIfConfigured(InvitationCodeEntity invitationCode, GatewayUserEntity user) {
        if (invitationCode.getRewardAccessGroup() == null) {
            return null;
        }
        if (userAccessGroupGrantRepository == null) {
            throw new IllegalStateException("用户权益组授权仓储未配置，无法发放邀请码权益组赠品。");
        }
        String sourceType = "INVITATION_ACCESS_GROUP_GRANT";
        String sourceId = invitationCode.getCode() + ":" + user.getId();
        return userAccessGroupGrantRepository.findBySourceTypeAndSourceId(sourceType, sourceId)
                .orElseGet(() -> {
                    Instant startsAt = Instant.now();
                    Integer durationDays = invitationCode.getRewardAccessGroupDurationDays();
                    UserAccessGroupGrantEntity grant = new UserAccessGroupGrantEntity();
                    grant.setUser(user);
                    grant.setAccessGroup(invitationCode.getRewardAccessGroup());
                    grant.setStatus("ACTIVE");
                    grant.setStartsAt(startsAt);
                    grant.setExpiresAt(durationDays == null || durationDays <= 0 ? null : startsAt.plus(durationDays, ChronoUnit.DAYS));
                    grant.setSourceType(sourceType);
                    grant.setSourceId(sourceId);
                    grant.setNotes("邀请码赠送权益组：" + invitationCode.getCode());
                    return userAccessGroupGrantRepository.save(grant);
                });
    }

    private void createInvitationRelationshipIfConfigured(
            InvitationCodeEntity invitationCode,
            InvitationCodeUsageEntity usage,
            GatewayUserEntity referrer,
            GatewayUserEntity invitedUser,
            String registrationChannel) {
        if (referrer == null || invitationRelationshipRepository == null) {
            return;
        }
        invitationRelationshipRepository.findByInvitedUser_Id(invitedUser.getId())
                .orElseGet(() -> {
                    InvitationRelationshipEntity parent = invitationRelationshipRepository.findByInvitedUser_Id(referrer.getId()).orElse(null);
                    InvitationRelationshipEntity relationship = new InvitationRelationshipEntity();
                    relationship.setInvitationCode(invitationCode);
                    relationship.setInvitationUsage(usage);
                    relationship.setReferrerUser(referrer);
                    relationship.setInvitedUser(invitedUser);
                    relationship.setDepth(parent == null ? 1 : parent.getDepth() + 1);
                    relationship.setPath((parent == null || parent.getPath() == null || parent.getPath().isBlank())
                            ? referrer.getId() + "/" + invitedUser.getId()
                            : parent.getPath() + "/" + invitedUser.getId());
                    relationship.setSourceChannel(registrationChannel);
                    return invitationRelationshipRepository.save(relationship);
                });
    }

    private IllegalArgumentException reject(String code, GatewayUserEntity user, String reason, String message) {
        audit(user, "INVITATION_CODE_REJECTED", "FAILED", code, "{\"reason\":\"" + reason + "\"}");
        return new IllegalArgumentException(message);
    }

    private void audit(GatewayUserEntity user, String action, String status, String code, String detailJson) {
        if (auditLogRepository == null) {
            return;
        }
        AuditLogEntity entity = new AuditLogEntity();
        entity.setAuditType("PORTAL_SECURITY");
        entity.setAction(action);
        entity.setTargetType("invitation_code");
        entity.setTargetId(code == null || code.isBlank() ? "unknown" : code);
        entity.setStatus(status);
        entity.setActor(user == null ? "portal" : user.getEmail());
        entity.setPath("/portal/auth/register");
        entity.setDetailJson(detailJson == null || detailJson.isBlank() ? "{}" : detailJson);
        auditLogRepository.save(entity);
    }
}
