package com.prodigalgal.xaigateway.admin.application.operations;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
public class ProcessCommandRunner implements CommandRunner {

    @Override
    public CommandResult run(List<String> command, Path workingDirectory, Map<String, String> environment) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }
        if (environment != null && !environment.isEmpty()) {
            processBuilder.environment().putAll(environment);
        }
        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());
            return new CommandResult(exitCode, stdout, stderr);
        } catch (IOException exception) {
            return new CommandResult(-1, "", exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "", exception.getMessage());
        }
    }
}
