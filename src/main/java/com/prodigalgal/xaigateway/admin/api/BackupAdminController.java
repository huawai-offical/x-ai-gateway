package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.PlatformOperationsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/backups")
public class BackupAdminController {

    private final PlatformOperationsService platformOperationsService;

    public BackupAdminController(PlatformOperationsService platformOperationsService) {
        this.platformOperationsService = platformOperationsService;
    }

    @GetMapping
    public List<BackupJobResponse> list() {
        return platformOperationsService.listBackups();
    }

    @PostMapping
    public BackupJobResponse create(@RequestBody(required = false) BackupJobRequest request) {
        boolean dryRun = request != null && Boolean.TRUE.equals(request.dryRun());
        return platformOperationsService.createBackup(dryRun);
    }

    @PostMapping("/{id}/restore")
    public RestoreJobResponse restore(@PathVariable Long id, @RequestBody(required = false) BackupJobRequest request) {
        boolean dryRun = request != null && Boolean.TRUE.equals(request.dryRun());
        return platformOperationsService.restoreBackup(id, dryRun);
    }
}
