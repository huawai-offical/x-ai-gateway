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

    public DefaultResourceBootstrapService(
            AccountGroupAdminService accountGroupAdminService,
            NetworkGovernanceService networkGovernanceService) {
        this.accountGroupAdminService = accountGroupAdminService;
        this.networkGovernanceService = networkGovernanceService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrapAtStartup() {
        bootstrapDefaults();
    }

    public void bootstrapDefaults() {
        var defaultGroup = accountGroupAdminService.ensureDefaultGroup();
        networkGovernanceService.ensureDefaultTlsProfiles();
        log.info("默认资源引导完成：default 账号分组 id={}，TLS 指纹默认画像已校验。", defaultGroup.getId());
    }
}
