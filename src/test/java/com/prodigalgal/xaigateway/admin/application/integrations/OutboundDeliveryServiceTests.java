package com.prodigalgal.xaigateway.admin.application.integrations;

import com.prodigalgal.xaigateway.infra.persistence.entity.NotificationChannelEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.OutboundDeliveryEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.NotificationChannelRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.OutboundDeliveryRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;

class OutboundDeliveryServiceTests {

    @Test
    void shouldMoveToFailedAndScheduleRetryWhenDispatchFails() throws Exception {
        OutboundDeliveryRepository deliveryRepository = Mockito.mock(OutboundDeliveryRepository.class);
        NotificationChannelRepository channelRepository = Mockito.mock(NotificationChannelRepository.class);
        OutboundChannelDispatcher channelDispatcher = Mockito.mock(OutboundChannelDispatcher.class);
        OutboundDeliveryService service = new OutboundDeliveryService(deliveryRepository, channelRepository, channelDispatcher, new ObjectMapper());

        NotificationChannelEntity channel = new NotificationChannelEntity();
        ReflectionTestUtils.setField(channel, "id", 3L);
        channel.setChannelName("ops-hook");
        channel.setChannelType(NotificationChannelType.WEBHOOK.name());

        OutboundDeliveryEntity delivery = new OutboundDeliveryEntity();
        ReflectionTestUtils.setField(delivery, "id", 9L);
        delivery.setEventId("evt-1");
        delivery.setEventType("ALERT_OPENED");
        delivery.setChannelId(3L);
        delivery.setDeliveryStatus(OutboundDeliveryStatus.PENDING.name());
        delivery.setAttemptCount(0);
        delivery.setPayloadJson(new ObjectMapper().writeValueAsString(new OutboundEventEnvelope(
                "evt-1",
                "ALERT_OPENED",
                Instant.now(),
                "HIGH",
                "OPS_ALERT",
                "CREDENTIAL",
                "101",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "summary",
                java.util.Map.of(),
                null,
                null,
                "x-ai-gateway"
        )));
        delivery.setOccurredAt(Instant.now());

        Mockito.when(deliveryRepository.findById(9L)).thenReturn(Optional.of(delivery));
        Mockito.when(channelRepository.findById(3L)).thenReturn(Optional.of(channel));
        Mockito.when(channelDispatcher.dispatch(any(), any()))
                .thenReturn(new OutboundDispatchResult(false, 500, "bad gateway", "bad gateway"));
        Mockito.when(deliveryRepository.save(any(OutboundDeliveryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.deliver(9L);

        assertEquals("FAILED", response.deliveryStatus());
        assertEquals(1, response.attemptCount());
        assertNotNull(response.nextRetryAt());
    }
}
