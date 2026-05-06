package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestStatus;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageCompleteness;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageSource;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserBalanceLedgerEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.PaymentOrderEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UsageRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserBalanceLedgerRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.PaymentOrderRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UsageRecordRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitoringBillingRollupServiceTests {

    @Test
    void shouldAggregateUsageBillingAndChannelHealth() {
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);
        PaymentOrderRepository paymentOrderRepository = Mockito.mock(PaymentOrderRepository.class);
        GatewayUserBalanceLedgerRepository ledgerRepository = Mockito.mock(GatewayUserBalanceLedgerRepository.class);
        MonitoringBillingRollupService service = new MonitoringBillingRollupService(
                requestLogRepository,
                usageRecordRepository,
                paymentOrderRepository,
                ledgerRepository
        );
        Instant from = Instant.parse("2026-05-06T00:00:00Z");
        Instant to = Instant.parse("2026-05-07T00:00:00Z");
        List<RequestLogEntity> requests = List.of(
                request("req-1", ProviderType.OPENAI_DIRECT, "gpt-4o", GatewayRequestStatus.COMPLETED, 100L, from.plusSeconds(60), null),
                request("req-2", ProviderType.OPENAI_DIRECT, "gpt-4o", GatewayRequestStatus.FAILED, 300L, from.plusSeconds(120), "UPSTREAM_500")
        );
        List<UsageRecordEntity> usage = List.of(
                usage("req-1", ProviderType.OPENAI_DIRECT, "gpt-4o", 100, 40, 20, 160, from.plusSeconds(70))
        );
        List<PaymentOrderEntity> orders = List.of(order("PAID", 1990L, "CNY", 500L, from.plusSeconds(80)));
        List<GatewayUserBalanceLedgerEntity> ledgers = List.of(ledger(500L, 1500L, from.plusSeconds(90)));

        Mockito.when(requestLogRepository.searchWithinWindow(1L, ProviderType.OPENAI_DIRECT, from, to)).thenReturn(requests);
        Mockito.when(usageRecordRepository.searchWithinWindow(1L, ProviderType.OPENAI_DIRECT, from, to)).thenReturn(usage);
        Mockito.when(paymentOrderRepository.findAllByCreatedAtBetweenOrderByCreatedAtDesc(from, to)).thenReturn(orders);
        Mockito.when(ledgerRepository.findAllByCreatedAtBetweenOrderByCreatedAtDesc(from, to)).thenReturn(ledgers);

        var response = service.rollup("day", 1L, ProviderType.OPENAI_DIRECT, from, to);
        String csv = service.exportCsv("day", 1L, ProviderType.OPENAI_DIRECT, from, to);

        assertEquals(2, response.requestCount());
        assertEquals(1, response.failedRequestCount());
        assertEquals(0.5d, response.failureRate());
        assertEquals(160, response.totalTokens());
        assertEquals(1, response.billing().paidOrderCount());
        assertEquals(1990L, response.billing().paidAmountMinor());
        assertEquals(500L, response.billing().ledgerNetTokenCredits());
        assertEquals("DEGRADED", response.channelHealth().getFirst().status());
        assertTrue(response.byProvider().stream().anyMatch(item -> "OPENAI_DIRECT".equals(item.value()) && item.totalTokens() == 160));
        assertTrue(csv.contains("section,dimension,value"));
        assertTrue(csv.contains("provider,provider,OPENAI_DIRECT"));
    }

    private RequestLogEntity request(
            String requestId,
            ProviderType providerType,
            String modelGroup,
            GatewayRequestStatus status,
            Long durationMs,
            Instant createdAt,
            String errorCode) {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(requestId);
        entity.setDistributedKeyId(1L);
        entity.setDistributedKeyPrefix("sk-gw");
        entity.setProtocol("openai");
        entity.setRequestPath("/v1/chat/completions");
        entity.setRequestedModel(modelGroup);
        entity.setPublicModel(modelGroup);
        entity.setResolvedModelKey(modelGroup);
        entity.setModelGroup(modelGroup);
        entity.setProviderType(providerType);
        entity.setCredentialId(101L);
        entity.setSelectionSource("TEST");
        entity.setPrefixHash("prefix");
        entity.setFingerprint("fingerprint");
        entity.setStatus(status);
        entity.setDurationMs(durationMs);
        entity.setStartedAt(createdAt);
        entity.setErrorCode(errorCode);
        entity.setErrorMessage(errorCode == null ? null : "provider failed");
        ReflectionTestUtils.setField(entity, "createdAt", createdAt);
        return entity;
    }

    private UsageRecordEntity usage(
            String requestId,
            ProviderType providerType,
            String modelGroup,
            int promptTokens,
            int completionTokens,
            int reasoningTokens,
            int totalTokens,
            Instant createdAt) {
        UsageRecordEntity entity = new UsageRecordEntity();
        entity.setRequestId(requestId);
        entity.setDistributedKeyId(1L);
        entity.setProtocol("openai");
        entity.setRequestPath("/v1/chat/completions");
        entity.setModelGroup(modelGroup);
        entity.setProviderType(providerType);
        entity.setCredentialId(101L);
        entity.setCompleteness(GatewayUsageCompleteness.FINAL);
        entity.setUsageSource(GatewayUsageSource.PROVIDER_FINAL);
        entity.setPromptTokens(promptTokens);
        entity.setCompletionTokens(completionTokens);
        entity.setReasoningTokens(reasoningTokens);
        entity.setTotalTokens(totalTokens);
        ReflectionTestUtils.setField(entity, "createdAt", createdAt);
        return entity;
    }

    private PaymentOrderEntity order(String status, long amountMinor, String currency, long tokenCredits, Instant createdAt) {
        GatewayUserEntity user = new GatewayUserEntity();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setEmail("paid@example.com");
        PaymentOrderEntity entity = new PaymentOrderEntity();
        entity.setOrderNo("pay_1");
        entity.setUser(user);
        entity.setProvider("mock");
        entity.setAmountMinor(amountMinor);
        entity.setCurrency(currency);
        entity.setTokenCredits(tokenCredits);
        entity.setStatus(status);
        ReflectionTestUtils.setField(entity, "createdAt", createdAt);
        return entity;
    }

    private GatewayUserBalanceLedgerEntity ledger(long delta, long balanceAfter, Instant createdAt) {
        GatewayUserEntity user = new GatewayUserEntity();
        ReflectionTestUtils.setField(user, "id", 7L);
        GatewayUserBalanceLedgerEntity entity = new GatewayUserBalanceLedgerEntity();
        entity.setUser(user);
        entity.setDeltaTokenCredits(delta);
        entity.setBalanceAfterTokenCredits(balanceAfter);
        entity.setReason("PAYMENT_RECHARGE");
        ReflectionTestUtils.setField(entity, "createdAt", createdAt);
        return entity;
    }
}
