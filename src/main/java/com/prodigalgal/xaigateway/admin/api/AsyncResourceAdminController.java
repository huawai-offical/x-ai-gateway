package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.AsyncResourceAdminService;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/resources/async")
public class AsyncResourceAdminController {

    private final AsyncResourceAdminService asyncResourceAdminService;

    public AsyncResourceAdminController(AsyncResourceAdminService asyncResourceAdminService) {
        this.asyncResourceAdminService = asyncResourceAdminService;
    }

    @GetMapping
    public List<AsyncResourceSummaryResponse> list(
            @RequestParam(required = false) Long distributedKeyId,
            @RequestParam(required = false) GatewayAsyncResourceType resourceType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return asyncResourceAdminService.listAsyncResources(distributedKeyId, resourceType, status, from, to);
    }

    @GetMapping("/{resourceKey}")
    public AsyncResourceDetailResponse detail(@PathVariable String resourceKey) {
        return asyncResourceAdminService.getAsyncResource(resourceKey);
    }
}
