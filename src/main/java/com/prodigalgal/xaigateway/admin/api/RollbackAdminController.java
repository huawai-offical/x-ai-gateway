package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.PlatformOperationsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/rollbacks")
public class RollbackAdminController {

    private final PlatformOperationsService platformOperationsService;

    public RollbackAdminController(PlatformOperationsService platformOperationsService) {
        this.platformOperationsService = platformOperationsService;
    }

    @GetMapping
    public List<RollbackJobResponse> list() {
        return platformOperationsService.listRollbacks();
    }

    @PostMapping
    public RollbackJobResponse create(
            @RequestParam Long upgradeJobId,
            @RequestParam Long releaseArtifactId,
            @RequestParam Long backupJobId) {
        return platformOperationsService.createRollback(upgradeJobId, releaseArtifactId, backupJobId, false);
    }
}
