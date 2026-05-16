package com.prodigalgal.xaigateway.gateway.core.cli;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalPartType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class CloudCliRequestFilterService {

    private static final String MASK = "[FILTERED]";
    private static final String REDACTED = "[REDACTED]";

    public CloudCliRequestFilterResult apply(
            CanonicalRequest request,
            GatewayClientFamily clientFamily,
            List<CloudCliRequestFilterRule> rules) {
        if (request == null || rules == null || rules.isEmpty()) {
            return new CloudCliRequestFilterResult(request, List.of(), List.of());
        }

        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<CloudCliRequestFilterHit> hits = new ArrayList<>();
        CanonicalRequest current = request;
        boolean denied = false;
        String denyRuleId = null;
        String denyReason = null;

        for (CloudCliRequestFilterRule rule : rules) {
            if (!valid(rule, clientFamily)) {
                skipped.add(ruleId(rule));
                continue;
            }
            ApplyResult result = applyRule(current, rule);
            if (result.applied()) {
                current = result.request();
                applied.add(ruleId(rule));
                hits.add(result.hit());
                if (result.denied()) {
                    denied = true;
                    denyRuleId = ruleId(rule);
                    denyReason = result.hit().summary();
                    break;
                }
            } else {
                skipped.add(ruleId(rule));
            }
        }

        return new CloudCliRequestFilterResult(
                current,
                List.copyOf(applied),
                List.copyOf(skipped),
                List.copyOf(hits),
                denied,
                denyRuleId,
                denyReason
        );
    }

    private ApplyResult applyRule(CanonicalRequest request, CloudCliRequestFilterRule rule) {
        return switch (target(rule)) {
            case "json_path", "provider_extensions" -> filterProviderExtensions(request, rule);
            case "tool_schema", "tool" -> filterTools(request, rule);
            case "file_metadata", "file" -> filterFileMetadata(request, rule);
            default -> filterMessages(request, rule);
        };
    }

    private ApplyResult filterMessages(CanonicalRequest request, CloudCliRequestFilterRule rule) {
        List<CanonicalMessage> messages = request.messages() == null ? List.of() : request.messages();
        List<CanonicalMessage> result = new ArrayList<>();
        boolean changed = false;
        boolean denied = false;
        int affected = 0;

        for (CanonicalMessage message : messages) {
            if (!matchesRole(message.role(), rule.role())) {
                result.add(message);
                continue;
            }
            PartPass partPass = filterTextParts(message.parts(), rule);
            if (partPass.denied()) {
                denied = true;
                affected += partPass.affected();
                result.add(message);
                continue;
            }
            if (partPass.changed()) {
                changed = true;
                affected += partPass.affected();
                if (!partPass.parts().isEmpty()) {
                    result.add(new CanonicalMessage(message.role(), List.copyOf(partPass.parts())));
                }
            } else {
                result.add(message);
            }
        }

        if (!changed && !denied) {
            return ApplyResult.skipped(request);
        }
        CanonicalRequest next = copyRequest(request, List.copyOf(result), request.tools(), request.providerExtensions());
        return ApplyResult.applied(next, hit(rule, "message_text", rolePath(rule), summary(rule, affected)), denied);
    }

    private PartPass filterTextParts(List<CanonicalContentPart> parts, CloudCliRequestFilterRule rule) {
        List<CanonicalContentPart> result = new ArrayList<>();
        boolean changed = false;
        boolean denied = false;
        int affected = 0;
        for (CanonicalContentPart part : parts == null ? List.<CanonicalContentPart>of() : parts) {
            if (part.type() != CanonicalPartType.TEXT || part.text() == null || !containsMatch(part.text(), rule)) {
                result.add(part);
                continue;
            }
            affected++;
            if (rule.action() == CloudCliRequestFilterAction.DENY) {
                denied = true;
                result.add(part);
                continue;
            }
            changed = true;
            switch (rule.action()) {
                case REMOVE -> addTextIfPresent(result, part.text().replace(rule.contains(), ""));
                case MASK -> result.add(CanonicalContentPart.text(blank(rule.contains()) ? MASK : part.text().replace(rule.contains(), MASK)));
                case REDACT -> result.add(CanonicalContentPart.text(blank(rule.contains()) ? REDACTED : part.text().replace(rule.contains(), REDACTED)));
                case REPLACE -> result.add(CanonicalContentPart.text(part.text().replace(rule.contains(), rule.replacement())));
                case DENY -> result.add(part);
            }
        }
        return new PartPass(List.copyOf(result), changed, denied, affected);
    }

    private ApplyResult filterProviderExtensions(CanonicalRequest request, CloudCliRequestFilterRule rule) {
        JsonNode providerExtensions = request.providerExtensions();
        if (providerExtensions == null || providerExtensions.isNull() || providerExtensions.isMissingNode()) {
            return ApplyResult.skipped(request);
        }
        JsonNode next = providerExtensions.deepCopy();
        JsonPathResult result = applyJsonPath(next, rule, "providerExtensions");
        if (!result.applied()) {
            return ApplyResult.skipped(request);
        }
        return ApplyResult.applied(
                copyRequest(request, request.messages(), request.tools(), next),
                hit(rule, "provider_extensions", rule.path(), summary(rule, result.affected())),
                result.denied()
        );
    }

    private ApplyResult filterTools(CanonicalRequest request, CloudCliRequestFilterRule rule) {
        List<CanonicalToolDefinition> tools = request.tools() == null ? List.of() : request.tools();
        if (tools.isEmpty()) {
            return ApplyResult.skipped(request);
        }
        List<CanonicalToolDefinition> nextTools = new ArrayList<>();
        boolean applied = false;
        boolean denied = false;
        int affected = 0;
        for (CanonicalToolDefinition tool : tools) {
            JsonNode schema = tool.inputSchema();
            if (schema == null || schema.isMissingNode() || schema.isNull()) {
                nextTools.add(tool);
                continue;
            }
            JsonNode nextSchema = schema.deepCopy();
            JsonPathResult result = applyJsonPath(nextSchema, rule, "tools." + tool.name());
            applied = applied || result.applied();
            denied = denied || result.denied();
            affected += result.affected();
            nextTools.add(new CanonicalToolDefinition(tool.name(), tool.description(), nextSchema, tool.strict()));
        }
        if (!applied) {
            return ApplyResult.skipped(request);
        }
        return ApplyResult.applied(
                copyRequest(request, request.messages(), List.copyOf(nextTools), request.providerExtensions()),
                hit(rule, "tool_schema", rule.path(), summary(rule, affected)),
                denied
        );
    }

    private ApplyResult filterFileMetadata(CanonicalRequest request, CloudCliRequestFilterRule rule) {
        List<CanonicalMessage> messages = request.messages() == null ? List.of() : request.messages();
        List<CanonicalMessage> nextMessages = new ArrayList<>();
        boolean applied = false;
        boolean denied = false;
        int affected = 0;

        for (CanonicalMessage message : messages) {
            List<CanonicalContentPart> parts = new ArrayList<>();
            for (CanonicalContentPart part : message.parts() == null ? List.<CanonicalContentPart>of() : message.parts()) {
                if (part.type() != CanonicalPartType.FILE && part.type() != CanonicalPartType.IMAGE) {
                    parts.add(part);
                    continue;
                }
                FilePartResult result = filterFilePart(part, rule);
                applied = applied || result.applied();
                denied = denied || result.denied();
                affected += result.applied() ? 1 : 0;
                if (result.part() != null) {
                    parts.add(result.part());
                }
            }
            nextMessages.add(new CanonicalMessage(message.role(), List.copyOf(parts)));
        }
        if (!applied) {
            return ApplyResult.skipped(request);
        }
        return ApplyResult.applied(
                copyRequest(request, List.copyOf(nextMessages), request.tools(), request.providerExtensions()),
                hit(rule, "file_metadata", rule.path(), summary(rule, affected)),
                denied
        );
    }

    private FilePartResult filterFilePart(CanonicalContentPart part, CloudCliRequestFilterRule rule) {
        String field = fileMetadataField(rule.path());
        String value = switch (field) {
            case "mime_type", "mimetype" -> part.mimeType();
            case "uri", "url" -> part.uri();
            default -> part.name();
        };
        if (value == null || !containsMatch(value, rule)) {
            return new FilePartResult(part, false, false);
        }
        if (rule.action() == CloudCliRequestFilterAction.DENY) {
            return new FilePartResult(part, true, true);
        }
        if (rule.action() == CloudCliRequestFilterAction.REMOVE) {
            return new FilePartResult(null, true, false);
        }
        String nextValue = switch (rule.action()) {
            case REPLACE -> value.replace(rule.contains(), rule.replacement());
            case REDACT -> REDACTED;
            case MASK -> blank(rule.contains()) ? MASK : value.replace(rule.contains(), MASK);
            default -> value;
        };
        CanonicalContentPart next = new CanonicalContentPart(
                part.type(),
                part.text(),
                field.equals("mime_type") || field.equals("mimetype") ? nextValue : part.mimeType(),
                field.equals("uri") || field.equals("url") ? nextValue : part.uri(),
                field.equals("name") || field.equals("filename") ? nextValue : part.name(),
                part.toolCallId(),
                part.toolName()
        );
        return new FilePartResult(next, true, false);
    }

    private JsonPathResult applyJsonPath(JsonNode root, CloudCliRequestFilterRule rule, String targetName) {
        List<PathToken> tokens = parsePath(rule.path());
        if (tokens.isEmpty()) {
            return JsonPathResult.skipped();
        }
        List<PathMatch> matches = new ArrayList<>();
        collectMatches(root, tokens, 0, null, null, matches);
        boolean applied = false;
        boolean denied = false;
        int affected = 0;
        for (PathMatch match : matches) {
            JsonNode value = match.value();
            if (value == null || value.isMissingNode() || value.isNull()) {
                continue;
            }
            String text = value.isTextual() ? value.asText() : value.toString();
            if (!containsMatch(text, rule)) {
                continue;
            }
            affected++;
            if (rule.action() == CloudCliRequestFilterAction.DENY) {
                denied = true;
                applied = true;
                continue;
            }
            if (match.parent() == null) {
                continue;
            }
            applied = true;
            if (rule.action() == CloudCliRequestFilterAction.REMOVE) {
                removeChild(match);
                continue;
            }
            String nextValue = switch (rule.action()) {
                case REPLACE -> text.replace(rule.contains(), rule.replacement());
                case REDACT -> REDACTED;
                case MASK -> rule.contains() == null || rule.contains().isBlank() ? MASK : text.replace(rule.contains(), MASK);
                default -> text;
            };
            replaceChild(match, nextValue);
        }
        return new JsonPathResult(applied, denied, affected, targetName);
    }

    private void collectMatches(JsonNode current, List<PathToken> tokens, int index, JsonNode parent, Object key, List<PathMatch> matches) {
        if (index >= tokens.size()) {
            matches.add(new PathMatch(parent, key, current));
            return;
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return;
        }
        PathToken token = tokens.get(index);
        if (token.wildcard()) {
            if (current.isArray()) {
                for (int i = 0; i < current.size(); i++) {
                    collectMatches(current.get(i), tokens, index + 1, current, i, matches);
                }
            } else if (current.isObject()) {
                current.properties().forEach(entry -> collectMatches(entry.getValue(), tokens, index + 1, current, entry.getKey(), matches));
            }
            return;
        }
        JsonNode next = current.path(token.name());
        collectMatches(next, tokens, index + 1, current, token.name(), matches);
    }

    private void removeChild(PathMatch match) {
        if (match.parent() instanceof ObjectNode objectNode && match.key() instanceof String field) {
            objectNode.remove(field);
        } else if (match.parent() instanceof ArrayNode arrayNode && match.key() instanceof Integer index && index >= 0 && index < arrayNode.size()) {
            arrayNode.remove(index);
        }
    }

    private void replaceChild(PathMatch match, String nextValue) {
        if (match.parent() instanceof ObjectNode objectNode && match.key() instanceof String field) {
            objectNode.put(field, nextValue);
        } else if (match.parent() instanceof ArrayNode arrayNode && match.key() instanceof Integer index && index >= 0 && index < arrayNode.size()) {
            arrayNode.set(index, arrayNode.textNode(nextValue));
        }
    }

    private List<PathToken> parsePath(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        String normalized = path.trim();
        if (normalized.startsWith("$.")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        List<PathToken> tokens = new ArrayList<>();
        for (String raw : normalized.split("\\.")) {
            if (raw.isBlank()) {
                continue;
            }
            String item = raw.trim();
            if (item.endsWith("[*]")) {
                String field = item.substring(0, item.length() - 3);
                if (!field.isBlank()) {
                    tokens.add(new PathToken(field, false));
                }
                tokens.add(new PathToken("*", true));
            } else if ("*".equals(item) || "[*]".equals(item)) {
                tokens.add(new PathToken("*", true));
            } else {
                tokens.add(new PathToken(item, false));
            }
        }
        return List.copyOf(tokens);
    }

    private boolean valid(CloudCliRequestFilterRule rule, GatewayClientFamily clientFamily) {
        if (rule == null || rule.action() == null) {
            return false;
        }
        if ((target(rule).equals("json_path") || target(rule).equals("provider_extensions") || target(rule).equals("tool_schema"))
                && (rule.path() == null || rule.path().isBlank())) {
            return false;
        }
        if (rule.action() == CloudCliRequestFilterAction.REPLACE
                && (rule.replacement() == null || rule.replacement().isBlank())) {
            return false;
        }
        if (target(rule).equals("message_text")
                && (rule.contains() == null || rule.contains().isBlank())) {
            return false;
        }
        if (rule.action() != CloudCliRequestFilterAction.MASK
                && rule.action() != CloudCliRequestFilterAction.REDACT
                && (rule.contains() == null || rule.contains().isBlank())) {
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

    private CanonicalRequest copyRequest(
            CanonicalRequest request,
            List<CanonicalMessage> messages,
            List<CanonicalToolDefinition> tools,
            JsonNode providerExtensions) {
        return new CanonicalRequest(
                request.distributedKeyPrefix(),
                request.ingressProtocol(),
                request.requestPath(),
                request.requestedModel(),
                messages == null ? List.of() : List.copyOf(messages),
                tools == null ? List.of() : List.copyOf(tools),
                request.toolChoice(),
                request.temperature(),
                request.maxTokens(),
                request.reasoning(),
                providerExtensions,
                request.metadata()
        );
    }

    private boolean containsMatch(String value, CloudCliRequestFilterRule rule) {
        if (rule.contains() == null || rule.contains().isBlank()) {
            return true;
        }
        return value != null && value.contains(rule.contains());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
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

    private String target(CloudCliRequestFilterRule rule) {
        return rule.target() == null || rule.target().isBlank()
                ? "message_text"
                : rule.target().trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private String fileMetadataField(String path) {
        if (path == null || path.isBlank()) {
            return "name";
        }
        String normalized = path.trim().toLowerCase(Locale.ROOT);
        int index = Math.max(normalized.lastIndexOf('.'), normalized.lastIndexOf('/'));
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private String rolePath(CloudCliRequestFilterRule rule) {
        return rule.role() == null || rule.role().isBlank() ? "messages[*]" : "messages[" + rule.role().trim().toLowerCase(Locale.ROOT) + "]";
    }

    private CloudCliRequestFilterHit hit(CloudCliRequestFilterRule rule, String target, String path, String summary) {
        return new CloudCliRequestFilterHit(ruleId(rule), rule.action().name(), target, path, summary);
    }

    private String summary(CloudCliRequestFilterRule rule, int affected) {
        String action = rule.action().name().toLowerCase(Locale.ROOT);
        String target = target(rule);
        return action + " " + affected + " " + target + " value(s)";
    }

    private void addTextIfPresent(List<CanonicalContentPart> result, String text) {
        if (text != null && !text.isBlank()) {
            result.add(CanonicalContentPart.text(text));
        }
    }

    private record ApplyResult(CanonicalRequest request, boolean applied, CloudCliRequestFilterHit hit, boolean denied) {
        static ApplyResult skipped(CanonicalRequest request) {
            return new ApplyResult(request, false, null, false);
        }

        static ApplyResult applied(CanonicalRequest request, CloudCliRequestFilterHit hit, boolean denied) {
            return new ApplyResult(request, true, hit, denied);
        }
    }

    private record PartPass(List<CanonicalContentPart> parts, boolean changed, boolean denied, int affected) {
    }

    private record FilePartResult(CanonicalContentPart part, boolean applied, boolean denied) {
    }

    private record JsonPathResult(boolean applied, boolean denied, int affected, String targetName) {
        static JsonPathResult skipped() {
            return new JsonPathResult(false, false, 0, null);
        }
    }

    private record PathToken(String name, boolean wildcard) {
    }

    private record PathMatch(JsonNode parent, Object key, JsonNode value) {
    }
}
