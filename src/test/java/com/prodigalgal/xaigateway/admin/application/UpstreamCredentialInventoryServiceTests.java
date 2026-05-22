package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpstreamCredentialInventoryServiceTests {

    @Test
    void shouldMergeStaticCredentialsAndAuthJsonAccounts() {
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        SupportedModelCatalogService catalogService = Mockito.mock(SupportedModelCatalogService.class);
        UpstreamCredentialInventoryService service = new UpstreamCredentialInventoryService(
                credentialRepository,
                accountRepository,
                groupRepository,
                catalogService,
                new ObjectMapper()
        );
        UpstreamAccountGroupEntity codexGroup = group(2L, "Codex", UpstreamAccountProviderType.CODEX_OAUTH);
        UpstreamAccountGroupEntity geminiGroup = group(3L, "Gemini AI Studio", UpstreamAccountProviderType.GEMINI_OAUTH);
        UpstreamCredentialEntity credential = credential(5L, geminiGroup);
        UpstreamAccountEntity account = account(5L, codexGroup);

        Mockito.when(credentialRepository.findAllByDeletedFalseOrderByCreatedAtDesc()).thenReturn(List.of(credential));
        Mockito.when(accountRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(account));
        Mockito.when(groupRepository.findAllById(Mockito.anySet())).thenReturn(List.of(codexGroup, geminiGroup));
        Mockito.when(catalogService.normalize(Mockito.anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        var rows = service.list();

        assertEquals(2, rows.size());
        assertEquals("account:5", rows.get(0).rowKey());
        assertEquals("AUTH_JSON_ACCOUNT", rows.get(0).sourceType());
        assertEquals("Codex", rows.get(0).groupName());
        assertEquals("api-key:5", rows.get(1).rowKey());
        assertEquals("API_KEY", rows.get(1).sourceType());
        assertEquals("Gemini AI Studio", rows.get(1).groupName());
    }

    private UpstreamAccountGroupEntity group(Long id, String name, UpstreamAccountProviderType providerType) {
        UpstreamAccountGroupEntity entity = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setGroupName(name);
        entity.setProviderType(providerType);
        return entity;
    }

    private UpstreamCredentialEntity credential(Long id, UpstreamAccountGroupEntity group) {
        UpstreamCredentialEntity entity = new UpstreamCredentialEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-05-20T10:00:00Z"));
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-05-20T10:00:00Z"));
        entity.setCredentialName("Gemini AI Studio 01");
        entity.setProviderType(ProviderType.GEMINI_DIRECT);
        entity.setBaseUrl("https://generativelanguage.googleapis.com");
        entity.setAuthKind(CredentialAuthKind.API_KEY);
        entity.setApiKeyFingerprint("fp-gemini");
        entity.setCredentialMetadataJson("{\"source\":\"ai_studio\"}");
        entity.setSupportedModels(List.of("gemini-2.5-pro"));
        entity.setActive(true);
        entity.setGroupId(group.getId());
        return entity;
    }

    private UpstreamAccountEntity account(Long id, UpstreamAccountGroupEntity group) {
        UpstreamAccountEntity entity = new UpstreamAccountEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-05-21T10:00:00Z"));
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-05-21T10:00:00Z"));
        entity.setGroup(group);
        entity.setAccountName("Codex 账号 01");
        entity.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        entity.setExternalAccountId("codex:user");
        entity.setSupportedModels(List.of("gpt-5.4"));
        entity.setActive(true);
        entity.setFrozen(false);
        entity.setHealthy(true);
        entity.setRefreshStatus("READY");
        entity.setMetadataJson("{\"client_family\":\"CODEX\"}");
        return entity;
    }
}
