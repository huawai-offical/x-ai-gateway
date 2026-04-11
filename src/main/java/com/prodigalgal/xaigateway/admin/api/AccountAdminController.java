package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.AccountAdminService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/accounts")
public class AccountAdminController {

    private final AccountAdminService accountAdminService;

    public AccountAdminController(AccountAdminService accountAdminService) {
        this.accountAdminService = accountAdminService;
    }

    @GetMapping("/pool/{poolId}")
    public List<UpstreamAccountResponse> listByPool(@PathVariable Long poolId) {
        return accountAdminService.listByPool(poolId);
    }

    @GetMapping("/{id}")
    public UpstreamAccountResponse get(@PathVariable Long id) {
        return accountAdminService.get(id);
    }

    @PostMapping("/{id}/freeze")
    public UpstreamAccountResponse freeze(@PathVariable Long id, @RequestParam boolean frozen) {
        return accountAdminService.toggleFrozen(id, frozen);
    }

    @PostMapping("/{id}/refresh")
    public UpstreamAccountResponse refresh(@PathVariable Long id) {
        return accountAdminService.refresh(id);
    }

    @PostMapping("/{id}/network")
    public UpstreamAccountResponse updateNetwork(@PathVariable Long id, @RequestBody AccountNetworkBindingRequest request) {
        return accountAdminService.updateNetwork(id, request.proxyId(), request.tlsFingerprintProfileId());
    }

    @GetMapping("/{id}/export")
    public ExportedClientConfigResponse export(@PathVariable Long id, @RequestParam(defaultValue = "GENERIC_OPENAI") String clientFamily) {
        return accountAdminService.exportConfig(id, clientFamily);
    }
}
