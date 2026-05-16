package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.OpsTimelineService;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/ops")
public class OpsTimelineAdminController {

    private final OpsTimelineService opsTimelineService;

    public OpsTimelineAdminController(OpsTimelineService opsTimelineService) {
        this.opsTimelineService = opsTimelineService;
    }

    @GetMapping("/probe-runs")
    public List<OpsProbeRunResponse> probeRuns() {
        return opsTimelineService.listProbeRuns();
    }

    @PostMapping("/probe-runs")
    public OpsProbeRunResponse createProbeRun(@RequestBody OpsProbeRunRequest request) {
        return opsTimelineService.createProbeRun(request);
    }

    @GetMapping("/system-events")
    public List<OpsSystemEventResponse> systemEvents(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityRef,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return opsTimelineService.listEvents(severity, source, eventType, entityType, entityRef, from, to);
    }
}
