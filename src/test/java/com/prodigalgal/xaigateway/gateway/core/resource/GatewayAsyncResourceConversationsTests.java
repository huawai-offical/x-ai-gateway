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

class GatewayAsyncResourceConversationsTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateConversationWithMetadataAndInitialItems() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        Mockito.when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GatewayAsyncResourceService service = service(repository);

        JsonNode created = service.createConversation(1L, objectMapper.readTree("""
                {
                  "metadata": {"topic": "demo"},
                  "items": [
                    {"type": "message", "role": "user", "content": "Hello"},
                    {"role": "assistant", "content": "Hi"}
                  ]
                }
                """));

        assertTrue(created.path("id").asText().startsWith("conv_"));
        assertEquals("conversation", created.path("object").asText());
        assertEquals(1778803200L, created.path("created_at").asLong());
        assertEquals("demo", created.path("metadata").path("topic").asText());

        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(repository, Mockito.times(3)).save(captor.capture());
        List<GatewayAsyncResourceEntity> saved = captor.getAllValues();
        assertEquals(GatewayAsyncResourceType.CONVERSATION, saved.get(0).getResourceType());
        assertEquals(GatewayAsyncResourceType.CONVERSATION_ITEM, saved.get(1).getResourceType());
        assertEquals(created.path("id").asText(), saved.get(1).getUpstreamObjectId());
        assertTrue(saved.get(1).getResourceKey().startsWith("msg_"));
        assertTrue(saved.get(2).getResponsePayloadJson().contains("\"type\":\"message\""));
    }

    @Test
    void shouldListConversationItemsWithDescendingCursorAndLimit() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayAsyncResourceService service = service(repository);
        GatewayAsyncResourceEntity conversation = conversation("conv_1");
        GatewayAsyncResourceEntity item3 = item("msg_3", "conv_1", 3);
        GatewayAsyncResourceEntity item2 = item("msg_2", "conv_1", 2);
        GatewayAsyncResourceEntity item1 = item("msg_1", "conv_1", 1);
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "conv_1",
                GatewayAsyncResourceType.CONVERSATION
        )).thenReturn(Optional.of(conversation));
        Mockito.when(repository.findChildResourcesAfterCursorDesc(
                Mockito.eq(1L),
                Mockito.eq(GatewayAsyncResourceType.CONVERSATION_ITEM),
                Mockito.eq("conv_1"),
                Mockito.<Instant>nullable(Instant.class),
                Mockito.<Long>nullable(Long.class),
                Mockito.any(Pageable.class)
        )).thenReturn(List.of(item3, item2));
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDistributedKeyIdAndDeletedFalse(
                "msg_3",
                GatewayAsyncResourceType.CONVERSATION_ITEM,
                1L
        )).thenReturn(Optional.of(item3));

        JsonNode firstPage = service.listConversationItems("conv_1", 1L, null, null, 1, null);

        assertEquals("list", firstPage.path("object").asText());
        assertEquals("msg_3", firstPage.path("data").path(0).path("id").asText());
        assertTrue(firstPage.path("has_more").asBoolean());
        assertEquals("msg_3", firstPage.path("first_id").asText());

        Mockito.when(repository.findChildResourcesAfterCursorDesc(
                Mockito.eq(1L),
                Mockito.eq(GatewayAsyncResourceType.CONVERSATION_ITEM),
                Mockito.eq("conv_1"),
                Mockito.eq(item3.getCreatedAt()),
                Mockito.eq(item3.getId()),
                Mockito.any(Pageable.class)
        )).thenReturn(List.of(item2, item1));

        JsonNode secondPage = service.listConversationItems("conv_1", 1L, "msg_3", null, 10, "desc");

        assertEquals(2, secondPage.path("data").size());
        assertEquals("msg_2", secondPage.path("data").path(0).path("id").asText());
        assertEquals("msg_1", secondPage.path("data").path(1).path("id").asText());
        assertFalse(secondPage.path("has_more").asBoolean());
    }

    @Test
    void shouldAppendRetrieveAndDeleteConversationItem() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        Mockito.when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GatewayAsyncResourceService service = service(repository);
        GatewayAsyncResourceEntity conversation = conversation("conv_1");
        GatewayAsyncResourceEntity item = item("msg_existing", "conv_1", 1);
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "conv_1",
                GatewayAsyncResourceType.CONVERSATION
        )).thenReturn(Optional.of(conversation));
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "msg_existing",
                GatewayAsyncResourceType.CONVERSATION_ITEM
        )).thenReturn(Optional.of(item));
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDistributedKeyIdAndDeletedFalse(
                "msg_existing",
                GatewayAsyncResourceType.CONVERSATION_ITEM,
                1L
        )).thenReturn(Optional.of(item));

        JsonNode created = service.createConversationItems("conv_1", 1L, objectMapper.readTree("""
                {
                  "items": [
                    {"role": "user", "content": "Second"}
                  ]
                }
                """), null);

        assertEquals("list", created.path("object").asText());
        assertTrue(created.path("data").path(0).path("id").asText().startsWith("msg_"));
        assertEquals("message", created.path("data").path(0).path("type").asText());

        JsonNode retrieved = service.getConversationItem("conv_1", "msg_existing", 1L, null);

        assertEquals("msg_existing", retrieved.path("id").asText());

        JsonNode afterDelete = service.deleteConversationItem("conv_1", "msg_existing", 1L);

        assertEquals("conversation", afterDelete.path("object").asText());
        assertTrue(item.isDeleted());
        assertEquals("deleted", item.getStatus());
    }

    @Test
    void shouldRejectOversizedBatchDuplicateItemIdAndInvalidListOptions() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayAsyncResourceService service = service(repository);
        GatewayAsyncResourceEntity conversation = conversation("conv_1");
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "conv_1",
                GatewayAsyncResourceType.CONVERSATION
        )).thenReturn(Optional.of(conversation));

        StringBuilder oversizedItems = new StringBuilder("{\"items\":[");
        for (int index = 0; index < 21; index++) {
            if (index > 0) {
                oversizedItems.append(',');
            }
            oversizedItems.append("{\"role\":\"user\",\"content\":\"").append(index).append("\"}");
        }
        oversizedItems.append("]}");

        IllegalArgumentException oversized = assertThrows(
                IllegalArgumentException.class,
                () -> service.createConversationItems("conv_1", 1L, objectMapper.readTree(oversizedItems.toString()), null));
        assertEquals("每次最多添加 20 个 Conversation Item。", oversized.getMessage());

        IllegalArgumentException duplicate = assertThrows(
                IllegalArgumentException.class,
                () -> service.createConversationItems("conv_1", 1L, objectMapper.readTree("""
                        {
                          "items": [
                            {"id": "msg_dup", "role": "user", "content": "a"},
                            {"id": "msg_dup", "role": "user", "content": "b"}
                          ]
                        }
                        """), null));
        assertEquals("Conversation Item id 重复。", duplicate.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> service.listConversationItems("conv_1", 1L, null, null, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.listConversationItems("conv_1", 1L, null, null, 20, "newest"));
    }

    @Test
    void shouldUpdateAndDeleteConversationWithoutDeletingItemLineage() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        Mockito.when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GatewayAsyncResourceService service = service(repository);
        GatewayAsyncResourceEntity conversation = conversation("conv_1");
        GatewayAsyncResourceEntity item = item("msg_1", "conv_1", 1);
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "conv_1",
                GatewayAsyncResourceType.CONVERSATION
        )).thenReturn(Optional.of(conversation));

        JsonNode updated = service.updateConversation("conv_1", 1L, objectMapper.readTree("""
                {"metadata": {"topic": "updated"}}
                """));

        assertEquals("updated", updated.path("metadata").path("topic").asText());
        assertTrue(conversation.getMetadataJson().contains("metadata_updated"));

        JsonNode deleted = service.deleteConversation("conv_1", 1L);

        assertEquals("conversation.deleted", deleted.path("object").asText());
        assertTrue(deleted.path("deleted").asBoolean());
        assertTrue(conversation.isDeleted());
        assertFalse(item.isDeleted());
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

    private GatewayAsyncResourceEntity conversation(String id) throws Exception {
        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        ReflectionTestUtils.setField(entity, "id", 100L);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-05-15T00:00:00Z"));
        entity.setResourceKey(id);
        entity.setDistributedKeyId(1L);
        entity.setResourceType(GatewayAsyncResourceType.CONVERSATION);
        entity.setStatus("active");
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", id);
        response.put("object", "conversation");
        response.put("created_at", 1778803200L);
        response.set("metadata", objectMapper.createObjectNode().put("topic", "demo"));
        entity.setRequestPayloadJson("{}");
        entity.setResponsePayloadJson(objectMapper.writeValueAsString(response));
        entity.setMetadataJson("{}");
        return entity;
    }

    private GatewayAsyncResourceEntity item(String id, String conversationId, long sequence) throws Exception {
        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        ReflectionTestUtils.setField(entity, "id", sequence);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-05-15T00:00:00Z").plusSeconds(sequence));
        entity.setResourceKey(id);
        entity.setDistributedKeyId(1L);
        entity.setResourceType(GatewayAsyncResourceType.CONVERSATION_ITEM);
        entity.setUpstreamObjectId(conversationId);
        entity.setStatus("completed");
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", id);
        response.put("type", "message");
        response.put("role", "user");
        response.put("content", "hello " + sequence);
        response.put("status", "completed");
        entity.setRequestPayloadJson(objectMapper.writeValueAsString(response));
        entity.setResponsePayloadJson(objectMapper.writeValueAsString(response));
        entity.setMetadataJson("{}");
        return entity;
    }
}
