package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.DashboardExternalAppService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/integrations/external-apps")
public class DashboardExternalAppAdminController {

    private final DashboardExternalAppService service;

    public DashboardExternalAppAdminController(DashboardExternalAppService service) {
        this.service = service;
    }

    @GetMapping
    public List<DashboardExternalAppResponse> list() {
        return service.list();
    }

    @GetMapping("/nav")
    public List<DashboardExternalAppResponse> navApps() {
        return service.navApps();
    }

    @PostMapping
    public DashboardExternalAppResponse create(@RequestBody DashboardExternalAppRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public DashboardExternalAppResponse update(@PathVariable Long id, @RequestBody DashboardExternalAppRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/{id}/signed-context")
    public ExternalAppSignedContextResponse signedContext(
            @PathVariable Long id,
            @RequestParam(required = false) String origin,
            @RequestParam(defaultValue = "console") String actor,
            @RequestParam(defaultValue = "300") long ttlSeconds) {
        return service.preview(id, origin, actor, ttlSeconds);
    }

    @GetMapping("/runtime/{slug}")
    public ExternalAppRuntimeResponse runtime(
            @PathVariable String slug,
            @RequestParam(required = false) String origin,
            @RequestParam(defaultValue = "console-extension-runtime") String actor,
            @RequestParam(defaultValue = "300") long ttlSeconds) {
        return service.runtime(slug, origin, actor, ttlSeconds);
    }

    @PostMapping("/{slug}/verify")
    public ExternalAppVerifyResponse verify(@PathVariable String slug, @RequestBody ExternalAppVerifyRequest request) {
        return service.verify(slug, request);
    }
}
