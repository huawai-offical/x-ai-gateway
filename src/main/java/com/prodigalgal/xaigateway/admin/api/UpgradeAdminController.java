package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.PlatformOperationsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/upgrades")
public class UpgradeAdminController {

    private final PlatformOperationsService platformOperationsService;

    public UpgradeAdminController(PlatformOperationsService platformOperationsService) {
        this.platformOperationsService = platformOperationsService;
    }

    @GetMapping
    public List<UpgradeJobResponse> listUpgrades() {
        return platformOperationsService.listUpgrades();
    }

    @PostMapping
    public UpgradeJobResponse createUpgrade(@RequestBody UpgradeJobRequest request) {
        return platformOperationsService.createUpgrade(request);
    }

    @GetMapping("/releases")
    public List<ReleaseArtifactResponse> listReleases() {
        return platformOperationsService.listReleaseArtifacts();
    }

    @PostMapping("/releases")
    public ReleaseArtifactResponse createRelease(@RequestBody ReleaseArtifactRequest request) {
        return platformOperationsService.saveReleaseArtifact(null, request);
    }

    @PutMapping("/releases/{id}")
    public ReleaseArtifactResponse updateRelease(@PathVariable Long id, @RequestBody ReleaseArtifactRequest request) {
        return platformOperationsService.saveReleaseArtifact(id, request);
    }
}
