package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.MaintenanceRunService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/operations/maintenance-runs")
public class MaintenanceRunAdminController {

    private final MaintenanceRunService maintenanceRunService;

    public MaintenanceRunAdminController(MaintenanceRunService maintenanceRunService) {
        this.maintenanceRunService = maintenanceRunService;
    }

    @GetMapping
    public List<MaintenanceRunResponse> list() {
        return maintenanceRunService.list();
    }

    @GetMapping("/{id}")
    public MaintenanceRunResponse get(@PathVariable Long id) {
        return maintenanceRunService.get(id);
    }

    @PostMapping
    public MaintenanceRunResponse execute(@RequestBody MaintenanceRunRequest request) {
        return maintenanceRunService.execute(request);
    }
}
