package com.prodigalgal.xaigateway.gateway.core.auth;

import tools.jackson.databind.ObjectMapper;
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

    public GovernanceWindowSnapshot snapshot(DistributedKeyView distributedKey) {
        long currentBudgetMicros = currentValue(distributedKey.budgetLimitMicros() == null ? null : budgetKey(distributedKey.id()));
        long currentRpm = currentValue(distributedKey.rpmLimit() == null ? null : rpmKey(distributedKey.id()));
        long currentTpm = currentValue(distributedKey.tpmLimit() == null ? null : tpmKey(distributedKey.id()));
        long currentConcurrency = currentValue(distributedKey.concurrencyLimit() == null ? null : concurrencyKey(distributedKey.id()));

        double budgetUtilization = utilization(currentBudgetMicros, distributedKey.budgetLimitMicros());
        double rpmUtilization = utilization(currentRpm, distributedKey.rpmLimit() == null ? null : distributedKey.rpmLimit().longValue());
        double tpmUtilization = utilization(currentTpm, distributedKey.tpmLimit() == null ? null : distributedKey.tpmLimit().longValue());
        double concurrencyUtilization = utilization(currentConcurrency, distributedKey.concurrencyLimit() == null ? null : distributedKey.concurrencyLimit().longValue());

        List<String> notes = new ArrayList<>();
        if (budgetUtilization >= 0.85D) {
            notes.add("budget usage is close to the current window limit");
        }
        if (rpmUtilization >= 0.85D) {
            notes.add("rpm usage is close to the current window limit");
        }
        if (tpmUtilization >= 0.85D) {
            notes.add("tpm usage is close to the current window limit");
        }
        if (concurrencyUtilization >= 0.85D) {
            notes.add("concurrency usage is close to the current window limit");
        }

        return new GovernanceWindowSnapshot(
                currentBudgetMicros,
                currentRpm,
                currentTpm,
                currentConcurrency,
                budgetUtilization,
                rpmUtilization,
                tpmUtilization,
                concurrencyUtilization,
                pressureLevel(budgetUtilization, rpmUtilization, tpmUtilization, concurrencyUtilization),
                List.copyOf(notes)
        );
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

    private long currentValue(String key) {
        if (key == null) {
            return 0L;
        }
        return rateLimitStore.get(key);
    }

    private double utilization(long current, Long limit) {
        if (limit == null || limit <= 0L) {
            return 0D;
        }
        return (double) current / limit;
    }

    private String pressureLevel(double budgetUtilization, double rpmUtilization, double tpmUtilization, double concurrencyUtilization) {
        double max = Math.max(Math.max(budgetUtilization, rpmUtilization), Math.max(tpmUtilization, concurrencyUtilization));
        if (max >= 1D) {
            return "CRITICAL";
        }
        if (max >= 0.85D) {
            return "HIGH";
        }
        if (max >= 0.60D) {
            return "MEDIUM";
        }
        if (max > 0D) {
            return "LOW";
        }
        return "HEALTHY";
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

    public record GovernanceWindowSnapshot(
            long currentBudgetMicros,
            long currentRpm,
            long currentTpm,
            long currentConcurrency,
            double budgetUtilization,
            double rpmUtilization,
            double tpmUtilization,
            double concurrencyUtilization,
            String pressureLevel,
            List<String> notes
    ) {
    }
}
