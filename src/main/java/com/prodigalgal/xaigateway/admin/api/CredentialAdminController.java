package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.CredentialAdminService;
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

    public CredentialAdminController(CredentialAdminService credentialAdminService) {
        this.credentialAdminService = credentialAdminService;
    }

    @GetMapping
    public List<CredentialResponse> list() {
        return credentialAdminService.list();
    }

    @PostMapping
    public CredentialResponse create(@Valid @RequestBody CredentialRequest request) {
        return credentialAdminService.create(request);
    }

    @PostMapping("/test-connectivity")
    public CredentialConnectivityResponse testConnectivity(
            @Valid @RequestBody CredentialConnectivityRequest request) {
        return credentialAdminService.testConnectivity(request);
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
}
