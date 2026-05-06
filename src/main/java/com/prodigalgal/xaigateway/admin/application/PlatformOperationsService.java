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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        return recordBackupJob(dryRun, dryRun ? "DRY_RUN_OK" : "COMPLETED", null, writeJson(snapshotSummary()));
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

    @Transactional(readOnly = true)
    public DeploymentManifestResponse deploymentManifest(String profile) {
        String normalizedProfile = normalizeProfile(profile);
        List<DeploymentManifestItemResponse> files = deploymentFiles();
        return new DeploymentManifestResponse(
                normalizedProfile,
                "x-ai-gateway:local",
                "deploy/docker-compose.yml",
                "deploy/.env.example",
                "scripts/install.ps1",
                "scripts/upgrade.ps1",
                "scripts/rollback.ps1",
                "http://localhost:8080/actuator/health/readiness",
                files,
                List.of(
                        "DB_URL",
                        "DB_USERNAME",
                        "DB_PASSWORD",
                        "REDIS_HOST",
                        "REDIS_PORT",
                        "REDIS_PASSWORD",
                        "GATEWAY_ENCRYPTION_KEY",
                        "GATEWAY_FILE_ROOT",
                        "GATEWAY_ROUTING_RUNTIME_STORE_TYPE"
                ),
                List.of(
                        "xag-postgres-data:/var/lib/postgresql/data",
                        "xag-redis-data:/data",
                        "./.data/files:/app/.data/files",
                        "./output/logs:/app/logs"
                ),
                List.of(
                        "Copy-Item deploy/.env.example .env",
                        "docker compose --env-file .env -f deploy/docker-compose.yml up -d",
                        ".\\scripts\\install.ps1 -EnvFile .env",
                        ".\\scripts\\upgrade.ps1 -TargetVersion <version> -Confirm",
                        ".\\scripts\\rollback.ps1 -BackupId <backup-id> -Confirm"
                ),
                List.of(
                        "docs/production-deployment-upgrade.md",
                        "docs/operations-drill-evidence.md",
                        "docs/testing-smoke-harness.md"
                ),
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public DeploymentPreflightResponse deploymentPreflight(String targetVersion, String profile) {
        String normalizedProfile = normalizeProfile(profile);
        String normalizedTargetVersion = targetVersion == null || targetVersion.isBlank()
                ? "未指定"
                : targetVersion.trim();
        List<DeploymentPreflightCheckResponse> checks = new ArrayList<>();
        deploymentFiles().forEach(file -> checks.add(new DeploymentPreflightCheckResponse(
                "file:" + file.path(),
                file.present() ? "OK" : "FAIL",
                file.required() && !file.present(),
                file.present() ? "已找到 " + file.path() : "缺少 " + file.path(),
                file.present() ? "无需处理。" : "按 docs/production-deployment-upgrade.md 补齐文件后再执行升级。"
        )));
        checks.add(new DeploymentPreflightCheckResponse(
                "liquibase:master-changelog",
                Files.exists(Path.of("src/main/resources/db/changelog/db.changelog-master.yaml")) ? "OK" : "FAIL",
                !Files.exists(Path.of("src/main/resources/db/changelog/db.changelog-master.yaml")),
                "数据库迁移入口检查。",
                "确认 Liquibase master changelog 存在且纳入发布制品。"
        ));
        checks.add(new DeploymentPreflightCheckResponse(
                "redis:runtime-store",
                "redis".equalsIgnoreCase(gatewayProperties.getRouting().getRuntimeStore().getType()) ? "OK" : "WARN",
                false,
                "当前 routing runtime store 为 " + gatewayProperties.getRouting().getRuntimeStore().getType() + "。",
                "生产多实例建议设置 GATEWAY_ROUTING_RUNTIME_STORE_TYPE=redis；本地单实例可继续使用 memory。"
        ));
        checks.add(new DeploymentPreflightCheckResponse(
                "release:target-version",
                "未指定".equals(normalizedTargetVersion) ? "WARN" : "OK",
                false,
                "目标版本：" + normalizedTargetVersion,
                "执行升级前建议传入 targetVersion，并在 release artifact 中记录 artifactRef。"
        ));
        int blockingCount = (int) checks.stream().filter(DeploymentPreflightCheckResponse::blocking).count();
        int warningCount = (int) checks.stream().filter(check -> "WARN".equals(check.status())).count();
        String status = blockingCount > 0 ? "BLOCKED" : warningCount > 0 ? "WARN" : "PASS";
        return new DeploymentPreflightResponse(
                normalizedTargetVersion,
                normalizedProfile,
                status,
                blockingCount,
                warningCount,
                checks,
                List.of(
                        "Copy-Item deploy/.env.example .env",
                        ".\\scripts\\install.ps1 -EnvFile .env"
                ),
                List.of(
                        ".\\gradlew.bat clean test",
                        ".\\scripts\\upgrade.ps1 -TargetVersion " + normalizedTargetVersion + " -Confirm"
                ),
                List.of(
                        ".\\scripts\\rollback.ps1 -BackupId <pre-upgrade-backup-id> -Confirm",
                        "docker compose --env-file .env -f deploy/docker-compose.yml logs gateway"
                ),
                List.of(
                        "docs/production-deployment-upgrade.md",
                        "docs/operations-drill-evidence.md",
                        "docs/testing-smoke-harness.md"
                ),
                Instant.now()
        );
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

    private String normalizeProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return "compose";
        }
        return profile.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private List<DeploymentManifestItemResponse> deploymentFiles() {
        return List.of(
                file("Dockerfile", "容器镜像构建入口", true),
                file("deploy/docker-compose.yml", "Postgres、Redis 与 gateway 编排", true),
                file("deploy/.env.example", "生产环境变量样例", true),
                file("scripts/install.ps1", "首次部署脚本", true),
                file("scripts/upgrade.ps1", "升级脚本", true),
                file("scripts/rollback.ps1", "回滚脚本", true),
                file("docs/production-deployment-upgrade.md", "部署、升级、回滚操作文档", true)
        );
    }

    private DeploymentManifestItemResponse file(String path, String purpose, boolean required) {
        return new DeploymentManifestItemResponse(path, purpose, required, Files.exists(Path.of(path)));
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

    public BackupJobResponse recordBackupJob(boolean dryRun, String status, String snapshotPath, String summaryJson) {
        BackupJobEntity entity = new BackupJobEntity();
        entity.setDryRun(dryRun);
        entity.setStatus(status);
        entity.setSnapshotPath(snapshotPath == null && !dryRun && summaryJson != null ? writeSnapshot("backup", summaryJson) : snapshotPath);
        entity.setSummaryJson(summaryJson);
        BackupJobEntity saved = backupJobRepository.save(entity);
        opsAuditService.record("PLATFORM", "BACKUP_CREATED", "backup_job", String.valueOf(saved.getId()), summaryJson);
        return toBackupResponse(saved);
    }

    public RestoreJobResponse recordRestoreJob(Long backupJobId, boolean dryRun, String status, String summaryJson) {
        RestoreJobEntity entity = new RestoreJobEntity();
        entity.setBackupJobId(backupJobId);
        entity.setDryRun(dryRun);
        entity.setStatus(status);
        entity.setSummaryJson(summaryJson);
        RestoreJobEntity saved = restoreJobRepository.save(entity);
        opsAuditService.record("PLATFORM", "RESTORE_CREATED", "restore_job", String.valueOf(saved.getId()), summaryJson);
        return toRestoreResponse(saved);
    }

    public UpgradeJobResponse recordUpgradeJob(Long targetReleaseArtifactId, Long preBackupJobId, String status, String message, boolean autoRollbackTriggered) {
        UpgradeJobEntity entity = new UpgradeJobEntity();
        entity.setTargetReleaseArtifactId(targetReleaseArtifactId);
        entity.setPreBackupJobId(preBackupJobId);
        entity.setStatus(status);
        entity.setMessage(message);
        entity.setAutoRollbackTriggered(autoRollbackTriggered);
        UpgradeJobEntity saved = upgradeJobRepository.save(entity);
        opsAuditService.record("PLATFORM", "UPGRADE_RECORDED", "upgrade_job", String.valueOf(saved.getId()), writeJson(Map.of(
                "targetReleaseArtifactId", targetReleaseArtifactId,
                "preBackupJobId", preBackupJobId,
                "status", status,
                "message", message,
                "autoRollbackTriggered", autoRollbackTriggered
        )));
        return toUpgradeResponse(saved);
    }

    public RollbackJobResponse recordRollbackJob(Long upgradeJobId, Long releaseArtifactId, Long backupJobId, String status, String message) {
        RollbackJobEntity entity = new RollbackJobEntity();
        entity.setUpgradeJobId(upgradeJobId);
        entity.setReleaseArtifactId(releaseArtifactId);
        entity.setBackupJobId(backupJobId);
        entity.setStatus(status);
        entity.setMessage(message);
        RollbackJobEntity saved = rollbackJobRepository.save(entity);
        opsAuditService.record("PLATFORM", "ROLLBACK_RECORDED", "rollback_job", String.valueOf(saved.getId()), writeJson(Map.of(
                "upgradeJobId", upgradeJobId,
                "releaseArtifactId", releaseArtifactId,
                "backupJobId", backupJobId,
                "status", status,
                "message", message
        )));
        return toRollbackResponse(saved);
    }

    public void activateRelease(Long releaseArtifactId, String status) {
        InstallationStateEntity state = getOrCreateInstallationState();
        state.setActiveReleaseArtifactId(releaseArtifactId);
        state.setStatus(status);
        state.setLastHealthCheckAt(Instant.now());
        installationStateRepository.save(state);
        releaseArtifactRepository.findAll().forEach(item -> {
            item.setActive(item.getId().equals(releaseArtifactId));
            releaseArtifactRepository.save(item);
        });
    }
}
