package com.prodigalgal.xaigateway.provider.adapter.openai;

import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class OpenAiPromptCacheKeyService {

    public String build(RouteSelectionResult selectionResult) {
        String key = String.format(
                Locale.ROOT,
                "xag:pc:%d:%s:%s",
                selectionResult.distributedKeyId(),
                sanitize(selectionResult.modelGroup()),
                sanitize(selectionResult.prefixHash())
        );

        return key.length() <= 256 ? key : key.substring(0, 256);
    }

    private String sanitize(String value) {
        if (value == null) {
            return "none";
        }

        return value.replaceAll("[^a-zA-Z0-9:_\\-\\.]", "_");
    }
}
