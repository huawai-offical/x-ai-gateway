package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ProviderSiteDossierResponse;
import com.prodigalgal.xaigateway.admin.api.ProviderSiteResponse;
import com.prodigalgal.xaigateway.admin.api.SiteModelCapabilityResponse;
import com.prodigalgal.xaigateway.admin.api.SurfaceDossierItemResponse;
import com.prodigalgal.xaigateway.gateway.core.catalog.SurfaceCapabilityView;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProviderSiteDossierService {

    private final ProviderSiteAdminService providerSiteAdminService;

    public ProviderSiteDossierService(ProviderSiteAdminService providerSiteAdminService) {
        this.providerSiteAdminService = providerSiteAdminService;
    }

    public ProviderSiteDossierResponse get(Long siteProfileId) {
        ProviderSiteResponse site = providerSiteAdminService.get(siteProfileId);
        List<SiteModelCapabilityResponse> capabilities = providerSiteAdminService.listCapabilities(siteProfileId);

        List<SurfaceDossierItemResponse> allSurfaces = site.surfaces().entrySet().stream()
                .map(this::toSurfaceItem)
                .sorted(Comparator.comparing(SurfaceDossierItemResponse::operation, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        List<SurfaceDossierItemResponse> blocked = allSurfaces.stream()
                .filter(item -> "BLOCKED".equals(item.supportStatus()))
                .toList();
        List<SurfaceDossierItemResponse> degraded = allSurfaces.stream()
                .filter(item -> "DEGRADED".equals(item.supportStatus()) || "ORCHESTRATION".equals(item.supportStatus()))
                .toList();
        List<SurfaceDossierItemResponse> acceptedExceptions = allSurfaces.stream()
                .filter(item -> item.blockerReasons().stream().anyMatch(this::isAcceptedException))
                .toList();

        return new ProviderSiteDossierResponse(
                site,
                capabilities,
                blocked,
                degraded,
                acceptedExceptions,
                buildRecommendedActions(site, blocked, degraded, acceptedExceptions)
        );
    }

    private SurfaceDossierItemResponse toSurfaceItem(Map.Entry<String, SurfaceCapabilityView> entry) {
        SurfaceCapabilityView surface = entry.getValue();
        return new SurfaceDossierItemResponse(
                entry.getKey(),
                surface.operation().name(),
                surface.normalizedPath(),
                surface.supportStatus() == null ? null : surface.supportStatus().name(),
                surface.degradationLevel() == null ? null : surface.degradationLevel().name(),
                surface.overallCapabilityLevel() == null ? null : surface.overallCapabilityLevel().name(),
                surface.blockerReasons(),
                surface.lossReasons()
        );
    }

    private List<String> buildRecommendedActions(
            ProviderSiteResponse site,
            List<SurfaceDossierItemResponse> blocked,
            List<SurfaceDossierItemResponse> degraded,
            List<SurfaceDossierItemResponse> acceptedExceptions) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        if (!blocked.isEmpty()) {
            actions.add("优先查看 blocked surfaces，确认是否需要进入 Capability Matrix 或 Incident 工作台定位受影响范围。");
        }
        if (!acceptedExceptions.isEmpty()) {
            actions.add("当前站点存在 accepted exception，建议在 Workbench 中用对应 requestPath 验证 explain / execute 结果。");
        }
        if (!degraded.isEmpty()) {
            actions.add("存在 degraded / orchestration surfaces，建议进入 Traces 对照最近 requestId 的 route 与 fallback。");
        }
        if (site.cooldownCredentialCount() > 0) {
            actions.add("站点下存在冷却中的凭证，建议跳转 Incident 或 Trace Workbench 检查治理原因。");
        }
        if (site.blockedReason() != null && !site.blockedReason().isBlank()) {
            actions.add("站点级 blockedReason 已存在，建议优先处理站点阻断原因，再检查 surface 细节。");
        }
        if (actions.isEmpty()) {
            actions.add("站点当前整体可用，优先使用 Workbench 验证关键 requestPath 的 explain / execute / trace 一致性。");
        }
        return List.copyOf(actions);
    }

    private boolean isAcceptedException(String blockerReason) {
        if (blockerReason == null || blockerReason.isBlank()) {
            return false;
        }
        String normalized = blockerReason.toLowerCase();
        return normalized.contains("accepted exception") || normalized.contains("accepted-exception");
    }
}
