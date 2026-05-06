package com.prodigalgal.xaigateway.protocol.ingress.publicapi;

import java.util.List;

public record PublicDocsBundleResponse(
        String docsVersion,
        String locale,
        String title,
        String openApiUrl,
        String openApiSpecVersion,
        List<String> sdkTargets,
        List<String> i18nPolicy,
        List<String> quickStart,
        List<PublicDocsCompatibilityResponse> compatibility,
        List<PublicDocsProviderPresetResponse> providerPresets,
        List<PublicDocsCliClientResponse> cliClients,
        List<PublicDocsExampleResponse> examples,
        List<PublicDocsErrorCodeResponse> errorCodes,
        List<String> routingNotes,
        List<String> billingNotes,
        List<String> conformanceChecks
) {
}
