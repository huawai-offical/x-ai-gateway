package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.CostEstimateRequest;
import com.prodigalgal.xaigateway.admin.api.CostModelRequest;
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
}
