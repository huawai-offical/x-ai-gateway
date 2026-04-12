package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.PlatformOperationsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/install")
public class InstallAdminController {

    private final PlatformOperationsService platformOperationsService;

    public InstallAdminController(PlatformOperationsService platformOperationsService) {
        this.platformOperationsService = platformOperationsService;
    }

    @GetMapping("/state")
    public InstallationStateResponse state() {
        return platformOperationsService.getInstallationState();
    }

    @PostMapping("/bootstrap")
    public InstallationStateResponse bootstrap(@RequestBody InstallBootstrapRequest request) {
        return platformOperationsService.bootstrap(request);
    }
}
