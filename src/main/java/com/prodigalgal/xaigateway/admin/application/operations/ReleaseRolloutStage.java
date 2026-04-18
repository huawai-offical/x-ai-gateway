package com.prodigalgal.xaigateway.admin.application.operations;

public enum ReleaseRolloutStage {
    PRECHECK,
    CREATE_CHECKPOINT,
    SWITCH_RELEASE,
    CANARY_VERIFY,
    FULL_VERIFY,
    COMPLETE
}
