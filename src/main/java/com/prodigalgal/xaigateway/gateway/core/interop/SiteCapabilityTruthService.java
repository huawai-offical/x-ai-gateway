package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import org.springframework.stereotype.Service;

@Service
public class SiteCapabilityTruthService {

    private final UpstreamSitePolicyService upstreamSitePolicyService;

    public SiteCapabilityTruthService(UpstreamSitePolicyService upstreamSitePolicyService) {
        this.upstreamSitePolicyService = upstreamSitePolicyService;
    }

    public InteropCapabilityLevel capabilityLevel(CatalogCandidateView candidate, InteropFeature feature) {
        if (candidate.siteKind() == null) {
            return InteropCapabilityLevel.UNSUPPORTED;
        }
        return upstreamSitePolicyService.capabilityLevel(
                candidate.siteKind(),
                candidate.supportsEmbeddings(),
                candidate.supportsThinking(),
                feature
        );
    }
}
