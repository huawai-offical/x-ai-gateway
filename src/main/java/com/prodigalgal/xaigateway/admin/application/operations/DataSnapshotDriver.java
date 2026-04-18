package com.prodigalgal.xaigateway.admin.application.operations;

import java.nio.file.Path;

public interface DataSnapshotDriver {

    AvailabilityCheck checkAvailability();

    void dumpDatabase(Path outputFile);

    void verifyDatabaseDump(Path dumpFile);

    void restoreDatabaseDump(Path dumpFile);

    record AvailabilityCheck(
            boolean available,
            String message
    ) {
    }
}
