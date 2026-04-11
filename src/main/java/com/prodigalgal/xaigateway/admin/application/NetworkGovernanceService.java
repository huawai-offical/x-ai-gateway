package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ProxyProbeResultResponse;
import com.prodigalgal.xaigateway.admin.api.ProxyRequest;
import com.prodigalgal.xaigateway.admin.api.ProxyResponse;
import com.prodigalgal.xaigateway.admin.api.TlsFingerprintProfileRequest;
import com.prodigalgal.xaigateway.admin.api.TlsFingerprintProfileResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.NetworkProxyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.NetworkProxyProbeResultEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.TlsFingerprintProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.NetworkProxyProbeResultRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.NetworkProxyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.TlsFingerprintProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.Socket;
import java.net.URI;
import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class NetworkGovernanceService {

    private final NetworkProxyRepository networkProxyRepository;
    private final NetworkProxyProbeResultRepository networkProxyProbeResultRepository;
    private final TlsFingerprintProfileRepository tlsFingerprintProfileRepository;

    public NetworkGovernanceService(
            NetworkProxyRepository networkProxyRepository,
            NetworkProxyProbeResultRepository networkProxyProbeResultRepository,
            TlsFingerprintProfileRepository tlsFingerprintProfileRepository) {
        this.networkProxyRepository = networkProxyRepository;
        this.networkProxyProbeResultRepository = networkProxyProbeResultRepository;
        this.tlsFingerprintProfileRepository = tlsFingerprintProfileRepository;
    }

    @Transactional(readOnly = true)
    public List<ProxyResponse> listProxies() {
        return networkProxyRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    public ProxyResponse saveProxy(Long id, ProxyRequest request) {
        NetworkProxyEntity entity = id == null
                ? new NetworkProxyEntity()
                : networkProxyRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到代理。"));
        entity.setProxyName(request.proxyName().trim());
        entity.setProxyUrl(request.proxyUrl().trim());
        entity.setDescription(request.description());
        entity.setActive(request.active() == null || request.active());
        return toResponse(networkProxyRepository.save(entity));
    }

    public ProxyProbeResultResponse probe(Long proxyId) {
        NetworkProxyEntity proxy = networkProxyRepository.findById(proxyId)
                .orElseThrow(() -> new IllegalArgumentException("未找到代理。"));
        long started = System.currentTimeMillis();
        NetworkProxyProbeResultEntity result = new NetworkProxyProbeResultEntity();
        result.setProxyId(proxyId);
        result.setTargetHost(proxy.getProxyUrl());
        try {
            URI uri = URI.create(proxy.getProxyUrl());
            int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
            try (Socket socket = new Socket(uri.getHost(), port)) {
                long latency = System.currentTimeMillis() - started;
                result.setLatencyMs(latency);
                result.setStatus("SUCCESS");
                proxy.setLastStatus("SUCCESS");
                proxy.setLastLatencyMs(latency);
                proxy.setLastErrorMessage(null);
                proxy.setLastProbedAt(Instant.now());
            }
        } catch (Exception exception) {
            result.setStatus("FAILED");
            result.setErrorMessage(exception.getMessage());
            proxy.setLastStatus("FAILED");
            proxy.setLastErrorMessage(exception.getMessage());
            proxy.setLastProbedAt(Instant.now());
        }
        networkProxyRepository.save(proxy);
        return toProbeResponse(networkProxyProbeResultRepository.save(result));
    }

    @Transactional(readOnly = true)
    public List<ProxyProbeResultResponse> listProbeResults(Long proxyId) {
        return networkProxyProbeResultRepository.findTop50ByProxyIdOrderByCreatedAtDesc(proxyId).stream().map(this::toProbeResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TlsFingerprintProfileResponse> listTlsProfiles() {
        return tlsFingerprintProfileRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toTlsResponse).toList();
    }

    public TlsFingerprintProfileResponse saveTlsProfile(Long id, TlsFingerprintProfileRequest request) {
        TlsFingerprintProfileEntity entity = id == null
                ? new TlsFingerprintProfileEntity()
                : tlsFingerprintProfileRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到 TLS 指纹画像。"));
        entity.setProfileName(request.profileName().trim());
        entity.setProfileCode(request.profileCode().trim());
        entity.setSettingsJson(request.settingsJson());
        entity.setDescription(request.description());
        entity.setActive(request.active() == null || request.active());
        return toTlsResponse(tlsFingerprintProfileRepository.save(entity));
    }

    private ProxyResponse toResponse(NetworkProxyEntity entity) {
        return new ProxyResponse(entity.getId(), entity.getProxyName(), entity.getProxyUrl(), entity.isActive(),
                entity.getLastStatus(), entity.getLastLatencyMs(), entity.getLastErrorMessage(), entity.getLastProbedAt(),
                entity.getDescription(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private ProxyProbeResultResponse toProbeResponse(NetworkProxyProbeResultEntity entity) {
        return new ProxyProbeResultResponse(entity.getId(), entity.getProxyId(), entity.getStatus(), entity.getLatencyMs(), entity.getTargetHost(), entity.getErrorMessage(), entity.getCreatedAt());
    }

    private TlsFingerprintProfileResponse toTlsResponse(TlsFingerprintProfileEntity entity) {
        return new TlsFingerprintProfileResponse(entity.getId(), entity.getProfileName(), entity.getProfileCode(), entity.getSettingsJson(), entity.getDescription(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
