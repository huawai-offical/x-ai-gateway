package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.AccountAdminService;
import com.prodigalgal.xaigateway.admin.application.OfficialAccountAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/accounts")
public class AccountAdminController {

    private final AccountAdminService accountAdminService;
    private final OfficialAccountAdminService officialAccountAdminService;

    public AccountAdminController(
            AccountAdminService accountAdminService,
            OfficialAccountAdminService officialAccountAdminService) {
        this.accountAdminService = accountAdminService;
        this.officialAccountAdminService = officialAccountAdminService;
    }

    @GetMapping("/group/{groupId}")
    public java.util.List<UpstreamAccountResponse> listByGroup(@PathVariable Long groupId) {
        return accountAdminService.listByGroup(groupId);
    }

    @GetMapping("/{id}")
    public UpstreamAccountResponse get(@PathVariable Long id) {
        return accountAdminService.get(id);
    }

    @PostMapping("/{id}/freeze")
    public UpstreamAccountResponse freeze(@PathVariable Long id, @RequestParam boolean frozen) {
        return accountAdminService.toggleFrozen(id, frozen);
    }

    @PostMapping("/{id}/runtime-reset")
    public UpstreamAccountResponse resetRuntime(@PathVariable Long id) {
        return accountAdminService.resetRuntime(id);
    }

    @PostMapping("/{id}/refresh-models")
    public AccountModelRefreshResponse refreshModels(@PathVariable Long id) {
        return accountAdminService.refreshModels(id);
    }

    @PostMapping("/import-auth-json")
    public UpstreamAccountResponse importAuthJson(@Valid @RequestBody AccountImportAuthJsonRequest request) {
        return accountAdminService.importAuthJson(request);
    }

    @PostMapping("/official/import")
    public OfficialAccountQuotaResponse importOfficialAccount(@Valid @RequestBody OfficialAccountImportRequest request) {
        return officialAccountAdminService.importOfficialAccount(request);
    }

    @PostMapping("/{id}/official/quota-refresh")
    public OfficialAccountQuotaResponse refreshOfficialQuota(
            @PathVariable Long id,
            @RequestBody(required = false) OfficialAccountQuotaRefreshRequest request) {
        return officialAccountAdminService.refreshQuota(id, request);
    }

    @PostMapping("/{id}/official/codex/responses-smoke")
    public OfficialCodexResponsesSmokeResponse codexResponsesSmoke(
            @PathVariable Long id,
            @RequestBody(required = false) OfficialCodexResponsesSmokeRequest request) {
        return officialAccountAdminService.codexResponsesSmoke(id, request);
    }
}
