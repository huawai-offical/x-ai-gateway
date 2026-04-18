package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.application.operations.RuntimeStateSnapshotDriver;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RuntimeStateSnapshotService {

    private final GatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper;
    private final RuntimeStateSnapshotDriver runtimeStateSnapshotDriver;

    public RuntimeStateSnapshotService(
            GatewayProperties gatewayProperties,
            ObjectMapper objectMapper,
            RuntimeStateSnapshotDriver runtimeStateSnapshotDriver) {
        this.gatewayProperties = gatewayProperties;
        this.objectMapper = objectMapper;
        this.runtimeStateSnapshotDriver = runtimeStateSnapshotDriver;
    }

    public SnapshotResult createSnapshot(String checkpointName) {
        String keyPrefix = gatewayProperties.getCache().getKeyPrefix() + ":";
        RuntimeStateSnapshotDriver.RuntimeStateSnapshot snapshot = runtimeStateSnapshotDriver.capture(keyPrefix);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("checkpointName", checkpointName);
        manifest.put("capturedAt", Instant.now().toString());
        manifest.put("keyPrefix", snapshot.keyPrefix());
        manifest.put("entries", snapshot.entries());
        Path manifestPath = snapshotRoot().resolve(checkpointName + "-runtime.json");
        writeJson(manifestPath, manifest);
        return new SnapshotResult(manifestPath.toAbsolutePath().toString(), writeJson(manifest));
    }

    public VerificationResult verifySnapshot(String manifestPath) {
        RuntimeStateSnapshotDriver.RuntimeStateSnapshot snapshot = readSnapshot(manifestPath);
        RuntimeStateSnapshotDriver.VerificationResult result = runtimeStateSnapshotDriver.verify(snapshot);
        return new VerificationResult(result.success(), result.message());
    }

    public void restoreSnapshot(String manifestPath) {
        runtimeStateSnapshotDriver.restore(readSnapshot(manifestPath));
    }

    private RuntimeStateSnapshotDriver.RuntimeStateSnapshot readSnapshot(String manifestPath) {
        try {
            Map<?, ?> manifest = objectMapper.readValue(Files.readString(Path.of(manifestPath)), Map.class);
            return objectMapper.convertValue(Map.of(
                    "keyPrefix", manifest.get("keyPrefix"),
                    "entries", manifest.get("entries")
            ), RuntimeStateSnapshotDriver.RuntimeStateSnapshot.class);
        } catch (IOException exception) {
            throw new IllegalStateException("读取 runtime state snapshot 失败。", exception);
        }
    }

    private void writeJson(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, writeJson(value));
        } catch (IOException exception) {
            throw new IllegalStateException("写入 runtime state snapshot 失败。", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化 runtime state snapshot 失败。", exception);
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
