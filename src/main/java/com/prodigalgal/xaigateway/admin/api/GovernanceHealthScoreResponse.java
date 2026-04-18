package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record GovernanceHealthScoreResponse(
        List<SiteHealthScoreResponse> sites,
        List<CredentialHealthScoreResponse> credentials
) {
}
