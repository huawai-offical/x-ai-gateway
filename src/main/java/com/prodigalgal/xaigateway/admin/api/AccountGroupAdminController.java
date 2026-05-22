package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.AccountGroupAdminService;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/account-groups")
public class AccountGroupAdminController {

    private final AccountGroupAdminService accountGroupAdminService;

    public AccountGroupAdminController(AccountGroupAdminService accountGroupAdminService) {
        this.accountGroupAdminService = accountGroupAdminService;
    }

    @GetMapping
    public List<AccountGroupResponse> list() { return accountGroupAdminService.list(); }

    @GetMapping("/model-catalog")
    public List<String> modelCatalog(@RequestParam(required = false) UpstreamAccountProviderType providerType) {
        return accountGroupAdminService.listSupportedModelCatalog(providerType);
    }

    @GetMapping("/{id}")
    public AccountGroupResponse get(@PathVariable Long id) { return accountGroupAdminService.get(id); }

    @PostMapping
    public AccountGroupResponse create(@Valid @RequestBody AccountGroupRequest request) { return accountGroupAdminService.create(request); }

    @PutMapping("/{id}")
    public AccountGroupResponse update(@PathVariable Long id, @Valid @RequestBody AccountGroupRequest request) { return accountGroupAdminService.update(id, request); }

    @PostMapping("/{id}/bindings")
    public DistributedKeyAccountGroupBindingResponse bindDistributedKey(
            @PathVariable Long id,
            @Valid @RequestBody DistributedKeyAccountGroupBindingRequest request) {
        return accountGroupAdminService.bindDistributedKey(id, request);
    }

    @PostMapping("/{id}/codex-runtime/batch-recovery-preflight")
    public CodexRuntimeBatchRecoveryResponse codexRuntimeBatchRecoveryPreflight(
            @PathVariable Long id,
            @RequestBody(required = false) CodexRuntimeBatchRecoveryRequest request) {
        return accountGroupAdminService.codexRuntimeBatchRecovery(id, request, false);
    }

    @PostMapping("/{id}/codex-runtime/batch-recovery")
    public CodexRuntimeBatchRecoveryResponse codexRuntimeBatchRecovery(
            @PathVariable Long id,
            @RequestBody(required = false) CodexRuntimeBatchRecoveryRequest request) {
        return accountGroupAdminService.codexRuntimeBatchRecovery(id, request, true);
    }

    @PostMapping("/{id}/status")
    public AccountGroupResponse toggle(@PathVariable Long id, @RequestParam boolean active) {
        return accountGroupAdminService.toggle(id, active);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        accountGroupAdminService.delete(id);
    }
}
