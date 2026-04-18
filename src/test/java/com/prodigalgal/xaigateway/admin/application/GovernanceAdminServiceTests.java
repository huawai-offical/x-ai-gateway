package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.GovernanceHealthScoreResponse;
import com.prodigalgal.xaigateway.admin.application.integrations.PlatformEventPublisher;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceDecision;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernancePolicyEngine;
import com.prodigalgal.xaigateway.gateway.core.routing.CredentialHealthState;
import com.prodigalgal.xaigateway.gateway.core.routing.HealthStateStore;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelAddressingStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AutoActionRuleRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.QuarantineRecordRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteGuardPolicyRepository;
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

class GovernanceAdminServiceTests {

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

    private ArgumentMatcher<com.prodigalgal.xaigateway.gateway.core.governance.GovernanceContext> matchesCredential(Long credentialId) {
        return context -> context != null && credentialId.equals(context.credentialId());
    }

    private ArgumentMatcher<com.prodigalgal.xaigateway.gateway.core.governance.GovernanceContext> matchesSite(Long siteProfileId) {
        return context -> context != null && siteProfileId.equals(context.siteProfileId()) && context.credentialId() == null;
    }
}
