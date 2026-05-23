package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminService;
import com.prodigalgal.xaigateway.admin.application.ProviderDomainCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/provider-sites")
public class ProviderSiteAdminController {

    private final ProviderSiteAdminService providerSiteAdminService;
    private final ProviderDomainCatalogService providerDomainCatalogService;

    public ProviderSiteAdminController(
            ProviderSiteAdminService providerSiteAdminService,
            ProviderDomainCatalogService providerDomainCatalogService) {
        this.providerSiteAdminService = providerSiteAdminService;
        this.providerDomainCatalogService = providerDomainCatalogService;
    }

    @GetMapping
    public List<ProviderSiteResponse> list() {
        return providerSiteAdminService.list();
    }

    @GetMapping("/presets")
    public List<ProviderSitePresetResponse> presets() {
        return providerSiteAdminService.listPresets();
    }

    @GetMapping("/presets/{code}")
    public ProviderSitePresetResponse preset(@PathVariable String code) {
        return providerSiteAdminService.getPreset(code);
    }

    @PostMapping("/presets/{code}/import")
    public ProviderSiteResponse importPreset(
            @PathVariable String code,
            @RequestBody(required = false) ProviderSitePresetImportRequest request) {
        return providerSiteAdminService.importPreset(code, request);
    }

    @GetMapping("/capability-matrix")
    public List<CapabilityMatrixRowResponse> capabilityMatrix() {
        return providerSiteAdminService.capabilityMatrix();
    }

    @GetMapping("/domain-catalog")
    public ProviderDomainCatalogResponse domainCatalog() {
        return providerDomainCatalogService.catalog();
    }

    @PostMapping("/refresh")
    public List<ProviderSiteResponse> refresh(@RequestBody(required = false) ProviderSiteRefreshRequest request) {
        return providerSiteAdminService.refreshCapabilities(request == null ? null : request.siteProfileIds());
    }

    @GetMapping("/{id}")
    public ProviderSiteResponse get(@PathVariable Long id) {
        return providerSiteAdminService.get(id);
    }

    @PostMapping
    public ProviderSiteResponse create(@Valid @RequestBody ProviderSiteRequest request) {
        return providerSiteAdminService.create(request);
    }

    @PutMapping("/{id}")
    public ProviderSiteResponse update(@PathVariable Long id, @Valid @RequestBody ProviderSiteRequest request) {
        return providerSiteAdminService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        providerSiteAdminService.delete(id);
    }

    @PostMapping("/{id}/refresh")
    public ProviderSiteResponse refresh(@PathVariable Long id) {
        return providerSiteAdminService.refreshCapabilities(id);
    }

    @GetMapping("/{id}/capabilities")
    public List<SiteModelCapabilityResponse> capabilities(@PathVariable Long id) {
        return providerSiteAdminService.listCapabilities(id);
    }

    @GetMapping("/{id}/protocol-endpoints")
    public List<ProviderProtocolEndpointResponse> protocolEndpoints(@PathVariable Long id) {
        return providerSiteAdminService.listProtocolEndpoints(id);
    }

    @PostMapping("/{id}/protocol-endpoints")
    public ProviderProtocolEndpointResponse createProtocolEndpoint(
            @PathVariable Long id,
            @Valid @RequestBody ProviderProtocolEndpointRequest request) {
        return providerSiteAdminService.createProtocolEndpoint(id, request);
    }

    @PutMapping("/{id}/protocol-endpoints/{endpointId}")
    public ProviderProtocolEndpointResponse updateProtocolEndpoint(
            @PathVariable Long id,
            @PathVariable Long endpointId,
            @Valid @RequestBody ProviderProtocolEndpointRequest request) {
        return providerSiteAdminService.updateProtocolEndpoint(id, endpointId, request);
    }

    @DeleteMapping("/{id}/protocol-endpoints/{endpointId}")
    public void deleteProtocolEndpoint(
            @PathVariable Long id,
            @PathVariable Long endpointId) {
        providerSiteAdminService.deleteProtocolEndpoint(id, endpointId);
    }
}
