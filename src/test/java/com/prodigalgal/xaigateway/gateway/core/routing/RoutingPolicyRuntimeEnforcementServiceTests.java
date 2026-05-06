package com.prodigalgal.xaigateway.gateway.core.routing;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceTargetType;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteGuardPolicyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteGuardPolicyRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutingPolicyRuntimeEnforcementServiceTests {

    @Test
    void shouldApplyRateLimitAndCircuitBreakerForMatchedCredentialPolicy() {
        RouteGuardPolicyRepository repository = Mockito.mock(RouteGuardPolicyRepository.class);
        RouteGuardPolicyEntity policy = credentialPolicy();
        Mockito.when(repository.findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc()).thenReturn(List.of(policy));
        RoutingPolicyRuntimeEnforcementService service = new RoutingPolicyRuntimeEnforcementService(repository, new ObjectMapper());
        RouteCandidateView candidate = candidate();
        UpstreamCredentialEntity credential = credential();

        RoutePolicyRuntimeDecision first = service.evaluateCandidate(candidate, credential);
        RoutePolicyRuntimeDecision second = service.evaluateCandidate(candidate, credential);

        assertTrue(first.allowed());
        assertFalse(second.allowed());
        assertEquals("route_policy_rate_limited", second.reasonCode());
        assertEquals(1L, second.policyIds().getFirst());
        assertEquals("RATE_WINDOW", service.states().getFirst().state());

        service.reset();
        service.recordFailure(candidate, credential, "upstream 503");
        service.recordFailure(candidate, credential, "upstream 503");
        RoutePolicyRuntimeDecision circuitOpen = service.evaluateCandidate(candidate, credential);

        assertFalse(circuitOpen.allowed());
        assertEquals("route_policy_circuit_open", circuitOpen.reasonCode());
        assertEquals("OPEN", service.states().getFirst().state());
        assertEquals(2, service.states().getFirst().failureCount());

        service.recordSuccess(candidate, credential);
        assertEquals("CLOSED", service.states().getFirst().state());
        service.reset();
        assertTrue(service.states().isEmpty());
    }

    @Test
    void shouldShareRuntimeStateAcrossServiceInstancesWhenStoreIsShared() {
        RouteGuardPolicyRepository repository = Mockito.mock(RouteGuardPolicyRepository.class);
        RouteGuardPolicyEntity policy = credentialPolicy();
        Mockito.when(repository.findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc()).thenReturn(List.of(policy));
        RoutingPolicyRuntimeStore runtimeStore = new InMemoryRoutingPolicyRuntimeStore();
        RoutingPolicyRuntimeEnforcementService firstService = new RoutingPolicyRuntimeEnforcementService(
                repository,
                new ObjectMapper(),
                runtimeStore
        );
        RoutingPolicyRuntimeEnforcementService secondService = new RoutingPolicyRuntimeEnforcementService(
                repository,
                new ObjectMapper(),
                runtimeStore
        );
        RouteCandidateView candidate = candidate();
        UpstreamCredentialEntity credential = credential();

        RoutePolicyRuntimeDecision first = firstService.evaluateCandidate(candidate, credential);
        RoutePolicyRuntimeDecision second = secondService.evaluateCandidate(candidate, credential);

        assertTrue(first.allowed());
        assertFalse(second.allowed());
        assertEquals("route_policy_rate_limited", second.reasonCode());
        assertEquals(2, secondService.states().getFirst().currentWindowCount());
    }

    private RouteGuardPolicyEntity credentialPolicy() {
        RouteGuardPolicyEntity policy = new RouteGuardPolicyEntity();
        ReflectionTestUtils.setField(policy, "id", 1L);
        policy.setPolicyName("credential guard");
        policy.setTargetType(GovernanceTargetType.CREDENTIAL);
        policy.setProviderType(ProviderType.OPENAI_DIRECT);
        policy.setCredentialId(101L);
        policy.setEnabled(true);
        policy.setRateLimitPolicy("{\"rpm\":1}");
        policy.setCircuitBreakerPolicy("{\"failureThreshold\":2,\"openSeconds\":60}");
        return policy;
    }

    private RouteCandidateView candidate() {
        return new RouteCandidateView(
                new CatalogCandidateView(
                        101L,
                        "openai-primary",
                        ProviderType.OPENAI_DIRECT,
                        "https://api.openai.com",
                        "gpt-4o",
                        "gpt-4o",
                        List.of("openai"),
                        true,
                        false,
                        true,
                        true,
                        true,
                        true,
                        ReasoningTransport.OPENAI_CHAT
                ),
                11L,
                10,
                100
        );
    }

    private UpstreamCredentialEntity credential() {
        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        ReflectionTestUtils.setField(credential, "id", 101L);
        credential.setProviderType(ProviderType.OPENAI_DIRECT);
        credential.setBaseUrl("https://api.openai.com");
        return credential;
    }
}
