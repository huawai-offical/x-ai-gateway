package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ChangePlanExecuteRequest;
import com.prodigalgal.xaigateway.admin.api.OpsSloSummaryResponse;
import com.prodigalgal.xaigateway.admin.application.operations.DataSnapshotDriver;
import com.prodigalgal.xaigateway.admin.application.operations.ChangePlanType;
import com.prodigalgal.xaigateway.admin.application.operations.ChangePreflightCheck;
import com.prodigalgal.xaigateway.admin.application.operations.ChangePreflightResult;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.ChangePlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ChangePlanRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.ReleaseArtifactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ChangePreflightService {

    private final ReleaseArtifactRepository releaseArtifactRepository;
    private final RecoveryCheckpointService recoveryCheckpointService;
    private final ChangePlanRepository changePlanRepository;
    private final MaintenanceWindowService maintenanceWindowService;
    private final DataSnapshotService dataSnapshotService;
    private final OpsSloService opsSloService;
    private final GatewayProperties gatewayProperties;
    private final DataSource dataSource;

    public ChangePreflightService(
            ReleaseArtifactRepository releaseArtifactRepository,
            RecoveryCheckpointService recoveryCheckpointService,
            ChangePlanRepository changePlanRepository,
            MaintenanceWindowService maintenanceWindowService,
            DataSnapshotService dataSnapshotService,
            OpsSloService opsSloService,
            GatewayProperties gatewayProperties,
            DataSource dataSource) {
        this.releaseArtifactRepository = releaseArtifactRepository;
        this.recoveryCheckpointService = recoveryCheckpointService;
        this.changePlanRepository = changePlanRepository;
        this.maintenanceWindowService = maintenanceWindowService;
        this.dataSnapshotService = dataSnapshotService;
        this.opsSloService = opsSloService;
        this.gatewayProperties = gatewayProperties;
        this.dataSource = dataSource;
    }

    public ChangePreflightResult evaluate(ChangePlanEntity changePlan, ChangePlanExecuteRequest executeRequest) {
        List<ChangePreflightCheck> checks = new ArrayList<>();
        ChangePlanType planType = ChangePlanType.valueOf(changePlan.getPlanType());
        Instant now = Instant.now();

        if (changePlan.getReleaseArtifactId() != null) {
            boolean artifactPresent = releaseArtifactRepository.findById(changePlan.getReleaseArtifactId()).isPresent();
            checks.add(new ChangePreflightCheck("releaseArtifact", artifactPresent ? "OK" : "FAILED", true, artifactPresent ? "release artifact 可用。" : "release artifact 不存在。"));
        } else if (planType == ChangePlanType.UPGRADE) {
            checks.add(new ChangePreflightCheck("releaseArtifact", "FAILED", true, "UPGRADE 计划必须指定 release artifact。"));
        }

        if (planType == ChangePlanType.RESTORE || planType == ChangePlanType.ROLLBACK) {
            boolean checkpointPresent = changePlan.getRecoveryCheckpointId() != null;
            if (checkpointPresent) {
                recoveryCheckpointService.getEntity(changePlan.getRecoveryCheckpointId());
            }
            checks.add(new ChangePreflightCheck("recoveryCheckpoint", checkpointPresent ? "OK" : "FAILED", true, checkpointPresent ? "recovery checkpoint 可用。" : "缺少 recovery checkpoint。"));
        }

        boolean dataSourceReady;
        try (Connection ignored = dataSource.getConnection()) {
            dataSourceReady = true;
        } catch (Exception exception) {
            dataSourceReady = false;
        }
        checks.add(new ChangePreflightCheck("postgresConnection", dataSourceReady ? "OK" : "FAILED", true, dataSourceReady ? "PostgreSQL 可连接。" : "PostgreSQL 连接失败。"));

        Path fileRoot = Path.of(gatewayProperties.getStorage().getFileRoot()).toAbsolutePath();
        boolean fileRootReady;
        try {
            Files.createDirectories(fileRoot);
            fileRootReady = Files.isDirectory(fileRoot);
        } catch (Exception exception) {
            fileRootReady = false;
        }
        checks.add(new ChangePreflightCheck("fileRoot", fileRootReady ? "OK" : "FAILED", true, fileRootReady ? "gateway.storage.fileRoot 可访问。" : "gateway.storage.fileRoot 不可访问。"));

        boolean runningConflict = changePlanRepository.existsByStatusIn(List.of("RUNNING", "ROLLING_BACK"))
                && !"RUNNING".equals(changePlan.getStatus())
                && !"ROLLING_BACK".equals(changePlan.getStatus());
        checks.add(new ChangePreflightCheck("runningConflict", runningConflict ? "FAILED" : "OK", true, runningConflict ? "存在冲突中的 RUNNING plan。" : "没有冲突中的 RUNNING plan。"));

        DataSnapshotDriver.AvailabilityCheck snapshotCheck = dataSnapshotService.availabilityCheck();
        checks.add(new ChangePreflightCheck("snapshotTools", snapshotCheck.available() ? "OK" : "FAILED", true, snapshotCheck.message()));

        OpsSloSummaryResponse sloSummary = opsSloService.summary(now);
        String riskLevel = sloSummary.summary() == null ? "UNKNOWN" : sloSummary.summary().riskLevel();
        boolean manualOverride = (executeRequest.manualOverride() != null && executeRequest.manualOverride())
                || changePlan.isManualOverride();
        boolean riskOk = !"CRITICAL".equalsIgnoreCase(riskLevel) || manualOverride;
        checks.add(new ChangePreflightCheck("riskLevel", riskOk ? "OK" : "FAILED", true, riskOk ? "当前风险等级允许执行。" : "当前风险等级为 CRITICAL，必须显式 override。"));

        if (planType == ChangePlanType.UPGRADE) {
            boolean windowActive = changePlan.getMaintenanceWindowId() != null && maintenanceWindowService.isActive(changePlan.getMaintenanceWindowId(), now);
            checks.add(new ChangePreflightCheck("maintenanceWindow", windowActive ? "OK" : "FAILED", true, windowActive ? "命中有效维护窗口。" : "UPGRADE 必须命中有效维护窗口。"));
        } else if (planType == ChangePlanType.RESTORE || planType == ChangePlanType.ROLLBACK) {
            boolean emergencyReady = hasText(valueOrElse(executeRequest.emergencyReason(), changePlan.getEmergencyReason()));
            checks.add(new ChangePreflightCheck("emergencyReason", emergencyReady ? "OK" : "FAILED", true, emergencyReady ? "已提供 emergencyReason。" : "RESTORE / ROLLBACK 在窗口外执行必须提供 emergencyReason。"));
        }

        return new ChangePreflightResult(riskLevel, checks);
    }

    private String valueOrElse(String first, String second) {
        return hasText(first) ? first : second;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
