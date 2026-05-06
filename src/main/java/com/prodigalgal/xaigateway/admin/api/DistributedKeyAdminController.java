package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.DistributedKeyAdminService;
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
@RequestMapping("/admin/distributed-keys")
public class DistributedKeyAdminController {

    private final DistributedKeyAdminService distributedKeyAdminService;

    public DistributedKeyAdminController(DistributedKeyAdminService distributedKeyAdminService) {
        this.distributedKeyAdminService = distributedKeyAdminService;
    }

    @GetMapping
    public List<DistributedKeyResponse> list() {
        return distributedKeyAdminService.list();
    }

    @PostMapping
    public DistributedKeyCreateResponse create(@Valid @RequestBody DistributedKeyRequest request) {
        return distributedKeyAdminService.create(request);
    }

    @PutMapping("/{id}")
    public DistributedKeyResponse update(
            @PathVariable Long id,
            @Valid @RequestBody DistributedKeyRequest request) {
        return distributedKeyAdminService.update(id, request);
    }

    @PostMapping("/{id}/rotate")
    public DistributedKeyCreateResponse rotate(@PathVariable Long id) {
        return distributedKeyAdminService.rotate(id);
    }

    @PostMapping("/{id}/status")
    public DistributedKeyResponse toggle(
            @PathVariable Long id,
            @RequestParam boolean active) {
        return distributedKeyAdminService.toggle(id, active);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        distributedKeyAdminService.delete(id);
    }

    @GetMapping("/{id}/client-config")
    public DistributedKeyClientConfigResponse exportClientConfig(
            @PathVariable Long id,
            @RequestParam(defaultValue = "config_toml") String format,
            @RequestParam(defaultValue = "GENERIC_OPENAI") String clientFamily,
            @RequestParam(required = false) String baseUrl) {
        return distributedKeyAdminService.exportClientConfig(id, format, clientFamily, baseUrl);
    }

    @GetMapping("/{id}/onboarding-pack")
    public DistributedKeyOnboardingPackResponse exportOnboardingPack(
            @PathVariable Long id,
            @RequestParam(required = false) String baseUrl) {
        return distributedKeyAdminService.exportOnboardingPack(id, baseUrl);
    }

    @PostMapping("/{id}/client-config/downloads/{grantToken}")
    public DistributedKeyClientConfigResponse consumeOneTimeClientConfig(
            @PathVariable Long id,
            @PathVariable String grantToken,
            @RequestParam(defaultValue = "config_toml") String format,
            @RequestParam(defaultValue = "GENERIC_OPENAI") String clientFamily,
            @RequestParam(required = false) String baseUrl) {
        return distributedKeyAdminService.consumeOneTimeClientConfig(id, grantToken, format, clientFamily, baseUrl);
    }

    @DeleteMapping("/{id}/client-config/downloads/{grantToken}")
    public DistributedKeySecretExportGrantResponse revokeOneTimeClientConfig(
            @PathVariable Long id,
            @PathVariable String grantToken) {
        return distributedKeyAdminService.revokeOneTimeClientConfig(id, grantToken);
    }
}
