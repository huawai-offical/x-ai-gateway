package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.MaintenanceRunRequest;
import com.prodigalgal.xaigateway.admin.api.MaintenanceRunResponse;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.MaintenanceRunEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.MaintenanceRunRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class MaintenanceRunService {

    private final MaintenanceRunRepository repository;
    private final GatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper;
    private final OpsAuditService opsAuditService;

    public MaintenanceRunService(
            MaintenanceRunRepository repository,
            GatewayProperties gatewayProperties,
            ObjectMapper objectMapper,
            OpsAuditService opsAuditService) {
        this.repository = repository;
        this.gatewayProperties = gatewayProperties;
        this.objectMapper = objectMapper;
        this.opsAuditService = opsAuditService;
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRunResponse> list() {
        return repository.findTop100ByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MaintenanceRunResponse get(Long id) {
        return toResponse(getRequired(id));
    }

    public MaintenanceRunResponse execute(MaintenanceRunRequest request) {
        String runType = normalizeRunType(request.runType());
        boolean dryRun = request.dryRun() == null || request.dryRun();
        boolean confirmRequired = requiresConfirm(runType, dryRun);
        boolean confirmed = Boolean.TRUE.equals(request.confirm());
        if (confirmRequired && !confirmed) {
            throw new IllegalArgumentException("该维护动作需要显式 confirm=true。");
        }

        Instant startedAt = Instant.now();
        MaintenanceRunEntity entity = new MaintenanceRunEntity();
        entity.setRunType(runType);
        entity.setDryRun(dryRun);
        entity.setConfirmRequired(confirmRequired);
        entity.setConfirmed(confirmed);
        entity.setActor(request.actor() == null || request.actor().isBlank() ? "console" : request.actor().trim());
        entity.setSourceRef(blankToNull(request.sourceRef()));
        entity.setStartedAt(startedAt);
        entity.setStatus("RUNNING");

        try {
            RunArtifact artifact = buildArtifact(runType, dryRun, request.detailJson());
            entity.setArtifactPath(artifact.path());
            entity.setArtifactChecksum(artifact.checksum());
            entity.setDetailJson(artifact.detailJson());
            entity.setStatus("COMPLETED");
        } catch (Exception exception) {
            entity.setStatus("FAILED");
            entity.setErrorMessage(exception.getMessage());
            entity.setDetailJson(writeJson(Map.of(
                    "runType", runType,
                    "dryRun", dryRun,
                    "error", exception.getMessage()
            )));
        }

        Instant completedAt = Instant.now();
        entity.setCompletedAt(completedAt);
        entity.setDurationMs(Duration.between(startedAt, completedAt).toMillis());
        MaintenanceRunEntity saved = repository.save(entity);
        opsAuditService.record("MAINTENANCE", "RUN_" + saved.getRunType(), "maintenance_run", String.valueOf(saved.getId()), saved.getDetailJson());
        return toResponse(saved);
    }

    private RunArtifact buildArtifact(String runType, boolean dryRun, String requestDetailJson) throws Exception {
        List<Map<String, Object>> checks = runChecks(runType, dryRun, requestDetailJson);
        Map<String, Object> detail = Map.of(
                "runType", runType,
                "dryRun", dryRun,
                "mode", modeFor(runType),
                "requestDetail", requestDetailJson == null || requestDetailJson.isBlank() ? "{}" : requestDetailJson,
                "checks", checks,
                "summary", summarizeChecks(checks),
                "generatedAt", Instant.now().toString()
        );
        String detailJson = writeJson(detail);
        if ("PRECHECK".equals(runType) || "UPGRADE_CHECK".equals(runType) || "ROLLBACK_PLAN".equals(runType) || dryRun) {
            return new RunArtifact(null, sha256(detailJson), detailJson);
        }
        Path root = Path.of(gatewayProperties.getStorage().getFileRoot()).toAbsolutePath().resolve("maintenance-runs");
        Files.createDirectories(root);
        Path file = root.resolve(runType.toLowerCase(Locale.ROOT) + "-" + Instant.now().toEpochMilli() + ".json");
        Files.writeString(file, detailJson, StandardCharsets.UTF_8);
        return new RunArtifact(file.toAbsolutePath().toString(), sha256(Files.readString(file, StandardCharsets.UTF_8)), detailJson);
    }

    private String modeFor(String runType) {
        return switch (runType) {
            case "PRECHECK" -> "readiness_probe";
            case "BACKUP" -> "snapshot_artifact";
            case "RESTORE_DRY_RUN" -> "restore_plan_validation";
            case "UPGRADE_CHECK" -> "release_compatibility_check";
            case "ROLLBACK_PLAN" -> "rollback_strategy_preview";
            default -> "maintenance";
        };
    }

    private List<Map<String, Object>> runChecks(String runType, boolean dryRun, String requestDetailJson) {
        List<Map<String, Object>> checks = new ArrayList<>();
        Path fileRoot = Path.of(gatewayProperties.getStorage().getFileRoot()).toAbsolutePath();
        checks.add(check("storageRootConfigured", fileRoot.toString(), true, "文件根目录已解析。"));
        checks.add(check("storageParentWritable", fileRoot.getParent() == null ? fileRoot.toString() : fileRoot.getParent().toString(),
                Files.isWritable(fileRoot.getParent() == null ? fileRoot : fileRoot.getParent()),
                "维护产物父目录需要可写。"));
        checks.add(check("requestDetailJson", "detailJson", isJsonLike(requestDetailJson), "detailJson 应为空或 JSON 对象/数组。"));
        checks.add(check("safeExecutionMode", dryRun ? "dry-run" : "confirmed", dryRun || !"RESTORE_DRY_RUN".equals(runType),
                "危险动作默认 dry-run；非 dry-run 必须走 confirm。"));
        if ("UPGRADE_CHECK".equals(runType)) {
            checks.add(check("releaseCompatibility", "upgrade-check", requestDetailJson != null && requestDetailJson.contains("targetVersion"),
                    "UPGRADE_CHECK 建议提供 targetVersion 以便前端展示兼容性结论。"));
        }
        if ("ROLLBACK_PLAN".equals(runType)) {
            checks.add(check("rollbackSource", "rollback-plan", requestDetailJson != null && requestDetailJson.contains("rollback"),
                    "ROLLBACK_PLAN 建议提供 rollback 目标或 sourceRef。"));
        }
        return checks;
    }

    private Map<String, Object> check(String name, String target, boolean passed, String message) {
        return Map.of(
                "name", name,
                "target", target,
                "status", passed ? "OK" : "FAILED",
                "message", message
        );
    }

    private Map<String, Object> summarizeChecks(List<Map<String, Object>> checks) {
        long failed = checks.stream().filter(check -> "FAILED".equals(check.get("status"))).count();
        return Map.of(
                "total", checks.size(),
                "failed", failed,
                "status", failed == 0 ? "READY" : "ATTENTION_REQUIRED"
        );
    }

    private boolean isJsonLike(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return true;
        }
        String value = detailJson.trim();
        return (value.startsWith("{") && value.endsWith("}")) || (value.startsWith("[") && value.endsWith("]"));
    }

    private boolean requiresConfirm(String runType, boolean dryRun) {
        return !dryRun && ("BACKUP".equals(runType) || "RESTORE_DRY_RUN".equals(runType));
    }

    private String normalizeRunType(String runType) {
        if (runType == null || runType.isBlank()) {
            return "PRECHECK";
        }
        String value = runType.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (value) {
            case "PRECHECK", "BACKUP", "RESTORE_DRY_RUN", "UPGRADE_CHECK", "ROLLBACK_PLAN" -> value;
            default -> throw new IllegalArgumentException("不支持的维护动作类型。");
        };
    }

    private MaintenanceRunEntity getRequired(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到维护运行记录。"));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("维护运行详情序列化失败。", exception);
        }
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private MaintenanceRunResponse toResponse(MaintenanceRunEntity entity) {
        return new MaintenanceRunResponse(
                entity.getId(),
                entity.getRunType(),
                entity.getStatus(),
                entity.isDryRun(),
                entity.isConfirmRequired(),
                entity.isConfirmed(),
                entity.getArtifactPath(),
                entity.getArtifactChecksum(),
                entity.getActor(),
                entity.getSourceRef(),
                entity.getDurationMs(),
                entity.getDetailJson(),
                entity.getErrorMessage(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private record RunArtifact(String path, String checksum, String detailJson) {
    }
}
