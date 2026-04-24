package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.CostEstimateRequest;
import com.prodigalgal.xaigateway.admin.api.CostModelRequest;
import com.prodigalgal.xaigateway.infra.persistence.entity.CostModelEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.CostModelRepository;
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
        CostRoutingService service = new CostRoutingService(repository);

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

        var estimate = service.estimate(new CostEstimateRequest("openai", "gpt-4o-mini", 1_000L, 2_000L, 500L));
        assertEquals(710_000L, estimate.estimatedMicros());

        var summary = service.summary();
        assertEquals(1L, summary.activeModels());
        assertTrue(summary.sampleMonthlyMicros() > 0);
    }
}
