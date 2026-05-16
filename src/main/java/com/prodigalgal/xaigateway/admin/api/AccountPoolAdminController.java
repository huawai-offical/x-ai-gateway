package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.AccountPoolAdminService;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/account-pools")
public class AccountPoolAdminController {

    private final AccountPoolAdminService accountPoolAdminService;

    public AccountPoolAdminController(AccountPoolAdminService accountPoolAdminService) {
        this.accountPoolAdminService = accountPoolAdminService;
    }

    @GetMapping
    public List<AccountPoolResponse> list() { return accountPoolAdminService.list(); }

    @GetMapping("/model-catalog")
    public List<String> modelCatalog(@RequestParam(required = false) UpstreamAccountProviderType providerType) {
        return accountPoolAdminService.listSupportedModelCatalog(providerType);
    }

    @GetMapping("/{id}")
    public AccountPoolResponse get(@PathVariable Long id) { return accountPoolAdminService.get(id); }

    @PostMapping
    public AccountPoolResponse create(@Valid @RequestBody AccountPoolRequest request) { return accountPoolAdminService.create(request); }

    @PutMapping("/{id}")
    public AccountPoolResponse update(@PathVariable Long id, @Valid @RequestBody AccountPoolRequest request) { return accountPoolAdminService.update(id, request); }

    @PostMapping("/{id}/bindings")
    public DistributedKeyAccountPoolBindingResponse bindDistributedKey(
            @PathVariable Long id,
            @Valid @RequestBody DistributedKeyAccountPoolBindingRequest request) {
        return accountPoolAdminService.bindDistributedKey(id, request);
    }

    @PostMapping("/{id}/codex-runtime/batch-recovery-preflight")
    public CodexRuntimeBatchRecoveryResponse codexRuntimeBatchRecoveryPreflight(
            @PathVariable Long id,
            @RequestBody(required = false) CodexRuntimeBatchRecoveryRequest request) {
        return accountPoolAdminService.codexRuntimeBatchRecovery(id, request, false);
    }

    @PostMapping("/{id}/codex-runtime/batch-recovery")
    public CodexRuntimeBatchRecoveryResponse codexRuntimeBatchRecovery(
            @PathVariable Long id,
            @RequestBody(required = false) CodexRuntimeBatchRecoveryRequest request) {
        return accountPoolAdminService.codexRuntimeBatchRecovery(id, request, true);
    }

    @PostMapping("/{id}/status")
    public AccountPoolResponse toggle(@PathVariable Long id, @RequestParam boolean active) {
        return accountPoolAdminService.toggle(id, active);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        accountPoolAdminService.delete(id);
    }
}
