package com.prodigalgal.xaigateway.admin.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultResourceBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(DefaultResourceBootstrapService.class);

    private final AccountGroupAdminService accountGroupAdminService;
    private final NetworkGovernanceService networkGovernanceService;
    private final ProviderSiteRegistryService providerSiteRegistryService;

    public DefaultResourceBootstrapService(
            AccountGroupAdminService accountGroupAdminService,
            NetworkGovernanceService networkGovernanceService,
            ProviderSiteRegistryService providerSiteRegistryService) {
        this.accountGroupAdminService = accountGroupAdminService;
        this.networkGovernanceService = networkGovernanceService;
        this.providerSiteRegistryService = providerSiteRegistryService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrapAtStartup() {
        bootstrapDefaults();
    }

    public void bootstrapDefaults() {
        var defaultGroup = accountGroupAdminService.ensureDefaultGroup();
        networkGovernanceService.ensureDefaultTlsProfiles();
        var providerSites = providerSiteRegistryService.importDefaultPresets();
        log.info(
                "默认资源引导完成：default 账号分组 id={}，TLS 指纹默认画像已校验，默认厂商 API 入口={} 个。",
                defaultGroup.getId(),
                providerSites.size()
        );
    }
}
