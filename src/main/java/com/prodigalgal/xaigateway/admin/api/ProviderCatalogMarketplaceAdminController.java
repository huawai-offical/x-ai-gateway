package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ProviderCatalogMarketplaceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/provider-sites/catalog-marketplace")
public class ProviderCatalogMarketplaceAdminController {

    private final ProviderCatalogMarketplaceService providerCatalogMarketplaceService;

    public ProviderCatalogMarketplaceAdminController(ProviderCatalogMarketplaceService providerCatalogMarketplaceService) {
        this.providerCatalogMarketplaceService = providerCatalogMarketplaceService;
    }

    @GetMapping("/status")
    public ProviderCatalogMarketplaceStatusResponse status() {
        return providerCatalogMarketplaceService.status();
    }

    @PostMapping("/updates")
    public ProviderCatalogMarketplaceUpdateResponse update(@RequestBody ProviderCatalogMarketplaceUpdateRequest request) {
        return providerCatalogMarketplaceService.update(request);
    }

    @PostMapping("/rollback")
    public ProviderCatalogMarketplaceUpdateResponse rollback() {
        return providerCatalogMarketplaceService.rollback();
    }
}
