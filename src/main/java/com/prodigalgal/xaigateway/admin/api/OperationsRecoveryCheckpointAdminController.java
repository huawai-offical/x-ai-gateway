package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.RecoveryCheckpointService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/operations/recovery-checkpoints")
public class OperationsRecoveryCheckpointAdminController {

    private final RecoveryCheckpointService recoveryCheckpointService;

    public OperationsRecoveryCheckpointAdminController(RecoveryCheckpointService recoveryCheckpointService) {
        this.recoveryCheckpointService = recoveryCheckpointService;
    }

    @GetMapping
    public List<RecoveryCheckpointResponse> list() {
        return recoveryCheckpointService.list();
    }

    @GetMapping("/{id}")
    public RecoveryCheckpointResponse get(@PathVariable Long id) {
        return recoveryCheckpointService.get(id);
    }

    @PostMapping("/{id}/verify")
    public RecoveryCheckpointResponse verify(@PathVariable Long id, @RequestBody(required = false) RecoveryCheckpointVerifyRequest request) {
        String actor = request == null ? "console" : request.verifiedBy();
        return recoveryCheckpointService.verify(id, actor == null || actor.isBlank() ? "console" : actor);
    }
}
