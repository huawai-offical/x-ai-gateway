package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.TranslationExplainRequest;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayDegradationPolicy;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayInteropPlanService;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlan;
import com.prodigalgal.xaigateway.protocol.ingress.interop.InteropPlanRequest;
import com.prodigalgal.xaigateway.protocol.ingress.interop.InteropPlanResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TranslationExplainService {

    private final GatewayInteropPlanService gatewayInteropPlanService;

    public TranslationExplainService(GatewayInteropPlanService gatewayInteropPlanService) {
        this.gatewayInteropPlanService = gatewayInteropPlanService;
    }

    public TranslationExecutionPlan explain(TranslationExplainRequest request) {
        InteropPlanResponse preview = gatewayInteropPlanService.preview(
                request.distributedKeyPrefix(),
                new InteropPlanRequest(
                        request.protocol(),
                        request.requestPath(),
                        request.requestedModel(),
                        request.degradationPolicy() == null || request.degradationPolicy().isBlank()
                                ? GatewayDegradationPolicy.ALLOW_LOSSY.name().toLowerCase()
                                : request.degradationPolicy(),
                        request.body()
                )
        );
        return new TranslationExecutionPlan(
                preview.executable(),
                preview.resourceType(),
                preview.operation(),
                preview.providerFamily(),
                preview.siteProfileId(),
                preview.executionKind(),
                preview.capabilityLevel() == null
                        ? null
                        : com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel.valueOf(preview.capabilityLevel().toUpperCase()),
                preview.upstreamObjectMode(),
                preview.degradations(),
                preview.blockers(),
                preview.authStrategy(),
                preview.pathStrategy(),
                preview.errorSchemaStrategy(),
                java.util.Map.of(
                        "protocol", preview.protocol(),
                        "requestPath", preview.requestPath(),
                        "requestedModel", preview.requestedModel()
                ),
                java.util.Map.of(
                        "selectionResult", preview.selectionResult(),
                        "summary", preview.summary(),
                        "debug", preview.debug()
                )
        );
    }
}
