package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record ErrorRulePreviewResponse(
        List<ErrorRuleResponse> matchedRules
) {
}
