package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.MonitoringBillingBucketResponse;
import com.prodigalgal.xaigateway.admin.api.MonitoringBillingDimensionResponse;
import com.prodigalgal.xaigateway.admin.api.MonitoringBillingRollupResponse;
import com.prodigalgal.xaigateway.admin.api.MonitoringBillingSummaryResponse;
import com.prodigalgal.xaigateway.admin.api.MonitoringChannelHealthResponse;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestStatus;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserBalanceLedgerEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.PaymentOrderEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UsageRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserBalanceLedgerRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.PaymentOrderRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UsageRecordRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MonitoringBillingRollupService {

    private final RequestLogRepository requestLogRepository;
    private final UsageRecordRepository usageRecordRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final GatewayUserBalanceLedgerRepository ledgerRepository;

    public MonitoringBillingRollupService(
            RequestLogRepository requestLogRepository,
            UsageRecordRepository usageRecordRepository,
            PaymentOrderRepository paymentOrderRepository,
            GatewayUserBalanceLedgerRepository ledgerRepository) {
        this.requestLogRepository = requestLogRepository;
        this.usageRecordRepository = usageRecordRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.ledgerRepository = ledgerRepository;
    }

    public MonitoringBillingRollupResponse rollup(
            String period,
            Long distributedKeyId,
            ProviderType providerType,
            Instant from,
            Instant to) {
        RollupWindow window = resolveWindow(period, from, to);
        List<RequestLogEntity> requestLogs = requestLogRepository.searchWithinWindow(
                distributedKeyId,
                providerType,
                window.from(),
                window.to());
        List<UsageRecordEntity> usageRecords = usageRecordRepository.searchWithinWindow(
                distributedKeyId,
                providerType,
                window.from(),
                window.to());
        List<PaymentOrderEntity> orders = paymentOrderRepository.findAllByCreatedAtBetweenOrderByCreatedAtDesc(
                window.from(),
                window.to());
        List<GatewayUserBalanceLedgerEntity> ledgers = ledgerRepository.findAllByCreatedAtBetweenOrderByCreatedAtDesc(
                window.from(),
                window.to());

        long requestCount = requestLogs.size();
        long failedRequestCount = requestLogs.stream()
                .filter(entity -> entity.getStatus() == GatewayRequestStatus.FAILED)
                .count();
        long completedRequestCount = requestLogs.stream()
                .filter(entity -> entity.getStatus() == GatewayRequestStatus.COMPLETED)
                .count();
        TokenTotals totals = tokenTotals(usageRecords);

        return new MonitoringBillingRollupResponse(
                window.from(),
                window.to(),
                window.period(),
                requestCount,
                completedRequestCount,
                failedRequestCount,
                rate(failedRequestCount, requestCount),
                averageDuration(requestLogs),
                usageRecords.size(),
                totals.promptTokens,
                totals.completionTokens,
                totals.reasoningTokens,
                totals.totalTokens,
                totals.cacheHitTokens,
                totals.cacheWriteTokens,
                totals.savedInputTokens,
                billingSummary(orders, ledgers),
                buckets(window.period(), requestLogs, usageRecords),
                dimensions("provider", requestLogs, usageRecords, entity -> value(entity.getProviderType())),
                dimensions("model", requestLogs, usageRecords, RequestLogEntity::getModelGroup),
                dimensions("distributedKey", requestLogs, usageRecords, entity -> value(entity.getDistributedKeyId())),
                channelHealth(requestLogs)
        );
    }

    public String exportCsv(
            String period,
            Long distributedKeyId,
            ProviderType providerType,
            Instant from,
            Instant to) {
        MonitoringBillingRollupResponse rollup = rollup(period, distributedKeyId, providerType, from, to);
        List<String> lines = new ArrayList<>();
        lines.add("section,dimension,value,request_count,failed_request_count,usage_record_count,prompt_tokens,completion_tokens,reasoning_tokens,total_tokens,cache_hit_tokens,cache_write_tokens,saved_input_tokens,average_duration_ms");
        lines.add(row(
                "total",
                "all",
                "all",
                rollup.requestCount(),
                rollup.failedRequestCount(),
                rollup.usageRecordCount(),
                rollup.promptTokens(),
                rollup.completionTokens(),
                rollup.reasoningTokens(),
                rollup.totalTokens(),
                rollup.cacheHitTokens(),
                rollup.cacheWriteTokens(),
                rollup.savedInputTokens(),
                rollup.averageDurationMs()
        ));
        rollup.byProvider().forEach(item -> lines.add(row("provider", item)));
        rollup.byModel().forEach(item -> lines.add(row("model", item)));
        rollup.byDistributedKey().forEach(item -> lines.add(row("distributedKey", item)));
        return String.join("\n", lines) + "\n";
    }

    private MonitoringBillingSummaryResponse billingSummary(
            List<PaymentOrderEntity> orders,
            List<GatewayUserBalanceLedgerEntity> ledgers) {
        List<PaymentOrderEntity> paidOrders = orders.stream()
                .filter(order -> "PAID".equalsIgnoreCase(order.getStatus()))
                .toList();
        String currency = paidOrders.stream()
                .map(PaymentOrderEntity::getCurrency)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("MIXED");
        long credit = ledgers.stream()
                .mapToLong(GatewayUserBalanceLedgerEntity::getDeltaTokenCredits)
                .filter(value -> value > 0)
                .sum();
        long debit = ledgers.stream()
                .mapToLong(GatewayUserBalanceLedgerEntity::getDeltaTokenCredits)
                .filter(value -> value < 0)
                .map(Math::abs)
                .sum();
        long endingBalance = ledgers.stream()
                .filter(entity -> entity.getCreatedAt() != null)
                .max(Comparator.comparing(GatewayUserBalanceLedgerEntity::getCreatedAt))
                .map(GatewayUserBalanceLedgerEntity::getBalanceAfterTokenCredits)
                .orElse(0L);
        return new MonitoringBillingSummaryResponse(
                paidOrders.size(),
                paidOrders.stream().mapToLong(PaymentOrderEntity::getAmountMinor).sum(),
                currency,
                paidOrders.stream().mapToLong(PaymentOrderEntity::getTokenCredits).sum(),
                credit,
                debit,
                credit - debit,
                endingBalance
        );
    }

    private List<MonitoringBillingBucketResponse> buckets(
            String period,
            List<RequestLogEntity> requestLogs,
            List<UsageRecordEntity> usageRecords) {
        LinkedHashMap<String, MutableBucket> buckets = new LinkedHashMap<>();
        requestLogs.stream()
                .sorted(Comparator.comparing(RequestLogEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(entity -> bucket(buckets, bucketLabel(period, entity.getCreatedAt())).addRequest(entity));
        usageRecords.stream()
                .sorted(Comparator.comparing(UsageRecordEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(entity -> bucket(buckets, bucketLabel(period, entity.getCreatedAt())).addUsage(entity));
        return buckets.entrySet().stream()
                .map(entry -> entry.getValue().toResponse(entry.getKey()))
                .toList();
    }

    private List<MonitoringBillingDimensionResponse> dimensions(
            String dimension,
            List<RequestLogEntity> requestLogs,
            List<UsageRecordEntity> usageRecords,
            RequestValueExtractor extractor) {
        LinkedHashMap<String, MutableDimension> dimensions = new LinkedHashMap<>();
        requestLogs.forEach(entity -> dimension(dimensions, normalizeValue(extractor.extract(entity))).addRequest(entity));
        usageRecords.forEach(entity -> {
            String value = switch (dimension) {
                case "provider" -> value(entity.getProviderType());
                case "model" -> entity.getModelGroup();
                case "distributedKey" -> value(entity.getDistributedKeyId());
                default -> "UNKNOWN";
            };
            dimension(dimensions, normalizeValue(value)).addUsage(entity);
        });
        return dimensions.entrySet().stream()
                .map(entry -> entry.getValue().toResponse(dimension, entry.getKey()))
                .toList();
    }

    private List<MonitoringChannelHealthResponse> channelHealth(List<RequestLogEntity> requestLogs) {
        LinkedHashMap<String, List<RequestLogEntity>> byProvider = new LinkedHashMap<>();
        requestLogs.forEach(entity -> byProvider.computeIfAbsent(value(entity.getProviderType()), ignored -> new ArrayList<>()).add(entity));
        return byProvider.entrySet().stream()
                .map(entry -> {
                    List<RequestLogEntity> logs = entry.getValue();
                    long failed = logs.stream().filter(log -> log.getStatus() == GatewayRequestStatus.FAILED).count();
                    double failureRate = rate(failed, logs.size());
                    RequestLogEntity lastFailed = logs.stream()
                            .filter(log -> log.getStatus() == GatewayRequestStatus.FAILED)
                            .max(Comparator.comparing(RequestLogEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                            .orElse(null);
                    String status = logs.isEmpty() ? "NO_DATA" : failureRate >= 0.2d ? "DEGRADED" : "HEALTHY";
                    return new MonitoringChannelHealthResponse(
                            entry.getKey(),
                            logs.size(),
                            failed,
                            failureRate,
                            averageDuration(logs),
                            status,
                            lastFailed == null ? null : lastFailed.getErrorCode(),
                            lastFailed == null ? null : lastFailed.getErrorMessage()
                    );
                })
                .toList();
    }

    private RollupWindow resolveWindow(String period, Instant from, Instant to) {
        String normalizedPeriod = normalizePeriod(period);
        Instant resolvedTo = to == null ? Instant.now() : to;
        Instant resolvedFrom = from;
        if (resolvedFrom == null) {
            resolvedFrom = switch (normalizedPeriod) {
                case "week" -> resolvedTo.minus(Duration.ofDays(7));
                case "month" -> resolvedTo.minus(Duration.ofDays(30));
                default -> resolvedTo.minus(Duration.ofDays(1));
            };
        }
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new IllegalArgumentException("from 不能晚于 to。");
        }
        return new RollupWindow(normalizedPeriod, resolvedFrom, resolvedTo);
    }

    private String normalizePeriod(String period) {
        if (period == null || period.isBlank()) {
            return "day";
        }
        return switch (period.trim().toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "daily", "day" -> "day";
            case "weekly", "week" -> "week";
            case "monthly", "month" -> "month";
            default -> throw new IllegalArgumentException("不支持的 rollup period。");
        };
    }

    private String bucketLabel(String period, Instant value) {
        if (value == null) {
            return "UNKNOWN";
        }
        LocalDate date = value.atZone(ZoneOffset.UTC).toLocalDate();
        return switch (period) {
            case "week" -> date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString();
            case "month" -> YearMonth.from(date).toString();
            default -> date.toString();
        };
    }

    private long averageDuration(List<RequestLogEntity> requestLogs) {
        return Math.round(requestLogs.stream()
                .map(RequestLogEntity::getDurationMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0d));
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0d;
        }
        return Math.round((numerator * 10000d / denominator)) / 10000d;
    }

    private TokenTotals tokenTotals(List<UsageRecordEntity> usageRecords) {
        TokenTotals totals = new TokenTotals();
        usageRecords.forEach(totals::add);
        return totals;
    }

    private MutableBucket bucket(Map<String, MutableBucket> buckets, String key) {
        return buckets.computeIfAbsent(key, ignored -> new MutableBucket());
    }

    private MutableDimension dimension(Map<String, MutableDimension> dimensions, String key) {
        return dimensions.computeIfAbsent(key, ignored -> new MutableDimension());
    }

    private String value(Object value) {
        return value == null ? "UNKNOWN" : String.valueOf(value);
    }

    private String normalizeValue(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.trim();
    }

    private String row(String section, MonitoringBillingDimensionResponse item) {
        return row(
                section,
                item.dimension(),
                item.value(),
                item.requestCount(),
                item.failedRequestCount(),
                item.usageRecordCount(),
                item.promptTokens(),
                item.completionTokens(),
                item.reasoningTokens(),
                item.totalTokens(),
                item.cacheHitTokens(),
                item.cacheWriteTokens(),
                item.savedInputTokens(),
                item.averageDurationMs()
        );
    }

    private String row(
            String section,
            String dimension,
            String value,
            long requestCount,
            long failedRequestCount,
            long usageRecordCount,
            long promptTokens,
            long completionTokens,
            long reasoningTokens,
            long totalTokens,
            long cacheHitTokens,
            long cacheWriteTokens,
            long savedInputTokens,
            long averageDurationMs) {
        return String.join(",",
                csv(section),
                csv(dimension),
                csv(value),
                String.valueOf(requestCount),
                String.valueOf(failedRequestCount),
                String.valueOf(usageRecordCount),
                String.valueOf(promptTokens),
                String.valueOf(completionTokens),
                String.valueOf(reasoningTokens),
                String.valueOf(totalTokens),
                String.valueOf(cacheHitTokens),
                String.valueOf(cacheWriteTokens),
                String.valueOf(savedInputTokens),
                String.valueOf(averageDurationMs));
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private interface RequestValueExtractor {
        String extract(RequestLogEntity entity);
    }

    private record RollupWindow(String period, Instant from, Instant to) {
    }

    private static class TokenTotals {
        long promptTokens;
        long completionTokens;
        long reasoningTokens;
        long totalTokens;
        long cacheHitTokens;
        long cacheWriteTokens;
        long savedInputTokens;

        void add(UsageRecordEntity entity) {
            promptTokens += entity.getPromptTokens();
            completionTokens += entity.getCompletionTokens();
            reasoningTokens += entity.getReasoningTokens();
            totalTokens += entity.getTotalTokens();
            cacheHitTokens += entity.getCacheHitTokens() + entity.getUpstreamCacheHitTokens();
            cacheWriteTokens += entity.getCacheWriteTokens() + entity.getUpstreamCacheWriteTokens();
            savedInputTokens += entity.getSavedInputTokens();
        }
    }

    private static class MutableBucket extends TokenTotals {
        long requestCount;
        long failedRequestCount;
        long usageRecordCount;

        void addRequest(RequestLogEntity entity) {
            requestCount++;
            if (entity.getStatus() == GatewayRequestStatus.FAILED) {
                failedRequestCount++;
            }
        }

        void addUsage(UsageRecordEntity entity) {
            usageRecordCount++;
            add(entity);
        }

        MonitoringBillingBucketResponse toResponse(String bucket) {
            return new MonitoringBillingBucketResponse(
                    bucket,
                    requestCount,
                    failedRequestCount,
                    usageRecordCount,
                    promptTokens,
                    completionTokens,
                    reasoningTokens,
                    totalTokens,
                    cacheHitTokens,
                    cacheWriteTokens,
                    savedInputTokens
            );
        }
    }

    private class MutableDimension extends TokenTotals {
        long requestCount;
        long failedRequestCount;
        long usageRecordCount;
        final List<RequestLogEntity> requests = new ArrayList<>();

        void addRequest(RequestLogEntity entity) {
            requestCount++;
            requests.add(entity);
            if (entity.getStatus() == GatewayRequestStatus.FAILED) {
                failedRequestCount++;
            }
        }

        void addUsage(UsageRecordEntity entity) {
            usageRecordCount++;
            add(entity);
        }

        MonitoringBillingDimensionResponse toResponse(String dimension, String value) {
            return new MonitoringBillingDimensionResponse(
                    dimension,
                    value,
                    requestCount,
                    failedRequestCount,
                    usageRecordCount,
                    promptTokens,
                    completionTokens,
                    reasoningTokens,
                    totalTokens,
                    cacheHitTokens,
                    cacheWriteTokens,
                    savedInputTokens,
                    averageDuration(requests)
            );
        }
    }
}
