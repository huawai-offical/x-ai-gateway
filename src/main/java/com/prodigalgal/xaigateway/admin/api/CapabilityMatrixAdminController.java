package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/capability-matrix")
public class CapabilityMatrixAdminController {

    private final ProviderSiteAdminService providerSiteAdminService;

    public CapabilityMatrixAdminController(ProviderSiteAdminService providerSiteAdminService) {
        this.providerSiteAdminService = providerSiteAdminService;
    }

    @GetMapping
    public List<CapabilityMatrixRowResponse> list() {
        return providerSiteAdminService.capabilityMatrix();
    }
}
