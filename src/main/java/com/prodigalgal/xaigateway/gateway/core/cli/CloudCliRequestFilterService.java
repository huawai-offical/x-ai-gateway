package com.prodigalgal.xaigateway.gateway.core.cli;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalPartType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class CloudCliRequestFilterService {

    private static final String MASK = "[FILTERED]";

    public CloudCliRequestFilterResult apply(
            CanonicalRequest request,
            GatewayClientFamily clientFamily,
            List<CloudCliRequestFilterRule> rules) {
        if (request == null || rules == null || rules.isEmpty()) {
            return new CloudCliRequestFilterResult(request, List.of(), List.of());
        }

        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<CanonicalMessage> messages = request.messages() == null ? List.of() : request.messages();
        List<CanonicalMessage> filteredMessages = messages;

        for (CloudCliRequestFilterRule rule : rules) {
            if (!valid(rule, clientFamily)) {
                skipped.add(ruleId(rule));
                continue;
            }
            FilterPass pass = filterMessages(filteredMessages, rule);
            if (pass.applied()) {
                filteredMessages = pass.messages();
                applied.add(ruleId(rule));
            } else {
                skipped.add(ruleId(rule));
            }
        }

        CanonicalRequest filteredRequest = new CanonicalRequest(
                request.distributedKeyPrefix(),
                request.ingressProtocol(),
                request.requestPath(),
                request.requestedModel(),
                List.copyOf(filteredMessages),
                request.tools(),
                request.toolChoice(),
                request.temperature(),
                request.maxTokens(),
                request.reasoning(),
                request.providerExtensions()
        );
        return new CloudCliRequestFilterResult(filteredRequest, List.copyOf(applied), List.copyOf(skipped));
    }

    private FilterPass filterMessages(List<CanonicalMessage> messages, CloudCliRequestFilterRule rule) {
        List<CanonicalMessage> result = new ArrayList<>();
        boolean changed = false;
        for (CanonicalMessage message : messages) {
            if (!matchesRole(message.role(), rule.role())) {
                result.add(message);
                continue;
            }
            PartPass partPass = filterParts(message.parts(), rule);
            if (partPass.changed()) {
                changed = true;
                if (!partPass.parts().isEmpty()) {
                    result.add(new CanonicalMessage(message.role(), List.copyOf(partPass.parts())));
                }
            } else {
                result.add(message);
            }
        }
        return new FilterPass(List.copyOf(result), changed);
    }

    private PartPass filterParts(List<CanonicalContentPart> parts, CloudCliRequestFilterRule rule) {
        List<CanonicalContentPart> result = new ArrayList<>();
        boolean changed = false;
        for (CanonicalContentPart part : parts == null ? List.<CanonicalContentPart>of() : parts) {
            if (part.type() != CanonicalPartType.TEXT || part.text() == null || !part.text().contains(rule.contains())) {
                result.add(part);
                continue;
            }
            changed = true;
            switch (rule.action()) {
                case REMOVE -> addTextIfPresent(result, part.text().replace(rule.contains(), ""));
                case MASK -> result.add(CanonicalContentPart.text(part.text().replace(rule.contains(), MASK)));
                case REPLACE -> result.add(CanonicalContentPart.text(part.text().replace(rule.contains(), rule.replacement())));
            }
        }
        return new PartPass(List.copyOf(result), changed);
    }

    private boolean valid(CloudCliRequestFilterRule rule, GatewayClientFamily clientFamily) {
        if (rule == null || rule.action() == null || rule.contains() == null || rule.contains().isBlank()) {
            return false;
        }
        if (rule.action() == CloudCliRequestFilterAction.REPLACE
                && (rule.replacement() == null || rule.replacement().isBlank())) {
            return false;
        }
        if (rule.clientFamilies() == null || rule.clientFamilies().isEmpty()) {
            return true;
        }
        for (String raw : rule.clientFamilies()) {
            try {
                if (GatewayClientFamily.from(raw) == clientFamily) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        return false;
    }

    private boolean matchesRole(CanonicalMessageRole messageRole, String ruleRole) {
        if (ruleRole == null || ruleRole.isBlank() || "all".equalsIgnoreCase(ruleRole)) {
            return true;
        }
        return messageRole != null && messageRole.name().equals(ruleRole.trim().toUpperCase(Locale.ROOT));
    }

    private String ruleId(CloudCliRequestFilterRule rule) {
        if (rule == null || rule.ruleId() == null || rule.ruleId().isBlank()) {
            return "unnamed";
        }
        return rule.ruleId();
    }

    private void addTextIfPresent(List<CanonicalContentPart> result, String text) {
        if (text != null && !text.isBlank()) {
            result.add(CanonicalContentPart.text(text));
        }
    }

    private record FilterPass(List<CanonicalMessage> messages, boolean applied) {
    }

    private record PartPass(List<CanonicalContentPart> parts, boolean changed) {
    }
}
