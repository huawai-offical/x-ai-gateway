package com.prodigalgal.xaigateway.gateway.core.auth;

import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class DistributedKeyAuthenticationServiceTests {

    @Test
    void shouldUseCachedSnapshotBeforeRepositoryLookup() {
        DistributedKeyRepository repository = Mockito.mock(DistributedKeyRepository.class);
        DistributedKeySecretService secretService = Mockito.mock(DistributedKeySecretService.class);
        AuthCacheStore authCacheStore = Mockito.mock(AuthCacheStore.class);
        GatewayProperties gatewayProperties = new GatewayProperties();
        DistributedKeyAuthenticationService service = new DistributedKeyAuthenticationService(
                repository,
                secretService,
                authCacheStore,
                gatewayProperties
        );

        Mockito.when(secretService.hashSecret("secret")).thenReturn("hash");
        Mockito.when(authCacheStore.get("sk-gw-test")).thenReturn(Optional.of(
                new DistributedKeyAuthSnapshot(1L, "sk-gw-test", "test", "masked", "hash", List.of("CODEX"))
        ));

        AuthenticatedDistributedKey result = service.authenticateRawToken("sk-gw-test.secret");

        assertEquals("sk-gw-test", result.keyPrefix());
        Mockito.verify(repository, Mockito.never()).findByKeyPrefixAndActiveTrue(any());
    }

    @Test
    void shouldLoadAndCacheSnapshotOnCacheMiss() {
        DistributedKeyRepository repository = Mockito.mock(DistributedKeyRepository.class);
        DistributedKeySecretService secretService = Mockito.mock(DistributedKeySecretService.class);
        AuthCacheStore authCacheStore = Mockito.mock(AuthCacheStore.class);
        GatewayProperties gatewayProperties = new GatewayProperties();
        DistributedKeyAuthenticationService service = new DistributedKeyAuthenticationService(
                repository,
                secretService,
                authCacheStore,
                gatewayProperties
        );
        DistributedKeyEntity entity = new DistributedKeyEntity();
        entity.setKeyName("test");
        entity.setKeyPrefix("sk-gw-test");
        entity.setMaskedKey("masked");
        entity.setSecretHash("hash");
        entity.setAllowedClientFamilies(List.of("CODEX"));
        org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", 1L);

        Mockito.when(secretService.hashSecret("secret")).thenReturn("hash");
        Mockito.when(authCacheStore.get("sk-gw-test")).thenReturn(Optional.empty());
        Mockito.when(repository.findByKeyPrefixAndActiveTrue("sk-gw-test")).thenReturn(Optional.of(entity));

        service.authenticateRawToken("sk-gw-test.secret");

        Mockito.verify(authCacheStore).put(any(DistributedKeyAuthSnapshot.class), eq(gatewayProperties.getCache().getAuthTtl()));
    }
}
