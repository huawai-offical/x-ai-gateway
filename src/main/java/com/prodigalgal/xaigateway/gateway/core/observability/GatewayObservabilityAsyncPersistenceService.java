package com.prodigalgal.xaigateway.gateway.core.observability;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageCompleteness;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageSource;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.CacheHitLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteDecisionLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UsageRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.CacheHitLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteDecisionLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UsageRecordRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GatewayObservabilityAsyncPersistenceService {

    static final String REQUEST_LOG_START = "request-log-start";
    static final String REQUEST_LOG_FINISH = "request-log-finish";
    static final String USAGE_RECORD_UPSERT = "usage-record-upsert";
    static final String ROUTE_DECISION_LOG_INSERT = "route-decision-log-insert";
    static final String CACHE_HIT_LOG_INSERT = "cache-hit-log-insert";

    private static final Logger log = LoggerFactory.getLogger(GatewayObservabilityAsyncPersistenceService.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RequestLogRepository requestLogRepository;
    private final UsageRecordRepository usageRecordRepository;
    private final RouteDecisionLogRepository routeDecisionLogRepository;
    private final CacheHitLogRepository cacheHitLogRepository;
    private final GatewayProperties gatewayProperties;
    private final TransactionTemplate transactionTemplate;

    public GatewayObservabilityAsyncPersistenceService(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            RequestLogRepository requestLogRepository,
            UsageRecordRepository usageRecordRepository,
            RouteDecisionLogRepository routeDecisionLogRepository,
            CacheHitLogRepository cacheHitLogRepository,
            GatewayProperties gatewayProperties,
            PlatformTransactionManager transactionManager) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.requestLogRepository = requestLogRepository;
        this.usageRecordRepository = usageRecordRepository;
        this.routeDecisionLogRepository = routeDecisionLogRepository;
        this.cacheHitLogRepository = cacheHitLogRepository;
        this.gatewayProperties = gatewayProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public boolean enqueueRequestLogStart(RequestLogSnapshot snapshot) {
        return enqueue(REQUEST_LOG_START, snapshot);
    }

    public boolean enqueueRequestLogFinish(RequestLogSnapshot snapshot) {
        return enqueue(REQUEST_LOG_FINISH, snapshot);
    }

    public boolean enqueueUsageRecordUpsert(UsageRecordSnapshot snapshot) {
        return enqueue(USAGE_RECORD_UPSERT, snapshot);
    }

    public boolean enqueueRouteDecisionLogInsert(RouteDecisionLogSnapshot snapshot) {
        return enqueue(ROUTE_DECISION_LOG_INSERT, snapshot);
    }

    public boolean enqueueCacheHitLogInsert(CacheHitLogSnapshot snapshot) {
        return enqueue(CACHE_HIT_LOG_INSERT, snapshot);
    }

    public void persistRequestLogStart(RequestLogSnapshot snapshot) {
        requestLogRepository.save(toRequestLogEntity(snapshot));
    }

    public void persistRequestLogFinish(RequestLogSnapshot snapshot) {
        RequestLogEntity entity = requestLogRepository.findByRequestId(snapshot.requestId())
                .orElseGet(RequestLogEntity::new);
        applyRequestLogSnapshot(entity, snapshot);
        requestLogRepository.save(entity);
    }

    public void persistUsageRecordUpsert(UsageRecordSnapshot snapshot) {
        UsageRecordEntity entity = usageRecordRepository.findByRequestId(snapshot.requestId())
                .orElseGet(UsageRecordEntity::new);
        applyUsageRecordSnapshot(entity, snapshot);
        usageRecordRepository.save(entity);
    }

    public void persistRouteDecisionLogInsert(RouteDecisionLogSnapshot snapshot) {
        routeDecisionLogRepository.save(toRouteDecisionLogEntity(snapshot));
    }

    public void persistCacheHitLogInsert(CacheHitLogSnapshot snapshot) {
        cacheHitLogRepository.save(toCacheHitLogEntity(snapshot));
    }

    @Scheduled(fixedDelayString = "${gateway.observability.async.flush-interval:PT1S}")
    public void flushScheduledBatch() {
        if (!isAsyncEnabled()) {
            return;
        }

        try {
            flushBatch();
        } catch (RuntimeException exception) {
            log.error("批量落库 observability 热路径队列失败。", exception);
        }
    }

    public int flushBatch() {
        if (!isAsyncEnabled()) {
            return 0;
        }

        List<String> serializedEntries;
        try {
            serializedEntries = stringRedisTemplate.opsForList().leftPop(queueKey(), batchSize());
        } catch (RuntimeException exception) {
            log.warn("从 Redis 读取 observability 热路径队列失败，等待下一轮重试。", exception);
            return 0;
        }

        if (serializedEntries == null || serializedEntries.isEmpty()) {
            return 0;
        }

        List<ParsedEvent> parsedEvents = new ArrayList<>();
        for (String serializedEntry : serializedEntries) {
            ParsedEvent parsedEvent = parseEvent(serializedEntry);
            if (parsedEvent != null) {
                parsedEvents.add(parsedEvent);
            }
        }
        if (parsedEvents.isEmpty()) {
            return 0;
        }

        try {
            transactionTemplate.executeWithoutResult(status -> persistBatch(parsedEvents));
            return parsedEvents.size();
        } catch (RuntimeException exception) {
            requeue(parsedEvents.stream().map(ParsedEvent::serialized).toList());
            throw exception;
        }
    }

    private boolean enqueue(String type, Object payload) {
        if (!isAsyncEnabled()) {
            return false;
        }

        try {
            stringRedisTemplate.opsForList().rightPush(
                    queueKey(),
                    objectMapper.writeValueAsString(new QueueEnvelope(type, objectMapper.valueToTree(payload)))
            );
            return true;
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化 observability 热路径事件失败。", exception);
        } catch (RuntimeException exception) {
            log.warn("写入 Redis observability 热路径队列失败，回退同步写库。", exception);
            return false;
        }
    }

    private ParsedEvent parseEvent(String serializedEntry) {
        try {
            QueueEnvelope envelope = objectMapper.readValue(serializedEntry, QueueEnvelope.class);
            if (envelope.type() == null || envelope.payload() == null) {
                log.error("忽略缺少 type/payload 的 observability 队列消息。payload={}", serializedEntry);
                return null;
            }

            return switch (envelope.type()) {
                case REQUEST_LOG_START -> new ParsedEvent(
                        serializedEntry,
                        envelope.type(),
                        objectMapper.treeToValue(envelope.payload(), RequestLogSnapshot.class)
                );
                case REQUEST_LOG_FINISH -> new ParsedEvent(
                        serializedEntry,
                        envelope.type(),
                        objectMapper.treeToValue(envelope.payload(), RequestLogSnapshot.class)
                );
                case USAGE_RECORD_UPSERT -> new ParsedEvent(
                        serializedEntry,
                        envelope.type(),
                        objectMapper.treeToValue(envelope.payload(), UsageRecordSnapshot.class)
                );
                case ROUTE_DECISION_LOG_INSERT -> new ParsedEvent(
                        serializedEntry,
                        envelope.type(),
                        objectMapper.treeToValue(envelope.payload(), RouteDecisionLogSnapshot.class)
                );
                case CACHE_HIT_LOG_INSERT -> new ParsedEvent(
                        serializedEntry,
                        envelope.type(),
                        objectMapper.treeToValue(envelope.payload(), CacheHitLogSnapshot.class)
                );
                default -> {
                    log.error("忽略未知 observability 队列消息类型：{}。", envelope.type());
                    yield null;
                }
            };
        } catch (Exception exception) {
            log.error("解析 observability 热路径队列消息失败，已丢弃。payload={}", serializedEntry, exception);
            return null;
        }
    }

    private void persistBatch(List<ParsedEvent> parsedEvents) {
        List<RequestLogSnapshot> requestLogStarts = new ArrayList<>();
        List<RequestLogSnapshot> requestLogFinishes = new ArrayList<>();
        List<UsageRecordSnapshot> usageSnapshots = new ArrayList<>();
        List<RouteDecisionLogSnapshot> routeDecisionSnapshots = new ArrayList<>();
        List<CacheHitLogSnapshot> cacheHitSnapshots = new ArrayList<>();

        for (ParsedEvent parsedEvent : parsedEvents) {
            switch (parsedEvent.type()) {
                case REQUEST_LOG_START -> requestLogStarts.add((RequestLogSnapshot) parsedEvent.payload());
                case REQUEST_LOG_FINISH -> requestLogFinishes.add((RequestLogSnapshot) parsedEvent.payload());
                case USAGE_RECORD_UPSERT -> usageSnapshots.add((UsageRecordSnapshot) parsedEvent.payload());
                case ROUTE_DECISION_LOG_INSERT -> routeDecisionSnapshots.add((RouteDecisionLogSnapshot) parsedEvent.payload());
                case CACHE_HIT_LOG_INSERT -> cacheHitSnapshots.add((CacheHitLogSnapshot) parsedEvent.payload());
                default -> throw new IllegalStateException("不支持的 observability 队列消息类型：" + parsedEvent.type());
            }
        }

        if (!requestLogStarts.isEmpty()) {
            requestLogRepository.saveAll(requestLogStarts.stream().map(this::toRequestLogEntity).toList());
        }

        if (!requestLogFinishes.isEmpty()) {
            List<String> requestIds = requestLogFinishes.stream()
                    .map(RequestLogSnapshot::requestId)
                    .toList();
            Map<String, RequestLogEntity> existing = indexByRequestId(requestLogRepository.findAllByRequestIdIn(requestIds));
            List<RequestLogEntity> finishEntities = new ArrayList<>();
            for (RequestLogSnapshot snapshot : requestLogFinishes) {
                RequestLogEntity entity = existing.get(snapshot.requestId());
                if (entity == null) {
                    entity = new RequestLogEntity();
                }
                applyRequestLogSnapshot(entity, snapshot);
                existing.put(snapshot.requestId(), entity);
                finishEntities.add(entity);
            }
            requestLogRepository.saveAll(finishEntities);
        }

        if (!usageSnapshots.isEmpty()) {
            List<String> requestIds = usageSnapshots.stream()
                    .map(UsageRecordSnapshot::requestId)
                    .toList();
            Map<String, UsageRecordEntity> existing = indexUsageByRequestId(usageRecordRepository.findAllByRequestIdIn(requestIds));
            List<UsageRecordEntity> usageEntities = new ArrayList<>();
            for (UsageRecordSnapshot snapshot : usageSnapshots) {
                UsageRecordEntity entity = existing.get(snapshot.requestId());
                if (entity == null) {
                    entity = new UsageRecordEntity();
                }
                applyUsageRecordSnapshot(entity, snapshot);
                existing.put(snapshot.requestId(), entity);
                usageEntities.add(entity);
            }
            usageRecordRepository.saveAll(usageEntities);
        }

        if (!routeDecisionSnapshots.isEmpty()) {
            routeDecisionLogRepository.saveAll(routeDecisionSnapshots.stream().map(this::toRouteDecisionLogEntity).toList());
        }

        if (!cacheHitSnapshots.isEmpty()) {
            cacheHitLogRepository.saveAll(cacheHitSnapshots.stream().map(this::toCacheHitLogEntity).toList());
        }
    }

    private void requeue(List<String> serializedEntries) {
        if (serializedEntries.isEmpty()) {
            return;
        }

        List<String> entriesToRestore = new ArrayList<>(serializedEntries);
        java.util.Collections.reverse(entriesToRestore);
        try {
            stringRedisTemplate.opsForList().leftPushAll(queueKey(), entriesToRestore);
        } catch (RuntimeException exception) {
            log.error("observability 热路径队列回写 Redis 失败，当前批次可能需要人工补偿。", exception);
        }
    }

    private Map<String, RequestLogEntity> indexByRequestId(Collection<RequestLogEntity> entities) {
        Map<String, RequestLogEntity> result = new LinkedHashMap<>();
        for (RequestLogEntity entity : entities) {
            result.put(entity.getRequestId(), entity);
        }
        return result;
    }

    private Map<String, UsageRecordEntity> indexUsageByRequestId(Collection<UsageRecordEntity> entities) {
        Map<String, UsageRecordEntity> result = new LinkedHashMap<>();
        for (UsageRecordEntity entity : entities) {
            result.put(entity.getRequestId(), entity);
        }
        return result;
    }

    private RequestLogEntity toRequestLogEntity(RequestLogSnapshot snapshot) {
        RequestLogEntity entity = new RequestLogEntity();
        applyRequestLogSnapshot(entity, snapshot);
        return entity;
    }

    private void applyRequestLogSnapshot(RequestLogEntity entity, RequestLogSnapshot snapshot) {
        entity.setRequestId(snapshot.requestId());
        entity.setDistributedKeyId(snapshot.distributedKeyId());
        entity.setDistributedKeyPrefix(snapshot.distributedKeyPrefix());
        if (snapshot.clientFamily() != null) {
            entity.setClientFamily(snapshot.clientFamily());
        }
        if (snapshot.clientInstance() != null) {
            entity.setClientInstance(snapshot.clientInstance());
        }
        if (snapshot.workspaceHint() != null) {
            entity.setWorkspaceHint(snapshot.workspaceHint());
        }
        if (snapshot.sessionAffinitySource() != null) {
            entity.setSessionAffinitySource(snapshot.sessionAffinitySource());
        }
        if (snapshot.sessionAffinityKey() != null) {
            entity.setSessionAffinityKey(snapshot.sessionAffinityKey());
        }
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

    private RouteDecisionLogEntity toRouteDecisionLogEntity(RouteDecisionLogSnapshot snapshot) {
        RouteDecisionLogEntity entity = new RouteDecisionLogEntity();
        entity.setRequestId(snapshot.requestId());
        entity.setDistributedKeyId(snapshot.distributedKeyId());
        entity.setDistributedKeyPrefix(snapshot.distributedKeyPrefix());
        entity.setRequestedModel(snapshot.requestedModel());
        entity.setPublicModel(snapshot.publicModel());
        entity.setResolvedModelKey(snapshot.resolvedModelKey());
        entity.setProtocol(snapshot.protocol());
        entity.setRequestPath(snapshot.requestPath());
        entity.setResourceType(snapshot.resourceType());
        entity.setOperation(snapshot.operation());
        entity.setModelGroup(snapshot.modelGroup());
        entity.setSelectionSource(snapshot.selectionSource());
        entity.setExecutionBackend(snapshot.executionBackend());
        entity.setSupportStatus(snapshot.supportStatus());
        entity.setDegradationLevel(snapshot.degradationLevel());
        entity.setObjectMode(snapshot.objectMode());
        entity.setSelectedCredentialId(snapshot.selectedCredentialId());
        entity.setSelectedProviderType(snapshot.selectedProviderType());
        entity.setSelectedBaseUrl(snapshot.selectedBaseUrl());
        entity.setPrefixHash(snapshot.prefixHash());
        entity.setFingerprint(snapshot.fingerprint());
        entity.setCandidateCount(snapshot.candidateCount());
        entity.setCandidateSummaryJson(snapshot.candidateSummaryJson());
        return entity;
    }

    private CacheHitLogEntity toCacheHitLogEntity(CacheHitLogSnapshot snapshot) {
        CacheHitLogEntity entity = new CacheHitLogEntity();
        entity.setRequestId(snapshot.requestId());
        entity.setDistributedKeyId(snapshot.distributedKeyId());
        entity.setProtocol(snapshot.protocol());
        entity.setRequestPath(snapshot.requestPath());
        entity.setResourceType(snapshot.resourceType());
        entity.setOperation(snapshot.operation());
        entity.setProviderType(snapshot.providerType());
        entity.setCredentialId(snapshot.credentialId());
        entity.setModelGroup(snapshot.modelGroup());
        entity.setPrefixHash(snapshot.prefixHash());
        entity.setFingerprint(snapshot.fingerprint());
        entity.setCacheKind(snapshot.cacheKind());
        entity.setExecutionBackend(snapshot.executionBackend());
        entity.setSupportStatus(snapshot.supportStatus());
        entity.setDegradationLevel(snapshot.degradationLevel());
        entity.setObjectMode(snapshot.objectMode());
        entity.setCacheHitTokens(snapshot.cacheHitTokens());
        entity.setCacheWriteTokens(snapshot.cacheWriteTokens());
        entity.setSavedInputTokens(snapshot.savedInputTokens());
        entity.setCachedContentRef(snapshot.cachedContentRef());
        return entity;
    }

    private void applyUsageRecordSnapshot(UsageRecordEntity entity, UsageRecordSnapshot snapshot) {
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

    private boolean isAsyncEnabled() {
        return gatewayProperties.getObservability().getAsync().isEnabled();
    }

    private String queueKey() {
        return gatewayProperties.getObservability().getAsync().getQueueKey();
    }

    private long batchSize() {
        return Math.max(1, gatewayProperties.getObservability().getAsync().getBatchSize());
    }

    private record QueueEnvelope(String type, JsonNode payload) {
    }

    private record ParsedEvent(String serialized, String type, Object payload) {
    }

    public record RequestLogSnapshot(
            String requestId,
            Long distributedKeyId,
            String distributedKeyPrefix,
            String clientFamily,
            String clientInstance,
            String workspaceHint,
            String sessionAffinitySource,
            String sessionAffinityKey,
            String protocol,
            String requestPath,
            String resourceType,
            String operation,
            String requestedModel,
            String publicModel,
            String resolvedModelKey,
            String modelGroup,
            ProviderType providerType,
            Long credentialId,
            String selectionSource,
            String executionBackend,
            String supportStatus,
            String degradationLevel,
            String objectMode,
            String gatewayResourceKey,
            String responseKind,
            String responseObjectType,
            String responseObjectId,
            String responseStatus,
            Integer canonicalEventCount,
            String prefixHash,
            String fingerprint,
            boolean stream,
            GatewayRequestStatus status,
            String errorCode,
            String errorMessage,
            Long durationMs,
            Instant startedAt,
            Instant completedAt) {
        public RequestLogSnapshot(
                String requestId,
                Long distributedKeyId,
                String distributedKeyPrefix,
                String protocol,
                String requestPath,
                String resourceType,
                String operation,
                String requestedModel,
                String publicModel,
                String resolvedModelKey,
                String modelGroup,
                ProviderType providerType,
                Long credentialId,
                String selectionSource,
                String executionBackend,
                String supportStatus,
                String degradationLevel,
                String objectMode,
                String gatewayResourceKey,
                String responseKind,
                String responseObjectType,
                String responseObjectId,
                String responseStatus,
                Integer canonicalEventCount,
                String prefixHash,
                String fingerprint,
                boolean stream,
                GatewayRequestStatus status,
                String errorCode,
                String errorMessage,
                Long durationMs,
                Instant startedAt,
                Instant completedAt) {
            this(
                    requestId,
                    distributedKeyId,
                    distributedKeyPrefix,
                    null,
                    null,
                    null,
                    null,
                    null,
                    protocol,
                    requestPath,
                    resourceType,
                    operation,
                    requestedModel,
                    publicModel,
                    resolvedModelKey,
                    modelGroup,
                    providerType,
                    credentialId,
                    selectionSource,
                    executionBackend,
                    supportStatus,
                    degradationLevel,
                    objectMode,
                    gatewayResourceKey,
                    responseKind,
                    responseObjectType,
                    responseObjectId,
                    responseStatus,
                    canonicalEventCount,
                    prefixHash,
                    fingerprint,
                    stream,
                    status,
                    errorCode,
                    errorMessage,
                    durationMs,
                    startedAt,
                    completedAt
            );
        }
    }

    public record UsageRecordSnapshot(
            String requestId,
            Long distributedKeyId,
            String protocol,
            String requestPath,
            String modelGroup,
            ProviderType providerType,
            Long credentialId,
            boolean stream,
            GatewayUsageCompleteness completeness,
            GatewayUsageSource usageSource,
            int rawPromptTokens,
            int promptTokens,
            int completionTokens,
            int reasoningTokens,
            int cacheHitTokens,
            int cacheWriteTokens,
            int upstreamCacheHitTokens,
            int upstreamCacheWriteTokens,
            int savedInputTokens,
            String cachedContentRef,
            int totalTokens,
            String nativeUsagePayloadJson) {
    }

    public record RouteDecisionLogSnapshot(
            String requestId,
            Long distributedKeyId,
            String distributedKeyPrefix,
            String requestedModel,
            String publicModel,
            String resolvedModelKey,
            String protocol,
            String requestPath,
            String resourceType,
            String operation,
            String modelGroup,
            String selectionSource,
            String executionBackend,
            String supportStatus,
            String degradationLevel,
            String objectMode,
            Long selectedCredentialId,
            ProviderType selectedProviderType,
            String selectedBaseUrl,
            String prefixHash,
            String fingerprint,
            int candidateCount,
            String candidateSummaryJson) {
    }

    public record CacheHitLogSnapshot(
            String requestId,
            Long distributedKeyId,
            String protocol,
            String requestPath,
            String resourceType,
            String operation,
            ProviderType providerType,
            Long credentialId,
            String modelGroup,
            String prefixHash,
            String fingerprint,
            String cacheKind,
            String executionBackend,
            String supportStatus,
            String degradationLevel,
            String objectMode,
            int cacheHitTokens,
            int cacheWriteTokens,
            int savedInputTokens,
            String cachedContentRef) {
    }
}
