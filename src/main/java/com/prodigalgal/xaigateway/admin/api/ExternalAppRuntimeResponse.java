package com.prodigalgal.xaigateway.admin.api;

public record ExternalAppRuntimeResponse(
        DashboardExternalAppResponse app,
        ExternalAppSignedContextResponse signedContext,
        boolean runnable,
        String runtimeStatus,
        String runtimeMessage,
        String actualOrigin
) {
}
