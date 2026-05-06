package com.prodigalgal.xaigateway.gateway.core.routing;

import com.prodigalgal.xaigateway.admin.api.RoutingPolicyRuntimeStateResponse;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceTargetType;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteGuardPolicyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteGuardPolicyRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class RoutingPolicyRuntimeEnforcementService {

    private static final int DEFAULT_FAILURE_THRESHOLD = 3;
    private static final int DEFAULT_OPEN_SECONDS = 60;

    private final RouteGuardPolicyRepository routeGuardPolicyRepository;
    private final ObjectMapper objectMapper;
    private final RoutingPolicyRuntimeStore runtimeStore;

    @Autowired
    public RoutingPolicyRuntimeEnforcementService(
            RouteGuardPolicyRepository routeGuardPolicyRepository,
            ObjectMapper objectMapper,
            RoutingPolicyRuntimeStore runtimeStore) {
        this.routeGuardPolicyRepository = routeGuardPolicyRepository;
        this.objectMapper = objectMapper;
        this.runtimeStore = runtimeStore;
    }

    public RoutingPolicyRuntimeEnforcementService(
            RouteGuardPolicyRepository routeGuardPolicyRepository,
            ObjectMapper objectMapper) {
        this(routeGuardPolicyRepository, objectMapper, new InMemoryRoutingPolicyRuntimeStore());
    }

    public RoutePolicyRuntimeDecision evaluateCandidate(
            RouteCandidateView candidate,
            UpstreamCredentialEntity credential) {
        Instant now = Instant.now();
        List<Long> matchedPolicyIds = new ArrayList<>();
        for (MatchedPolicy policy : matchedPolicies(candidate, credential)) {
            String key = runtimeKey(policy.policy(), policy.targetRef());
            CircuitPolicy circuitPolicy = circuitPolicy(policy.policy());
            if (circuitPolicy.enabled()) {
                RoutingPolicyCircuitState state = runtimeStore.findCircuitState(key).orElse(null);
                if (state != null && "OPEN".equals(state.state()) && state.openUntil() != null && state.openUntil().isAfter(now)) {
                    return new RoutePolicyRuntimeDecision(
                            false,
                            "route_policy_circuit_open",
                            "CIRCUIT_OPEN",
                            state.openUntil(),
                            List.of(policy.policy().getId())
                    );
                }
                if (state != null && "OPEN".equals(state.state()) && state.openUntil() != null && !state.openUntil().isAfter(now)) {
                    RoutingPolicyCircuitState halfOpen = runtimeStore.markHalfOpen(key, now);
                    if (halfOpen == null) {
                        return new RoutePolicyRuntimeDecision(
                                false,
                                "route_policy_circuit_half_open_probe_in_progress",
                                "CIRCUIT_HALF_OPEN",
                                now.plusSeconds(1),
                                List.of(policy.policy().getId())
                        );
                    }
                }
                matchedPolicyIds.add(policy.policy().getId());
            }

            RateLimitPolicy rateLimitPolicy = rateLimitPolicy(policy.policy());
            if (rateLimitPolicy.enabled() && rateLimitPolicy.requestsPerMinute() > 0) {
                RoutingPolicyRateWindowState window = runtimeStore.incrementRateWindow(
                        key,
                        policy.policy().getId(),
                        policy.targetRef(),
                        now,
                        Duration.ofSeconds(60)
                );
                matchedPolicyIds.add(policy.policy().getId());
                if (window.counter() > rateLimitPolicy.requestsPerMinute()) {
                    return new RoutePolicyRuntimeDecision(
                            false,
                            "route_policy_rate_limited",
                            "RATE_LIMITED",
                            window.expiresAt(),
                            List.of(policy.policy().getId())
                    );
                }
            }
        }
        return new RoutePolicyRuntimeDecision(true, "allowed", "HEALTHY", null, List.copyOf(matchedPolicyIds));
    }

    public void recordSuccess(RouteCandidateView candidate, UpstreamCredentialEntity credential) {
        for (MatchedPolicy policy : matchedPolicies(candidate, credential)) {
            CircuitPolicy circuitPolicy = circuitPolicy(policy.policy());
            if (!circuitPolicy.enabled()) {
                continue;
            }
            String key = runtimeKey(policy.policy(), policy.targetRef());
            runtimeStore.recordSuccess(key, policy.policy().getId(), policy.targetRef(), Instant.now());
        }
    }

    public void recordFailure(RouteCandidateView candidate, UpstreamCredentialEntity credential, String reason) {
        Instant now = Instant.now();
        for (MatchedPolicy policy : matchedPolicies(candidate, credential)) {
            CircuitPolicy circuitPolicy = circuitPolicy(policy.policy());
            if (!circuitPolicy.enabled()) {
                continue;
            }
            String key = runtimeKey(policy.policy(), policy.targetRef());
            runtimeStore.recordFailure(
                    key,
                    policy.policy().getId(),
                    policy.targetRef(),
                    circuitPolicy.failureThreshold(),
                    Duration.ofSeconds(circuitPolicy.openSeconds()),
                    reason,
                    now
            );
        }
    }

    public List<RoutingPolicyRuntimeStateResponse> states() {
        Instant now = Instant.now();
        List<RoutingPolicyRuntimeStateResponse> states = new ArrayList<>();
        for (RoutingPolicyRuntimeStoreSnapshot snapshot : runtimeStore.snapshots(now)) {
            RoutingPolicyCircuitState state = snapshot.circuitState();
            RoutingPolicyRateWindowState rateWindow = snapshot.rateWindow();
            RuntimeKey parsed = state == null ? parseRuntimeKey(snapshot.runtimeKey()) : null;
            states.add(new RoutingPolicyRuntimeStateResponse(
                    snapshot.runtimeKey(),
                    state == null ? parsed.policyId() : state.policyId(),
                    state == null ? parsed.targetRef() : state.targetRef(),
                    state == null ? "RATE_WINDOW" : state.state(),
                    state == null ? 0 : state.failureCount(),
                    state == null ? null : state.openUntil(),
                    rateWindow == null || !rateWindow.expiresAt().isAfter(now) ? 0 : rateWindow.counter(),
                    rateWindow == null ? null : rateWindow.expiresAt(),
                    state == null ? "rate-limit-window" : state.reason()
            ));
        }
        return states.stream()
                .sorted(Comparator.comparing(RoutingPolicyRuntimeStateResponse::runtimeKey))
                .toList();
    }

    public void reset() {
        runtimeStore.reset();
    }

    public void reset(String runtimeKey) {
        if (runtimeKey == null || runtimeKey.isBlank()) {
            reset();
            return;
        }
        runtimeStore.reset(runtimeKey.trim());
    }

    private List<MatchedPolicy> matchedPolicies(RouteCandidateView candidate, UpstreamCredentialEntity credential) {
        return routeGuardPolicyRepository.findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc().stream()
                .map(policy -> match(policy, candidate, credential))
                .filter(item -> item != null)
                .toList();
    }

    private MatchedPolicy match(
            RouteGuardPolicyEntity policy,
            RouteCandidateView candidate,
            UpstreamCredentialEntity credential) {
        if (policy.getProviderType() != null && policy.getProviderType() != candidate.candidate().providerType()) {
            return null;
        }
        GovernanceTargetType targetType = policy.getTargetType();
        if (targetType == null) {
            return null;
        }
        return switch (targetType) {
            case PROVIDER_TYPE -> policy.getProviderType() == null || policy.getProviderType() == candidate.candidate().providerType()
                    ? new MatchedPolicy(policy, "provider:" + candidate.candidate().providerType().name())
                    : null;
            case SITE_PROFILE -> equalsLong(policy.getSiteProfileId(), candidate.candidate().siteProfileId())
                    ? new MatchedPolicy(policy, "site:" + policy.getSiteProfileId())
                    : null;
            case CREDENTIAL -> equalsLong(policy.getCredentialId(), candidate.candidate().credentialId())
                    ? new MatchedPolicy(policy, "credential:" + policy.getCredentialId())
                    : null;
            case PROXY -> credential != null && equalsLong(policy.getProxyId(), credential.getProxyId())
                    ? new MatchedPolicy(policy, "proxy:" + policy.getProxyId())
                    : null;
            case ACCOUNT -> null;
        };
    }

    private CircuitPolicy circuitPolicy(RouteGuardPolicyEntity policy) {
        JsonNode node = parse(policy.getCircuitBreakerPolicy());
        if (node == null) {
            return new CircuitPolicy(false, DEFAULT_FAILURE_THRESHOLD, DEFAULT_OPEN_SECONDS);
        }
        boolean enabled = node.path("enabled").asBoolean(true);
        int threshold = firstPositiveInt(node, DEFAULT_FAILURE_THRESHOLD, "failureThreshold", "threshold", "failures");
        int openSeconds = firstPositiveInt(node, DEFAULT_OPEN_SECONDS, "openSeconds", "cooldownSeconds", "durationSeconds");
        return new CircuitPolicy(enabled, threshold, openSeconds);
    }

    private RateLimitPolicy rateLimitPolicy(RouteGuardPolicyEntity policy) {
        JsonNode node = parse(policy.getRateLimitPolicy());
        if (node == null) {
            return new RateLimitPolicy(false, 0);
        }
        boolean enabled = node.path("enabled").asBoolean(true);
        int requestsPerMinute = firstPositiveInt(node, 0, "requestsPerMinute", "rpm", "limit");
        return new RateLimitPolicy(enabled, requestsPerMinute);
    }

    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            return node == null || !node.isObject() ? null : node;
        } catch (Exception ignored) {
            return null;
        }
    }

    private int firstPositiveInt(JsonNode node, int fallback, String... fieldNames) {
        for (String fieldName : fieldNames) {
            int value = node.path(fieldName).asInt(0);
            if (value > 0) {
                return value;
            }
        }
        return fallback;
    }

    private String runtimeKey(RouteGuardPolicyEntity policy, String targetRef) {
        return "policy:" + policy.getId() + ":" + targetRef;
    }

    private RuntimeKey parseRuntimeKey(String runtimeKey) {
        String[] parts = runtimeKey.split(":", 4);
        Long policyId = null;
        if (parts.length > 1) {
            try {
                policyId = Long.parseLong(parts[1]);
            } catch (NumberFormatException ignored) {
                policyId = null;
            }
        }
        String targetRef = parts.length > 3 ? parts[2] + ":" + parts[3] : runtimeKey;
        return new RuntimeKey(policyId, targetRef);
    }

    private boolean equalsLong(Long left, Long right) {
        return left != null && left.equals(right);
    }

    private record CircuitPolicy(boolean enabled, int failureThreshold, int openSeconds) {
    }

    private record RateLimitPolicy(boolean enabled, int requestsPerMinute) {
    }

    private record MatchedPolicy(RouteGuardPolicyEntity policy, String targetRef) {
    }

    private record RuntimeKey(Long policyId, String targetRef) {
    }
}
