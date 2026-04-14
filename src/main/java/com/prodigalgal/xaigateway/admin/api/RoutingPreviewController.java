package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlanCompilation;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayDegradationPolicy;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompiler;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/routing")
public class RoutingPreviewController {

    private final GatewayRouteSelectionService gatewayRouteSelectionService;
    private final TranslationExecutionPlanCompiler translationExecutionPlanCompiler;

    public RoutingPreviewController(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            TranslationExecutionPlanCompiler translationExecutionPlanCompiler) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.translationExecutionPlanCompiler = translationExecutionPlanCompiler;
    }

    @PostMapping("/preview")
    public RouteSelectionPreviewResponse preview(@Valid @RequestBody RouteSelectionPreviewRequest request) {
        RouteSelectionResult result = gatewayRouteSelectionService.select(new RouteSelectionRequest(
                request.distributedKeyPrefix(),
                request.protocol(),
                request.requestPath(),
                request.requestedModel(),
                request.requestBody(),
                request.clientFamily() == null ? GatewayClientFamily.GENERIC_OPENAI : GatewayClientFamily.from(request.clientFamily()),
                false
        ));
        CanonicalExecutionPlanCompilation compilation = translationExecutionPlanCompiler.compilePreview(
                request.distributedKeyPrefix(),
                request.protocol(),
                request.requestPath(),
                request.requestedModel(),
                GatewayDegradationPolicy.ALLOW_LOSSY,
                request.clientFamily() == null ? GatewayClientFamily.GENERIC_OPENAI : GatewayClientFamily.from(request.clientFamily()),
                request.requestBody()
        );
        return new RouteSelectionPreviewResponse(
                result,
                compilation.semantics(),
                compilation.canonicalRequest(),
                compilation.canonicalPlan(),
                result.candidateEvaluations()
        );
    }
}
