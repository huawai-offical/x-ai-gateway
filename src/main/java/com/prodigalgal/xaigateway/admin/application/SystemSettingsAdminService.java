package com.prodigalgal.xaigateway.admin.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.api.SystemSettingsRequest;
import com.prodigalgal.xaigateway.admin.api.SystemSettingsResponse;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.SystemSettingEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.SystemSettingRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SystemSettingsAdminService {

    private static final String UPSTREAM_CACHE_KEY = "gateway.upstream-cache";
    private static final String UPSTREAM_RUNTIME_KEY = "gateway.upstream-runtime";

    private final SystemSettingRepository systemSettingRepository;
    private final GatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper;

    public SystemSettingsAdminService(
            SystemSettingRepository systemSettingRepository,
            GatewayProperties gatewayProperties,
            ObjectMapper objectMapper) {
        this.systemSettingRepository = systemSettingRepository;
        this.gatewayProperties = gatewayProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SystemSettingsResponse get() {
        GatewayProperties.Cache cacheDefaults = gatewayProperties.getCache();

        SystemSettingsResponse.UpstreamCacheSettingsResponse upstreamCache =
                read(UPSTREAM_CACHE_KEY, SystemSettingsResponse.UpstreamCacheSettingsResponse.class)
                        .orElseGet(() -> new SystemSettingsResponse.UpstreamCacheSettingsResponse(
                                cacheDefaults.isEnabled(),
                                cacheDefaults.isStickyByDistributedKey(),
                                cacheDefaults.isPrefixAffinityEnabled(),
                                cacheDefaults.isFingerprintAffinityEnabled(),
                                cacheDefaults.getAffinityTtl().toString(),
                                cacheDefaults.getFingerprintMaxPrefixTokens(),
                                cacheDefaults.getKeyPrefix()
                        ));

        SystemSettingsResponse.UpstreamRuntimeSettingsResponse upstream =
                read(UPSTREAM_RUNTIME_KEY, SystemSettingsResponse.UpstreamRuntimeSettingsResponse.class)
                        .orElseGet(() -> new SystemSettingsResponse.UpstreamRuntimeSettingsResponse(
                                180_000,
                                600_000,
                                180_000,
                                600_000
                        ));

        Instant updatedAt = latestUpdatedAt();
        return new SystemSettingsResponse(upstreamCache, upstream, updatedAt);
    }

    public SystemSettingsResponse save(SystemSettingsRequest request) {
        SystemSettingsResponse current = get();

        SystemSettingsResponse.UpstreamCacheSettingsResponse upstreamCache = request.upstreamCache() == null
                ? current.upstreamCache()
                : new SystemSettingsResponse.UpstreamCacheSettingsResponse(
                        boolOrDefault(request.upstreamCache().enabled(), current.upstreamCache().enabled()),
                        boolOrDefault(request.upstreamCache().stickyByDistributedKey(), current.upstreamCache().stickyByDistributedKey()),
                        boolOrDefault(request.upstreamCache().prefixAffinityEnabled(), current.upstreamCache().prefixAffinityEnabled()),
                        boolOrDefault(request.upstreamCache().fingerprintAffinityEnabled(), current.upstreamCache().fingerprintAffinityEnabled()),
                        request.upstreamCache().affinityTtl() == null ? current.upstreamCache().affinityTtl() : normalizeDuration(request.upstreamCache().affinityTtl()),
                        request.upstreamCache().fingerprintMaxPrefixTokens() == null ? current.upstreamCache().fingerprintMaxPrefixTokens() : request.upstreamCache().fingerprintMaxPrefixTokens(),
                        blankOrDefault(request.upstreamCache().keyPrefix(), current.upstreamCache().keyPrefix())
                );

        SystemSettingsResponse.UpstreamRuntimeSettingsResponse upstream = request.upstream() == null
                ? current.upstream()
                : new SystemSettingsResponse.UpstreamRuntimeSettingsResponse(
                        intOrDefault(request.upstream().sdkTimeoutMs(), current.upstream().sdkTimeoutMs()),
                        intOrDefault(request.upstream().sdkStreamTimeoutMs(), current.upstream().sdkStreamTimeoutMs()),
                        intOrDefault(request.upstream().httpTimeoutMs(), current.upstream().httpTimeoutMs()),
                        intOrDefault(request.upstream().httpStreamTimeoutMs(), current.upstream().httpStreamTimeoutMs())
                );

        write(UPSTREAM_CACHE_KEY, upstreamCache, "json", "上游缓存运行时设置。");
        write(UPSTREAM_RUNTIME_KEY, upstream, "json", "上游超时运行时设置。");

        return new SystemSettingsResponse(upstreamCache, upstream, latestUpdatedAt());
    }

    public SystemSettingsResponse reset() {
        systemSettingRepository.findBySettingKey(UPSTREAM_CACHE_KEY).ifPresent(systemSettingRepository::delete);
        systemSettingRepository.findBySettingKey(UPSTREAM_RUNTIME_KEY).ifPresent(systemSettingRepository::delete);
        return get();
    }

    private Instant latestUpdatedAt() {
        return systemSettingRepository.findAll().stream()
                .map(SystemSettingEntity::getUpdatedAt)
                .max(Instant::compareTo)
                .orElse(null);
    }

    private boolean boolOrDefault(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private int intOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String blankOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String normalizeDuration(String value) {
        return Duration.parse(value).toString();
    }

    private <T> Optional<T> read(String key, Class<T> type) {
        return systemSettingRepository.findBySettingKey(key)
                .map(SystemSettingEntity::getSettingValue)
                .map(value -> {
                    try {
                        return objectMapper.readValue(value, type);
                    } catch (JsonProcessingException exception) {
                        throw new IllegalStateException("无法读取系统配置：" + key, exception);
                    }
                });
    }

    private void write(String key, Object value, String valueType, String description) {
        SystemSettingEntity entity = systemSettingRepository.findBySettingKey(key).orElseGet(SystemSettingEntity::new);
        entity.setSettingKey(key);
        entity.setValueType(valueType);
        entity.setDescription(description);
        try {
            entity.setSettingValue(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存系统配置：" + key, exception);
        }
        systemSettingRepository.save(entity);
    }
}
