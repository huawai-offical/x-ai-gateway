package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountPoolEntity;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class DefaultResourceBootstrapServiceTests {

    @Test
    void shouldEnsureDefaultPoolAtStartup() {
        AccountPoolAdminService accountPoolAdminService = Mockito.mock(AccountPoolAdminService.class);
        UpstreamAccountPoolEntity defaultPool = new UpstreamAccountPoolEntity();
        ReflectionTestUtils.setField(defaultPool, "id", 1L);
        defaultPool.setPoolName(AccountPoolAdminService.DEFAULT_POOL_NAME);
        defaultPool.setProviderType(UpstreamAccountProviderType.OPENAI_OAUTH);
        Mockito.when(accountPoolAdminService.ensureDefaultPool()).thenReturn(defaultPool);

        new DefaultResourceBootstrapService(accountPoolAdminService).bootstrapDefaults();

        Mockito.verify(accountPoolAdminService).ensureDefaultPool();
    }
}
