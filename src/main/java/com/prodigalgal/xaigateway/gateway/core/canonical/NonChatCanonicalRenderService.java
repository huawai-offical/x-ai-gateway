package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionResult;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NonChatCanonicalRenderService {

    private final List<NonChatCanonicalRenderer> renderers;

    public NonChatCanonicalRenderService(List<NonChatCanonicalRenderer> renderers) {
        this.renderers = renderers == null ? List.of() : List.copyOf(renderers);
    }

    public InteropCapabilityLevel renderLevel(
            String protocol,
            String requestPath,
            GatewayRequestSemantics semantics
    ) {
        CanonicalIngressProtocol ingressProtocol = CanonicalIngressProtocol.from(protocol);
        return renderers.stream()
                .filter(renderer -> renderer.supports(ingressProtocol, requestPath, semantics))
                .map(renderer -> renderer.renderCapabilityLevel(ingressProtocol, requestPath, semantics))
                .findFirst()
                .orElseGet(() -> CanonicalRenderCapabilitySupport.renderLevel(protocol, requestPath, semantics));
    }

    public NonChatRenderedResponse render(
            CanonicalResourceRequest request,
            CanonicalExecutionPlan executionPlan,
            GatewayResourceExecutionResult result
    ) {
        GatewayRequestSemantics semantics = semanticsOf(request, executionPlan);
        NonChatCanonicalRenderer renderer = findRenderer(
                request.ingressProtocol(),
                request.requestPath(),
                semantics
        );
        return renderer.render(request, executionPlan, result);
    }

    public NonChatRenderedResponse renderNativeView(
            CanonicalResourceRequest request,
            CanonicalExecutionPlan executionPlan,
            Object nativeView
    ) {
        GatewayRequestSemantics semantics = semanticsOf(request, executionPlan);
        return renderers.stream()
                .filter(renderer -> renderer.supportsNativeView(
                        request.ingressProtocol(),
                        request.requestPath(),
                        semantics,
                        nativeView
                ))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("当前 ingress 尚无可用 native view renderer。"))
                .renderNativeView(request, executionPlan, nativeView);
    }

    private NonChatCanonicalRenderer findRenderer(
            CanonicalIngressProtocol ingressProtocol,
            String requestPath,
            GatewayRequestSemantics semantics
    ) {
        return renderers.stream()
                .filter(renderer -> renderer.supports(ingressProtocol, requestPath, semantics))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("当前 ingress 尚无可用 non-chat renderer。"));
    }

    private GatewayRequestSemantics semanticsOf(
            CanonicalResourceRequest request,
            CanonicalExecutionPlan executionPlan
    ) {
        if (request != null) {
            return new GatewayRequestSemantics(
                    request.resourceType(),
                    request.operation(),
                    executionPlan == null ? null : executionPlan.surface(),
                    request.normalizedPath(),
                    List.of(),
                    executionPlan == null ? null : executionPlan.routeSelectionMode()
            );
        }
        return new GatewayRequestSemantics(
                executionPlan == null ? null : executionPlan.resourceType(),
                executionPlan == null ? null : executionPlan.operation(),
                List.of(),
                executionPlan == null ? null : executionPlan.routeSelectionMode()
        );
    }
}
