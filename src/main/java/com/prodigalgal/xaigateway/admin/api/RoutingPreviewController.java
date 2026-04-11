package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
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

    public RoutingPreviewController(GatewayRouteSelectionService gatewayRouteSelectionService) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
    }

    @PostMapping("/preview")
    public RouteSelectionPreviewResponse preview(@Valid @RequestBody RouteSelectionPreviewRequest request) {
        RouteSelectionResult result = gatewayRouteSelectionService.select(new RouteSelectionRequest(
                request.distributedKeyPrefix(),
                request.protocol(),
                request.requestPath(),
                request.requestedModel(),
                request.requestBody()
        ));

        return new RouteSelectionPreviewResponse(
                result.distributedKeyId(),
                result.distributedKeyPrefix(),
                result.requestedModel(),
                result.publicModel(),
                result.resolvedModelKey(),
                result.protocol(),
                result.prefixHash(),
                result.fingerprint(),
                result.modelGroup(),
                result.selectionSource(),
                result.selectedCandidate(),
                result.candidates().size(),
                result.candidates()
        );
    }
}
