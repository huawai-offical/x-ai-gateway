package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/provider-sites")
public class ProviderSiteAdminController {

    private final ProviderSiteAdminService providerSiteAdminService;

    public ProviderSiteAdminController(ProviderSiteAdminService providerSiteAdminService) {
        this.providerSiteAdminService = providerSiteAdminService;
    }

    @GetMapping
    public List<ProviderSiteResponse> list() {
        return providerSiteAdminService.list();
    }

    @PostMapping
    public ProviderSiteResponse create(@Valid @RequestBody ProviderSiteRequest request) {
        return providerSiteAdminService.create(request);
    }

    @GetMapping("/{id}")
    public ProviderSiteResponse get(@PathVariable Long id) {
        return providerSiteAdminService.get(id);
    }

    @PutMapping("/{id}")
    public ProviderSiteResponse update(@PathVariable Long id, @Valid @RequestBody ProviderSiteRequest request) {
        return providerSiteAdminService.update(id, request);
    }

    @PostMapping("/{id}/refresh-capabilities")
    public ProviderSiteResponse refreshCapabilities(@PathVariable Long id) {
        return providerSiteAdminService.refreshCapabilities(id);
    }

    @GetMapping("/{id}/capabilities")
    public List<SiteModelCapabilityResponse> capabilities(@PathVariable Long id) {
        return providerSiteAdminService.listCapabilities(id);
    }

    @GetMapping("/capability-matrix")
    public List<CapabilityMatrixRowResponse> capabilityMatrix() {
        return providerSiteAdminService.capabilityMatrix();
    }
}
