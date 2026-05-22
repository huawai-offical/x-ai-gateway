package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ModelPolicyRequest;
import com.prodigalgal.xaigateway.admin.api.ModelPolicyResponse;
import com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyConflict;
import com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyResolver;
import com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyScopeType;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.infra.persistence.entity.ModelPolicyEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ModelPolicyRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelPolicyAdminServiceTests {

    @Test
    void shouldCreatePolicyWithNormalizedModelKeysAndJsonFields() {
        ModelPolicyRepository repository = Mockito.mock(ModelPolicyRepository.class);
        ModelPolicyAdminService service = service(repository, Mockito.mock(ModelPolicyResolver.class));
        when(repository.save(Mockito.any(ModelPolicyEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ModelPolicyResponse response = service.create(new ModelPolicyRequest(
                ModelPolicyScopeType.DISTRIBUTED_KEY,
                1L,
                null,
                "MAP",
                "GPT-5-Codex",
                "MiMo-V2.5-Pro",
                "coder",
                List.of("RESPONSES"),
                true,
                false,
                10,
                250,
                Map.of("tools", true),
                Map.of("extra_body", Map.of("thinking", Map.of("type", "enabled"))),
                Map.of("assistantReasoningField", "reasoning_content"),
                Map.of("canary", Map.of("weight", 250)),
                "manual",
                "Codex 映射"
        ));

        assertEquals("gpt-5-codex", response.publicModelKey());
        assertEquals("mimo-v2.5-pro", response.upstreamModelKey());
        assertEquals(List.of("responses"), response.supportedProtocols());
        assertEquals(250, response.weight());
        assertTrue((Boolean) response.capability().get("tools"));
        assertEquals("manual", response.mappingSource());
    }

    @Test
    void shouldUpdateAndDeleteExistingPolicy() {
        ModelPolicyRepository repository = Mockito.mock(ModelPolicyRepository.class);
        ModelPolicyEntity existing = new ModelPolicyEntity();
        existing.setScopeType(ModelPolicyScopeType.CREDENTIAL);
        existing.setScopeId(9L);
        existing.setPublicModel("old");
        existing.setPublicModelKey("old");
        when(repository.findById(7L)).thenReturn(Optional.of(existing));
        when(repository.save(Mockito.any(ModelPolicyEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ModelPolicyAdminService service = service(repository, Mockito.mock(ModelPolicyResolver.class));

        ModelPolicyResponse updated = service.update(7L, new ModelPolicyRequest(
                ModelPolicyScopeType.CREDENTIAL,
                9L,
                null,
                "DENY",
                "DeepSeek-Chat",
                null,
                null,
                List.of(),
                true,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "禁止该 key 使用"
        ));
        service.delete(7L);

        assertEquals("deepseek-chat", updated.publicModelKey());
        assertTrue(updated.deny());
        verify(repository).delete(existing);
    }

    @Test
    void shouldExposeConflictDetection() {
        ModelPolicyResolver resolver = Mockito.mock(ModelPolicyResolver.class);
        when(resolver.detectConflicts()).thenReturn(List.of(new ModelPolicyConflict(
                "WARN",
                "mapping_target_unreachable",
                "目标无候选",
                3L
        )));
        ModelPolicyAdminService service = service(Mockito.mock(ModelPolicyRepository.class), resolver);

        var conflicts = service.conflicts();

        assertEquals(1, conflicts.size());
        assertEquals("mapping_target_unreachable", conflicts.get(0).code());
        assertFalse(conflicts.get(0).message().isBlank());
    }

    private ModelPolicyAdminService service(ModelPolicyRepository repository, ModelPolicyResolver resolver) {
        return new ModelPolicyAdminService(
                repository,
                Mockito.mock(GatewayRouteSelectionService.class),
                resolver,
                new ObjectMapper()
        );
    }
}
