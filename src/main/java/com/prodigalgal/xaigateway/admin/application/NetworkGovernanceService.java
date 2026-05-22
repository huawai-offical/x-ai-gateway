package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ProxyRequest;
import com.prodigalgal.xaigateway.admin.api.ProxyResponse;
import com.prodigalgal.xaigateway.admin.api.TlsFingerprintProfileRequest;
import com.prodigalgal.xaigateway.admin.api.TlsFingerprintProfileResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.NetworkProxyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.TlsFingerprintProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.NetworkProxyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.TlsFingerprintProfileRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class NetworkGovernanceService {
    private static final Set<String> SUPPORTED_PROXY_SCHEMES = Set.of("http", "https", "socks", "socks4", "socks5");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final NetworkProxyRepository networkProxyRepository;
    private final TlsFingerprintProfileRepository tlsFingerprintProfileRepository;
    private final UpstreamAccountRepository upstreamAccountRepository;

    public NetworkGovernanceService(
            NetworkProxyRepository networkProxyRepository,
            TlsFingerprintProfileRepository tlsFingerprintProfileRepository,
            UpstreamAccountRepository upstreamAccountRepository) {
        this.networkProxyRepository = networkProxyRepository;
        this.tlsFingerprintProfileRepository = tlsFingerprintProfileRepository;
        this.upstreamAccountRepository = upstreamAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<ProxyResponse> listProxies() {
        return networkProxyRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    public ProxyResponse saveProxy(Long id, ProxyRequest request) {
        NetworkProxyEntity entity = id == null
                ? new NetworkProxyEntity()
                : networkProxyRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到代理。"));
        URI proxyUri = parseProxyUri(request.proxyUrl());
        entity.setProxyName(request.proxyName().trim());
        entity.setProxyUrl(proxyUri.toString());
        entity.setDescription(request.description());
        entity.setActive(request.active() == null || request.active());
        return toResponse(networkProxyRepository.save(entity));
    }

    public void deleteProxy(Long id) {
        NetworkProxyEntity entity = networkProxyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到代理。"));
        long bindingCount = upstreamAccountRepository.countByProxyId(id);
        if (bindingCount > 0) {
            throw new IllegalArgumentException("该代理仍被上游账号引用，请先解除账号绑定。");
        }
        networkProxyRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public List<TlsFingerprintProfileResponse> listTlsProfiles() {
        return tlsFingerprintProfileRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toTlsResponse).toList();
    }

    public void ensureDefaultTlsProfiles() {
        defaultTlsProfiles().forEach(profile -> tlsFingerprintProfileRepository.findFirstByProfileCode(profile.profileCode())
                .orElseGet(() -> tlsFingerprintProfileRepository.save(profile.toEntity())));
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

    public void deleteTlsProfile(Long id) {
        TlsFingerprintProfileEntity entity = tlsFingerprintProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到 TLS 指纹画像。"));
        long bindingCount = upstreamAccountRepository.countByTlsFingerprintProfileId(id);
        if (bindingCount > 0) {
            throw new IllegalArgumentException("该 TLS 指纹画像仍被上游账号引用，请先解除账号绑定。");
        }
        tlsFingerprintProfileRepository.delete(entity);
    }

    private ProxyResponse toResponse(NetworkProxyEntity entity) {
        return new ProxyResponse(entity.getId(), entity.getProxyName(), entity.getProxyUrl(), entity.isActive(),
                entity.getDescription(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private URI parseProxyUri(String proxyUrl) {
        if (proxyUrl == null || proxyUrl.isBlank()) {
            throw new IllegalArgumentException("代理地址不能为空。");
        }
        try {
            URI uri = URI.create(proxyUrl.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!SUPPORTED_PROXY_SCHEMES.contains(scheme)) {
                throw new IllegalArgumentException("代理地址仅支持 http、https、socks、socks4、socks5 协议。");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("代理地址必须包含 host。");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("代理地址")) {
                throw exception;
            }
            throw new IllegalArgumentException("代理地址格式不正确。");
        }
    }

    private TlsFingerprintProfileResponse toTlsResponse(TlsFingerprintProfileEntity entity) {
        return new TlsFingerprintProfileResponse(entity.getId(), entity.getProfileName(), entity.getProfileCode(), entity.getSettingsJson(), entity.getDescription(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private List<DefaultTlsProfile> defaultTlsProfiles() {
        return List.of(
                new DefaultTlsProfile(
                        "Codex CLI",
                        "codex-cli",
                        "模拟 Codex CLI / App API 常见请求头。",
                        Map.of(
                                "client", "codex-cli",
                                "headers", Map.of(
                                        "accept", "application/json",
                                        "content-type", "application/json",
                                        "user-agent", "codex_cli_rs/x-ai-gateway",
                                        "x-client-family", "CODEX",
                                        "openai-beta", "responses=v1"
                                )
                        )
                ),
                new DefaultTlsProfile(
                        "Claude Code",
                        "claude-code",
                        "模拟 Claude Code / Anthropic Messages 常见请求头。",
                        Map.of(
                                "client", "claude-code",
                                "headers", Map.of(
                                        "accept", "application/json",
                                        "content-type", "application/json",
                                        "user-agent", "claude-code/x-ai-gateway",
                                        "anthropic-version", "2023-06-01"
                                )
                        )
                ),
                new DefaultTlsProfile(
                        "Web Browser Chrome",
                        "web-browser-chrome",
                        "模拟桌面 Chrome 浏览器常见 header 画像。",
                        Map.of(
                                "client", "web-browser",
                                "headers", Map.of(
                                        "accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                                        "accept-language", "zh-CN,zh;q=0.9,en;q=0.8",
                                        "user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36",
                                        "sec-ch-ua", "\"Chromium\";v=\"125\", \"Google Chrome\";v=\"125\"",
                                        "sec-ch-ua-mobile", "?0",
                                        "sec-ch-ua-platform", "\"Windows\""
                                )
                        )
                )
        );
    }

    private record DefaultTlsProfile(String profileName, String profileCode, String description, Map<String, Object> settings) {
        private TlsFingerprintProfileEntity toEntity() {
            TlsFingerprintProfileEntity entity = new TlsFingerprintProfileEntity();
            entity.setProfileName(profileName);
            entity.setProfileCode(profileCode);
            entity.setDescription(description);
            entity.setSettingsJson(writeSettings(settings));
            entity.setActive(true);
            return entity;
        }
    }

    private static String writeSettings(Map<String, Object> settings) {
        try {
            return OBJECT_MAPPER.writeValueAsString(settings);
        } catch (Exception exception) {
            throw new IllegalStateException("默认 TLS 指纹画像序列化失败。", exception);
        }
    }
}
