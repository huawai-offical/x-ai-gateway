package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ClientInstanceAdminService;
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
@RequestMapping("/admin/client-instances")
public class ClientInstanceAdminController {

    private final ClientInstanceAdminService clientInstanceAdminService;

    public ClientInstanceAdminController(ClientInstanceAdminService clientInstanceAdminService) {
        this.clientInstanceAdminService = clientInstanceAdminService;
    }

    @GetMapping
    public List<ClientInstanceResponse> list(@RequestParam(required = false) Long distributedKeyId) {
        return clientInstanceAdminService.list(distributedKeyId);
    }

    @PostMapping
    public ClientInstanceResponse register(@Valid @RequestBody ClientInstanceRequest request) {
        return clientInstanceAdminService.register(request);
    }

    @GetMapping("/{id}")
    public ClientInstanceResponse get(@PathVariable Long id) {
        return clientInstanceAdminService.get(id);
    }

    @PutMapping("/{id}")
    public ClientInstanceResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ClientInstanceRequest request) {
        return clientInstanceAdminService.update(id, request);
    }

    @PostMapping("/{id}/status")
    public ClientInstanceResponse toggle(
            @PathVariable Long id,
            @RequestBody ClientInstanceStatusRequest request) {
        return clientInstanceAdminService.toggle(id, request == null ? null : request.active(), request == null ? null : request.reason());
    }

    @DeleteMapping("/{id}")
    public ClientInstanceResponse revoke(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return clientInstanceAdminService.revoke(id, reason);
    }

    @PostMapping("/{id}/authorizations")
    public ClientInstanceAuthorizationResponse issueAuthorization(
            @PathVariable Long id,
            @RequestBody ClientInstanceAuthorizationRequest request) {
        return clientInstanceAdminService.issueAuthorization(id, request);
    }

    @PostMapping("/{id}/authorizations/{grantToken}/consume")
    public ClientInstanceConfigResponse consumeAuthorization(
            @PathVariable Long id,
            @PathVariable String grantToken) {
        return clientInstanceAdminService.consumeAuthorization(id, grantToken);
    }

    @DeleteMapping("/{id}/authorizations/{grantToken}")
    public ClientInstanceAuthorizationResponse revokeAuthorization(
            @PathVariable Long id,
            @PathVariable String grantToken) {
        return clientInstanceAdminService.revokeAuthorization(id, grantToken);
    }
}
