package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OpsScheduledProbeJobRequest;
import com.prodigalgal.xaigateway.admin.api.OpsScheduledProbeJobResponse;
import com.prodigalgal.xaigateway.admin.api.ProxyProbeResultResponse;
import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventBusService;
import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventType;
import com.prodigalgal.xaigateway.infra.persistence.entity.OpsScheduledProbeJobEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpsScheduledProbeJobRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class OpsProbeJobService {

    private final OpsScheduledProbeJobRepository opsScheduledProbeJobRepository;
    private final NetworkGovernanceService networkGovernanceService;
    private final OpsAuditService opsAuditService;
    private final OpsEventBusService opsEventBusService;

    public OpsProbeJobService(
            OpsScheduledProbeJobRepository opsScheduledProbeJobRepository,
            NetworkGovernanceService networkGovernanceService,
            OpsAuditService opsAuditService,
            OpsEventBusService opsEventBusService) {
        this.opsScheduledProbeJobRepository = opsScheduledProbeJobRepository;
        this.networkGovernanceService = networkGovernanceService;
        this.opsAuditService = opsAuditService;
        this.opsEventBusService = opsEventBusService;
    }

    @Transactional(readOnly = true)
    public List<OpsScheduledProbeJobResponse> list() {
        return opsScheduledProbeJobRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    public OpsScheduledProbeJobResponse save(Long id, OpsScheduledProbeJobRequest request) {
        OpsScheduledProbeJobEntity entity = id == null
                ? new OpsScheduledProbeJobEntity()
                : opsScheduledProbeJobRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到 probe job。"));
        entity.setJobName(request.jobName());
        entity.setProbeType(request.probeType());
        entity.setTargetRef(request.targetRef());
        entity.setIntervalSeconds(request.intervalSeconds() == null ? 60 : request.intervalSeconds());
        entity.setEnabled(request.enabled() == null || request.enabled());
        return toResponse(opsScheduledProbeJobRepository.save(entity));
    }

    public OpsScheduledProbeJobResponse trigger(Long id) {
        OpsScheduledProbeJobEntity entity = opsScheduledProbeJobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到 probe job。"));
        runJob(entity);
        return toResponse(entity);
    }

    @Scheduled(fixedDelay = 30000)
    public void runDueJobs() {
        Instant now = Instant.now();
        for (OpsScheduledProbeJobEntity entity : opsScheduledProbeJobRepository.findAllByEnabledTrueOrderByCreatedAtAsc()) {
            if (entity.getLastRunAt() != null && entity.getLastRunAt().plusSeconds(entity.getIntervalSeconds()).isAfter(now)) {
                continue;
            }
            runJob(entity);
        }
    }

    private void runJob(OpsScheduledProbeJobEntity entity) {
        entity.setLastRunAt(Instant.now());
        try {
            if ("NETWORK_PROXY".equalsIgnoreCase(entity.getProbeType())) {
                ProxyProbeResultResponse probe = networkGovernanceService.probe(Long.parseLong(entity.getTargetRef()));
                entity.setLastStatus(probe.status());
                entity.setLastErrorMessage(probe.errorMessage());
                opsEventBusService.publish(OpsEventType.PROBE_RESULT, probe);
            } else {
                entity.setLastStatus("SKIPPED");
                entity.setLastErrorMessage("当前仅实现 NETWORK_PROXY probe job。");
            }
            opsAuditService.record("OPS", "PROBE_JOB_RUN", "ops_scheduled_probe_job", String.valueOf(entity.getId()), entity.getTargetRef());
        } catch (Exception exception) {
            entity.setLastStatus("FAILED");
            entity.setLastErrorMessage(exception.getMessage());
        }
        opsScheduledProbeJobRepository.save(entity);
    }

    private OpsScheduledProbeJobResponse toResponse(OpsScheduledProbeJobEntity entity) {
        return new OpsScheduledProbeJobResponse(
                entity.getId(),
                entity.getJobName(),
                entity.getProbeType(),
                entity.getTargetRef(),
                entity.getIntervalSeconds(),
                entity.isEnabled(),
                entity.getLastRunAt(),
                entity.getLastStatus(),
                entity.getLastErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
