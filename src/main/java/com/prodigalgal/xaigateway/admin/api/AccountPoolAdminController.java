package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.AccountPoolAdminService;
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
}
