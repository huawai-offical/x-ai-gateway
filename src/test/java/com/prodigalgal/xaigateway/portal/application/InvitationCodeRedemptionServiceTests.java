package com.prodigalgal.xaigateway.portal.application;

import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserBalanceLedgerEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.InvitationCodeEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.InvitationRelationshipEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.InvitationCodeUsageEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.AccessGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SubscriptionPlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UserAccessGroupGrantEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UserSubscriptionEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserBalanceLedgerRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.InvitationCodeRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.InvitationRelationshipRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.InvitationCodeUsageRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UserAccessGroupGrantRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UserSubscriptionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvitationCodeRedemptionServiceTests {

    @Test
    void shouldRedeemInvitationCodeForRegistration() {
        InvitationCodeRepository codeRepository = Mockito.mock(InvitationCodeRepository.class);
        InvitationCodeUsageRepository usageRepository = Mockito.mock(InvitationCodeUsageRepository.class);
        InvitationCodeRedemptionService service = new InvitationCodeRedemptionService(codeRepository, usageRepository);
        InvitationCodeEntity code = code(61L, "INVITE-1");
        GatewayUserEntity user = user(8L);
        Mockito.when(codeRepository.findFirstByCodeIgnoreCase("INVITE-1")).thenReturn(Optional.of(code));
        Mockito.when(usageRepository.existsByInvitationCode_IdAndUser_Id(61L, 8L)).thenReturn(false);
        Mockito.when(usageRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(codeRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.redeemForRegistration(" invite-1 ", user, "Alpha@Example.com", "INVITE_CODE", "PORTAL_REGISTER");

        assertEquals(1, code.getUsedCount());
        ArgumentCaptor<InvitationCodeUsageEntity> captor = ArgumentCaptor.forClass(InvitationCodeUsageEntity.class);
        Mockito.verify(usageRepository).save(captor.capture());
        assertEquals("alpha@example.com", captor.getValue().getRegistrationEmail());
        assertEquals("INVITE_CODE", captor.getValue().getRegistrationChannel());
    }

    @Test
    void shouldApplyRewardLedgerWhenInvitationCodeHasReward() {
        InvitationCodeRepository codeRepository = Mockito.mock(InvitationCodeRepository.class);
        InvitationCodeUsageRepository usageRepository = Mockito.mock(InvitationCodeUsageRepository.class);
        GatewayUserBalanceLedgerRepository ledgerRepository = Mockito.mock(GatewayUserBalanceLedgerRepository.class);
        InvitationCodeRedemptionService service = new InvitationCodeRedemptionService(
                codeRepository,
                usageRepository,
                ledgerRepository,
                null
        );
        InvitationCodeEntity code = code(61L, "INVITE-REWARD");
        code.setRewardTokenCredits(5000L);
        GatewayUserEntity user = user(8L);
        GatewayUserBalanceLedgerEntity existing = new GatewayUserBalanceLedgerEntity();
        existing.setBalanceAfterTokenCredits(1000L);
        Mockito.when(codeRepository.findFirstByCodeIgnoreCase("INVITE-REWARD")).thenReturn(Optional.of(code));
        Mockito.when(usageRepository.existsByInvitationCode_IdAndUser_Id(61L, 8L)).thenReturn(false);
        Mockito.when(usageRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(codeRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(ledgerRepository.findByReferenceTypeAndReferenceId("INVITATION_CODE", "INVITE-REWARD:8"))
                .thenReturn(Optional.empty());
        Mockito.when(ledgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(8L)).thenReturn(Optional.of(existing));
        Mockito.when(ledgerRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.redeemForRegistration(" invite-reward ", user, "Alpha@Example.com", "SOCIAL_OAUTH", "PORTAL_SOCIAL_OAUTH");

        ArgumentCaptor<InvitationCodeUsageEntity> usageCaptor = ArgumentCaptor.forClass(InvitationCodeUsageEntity.class);
        Mockito.verify(usageRepository).save(usageCaptor.capture());
        assertEquals(5000L, usageCaptor.getValue().getRewardTokenCredits());
        ArgumentCaptor<GatewayUserBalanceLedgerEntity> ledgerCaptor = ArgumentCaptor.forClass(GatewayUserBalanceLedgerEntity.class);
        Mockito.verify(ledgerRepository).save(ledgerCaptor.capture());
        assertEquals(5000L, ledgerCaptor.getValue().getDeltaTokenCredits());
        assertEquals(6000L, ledgerCaptor.getValue().getBalanceAfterTokenCredits());
        assertEquals("INVITATION_CODE", ledgerCaptor.getValue().getReferenceType());
        assertEquals("INVITE-REWARD:8", ledgerCaptor.getValue().getReferenceId());
    }

    @Test
    void shouldApplyReferrerPlanAccessGroupAndRelationshipRewards() {
        InvitationCodeRepository codeRepository = Mockito.mock(InvitationCodeRepository.class);
        InvitationCodeUsageRepository usageRepository = Mockito.mock(InvitationCodeUsageRepository.class);
        GatewayUserBalanceLedgerRepository ledgerRepository = Mockito.mock(GatewayUserBalanceLedgerRepository.class);
        UserSubscriptionRepository subscriptionRepository = Mockito.mock(UserSubscriptionRepository.class);
        UserAccessGroupGrantRepository accessGroupGrantRepository = Mockito.mock(UserAccessGroupGrantRepository.class);
        InvitationRelationshipRepository relationshipRepository = Mockito.mock(InvitationRelationshipRepository.class);
        InvitationCodeRedemptionService service = new InvitationCodeRedemptionService(
                codeRepository,
                usageRepository,
                ledgerRepository,
                null,
                subscriptionRepository,
                accessGroupGrantRepository,
                relationshipRepository
        );
        GatewayUserEntity invited = user(8L);
        GatewayUserEntity owner = user(42L);
        owner.setEmail("owner@example.com");
        InvitationCodeEntity code = code(61L, "INVITE-GROWTH");
        code.setOwnerUser(owner);
        code.setRewardTokenCredits(5000L);
        code.setReferrerRewardTokenCredits(1200L);
        code.setRewardPlan(plan(7L));
        code.setRewardPlanDurationDays(15);
        code.setRewardAccessGroup(accessGroup(9L));
        code.setRewardAccessGroupDurationDays(20);
        Mockito.when(codeRepository.findFirstByCodeIgnoreCase("INVITE-GROWTH")).thenReturn(Optional.of(code));
        Mockito.when(usageRepository.existsByInvitationCode_IdAndUser_Id(61L, 8L)).thenReturn(false);
        Mockito.when(usageRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(codeRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(ledgerRepository.findByReferenceTypeAndReferenceId(Mockito.anyString(), Mockito.anyString())).thenReturn(Optional.empty());
        Mockito.when(ledgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(Mockito.anyLong())).thenReturn(Optional.empty());
        Mockito.when(ledgerRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(subscriptionRepository.findBySourceTypeAndSourceId("INVITATION_REWARD_PLAN", "INVITE-GROWTH:8")).thenReturn(Optional.empty());
        Mockito.when(subscriptionRepository.save(Mockito.any())).thenAnswer(invocation -> {
            UserSubscriptionEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 700L);
            return entity;
        });
        Mockito.when(accessGroupGrantRepository.findBySourceTypeAndSourceId("INVITATION_ACCESS_GROUP_GRANT", "INVITE-GROWTH:8")).thenReturn(Optional.empty());
        Mockito.when(accessGroupGrantRepository.save(Mockito.any())).thenAnswer(invocation -> {
            UserAccessGroupGrantEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 900L);
            return entity;
        });
        Mockito.when(relationshipRepository.findByInvitedUser_Id(Mockito.anyLong())).thenReturn(Optional.empty());
        Mockito.when(relationshipRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.redeemForRegistration("invite-growth", invited, "alpha@example.com", "INVITE_CODE", "PORTAL_REGISTER");

        ArgumentCaptor<GatewayUserBalanceLedgerEntity> ledgerCaptor = ArgumentCaptor.forClass(GatewayUserBalanceLedgerEntity.class);
        Mockito.verify(ledgerRepository, Mockito.times(2)).save(ledgerCaptor.capture());
        List<String> referenceTypes = ledgerCaptor.getAllValues().stream()
                .map(GatewayUserBalanceLedgerEntity::getReferenceType)
                .toList();
        assertEquals(List.of("INVITATION_CODE", "INVITATION_REFERRER_REWARD"), referenceTypes);
        Mockito.verify(subscriptionRepository).save(Mockito.any(UserSubscriptionEntity.class));
        Mockito.verify(accessGroupGrantRepository).save(Mockito.any(UserAccessGroupGrantEntity.class));
        ArgumentCaptor<InvitationRelationshipEntity> relationshipCaptor = ArgumentCaptor.forClass(InvitationRelationshipEntity.class);
        Mockito.verify(relationshipRepository).save(relationshipCaptor.capture());
        assertEquals(42L, relationshipCaptor.getValue().getReferrerUser().getId());
        assertEquals(8L, relationshipCaptor.getValue().getInvitedUser().getId());
        ArgumentCaptor<InvitationCodeUsageEntity> usageCaptor = ArgumentCaptor.forClass(InvitationCodeUsageEntity.class);
        Mockito.verify(usageRepository).save(usageCaptor.capture());
        assertEquals(1200L, usageCaptor.getValue().getReferrerRewardTokenCredits());
    }

    @Test
    void shouldRejectUnavailableInvitationCode() {
        InvitationCodeRepository codeRepository = Mockito.mock(InvitationCodeRepository.class);
        InvitationCodeUsageRepository usageRepository = Mockito.mock(InvitationCodeUsageRepository.class);
        InvitationCodeRedemptionService service = new InvitationCodeRedemptionService(codeRepository, usageRepository);
        InvitationCodeEntity code = code(61L, "INVITE-1");
        code.setUsedCount(1);
        code.setMaxUses(1);
        Mockito.when(codeRepository.findFirstByCodeIgnoreCase("INVITE-1")).thenReturn(Optional.of(code));

        assertThrows(IllegalArgumentException.class, () -> service.redeemForRegistration(
                "INVITE-1",
                user(8L),
                "alpha@example.com",
                "INVITE_CODE",
                "PORTAL_REGISTER"
        ));
        Mockito.verify(usageRepository, Mockito.never()).save(Mockito.any());
    }

    private InvitationCodeEntity code(Long id, String value) {
        InvitationCodeEntity code = new InvitationCodeEntity();
        ReflectionTestUtils.setField(code, "id", id);
        code.setCode(value);
        code.setActive(true);
        code.setMaxUses(1);
        code.setUsedCount(0);
        code.setExpiresAt(Instant.parse("2026-06-24T00:00:00Z"));
        return code;
    }

    private GatewayUserEntity user(Long id) {
        GatewayUserEntity user = new GatewayUserEntity();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail("alpha@example.com");
        user.setActive(true);
        return user;
    }

    private SubscriptionPlanEntity plan(Long id) {
        SubscriptionPlanEntity plan = new SubscriptionPlanEntity();
        ReflectionTestUtils.setField(plan, "id", id);
        plan.setPlanName("growth-plan");
        plan.setActive(true);
        plan.setDefaultDurationDays(30);
        return plan;
    }

    private AccessGroupEntity accessGroup(Long id) {
        AccessGroupEntity group = new AccessGroupEntity();
        ReflectionTestUtils.setField(group, "id", id);
        group.setGroupName("growth-access");
        group.setActive(true);
        return group;
    }
}
