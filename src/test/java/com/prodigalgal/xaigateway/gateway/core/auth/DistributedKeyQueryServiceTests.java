package com.prodigalgal.xaigateway.gateway.core.auth;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccountGroupBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountGroupBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DistributedKeyQueryServiceTests {

    @Test
    void shouldExpandAccountGroupCredentialsForActiveDistributedKey() {
        Fixture fixture = fixture();
        DistributedKeyEntity key = distributedKey(7L);
        DistributedKeyAccountGroupBindingEntity groupBinding = groupBinding(21L, key, 301L, ProviderType.OPENAI_COMPATIBLE, 30);
        UpstreamCredentialEntity first = credential(101L, 301L, ProviderType.OPENAI_COMPATIBLE, "mimo-openai-1");
        UpstreamCredentialEntity second = credential(102L, 301L, ProviderType.OPENAI_COMPATIBLE, "mimo-openai-2");

        when(fixture.distributedKeyRepository.findByKeyPrefixAndActiveTrue("sk-gw-test"))
                .thenReturn(Optional.of(key));
        when(fixture.accountGroupBindingRepository.countByDistributedKey_IdAndActiveTrueAndGroup_ActiveTrue(7L))
                .thenReturn(1L);
        when(fixture.bindingRepository.findAllByDistributedKeyIdAndActiveTrueOrderByPriorityAscCreatedAtAsc(7L))
                .thenReturn(List.of());
        when(fixture.accountGroupBindingRepository.findAllByDistributedKey_IdAndActiveTrueOrderByPriorityAscCreatedAtAsc(7L))
                .thenReturn(List.of(groupBinding));
        when(fixture.credentialRepository.findAllByGroupIdAndProviderTypeAndDeletedFalseAndActiveTrueOrderByCreatedAtAsc(
                301L,
                ProviderType.OPENAI_COMPATIBLE
        )).thenReturn(List.of(first, second));
        when(fixture.accessGroupEntitlementService.resolveForDistributedKey(key))
                .thenReturn(ResolvedAccessPolicy.empty());

        DistributedKeyView view = fixture.service.findActiveByKeyPrefix("sk-gw-test").orElseThrow();

        assertEquals(2, view.bindings().size());
        assertEquals(List.of(101L, 102L), view.bindings().stream().map(DistributedCredentialBindingView::credentialId).toList());
        assertTrue(view.bindings().stream().allMatch(binding -> binding.bindingId() == null));
        assertTrue(view.bindings().stream().allMatch(binding -> binding.priority() == 30));
        assertTrue(view.bindings().stream().allMatch(binding -> binding.weight() == 100));
        verify(fixture.credentialRepository)
                .findAllByGroupIdAndProviderTypeAndDeletedFalseAndActiveTrueOrderByCreatedAtAsc(
                        301L,
                        ProviderType.OPENAI_COMPATIBLE
                );
    }

    @Test
    void shouldPreferDirectBindingWhenCredentialIsAlsoExpandedFromGroup() {
        Fixture fixture = fixture();
        DistributedKeyEntity key = distributedKey(7L);
        UpstreamCredentialEntity credential = credential(101L, 301L, ProviderType.OPENAI_COMPATIBLE, "mimo-openai");
        DistributedKeyBindingEntity directBinding = directBinding(11L, key, credential, 5, 80);
        DistributedKeyAccountGroupBindingEntity groupBinding = groupBinding(21L, key, 301L, ProviderType.OPENAI_COMPATIBLE, 30);

        when(fixture.distributedKeyRepository.findByIdAndActiveTrue(7L))
                .thenReturn(Optional.of(key));
        when(fixture.accountGroupBindingRepository.countByDistributedKey_IdAndActiveTrueAndGroup_ActiveTrue(7L))
                .thenReturn(1L);
        when(fixture.bindingRepository.findAllByDistributedKeyIdAndActiveTrueOrderByPriorityAscCreatedAtAsc(7L))
                .thenReturn(List.of(directBinding));
        when(fixture.accountGroupBindingRepository.findAllByDistributedKey_IdAndActiveTrueOrderByPriorityAscCreatedAtAsc(7L))
                .thenReturn(List.of(groupBinding));
        when(fixture.credentialRepository.findAllByGroupIdAndProviderTypeAndDeletedFalseAndActiveTrueOrderByCreatedAtAsc(
                301L,
                ProviderType.OPENAI_COMPATIBLE
        )).thenReturn(List.of(credential));
        when(fixture.accessGroupEntitlementService.resolveForDistributedKey(key))
                .thenReturn(ResolvedAccessPolicy.empty());

        DistributedKeyView view = fixture.service.findActiveById(7L).orElseThrow();

        assertEquals(1, view.bindings().size());
        DistributedCredentialBindingView binding = view.bindings().getFirst();
        assertEquals(11L, binding.bindingId());
        assertEquals(101L, binding.credentialId());
        assertEquals(5, binding.priority());
        assertEquals(80, binding.weight());
    }

    @Test
    void shouldFilterExpandedCredentialsByAccountGroupBindingProviderType() {
        Fixture fixture = fixture();
        DistributedKeyEntity key = distributedKey(7L);
        DistributedKeyAccountGroupBindingEntity groupBinding = groupBinding(21L, key, 301L, ProviderType.ANTHROPIC_DIRECT, 20);
        UpstreamCredentialEntity anthropic = credential(201L, 301L, ProviderType.ANTHROPIC_DIRECT, "anthropic-direct");

        when(fixture.distributedKeyRepository.findByIdAndActiveTrue(7L))
                .thenReturn(Optional.of(key));
        when(fixture.accountGroupBindingRepository.countByDistributedKey_IdAndActiveTrueAndGroup_ActiveTrue(7L))
                .thenReturn(1L);
        when(fixture.bindingRepository.findAllByDistributedKeyIdAndActiveTrueOrderByPriorityAscCreatedAtAsc(7L))
                .thenReturn(List.of());
        when(fixture.accountGroupBindingRepository.findAllByDistributedKey_IdAndActiveTrueOrderByPriorityAscCreatedAtAsc(7L))
                .thenReturn(List.of(groupBinding));
        when(fixture.credentialRepository.findAllByGroupIdAndProviderTypeAndDeletedFalseAndActiveTrueOrderByCreatedAtAsc(
                301L,
                ProviderType.ANTHROPIC_DIRECT
        )).thenReturn(List.of(anthropic));
        when(fixture.accessGroupEntitlementService.resolveForDistributedKey(key))
                .thenReturn(ResolvedAccessPolicy.empty());

        DistributedKeyView view = fixture.service.findActiveById(7L).orElseThrow();

        assertEquals(1, view.bindings().size());
        assertEquals(ProviderType.ANTHROPIC_DIRECT, view.bindings().getFirst().providerType());
        verify(fixture.credentialRepository)
                .findAllByGroupIdAndProviderTypeAndDeletedFalseAndActiveTrueOrderByCreatedAtAsc(
                        301L,
                        ProviderType.ANTHROPIC_DIRECT
                );
    }

    @Test
    void shouldSkipInactiveAccountGroupWhenExpandingCredentials() {
        Fixture fixture = fixture();
        DistributedKeyEntity key = distributedKey(7L);
        DistributedKeyAccountGroupBindingEntity groupBinding = groupBinding(21L, key, 301L, ProviderType.OPENAI_COMPATIBLE, 20);
        groupBinding.getGroup().setActive(false);

        when(fixture.distributedKeyRepository.findByIdAndActiveTrue(7L))
                .thenReturn(Optional.of(key));
        when(fixture.accountGroupBindingRepository.countByDistributedKey_IdAndActiveTrueAndGroup_ActiveTrue(7L))
                .thenReturn(1L);
        when(fixture.bindingRepository.findAllByDistributedKeyIdAndActiveTrueOrderByPriorityAscCreatedAtAsc(7L))
                .thenReturn(List.of());
        when(fixture.accountGroupBindingRepository.findAllByDistributedKey_IdAndActiveTrueOrderByPriorityAscCreatedAtAsc(7L))
                .thenReturn(List.of(groupBinding));
        when(fixture.accessGroupEntitlementService.resolveForDistributedKey(key))
                .thenReturn(ResolvedAccessPolicy.empty());

        DistributedKeyView view = fixture.service.findActiveById(7L).orElseThrow();

        assertTrue(view.bindings().isEmpty());
        verifyNoInteractions(fixture.credentialRepository);
    }

    @Test
    void shouldRejectActiveKeyWithoutActiveAccountGroupBinding() {
        Fixture fixture = fixture();
        DistributedKeyEntity key = distributedKey(7L);

        when(fixture.distributedKeyRepository.findByIdAndActiveTrue(7L))
                .thenReturn(Optional.of(key));
        when(fixture.accountGroupBindingRepository.countByDistributedKey_IdAndActiveTrueAndGroup_ActiveTrue(7L))
                .thenReturn(0L);

        assertTrue(fixture.service.findActiveById(7L).isEmpty());
        verifyNoInteractions(fixture.bindingRepository, fixture.credentialRepository, fixture.accessGroupEntitlementService);
    }

    private Fixture fixture() {
        DistributedKeyRepository distributedKeyRepository = Mockito.mock(DistributedKeyRepository.class);
        DistributedKeyBindingRepository bindingRepository = Mockito.mock(DistributedKeyBindingRepository.class);
        DistributedKeyAccountGroupBindingRepository accountGroupBindingRepository =
                Mockito.mock(DistributedKeyAccountGroupBindingRepository.class);
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        AccessGroupEntitlementService accessGroupEntitlementService = Mockito.mock(AccessGroupEntitlementService.class);
        DistributedKeyQueryService service = new DistributedKeyQueryService(
                distributedKeyRepository,
                bindingRepository,
                accountGroupBindingRepository,
                credentialRepository,
                accessGroupEntitlementService
        );
        return new Fixture(
                distributedKeyRepository,
                bindingRepository,
                accountGroupBindingRepository,
                credentialRepository,
                accessGroupEntitlementService,
                service
        );
    }

    private DistributedKeyEntity distributedKey(Long id) {
        DistributedKeyEntity entity = new DistributedKeyEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setKeyName("测试 Key");
        entity.setKeyPrefix("sk-gw-test");
        entity.setMaskedKey("sk-gw-test****");
        entity.setSecretHash("hash");
        entity.setActive(true);
        return entity;
    }

    private DistributedKeyAccountGroupBindingEntity groupBinding(
            Long id,
            DistributedKeyEntity key,
            Long groupId,
            ProviderType providerType,
            int priority) {
        UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(group, "id", groupId);

        DistributedKeyAccountGroupBindingEntity binding = new DistributedKeyAccountGroupBindingEntity();
        ReflectionTestUtils.setField(binding, "id", id);
        binding.setDistributedKey(key);
        binding.setGroup(group);
        binding.setProviderType(providerType);
        binding.setPriority(priority);
        binding.setActive(true);
        return binding;
    }

    private DistributedKeyBindingEntity directBinding(
            Long id,
            DistributedKeyEntity key,
            UpstreamCredentialEntity credential,
            int priority,
            int weight) {
        DistributedKeyBindingEntity binding = new DistributedKeyBindingEntity();
        ReflectionTestUtils.setField(binding, "id", id);
        binding.setDistributedKey(key);
        binding.setCredential(credential);
        binding.setPriority(priority);
        binding.setWeight(weight);
        binding.setActive(true);
        return binding;
    }

    private UpstreamCredentialEntity credential(Long id, Long groupId, ProviderType providerType, String name) {
        UpstreamCredentialEntity entity = new UpstreamCredentialEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setGroupId(groupId);
        entity.setProviderType(providerType);
        entity.setCredentialName(name);
        entity.setBaseUrl("https://example.com/" + providerType.name().toLowerCase());
        entity.setActive(true);
        entity.setDeleted(false);
        return entity;
    }

    private record Fixture(
            DistributedKeyRepository distributedKeyRepository,
            DistributedKeyBindingRepository bindingRepository,
            DistributedKeyAccountGroupBindingRepository accountGroupBindingRepository,
            UpstreamCredentialRepository credentialRepository,
            AccessGroupEntitlementService accessGroupEntitlementService,
            DistributedKeyQueryService service
    ) {
    }
}
