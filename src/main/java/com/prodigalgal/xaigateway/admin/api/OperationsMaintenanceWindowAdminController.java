package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.MaintenanceWindowService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/operations/maintenance-windows")
public class OperationsMaintenanceWindowAdminController {

    private final MaintenanceWindowService maintenanceWindowService;

    public OperationsMaintenanceWindowAdminController(MaintenanceWindowService maintenanceWindowService) {
        this.maintenanceWindowService = maintenanceWindowService;
    }

    @GetMapping
    public List<MaintenanceWindowResponse> list() {
        return maintenanceWindowService.list(null);
    }

    @PostMapping
    public MaintenanceWindowResponse create(@RequestBody MaintenanceWindowRequest request) {
        return maintenanceWindowService.save(null, request);
    }

    @PutMapping("/{id}")
    public MaintenanceWindowResponse update(@PathVariable Long id, @RequestBody MaintenanceWindowRequest request) {
        return maintenanceWindowService.save(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        maintenanceWindowService.delete(id);
    }
}
