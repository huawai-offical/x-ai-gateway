package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.NetworkGovernanceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/network/probes")
public class ProxyProbeAdminController {

    private final NetworkGovernanceService networkGovernanceService;

    public ProxyProbeAdminController(NetworkGovernanceService networkGovernanceService) {
        this.networkGovernanceService = networkGovernanceService;
    }

    @GetMapping("/{proxyId}")
    public List<ProxyProbeResultResponse> list(@PathVariable Long proxyId) {
        return networkGovernanceService.listProbeResults(proxyId);
    }
}
