package com.prodigalgal.xaigateway.gateway.core.observability;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceResponse;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
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
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final GatewayObservabilityAsyncPersistenceService asyncPersistenceService;

    @Autowired
    public GatewayRequestLifecycleService(
            RequestLogRepository requestLogRepository,
            UsageRecordRepository usageRecordRepository,
            GatewayAuditLogService gatewayAuditLogService,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper,
            GatewayObservabilityAsyncPersistenceService asyncPersistenceService) {
        this.requestLogRepository = requestLogRepository;
        this.usageRecordRepository = usageRecordRepository;
        this.gatewayAuditLogService = gatewayAuditLogService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.asyncPersistenceService = asyncPersistenceService;
    }

    public GatewayRequestLifecycleService(
            RequestLogRepository requestLogRepository,
            UsageRecordRepository usageRecordRepository,
            GatewayAuditLogService gatewayAuditLogService,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {
        this(requestLogRepository, usageRecordRepository, gatewayAuditLogService, meterRegistry, objectMapper, null);
    }

    public void startRequest(
            String requestId,
            RouteSelectionResult selectionResult,
            CanonicalRequest request,
            boolean stream,
            Instant startedAt) {
        startRequest(requestId, selectionResult, request.requestPath(), null, null, null, null, null, null, null, stream, startedAt);
    }

    public void startRequest(
            String requestId,
            RouteSelectionResult selectionResult,
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            boolean stream,
            Instant startedAt) {
        startRequest(
                requestId,
                selectionResult,
                request.requestPath(),
                plan == null || plan.resourceType() == null ? null : plan.resourceType().wireName(),
                plan == null || plan.operation() == null ? null : plan.operation().wireName(),
                plan == null ? null : plan.executionBackend() == null ? null : plan.executionBackend().wireName(),
                plan == null ? null : plan.supportStatus() == null ? null : plan.supportStatus().name(),
                plan == null ? null : plan.degradationLevel() == null ? null : plan.degradationLevel().name(),
                plan == null ? null : plan.objectMode(),
                gatewayResourceKey(request, plan, null),
                stream,
                startedAt
        );
    }

    public void startRequest(
            String requestId,
            Long distributedKeyId,
            String distributedKeyPrefix,
            String protocol,
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            boolean stream,
            Instant startedAt) {
        GatewayObservabilityAsyncPersistenceService.RequestLogSnapshot snapshot =
                new GatewayObservabilityAsyncPersistenceService.RequestLogSnapshot(
                        requestId,
                        distributedKeyId,
                        distributedKeyPrefix,
                        protocol,
                        request.requestPath(),
                        plan == null || plan.resourceType() == null ? null : plan.resourceType().wireName(),
                        plan == null || plan.operation() == null ? null : plan.operation().wireName(),
                        request.requestedModel(),
                        request.requestedModel(),
                        request.requestedModel(),
                        request.requestedModel(),
                        null,
                        null,
                        "NO_ROUTE",
                        plan == null || plan.executionBackend() == null ? null : plan.executionBackend().wireName(),
                        plan == null || plan.supportStatus() == null ? null : plan.supportStatus().name(),
                        plan == null || plan.degradationLevel() == null ? null : plan.degradationLevel().name(),
                        plan == null ? null : plan.objectMode(),
                        gatewayResourceKey(request, plan, null),
                        null,
                        null,
                        null,
                        null,
                        null,
                        request.requestPath(),
                        distributedKeyPrefix == null ? null : distributedKeyPrefix.toLowerCase(Locale.ROOT),
                        stream,
                        GatewayRequestStatus.IN_PROGRESS,
                        null,
                        null,
                        null,
                        startedAt,
                        null
                );
        enqueueOrPersistRequestLogStart(snapshot);
    }

    private void startRequest(
            String requestId,
            RouteSelectionResult selectionResult,
            String requestPath,
            String resourceType,
            String operation,
            String executionBackend,
            String supportStatus,
            String degradationLevel,
            String objectMode,
            String gatewayResourceKey,
            boolean stream,
            Instant startedAt) {
        GatewayObservabilityAsyncPersistenceService.RequestLogSnapshot snapshot =
                new GatewayObservabilityAsyncPersistenceService.RequestLogSnapshot(
                        requestId,
                        selectionResult.distributedKeyId(),
                        selectionResult.distributedKeyPrefix(),
                        selectionResult.protocol(),
                        requestPath,
                        resourceType,
                        operation,
                        selectionResult.requestedModel(),
                        selectionResult.publicModel(),
                        selectionResult.resolvedModelKey(),
                        selectionResult.modelGroup(),
                        selectionResult.selectedCandidate().candidate().providerType(),
                        selectionResult.selectedCandidate().candidate().credentialId(),
                        selectionResult.selectionSource().name(),
                        executionBackend,
                        supportStatus,
                        degradationLevel,
                        objectMode,
                        gatewayResourceKey,
                        null,
                        null,
                        null,
                        null,
                        null,
                        selectionResult.prefixHash(),
                        selectionResult.fingerprint(),
                        stream,
                        GatewayRequestStatus.IN_PROGRESS,
                        null,
                        null,
                        null,
                        startedAt,
                        null
                );
        enqueueOrPersistRequestLogStart(snapshot);
    }

    public void completeRequest(
            String requestId,
            RouteSelectionResult selectionResult,
            CanonicalRequest request,
            boolean stream,
            GatewayUsageView usage,
            Instant startedAt) {
        finishRequest(
                requestId,
                selectionResult,
                request.requestPath(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                stream,
                GatewayRequestStatus.COMPLETED,
                null,
                null,
                usage,
                null,
                startedAt
        );
    }

    public void completeRequest(
            String requestId,
            RouteSelectionResult selectionResult,
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            boolean stream,
            GatewayUsageView usage,
            Instant startedAt) {
        completeRequest(requestId, selectionResult, request, plan, stream, usage, null, startedAt);
    }

    public void completeRequest(
            String requestId,
            RouteSelectionResult selectionResult,
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            boolean stream,
            GatewayUsageView usage,
            CanonicalResourceResponse canonicalResponse,
            Instant startedAt) {
        finishRequest(
                requestId,
                selectionResult,
                request.requestPath(),
                plan == null || plan.resourceType() == null ? null : plan.resourceType().wireName(),
                plan == null || plan.operation() == null ? null : plan.operation().wireName(),
                plan == null || plan.executionBackend() == null ? null : plan.executionBackend().wireName(),
                plan == null || plan.supportStatus() == null ? null : plan.supportStatus().name(),
                plan == null || plan.degradationLevel() == null ? null : plan.degradationLevel().name(),
                plan == null ? null : plan.objectMode(),
                gatewayResourceKey(request, plan, canonicalResponse),
                stream,
                GatewayRequestStatus.COMPLETED,
                null,
                null,
                usage,
                canonicalResponse,
                startedAt
        );
    }

    public void completeRequest(
            String requestId,
            Long distributedKeyId,
            String distributedKeyPrefix,
            String protocol,
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            boolean stream,
            GatewayUsageView usage,
            CanonicalResourceResponse canonicalResponse,
            Instant startedAt) {
        finishRequestWithoutSelection(
                requestId,
                distributedKeyId,
                distributedKeyPrefix,
                protocol,
                request.requestPath(),
                request.requestedModel(),
                plan == null || plan.resourceType() == null ? null : plan.resourceType().wireName(),
                plan == null || plan.operation() == null ? null : plan.operation().wireName(),
                plan == null || plan.executionBackend() == null ? null : plan.executionBackend().wireName(),
                plan == null || plan.supportStatus() == null ? null : plan.supportStatus().name(),
                plan == null || plan.degradationLevel() == null ? null : plan.degradationLevel().name(),
                plan == null ? null : plan.objectMode(),
                gatewayResourceKey(request, plan, canonicalResponse),
                stream,
                GatewayRequestStatus.COMPLETED,
                null,
                null,
                canonicalResponse,
                startedAt
        );
    }

    public void failRequest(
            String requestId,
            RouteSelectionResult selectionResult,
            CanonicalRequest request,
            boolean stream,
            Throwable error,
            GatewayUsageView usage,
            Instant startedAt) {
        finishRequest(requestId, selectionResult, request.requestPath(), null, null, null, null, null, null, null, stream, GatewayRequestStatus.FAILED,
                error == null ? null : error.getClass().getSimpleName(),
                error == null ? null : error.getMessage(),
                usage,
                null,
                startedAt);
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

    public void failRequest(
            String requestId,
            RouteSelectionResult selectionResult,
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            boolean stream,
            Throwable error,
            GatewayUsageView usage,
            Instant startedAt) {
        finishRequest(
                requestId,
                selectionResult,
                request.requestPath(),
                plan == null || plan.resourceType() == null ? null : plan.resourceType().wireName(),
                plan == null || plan.operation() == null ? null : plan.operation().wireName(),
                plan == null || plan.executionBackend() == null ? null : plan.executionBackend().wireName(),
                plan == null || plan.supportStatus() == null ? null : plan.supportStatus().name(),
                plan == null || plan.degradationLevel() == null ? null : plan.degradationLevel().name(),
                plan == null ? null : plan.objectMode(),
                gatewayResourceKey(request, plan, null),
                stream,
                GatewayRequestStatus.FAILED,
                error == null ? null : error.getClass().getSimpleName(),
                error == null ? null : error.getMessage(),
                usage,
                null,
                startedAt
        );
    }

    public void failRequest(
            String requestId,
            Long distributedKeyId,
            String distributedKeyPrefix,
            String protocol,
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            boolean stream,
            Throwable error,
            Instant startedAt) {
        finishRequestWithoutSelection(
                requestId,
                distributedKeyId,
                distributedKeyPrefix,
                protocol,
                request.requestPath(),
                request.requestedModel(),
                plan == null || plan.resourceType() == null ? null : plan.resourceType().wireName(),
                plan == null || plan.operation() == null ? null : plan.operation().wireName(),
                plan == null || plan.executionBackend() == null ? null : plan.executionBackend().wireName(),
                plan == null || plan.supportStatus() == null ? null : plan.supportStatus().name(),
                plan == null || plan.degradationLevel() == null ? null : plan.degradationLevel().name(),
                plan == null ? null : plan.objectMode(),
                gatewayResourceKey(request, plan, null),
                stream,
                GatewayRequestStatus.FAILED,
                error == null ? null : error.getClass().getSimpleName(),
                error == null ? null : error.getMessage(),
                null,
                startedAt
        );
    }

    public void cancelRequest(
            String requestId,
            RouteSelectionResult selectionResult,
            CanonicalRequest request,
            boolean stream,
            GatewayUsageView usage,
            Instant startedAt) {
        finishRequest(requestId, selectionResult, request.requestPath(), null, null, null, null, null, null, null, stream, GatewayRequestStatus.CANCELED,
                "CLIENT_CANCELLED",
                "Request stream cancelled by client",
                usage,
                null,
                startedAt);
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
            String requestPath,
            String resourceType,
            String operation,
            String executionBackend,
            String supportStatus,
            String degradationLevel,
            String objectMode,
            String gatewayResourceKey,
            boolean stream,
            GatewayRequestStatus status,
            String errorCode,
            String errorMessage,
            GatewayUsageView usage,
            CanonicalResourceResponse canonicalResponse,
            Instant startedAt) {
        Instant completedAt = Instant.now();
        long durationMs = Duration.between(startedAt, completedAt).toMillis();
        GatewayObservabilityAsyncPersistenceService.RequestLogSnapshot snapshot =
                new GatewayObservabilityAsyncPersistenceService.RequestLogSnapshot(
                        requestId,
                        selectionResult.distributedKeyId(),
                        selectionResult.distributedKeyPrefix(),
                        selectionResult.protocol(),
                        requestPath,
                        resourceType,
                        operation,
                        selectionResult.requestedModel(),
                        selectionResult.publicModel(),
                        selectionResult.resolvedModelKey(),
                        selectionResult.modelGroup(),
                        selectionResult.selectedCandidate().candidate().providerType(),
                        selectionResult.selectedCandidate().candidate().credentialId(),
                        selectionResult.selectionSource().name(),
                        executionBackend,
                        supportStatus,
                        degradationLevel,
                        objectMode,
                        gatewayResourceKey,
                        canonicalResponse == null ? null : canonicalResponse.responseKind(),
                        canonicalResponse == null ? null : canonicalResponse.objectType(),
                        canonicalResponse == null ? null : canonicalResponse.objectId(),
                        canonicalResponse == null ? null : canonicalResponse.status(),
                        canonicalResponse == null ? null : canonicalResponse.events().size(),
                        selectionResult.prefixHash(),
                        selectionResult.fingerprint(),
                        stream,
                        status,
                        errorCode,
                        truncate(errorMessage),
                        durationMs,
                        startedAt,
                        completedAt
                );
        enqueueOrPersistRequestLogFinish(snapshot);

        saveUsageRecord(
                requestId,
                selectionResult,
                requestPath,
                resourceType,
                operation,
                executionBackend,
                objectMode,
                stream,
                usage
        );
        recordMetrics(selectionResult, requestPath, stream, status, usage, durationMs);
    }

    private void finishRequestWithoutSelection(
            String requestId,
            Long distributedKeyId,
            String distributedKeyPrefix,
            String protocol,
            String requestPath,
            String requestedModel,
            String resourceType,
            String operation,
            String executionBackend,
            String supportStatus,
            String degradationLevel,
            String objectMode,
            String gatewayResourceKey,
            boolean stream,
            GatewayRequestStatus status,
            String errorCode,
            String errorMessage,
            CanonicalResourceResponse canonicalResponse,
            Instant startedAt) {
        Instant completedAt = Instant.now();
        long durationMs = Duration.between(startedAt, completedAt).toMillis();
        GatewayObservabilityAsyncPersistenceService.RequestLogSnapshot snapshot =
                new GatewayObservabilityAsyncPersistenceService.RequestLogSnapshot(
                        requestId,
                        distributedKeyId,
                        distributedKeyPrefix,
                        protocol,
                        requestPath,
                        resourceType,
                        operation,
                        requestedModel,
                        requestedModel,
                        requestedModel,
                        requestedModel,
                        null,
                        null,
                        "NO_ROUTE",
                        executionBackend,
                        supportStatus,
                        degradationLevel,
                        objectMode,
                        gatewayResourceKey,
                        canonicalResponse == null ? null : canonicalResponse.responseKind(),
                        canonicalResponse == null ? null : canonicalResponse.objectType(),
                        canonicalResponse == null ? null : canonicalResponse.objectId(),
                        canonicalResponse == null ? null : canonicalResponse.status(),
                        canonicalResponse == null ? null : canonicalResponse.events().size(),
                        requestPath,
                        distributedKeyPrefix == null ? null : distributedKeyPrefix.toLowerCase(Locale.ROOT),
                        stream,
                        status,
                        errorCode,
                        truncate(errorMessage),
                        durationMs,
                        startedAt,
                        completedAt
                );
        enqueueOrPersistRequestLogFinish(snapshot);
    }

    private void enqueueOrPersistRequestLogStart(
            GatewayObservabilityAsyncPersistenceService.RequestLogSnapshot snapshot) {
        if (asyncPersistenceService != null && asyncPersistenceService.enqueueRequestLogStart(snapshot)) {
            return;
        }
        requestLogRepository.save(toLegacyRequestLogEntity(snapshot));
    }

    private void enqueueOrPersistRequestLogFinish(
            GatewayObservabilityAsyncPersistenceService.RequestLogSnapshot snapshot) {
        if (asyncPersistenceService != null && asyncPersistenceService.enqueueRequestLogFinish(snapshot)) {
            return;
        }
        RequestLogEntity entity = requestLogRepository.findByRequestId(snapshot.requestId()).orElseGet(RequestLogEntity::new);
        applyLegacyRequestLogSnapshot(entity, snapshot);
        requestLogRepository.save(entity);
    }

    private void enqueueOrPersistUsageRecord(
            GatewayObservabilityAsyncPersistenceService.UsageRecordSnapshot snapshot) {
        if (asyncPersistenceService != null && asyncPersistenceService.enqueueUsageRecordUpsert(snapshot)) {
            return;
        }
        UsageRecordEntity entity = usageRecordRepository.findByRequestId(snapshot.requestId()).orElseGet(UsageRecordEntity::new);
        applyLegacyUsageRecordSnapshot(entity, snapshot);
        usageRecordRepository.save(entity);
    }

    private RequestLogEntity toLegacyRequestLogEntity(
            GatewayObservabilityAsyncPersistenceService.RequestLogSnapshot snapshot) {
        RequestLogEntity entity = new RequestLogEntity();
        applyLegacyRequestLogSnapshot(entity, snapshot);
        return entity;
    }

    private void applyLegacyRequestLogSnapshot(
            RequestLogEntity entity,
            GatewayObservabilityAsyncPersistenceService.RequestLogSnapshot snapshot) {
        entity.setRequestId(snapshot.requestId());
        entity.setDistributedKeyId(snapshot.distributedKeyId());
        entity.setDistributedKeyPrefix(snapshot.distributedKeyPrefix());
        entity.setProtocol(snapshot.protocol());
        entity.setRequestPath(snapshot.requestPath());
        entity.setResourceType(snapshot.resourceType());
        entity.setOperation(snapshot.operation());
        entity.setRequestedModel(snapshot.requestedModel());
        entity.setPublicModel(snapshot.publicModel());
        entity.setResolvedModelKey(snapshot.resolvedModelKey());
        entity.setModelGroup(snapshot.modelGroup());
        entity.setProviderType(snapshot.providerType());
        entity.setCredentialId(snapshot.credentialId());
        entity.setSelectionSource(snapshot.selectionSource());
        entity.setExecutionBackend(snapshot.executionBackend());
        entity.setSupportStatus(snapshot.supportStatus());
        entity.setDegradationLevel(snapshot.degradationLevel());
        entity.setObjectMode(snapshot.objectMode());
        entity.setGatewayResourceKey(snapshot.gatewayResourceKey());
        entity.setResponseKind(snapshot.responseKind());
        entity.setResponseObjectType(snapshot.responseObjectType());
        entity.setResponseObjectId(snapshot.responseObjectId());
        entity.setResponseStatus(snapshot.responseStatus());
        entity.setCanonicalEventCount(snapshot.canonicalEventCount());
        entity.setPrefixHash(snapshot.prefixHash());
        entity.setFingerprint(snapshot.fingerprint());
        entity.setStream(snapshot.stream());
        entity.setStatus(snapshot.status());
        entity.setErrorCode(snapshot.errorCode());
        entity.setErrorMessage(snapshot.errorMessage());
        entity.setDurationMs(snapshot.durationMs());
        entity.setStartedAt(snapshot.startedAt());
        entity.setCompletedAt(snapshot.completedAt());
    }

    private void applyLegacyUsageRecordSnapshot(
            UsageRecordEntity entity,
            GatewayObservabilityAsyncPersistenceService.UsageRecordSnapshot snapshot) {
        entity.setRequestId(snapshot.requestId());
        entity.setDistributedKeyId(snapshot.distributedKeyId());
        entity.setProtocol(snapshot.protocol());
        entity.setRequestPath(snapshot.requestPath());
        entity.setModelGroup(snapshot.modelGroup());
        entity.setProviderType(snapshot.providerType());
        entity.setCredentialId(snapshot.credentialId());
        entity.setStream(snapshot.stream());
        entity.setCompleteness(snapshot.completeness());
        entity.setUsageSource(snapshot.usageSource());
        entity.setRawPromptTokens(snapshot.rawPromptTokens());
        entity.setPromptTokens(snapshot.promptTokens());
        entity.setCompletionTokens(snapshot.completionTokens());
        entity.setReasoningTokens(snapshot.reasoningTokens());
        entity.setCacheHitTokens(snapshot.cacheHitTokens());
        entity.setCacheWriteTokens(snapshot.cacheWriteTokens());
        entity.setUpstreamCacheHitTokens(snapshot.upstreamCacheHitTokens());
        entity.setUpstreamCacheWriteTokens(snapshot.upstreamCacheWriteTokens());
        entity.setSavedInputTokens(snapshot.savedInputTokens());
        entity.setCachedContentRef(snapshot.cachedContentRef());
        entity.setTotalTokens(snapshot.totalTokens());
        entity.setNativeUsagePayloadJson(snapshot.nativeUsagePayloadJson());
    }

    private String gatewayResourceKey(
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            CanonicalResourceResponse canonicalResponse) {
        if (plan == null || !supportsGatewayResourceKey(plan.resourceType())) {
            return null;
        }
        if (canonicalResponse != null && canonicalResponse.objectId() != null && !canonicalResponse.objectId().isBlank()) {
            return canonicalResponse.objectId();
        }
        if (request == null || request.pathParams().isEmpty()) {
            return null;
        }
        return request.pathParams().values().stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private boolean supportsGatewayResourceKey(TranslationResourceType resourceType) {
        if (resourceType == null) {
            return false;
        }
        return resourceType == TranslationResourceType.RESPONSE
                || resourceType == TranslationResourceType.UPLOAD
                || resourceType == TranslationResourceType.BATCH
                || resourceType == TranslationResourceType.TUNING
                || resourceType == TranslationResourceType.REALTIME;
    }

    private void saveUsageRecord(
            String requestId,
            RouteSelectionResult selectionResult,
            String requestPath,
            String resourceType,
            String operation,
            String executionBackend,
            String objectMode,
            boolean stream,
            GatewayUsageView usage) {
        if (usage == null || !usage.present()) {
            return;
        }

        GatewayObservabilityAsyncPersistenceService.UsageRecordSnapshot snapshot =
                new GatewayObservabilityAsyncPersistenceService.UsageRecordSnapshot(
                        requestId,
                        selectionResult.distributedKeyId(),
                        selectionResult.protocol(),
                        requestPath,
                        selectionResult.modelGroup(),
                        selectionResult.selectedCandidate().candidate().providerType(),
                        selectionResult.selectedCandidate().candidate().credentialId(),
                        stream,
                        usage.completeness(),
                        usage.source(),
                        usage.rawPromptTokens(),
                        usage.promptTokens(),
                        usage.completionTokens(),
                        usage.reasoningTokens(),
                        usage.cacheHitTokens(),
                        usage.cacheWriteTokens(),
                        usage.upstreamCacheHitTokens(),
                        usage.upstreamCacheWriteTokens(),
                        usage.savedInputTokens(),
                        usage.cachedContentRef(),
                        usage.totalTokens(),
                        toJson(usage.nativeUsagePayload())
                );
        enqueueOrPersistUsageRecord(snapshot);
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
        } catch (JacksonException exception) {
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
