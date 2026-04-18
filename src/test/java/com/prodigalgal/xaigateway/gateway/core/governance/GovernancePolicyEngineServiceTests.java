package com.prodigalgal.xaigateway.gateway.core.governance;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.QuarantineRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteGuardPolicyEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.QuarantineRecordRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteGuardPolicyRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernancePolicyEngineServiceTests {

    @Test
    void shouldBlockCredentialWhenActiveQuarantineExists() {
        RouteGuardPolicyRepository routeGuardPolicyRepository = Mockito.mock(RouteGuardPolicyRepository.class);
        QuarantineRecordRepository quarantineRecordRepository = Mockito.mock(QuarantineRecordRepository.class);
        GovernancePolicyEngineService service = new GovernancePolicyEngineService(routeGuardPolicyRepository, quarantineRecordRepository);

        QuarantineRecordEntity record = new QuarantineRecordEntity();
        record.setTargetType(GovernanceTargetType.CREDENTIAL);
        record.setCredentialId(101L);
        record.setActionType(GovernanceActionType.QUARANTINE);
        record.setReason("credential quarantined");
        record.setStatus(QuarantineStatus.ACTIVE);
        record.setStartedAt(Instant.now());
        record.setExpiresAt(Instant.now().plusSeconds(300));

        Mockito.when(routeGuardPolicyRepository.findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc()).thenReturn(List.of());
        Mockito.when(quarantineRecordRepository.findAllByStatusOrderByStartedAtDesc(QuarantineStatus.ACTIVE)).thenReturn(List.of(record));

        GovernanceDecision decision = service.evaluate(new GovernanceContext(ProviderType.OPENAI_DIRECT, 1L, 101L, null, null));

        assertFalse(decision.allowed());
        assertEquals("QUARANTINED", decision.healthState());
        assertEquals("credential quarantined", decision.reason());
    }

    @Test
    void shouldPreferOverrideAllowPolicy() {
        RouteGuardPolicyRepository routeGuardPolicyRepository = Mockito.mock(RouteGuardPolicyRepository.class);
        QuarantineRecordRepository quarantineRecordRepository = Mockito.mock(QuarantineRecordRepository.class);
        GovernancePolicyEngineService service = new GovernancePolicyEngineService(routeGuardPolicyRepository, quarantineRecordRepository);

        RouteGuardPolicyEntity policy = new RouteGuardPolicyEntity();
        policy.setPolicyName("allow-gemini");
        policy.setTargetType(GovernanceTargetType.PROVIDER_TYPE);
        policy.setProviderType(ProviderType.GEMINI_DIRECT);
        policy.setPolicyMode(GovernancePolicyMode.OVERRIDE_ALLOW);
        policy.setActionType(GovernanceActionType.NONE);
        policy.setEnabled(true);
        ReflectionTestUtils.setField(policy, "id", 1L);
        ReflectionTestUtils.setField(policy, "createdAt", Instant.parse("2026-04-17T08:00:00Z"));
        ReflectionTestUtils.setField(policy, "updatedAt", Instant.parse("2026-04-17T08:00:00Z"));

        Mockito.when(routeGuardPolicyRepository.findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc()).thenReturn(List.of(policy));
        Mockito.when(quarantineRecordRepository.findAllByStatusOrderByStartedAtDesc(QuarantineStatus.ACTIVE)).thenReturn(List.of());

        GovernanceDecision decision = service.evaluate(new GovernanceContext(ProviderType.GEMINI_DIRECT, 1L, 101L, null, null));

        assertTrue(decision.allowed());
        assertEquals("OVERRIDE_ALLOWED", decision.healthState());
    }
}
