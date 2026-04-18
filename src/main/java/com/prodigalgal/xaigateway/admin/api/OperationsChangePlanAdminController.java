package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.PlatformChangePlanService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/operations/change-plans")
public class OperationsChangePlanAdminController {

    private final PlatformChangePlanService platformChangePlanService;

    public OperationsChangePlanAdminController(PlatformChangePlanService platformChangePlanService) {
        this.platformChangePlanService = platformChangePlanService;
    }

    @GetMapping
    public List<ChangePlanResponse> list() {
        return platformChangePlanService.list();
    }

    @PostMapping
    public ChangePlanResponse create(@RequestBody ChangePlanRequest request) {
        return platformChangePlanService.create(request);
    }

    @GetMapping("/{id}")
    public ChangePlanResponse get(@PathVariable Long id) {
        return platformChangePlanService.get(id);
    }

    @PostMapping("/{id}/approve")
    public ChangePlanResponse approve(@PathVariable Long id, @RequestBody ChangePlanApproveRequest request) {
        return platformChangePlanService.approve(id, request);
    }

    @PostMapping("/{id}/reject")
    public ChangePlanResponse reject(@PathVariable Long id, @RequestBody ChangePlanRejectRequest request) {
        return platformChangePlanService.reject(id, request);
    }

    @PostMapping("/{id}/execute")
    public ChangePlanResponse execute(@PathVariable Long id, @RequestBody(required = false) ChangePlanExecuteRequest request) {
        return platformChangePlanService.execute(id, request == null ? new ChangePlanExecuteRequest(null, null, null, null, null) : request);
    }

    @PostMapping("/{id}/cancel")
    public ChangePlanResponse cancel(@PathVariable Long id, @RequestBody ChangePlanCancelRequest request) {
        return platformChangePlanService.cancel(id, request);
    }
}
