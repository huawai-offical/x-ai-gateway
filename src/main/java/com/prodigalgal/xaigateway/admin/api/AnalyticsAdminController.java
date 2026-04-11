package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.AnalyticsQueryService;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/analytics")
public class AnalyticsAdminController {

    private final AnalyticsQueryService analyticsQueryService;

    public AnalyticsAdminController(AnalyticsQueryService analyticsQueryService) {
        this.analyticsQueryService = analyticsQueryService;
    }

    @GetMapping("/overview")
    public AnalyticsOverviewResponse overview(
            @RequestParam(required = false) Long distributedKeyId,
            @RequestParam(required = false) ProviderType providerType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer bucketMinutes) {
        return analyticsQueryService.overview(distributedKeyId, providerType, from, to, bucketMinutes);
    }
}
