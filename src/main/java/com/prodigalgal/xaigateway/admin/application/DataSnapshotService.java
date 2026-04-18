package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.application.operations.DataSnapshotDriver;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class DataSnapshotService {

    private final GatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper;
    private final DataSnapshotDriver dataSnapshotDriver;

    public DataSnapshotService(
            GatewayProperties gatewayProperties,
            ObjectMapper objectMapper,
            DataSnapshotDriver dataSnapshotDriver) {
        this.gatewayProperties = gatewayProperties;
        this.objectMapper = objectMapper;
        this.dataSnapshotDriver = dataSnapshotDriver;
    }

    public DataSnapshotDriver.AvailabilityCheck availabilityCheck() {
        return dataSnapshotDriver.checkAvailability();
    }

    public SnapshotResult createSnapshot(String checkpointName) {
        try {
            Path root = snapshotRoot();
            Files.createDirectories(root);
            Path dumpPath = root.resolve(checkpointName + "-postgres.dump");
            Path fileArchivePath = root.resolve(checkpointName + "-files.zip");
            dataSnapshotDriver.dumpDatabase(dumpPath);
            zipDirectory(fileRoot(), fileArchivePath);
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("checkpointName", checkpointName);
            manifest.put("capturedAt", Instant.now().toString());
            manifest.put("postgresDumpPath", dumpPath.toAbsolutePath().toString());
            manifest.put("fileArchivePath", fileArchivePath.toAbsolutePath().toString());
            Path manifestPath = root.resolve(checkpointName + "-data.json");
            writeJson(manifestPath, manifest);
            return new SnapshotResult(manifestPath.toAbsolutePath().toString(), writeJson(manifest));
        } catch (IOException exception) {
            throw new IllegalStateException("创建 data snapshot 失败。", exception);
        }
    }

    public VerificationResult verifySnapshot(String manifestPath) {
        try {
            Map<?, ?> manifest = objectMapper.readValue(Files.readString(Path.of(manifestPath)), Map.class);
            Path dumpPath = Path.of(String.valueOf(manifest.get("postgresDumpPath")));
            Path archivePath = Path.of(String.valueOf(manifest.get("fileArchivePath")));
            if (!Files.exists(dumpPath)) {
                return new VerificationResult(false, "缺少 PostgreSQL dump 文件。");
            }
            if (!Files.exists(archivePath)) {
                return new VerificationResult(false, "缺少文件根目录归档。");
            }
            dataSnapshotDriver.verifyDatabaseDump(dumpPath);
            return new VerificationResult(true, "data snapshot 可用。");
        } catch (IOException exception) {
            return new VerificationResult(false, "读取 data snapshot manifest 失败。");
        }
    }

    public void restoreSnapshot(String manifestPath) {
        try {
            Map<?, ?> manifest = objectMapper.readValue(Files.readString(Path.of(manifestPath)), Map.class);
            Path dumpPath = Path.of(String.valueOf(manifest.get("postgresDumpPath")));
            Path archivePath = Path.of(String.valueOf(manifest.get("fileArchivePath")));
            dataSnapshotDriver.restoreDatabaseDump(dumpPath);
            unzipDirectory(archivePath, fileRoot());
        } catch (IOException exception) {
            throw new IllegalStateException("恢复 data snapshot 失败。", exception);
        }
    }

    private void zipDirectory(Path sourceRoot, Path targetZip) throws IOException {
        Files.createDirectories(targetZip.getParent());
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(targetZip))) {
            if (!Files.exists(sourceRoot)) {
                return;
            }
            Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relative = sourceRoot.relativize(file);
                    zipOutputStream.putNextEntry(new ZipEntry(relative.toString().replace('\\', '/')));
                    Files.copy(file, zipOutputStream);
                    zipOutputStream.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private void unzipDirectory(Path zipPath, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path outputPath = targetDir.resolve(entry.getName()).normalize();
                if (!outputPath.startsWith(targetDir)) {
                    throw new IllegalStateException("检测到非法归档路径。");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(zipInputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private void writeJson(Path path, Object value) {
        try {
            Files.writeString(path, writeJson(value), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("写入 data snapshot manifest 失败。", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化 data snapshot manifest 失败。", exception);
        }
    }

    private Path snapshotRoot() {
        return fileRoot().resolve("operations").resolve("change-plans");
    }

    private Path fileRoot() {
        return Path.of(gatewayProperties.getStorage().getFileRoot()).toAbsolutePath();
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
