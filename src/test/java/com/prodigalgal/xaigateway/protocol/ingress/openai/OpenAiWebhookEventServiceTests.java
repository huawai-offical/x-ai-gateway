package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayAsyncResourceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

class OpenAiWebhookEventServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-16T00:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPersistVerifiedWebhookEvent() {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        Mockito.when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        OpenAiWebhookEventService service = service(repository);
        String payload = """
                {"object":"event","id":"evt_1","type":"response.completed","data":{"id":"resp_1"}}
                """.trim();

        JsonNode response = service.accept(verification("wh_1", false), payload);

        assertEquals("webhook.delivery", response.path("object").asText());
        assertEquals("evt_1", response.path("event_id").asText());
        assertEquals("response.completed", response.path("type").asText());
        assertTrue(response.path("received").asBoolean());

        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(repository).save(captor.capture());
        GatewayAsyncResourceEntity entity = captor.getValue();
        assertEquals(OpenAiWebhookEventService.SYSTEM_WEBHOOK_DISTRIBUTED_KEY_ID, entity.getDistributedKeyId());
        assertEquals(GatewayAsyncResourceType.WEBHOOK_EVENT, entity.getResourceType());
        assertEquals("evt_1", entity.getResourceKey());
        assertEquals("received", entity.getStatus());
        assertTrue(entity.getMetadataJson().contains("wh_1"));
        assertTrue(entity.getResponsePayloadJson().contains("response.completed"));
    }

    @Test
    void shouldReturnDuplicateWithoutSavingForDuplicateDeliveryOrEventId() {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        OpenAiWebhookEventService service = service(repository);
        String payload = "{\"id\":\"evt_dup\",\"type\":\"response.completed\"}";

        JsonNode duplicateDelivery = service.accept(verification("wh_dup", true), payload);

        assertTrue(duplicateDelivery.path("duplicate").asBoolean());
        Mockito.verify(repository, Mockito.never()).save(any());

        Mockito.when(repository.existsByResourceKey("evt_dup")).thenReturn(true);
        JsonNode duplicateEvent = service.accept(verification("wh_new", false), payload);

        assertTrue(duplicateEvent.path("duplicate").asBoolean());
        Mockito.verify(repository, Mockito.never()).save(any());
    }

    @Test
    void shouldRejectInvalidJsonPayloadAfterSignatureVerification() {
        OpenAiWebhookEventService service = service(Mockito.mock(GatewayAsyncResourceRepository.class));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.accept(verification("wh_bad_json", false), "{not-json"));

        assertEquals("OpenAI webhook payload 不是合法 JSON。", exception.getMessage());
    }

    private OpenAiWebhookEventService service(GatewayAsyncResourceRepository repository) {
        return new OpenAiWebhookEventService(
                repository,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private OpenAiWebhookSignatureVerifier.OpenAiWebhookVerificationResult verification(String webhookId, boolean duplicate) {
        return new OpenAiWebhookSignatureVerifier.OpenAiWebhookVerificationResult(webhookId, NOW, duplicate);
    }
}
