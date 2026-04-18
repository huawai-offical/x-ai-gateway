package com.prodigalgal.xaigateway.admin.application.integrations;

public enum PlatformEventType {
    ALERT_OPENED,
    ALERT_ACKED,
    ALERT_RESOLVED,
    SITE_QUARANTINED,
    SITE_RESUMED,
    BUDGET_EXCEEDED,
    UPGRADE_STARTED,
    UPGRADE_FAILED,
    UPGRADE_ROLLED_BACK,
    KEY_DISABLED,
    ACCOUNT_EXPIRED,
    OAUTH_REFRESH_FAILED
}
