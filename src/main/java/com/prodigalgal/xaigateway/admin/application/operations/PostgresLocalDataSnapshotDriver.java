package com.prodigalgal.xaigateway.admin.application.operations;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PostgresLocalDataSnapshotDriver implements DataSnapshotDriver {

    private final Environment environment;
    private final CommandRunner commandRunner;

    public PostgresLocalDataSnapshotDriver(
            Environment environment,
            CommandRunner commandRunner) {
        this.environment = environment;
        this.commandRunner = commandRunner;
    }

    @Override
    public AvailabilityCheck checkAvailability() {
        CommandRunner.CommandResult dumpResult = commandRunner.run(List.of("pg_dump", "--version"), null, Map.of());
        if (dumpResult.exitCode() != 0) {
            return new AvailabilityCheck(false, "缺少 pg_dump，可执行数据快照。");
        }
        CommandRunner.CommandResult restoreResult = commandRunner.run(List.of("pg_restore", "--version"), null, Map.of());
        if (restoreResult.exitCode() != 0) {
            return new AvailabilityCheck(false, "缺少 pg_restore，可执行数据恢复。");
        }
        return new AvailabilityCheck(true, "PostgreSQL 快照工具可用。");
    }

    @Override
    public void dumpDatabase(Path outputFile) {
        DatabaseTarget target = parseTarget();
        List<String> command = new ArrayList<>();
        command.add("pg_dump");
        command.add("-Fc");
        command.add("-f");
        command.add(outputFile.toAbsolutePath().toString());
        command.add("--host");
        command.add(target.host());
        command.add("--port");
        command.add(String.valueOf(target.port()));
        command.add("--username");
        command.add(target.username());
        command.add(target.database());
        runCommand(command, target.password(), outputFile.getParent(), "导出 PostgreSQL 快照失败。");
    }

    @Override
    public void verifyDatabaseDump(Path dumpFile) {
        List<String> command = List.of("pg_restore", "--list", dumpFile.toAbsolutePath().toString());
        runCommand(command, null, dumpFile.getParent(), "校验 PostgreSQL 快照失败。");
    }

    @Override
    public void restoreDatabaseDump(Path dumpFile) {
        DatabaseTarget target = parseTarget();
        List<String> command = new ArrayList<>();
        command.add("pg_restore");
        command.add("--clean");
        command.add("--if-exists");
        command.add("--no-owner");
        command.add("--no-privileges");
        command.add("--host");
        command.add(target.host());
        command.add("--port");
        command.add(String.valueOf(target.port()));
        command.add("--username");
        command.add(target.username());
        command.add("--dbname");
        command.add(target.database());
        command.add(dumpFile.toAbsolutePath().toString());
        runCommand(command, target.password(), dumpFile.getParent(), "恢复 PostgreSQL 快照失败。");
    }

    private void runCommand(List<String> command, String password, Path workingDirectory, String errorMessage) {
        Map<String, String> environment = password == null || password.isBlank()
                ? Map.of()
                : Map.of("PGPASSWORD", password);
        CommandRunner.CommandResult result = commandRunner.run(command, workingDirectory, environment);
        if (result.exitCode() != 0) {
            throw new IllegalStateException(errorMessage + " " + result.stderr());
        }
    }

    private DatabaseTarget parseTarget() {
        String url = environment.getProperty("spring.datasource.url");
        String jdbcPrefix = "jdbc:postgresql://";
        if (url == null || !url.startsWith(jdbcPrefix)) {
            throw new IllegalStateException("当前 data snapshot 仅支持 PostgreSQL JDBC URL。");
        }
        try {
            URI uri = new URI("postgresql://" + url.substring(jdbcPrefix.length()));
            String path = uri.getPath();
            String database = path == null ? null : path.replaceFirst("^/", "");
            if (database == null || database.isBlank()) {
                throw new IllegalStateException("缺少 PostgreSQL database 名称。");
            }
            return new DatabaseTarget(
                    uri.getHost() == null ? "localhost" : uri.getHost(),
                    uri.getPort() <= 0 ? 5432 : uri.getPort(),
                    database,
                    environment.getProperty("spring.datasource.username"),
                    environment.getProperty("spring.datasource.password")
            );
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("解析 PostgreSQL 连接信息失败。", exception);
        }
    }

    private record DatabaseTarget(
            String host,
            int port,
            String database,
            String username,
            String password
    ) {
    }
}
