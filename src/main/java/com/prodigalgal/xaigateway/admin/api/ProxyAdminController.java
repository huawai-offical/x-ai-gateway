package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.NetworkGovernanceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/network/proxies")
public class ProxyAdminController {

    private final NetworkGovernanceService networkGovernanceService;

    public ProxyAdminController(NetworkGovernanceService networkGovernanceService) {
        this.networkGovernanceService = networkGovernanceService;
    }

    @GetMapping
    public List<ProxyResponse> list() { return networkGovernanceService.listProxies(); }

    @PostMapping
    public ProxyResponse create(@Valid @RequestBody ProxyRequest request) { return networkGovernanceService.saveProxy(null, request); }

    @PutMapping("/{id}")
    public ProxyResponse update(@PathVariable Long id, @Valid @RequestBody ProxyRequest request) { return networkGovernanceService.saveProxy(id, request); }

    @PostMapping("/{id}/probe")
    public ProxyProbeResultResponse probe(@PathVariable Long id) { return networkGovernanceService.probe(id); }
}
