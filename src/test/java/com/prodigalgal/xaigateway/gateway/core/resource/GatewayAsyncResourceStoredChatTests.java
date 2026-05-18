package com.prodigalgal.xaigateway.gateway.core.resource;

import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayAsyncResourceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class GatewayAsyncResourceStoredChatTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldStoreChatCompletionAsScopedResponseResource() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        Mockito.when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GatewayAsyncResourceService service = service(repository);

        JsonNode stored = service.storeChatCompletion(
                1L,
                "gpt-4o",
                requestPayload(),
                responsePayload("upstream_chatcmpl_1", "gpt-4o", Map.of("purpose", "qa"))
        );

        assertTrue(stored.path("id").asText().startsWith("chatcmpl_"));
        assertEquals("chat.completion", stored.path("object").asText());
        assertEquals("completed", stored.path("status").asText());

        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(repository).save(captor.capture());
        GatewayAsyncResourceEntity entity = captor.getValue();
        assertEquals(1L, entity.getDistributedKeyId());
        assertEquals(GatewayAsyncResourceType.RESPONSE, entity.getResourceType());
        assertEquals("gpt-4o", entity.getRequestModel());
        assertEquals(stored.path("id").asText(), entity.getResourceKey());
        assertTrue(entity.getMetadataJson().contains("gateway_stored_chat_completion"));
        assertTrue(entity.getRequestPayloadJson().contains("\"messages\""));
        assertTrue(entity.getResponsePayloadJson().contains("\"chat.completion\""));
    }

    @Test
    void shouldListStoredChatCompletionsWithModelMetadataCursorAndLimit() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayAsyncResourceService service = service(repository);
        GatewayAsyncResourceEntity chat2 = entity("chatcmpl_2", "gpt-4o", "chat.completion", Map.of("purpose", "qa"));
        GatewayAsyncResourceEntity chat1 = entity("chatcmpl_1", "gpt-4o", "chat.completion", Map.of("purpose", "qa"));
        Mockito.when(repository.findStoredResourcesAfterCursorAsc(
                Mockito.eq(1L),
                Mockito.eq(GatewayAsyncResourceType.RESPONSE),
                Mockito.eq("chatcmpl_"),
                Mockito.eq("gpt-4o"),
                Mockito.<Instant>nullable(Instant.class),
                Mockito.<Long>nullable(Long.class),
                Mockito.any(Pageable.class)
        )).thenReturn(List.of(chat1, chat2), List.of(chat2));
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDistributedKeyIdAndDeletedFalse(
                "chatcmpl_1",
                GatewayAsyncResourceType.RESPONSE,
                1L
        )).thenReturn(Optional.of(chat1));
        Mockito.when(repository.findStoredResourcesAfterCursorDesc(
                Mockito.eq(1L),
                Mockito.eq(GatewayAsyncResourceType.RESPONSE),
                Mockito.eq("chatcmpl_"),
                Mockito.eq("gpt-4o"),
                Mockito.<Instant>nullable(Instant.class),
                Mockito.<Long>nullable(Long.class),
                Mockito.any(Pageable.class)
        )).thenReturn(List.of(chat2, chat1));

        JsonNode firstPage = service.listChatCompletions(1L, null, 1, "gpt-4o", null, Map.of("purpose", "qa"));

        assertEquals("list", firstPage.path("object").asText());
        assertEquals("chatcmpl_1", firstPage.path("data").path(0).path("id").asText());
        assertTrue(firstPage.path("has_more").asBoolean());
        assertEquals("chatcmpl_1", firstPage.path("first_id").asText());
        assertEquals("chatcmpl_1", firstPage.path("last_id").asText());

        JsonNode secondPage = service.listChatCompletions(1L, "chatcmpl_1", 10, "gpt-4o", null, Map.of("purpose", "qa"));

        assertEquals(1, secondPage.path("data").size());
        assertEquals("chatcmpl_2", secondPage.path("data").path(0).path("id").asText());
        assertFalse(secondPage.path("has_more").asBoolean());

        JsonNode descending = service.listChatCompletions(1L, null, 1, "gpt-4o", "desc", Map.of("purpose", "qa"));

        assertEquals("chatcmpl_2", descending.path("data").path(0).path("id").asText());
        assertTrue(descending.path("has_more").asBoolean());
        Mockito.verify(repository, Mockito.never()).search(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(Pageable.class));
    }

    @Test
    void shouldContinueDatabaseCursorScanUntilMetadataFilterMatches() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayAsyncResourceService service = service(repository);
        GatewayAsyncResourceEntity other1 = entity("chatcmpl_1", "gpt-4o", "chat.completion", Map.of("purpose", "other"));
        GatewayAsyncResourceEntity other2 = entity("chatcmpl_2", "gpt-4o", "chat.completion", Map.of("purpose", "other"));
        GatewayAsyncResourceEntity match = entity("chatcmpl_3", "gpt-4o", "chat.completion", Map.of("purpose", "qa"));
        Mockito.when(repository.findStoredResourcesAfterCursorAsc(
                Mockito.eq(1L),
                Mockito.eq(GatewayAsyncResourceType.RESPONSE),
                Mockito.eq("chatcmpl_"),
                Mockito.eq("gpt-4o"),
                Mockito.<Instant>nullable(Instant.class),
                Mockito.<Long>nullable(Long.class),
                Mockito.any(Pageable.class)
        )).thenReturn(List.of(other1, other2), List.of(match));

        JsonNode result = service.listChatCompletions(1L, null, 1, "gpt-4o", null, Map.of("purpose", "qa"));

        assertEquals(1, result.path("data").size());
        assertEquals("chatcmpl_3", result.path("data").path(0).path("id").asText());
        assertFalse(result.path("has_more").asBoolean());
        Mockito.verify(repository, Mockito.times(2)).findStoredResourcesAfterCursorAsc(
                Mockito.eq(1L),
                Mockito.eq(GatewayAsyncResourceType.RESPONSE),
                Mockito.eq("chatcmpl_"),
                Mockito.eq("gpt-4o"),
                Mockito.<Instant>nullable(Instant.class),
                Mockito.<Long>nullable(Long.class),
                Mockito.any(Pageable.class));
    }

    @Test
    void shouldReturnEmptyStoredChatListWhenCursorDoesNotMatchCurrentFilter() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayAsyncResourceService service = service(repository);
        GatewayAsyncResourceEntity cursor = entity("chatcmpl_1", "gpt-4o", "chat.completion", Map.of("purpose", "other"));
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDistributedKeyIdAndDeletedFalse(
                "chatcmpl_1",
                GatewayAsyncResourceType.RESPONSE,
                1L
        )).thenReturn(Optional.of(cursor));

        JsonNode result = service.listChatCompletions(1L, "chatcmpl_1", 10, "gpt-4o", null, Map.of("purpose", "qa"));

        assertEquals("list", result.path("object").asText());
        assertEquals(0, result.path("data").size());
        assertFalse(result.path("has_more").asBoolean());
        Mockito.verify(repository, Mockito.never()).findStoredResourcesAfterCursorAsc(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(Pageable.class));
    }

    @Test
    void shouldUpdateDeleteAndPageStoredChatCompletionMessages() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        Mockito.when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GatewayAsyncResourceService service = service(repository);
        GatewayAsyncResourceEntity entity = entity("chatcmpl_1", "gpt-4o", "chat.completion", Map.of("purpose", "qa"));
        entity.setRequestPayloadJson(writeJson(requestPayloadWithMessageIds()));
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "chatcmpl_1",
                GatewayAsyncResourceType.RESPONSE
        )).thenReturn(Optional.of(entity));

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("purpose", "updated");
        JsonNode updated = service.updateChatCompletionMetadata("chatcmpl_1", 1L, metadata);

        assertEquals("updated", updated.path("metadata").path("purpose").asText());
        assertTrue(entity.getMetadataJson().contains("metadata_updated"));

        JsonNode defaultOrder = service.listChatCompletionMessages("chatcmpl_1", 1L, null, 1, null);

        assertEquals("msg_1", defaultOrder.path("data").path(0).path("id").asText());
        assertTrue(defaultOrder.path("has_more").asBoolean());

        JsonNode messages = service.listChatCompletionMessages("chatcmpl_1", 1L, "msg_3", 1, "desc");

        assertEquals("list", messages.path("object").asText());
        assertEquals("msg_2", messages.path("data").path(0).path("id").asText());
        assertTrue(messages.path("has_more").asBoolean());
        assertEquals("msg_2", messages.path("first_id").asText());
        assertEquals("msg_2", messages.path("last_id").asText());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.listChatCompletionMessages("chatcmpl_1", 1L, null, 0, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.listChatCompletionMessages("chatcmpl_1", 1L, null, 101, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.listChatCompletionMessages("chatcmpl_1", 1L, null, 20, "newest"));

        JsonNode deleted = service.deleteChatCompletion("chatcmpl_1", 1L);

        assertEquals("chat.completion.deleted", deleted.path("object").asText());
        assertTrue(deleted.path("deleted").asBoolean());
        assertTrue(entity.isDeleted());
        assertEquals("deleted", entity.getStatus());
    }

    @Test
    void shouldCancelBackgroundResponseAndListInputItems() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        Mockito.when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GatewayAsyncResourceService service = service(repository);
        GatewayAsyncResourceEntity entity = entity("resp_lifecycle_1", "gpt-4o", "response", Map.of("purpose", "qa"));
        entity.setStatus("in_progress");
        entity.setRequestPayloadJson(writeJson(responsesRequestPayload(true)));
        ObjectNode response = responsePayload("resp_lifecycle_1", "gpt-4o", Map.of("purpose", "qa"));
        response.put("object", "response");
        response.put("status", "in_progress");
        entity.setResponsePayloadJson(writeJson(response));
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "resp_lifecycle_1",
                GatewayAsyncResourceType.RESPONSE
        )).thenReturn(Optional.of(entity));

        JsonNode inputItems = service.listResponseInputItems("resp_lifecycle_1", 1L, null, 1, null);

        assertEquals("list", inputItems.path("object").asText());
        assertEquals("msg_2", inputItems.path("data").path(0).path("id").asText());
        assertEquals("function_call_output", inputItems.path("data").path(0).path("type").asText());
        assertTrue(inputItems.path("has_more").asBoolean());
        assertEquals("msg_2", inputItems.path("first_id").asText());

        JsonNode nextPage = service.listResponseInputItems("resp_lifecycle_1", 1L, "msg_2", 10, "desc");

        assertEquals(2, nextPage.path("data").size());
        assertEquals("msg_1", nextPage.path("data").path(0).path("id").asText());
        assertEquals("msg_resp_lifecycle_1_0", nextPage.path("data").path(1).path("id").asText());
        assertEquals("message", nextPage.path("data").path(1).path("type").asText());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.listResponseInputItems("resp_lifecycle_1", 1L, null, 0, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.listResponseInputItems("resp_lifecycle_1", 1L, null, 101, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.listResponseInputItems("resp_lifecycle_1", 1L, null, 20, "newest"));

        JsonNode cancelled = service.cancelResponse("resp_lifecycle_1", 1L);

        assertEquals("response", cancelled.path("object").asText());
        assertEquals("cancelled", cancelled.path("status").asText());
        assertEquals(1778803200L, cancelled.path("cancelled_at").asLong());
        assertEquals("cancelled", entity.getStatus());
        assertTrue(entity.getMetadataJson().contains("cancelled"));
    }

    @Test
    void shouldRejectCancelForCompletedOrNonBackgroundResponse() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayAsyncResourceService service = service(repository);
        GatewayAsyncResourceEntity completed = entity("resp_done_1", "gpt-4o", "response", Map.of());
        completed.setStatus("completed");
        completed.setRequestPayloadJson(writeJson(responsesRequestPayload(true)));
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "resp_done_1",
                GatewayAsyncResourceType.RESPONSE
        )).thenReturn(Optional.of(completed));

        IllegalArgumentException completedError = assertThrows(
                IllegalArgumentException.class,
                () -> service.cancelResponse("resp_done_1", 1L));
        assertEquals("已完成的 Response 不允许取消。", completedError.getMessage());

        GatewayAsyncResourceEntity foreground = entity("resp_foreground_1", "gpt-4o", "response", Map.of());
        foreground.setStatus("in_progress");
        foreground.setRequestPayloadJson(writeJson(responsesRequestPayload(false)));
        ObjectNode foregroundResponse = responsePayload("resp_foreground_1", "gpt-4o", Map.of());
        foregroundResponse.put("object", "response");
        foregroundResponse.put("status", "in_progress");
        foreground.setResponsePayloadJson(writeJson(foregroundResponse));
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "resp_foreground_1",
                GatewayAsyncResourceType.RESPONSE
        )).thenReturn(Optional.of(foreground));

        IllegalArgumentException foregroundError = assertThrows(
                IllegalArgumentException.class,
                () -> service.cancelResponse("resp_foreground_1", 1L));
        assertEquals("只有 background=true 的 Response 支持取消。", foregroundError.getMessage());
    }

    private GatewayAsyncResourceService service(GatewayAsyncResourceRepository repository) {
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        return new GatewayAsyncResourceService(
                repository,
                Mockito.mock(DistributedKeyQueryService.class),
                Mockito.mock(UpstreamCredentialRepository.class),
                Mockito.mock(UpstreamSiteProfileRepository.class),
                snapshotRepository,
                Mockito.mock(GatewayFileRepository.class),
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                objectMapper,
                Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );
    }

    private GatewayAsyncResourceEntity entity(String id, String model, String object, Map<String, String> metadata) throws Exception {
        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        long sequence = Math.abs(id.hashCode() % 1000L) + 1L;
        ReflectionTestUtils.setField(entity, "id", sequence);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-05-15T00:00:00Z").plusSeconds(sequence));
        entity.setResourceKey(id);
        entity.setDistributedKeyId(1L);
        entity.setResourceType(GatewayAsyncResourceType.RESPONSE);
        entity.setRequestModel(model);
        entity.setStatus("completed");
        entity.setRequestPayloadJson(writeJson(requestPayload()));
        entity.setResponsePayloadJson(writeJson(responsePayload(id, model, metadata).put("object", object)));
        entity.setMetadataJson("{}");
        return entity;
    }

    private ObjectNode requestPayload() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", "gpt-4o");
        var messages = objectMapper.createArrayNode();
        messages.add(objectMapper.createObjectNode().put("role", "user").put("content", "hello"));
        request.set("messages", messages);
        return request;
    }

    private ObjectNode requestPayloadWithMessageIds() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", "gpt-4o");
        var messages = objectMapper.createArrayNode();
        messages.add(objectMapper.createObjectNode().put("id", "msg_1").put("role", "system").put("content", "system"));
        messages.add(objectMapper.createObjectNode().put("id", "msg_2").put("role", "user").put("content", "hello"));
        messages.add(objectMapper.createObjectNode().put("id", "msg_3").put("role", "assistant").put("content", "hi"));
        request.set("messages", messages);
        return request;
    }

    private ObjectNode responsesRequestPayload(boolean background) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", "gpt-4o");
        request.put("background", background);
        var input = objectMapper.createArrayNode();
        input.add("hello");
        input.add(objectMapper.createObjectNode()
                .put("id", "msg_1")
                .put("type", "message")
                .put("role", "user")
                .set("content", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("type", "input_text")
                                .put("text", "second"))));
        input.add(objectMapper.createObjectNode()
                .put("id", "msg_2")
                .put("type", "function_call_output")
                .put("call_id", "call_1")
                .put("output", "tool result"));
        request.set("input", input);
        return request;
    }

    private ObjectNode responsePayload(String id, String model, Map<String, String> metadata) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", id);
        response.put("object", "chat.completion");
        response.put("status", "completed");
        response.put("model", model);
        ObjectNode metadataNode = objectMapper.createObjectNode();
        metadata.forEach(metadataNode::put);
        response.set("metadata", metadataNode);
        return response;
    }

    private String writeJson(JsonNode node) throws Exception {
        return objectMapper.writeValueAsString(node);
    }
}
