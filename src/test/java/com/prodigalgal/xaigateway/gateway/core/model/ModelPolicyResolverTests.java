package com.prodigalgal.xaigateway.gateway.core.model;

import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccountGroupBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.ModelPolicyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountGroupBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.ModelPolicyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class ModelPolicyResolverTests {

    @Test
    void shouldMapPublicModelToPolicyUpstreamCandidates() {
        Fixture fixture = new Fixture();
        ModelPolicyEntity policy = policy(ModelPolicyScopeType.DISTRIBUTED_KEY, 1L, "gpt-5-codex", "mimo-v2.5-pro", "MAP");
        when(fixture.policyRepository.findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc()).thenReturn(List.of(policy));
        when(fixture.credentialRepository.findAllByIdInAndDeletedFalse(Mockito.anyCollection()))
                .thenReturn(List.of(credential(101L, null, 301L)));
        when(fixture.siteProfileRepository.findAllById(Mockito.anyCollection()))
                .thenReturn(List.of(siteProfile(301L)));
        when(fixture.catalogQueryService.listCandidatesByModelKey("mimo-v2.5-pro"))
                .thenReturn(List.of(candidate(101L, "mimo-v2.5-pro")));

        ModelPolicyResolvedModel resolved = fixture.resolver.resolveRequestedModel(key(), "responses", "gpt-5-codex");

        assertEquals("gpt-5-codex", resolved.publicModel());
        assertEquals("mimo-v2.5-pro", resolved.resolvedModelKey());
        assertEquals(1, resolved.candidates().size());
        assertTrue(resolved.policyMapped());
    }

    @Test
    void shouldDenyCandidateWhenCredentialPolicyBlocksModel() {
        Fixture fixture = new Fixture();
        ModelPolicyEntity deny = policy(ModelPolicyScopeType.CREDENTIAL, 101L, "gpt-5-codex", "mimo-v2.5-pro", "DENY");
        deny.setDeny(true);
        when(fixture.policyRepository.findAllByScopeTypeAndScopeIdAndEnabledTrueOrderByPriorityAscCreatedAtAsc(ModelPolicyScopeType.CREDENTIAL, 101L))
                .thenReturn(List.of(deny));
        when(fixture.credentialRepository.findById(101L)).thenReturn(Optional.of(credential(101L, 201L, 301L)));
        when(fixture.siteProfileRepository.findById(301L)).thenReturn(Optional.of(siteProfile(301L)));
        when(fixture.groupRepository.findById(201L)).thenReturn(Optional.of(group(201L, List.of())));

        ModelPolicyCandidateDecision decision = fixture.resolver.evaluateCandidate(
                key(),
                "responses",
                "gpt-5-codex",
                "gpt-5-codex",
                new RouteCandidateView(candidate(101L, "mimo-v2.5-pro"), 11L, 10, 100)
        );

        assertFalse(decision.allowed());
        assertTrue(decision.exclusionReasons().contains("model_policy_denied:credential"));
    }

    @Test
    void shouldApplyFallbackAndCanaryRuntimePolicyToCandidateWeightAndPriority() {
        Fixture fixture = new Fixture();
        ModelPolicyEntity runtime = policy(ModelPolicyScopeType.DISTRIBUTED_KEY, 1L, "gpt-5-codex", "mimo-v2.5-pro", "RUNTIME");
        runtime.setRuntimePolicyJson("""
                {
                  "fallbackChain": ["deepseek-chat", "mimo-v2.5-pro"],
                  "canary": {"weight": 250}
                }
                """);
        when(fixture.policyRepository.findAllByScopeTypeAndScopeIdAndEnabledTrueOrderByPriorityAscCreatedAtAsc(ModelPolicyScopeType.DISTRIBUTED_KEY, 1L))
                .thenReturn(List.of(runtime));
        when(fixture.credentialRepository.findById(101L)).thenReturn(Optional.of(credential(101L, null, 301L)));
        when(fixture.siteProfileRepository.findById(301L)).thenReturn(Optional.of(siteProfile(301L)));

        ModelPolicyCandidateDecision decision = fixture.resolver.evaluateCandidate(
                key(),
                "responses",
                "gpt-5-codex",
                "gpt-5-codex",
                new RouteCandidateView(candidate(101L, "mimo-v2.5-pro"), 11L, 10, 100)
        );

        assertTrue(decision.allowed());
        assertEquals(30, decision.candidate().bindingPriority());
        assertEquals(250, decision.candidate().bindingWeight());
        assertTrue(decision.scoreNotes().contains("fallback_chain_index=1"));
        assertTrue(decision.scoreNotes().contains("canary_weight=250"));
    }

    @Test
    void shouldRateLimitAfterRecordedSuccess() {
        Fixture fixture = new Fixture();
        ModelPolicyEntity runtime = policy(ModelPolicyScopeType.DISTRIBUTED_KEY, 1L, "gpt-5-codex", "mimo-v2.5-pro", "RUNTIME");
        ReflectionTestUtils.setField(runtime, "id", 91L);
        runtime.setRuntimePolicyJson("{\"rateLimit\":{\"rpm\":1}}");
        when(fixture.policyRepository.findAllByScopeTypeAndScopeIdAndEnabledTrueOrderByPriorityAscCreatedAtAsc(ModelPolicyScopeType.DISTRIBUTED_KEY, 1L))
                .thenReturn(List.of(runtime));
        when(fixture.credentialRepository.findById(101L)).thenReturn(Optional.of(credential(101L, null, 301L)));
        when(fixture.siteProfileRepository.findById(301L)).thenReturn(Optional.of(siteProfile(301L)));
        RouteCandidateView candidate = new RouteCandidateView(candidate(101L, "mimo-v2.5-pro"), 11L, 10, 100);
        RouteSelectionResult selection = new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "gpt-5-codex",
                "gpt-5-codex",
                "mimo-v2.5-pro",
                "responses",
                "prefix",
                "fingerprint",
                "gpt-5-codex",
                RouteSelectionSource.WEIGHTED_HASH,
                candidate,
                List.of(candidate)
        );

        fixture.resolver.recordSuccess(selection);

        ModelPolicyCandidateDecision decision = fixture.resolver.evaluateCandidate(
                key(),
                "responses",
                "gpt-5-codex",
                "gpt-5-codex",
                candidate
        );
        assertFalse(decision.allowed());
        assertTrue(decision.exclusionReasons().contains("model_policy_rate_limited"));
    }

    private static DistributedKeyView key() {
        return new DistributedKeyView(
                1L,
                "test",
                "sk-gw-test",
                "masked",
                List.of("responses"),
                List.of(),
                List.of(new DistributedCredentialBindingView(
                        11L,
                        101L,
                        "mimo",
                        ProviderType.OPENAI_COMPATIBLE,
                        "https://token-plan-sgp.xiaomimimo.com/v1",
                        10,
                        100
                ))
        );
    }

    private static CatalogCandidateView candidate(Long credentialId, String modelKey) {
        return new CatalogCandidateView(
                credentialId,
                "credential-" + credentialId,
                ProviderType.OPENAI_COMPATIBLE,
                301L,
                ProviderFamily.OPENAI,
                UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC,
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1,
                ErrorSchemaStrategy.OPENAI_ERROR,
                "https://example.com/v1",
                modelKey,
                modelKey,
                List.of("responses", "openai"),
                true,
                true,
                false,
                false,
                false,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT,
                com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel.NATIVE
        );
    }

    private static ModelPolicyEntity policy(
            ModelPolicyScopeType scopeType,
            Long scopeId,
            String publicModel,
            String upstreamModel,
            String kind) {
        ModelPolicyEntity entity = new ModelPolicyEntity();
        entity.setScopeType(scopeType);
        entity.setScopeId(scopeId);
        entity.setPolicyKind(kind);
        entity.setPublicModel(publicModel);
        entity.setPublicModelKey(publicModel);
        entity.setUpstreamModel(upstreamModel);
        entity.setUpstreamModelKey(upstreamModel);
        entity.setSupportedProtocols(List.of("responses"));
        entity.setEnabled(true);
        entity.setDeny("DENY".equals(kind));
        entity.setPriority(100);
        entity.setWeight(100);
        entity.setMappingSource("manual");
        return entity;
    }

    private static UpstreamCredentialEntity credential(Long id, Long groupId, Long siteProfileId) {
        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        ReflectionTestUtils.setField(credential, "id", id);
        credential.setProviderType(ProviderType.OPENAI_COMPATIBLE);
        credential.setBaseUrl("https://example.com/v1");
        credential.setGroupId(groupId);
        credential.setSiteProfileId(siteProfileId);
        return credential;
    }

    private static UpstreamAccountGroupEntity group(Long id, List<String> models) {
        UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(group, "id", id);
        group.setSupportedModels(models);
        return group;
    }

    private static UpstreamSiteProfileEntity siteProfile(Long id) {
        UpstreamSiteProfileEntity profile = new UpstreamSiteProfileEntity();
        ReflectionTestUtils.setField(profile, "id", id);
        profile.setVendorCode("xiaomi_mimo");
        profile.setSiteKind(UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC);
        return profile;
    }

    private static class Fixture {
        private final ModelPolicyRepository policyRepository = Mockito.mock(ModelPolicyRepository.class);
        private final ModelCatalogQueryService catalogQueryService = Mockito.mock(ModelCatalogQueryService.class);
        private final UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        private final UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        private final UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        private final UpstreamSiteProfileRepository siteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        private final DistributedKeyAccountGroupBindingRepository bindingRepository = Mockito.mock(DistributedKeyAccountGroupBindingRepository.class);
        private final ModelPolicyRuntimeStateService runtimeStateService = new ModelPolicyRuntimeStateService();
        private final ModelPolicyResolver resolver = new ModelPolicyResolver(
                policyRepository,
                catalogQueryService,
                credentialRepository,
                groupRepository,
                accountRepository,
                siteProfileRepository,
                bindingRepository,
                runtimeStateService,
                new ObjectMapper()
        );

        private Fixture() {
            when(policyRepository.findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc()).thenReturn(List.of());
            when(credentialRepository.findAllByIdInAndDeletedFalse(Mockito.anyCollection())).thenReturn(List.of());
            when(siteProfileRepository.findAllById(Mockito.anyCollection())).thenReturn(List.of());
            when(bindingRepository.findAllByDistributedKey_IdAndActiveTrueOrderByPriorityAscCreatedAtAsc(1L))
                    .thenReturn(List.of());
            when(bindingRepository.findAllByDistributedKey_IdAndProviderTypeAndActiveTrueOrderByPriorityAscCreatedAtAsc(1L, ProviderType.OPENAI_COMPATIBLE))
                    .thenReturn(List.of());
            when(accountRepository.findAllByGroup_IdAndActiveTrueAndFrozenFalseAndHealthyTrueOrderByUpdatedAtDesc(Mockito.anyLong()))
                    .thenReturn(List.of());
        }
    }
}
