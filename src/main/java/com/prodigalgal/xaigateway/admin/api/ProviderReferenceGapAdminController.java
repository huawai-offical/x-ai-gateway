package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ProviderReferenceGapService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/provider-reference-gap")
public class ProviderReferenceGapAdminController {

    private final ProviderReferenceGapService providerReferenceGapService;

    public ProviderReferenceGapAdminController(ProviderReferenceGapService providerReferenceGapService) {
        this.providerReferenceGapService = providerReferenceGapService;
    }

    @GetMapping
    public ProviderReferenceGapResponse get() {
        return providerReferenceGapService.get();
    }
}
