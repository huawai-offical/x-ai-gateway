package com.prodigalgal.xaigateway.admin.application.operations;

import java.util.List;

public interface RuntimeStateSnapshotDriver {

    RuntimeStateSnapshot capture(String keyPrefix);

    void restore(RuntimeStateSnapshot snapshot);

    VerificationResult verify(RuntimeStateSnapshot snapshot);

    record RuntimeStateSnapshot(
            String keyPrefix,
            List<RuntimeStateEntry> entries
    ) {
        public RuntimeStateSnapshot {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    record RuntimeStateEntry(
            String key,
            String value,
            Long ttlSeconds
    ) {
    }

    record VerificationResult(
            boolean success,
            String message
    ) {
    }
}
