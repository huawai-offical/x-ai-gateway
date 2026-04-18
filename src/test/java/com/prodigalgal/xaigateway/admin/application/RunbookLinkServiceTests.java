package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.infra.persistence.entity.RunbookLinkEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.RunbookLinkRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunbookLinkServiceTests {

    @Test
    void shouldPreferExactEventAndEntityMatch() {
        RunbookLinkRepository repository = Mockito.mock(RunbookLinkRepository.class);
        RunbookLinkService service = new RunbookLinkService(repository);

        RunbookLinkEntity eventOnly = new RunbookLinkEntity();
        eventOnly.setEventType("ALERT_OPENED");
        eventOnly.setLinkUrl("https://runbook/event");
        eventOnly.setEnabled(true);

        RunbookLinkEntity exact = new RunbookLinkEntity();
        exact.setEventType("ALERT_OPENED");
        exact.setEntityType("CREDENTIAL");
        exact.setLinkUrl("https://runbook/exact");
        exact.setEnabled(true);

        Mockito.when(repository.findAllByEnabledTrueOrderByCreatedAtDesc()).thenReturn(List.of(eventOnly, exact));

        assertEquals("https://runbook/exact", service.resolveUrl("ALERT_OPENED", "CREDENTIAL"));
    }
}
