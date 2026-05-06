package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.RoutingPolicyRuntimePlanResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteGuardPolicyEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteGuardPolicyRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class RoutingPolicyRuntimeConfigService {

    private static final int MIN_ATTEMPTS = 1;
    private static final int MAX_ATTEMPTS = 10;

    private final RouteGuardPolicyRepository routeGuardPolicyRepository;
    private final ObjectMapper objectMapper;

    public RoutingPolicyRuntimeConfigService(
            RouteGuardPolicyRepository routeGuardPolicyRepository,
            ObjectMapper objectMapper) {
        this.routeGuardPolicyRepository = routeGuardPolicyRepository;
        this.objectMapper = objectMapper;
    }

    public RoutingPolicyRuntimePlanResponse runtimePlan(int defaultMaxAttempts) {
        List<RouteGuardPolicyEntity> policies = routeGuardPolicyRepository.findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc();
        List<Long> sourcePolicyIds = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        int maxAttempts = clampAttempts(defaultMaxAttempts);
        boolean retryConfigured = false;
        boolean fallbackEnabled = false;
        List<String> fallbackOrder = List.of();
        boolean circuitBreakerEnabled = false;
        Integer circuitFailureThreshold = null;
        boolean rateLimitEnabled = false;
        Integer requestsPerMinute = null;

        for (RouteGuardPolicyEntity policy : policies) {
            Long policyId = policy.getId();
            JsonNode retryNode = parsePolicy(policy.getRetryPolicy(), policyId, "retryPolicy", warnings);
            if (!retryConfigured && retryNode != null) {
                Integer configured = firstPositiveInt(retryNode, "maxAttempts", "attempts", "retries");
                if (configured != null) {
                    maxAttempts = clampAttempts(configured);
                    retryConfigured = true;
                    addSourcePolicyId(sourcePolicyIds, policyId);
                } else {
                    warnings.add("policy " + policyId + " retryPolicy 缺少正数 maxAttempts。");
                }
            }

            JsonNode fallbackNode = parsePolicy(policy.getFallbackPolicy(), policyId, "fallbackPolicy", warnings);
            if (!fallbackEnabled && fallbackNode != null) {
                fallbackEnabled = booleanValue(fallbackNode, true, "enabled");
                fallbackOrder = firstStringList(fallbackNode, "order", "fallbackOrder", "providers");
                addSourcePolicyId(sourcePolicyIds, policyId);
            }

            JsonNode circuitNode = parsePolicy(policy.getCircuitBreakerPolicy(), policyId, "circuitBreakerPolicy", warnings);
            if (!circuitBreakerEnabled && circuitNode != null) {
                circuitBreakerEnabled = booleanValue(circuitNode, true, "enabled");
                circuitFailureThreshold = firstPositiveInt(circuitNode, "failureThreshold", "threshold", "failures");
                addSourcePolicyId(sourcePolicyIds, policyId);
                if (circuitFailureThreshold == null) {
                    warnings.add("policy " + policyId + " circuitBreakerPolicy 缺少正数 failureThreshold。");
                }
            }

            JsonNode rateLimitNode = parsePolicy(policy.getRateLimitPolicy(), policyId, "rateLimitPolicy", warnings);
            if (!rateLimitEnabled && rateLimitNode != null) {
                rateLimitEnabled = booleanValue(rateLimitNode, true, "enabled");
                requestsPerMinute = firstPositiveInt(rateLimitNode, "requestsPerMinute", "rpm", "limit");
                addSourcePolicyId(sourcePolicyIds, policyId);
                if (requestsPerMinute == null) {
                    warnings.add("policy " + policyId + " rateLimitPolicy 缺少正数 requestsPerMinute/rpm。");
                }
            }
        }

        return new RoutingPolicyRuntimePlanResponse(
                maxAttempts,
                fallbackEnabled,
                fallbackOrder,
                circuitBreakerEnabled,
                circuitFailureThreshold,
                rateLimitEnabled,
                requestsPerMinute,
                List.copyOf(sourcePolicyIds),
                List.copyOf(warnings)
        );
    }

    public int maxAttempts(int defaultMaxAttempts, int candidateCount) {
        int requested = runtimePlan(defaultMaxAttempts).maxAttempts();
        return Math.min(Math.max(candidateCount, 0), requested);
    }

    private JsonNode parsePolicy(String raw, Long policyId, String fieldName, List<String> warnings) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node == null || node.isNull() || node.isMissingNode()) {
                return null;
            }
            if (!node.isObject()) {
                warnings.add("policy " + policyId + " " + fieldName + " 必须是 JSON object。");
                return null;
            }
            return node;
        } catch (JacksonException exception) {
            warnings.add("policy " + policyId + " " + fieldName + " 不是合法 JSON：" + exception.getMessage());
            return null;
        }
    }

    private Integer firstPositiveInt(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value == null || value.isMissingNode() || value.isNull()) {
                continue;
            }
            int parsed = value.asInt(0);
            if (parsed > 0) {
                return parsed;
            }
        }
        return null;
    }

    private boolean booleanValue(JsonNode node, boolean fallback, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        return value.asBoolean(fallback);
    }

    private List<String> firstStringList(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode array = node.path(fieldName);
            if (array == null || array.isMissingNode() || array.isNull() || !array.isArray()) {
                continue;
            }
            List<String> values = new ArrayList<>();
            for (JsonNode item : array) {
                String value = item.asText(null);
                if (value != null && !value.isBlank()) {
                    values.add(value.trim());
                }
            }
            if (!values.isEmpty()) {
                return List.copyOf(values);
            }
        }
        return List.of();
    }

    private int clampAttempts(int value) {
        return Math.min(Math.max(value, MIN_ATTEMPTS), MAX_ATTEMPTS);
    }

    private void addSourcePolicyId(List<Long> sourcePolicyIds, Long policyId) {
        if (policyId != null && !sourcePolicyIds.contains(policyId)) {
            sourcePolicyIds.add(policyId);
        }
    }
}
