package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.application.integrations.PlatformEventPublisher;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceActionType;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceRecoveryMode;
import com.prodigalgal.xaigateway.gateway.core.governance.QuarantineStatus;
import com.prodigalgal.xaigateway.gateway.core.routing.HealthStateStore;
import com.prodigalgal.xaigateway.infra.persistence.entity.AutoActionRuleEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.OpsAlertEventEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.QuarantineRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AutoActionRuleRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.QuarantineRecordRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GovernanceAutoActionServiceTests {

    @Test
    void shouldCreateCooldownQuarantineForCredentialEvent() {
        AutoActionRuleRepository autoActionRuleRepository = Mockito.mock(AutoActionRuleRepository.class);
        QuarantineRecordRepository quarantineRecordRepository = Mockito.mock(QuarantineRecordRepository.class);
        HealthStateStore healthStateStore = Mockito.mock(HealthStateStore.class);
        OpsAuditService opsAuditService = Mockito.mock(OpsAuditService.class);
        PlatformEventPublisher platformEventPublisher = Mockito.mock(PlatformEventPublisher.class);
        GovernanceAutoActionService service = new GovernanceAutoActionService(
                autoActionRuleRepository,
                quarantineRecordRepository,
                healthStateStore,
                opsAuditService,
                new ObjectMapper(),
                platformEventPublisher
        );

        AutoActionRuleEntity rule = new AutoActionRuleEntity();
        ReflectionTestUtils.setField(rule, "id", 10L);
        rule.setRuleName("credential-cooldown");
        rule.setEventType("REQUEST_ERROR_RATIO");
        rule.setSeverity("HIGH");
        rule.setEntityType("CREDENTIAL");
        rule.setActionType(GovernanceActionType.COOLDOWN);
        rule.setTtlSeconds(180);
        rule.setRecoveryMode(GovernanceRecoveryMode.AUTO_RESUME);
        rule.setEnabled(true);

        OpsAlertEventEntity event = new OpsAlertEventEntity();
        ReflectionTestUtils.setField(event, "id", 20L);
        event.setRuleId(1L);
        event.setEventType("REQUEST_ERROR_RATIO");
        event.setSeverity("HIGH");
        event.setTitle("error ratio");
        event.setMessage("too many errors");
        event.setEntityType("CREDENTIAL");
        event.setEntityRef("101");
        event.setMetricValue(BigDecimal.ONE);

        Mockito.when(autoActionRuleRepository.findAllByEnabledTrueOrderByCreatedAtAsc()).thenReturn(List.of(rule));
        Mockito.when(quarantineRecordRepository.findAllByStatusOrderByStartedAtDesc(QuarantineStatus.ACTIVE)).thenReturn(List.of());
        Mockito.when(quarantineRecordRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.handleAlertEvent(event);

        ArgumentCaptor<QuarantineRecordEntity> recordCaptor = ArgumentCaptor.forClass(QuarantineRecordEntity.class);
        Mockito.verify(quarantineRecordRepository).save(recordCaptor.capture());
        Mockito.verify(healthStateStore).markCooldown(Mockito.eq(101L), Mockito.eq("governance-auto-action"), Mockito.any());
        Mockito.verify(opsAuditService).record(Mockito.eq("GOVERNANCE"), Mockito.eq("AUTO_COOLDOWN"), Mockito.eq("CREDENTIAL"), Mockito.eq("101"), Mockito.anyString());
        Mockito.verify(platformEventPublisher).publish(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        QuarantineRecordEntity record = recordCaptor.getValue();
        assertEquals(GovernanceActionType.COOLDOWN, record.getActionType());
        assertEquals(101L, record.getCredentialId());
        assertNotNull(record.getExpiresAt());
    }
}
