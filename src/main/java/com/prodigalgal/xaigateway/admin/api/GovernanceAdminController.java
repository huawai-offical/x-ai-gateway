package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.GovernanceAdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ops")
public class GovernanceAdminController {

    private final GovernanceAdminService governanceAdminService;

    public GovernanceAdminController(GovernanceAdminService governanceAdminService) {
        this.governanceAdminService = governanceAdminService;
    }

    @GetMapping("/policies/route-guards")
    public List<RouteGuardPolicyResponse> listRouteGuards() {
        return governanceAdminService.listRouteGuards();
    }

    @GetMapping("/policies/routing-summary")
    public RoutingPolicySummaryResponse routingPolicySummary() {
        return governanceAdminService.routingPolicySummary();
    }

    @GetMapping("/policies/routing-runtime-plan")
    public RoutingPolicyRuntimePlanResponse routingRuntimePlan() {
        return governanceAdminService.routingRuntimePlan();
    }

    @GetMapping("/policies/routing-runtime-states")
    public List<RoutingPolicyRuntimeStateResponse> routingRuntimeStates() {
        return governanceAdminService.routingRuntimeStates();
    }

    @PostMapping("/policies/routing-runtime-states/reset")
    public void resetRoutingRuntimeStates(
            @RequestParam(required = false) String runtimeKey,
            @RequestParam(required = false) Long policyId,
            @RequestParam(required = false) String targetRef) {
        governanceAdminService.resetRoutingRuntimeStates(runtimeKey, policyId, targetRef);
    }

    @PostMapping("/policies/route-guards")
    public RouteGuardPolicyResponse createRouteGuard(@Valid @RequestBody RouteGuardPolicyRequest request) {
        return governanceAdminService.saveRouteGuard(null, request);
    }

    @PutMapping("/policies/route-guards/{id}")
    public RouteGuardPolicyResponse updateRouteGuard(@PathVariable Long id, @Valid @RequestBody RouteGuardPolicyRequest request) {
        return governanceAdminService.saveRouteGuard(id, request);
    }

    @DeleteMapping("/policies/route-guards/{id}")
    public void deleteRouteGuard(@PathVariable Long id) {
        governanceAdminService.deleteRouteGuard(id);
    }

    @GetMapping("/policies/auto-actions")
    public List<AutoActionRuleResponse> listAutoActions() {
        return governanceAdminService.listAutoActions();
    }

    @PostMapping("/policies/auto-actions")
    public AutoActionRuleResponse createAutoAction(@Valid @RequestBody AutoActionRuleRequest request) {
        return governanceAdminService.saveAutoAction(null, request);
    }

    @PutMapping("/policies/auto-actions/{id}")
    public AutoActionRuleResponse updateAutoAction(@PathVariable Long id, @Valid @RequestBody AutoActionRuleRequest request) {
        return governanceAdminService.saveAutoAction(id, request);
    }

    @DeleteMapping("/policies/auto-actions/{id}")
    public void deleteAutoAction(@PathVariable Long id) {
        governanceAdminService.deleteAutoAction(id);
    }

    @GetMapping("/quarantines")
    public List<QuarantineRecordResponse> listQuarantines(@RequestParam(required = false) String status) {
        return governanceAdminService.listQuarantines(status);
    }

    @GetMapping("/health-scores")
    public GovernanceHealthScoreResponse listHealthScores() {
        return governanceAdminService.listHealthScores();
    }

    @PostMapping("/quarantines/{id}/release")
    public QuarantineRecordResponse releaseQuarantine(@PathVariable Long id, @RequestBody(required = false) QuarantineReleaseRequest request) {
        return governanceAdminService.releaseQuarantine(id, request == null ? null : request.releaseReason());
    }
}
