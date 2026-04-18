package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.application.integrations.PlatformEventPublisher;
import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventBusService;
import com.prodigalgal.xaigateway.infra.persistence.entity.AlertSilenceEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AlertSilenceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpsAlertEventRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpsAlertRuleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAlertServiceTests {

    @Test
    void shouldMarkEventAsSilencedAndSkipAutoAction() {
        OpsAlertRuleRepository opsAlertRuleRepository = Mockito.mock(OpsAlertRuleRepository.class);
        OpsAlertEventRepository opsAlertEventRepository = Mockito.mock(OpsAlertEventRepository.class);
        OpsEventBusService opsEventBusService = Mockito.mock(OpsEventBusService.class);
        GovernanceAutoActionService governanceAutoActionService = Mockito.mock(GovernanceAutoActionService.class);
        AlertSilenceRepository alertSilenceRepository = Mockito.mock(AlertSilenceRepository.class);
        PlatformEventPublisher platformEventPublisher = Mockito.mock(PlatformEventPublisher.class);
        OpsAlertService service = new OpsAlertService(
                opsAlertRuleRepository,
                opsAlertEventRepository,
                opsEventBusService,
                governanceAutoActionService,
                alertSilenceRepository,
                platformEventPublisher
        );

        AlertSilenceEntity silence = new AlertSilenceEntity();
        Instant now = Instant.now();
        silence.setSilenceName("mute-request-error-ratio");
        silence.setEventType("REQUEST_ERROR_RATIO");
        silence.setSeverity("HIGH");
        silence.setEntityType("CREDENTIAL");
        silence.setEntityRef("101");
        silence.setStartsAt(now.minusSeconds(300));
        silence.setEndsAt(now.plusSeconds(300));
        silence.setEnabled(true);

        when(alertSilenceRepository.findAllByEnabledTrueOrderByCreatedAtDesc()).thenReturn(List.of(silence));
        when(opsAlertEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.emitEvent(
                "REQUEST_ERROR_RATIO",
                "HIGH",
                "request error spike",
                "credential unstable",
                "CREDENTIAL",
                "101",
                new BigDecimal("0.42")
        );

        assertEquals("SILENCED", response.status());
        verify(opsEventBusService, never()).publish(any(), any());
        verify(governanceAutoActionService, never()).handleAlertEvent(any());
        verify(platformEventPublisher, never()).publish(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
