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

    private final AccountPoolAdminService accountPoolAdminService;

    public DefaultResourceBootstrapService(AccountPoolAdminService accountPoolAdminService) {
        this.accountPoolAdminService = accountPoolAdminService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrapAtStartup() {
        bootstrapDefaults();
    }

    public void bootstrapDefaults() {
        var defaultPool = accountPoolAdminService.ensureDefaultPool();
        log.info("默认资源引导完成：default 账号池 id={}。", defaultPool.getId());
    }
}
