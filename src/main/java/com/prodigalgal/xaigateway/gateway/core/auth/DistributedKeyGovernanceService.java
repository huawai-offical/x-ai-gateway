package com.prodigalgal.xaigateway.gateway.core.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class DistributedKeyGovernanceService {

    private final RateLimitStore rateLimitStore;
    private final ObjectMapper objectMapper;

    public DistributedKeyGovernanceService(
            RateLimitStore rateLimitStore,
            ObjectMapper objectMapper) {
        this.rateLimitStore = rateLimitStore;
        this.objectMapper = objectMapper;
    }

    public GovernanceDecision evaluate(DistributedKeyView distributedKey, GatewayClientFamily clientFamily, Object requestBody, boolean reserveConcurrency) {
        List<String> blockers = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        long estimatedTokens = estimateTokens(requestBody);
        long estimatedBudgetMicros = estimateBudgetMicros(estimatedTokens);

        if (distributedKey.expiresAt() != null && distributedKey.expiresAt().isBefore(java.time.Instant.now())) {
            blockers.add("当前 DistributedKey 已过期。");
        }

        if (distributedKey.requireClientFamilyMatch()
                && !distributedKey.allowedClientFamilies().isEmpty()
                && !distributedKey.allowedClientFamilies().contains(clientFamily.name())) {
            blockers.add("当前 DistributedKey 不允许客户端家族 " + clientFamily.name() + "。");
        }

        if (distributedKey.budgetLimitMicros() != null && distributedKey.budgetWindowSeconds() != null) {
            long current = incrementWithinWindow(budgetKey(distributedKey.id()), distributedKey.budgetWindowSeconds(), reserveConcurrency ? estimatedBudgetMicros : 0L);
            if (reserveConcurrency && current > distributedKey.budgetLimitMicros()) {
                blockers.add("当前 DistributedKey 已超过预算窗口限制。");
            }
            notes.add("estimated_budget_micros=" + estimatedBudgetMicros);
        }

        if (distributedKey.rpmLimit() != null) {
            long current = incrementWithinWindow(rpmKey(distributedKey.id()), 60, reserveConcurrency ? 1L : 0L);
            if (reserveConcurrency && current > distributedKey.rpmLimit()) {
                blockers.add("当前 DistributedKey 已超过 RPM 限制。");
            }
        }

        if (distributedKey.tpmLimit() != null) {
            long current = incrementWithinWindow(tpmKey(distributedKey.id()), 60, reserveConcurrency ? estimatedTokens : 0L);
            if (reserveConcurrency && current > distributedKey.tpmLimit()) {
                blockers.add("当前 DistributedKey 已超过 TPM 限制。");
            }
            notes.add("estimated_tokens=" + estimatedTokens);
        }

        String concurrencyReservationKey = null;
        if (distributedKey.concurrencyLimit() != null) {
            if (reserveConcurrency) {
                long current = incrementWithinWindow(concurrencyKey(distributedKey.id()), 300, 1L);
                concurrencyReservationKey = concurrencyKey(distributedKey.id());
                if (current > distributedKey.concurrencyLimit()) {
                    blockers.add("当前 DistributedKey 已超过并发限制。");
                }
            } else {
                long current = rateLimitStore.get(concurrencyKey(distributedKey.id()));
                if (current >= distributedKey.concurrencyLimit()) {
                    blockers.add("当前 DistributedKey 并发已满。");
                }
            }
        }

        return new GovernanceDecision(blockers, notes, estimatedTokens, estimatedBudgetMicros, concurrencyReservationKey);
    }

    public void releaseConcurrency(String reservationKey) {
        if (reservationKey == null) {
            return;
        }
        rateLimitStore.decrement(reservationKey);
    }

    private long estimateTokens(Object requestBody) {
        try {
            String payload = requestBody == null ? "" : objectMapper.writeValueAsString(requestBody);
            return Math.max(1L, payload.length() / 4L);
        } catch (Exception exception) {
            return 1L;
        }
    }

    private long estimateBudgetMicros(long estimatedTokens) {
        return Math.max(1_000L, estimatedTokens * 1_000L);
    }

    private long incrementWithinWindow(String key, int windowSeconds, long amount) {
        if (amount <= 0) {
            return rateLimitStore.get(key);
        }
        return rateLimitStore.increment(key, amount, Duration.ofSeconds(windowSeconds));
    }

    private String budgetKey(Long distributedKeyId) {
        return "xag:governance:budget:" + distributedKeyId;
    }

    private String rpmKey(Long distributedKeyId) {
        return "xag:governance:rpm:" + distributedKeyId;
    }

    private String tpmKey(Long distributedKeyId) {
        return "xag:governance:tpm:" + distributedKeyId;
    }

    private String concurrencyKey(Long distributedKeyId) {
        return "xag:governance:concurrency:" + distributedKeyId;
    }

    public record GovernanceDecision(
            List<String> blockers,
            List<String> notes,
            long estimatedTokens,
            long estimatedBudgetMicros,
            String concurrencyReservationKey
    ) {
    }
}
