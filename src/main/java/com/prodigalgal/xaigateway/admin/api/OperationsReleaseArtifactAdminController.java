package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.PlatformOperationsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/operations/release-artifacts")
public class OperationsReleaseArtifactAdminController {

    private final PlatformOperationsService platformOperationsService;

    public OperationsReleaseArtifactAdminController(PlatformOperationsService platformOperationsService) {
        this.platformOperationsService = platformOperationsService;
    }

    @GetMapping
    public List<ReleaseArtifactResponse> list() {
        return platformOperationsService.listReleaseArtifacts();
    }

    @PostMapping
    public ReleaseArtifactResponse create(@RequestBody ReleaseArtifactRequest request) {
        return platformOperationsService.saveReleaseArtifact(null, request);
    }

    @PutMapping("/{id}")
    public ReleaseArtifactResponse update(@PathVariable Long id, @RequestBody ReleaseArtifactRequest request) {
        return platformOperationsService.saveReleaseArtifact(id, request);
    }
}
