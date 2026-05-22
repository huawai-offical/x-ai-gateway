package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.GovernanceHealthScoreResponse;
import com.prodigalgal.xaigateway.admin.api.RouteGuardPolicyRequest;
import com.prodigalgal.xaigateway.admin.api.RouteGuardPolicyResponse;
import com.prodigalgal.xaigateway.admin.api.RoutingPolicyRuntimePlanResponse;
import com.prodigalgal.xaigateway.admin.api.RoutingPolicySummaryResponse;
import com.prodigalgal.xaigateway.admin.application.integrations.PlatformEventPublisher;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceActionType;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceDecision;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernancePolicyEngine;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernancePolicyMode;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceTargetType;
import com.prodigalgal.xaigateway.gateway.core.routing.CredentialHealthState;
import com.prodigalgal.xaigateway.gateway.core.routing.HealthStateStore;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelAddressingStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteGuardPolicyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AutoActionRuleRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.QuarantineRecordRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteGuardPolicyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernanceAdminServiceTests {

    @Test
    void shouldPersistRoutingPolicyConfigAndSummarizeCoverage() {
        RouteGuardPolicyRepository routeGuardPolicyRepository = Mockito.mock(RouteGuardPolicyRepository.class);
        AutoActionRuleRepository autoActionRuleRepository = Mockito.mock(AutoActionRuleRepository.class);
        QuarantineRecordRepository quarantineRecordRepository = Mockito.mock(QuarantineRecordRepository.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        OpsAuditService opsAuditService = Mockito.mock(OpsAuditService.class);
        GovernanceAdminService service = new GovernanceAdminService(
                routeGuardPolicyRepository,
                autoActionRuleRepository,
                quarantineRecordRepository,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                Mockito.mock(HealthStateStore.class),
                Mockito.mock(GovernancePolicyEngine.class),
                opsAuditService,
                new ObjectMapper(),
                Mockito.mock(PlatformEventPublisher.class)
        );
        Mockito.when(routeGuardPolicyRepository.save(Mockito.any(RouteGuardPolicyEntity.class)))
                .thenAnswer(invocation -> {
                    RouteGuardPolicyEntity entity = invocation.getArgument(0);
                    ReflectionTestUtils.setField(entity, "id", 7L);
                    ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-05-01T08:00:00Z"));
                    ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-05-01T08:05:00Z"));
                    return entity;
                });

        RouteGuardPolicyRequest request = new RouteGuardPolicyRequest(
                "guard-openai",
                GovernanceTargetType.PROVIDER_TYPE,
                ProviderType.OPENAI_DIRECT,
                null,
                null,
                null,
                null,
                GovernancePolicyMode.ENFORCE,
                GovernanceActionType.COOLDOWN,
                300,
                10,
                true,
                "sample",
                "{\"maxAttempts\":2}",
                "{\"order\":[\"same_site\"]}",
                "{\"failureThreshold\":3}",
                "{\"rpm\":60}"
        );

        RouteGuardPolicyResponse saved = service.saveRouteGuard(null, request);
        Mockito.when(routeGuardPolicyRepository.findAllByOrderByPriorityAscCreatedAtAsc())
                .thenReturn(List.of(capturedRoutePolicy(saved)));

        RoutingPolicySummaryResponse summary = service.routingPolicySummary();

        assertEquals("{\"maxAttempts\":2}", saved.retryPolicy());
        assertEquals(1, summary.totalPolicies());
        assertEquals(1, summary.retryConfigured());
        assertEquals(1, summary.fallbackConfigured());
        assertEquals(1, summary.circuitBreakerConfigured());
        assertEquals(1, summary.rateLimitConfigured());
        assertTrue(summary.policies().getFirst().enabled());
        Mockito.verify(opsAuditService).record(
                Mockito.eq("GOVERNANCE"),
                Mockito.eq("ROUTE_GUARD_CREATED"),
                Mockito.eq("ROUTE_GUARD"),
                Mockito.eq("7"),
                Mockito.contains("retryPolicyConfigured"));
    }

    @Test
    void shouldParseRoutingRuntimePlanAndReportWarnings() {
        RouteGuardPolicyRepository routeGuardPolicyRepository = Mockito.mock(RouteGuardPolicyRepository.class);
        RoutingPolicyRuntimeConfigService service = new RoutingPolicyRuntimeConfigService(
                routeGuardPolicyRepository,
                new ObjectMapper()
        );

        RouteGuardPolicyEntity primary = new RouteGuardPolicyEntity();
        ReflectionTestUtils.setField(primary, "id", 91L);
        primary.setPolicyName("runtime-plan-primary");
        primary.setTargetType(GovernanceTargetType.PROVIDER_TYPE);
        primary.setProviderType(ProviderType.OPENAI_DIRECT);
        primary.setEnabled(true);
        primary.setRetryPolicy("{\"maxAttempts\":2}");
        primary.setFallbackPolicy("{\"enabled\":true,\"order\":[\"score\",\"priority\"]}");
        primary.setCircuitBreakerPolicy("{\"enabled\":true,\"failureThreshold\":3}");
        primary.setRateLimitPolicy("{\"enabled\":true,\"rpm\":60}");

        RouteGuardPolicyEntity invalid = new RouteGuardPolicyEntity();
        ReflectionTestUtils.setField(invalid, "id", 92L);
        invalid.setPolicyName("runtime-plan-invalid");
        invalid.setTargetType(GovernanceTargetType.PROVIDER_TYPE);
        invalid.setProviderType(ProviderType.OPENAI_DIRECT);
        invalid.setEnabled(true);
        invalid.setRetryPolicy("{bad-json");

        Mockito.when(routeGuardPolicyRepository.findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc())
                .thenReturn(List.of(primary, invalid));

        RoutingPolicyRuntimePlanResponse plan = service.runtimePlan(3);

        assertEquals(2, plan.maxAttempts());
        assertTrue(plan.fallbackEnabled());
        assertEquals(List.of("score", "priority"), plan.fallbackOrder());
        assertTrue(plan.circuitBreakerEnabled());
        assertEquals(3, plan.circuitFailureThreshold());
        assertTrue(plan.rateLimitEnabled());
        assertEquals(60, plan.requestsPerMinute());
        assertTrue(plan.sourcePolicyIds().contains(91L));
        assertTrue(plan.warnings().stream().anyMatch(item -> item.contains("不是合法 JSON")));
    }

    @Test
    void shouldAggregateHealthScoresForSitesAndCredentials() {
        RouteGuardPolicyRepository routeGuardPolicyRepository = Mockito.mock(RouteGuardPolicyRepository.class);
        AutoActionRuleRepository autoActionRuleRepository = Mockito.mock(AutoActionRuleRepository.class);
        QuarantineRecordRepository quarantineRecordRepository = Mockito.mock(QuarantineRecordRepository.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        HealthStateStore healthStateStore = Mockito.mock(HealthStateStore.class);
        GovernancePolicyEngine governancePolicyEngine = Mockito.mock(GovernancePolicyEngine.class);
        OpsAuditService opsAuditService = Mockito.mock(OpsAuditService.class);
        PlatformEventPublisher platformEventPublisher = Mockito.mock(PlatformEventPublisher.class);
        GovernanceAdminService service = new GovernanceAdminService(
                routeGuardPolicyRepository,
                autoActionRuleRepository,
                quarantineRecordRepository,
                upstreamCredentialRepository,
                upstreamSiteProfileRepository,
                healthStateStore,
                governancePolicyEngine,
                opsAuditService,
                new ObjectMapper(),
                platformEventPublisher
        );

        UpstreamSiteProfileEntity site = new UpstreamSiteProfileEntity();
        ReflectionTestUtils.setField(site, "id", 1L);
        site.setProfileCode("openai-main");
        site.setDisplayName("OpenAI 主站");
        site.setProviderFamily(ProviderFamily.OPENAI);
        site.setSiteKind(UpstreamSiteKind.OPENAI_DIRECT);
        site.setAuthStrategy(AuthStrategy.BEARER);
        site.setPathStrategy(PathStrategy.OPENAI_V1);
        site.setModelAddressingStrategy(ModelAddressingStrategy.MODEL_NAME);
        site.setErrorSchemaStrategy(ErrorSchemaStrategy.OPENAI_ERROR);
        site.setActive(true);

        UpstreamCredentialEntity healthy = new UpstreamCredentialEntity();
        ReflectionTestUtils.setField(healthy, "id", 101L);
        healthy.setCredentialName("openai-primary");
        healthy.setProviderType(ProviderType.OPENAI_DIRECT);
        healthy.setSiteProfileId(1L);
        healthy.setBaseUrl("https://api.openai.com");
        healthy.setApiKeyCiphertext("cipher");
        healthy.setApiKeyFingerprint("fp-101");
        healthy.setActive(true);

        UpstreamCredentialEntity cooling = new UpstreamCredentialEntity();
        ReflectionTestUtils.setField(cooling, "id", 102L);
        cooling.setCredentialName("openai-secondary");
        cooling.setProviderType(ProviderType.OPENAI_DIRECT);
        cooling.setSiteProfileId(1L);
        cooling.setBaseUrl("https://api.openai.com");
        cooling.setApiKeyCiphertext("cipher");
        cooling.setApiKeyFingerprint("fp-102");
        cooling.setActive(true);

        Mockito.when(upstreamCredentialRepository.findAllByDeletedFalseOrderByCreatedAtDesc())
                .thenReturn(List.of(cooling, healthy));
        Mockito.when(upstreamSiteProfileRepository.findAll()).thenReturn(List.of(site));
        Mockito.when(quarantineRecordRepository.findAllByStatusOrderByStartedAtDesc(Mockito.any()))
                .thenReturn(List.of());
        Mockito.when(healthStateStore.getCredentialState(102L))
                .thenReturn(Optional.of(new CredentialHealthState("COOLDOWN", "runtime cooldown", Instant.now().plusSeconds(300))));
        Mockito.when(healthStateStore.getCredentialState(101L)).thenReturn(Optional.empty());
        Mockito.when(governancePolicyEngine.evaluate(Mockito.argThat(matchesCredential(102L)))).thenReturn(GovernanceDecision.allow());
        Mockito.when(governancePolicyEngine.evaluate(Mockito.argThat(matchesCredential(101L)))).thenReturn(GovernanceDecision.allow());
        Mockito.when(governancePolicyEngine.evaluate(Mockito.argThat(matchesSite(1L)))).thenReturn(GovernanceDecision.allow());

        GovernanceHealthScoreResponse response = service.listHealthScores();

        assertEquals(2, response.credentials().size());
        assertEquals("COOLDOWN", response.credentials().getFirst().healthState());
        assertEquals(25, response.credentials().getFirst().score());
        assertEquals("DEGRADED", response.sites().getFirst().healthState());
        assertEquals(63, response.sites().getFirst().score());
        assertEquals(2, response.sites().getFirst().activeCredentialCount());
        assertEquals(1, response.sites().getFirst().blockedCredentialCount());
    }

    @Test
    void shouldIncludeAccountCredentialsInHealthScores() {
        RouteGuardPolicyRepository routeGuardPolicyRepository = Mockito.mock(RouteGuardPolicyRepository.class);
        AutoActionRuleRepository autoActionRuleRepository = Mockito.mock(AutoActionRuleRepository.class);
        QuarantineRecordRepository quarantineRecordRepository = Mockito.mock(QuarantineRecordRepository.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamAccountRepository upstreamAccountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamSiteProfileRepository upstreamSiteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        HealthStateStore healthStateStore = Mockito.mock(HealthStateStore.class);
        GovernancePolicyEngine governancePolicyEngine = Mockito.mock(GovernancePolicyEngine.class);
        GovernanceAdminService service = new GovernanceAdminService(
                routeGuardPolicyRepository,
                autoActionRuleRepository,
                quarantineRecordRepository,
                upstreamCredentialRepository,
                upstreamAccountRepository,
                upstreamSiteProfileRepository,
                healthStateStore,
                governancePolicyEngine,
                Mockito.mock(OpsAuditService.class),
                new ObjectMapper(),
                Mockito.mock(PlatformEventPublisher.class),
                null,
                null
        );

        UpstreamAccountEntity codex = new UpstreamAccountEntity();
        ReflectionTestUtils.setField(codex, "id", 701L);
        codex.setAccountName("codex-auth-json");
        codex.setProviderType(com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType.CODEX_OAUTH);
        codex.setActive(true);
        codex.setFrozen(false);
        codex.setHealthy(true);
        codex.setSiteProfileId(1L);
        Mockito.when(upstreamCredentialRepository.findAllByDeletedFalseOrderByCreatedAtDesc()).thenReturn(List.of());
        Mockito.when(upstreamAccountRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(codex));
        Mockito.when(upstreamSiteProfileRepository.findAll()).thenReturn(List.of());
        Mockito.when(quarantineRecordRepository.findAllByStatusOrderByStartedAtDesc(Mockito.any())).thenReturn(List.of());
        Mockito.when(governancePolicyEngine.evaluate(Mockito.argThat(context -> context != null && Long.valueOf(701L).equals(context.accountId()))))
                .thenReturn(GovernanceDecision.allow());

        GovernanceHealthScoreResponse response = service.listHealthScores();

        assertEquals(1, response.credentials().size());
        assertEquals("AUTH_JSON_ACCOUNT", response.credentials().getFirst().sourceType());
        assertEquals(701L, response.credentials().getFirst().accountId());
        assertEquals("codex-auth-json", response.credentials().getFirst().displayName());
        assertEquals(100, response.credentials().getFirst().score());
    }

    private ArgumentMatcher<com.prodigalgal.xaigateway.gateway.core.governance.GovernanceContext> matchesCredential(Long credentialId) {
        return context -> context != null && credentialId.equals(context.credentialId());
    }

    private ArgumentMatcher<com.prodigalgal.xaigateway.gateway.core.governance.GovernanceContext> matchesSite(Long siteProfileId) {
        return context -> context != null && siteProfileId.equals(context.siteProfileId()) && context.credentialId() == null;
    }

    private RouteGuardPolicyEntity capturedRoutePolicy(RouteGuardPolicyResponse response) {
        RouteGuardPolicyEntity entity = new RouteGuardPolicyEntity();
        ReflectionTestUtils.setField(entity, "id", response.id());
        ReflectionTestUtils.setField(entity, "createdAt", response.createdAt());
        ReflectionTestUtils.setField(entity, "updatedAt", response.updatedAt());
        entity.setPolicyName(response.policyName());
        entity.setTargetType(response.targetType());
        entity.setProviderType(response.providerType());
        entity.setPolicyMode(response.policyMode());
        entity.setActionType(response.actionType());
        entity.setTtlSeconds(response.ttlSeconds());
        entity.setPriority(response.priority());
        entity.setEnabled(response.enabled());
        entity.setDescription(response.description());
        entity.setRetryPolicy(response.retryPolicy());
        entity.setFallbackPolicy(response.fallbackPolicy());
        entity.setCircuitBreakerPolicy(response.circuitBreakerPolicy());
        entity.setRateLimitPolicy(response.rateLimitPolicy());
        return entity;
    }
}
