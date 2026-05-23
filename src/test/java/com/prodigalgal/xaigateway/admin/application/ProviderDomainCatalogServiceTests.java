package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelAddressingStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccountGroupBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.ProviderProtocolEndpointEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountGroupBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.ProviderProtocolEndpointRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteModelCapabilityRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderDomainCatalogServiceTests {

    @Test
    void shouldBuildVendorEndpointGroupCredentialCatalog() {
        UpstreamSiteProfileRepository siteRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteModelCapabilityRepository modelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        ProviderProtocolEndpointRepository endpointRepository = Mockito.mock(ProviderProtocolEndpointRepository.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        DistributedKeyAccountGroupBindingRepository bindingRepository =
                Mockito.mock(DistributedKeyAccountGroupBindingRepository.class);
        ProviderDomainCatalogService service = new ProviderDomainCatalogService(
                siteRepository,
                snapshotRepository,
                modelCapabilityRepository,
                endpointRepository,
                groupRepository,
                credentialRepository,
                bindingRepository
        );

        UpstreamSiteProfileEntity mimo = site(9L, "preset:xiaomi_mimo", "Xiaomi MiMo");
        ProviderProtocolEndpointEntity openAiEndpoint = endpoint(
                91L,
                9L,
                "xiaomi_mimo:openai",
                "MiMo OpenAI-compatible",
                "xiaomi_mimo.openai_compatible",
                ProviderType.OPENAI_COMPATIBLE,
                UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC
        );
        ProviderProtocolEndpointEntity anthropicEndpoint = endpoint(
                92L,
                9L,
                "xiaomi_mimo:anthropic",
                "MiMo Anthropic-compatible",
                "xiaomi_mimo.anthropic_compatible",
                ProviderType.ANTHROPIC_DIRECT,
                UpstreamSiteKind.ANTHROPIC_DIRECT
        );
        UpstreamAccountGroupEntity productionGroup = group(41L, "MiMo 生产组", UpstreamAccountProviderType.OPENAI_OAUTH);
        UpstreamAccountGroupEntity unusedGroup = group(42L, "备用组", UpstreamAccountProviderType.OPENAI_OAUTH);
        UpstreamCredentialEntity key1 = credential(
                501L,
                "Xiaomi MiMo OpenAI Key 1",
                ProviderType.OPENAI_COMPATIBLE,
                9L,
                91L,
                41L,
                true,
                null
        );
        UpstreamCredentialEntity key2 = credential(
                502L,
                "Xiaomi MiMo Anthropic Key 1",
                ProviderType.ANTHROPIC_DIRECT,
                9L,
                92L,
                41L,
                false,
                Instant.parse("2999-05-23T10:00:00Z")
        );
        DistributedKeyEntity distributedKey = distributedKey(701L, "客户 A Key", "xagw_live");
        DistributedKeyAccountGroupBindingEntity binding = binding(801L, distributedKey, productionGroup, ProviderType.OPENAI_COMPATIBLE, 10);
        SiteCapabilitySnapshotEntity snapshot = new SiteCapabilitySnapshotEntity();
        snapshot.setSiteProfile(mimo);
        snapshot.setHealthState("READY");

        Mockito.when(siteRepository.findAll()).thenReturn(List.of(mimo));
        Mockito.when(endpointRepository.findAll()).thenReturn(List.of(openAiEndpoint, anthropicEndpoint));
        Mockito.when(groupRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(productionGroup, unusedGroup));
        Mockito.when(credentialRepository.findAllByDeletedFalseOrderByCreatedAtDesc()).thenReturn(List.of(key2, key1));
        Mockito.when(bindingRepository.findAll()).thenReturn(List.of(binding));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(9L)).thenReturn(Optional.of(snapshot));
        Mockito.when(modelCapabilityRepository.findAllBySiteProfile_IdOrderByModelKeyAsc(9L)).thenReturn(List.of());

        var response = service.catalog();

        assertEquals(1, response.summary().vendorCount());
        assertEquals(2, response.summary().protocolEndpointCount());
        assertEquals(2, response.summary().accountGroupCount());
        assertEquals(2, response.summary().credentialCount());
        assertEquals(1, response.summary().distributedKeyBindingCount());
        assertEquals(1, response.vendors().size());
        var vendor = response.vendors().getFirst();
        assertEquals("Xiaomi MiMo", vendor.displayName());
        assertEquals("READY", vendor.healthState());
        assertEquals(2, vendor.linkedCredentialCount());
        assertEquals(2, vendor.protocolEndpoints().size());
        assertEquals(List.of(41L), vendor.protocolEndpoints().getFirst().accountGroupIds());
        assertEquals(1, vendor.accountGroups().size());

        var group = vendor.accountGroups().getFirst();
        assertEquals("MiMo 生产组", group.groupName());
        assertEquals("ENVIRONMENT", group.groupKind());
        assertEquals(2, group.apiCredentialCount());
        assertEquals(2, group.endpointCoverage().size());
        assertEquals("credential_protocol_endpoint_id", group.endpointCoverage().getFirst().source());
        assertEquals(2, group.credentials().size());
        assertFalse(group.credentials().getFirst().active());
        assertTrue(group.credentials().getFirst().cooldown());
        assertEquals("INACTIVE", group.credentials().getFirst().status());
        assertEquals(1, group.distributedKeyBindings().size());
        assertEquals("客户 A Key", group.distributedKeyBindings().getFirst().keyName());

        assertEquals(1, response.unassignedAccountGroups().size());
        assertEquals("备用组", response.unassignedAccountGroups().getFirst().groupName());
        assertEquals("HEALTH_STANDBY", response.unassignedAccountGroups().getFirst().groupKind());
    }

    private UpstreamSiteProfileEntity site(Long id, String profileCode, String displayName) {
        UpstreamSiteProfileEntity entity = new UpstreamSiteProfileEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setProfileCode(profileCode);
        entity.setDisplayName(displayName);
        entity.setVendorCode("xiaomi_mimo");
        entity.setVendorName("小米 MiMo");
        entity.setProviderFamily(ProviderFamily.OPENAI);
        entity.setSiteKind(UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC);
        entity.setAuthStrategy(AuthStrategy.BEARER);
        entity.setPathStrategy(PathStrategy.OPENAI_V1);
        entity.setModelAddressingStrategy(ModelAddressingStrategy.MODEL_NAME);
        entity.setErrorSchemaStrategy(ErrorSchemaStrategy.OPENAI_ERROR);
        entity.setActive(true);
        return entity;
    }

    private ProviderProtocolEndpointEntity endpoint(
            Long id,
            Long siteProfileId,
            String endpointCode,
            String displayName,
            String protocolSuite,
            ProviderType providerType,
            UpstreamSiteKind siteKind) {
        ProviderProtocolEndpointEntity entity = new ProviderProtocolEndpointEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setSiteProfileId(siteProfileId);
        entity.setEndpointCode(endpointCode);
        entity.setDisplayName(displayName);
        entity.setProtocolSuite(protocolSuite);
        entity.setProviderType(providerType);
        entity.setSiteKind(siteKind);
        entity.setBaseUrl("https://token-plan-sgp.xiaomimimo.com/v1");
        entity.setAuthStrategy(AuthStrategy.BEARER);
        entity.setPathStrategy(PathStrategy.OPENAI_V1);
        entity.setModelAddressingStrategy(ModelAddressingStrategy.MODEL_NAME);
        entity.setErrorSchemaStrategy(ErrorSchemaStrategy.OPENAI_ERROR);
        entity.setActive(true);
        return entity;
    }

    private UpstreamAccountGroupEntity group(Long id, String groupName, UpstreamAccountProviderType providerType) {
        UpstreamAccountGroupEntity entity = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setGroupName(groupName);
        entity.setProviderType(providerType);
        entity.setSupportedModels(List.of("mimo-vl"));
        entity.setSupportedProtocols(List.of("openai", "anthropic"));
        entity.setAllowedClientFamilies(List.of("GENERIC_OPENAI"));
        entity.setDescription(groupName);
        entity.setActive(true);
        return entity;
    }

    private UpstreamCredentialEntity credential(
            Long id,
            String credentialName,
            ProviderType providerType,
            Long siteProfileId,
            Long protocolEndpointId,
            Long groupId,
            boolean active,
            Instant cooldownUntil) {
        UpstreamCredentialEntity entity = new UpstreamCredentialEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setCredentialName(credentialName);
        entity.setProviderType(providerType);
        entity.setBaseUrl("https://token-plan-sgp.xiaomimimo.com/v1");
        entity.setApiKeyFingerprint("fp-" + id);
        entity.setSiteProfileId(siteProfileId);
        entity.setProtocolEndpointId(protocolEndpointId);
        entity.setGroupId(groupId);
        entity.setActive(active);
        entity.setCooldownUntil(cooldownUntil);
        entity.setSupportedModels(List.of("mimo-vl"));
        return entity;
    }

    private DistributedKeyEntity distributedKey(Long id, String keyName, String keyPrefix) {
        DistributedKeyEntity entity = new DistributedKeyEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setKeyName(keyName);
        entity.setKeyPrefix(keyPrefix);
        entity.setMaskedKey(keyPrefix + "_***");
        entity.setSecretHash("hash");
        entity.setActive(true);
        return entity;
    }

    private DistributedKeyAccountGroupBindingEntity binding(
            Long id,
            DistributedKeyEntity distributedKey,
            UpstreamAccountGroupEntity group,
            ProviderType providerType,
            int priority) {
        DistributedKeyAccountGroupBindingEntity entity = new DistributedKeyAccountGroupBindingEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setDistributedKey(distributedKey);
        entity.setGroup(group);
        entity.setProviderType(providerType);
        entity.setPriority(priority);
        entity.setActive(true);
        return entity;
    }
}
