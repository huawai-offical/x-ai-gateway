package com.prodigalgal.xaigateway.admin.application.operations;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface CommandRunner {

    CommandResult run(List<String> command, Path workingDirectory, Map<String, String> environment);

    record CommandResult(
            int exitCode,
            String stdout,
            String stderr
    ) {
    }
}
