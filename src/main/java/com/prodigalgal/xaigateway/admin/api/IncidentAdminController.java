package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.IncidentWorkbenchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/incidents")
public class IncidentAdminController {

    private final IncidentWorkbenchService incidentWorkbenchService;

    public IncidentAdminController(IncidentWorkbenchService incidentWorkbenchService) {
        this.incidentWorkbenchService = incidentWorkbenchService;
    }

    @GetMapping("/summary")
    public IncidentSummaryResponse summary() {
        return incidentWorkbenchService.summary();
    }
}
