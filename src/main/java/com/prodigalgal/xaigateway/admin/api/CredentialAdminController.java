package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.CredentialAdminService;
import com.prodigalgal.xaigateway.admin.application.UpstreamCredentialInventoryService;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/credentials")
public class CredentialAdminController {

    private final CredentialAdminService credentialAdminService;
    private final UpstreamCredentialInventoryService upstreamCredentialInventoryService;

    public CredentialAdminController(
            CredentialAdminService credentialAdminService,
            UpstreamCredentialInventoryService upstreamCredentialInventoryService) {
        this.credentialAdminService = credentialAdminService;
        this.upstreamCredentialInventoryService = upstreamCredentialInventoryService;
    }

    @GetMapping
    public List<CredentialResponse> list() {
        return credentialAdminService.list();
    }

    @GetMapping("/inventory")
    public List<UpstreamCredentialInventoryResponse> inventory() {
        return upstreamCredentialInventoryService.list();
    }

    @GetMapping("/model-catalog")
    public List<String> modelCatalog(@RequestParam(required = false) ProviderType providerType) {
        if (providerType == null) {
            return List.of();
        }
        return credentialAdminService.listSupportedModelCatalog(providerType);
    }

    @GetMapping("/group/{groupId}")
    public List<CredentialResponse> listByGroup(@PathVariable Long groupId) {
        return credentialAdminService.listByGroup(groupId);
    }

    @GetMapping("/{id}")
    public CredentialResponse get(@PathVariable Long id) {
        return credentialAdminService.get(id);
    }

    @PostMapping
    public CredentialResponse create(@Valid @RequestBody CredentialRequest request) {
        return credentialAdminService.create(request);
    }

    @PostMapping("/multi-endpoint")
    public List<CredentialResponse> createMultiEndpoint(@Valid @RequestBody CredentialRequest request) {
        return credentialAdminService.createForProtocolEndpoints(request);
    }

    @PostMapping("/test-connectivity")
    public CredentialConnectivityResponse testConnectivity(
            @Valid @RequestBody CredentialConnectivityRequest request) {
        return credentialAdminService.testConnectivity(request);
    }

    @PostMapping("/{id}/connectivity-test")
    public CredentialConnectivityResponse testSavedCredentialConnectivity(@PathVariable Long id) {
        return credentialAdminService.testSavedCredentialConnectivity(id);
    }

    @PutMapping("/{id}")
    public CredentialResponse update(@PathVariable Long id, @Valid @RequestBody CredentialRequest request) {
        return credentialAdminService.update(id, request);
    }

    @PostMapping("/{id}/status")
    public CredentialResponse toggle(@PathVariable Long id, @RequestParam boolean active) {
        return credentialAdminService.toggle(id, active);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        credentialAdminService.delete(id);
    }

    @PostMapping("/{id}/refresh-models")
    public CredentialModelRefreshResponse refreshModels(@PathVariable Long id) {
        return credentialAdminService.refreshModels(id);
    }

    @PostMapping("/{id}/openai-direct/smoke")
    public OpenAiDirectSmokeResponse openAiDirectSmoke(
            @PathVariable Long id,
            @RequestBody(required = false) OpenAiDirectSmokeRequest request) {
        return credentialAdminService.openAiDirectSmoke(id, request);
    }

    @PostMapping("/{id}/openai-direct/resource-smoke")
    public OpenAiDirectResourceSmokeResponse openAiDirectResourceSmoke(
            @PathVariable Long id,
            @RequestBody(required = false) OpenAiDirectResourceSmokeRequest request) {
        return credentialAdminService.openAiDirectResourceSmoke(id, request);
    }

    @PostMapping("/{id}/openai-direct/resource-smoke/certification")
    public OpenAiDirectSmokeCertificationResponse openAiDirectResourceSmokeCertification(
            @PathVariable Long id,
            @RequestBody(required = false) OpenAiDirectResourceSmokeRequest request) {
        return credentialAdminService.openAiDirectResourceSmokeCertification(id, request);
    }

    @PostMapping("/{id}/functional-provider/smoke")
    public FunctionalProviderSmokeResponse functionalProviderSmoke(
            @PathVariable Long id,
            @RequestBody(required = false) FunctionalProviderSmokeRequest request) {
        return credentialAdminService.functionalProviderSmoke(id, request);
    }

    @PostMapping("/{id}/functional-provider/smoke/certification")
    public FunctionalProviderSmokeCertificationResponse functionalProviderSmokeCertification(
            @PathVariable Long id,
            @RequestBody(required = false) FunctionalProviderSmokeRequest request) {
        return credentialAdminService.functionalProviderSmokeCertification(id, request);
    }
}
