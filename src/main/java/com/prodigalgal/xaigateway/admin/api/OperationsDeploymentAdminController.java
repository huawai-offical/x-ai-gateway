package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.PlatformOperationsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/operations/deployment")
public class OperationsDeploymentAdminController {

    private final PlatformOperationsService platformOperationsService;

    public OperationsDeploymentAdminController(PlatformOperationsService platformOperationsService) {
        this.platformOperationsService = platformOperationsService;
    }

    @GetMapping("/manifest")
    public DeploymentManifestResponse manifest(@RequestParam(defaultValue = "compose") String profile) {
        return platformOperationsService.deploymentManifest(profile);
    }

    @GetMapping("/preflight")
    public DeploymentPreflightResponse preflight(
            @RequestParam(required = false) String targetVersion,
            @RequestParam(defaultValue = "compose") String profile) {
        return platformOperationsService.deploymentPreflight(targetVersion, profile);
    }
}
