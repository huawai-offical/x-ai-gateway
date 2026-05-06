package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminService;
import com.prodigalgal.xaigateway.admin.application.ProviderSiteDossierService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final ProviderSiteDossierService providerSiteDossierService;

    public ProviderSiteAdminController(
            ProviderSiteAdminService providerSiteAdminService,
            ProviderSiteDossierService providerSiteDossierService
    ) {
        this.providerSiteAdminService = providerSiteAdminService;
        this.providerSiteDossierService = providerSiteDossierService;
    }

    @GetMapping
    public List<ProviderSiteResponse> list() {
        return providerSiteAdminService.list();
    }

    @PostMapping
    public ProviderSiteResponse create(@Valid @RequestBody ProviderSiteRequest request) {
        return providerSiteAdminService.create(request);
    }

    @GetMapping("/presets")
    public List<ProviderSitePresetResponse> listPresets() {
        return providerSiteAdminService.listPresets();
    }

    @GetMapping("/presets/{code}")
    public ProviderSitePresetResponse getPreset(@PathVariable String code) {
        return providerSiteAdminService.getPreset(code);
    }

    @PostMapping("/presets/{code}/import")
    public ProviderSiteResponse importPreset(
            @PathVariable String code,
            @RequestBody(required = false) ProviderSitePresetImportRequest request) {
        return providerSiteAdminService.importPreset(code, request);
    }

    @GetMapping("/{id}")
    public ProviderSiteResponse get(@PathVariable Long id) {
        return providerSiteAdminService.get(id);
    }

    @GetMapping("/{id}/dossier")
    public ProviderSiteDossierResponse dossier(@PathVariable Long id) {
        return providerSiteDossierService.get(id);
    }

    @PutMapping("/{id}")
    public ProviderSiteResponse update(@PathVariable Long id, @Valid @RequestBody ProviderSiteRequest request) {
        return providerSiteAdminService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        providerSiteAdminService.delete(id);
    }

    @PostMapping("/{id}/refresh-capabilities")
    public ProviderSiteResponse refreshCapabilities(@PathVariable Long id) {
        return providerSiteAdminService.refreshCapabilities(id);
    }

    @PostMapping("/refresh-capabilities")
    public List<ProviderSiteResponse> refreshCapabilities(@RequestBody(required = false) ProviderSiteRefreshRequest request) {
        return providerSiteAdminService.refreshCapabilities(
                request == null ? null : request.siteProfileIds()
        );
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
