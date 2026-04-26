package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.DistributedKeyClientConfigResponse;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeySecretService;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountPoolBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccessGroupGrantRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributedKeyAdminServiceTests {

    @Test
    void shouldExportClientConfigWithMaskedKeyOnly() {
        DistributedKeyRepository keyRepository = Mockito.mock(DistributedKeyRepository.class);
        DistributedKeyAdminService service = new DistributedKeyAdminService(
                keyRepository,
                Mockito.mock(DistributedKeySecretService.class),
                Mockito.mock(DistributedKeyBindingRepository.class),
                Mockito.mock(DistributedKeyAccountPoolBindingRepository.class),
                Mockito.mock(DistributedKeyAccessGroupGrantRepository.class),
                Mockito.mock(GatewayUserRepository.class),
                Optional.empty()
        );
        DistributedKeyEntity entity = new DistributedKeyEntity();
        entity.setKeyName("codex-key");
        entity.setKeyPrefix("sk-gw-test");
        entity.setMaskedKey("sk-gw-test...abcd");
        ReflectionTestUtils.setField(entity, "id", 3L);
        Mockito.when(keyRepository.findById(3L)).thenReturn(Optional.of(entity));

        DistributedKeyClientConfigResponse response = service.exportClientConfig(
                3L,
                "auth-json",
                "CODEX",
                "https://gateway.example.com/v1/"
        );

        assertEquals("auth_json", response.format());
        assertEquals("CODEX", response.clientFamily());
        assertTrue(response.config().contains("https://gateway.example.com/v1"));
        assertTrue(response.config().contains("sk-gw-test...abcd"));
        assertFalse(response.config().contains("full-secret"));
        assertTrue(response.warning().contains("不会返回完整 secret"));
    }
}
