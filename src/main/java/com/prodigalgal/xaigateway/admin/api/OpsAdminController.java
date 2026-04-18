package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.OpsAlertService;
import com.prodigalgal.xaigateway.admin.application.OpsCapacityService;
import com.prodigalgal.xaigateway.admin.application.OpsDashboardService;
import com.prodigalgal.xaigateway.admin.application.OpsProbeJobService;
import com.prodigalgal.xaigateway.admin.application.OpsRuntimeLogService;
import com.prodigalgal.xaigateway.admin.application.OpsSloService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/admin/ops")
public class OpsAdminController {

    private final OpsDashboardService opsDashboardService;
    private final OpsAlertService opsAlertService;
    private final OpsProbeJobService opsProbeJobService;
    private final OpsRuntimeLogService opsRuntimeLogService;
    private final OpsSloService opsSloService;
    private final OpsCapacityService opsCapacityService;

    public OpsAdminController(
            OpsDashboardService opsDashboardService,
            OpsAlertService opsAlertService,
            OpsProbeJobService opsProbeJobService,
            OpsRuntimeLogService opsRuntimeLogService,
            OpsSloService opsSloService,
            OpsCapacityService opsCapacityService) {
        this.opsDashboardService = opsDashboardService;
        this.opsAlertService = opsAlertService;
        this.opsProbeJobService = opsProbeJobService;
        this.opsRuntimeLogService = opsRuntimeLogService;
        this.opsSloService = opsSloService;
        this.opsCapacityService = opsCapacityService;
    }

    @GetMapping("/summary")
    public OpsSummaryResponse summary() {
        return opsDashboardService.summary();
    }

    @GetMapping("/alerts")
    public List<OpsAlertEventResponse> alerts(@RequestParam(required = false) String status) {
        return opsAlertService.listEvents(status);
    }

    @PostMapping("/alerts/{id}/ack")
    public OpsAlertEventResponse acknowledge(@PathVariable Long id) {
        return opsAlertService.acknowledge(id);
    }

    @GetMapping("/alerts/rules")
    public List<OpsAlertRuleResponse> listAlertRules() {
        return opsAlertService.listRules();
    }

    @PostMapping("/alerts/rules")
    public OpsAlertRuleResponse createAlertRule(@Valid @RequestBody OpsAlertRuleRequest request) {
        return opsAlertService.saveRule(null, request);
    }

    @PutMapping("/alerts/rules/{id}")
    public OpsAlertRuleResponse updateAlertRule(@PathVariable Long id, @Valid @RequestBody OpsAlertRuleRequest request) {
        return opsAlertService.saveRule(id, request);
    }

    @GetMapping("/alerts/silences")
    public List<AlertSilenceResponse> listAlertSilences() {
        return opsAlertService.listSilences();
    }

    @PostMapping("/alerts/silences")
    public AlertSilenceResponse createAlertSilence(@RequestBody AlertSilenceRequest request) {
        return opsAlertService.saveSilence(null, request);
    }

    @PutMapping("/alerts/silences/{id}")
    public AlertSilenceResponse updateAlertSilence(@PathVariable Long id, @RequestBody AlertSilenceRequest request) {
        return opsAlertService.saveSilence(id, request);
    }

    @GetMapping("/slo")
    public OpsSloSummaryResponse sloSummary() {
        return opsSloService.summary(Instant.now());
    }

    @GetMapping("/slo/policies")
    public List<SloPolicyResponse> listSloPolicies() {
        return opsSloService.listPolicies();
    }

    @PostMapping("/slo/policies")
    public SloPolicyResponse createSloPolicy(@RequestBody SloPolicyRequest request) {
        return opsSloService.savePolicy(null, request);
    }

    @PutMapping("/slo/policies/{id}")
    public SloPolicyResponse updateSloPolicy(@PathVariable Long id, @RequestBody SloPolicyRequest request) {
        return opsSloService.savePolicy(id, request);
    }

    @GetMapping("/capacity")
    public OpsCapacitySummaryResponse capacitySummary() {
        return opsCapacityService.summary(Instant.now());
    }

    @GetMapping("/probes")
    public List<OpsScheduledProbeJobResponse> listProbeJobs() {
        return opsProbeJobService.list();
    }

    @PostMapping("/probes")
    public OpsScheduledProbeJobResponse createProbeJob(@Valid @RequestBody OpsScheduledProbeJobRequest request) {
        return opsProbeJobService.save(null, request);
    }

    @PostMapping("/probes/{id}/run")
    public OpsScheduledProbeJobResponse runProbeJob(@PathVariable Long id) {
        return opsProbeJobService.trigger(id);
    }

    @GetMapping("/logs/system")
    public List<OpsOperationAuditResponse> systemLogs() {
        return opsDashboardService.summary().recentLogs();
    }

    @GetMapping("/logs/runtime")
    public List<OpsRuntimeLogSettingResponse> runtimeLogs() {
        return opsRuntimeLogService.list();
    }

    @PostMapping("/logs/runtime")
    public OpsRuntimeLogSettingResponse saveRuntimeLog(@Valid @RequestBody OpsRuntimeLogSettingRequest request) {
        return opsRuntimeLogService.save(null, request);
    }
}
