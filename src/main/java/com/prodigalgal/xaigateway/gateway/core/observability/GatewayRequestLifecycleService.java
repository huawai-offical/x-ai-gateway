package com.prodigalgal.xaigateway.gateway.core.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageCompleteness;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UsageRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UsageRecordRepository;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GatewayRequestLifecycleService {

    private final RequestLogRepository requestLogRepository;
    private final UsageRecordRepository usageRecordRepository;
    private final GatewayAuditLogService gatewayAuditLogService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public GatewayRequestLifecycleService(
            RequestLogRepository requestLogRepository,
            UsageRecordRepository usageRecordRepository,
            GatewayAuditLogService gatewayAuditLogService,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {
        this.requestLogRepository = requestLogRepository;
        this.usageRecordRepository = usageRecordRepository;
        this.gatewayAuditLogService = gatewayAuditLogService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    public void startRequest(
            String requestId,
            RouteSelectionResult selectionResult,
            ChatExecutionRequest request,
            boolean stream,
            Instant startedAt) {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(requestId);
        entity.setDistributedKeyId(selectionResult.distributedKeyId());
        entity.setDistributedKeyPrefix(selectionResult.distributedKeyPrefix());
        entity.setProtocol(selectionResult.protocol());
        entity.setRequestPath(request.requestPath());
        entity.setRequestedModel(selectionResult.requestedModel());
        entity.setPublicModel(selectionResult.publicModel());
        entity.setResolvedModelKey(selectionResult.resolvedModelKey());
        entity.setModelGroup(selectionResult.modelGroup());
        entity.setProviderType(selectionResult.selectedCandidate().candidate().providerType());
        entity.setCredentialId(selectionResult.selectedCandidate().candidate().credentialId());
        entity.setSelectionSource(selectionResult.selectionSource().name());
        entity.setPrefixHash(selectionResult.prefixHash());
        entity.setFingerprint(selectionResult.fingerprint());
        entity.setStream(stream);
        entity.setStatus(GatewayRequestStatus.IN_PROGRESS);
        entity.setStartedAt(startedAt);
        requestLogRepository.save(entity);
    }

    public void completeRequest(
            String requestId,
            RouteSelectionResult selectionResult,
            ChatExecutionRequest request,
            boolean stream,
            GatewayUsageView usage,
            Instant startedAt) {
        finishRequest(requestId, selectionResult, request, stream, GatewayRequestStatus.COMPLETED, null, null, usage, startedAt);
    }

    public void failRequest(
            String requestId,
            RouteSelectionResult selectionResult,
            ChatExecutionRequest request,
            boolean stream,
            Throwable error,
            GatewayUsageView usage,
            Instant startedAt) {
        finishRequest(
                requestId,
                selectionResult,
                request,
                stream,
                GatewayRequestStatus.FAILED,
                error == null ? null : error.getClass().getSimpleName(),
                error == null ? null : error.getMessage(),
                usage,
                startedAt
        );
        gatewayAuditLogService.recordGatewayEvent(
                requestId,
                "REQUEST_FAILED",
                GatewayRequestStatus.FAILED.name(),
                Map.of(
                        "protocol", selectionResult.protocol(),
                        "requestPath", request.requestPath(),
                        "providerType", selectionResult.selectedCandidate().candidate().providerType().name(),
                        "errorType", error == null ? null : error.getClass().getSimpleName()
                )
        );
    }

    public void cancelRequest(
            String requestId,
            RouteSelectionResult selectionResult,
            ChatExecutionRequest request,
            boolean stream,
            GatewayUsageView usage,
            Instant startedAt) {
        finishRequest(
                requestId,
                selectionResult,
                request,
                stream,
                GatewayRequestStatus.CANCELED,
                "CLIENT_CANCELLED",
                "Request stream cancelled by client",
                usage,
                startedAt
        );
        gatewayAuditLogService.recordGatewayEvent(
                requestId,
                "REQUEST_CANCELLED",
                GatewayRequestStatus.CANCELED.name(),
                Map.of(
                        "protocol", selectionResult.protocol(),
                        "requestPath", request.requestPath(),
                        "providerType", selectionResult.selectedCandidate().candidate().providerType().name()
                )
        );
    }

    private void finishRequest(
            String requestId,
            RouteSelectionResult selectionResult,
            ChatExecutionRequest request,
            boolean stream,
            GatewayRequestStatus status,
            String errorCode,
            String errorMessage,
            GatewayUsageView usage,
            Instant startedAt) {
        Instant completedAt = Instant.now();
        long durationMs = Duration.between(startedAt, completedAt).toMillis();

        requestLogRepository.findByRequestId(requestId).ifPresent(entity -> {
            entity.setProviderType(selectionResult.selectedCandidate().candidate().providerType());
            entity.setCredentialId(selectionResult.selectedCandidate().candidate().credentialId());
            entity.setSelectionSource(selectionResult.selectionSource().name());
            entity.setStatus(status);
            entity.setErrorCode(errorCode);
            entity.setErrorMessage(truncate(errorMessage));
            entity.setCompletedAt(completedAt);
            entity.setDurationMs(durationMs);
            requestLogRepository.save(entity);
        });

        saveUsageRecord(requestId, selectionResult, request, stream, usage);
        recordMetrics(selectionResult, request.requestPath(), stream, status, usage, durationMs);
    }

    private void saveUsageRecord(
            String requestId,
            RouteSelectionResult selectionResult,
            ChatExecutionRequest request,
            boolean stream,
            GatewayUsageView usage) {
        if (usage == null || !usage.present()) {
            return;
        }

        UsageRecordEntity entity = usageRecordRepository.findByRequestId(requestId).orElseGet(UsageRecordEntity::new);
        entity.setRequestId(requestId);
        entity.setDistributedKeyId(selectionResult.distributedKeyId());
        entity.setProtocol(selectionResult.protocol());
        entity.setRequestPath(request.requestPath());
        entity.setModelGroup(selectionResult.modelGroup());
        entity.setProviderType(selectionResult.selectedCandidate().candidate().providerType());
        entity.setCredentialId(selectionResult.selectedCandidate().candidate().credentialId());
        entity.setStream(stream);
        entity.setCompleteness(usage.completeness());
        entity.setUsageSource(usage.source());
        entity.setRawPromptTokens(usage.rawPromptTokens());
        entity.setPromptTokens(usage.promptTokens());
        entity.setCompletionTokens(usage.completionTokens());
        entity.setReasoningTokens(usage.reasoningTokens());
        entity.setCacheHitTokens(usage.cacheHitTokens());
        entity.setCacheWriteTokens(usage.cacheWriteTokens());
        entity.setUpstreamCacheHitTokens(usage.upstreamCacheHitTokens());
        entity.setUpstreamCacheWriteTokens(usage.upstreamCacheWriteTokens());
        entity.setSavedInputTokens(usage.savedInputTokens());
        entity.setCachedContentRef(usage.cachedContentRef());
        entity.setTotalTokens(usage.totalTokens());
        entity.setNativeUsagePayloadJson(toJson(usage.nativeUsagePayload()));
        usageRecordRepository.save(entity);
    }

    private void recordMetrics(
            RouteSelectionResult selectionResult,
            String requestPath,
            boolean stream,
            GatewayRequestStatus status,
            GatewayUsageView usage,
            long durationMs) {
        Tags tags = Tags.of(
                "protocol", selectionResult.protocol(),
                "request_path", requestPath,
                "provider_type", selectionResult.selectedCandidate().candidate().providerType().name(),
                "model_group", selectionResult.modelGroup(),
                "selection_source", selectionResult.selectionSource().name(),
                "stream", Boolean.toString(stream),
                "status", status.name(),
                "cache_kind", cacheKind(usage),
                "usage_completeness", usage == null ? GatewayUsageCompleteness.NONE.name() : usage.completeness().name()
        );

        meterRegistry.counter("gateway.request.total", tags).increment();
        Timer.builder("gateway.request.duration")
                .tags(tags)
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));

        if (usage == null || !usage.present()) {
            return;
        }

        DistributionSummary.builder("gateway.usage.total_tokens")
                .tags(tags)
                .register(meterRegistry)
                .record(usage.totalTokens());
        DistributionSummary.builder("gateway.usage.saved_input_tokens")
                .tags(tags)
                .register(meterRegistry)
                .record(usage.savedInputTokens());
        DistributionSummary.builder("gateway.cache.hit_tokens")
                .tags(tags)
                .register(meterRegistry)
                .record(usage.cacheHitTokens());
        DistributionSummary.builder("gateway.cache.write_tokens")
                .tags(tags)
                .register(meterRegistry)
                .record(usage.cacheWriteTokens());
    }

    private String cacheKind(GatewayUsageView usage) {
        if (usage == null || !usage.present()) {
            return "none";
        }
        if (usage.cachedContentRef() != null && !usage.cachedContentRef().isBlank()) {
            return "cached_content";
        }
        if (usage.cacheHitTokens() > 0 || usage.cacheWriteTokens() > 0) {
            return "prompt_cache";
        }
        return "none";
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1024 ? message : message.substring(0, 1024);
    }
}
