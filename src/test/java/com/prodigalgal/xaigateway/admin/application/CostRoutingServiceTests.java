package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.CostEstimateRequest;
import com.prodigalgal.xaigateway.admin.api.CostModelRequest;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageCompleteness;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageSource;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageView;
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
import com.prodigalgal.xaigateway.infra.persistence.entity.CostModelEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserBalanceLedgerEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.CostModelRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserBalanceLedgerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CostRoutingServiceTests {

    @Test
    void shouldCreateCostModelEstimateAndSummarize() {
        CostModelRepository repository = Mockito.mock(CostModelRepository.class);
        DistributedKeyRepository keyRepository = Mockito.mock(DistributedKeyRepository.class);
        GatewayUserBalanceLedgerRepository ledgerRepository = Mockito.mock(GatewayUserBalanceLedgerRepository.class);
        CostRoutingService service = new CostRoutingService(repository, keyRepository, ledgerRepository);

        Mockito.when(repository.save(Mockito.any())).thenAnswer(invocation -> {
            CostModelEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 51L);
            return entity;
        });

        var model = service.saveModel(null, new CostModelRequest("openai", "gpt-4o-mini", "usd", 100L, 300L, 20L, true, null));
        assertEquals("OPENAI", model.providerType());

        CostModelEntity entity = new CostModelEntity();
        ReflectionTestUtils.setField(entity, "id", 51L);
        entity.setProviderType("OPENAI");
        entity.setModelName("gpt-4o-mini");
        entity.setCurrency("USD");
        entity.setInputTokenMicros(100L);
        entity.setOutputTokenMicros(300L);
        entity.setCacheHitTokenMicros(20L);
        entity.setActive(true);
        Mockito.when(repository.findFirstByProviderTypeAndModelNameAndActiveTrueOrderByUpdatedAtDesc("OPENAI", "gpt-4o-mini"))
                .thenReturn(Optional.of(entity));
        Mockito.when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(entity));

        var estimate = service.estimate(new CostEstimateRequest("openai", "gpt-4o-mini", 1_000L, 2_000L, 500L, null, null, null, null));
        assertEquals(710_000L, estimate.estimatedMicros());
        assertTrue(estimate.allowed());

        var summary = service.summary();
        assertEquals(1L, summary.activeModels());
        assertTrue(summary.sampleMonthlyMicros() > 0);
    }

    @Test
    void shouldPreviewBudgetAndBalanceRejection() {
        CostModelRepository repository = Mockito.mock(CostModelRepository.class);
        DistributedKeyRepository keyRepository = Mockito.mock(DistributedKeyRepository.class);
        GatewayUserBalanceLedgerRepository ledgerRepository = Mockito.mock(GatewayUserBalanceLedgerRepository.class);
        CostRoutingService service = new CostRoutingService(repository, keyRepository, ledgerRepository);

        CostModelEntity model = new CostModelEntity();
        ReflectionTestUtils.setField(model, "id", 52L);
        model.setProviderType("OPENAI");
        model.setModelName("gpt-4o-mini");
        model.setCurrency("USD");
        model.setInputTokenMicros(100L);
        model.setOutputTokenMicros(300L);
        model.setCacheHitTokenMicros(20L);
        model.setActive(true);

        GatewayUserEntity user = new GatewayUserEntity();
        ReflectionTestUtils.setField(user, "id", 9L);

        DistributedKeyEntity key = new DistributedKeyEntity();
        ReflectionTestUtils.setField(key, "id", 71L);
        key.setKeyName("Budget Key");
        key.setOwnerUser(user);
        key.setBudgetLimitMicros(200_000L);
        key.setBudgetWindowSeconds(3600);

        GatewayUserBalanceLedgerEntity ledger = new GatewayUserBalanceLedgerEntity();
        ledger.setBalanceAfterTokenCredits(180_000L);

        Mockito.when(repository.findFirstByProviderTypeAndModelNameAndActiveTrueOrderByUpdatedAtDesc("OPENAI", "gpt-4o-mini"))
                .thenReturn(Optional.of(model));
        Mockito.when(keyRepository.findById(71L)).thenReturn(Optional.of(key));
        Mockito.when(ledgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(9L)).thenReturn(Optional.of(ledger));

        var estimate = service.estimate(new CostEstimateRequest("openai", "gpt-4o-mini", 1_000L, 1_000L, 0L, 71L, null, null, 250_000L));

        assertEquals(400_000L, estimate.estimatedMicros());
        assertEquals(71L, estimate.distributedKeyId());
        assertEquals(9L, estimate.ownerUserId());
        assertEquals(200_000L, estimate.budgetLimitMicros());
        assertEquals(180_000L, estimate.currentTokenCredits());
        assertEquals(3, estimate.rejectionReasons().size());
        assertTrue(!estimate.allowed());
    }

    @Test
    void shouldSettleCompletedUsageIntoUserLedgerOnce() {
        CostModelRepository repository = Mockito.mock(CostModelRepository.class);
        DistributedKeyRepository keyRepository = Mockito.mock(DistributedKeyRepository.class);
        GatewayUserBalanceLedgerRepository ledgerRepository = Mockito.mock(GatewayUserBalanceLedgerRepository.class);
        CostRoutingService service = new CostRoutingService(repository, keyRepository, ledgerRepository);

        CostModelEntity model = new CostModelEntity();
        ReflectionTestUtils.setField(model, "id", 53L);
        model.setProviderType("OPENAI_DIRECT");
        model.setModelName("gpt-4o-mini");
        model.setCurrency("USD");
        model.setInputTokenMicros(100L);
        model.setOutputTokenMicros(300L);
        model.setCacheHitTokenMicros(20L);
        model.setActive(true);

        GatewayUserEntity user = new GatewayUserEntity();
        ReflectionTestUtils.setField(user, "id", 18L);

        DistributedKeyEntity key = new DistributedKeyEntity();
        ReflectionTestUtils.setField(key, "id", 91L);
        key.setKeyName("Charge Key");
        key.setOwnerUser(user);

        GatewayUserBalanceLedgerEntity latestLedger = new GatewayUserBalanceLedgerEntity();
        latestLedger.setBalanceAfterTokenCredits(1_000_000L);

        Mockito.when(repository.findFirstByProviderTypeAndModelNameAndActiveTrueOrderByUpdatedAtDesc("OPENAI_DIRECT", "gpt-4o-mini"))
                .thenReturn(Optional.of(model));
        Mockito.when(keyRepository.findById(91L)).thenReturn(Optional.of(key));
        Mockito.when(ledgerRepository.findByReferenceTypeAndReferenceId("REQUEST_USAGE", "req-charge"))
                .thenReturn(Optional.empty());
        Mockito.when(ledgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(18L)).thenReturn(Optional.of(latestLedger));
        Mockito.when(ledgerRepository.save(Mockito.any())).thenAnswer(invocation -> {
            GatewayUserBalanceLedgerEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 501L);
            return entity;
        });

        var settled = service.settleCompletedUsage("req-charge", selectionResult(), usageView());

        assertTrue(settled.isPresent());
        assertEquals(501L, settled.get().ledgerId());
        assertEquals(18L, settled.get().userId());
        assertEquals(91L, settled.get().distributedKeyId());
        assertEquals("gpt-4o-mini", settled.get().modelName());
        assertEquals(251_000L, settled.get().chargedMicros());
        assertEquals(749_000L, settled.get().balanceAfterTokenCredits());
    }

    @Test
    void shouldSkipDuplicateSettlementForSameRequest() {
        CostModelRepository repository = Mockito.mock(CostModelRepository.class);
        DistributedKeyRepository keyRepository = Mockito.mock(DistributedKeyRepository.class);
        GatewayUserBalanceLedgerRepository ledgerRepository = Mockito.mock(GatewayUserBalanceLedgerRepository.class);
        CostRoutingService service = new CostRoutingService(repository, keyRepository, ledgerRepository);

        GatewayUserBalanceLedgerEntity existingLedger = new GatewayUserBalanceLedgerEntity();
        ReflectionTestUtils.setField(existingLedger, "id", 601L);
        Mockito.when(ledgerRepository.findByReferenceTypeAndReferenceId("REQUEST_USAGE", "req-charge"))
                .thenReturn(Optional.of(existingLedger));

        var settled = service.settleCompletedUsage("req-charge", selectionResult(), usageView());

        assertFalse(settled.isPresent());
        Mockito.verify(ledgerRepository, Mockito.never()).save(Mockito.any());
    }

    private RouteSelectionResult selectionResult() {
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "candidate",
                ProviderType.OPENAI_DIRECT,
                1L,
                ProviderFamily.OPENAI,
                UpstreamSiteKind.OPENAI_DIRECT,
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1,
                ErrorSchemaStrategy.OPENAI_ERROR,
                "https://example.com",
                "gpt-4o-mini",
                "gpt-4o-mini",
                List.of("openai"),
                true,
                true,
                true,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT,
                InteropCapabilityLevel.NATIVE
        );
        RouteCandidateView selected = new RouteCandidateView(candidate, 11L, 10, 100);
        return new RouteSelectionResult(
                91L,
                "sk-gw-charge",
                "gpt-4o-mini",
                "gpt-4o-mini",
                "gpt-4o-mini",
                "openai",
                "prefix",
                "fingerprint",
                "gpt-4o-mini",
                GatewayClientFamily.GENERIC_OPENAI,
                List.of(),
                null,
                RouteSelectionSource.WEIGHTED_HASH,
                selected,
                List.of(selected)
        );
    }

    private GatewayUsageView usageView() {
        return new GatewayUsageView(
                1_000,
                1_000,
                500,
                0,
                50,
                0,
                0,
                0,
                0,
                null,
                1_500,
                GatewayUsageCompleteness.FINAL,
                GatewayUsageSource.DIRECT_RESPONSE,
                null
        );
    }
}
