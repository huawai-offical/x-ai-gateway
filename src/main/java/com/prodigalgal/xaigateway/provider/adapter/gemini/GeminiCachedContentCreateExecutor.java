package com.prodigalgal.xaigateway.provider.adapter.gemini;

import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class GeminiCachedContentCreateExecutor {

    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final CredentialMaterialResolver credentialMaterialResolver;
    private final GeminiCachedContentReferenceService cachedContentReferenceService;
    private final GeminiCachedContentApiClient cachedContentApiClient;
    private final ObjectMapper objectMapper;

    public GeminiCachedContentCreateExecutor(
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialMaterialResolver credentialMaterialResolver,
            GeminiCachedContentReferenceService cachedContentReferenceService,
            GeminiCachedContentApiClient cachedContentApiClient,
            ObjectMapper objectMapper) {
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.credentialMaterialResolver = credentialMaterialResolver;
        this.cachedContentReferenceService = cachedContentReferenceService;
        this.cachedContentApiClient = cachedContentApiClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ObjectNode create(Long distributedKeyId, JsonNode requestBody) {
        if (requestBody == null || !requestBody.isObject()) {
            throw new IllegalArgumentException("Gemini cachedContents 创建请求体必须是 JSON object。");
        }
        Long credentialId = requiredLong(requestBody, "credentialId", "credential_id");
        String modelGroup = requiredText(requestBody, "modelGroup", "model", "model_group");
        String prefixHash = requiredText(requestBody, "prefixHash", "cacheKey", "gatewayCacheId", "prefix_hash");
        UpstreamCredentialEntity credential = upstreamCredentialRepository.findById(credentialId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("未找到可用的上游凭证。"));
        if (credential.getProviderType() != ProviderType.GEMINI_DIRECT) {
            throw new IllegalArgumentException("当前缓存创建 executor 仅支持 GEMINI_DIRECT。");
        }
        if (!credential.isActive()) {
            throw new IllegalArgumentException("上游凭证未启用，无法创建 Gemini cachedContents。");
        }

        ResolvedCredentialMaterial material = credentialMaterialResolver.resolveStored(credential);
        ObjectNode upstreamPayload = buildUpstreamPayload(modelGroup, requestBody);
        ObjectNode upstreamResponse = cachedContentApiClient.create(credential.getBaseUrl(), material.secret(), upstreamPayload);
        String cachedContentName = requiredText(upstreamResponse, "name", "cachedContent", "cached_content");
        cachedContentReferenceService.bind(
                distributedKeyId,
                modelGroup.trim(),
                prefixHash.trim(),
                credentialId,
                cachedContentName);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "gateway.cache.create_result");
        response.put("provider_type", ProviderType.GEMINI_DIRECT.name());
        response.put("credential_id", credentialId);
        response.put("model_group", modelGroup.trim());
        response.put("prefix_hash", prefixHash.trim());
        response.put("external_cache_ref", cachedContentName);
        response.put("created_at", Instant.now().toString());
        response.set("upstream_request", upstreamPayload);
        response.set("upstream_response", upstreamResponse);
        return response;
    }

    private ObjectNode buildUpstreamPayload(String modelGroup, JsonNode requestBody) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", normalizeGeminiModel(modelGroup));
        JsonNode contents = firstNode(requestBody, "contents");
        if (contents == null || contents.isMissingNode() || contents.isNull()) {
            contents = textContent(requestBody);
        }
        if (contents == null || contents.isMissingNode() || contents.isNull()) {
            throw new IllegalArgumentException("contents/content 不能为空。");
        }
        payload.set("contents", contents);
        copyIfPresent(requestBody, payload, "systemInstruction", "systemInstruction", "system_instruction");
        copyIfPresent(requestBody, payload, "ttl", "ttl");
        copyIfPresent(requestBody, payload, "expireTime", "expireTime", "expire_time");
        copyIfPresent(requestBody, payload, "displayName", "displayName", "display_name");
        return payload;
    }

    private JsonNode textContent(JsonNode requestBody) {
        String text = optionalText(requestBody, "content", "prompt");
        if (text == null || text.isBlank()) {
            return null;
        }
        ArrayNode contents = objectMapper.createArrayNode();
        ObjectNode content = contents.addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", text);
        return contents;
    }

    private String normalizeGeminiModel(String modelGroup) {
        String value = modelGroup.trim();
        return value.toLowerCase(Locale.ROOT).startsWith("models/") ? value : "models/" + value;
    }

    private void copyIfPresent(JsonNode source, ObjectNode target, String targetName, String... sourceNames) {
        JsonNode value = firstNode(source, sourceNames);
        if (value != null && !value.isMissingNode() && !value.isNull()) {
            target.set(targetName, value);
        }
    }

    private JsonNode firstNode(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private String requiredText(JsonNode node, String... fieldNames) {
        String text = optionalText(node, fieldNames);
        if (text != null && !text.isBlank()) {
            return text;
        }
        throw new IllegalArgumentException(fieldNames[0] + " 不能为空。");
    }

    private String optionalText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText(null);
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private Long requiredLong(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (!value.isMissingNode() && !value.isNull()) {
                return value.asLong();
            }
        }
        throw new IllegalArgumentException(fieldNames[0] + " 不能为空。");
    }
}
