package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/{id}/capabilities")
    public List<SiteModelCapabilityResponse> capabilities(@PathVariable Long id) {
        return providerSiteAdminService.listCapabilities(id);
    }
}
