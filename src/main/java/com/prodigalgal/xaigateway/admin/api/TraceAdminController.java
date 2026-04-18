package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.TraceWorkbenchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/traces")
public class TraceAdminController {

    private final TraceWorkbenchService traceWorkbenchService;

    public TraceAdminController(TraceWorkbenchService traceWorkbenchService) {
        this.traceWorkbenchService = traceWorkbenchService;
    }

    @GetMapping("/lookup")
    public TraceLookupResponse lookup(
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String gatewayResourceKey,
            @RequestParam(required = false) String upstreamObjectId) {
        return traceWorkbenchService.lookup(requestId, gatewayResourceKey, upstreamObjectId);
    }
}
