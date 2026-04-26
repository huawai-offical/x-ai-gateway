package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.AccessGroupAdminService;
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
@RequestMapping("/admin/access-groups")
public class AccessGroupAdminController {

    private final AccessGroupAdminService accessGroupAdminService;

    public AccessGroupAdminController(AccessGroupAdminService accessGroupAdminService) {
        this.accessGroupAdminService = accessGroupAdminService;
    }

    @GetMapping
    public List<AccessGroupResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active) {
        return accessGroupAdminService.list(keyword, active);
    }

    @GetMapping("/{id}")
    public AccessGroupResponse get(@PathVariable Long id) {
        return accessGroupAdminService.get(id);
    }

    @PostMapping
    public AccessGroupResponse create(@Valid @RequestBody AccessGroupRequest request) {
        return accessGroupAdminService.create(request);
    }

    @PutMapping("/{id}")
    public AccessGroupResponse update(@PathVariable Long id, @Valid @RequestBody AccessGroupRequest request) {
        return accessGroupAdminService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        accessGroupAdminService.delete(id);
    }

    @PostMapping("/{id}/plans")
    public AccessGroupResponse bindPlan(
            @PathVariable Long id,
            @Valid @RequestBody AccessGroupPlanBindingRequest request) {
        return accessGroupAdminService.bindPlan(id, request);
    }

    @DeleteMapping("/{id}/plans/{planId}")
    public AccessGroupResponse removePlanBinding(@PathVariable Long id, @PathVariable Long planId) {
        return accessGroupAdminService.removePlanBinding(id, planId);
    }

    @PostMapping("/{id}/distributed-keys")
    public AccessGroupResponse grantDistributedKey(
            @PathVariable Long id,
            @Valid @RequestBody AccessGroupKeyGrantRequest request) {
        return accessGroupAdminService.grantDistributedKey(id, request);
    }

    @DeleteMapping("/{id}/distributed-keys/{distributedKeyId}")
    public AccessGroupResponse removeDistributedKeyGrant(
            @PathVariable Long id,
            @PathVariable Long distributedKeyId) {
        return accessGroupAdminService.removeDistributedKeyGrant(id, distributedKeyId);
    }

    @GetMapping("/distributed-keys/{distributedKeyId}/resolved-policy")
    public AccessGroupResolvedPolicyResponse resolveDistributedKeyPolicy(@PathVariable Long distributedKeyId) {
        return accessGroupAdminService.resolveDistributedKeyPolicy(distributedKeyId);
    }
}
