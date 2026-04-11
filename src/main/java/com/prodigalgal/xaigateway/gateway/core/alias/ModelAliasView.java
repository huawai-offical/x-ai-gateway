package com.prodigalgal.xaigateway.gateway.core.alias;

import java.util.List;

public record ModelAliasView(
        Long id,
        String aliasName,
        String aliasKey,
        List<ModelAliasRuleView> rules
) {
}
