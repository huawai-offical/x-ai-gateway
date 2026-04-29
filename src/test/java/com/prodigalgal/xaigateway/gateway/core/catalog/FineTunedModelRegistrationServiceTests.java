package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.persistence.entity.ModelAliasEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.ModelAliasRuleEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteModelCapabilityEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ModelAliasRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.ModelAliasRuleRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteModelCapabilityRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FineTunedModelRegistrationServiceTests {

    @Test
    void shouldRegisterCapabilityAndAliasFromTuningJob() {
        SiteModelCapabilityRepository capabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        UpstreamSiteProfileRepository siteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        ModelAliasRepository aliasRepository = Mockito.mock(ModelAliasRepository.class);
        ModelAliasRuleRepository ruleRepository = Mockito.mock(ModelAliasRuleRepository.class);
        FineTunedModelRegistrationService service = new FineTunedModelRegistrationService(
                capabilityRepository,
                siteProfileRepository,
                aliasRepository,
                ruleRepository
        );

        UpstreamSiteProfileEntity siteProfile = new UpstreamSiteProfileEntity();
        ReflectionTestUtils.setField(siteProfile, "id", 2L);
        siteProfile.setSiteKind(UpstreamSiteKind.GEMINI_DIRECT);

        SiteModelCapabilityEntity template = new SiteModelCapabilityEntity();
        ReflectionTestUtils.setField(template, "id", 7L);
        template.setSiteProfile(siteProfile);
        template.setModelName("gemini-2.5-pro");
        template.setModelKey("gemini-2.5-pro");
        template.setSupportedProtocols(List.of("openai", "google_native"));
        template.setSupportsChat(true);
        template.setSupportsTools(true);
        template.setSupportsImageInput(true);
        template.setSupportsEmbeddings(false);
        template.setSupportsCache(true);
        template.setSupportsThinking(true);
        template.setSupportsVisibleReasoning(true);
        template.setSupportsReasoningReuse(true);
        template.setReasoningTransport(ReasoningTransport.GEMINI_THOUGHTS);
        template.setCapabilityLevel(InteropCapabilityLevel.NATIVE);

        Mockito.when(siteProfileRepository.findById(2L)).thenReturn(Optional.of(siteProfile));
        Mockito.when(capabilityRepository.findAllBySiteProfile_IdOrderByModelKeyAsc(2L)).thenReturn(List.of(template));
        Mockito.when(aliasRepository.findAll()).thenReturn(List.of());
        Mockito.when(aliasRepository.save(Mockito.any())).thenAnswer(invocation -> {
            ModelAliasEntity entity = invocation.getArgument(0);
            if (ReflectionTestUtils.getField(entity, "id") == null) {
                ReflectionTestUtils.setField(entity, "id", 31L);
            }
            return entity;
        });
        Mockito.when(ruleRepository.findAllByAliasIdAndEnabledTrueOrderByPriorityAscCreatedAtAsc(31L)).thenReturn(List.of());
        Mockito.when(capabilityRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(ruleRepository.saveAll(Mockito.anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.register(
                2L,
                ProviderType.GEMINI_DIRECT,
                "gemini-2.5-pro",
                "tunedModels/demo",
                "demo-suffix",
                "ftjob_1"
        );

        assertEquals("tunedModels/demo", result.modelName());
        assertEquals("tunedmodels/demo", result.modelKey());
        assertEquals(List.of("demo-suffix"), result.aliases());

        ArgumentCaptor<SiteModelCapabilityEntity> capabilityCaptor = ArgumentCaptor.forClass(SiteModelCapabilityEntity.class);
        Mockito.verify(capabilityRepository).save(capabilityCaptor.capture());
        assertEquals("tunedModels/demo", capabilityCaptor.getValue().getModelName());
        assertEquals("tunedmodels/demo", capabilityCaptor.getValue().getModelKey());
        assertTrue(capabilityCaptor.getValue().isSupportsThinking());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModelAliasRuleEntity>> rulesCaptor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(ruleRepository).saveAll(rulesCaptor.capture());
        assertEquals(2, rulesCaptor.getValue().size());
        assertEquals("demo-suffix", rulesCaptor.getValue().get(0).getAlias().getAliasName());
    }

    @Test
    void shouldUnregisterCapabilityAndDisableAutoAlias() {
        SiteModelCapabilityRepository capabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        UpstreamSiteProfileRepository siteProfileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        ModelAliasRepository aliasRepository = Mockito.mock(ModelAliasRepository.class);
        ModelAliasRuleRepository ruleRepository = Mockito.mock(ModelAliasRuleRepository.class);
        FineTunedModelRegistrationService service = new FineTunedModelRegistrationService(
                capabilityRepository,
                siteProfileRepository,
                aliasRepository,
                ruleRepository
        );

        SiteModelCapabilityEntity capability = new SiteModelCapabilityEntity();
        capability.setModelKey("tunedmodels-demo");
        ModelAliasEntity alias = new ModelAliasEntity();
        ReflectionTestUtils.setField(alias, "id", 41L);
        alias.setAliasName("demo-suffix");
        alias.setAliasKey("demo-suffix");
        alias.setEnabled(true);
        alias.setDescription("Auto registered from fine-tuning job ftjob_1");
        ModelAliasRuleEntity rule = new ModelAliasRuleEntity();
        rule.setAlias(alias);
        rule.setEnabled(true);

        Mockito.when(capabilityRepository.findAllBySiteProfile_IdOrderByModelKeyAsc(2L)).thenReturn(List.of(capability));
        Mockito.when(aliasRepository.findAll()).thenReturn(List.of(alias));
        Mockito.when(ruleRepository.findAllByAliasIdAndEnabledTrueOrderByPriorityAscCreatedAtAsc(41L)).thenReturn(List.of(rule));
        Mockito.when(aliasRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.unregister(2L, "tunedmodels-demo", List.of("demo-suffix"), "ftjob_1");

        assertEquals(1, result.removedCapabilities());
        assertEquals(1, result.disabledAliases());
        Mockito.verify(capabilityRepository).deleteAll(List.of(capability));
        Mockito.verify(ruleRepository).deleteAll(List.of(rule));
        assertFalse(alias.isEnabled());
    }
}
