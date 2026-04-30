package com.prodigalgal.xaigateway.gateway.core.resource;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCacheReferenceEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCacheReferenceRepository;
import java.time.Instant;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
@Transactional
public class GatewayCacheResourceService {

    private static final int DEFAULT_LIMIT = 100;

    private final UpstreamCacheReferenceRepository upstreamCacheReferenceRepository;
    private final ObjectMapper objectMapper;

    public GatewayCacheResourceService(
            UpstreamCacheReferenceRepository upstreamCacheReferenceRepository,
            ObjectMapper objectMapper) {
        this.upstreamCacheReferenceRepository = upstreamCacheReferenceRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ObjectNode list(Long distributedKeyId, ProviderType providerType, String status) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "list");
        ArrayNode data = response.putArray("data");
        upstreamCacheReferenceRepository.search(
                        distributedKeyId,
                        providerType,
                        normalizeStatus(status),
                        PageRequest.of(0, DEFAULT_LIMIT))
                .forEach(entity -> data.add(toResponse(entity)));
        response.put("has_more", false);
        return response;
    }

    @Transactional(readOnly = true)
    public ObjectNode get(Long distributedKeyId, String cacheName) {
        return toResponse(resolve(distributedKeyId, cacheName));
    }

    public ObjectNode importCache(Long distributedKeyId, JsonNode requestBody) {
        if (requestBody == null || !requestBody.isObject()) {
            throw new IllegalArgumentException("cache import 请求体必须是 JSON object。");
        }
        ProviderType providerType = providerType(requiredText(requestBody, "providerType", "provider", "provider_type"));
        String modelGroup = firstText(requestBody, "modelGroup", "model");
        String prefixHash = firstText(requestBody, "prefixHash", "gatewayCacheId", "cacheKey");
        String externalCacheRef = firstText(requestBody, "externalCacheRef", "cachedContent", "cached_content", "name");
        Long credentialId = requiredLong(requestBody, "credentialId");

        if (modelGroup == null || modelGroup.isBlank()) {
            throw new IllegalArgumentException("modelGroup/model 不能为空。");
        }
        if (prefixHash == null || prefixHash.isBlank()) {
            throw new IllegalArgumentException("prefixHash/gatewayCacheId/cacheKey 不能为空。");
        }
        if (externalCacheRef == null || externalCacheRef.isBlank()) {
            throw new IllegalArgumentException("externalCacheRef/cachedContent/name 不能为空。");
        }

        UpstreamCacheReferenceEntity entity = upstreamCacheReferenceRepository
                .findByDistributedKeyIdAndProviderTypeAndModelGroupAndPrefixHash(
                        distributedKeyId,
                        providerType,
                        modelGroup.trim(),
                        prefixHash.trim())
                .orElseGet(UpstreamCacheReferenceEntity::new);
        entity.setDistributedKeyId(distributedKeyId);
        entity.setProviderType(providerType);
        entity.setCredentialId(credentialId);
        entity.setModelGroup(modelGroup.trim());
        entity.setPrefixHash(prefixHash.trim());
        entity.setExternalCacheRef(externalCacheRef.trim());
        entity.setStatus(defaultStatus(firstText(requestBody, "status", "state")));
        entity.setExpireAt(instantValue(requestBody, "expireAt", "expiresAt"));
        entity.setLastUsedAt(instantValue(requestBody, "lastUsedAt", "last_used_at"));
        return toResponse(upstreamCacheReferenceRepository.save(entity));
    }

    public ObjectNode delete(Long distributedKeyId, String cacheName) {
        ObjectNode response = invalidate(distributedKeyId, cacheName);
        response.put("object", "gateway.cache.deleted");
        response.put("deleted", true);
        return response;
    }

    public ObjectNode invalidate(Long distributedKeyId, String cacheName) {
        UpstreamCacheReferenceEntity entity = resolve(distributedKeyId, cacheName);
        entity.setStatus("INVALIDATED");
        entity.setLastUsedAt(Instant.now());
        UpstreamCacheReferenceEntity saved = upstreamCacheReferenceRepository.save(entity);
        ObjectNode response = toResponse(saved);
        response.put("invalidated", true);
        return response;
    }

    public ObjectNode touch(Long distributedKeyId, String cacheName) {
        UpstreamCacheReferenceEntity entity = resolve(distributedKeyId, cacheName);
        entity.setLastUsedAt(Instant.now());
        return toResponse(upstreamCacheReferenceRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public UpstreamCacheReferenceEntity resolve(Long distributedKeyId, String cacheName) {
        String normalized = normalizeName(cacheName);
        Long id = parseLong(normalized);
        if (id != null) {
            return upstreamCacheReferenceRepository.findByIdAndDistributedKeyId(id, distributedKeyId)
                    .orElseThrow(() -> new IllegalArgumentException("未找到指定的 Gateway Cache。"));
        }
        return upstreamCacheReferenceRepository
                .findFirstByDistributedKeyIdAndExternalCacheRefOrderByUpdatedAtDesc(distributedKeyId, normalized)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的 Gateway Cache。"));
    }

    public ObjectNode toResponse(UpstreamCacheReferenceEntity entity) {
        ObjectNode response = objectMapper.createObjectNode();
        String cacheId = "cache_" + entity.getId();
        boolean expired = isExpired(entity);
        boolean active = "ACTIVE".equalsIgnoreCase(entity.getStatus()) && !expired;
        response.put("id", cacheId);
        response.put("name", "caches/" + cacheId);
        response.put("object", "gateway.cache");
        response.put("distributed_key_id", entity.getDistributedKeyId());
        response.put("provider_type", entity.getProviderType().name());
        response.put("credential_id", entity.getCredentialId());
        response.put("model_group", entity.getModelGroup());
        response.put("prefix_hash", entity.getPrefixHash());
        response.put("external_cache_ref", entity.getExternalCacheRef());
        response.put("status", entity.getStatus());
        response.put("effective_status", expired ? "EXPIRED" : entity.getStatus());
        response.put("expired", expired);
        response.put("active", active);
        putInstant(response, "expire_at", entity.getExpireAt());
        putInstant(response, "last_used_at", entity.getLastUsedAt());
        putInstant(response, "created_at", entity.getCreatedAt());
        putInstant(response, "updated_at", entity.getUpdatedAt());
        ObjectNode lifecycle = response.putObject("lifecycle");
        lifecycle.put("status", entity.getStatus());
        lifecycle.put("effective_status", expired ? "EXPIRED" : entity.getStatus());
        lifecycle.put("expired", expired);
        lifecycle.put("active", active);
        putInstant(lifecycle, "expire_at", entity.getExpireAt());
        putInstant(lifecycle, "last_used_at", entity.getLastUsedAt());
        return response;
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
    }

    private String defaultStatus(String status) {
        String normalized = normalizeStatus(status);
        return normalized == null ? "ACTIVE" : normalized;
    }

    private ProviderType providerType(String value) {
        try {
            return ProviderType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("providerType 不支持：" + value);
        }
    }

    private boolean isExpired(UpstreamCacheReferenceEntity entity) {
        return entity.getExpireAt() != null && entity.getExpireAt().isBefore(Instant.now());
    }

    private String normalizeName(String cacheName) {
        if (cacheName == null || cacheName.isBlank()) {
            throw new IllegalArgumentException("cache name 不能为空。");
        }
        String value = cacheName.trim();
        return value.startsWith("cache_") ? value.substring("cache_".length()) : value;
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String requiredText(JsonNode node, String... fieldNames) {
        String value = firstText(node, fieldNames);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldNames[0] + " 不能为空。");
        }
        return value.trim();
    }

    private Long requiredLong(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            throw new IllegalArgumentException(fieldName + " 不能为空。");
        }
        return value.asLong();
    }

    private String firstText(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText(null);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private Instant instantValue(JsonNode node, String... fieldNames) {
        String text = firstText(node, fieldNames);
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (RuntimeException ignored) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(text));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(fieldNames[0] + " 必须是 ISO-8601 时间或 epoch seconds。");
            }
        }
    }

    private void putInstant(ObjectNode node, String fieldName, Instant value) {
        if (value == null) {
            node.putNull(fieldName);
        } else {
            node.put(fieldName, value.toString());
        }
    }
}
