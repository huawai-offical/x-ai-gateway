package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.DistributedKeyAdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
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
}
