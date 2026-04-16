package com.prodigalgal.xaigateway.gateway.core.interop;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NonChatDegradationPolicyService {

    public NonChatDegradationOutcome evaluate(
            GatewayRequestSemantics semantics,
            GatewayDegradationPolicy degradationPolicy,
            NonChatRoutePolicyDecision policyDecision
    ) {
        List<String> blockedReasons = new ArrayList<>(policyDecision.blockedReasons());
        List<String> lossReasons = new ArrayList<>(policyDecision.lossReasons());

        if (policyDecision.renderCapabilityLevel() == InteropCapabilityLevel.UNSUPPORTED) {
            blockedReasons.add("当前 ingress 尚无可用 render shape。");
        }
        if (degradationPolicy != null && !degradationPolicy.allows(policyDecision.overallCapabilityLevel())) {
            blockedReasons.add("当前策略不允许 " + policyDecision.overallCapabilityLevel().name().toLowerCase() + " 执行。");
        }

        return new NonChatDegradationOutcome(
                SupportStatus.resolve(policyDecision.preferredBackend(), policyDecision.overallCapabilityLevel(), blockedReasons),
                SupportStatus.normalizeDegradationLevel(policyDecision.overallCapabilityLevel(), blockedReasons),
                deduplicate(blockedReasons),
                deduplicate(lossReasons),
                renderPolicyReason(policyDecision),
                fallbackPolicyReason(semantics, policyDecision)
        );
    }

    private String renderPolicyReason(NonChatRoutePolicyDecision policyDecision) {
        if (policyDecision.renderCapabilityLevel() == InteropCapabilityLevel.UNSUPPORTED) {
            return "render_capability=unsupported";
        }
        return "render_capability=" + policyDecision.renderCapabilityLevel().name().toLowerCase();
    }

    private String fallbackPolicyReason(
            GatewayRequestSemantics semantics,
            NonChatRoutePolicyDecision policyDecision
    ) {
        if (semantics == null || semantics.routeSelectionMode() == null) {
            return "fallback=unknown";
        }
        if (policyDecision.renderCapabilityLevel() == InteropCapabilityLevel.UNSUPPORTED) {
            return "fallback=blocked_before_execution(render_unsupported)";
        }
        return switch (semantics.routeSelectionMode()) {
            case CATALOG_SELECTION -> "fallback=allowed_before_first_byte_only";
            case LOCAL_CATALOG -> "fallback=disabled(selection_mode=local_catalog)";
            case STORED_LINEAGE -> "fallback=disabled(selection_mode=stored_lineage)";
            case DISTRIBUTED_TARGET -> "fallback=disabled(selection_mode=distributed_target)";
        };
    }

    private List<String> deduplicate(List<String> items) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String item : items) {
            if (item != null && !item.isBlank()) {
                values.add(item);
            }
        }
        return List.copyOf(values);
    }
}
