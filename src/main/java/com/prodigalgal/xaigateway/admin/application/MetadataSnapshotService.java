package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.*;
import com.prodigalgal.xaigateway.infra.persistence.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class MetadataSnapshotService {

    private final GatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper;
    private final InstallationStateRepository installationStateRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final ReleaseArtifactRepository releaseArtifactRepository;
    private final RouteGuardPolicyRepository routeGuardPolicyRepository;
    private final AutoActionRuleRepository autoActionRuleRepository;
    private final SloPolicyRepository sloPolicyRepository;
    private final AlertSilenceRepository alertSilenceRepository;

    public MetadataSnapshotService(
            GatewayProperties gatewayProperties,
            ObjectMapper objectMapper,
            InstallationStateRepository installationStateRepository,
            SystemSettingRepository systemSettingRepository,
            ReleaseArtifactRepository releaseArtifactRepository,
            RouteGuardPolicyRepository routeGuardPolicyRepository,
            AutoActionRuleRepository autoActionRuleRepository,
            SloPolicyRepository sloPolicyRepository,
            AlertSilenceRepository alertSilenceRepository) {
        this.gatewayProperties = gatewayProperties;
        this.objectMapper = objectMapper;
        this.installationStateRepository = installationStateRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.releaseArtifactRepository = releaseArtifactRepository;
        this.routeGuardPolicyRepository = routeGuardPolicyRepository;
        this.autoActionRuleRepository = autoActionRuleRepository;
        this.sloPolicyRepository = sloPolicyRepository;
        this.alertSilenceRepository = alertSilenceRepository;
    }

    public SnapshotResult createSnapshot(String checkpointName) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("checkpointName", checkpointName);
        manifest.put("capturedAt", Instant.now().toString());
        manifest.put("installationState", installationStateRepository.findAll().stream().findFirst().map(this::toMap).orElse(null));
        manifest.put("systemSettings", systemSettingRepository.findAll().stream().map(this::toMap).toList());
        manifest.put("releaseArtifacts", releaseArtifactRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toMap).toList());
        manifest.put("routeGuardPolicies", routeGuardPolicyRepository.findAll().stream().map(this::toMap).toList());
        manifest.put("autoActionRules", autoActionRuleRepository.findAll().stream().map(this::toMap).toList());
        manifest.put("sloPolicies", sloPolicyRepository.findAll().stream().map(this::toMap).toList());
        manifest.put("alertSilences", alertSilenceRepository.findAll().stream().map(this::toMap).toList());
        Path manifestPath = snapshotRoot().resolve(checkpointName + "-metadata.json");
        writeJson(manifestPath, manifest);
        return new SnapshotResult(manifestPath.toAbsolutePath().toString(), writeJson(manifest));
    }

    public VerificationResult verifySnapshot(String manifestPath) {
        Path path = Path.of(manifestPath);
        if (!Files.exists(path)) {
            return new VerificationResult(false, "metadata snapshot 文件不存在。");
        }
        try {
            objectMapper.readValue(Files.readString(path), new TypeReference<Map<String, Object>>() {
            });
            return new VerificationResult(true, "metadata snapshot 可解析。");
        } catch (IOException exception) {
            return new VerificationResult(false, "metadata snapshot 解析失败。");
        }
    }

    public void restoreSnapshot(String manifestPath) {
        try {
            Map<String, Object> manifest = objectMapper.readValue(Files.readString(Path.of(manifestPath)), new TypeReference<Map<String, Object>>() {
            });
            installationStateRepository.deleteAll();
            systemSettingRepository.deleteAll();
            releaseArtifactRepository.deleteAll();
            routeGuardPolicyRepository.deleteAll();
            autoActionRuleRepository.deleteAll();
            sloPolicyRepository.deleteAll();
            alertSilenceRepository.deleteAll();

            InstallationStateEntity installationState = convert(manifest.get("installationState"), InstallationStateEntity.class);
            if (installationState != null) {
                installationState.setActiveReleaseArtifactId(null);
                installationState = installationStateRepository.save(installationState);
            }

            List<ReleaseArtifactEntity> releaseArtifacts = convertList(manifest.get("releaseArtifacts"), ReleaseArtifactEntity.class);
            List<ReleaseArtifactEntity> savedArtifacts = releaseArtifactRepository.saveAll(releaseArtifacts);
            if (installationState != null) {
                ReleaseArtifactEntity activeArtifact = savedArtifacts.stream()
                        .filter(ReleaseArtifactEntity::isActive)
                        .findFirst()
                        .orElse(null);
                if (activeArtifact != null) {
                    installationState.setActiveReleaseArtifactId(activeArtifact.getId());
                    installationStateRepository.save(installationState);
                }
            }
            systemSettingRepository.saveAll(convertList(manifest.get("systemSettings"), SystemSettingEntity.class));
            routeGuardPolicyRepository.saveAll(convertList(manifest.get("routeGuardPolicies"), RouteGuardPolicyEntity.class));
            autoActionRuleRepository.saveAll(convertList(manifest.get("autoActionRules"), AutoActionRuleEntity.class));
            sloPolicyRepository.saveAll(convertList(manifest.get("sloPolicies"), SloPolicyEntity.class));
            alertSilenceRepository.saveAll(convertList(manifest.get("alertSilences"), AlertSilenceEntity.class));
        } catch (IOException exception) {
            throw new IllegalStateException("恢复 metadata snapshot 失败。", exception);
        }
    }

    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {
        });
    }

    private <T> T convert(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, type);
    }

    private <T> List<T> convertList(Object value, Class<T> type) {
        if (value == null) {
            return List.of();
        }
        List<?> rawList = objectMapper.convertValue(value, new TypeReference<List<?>>() {
        });
        return rawList.stream().map(item -> objectMapper.convertValue(item, type)).toList();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化 metadata snapshot 失败。", exception);
        }
    }

    private void writeJson(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, writeJson(value));
        } catch (IOException exception) {
            throw new IllegalStateException("写入 metadata snapshot 失败。", exception);
        }
    }

    private Path snapshotRoot() {
        return Path.of(gatewayProperties.getStorage().getFileRoot()).toAbsolutePath().resolve("operations").resolve("change-plans");
    }

    public record SnapshotResult(
            String manifestPath,
            String manifestJson
    ) {
    }

    public record VerificationResult(
            boolean success,
            String message
    ) {
    }
}
