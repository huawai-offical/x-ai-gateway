package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.DashboardQueryService;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
public class DashboardAdminController {

    private final DashboardQueryService dashboardQueryService;

    public DashboardAdminController(DashboardQueryService dashboardQueryService) {
        this.dashboardQueryService = dashboardQueryService;
    }

    @GetMapping("/overview")
    public DashboardOverviewResponse overview(
            @RequestParam(required = false) Long distributedKeyId,
            @RequestParam(required = false) ProviderType providerType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer bucketMinutes) {
        return dashboardQueryService.overview(distributedKeyId, providerType, from, to, bucketMinutes);
    }
}
