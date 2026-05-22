package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class DefaultResourceBootstrapServiceTests {

    @Test
    void shouldEnsureDefaultGroupAtStartup() {
        AccountGroupAdminService accountGroupAdminService = Mockito.mock(AccountGroupAdminService.class);
        UpstreamAccountGroupEntity defaultGroup = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(defaultGroup, "id", 1L);
        defaultGroup.setGroupName(AccountGroupAdminService.DEFAULT_GROUP_NAME);
        defaultGroup.setProviderType(UpstreamAccountProviderType.OPENAI_OAUTH);
        Mockito.when(accountGroupAdminService.ensureDefaultGroup()).thenReturn(defaultGroup);
        NetworkGovernanceService networkGovernanceService = Mockito.mock(NetworkGovernanceService.class);

        new DefaultResourceBootstrapService(accountGroupAdminService, networkGovernanceService).bootstrapDefaults();

        Mockito.verify(accountGroupAdminService).ensureDefaultGroup();
        Mockito.verify(networkGovernanceService).ensureDefaultTlsProfiles();
    }
}
