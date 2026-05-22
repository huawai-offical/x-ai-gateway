package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.infra.persistence.entity.TlsFingerprintProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.NetworkProxyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.TlsFingerprintProfileRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkGovernanceServiceTests {

    @Test
    void shouldCreateDefaultTlsProfilesIdempotently() {
        TlsFingerprintProfileRepository tlsRepository = Mockito.mock(TlsFingerprintProfileRepository.class);
        NetworkGovernanceService service = new NetworkGovernanceService(
                Mockito.mock(NetworkProxyRepository.class),
                tlsRepository,
                Mockito.mock(UpstreamAccountRepository.class)
        );
        Mockito.when(tlsRepository.findFirstByProfileCode("codex-cli")).thenReturn(Optional.empty());
        Mockito.when(tlsRepository.findFirstByProfileCode("claude-code")).thenReturn(Optional.empty());
        TlsFingerprintProfileEntity existing = new TlsFingerprintProfileEntity();
        existing.setProfileCode("web-browser-chrome");
        Mockito.when(tlsRepository.findFirstByProfileCode("web-browser-chrome")).thenReturn(Optional.of(existing));
        Mockito.when(tlsRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.ensureDefaultTlsProfiles();

        ArgumentCaptor<TlsFingerprintProfileEntity> captor = ArgumentCaptor.forClass(TlsFingerprintProfileEntity.class);
        Mockito.verify(tlsRepository, Mockito.times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(item -> "codex-cli".equals(item.getProfileCode())
                && item.getSettingsJson().contains("x-client-family")));
        assertTrue(captor.getAllValues().stream().anyMatch(item -> "claude-code".equals(item.getProfileCode())
                && item.getSettingsJson().contains("anthropic-version")));
    }
}
