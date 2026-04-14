package com.prodigalgal.xaigateway.admin.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.api.*;
import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventBusService;
import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventType;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.*;
import com.prodigalgal.xaigateway.infra.persistence.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class PlatformOperationsService {

    private final InstallationStateRepository installationStateRepository;
    private final BackupJobRepository backupJobRepository;
    private final RestoreJobRepository restoreJobRepository;
    private final ReleaseArtifactRepository releaseArtifactRepository;
    private final UpgradeJobRepository upgradeJobRepository;
    private final RollbackJobRepository rollbackJobRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final GatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper;
    private final OpsAuditService opsAuditService;
    private final OpsEventBusService opsEventBusService;

    public PlatformOperationsService(
            InstallationStateRepository installationStateRepository,
            BackupJobRepository backupJobRepository,
            RestoreJobRepository restoreJobRepository,
            ReleaseArtifactRepository releaseArtifactRepository,
            UpgradeJobRepository upgradeJobRepository,
            RollbackJobRepository rollbackJobRepository,
            SystemSettingRepository systemSettingRepository,
            GatewayProperties gatewayProperties,
            ObjectMapper objectMapper,
            OpsAuditService opsAuditService,
            OpsEventBusService opsEventBusService) {
        this.installationStateRepository = installationStateRepository;
        this.backupJobRepository = backupJobRepository;
        this.restoreJobRepository = restoreJobRepository;
        this.releaseArtifactRepository = releaseArtifactRepository;
        this.upgradeJobRepository = upgradeJobRepository;
        this.rollbackJobRepository = rollbackJobRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.gatewayProperties = gatewayProperties;
        this.objectMapper = objectMapper;
        this.opsAuditService = opsAuditService;
        this.opsEventBusService = opsEventBusService;
    }

    @Transactional(readOnly = true)
    public InstallationStateResponse getInstallationState() {
        return toInstallResponse(getOrCreateInstallationState());
    }

    public InstallationStateResponse bootstrap(InstallBootstrapRequest request) {
        InstallationStateEntity state = getOrCreateInstallationState();
        state.setStatus("READY");
        state.setBootstrapCompleted(true);
        state.setLastHealthCheckAt(Instant.now());
        state.setMetadataJson(writeJson(Map.of(
                "adminEmail", request.adminEmail(),
                "environmentName", request.environmentName(),
                "db", "ok",
                "redis", "ok"
        )));
        InstallationStateEntity saved = installationStateRepository.save(state);
        opsAuditService.record("PLATFORM", "BOOTSTRAP", "installation_state", String.valueOf(saved.getId()), saved.getMetadataJson());
        opsEventBusService.publish(OpsEventType.SYSTEM_LOG, toInstallResponse(saved));
        return toInstallResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BackupJobResponse> listBackups() {
        return backupJobRepository.findTop100ByOrderByCreatedAtDesc().stream().map(this::toBackupResponse).toList();
    }

    public BackupJobResponse createBackup(boolean dryRun) {
        BackupJobEntity entity = new BackupJobEntity();
        entity.setDryRun(dryRun);
        entity.setStatus(dryRun ? "DRY_RUN_OK" : "COMPLETED");
        entity.setSummaryJson(writeJson(snapshotSummary()));
        if (!dryRun) {
            entity.setSnapshotPath(writeSnapshot("backup", entity.getSummaryJson()));
        }
        BackupJobEntity saved = backupJobRepository.save(entity);
        opsAuditService.record("PLATFORM", "BACKUP_CREATED", "backup_job", String.valueOf(saved.getId()), saved.getSummaryJson());
        return toBackupResponse(saved);
    }

    public RestoreJobResponse restoreBackup(Long backupJobId, boolean dryRun) {
        BackupJobEntity backup = backupJobRepository.findById(backupJobId)
                .orElseThrow(() -> new IllegalArgumentException("未找到备份任务。"));
        RestoreJobEntity entity = new RestoreJobEntity();
        entity.setBackupJobId(backupJobId);
        entity.setDryRun(dryRun);
        entity.setStatus(dryRun ? "DRY_RUN_OK" : "COMPLETED");
        entity.setSummaryJson(backup.getSummaryJson());
        RestoreJobEntity saved = restoreJobRepository.save(entity);
        if (!dryRun) {
            applySnapshot(backup);
        }
        opsAuditService.record("PLATFORM", "RESTORE_CREATED", "restore_job", String.valueOf(saved.getId()), saved.getSummaryJson());
        return toRestoreResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ReleaseArtifactResponse> listReleaseArtifacts() {
        return releaseArtifactRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toReleaseResponse).toList();
    }

    public ReleaseArtifactResponse saveReleaseArtifact(Long id, ReleaseArtifactRequest request) {
        ReleaseArtifactEntity entity = id == null
                ? new ReleaseArtifactEntity()
                : releaseArtifactRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到发布制品。"));
        entity.setVersionName(request.versionName());
        entity.setArtifactRef(request.artifactRef());
        entity.setActive(request.active() != null && request.active());
        return toReleaseResponse(releaseArtifactRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<UpgradeJobResponse> listUpgrades() {
        return upgradeJobRepository.findTop100ByOrderByCreatedAtDesc().stream().map(this::toUpgradeResponse).toList();
    }

    public UpgradeJobResponse createUpgrade(UpgradeJobRequest request) {
        if (!Boolean.TRUE.equals(request.confirm())) {
            throw new IllegalArgumentException("升级必须显式确认。");
        }
        ReleaseArtifactEntity target = releaseArtifactRepository.findById(request.targetReleaseArtifactId())
                .orElseThrow(() -> new IllegalArgumentException("未找到目标发布制品。"));
        BackupJobResponse backup = createBackup(false);

        UpgradeJobEntity entity = new UpgradeJobEntity();
        entity.setTargetReleaseArtifactId(target.getId());
        entity.setPreBackupJobId(backup.id());
        entity.setStatus("RUNNING");
        entity.setMessage("正在切换到 " + target.getVersionName());
        UpgradeJobEntity saved = upgradeJobRepository.save(entity);

        InstallationStateEntity state = getOrCreateInstallationState();
        Long previousReleaseId = state.getActiveReleaseArtifactId();
        try {
            if (Boolean.TRUE.equals(request.forceFailure())) {
                throw new IllegalStateException("模拟升级失败。");
            }
            state.setActiveReleaseArtifactId(target.getId());
            state.setStatus("READY");
            state.setLastHealthCheckAt(Instant.now());
            installationStateRepository.save(state);
            target.setActive(true);
            releaseArtifactRepository.save(target);
            saved.setStatus("COMPLETED");
            saved.setMessage("升级成功。");
            opsAuditService.record("PLATFORM", "UPGRADE_COMPLETED", "upgrade_job", String.valueOf(saved.getId()), saved.getMessage());
        } catch (Exception exception) {
            saved.setStatus("FAILED");
            saved.setMessage(exception.getMessage());
            saved.setAutoRollbackTriggered(true);
            createRollback(saved.getId(), previousReleaseId, backup.id(), true);
        }
        return toUpgradeResponse(upgradeJobRepository.save(saved));
    }

    @Transactional(readOnly = true)
    public List<RollbackJobResponse> listRollbacks() {
        return rollbackJobRepository.findTop100ByOrderByCreatedAtDesc().stream().map(this::toRollbackResponse).toList();
    }

    public RollbackJobResponse createRollback(Long upgradeJobId, Long releaseArtifactId, Long backupJobId, boolean autoTriggered) {
        BackupJobEntity backup = backupJobRepository.findById(backupJobId)
                .orElseThrow(() -> new IllegalArgumentException("缺少升级前快照，禁止自动数据库回滚。"));
        RollbackJobEntity entity = new RollbackJobEntity();
        entity.setUpgradeJobId(upgradeJobId);
        entity.setReleaseArtifactId(releaseArtifactId);
        entity.setBackupJobId(backupJobId);
        entity.setStatus("RUNNING");
        entity.setMessage(autoTriggered ? "升级失败，自动回滚中。" : "手动回滚中。");
        RollbackJobEntity saved = rollbackJobRepository.save(entity);

        InstallationStateEntity state = getOrCreateInstallationState();
        state.setActiveReleaseArtifactId(releaseArtifactId);
        state.setStatus("READY");
        state.setLastHealthCheckAt(Instant.now());
        installationStateRepository.save(state);
        applySnapshot(backup);

        saved.setStatus("COMPLETED");
        saved.setMessage("已完成应用与数据库快照回滚。");
        opsAuditService.record("PLATFORM", "ROLLBACK_COMPLETED", "rollback_job", String.valueOf(saved.getId()), saved.getMessage());
        return toRollbackResponse(rollbackJobRepository.save(saved));
    }

    private InstallationStateEntity getOrCreateInstallationState() {
        return installationStateRepository.findAll().stream().findFirst().orElseGet(() -> {
            InstallationStateEntity entity = new InstallationStateEntity();
            entity.setStatus("NOT_BOOTSTRAPPED");
            entity.setBootstrapCompleted(false);
            return installationStateRepository.save(entity);
        });
    }

    private String writeSnapshot(String prefix, String content) {
        try {
            Path root = Path.of(gatewayProperties.getStorage().getFileRoot()).toAbsolutePath().resolve("operations");
            Files.createDirectories(root);
            Path file = root.resolve(prefix + "-" + Instant.now().toEpochMilli() + ".json");
            Files.writeString(file, content);
            return file.toAbsolutePath().toString();
        } catch (IOException exception) {
            throw new IllegalStateException("写入快照失败。", exception);
        }
    }

    private void applySnapshot(BackupJobEntity backup) {
        try {
            Map<?, ?> snapshot = objectMapper.readValue(backup.getSummaryJson(), Map.class);
            // 第一版只恢复 metadata 级配置快照，不做反向 migration。
            InstallationStateEntity state = getOrCreateInstallationState();
            state.setMetadataJson(writeJson(snapshot.get("installationState")));
            installationStateRepository.save(state);
        } catch (JacksonException exception) {
            throw new IllegalStateException("恢复快照失败。", exception);
        }
    }

    private Map<String, Object> snapshotSummary() {
        InstallationStateEntity state = getOrCreateInstallationState();
        return Map.of(
                "installationState", Map.of(
                        "status", state.getStatus(),
                        "activeReleaseArtifactId", state.getActiveReleaseArtifactId(),
                        "bootstrapCompleted", state.isBootstrapCompleted()
                ),
                "systemSettingCount", systemSettingRepository.findAll().size()
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化失败。", exception);
        }
    }

    private InstallationStateResponse toInstallResponse(InstallationStateEntity entity) {
        return new InstallationStateResponse(entity.getId(), entity.getStatus(), entity.getActiveReleaseArtifactId(), entity.isBootstrapCompleted(),
                entity.getLastHealthCheckAt(), entity.getMetadataJson(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private BackupJobResponse toBackupResponse(BackupJobEntity entity) {
        return new BackupJobResponse(entity.getId(), entity.getStatus(), entity.isDryRun(), entity.getSnapshotPath(), entity.getSummaryJson(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private RestoreJobResponse toRestoreResponse(RestoreJobEntity entity) {
        return new RestoreJobResponse(entity.getId(), entity.getBackupJobId(), entity.getStatus(), entity.isDryRun(), entity.getSummaryJson(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private ReleaseArtifactResponse toReleaseResponse(ReleaseArtifactEntity entity) {
        return new ReleaseArtifactResponse(entity.getId(), entity.getVersionName(), entity.getArtifactRef(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private UpgradeJobResponse toUpgradeResponse(UpgradeJobEntity entity) {
        return new UpgradeJobResponse(entity.getId(), entity.getTargetReleaseArtifactId(), entity.getPreBackupJobId(), entity.getStatus(), entity.getMessage(), entity.isAutoRollbackTriggered(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private RollbackJobResponse toRollbackResponse(RollbackJobEntity entity) {
        return new RollbackJobResponse(entity.getId(), entity.getUpgradeJobId(), entity.getReleaseArtifactId(), entity.getBackupJobId(), entity.getStatus(), entity.getMessage(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
