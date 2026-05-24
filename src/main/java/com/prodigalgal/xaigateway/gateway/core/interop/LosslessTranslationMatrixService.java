package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class LosslessTranslationMatrixService {

    private static final List<CanonicalIngressProtocol> CONVERSATION_PROTOCOLS = List.of(
            CanonicalIngressProtocol.OPENAI,
            CanonicalIngressProtocol.RESPONSES,
            CanonicalIngressProtocol.ANTHROPIC_NATIVE,
            CanonicalIngressProtocol.GOOGLE_NATIVE
    );
    private static final List<CanonicalIngressProtocol> OPENAI_ANTHROPIC_PROTOCOLS = List.of(
            CanonicalIngressProtocol.OPENAI,
            CanonicalIngressProtocol.RESPONSES,
            CanonicalIngressProtocol.ANTHROPIC_NATIVE
    );
    private static final List<LosslessTranslationMatrixEntry> ENTRIES = buildEntries();

    public List<LosslessTranslationMatrixEntry> entries() {
        return ENTRIES;
    }

    public List<LosslessTranslationMatrixEntry> entriesForSemantics(
            CanonicalIngressProtocol sourceProtocol,
            CanonicalIngressProtocol targetProtocol,
            GatewayRequestSemantics semantics) {
        if (semantics == null) {
            return List.of(classify(
                    sourceProtocol,
                    targetProtocol,
                    TranslationResourceType.UNKNOWN,
                    TranslationOperation.UNKNOWN,
                    "unknown"
            ));
        }
        List<LosslessTranslationMatrixEntry> entries = new ArrayList<>();
        for (InteropFeature feature : semantics.requiredFeatures()) {
            for (String attributePath : attributePaths(feature)) {
                entries.add(classify(
                        sourceProtocol,
                        targetProtocol,
                        semantics.resourceType(),
                        semantics.operation(),
                        attributePath
                ));
            }
        }
        if (entries.isEmpty()) {
            entries.add(classify(
                    sourceProtocol,
                    targetProtocol,
                    semantics.resourceType(),
                    semantics.operation(),
                    "unknown"
            ));
        }
        return List.copyOf(entries);
    }

    public List<LosslessTranslationMatrixEntry> entriesForRequest(
            CanonicalIngressProtocol sourceProtocol,
            CanonicalIngressProtocol targetProtocol,
            GatewayRequestSemantics semantics,
            JsonNode body) {
        Set<String> attributes = requestAttributePaths(sourceProtocol, semantics, body);
        if (attributes.isEmpty()) {
            return entriesForSemantics(sourceProtocol, targetProtocol, semantics);
        }
        List<LosslessTranslationMatrixEntry> entries = new ArrayList<>();
        TranslationResourceType resourceType = semantics == null ? TranslationResourceType.UNKNOWN : semantics.resourceType();
        TranslationOperation operation = semantics == null ? TranslationOperation.UNKNOWN : semantics.operation();
        for (String attribute : attributes) {
            entries.add(classify(sourceProtocol, targetProtocol, resourceType, operation, attribute));
        }
        return List.copyOf(entries);
    }

    public List<LosslessTranslationMatrixEntry> blockingEntriesForRequest(
            CanonicalIngressProtocol sourceProtocol,
            CanonicalIngressProtocol targetProtocol,
            GatewayRequestSemantics semantics,
            JsonNode body) {
        if (!requiresTranslationValidation(sourceProtocol, targetProtocol)) {
            return List.of();
        }
        return entriesForRequest(sourceProtocol, targetProtocol, semantics, body).stream()
                .filter(LosslessTranslationMatrixEntry::mustFailWhenRequestedAsTranslation)
                .distinct()
                .toList();
    }

    public LosslessTranslationMatrixEntry classify(
            CanonicalIngressProtocol sourceProtocol,
            CanonicalIngressProtocol targetProtocol,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            String attributePath) {
        CanonicalIngressProtocol normalizedSource = normalizeProtocol(sourceProtocol);
        CanonicalIngressProtocol normalizedTarget = normalizeProtocol(targetProtocol);
        TranslationResourceType normalizedResourceType = resourceType == null
                ? TranslationResourceType.UNKNOWN
                : resourceType;
        TranslationOperation normalizedOperation = operation == null
                ? TranslationOperation.UNKNOWN
                : operation;
        String normalizedAttribute = normalizeAttribute(attributePath);

        Optional<LosslessTranslationMatrixEntry> exact = find(
                normalizedSource,
                normalizedTarget,
                normalizedResourceType,
                normalizedOperation,
                normalizedAttribute
        );
        if (exact.isPresent()) {
            return exact.get();
        }
        Optional<LosslessTranslationMatrixEntry> resourceWide = find(
                normalizedSource,
                normalizedTarget,
                normalizedResourceType,
                TranslationOperation.UNKNOWN,
                normalizedAttribute
        );
        if (resourceWide.isPresent()) {
            return resourceWide.get();
        }
        if (normalizedSource == normalizedTarget && normalizedSource != CanonicalIngressProtocol.UNKNOWN) {
            return new LosslessTranslationMatrixEntry(
                    normalizedResourceType,
                    normalizedOperation,
                    normalizedAttribute,
                    normalizedSource,
                    normalizedTarget,
                    LosslessTranslationSupport.NATIVE_REQUIRED,
                    "同协议请求必须走对应厂商 native route，不通过 gateway 翻译伪造。",
                    "native_route_required"
            );
        }
        return new LosslessTranslationMatrixEntry(
                normalizedResourceType,
                normalizedOperation,
                normalizedAttribute,
                normalizedSource,
                normalizedTarget,
                LosslessTranslationSupport.UNSUPPORTED,
                "矩阵未声明该属性可无损翻译，必须直接失败。",
                "unsupported_translation_attribute"
        );
    }

    private Optional<LosslessTranslationMatrixEntry> find(
            CanonicalIngressProtocol sourceProtocol,
            CanonicalIngressProtocol targetProtocol,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            String attributePath) {
        return ENTRIES.stream()
                .filter(entry -> entry.sourceProtocol() == sourceProtocol)
                .filter(entry -> entry.targetProtocol() == targetProtocol)
                .filter(entry -> entry.resourceType() == resourceType)
                .filter(entry -> entry.operation() == operation)
                .filter(entry -> entry.attributePath().equals(attributePath))
                .findFirst();
    }

    private static List<LosslessTranslationMatrixEntry> buildEntries() {
        List<LosslessTranslationMatrixEntry> entries = new ArrayList<>();

        addLosslessConversationAttribute(entries, "message.role");
        addLosslessConversationAttribute(entries, "content.text");
        addLosslessConversationAttribute(entries, "content.image.inline_data");
        addNativeConversationAttribute(entries, "content.image.remote_url", "remote image URL 的鉴权、抓取和缓存语义不保证跨厂商等价。");
        addLosslessConversationAttribute(entries, "content.file.inline_data");
        addNativeConversationAttribute(entries, "content.file.remote_url", "remote file URL 的鉴权、抓取和缓存语义不保证跨厂商等价。");
        addNativeConversationAttribute(entries, "content.file.provider_file_id", "provider file id 只在原厂对象生命周期内有效。");
        addLosslessOpenAiAnthropicAttribute(entries, "tool.function_schema.basic_object");
        addUnsupportedGoogleConversationAttribute(entries, "tool.function_schema.basic_object", "Gemini functionDeclarations 与 OpenAI/Anthropic JSON schema 子集不按全量 JSON Schema 等价承诺。");
        addLosslessOpenAiAnthropicAttribute(entries, "tool_result.text");
        addLosslessOpenAiAnthropicAttribute(entries, "tool_result.call_id");
        addUnsupportedGoogleConversationAttribute(entries, "tool_result.call_id", "Gemini functionResponse 使用 function name 关联，无法保留 OpenAI/Anthropic tool call id。");
        addLosslessConversationAttribute(entries, "stream.text_delta");
        addNativeConversationAttribute(entries, "stream.tool_call_delta", "增量 tool call 事件在各厂商协议中状态机不同。");
        addLosslessConversationAttribute(entries, "usage.input_output_tokens");
        addNativeConversationAttribute(entries, "usage.cache_tokens", "cache token、prompt cache 与 provider 计费细节只允许 native 暴露。");
        addNativeConversationAttribute(entries, "reasoning.thinking_budget", "reasoning/thinking 配置只允许目标厂商 native profile 执行。");
        addNativeConversationAttribute(entries, "reasoning.encrypted_content", "encrypted reasoning 是 opaque provider state，不能本地重建或翻译。");

        addNative(entries, TranslationResourceType.RESPONSE, TranslationOperation.RESPONSE_CREATE, "response.compaction", CONVERSATION_PROTOCOLS, "Responses compact 必须走 OpenAI Direct native route。", "native_compaction_required");
        addNative(entries, TranslationResourceType.RESPONSE, TranslationOperation.RESPONSE_CREATE, "response.hosted_tool.file_search", CONVERSATION_PROTOCOLS, "hosted tool lifecycle 不跨厂商翻译。", "native_hosted_tool_required");
        addNative(entries, TranslationResourceType.FILE, TranslationOperation.UNKNOWN, "file.object_lifecycle", CONVERSATION_PROTOCOLS, "file object id、状态机和内容读取必须由原厂 native lifecycle 承担。", "native_file_lifecycle_required");
        addNative(entries, TranslationResourceType.UPLOAD, TranslationOperation.UNKNOWN, "upload.multipart_lifecycle", CONVERSATION_PROTOCOLS, "multipart upload 状态机不跨厂商翻译。", "native_upload_lifecycle_required");
        addNative(entries, TranslationResourceType.IMAGE, TranslationOperation.IMAGE_GENERATION, "image.generation.request", CONVERSATION_PROTOCOLS, "图片生成参数、返回对象和安全元数据不声明跨厂商无损。", "native_image_generation_required");
        addNative(entries, TranslationResourceType.IMAGE, TranslationOperation.IMAGE_EDIT, "image.edit.request", CONVERSATION_PROTOCOLS, "图片编辑参数、mask 和返回对象不声明跨厂商无损。", "native_image_edit_required");
        addNative(entries, TranslationResourceType.IMAGE, TranslationOperation.IMAGE_VARIATION, "image.variation.request", CONVERSATION_PROTOCOLS, "图片变体参数、输入对象和返回对象不声明跨厂商无损。", "native_image_variation_required");
        addNative(entries, TranslationResourceType.AUDIO, TranslationOperation.AUDIO_TRANSCRIPTION, "audio.transcription.request", CONVERSATION_PROTOCOLS, "音频转写参数和 segment/logprob 等结果细节不声明跨厂商无损。", "native_audio_transcription_required");
        addNative(entries, TranslationResourceType.AUDIO, TranslationOperation.AUDIO_TRANSLATION, "audio.translation.request", CONVERSATION_PROTOCOLS, "音频翻译不是所有目标厂商的等价 native 资源。", "native_audio_translation_required");
        addNative(entries, TranslationResourceType.AUDIO, TranslationOperation.AUDIO_SPEECH, "audio.speech.request", CONVERSATION_PROTOCOLS, "TTS voice、format 和音频元数据不声明跨厂商无损。", "native_audio_speech_required");
        addNative(entries, TranslationResourceType.WEB_SEARCH, TranslationOperation.WEB_SEARCH_CREATE, "web_search.grounded_sources", CONVERSATION_PROTOCOLS, "web grounded source/citation contract 只按原厂或明确 provider profile 暴露。", "native_web_search_required");

        return List.copyOf(entries);
    }

    private static void addLosslessConversationAttribute(List<LosslessTranslationMatrixEntry> entries, String attributePath) {
        addLossless(entries, TranslationResourceType.CHAT, TranslationOperation.CHAT_COMPLETION, attributePath, CONVERSATION_PROTOCOLS);
        addLossless(entries, TranslationResourceType.RESPONSE, TranslationOperation.RESPONSE_CREATE, attributePath, CONVERSATION_PROTOCOLS);
    }

    private static void addLosslessOpenAiAnthropicAttribute(List<LosslessTranslationMatrixEntry> entries, String attributePath) {
        addLossless(entries, TranslationResourceType.CHAT, TranslationOperation.CHAT_COMPLETION, attributePath, OPENAI_ANTHROPIC_PROTOCOLS);
        addLossless(entries, TranslationResourceType.RESPONSE, TranslationOperation.RESPONSE_CREATE, attributePath, OPENAI_ANTHROPIC_PROTOCOLS);
    }

    private static void addNativeConversationAttribute(List<LosslessTranslationMatrixEntry> entries, String attributePath, String requirement) {
        addNative(entries, TranslationResourceType.CHAT, TranslationOperation.CHAT_COMPLETION, attributePath, CONVERSATION_PROTOCOLS, requirement, "native_route_required");
        addNative(entries, TranslationResourceType.RESPONSE, TranslationOperation.RESPONSE_CREATE, attributePath, CONVERSATION_PROTOCOLS, requirement, "native_route_required");
    }

    private static void addUnsupportedGoogleConversationAttribute(
            List<LosslessTranslationMatrixEntry> entries,
            String attributePath,
            String requirement) {
        for (CanonicalIngressProtocol protocol : OPENAI_ANTHROPIC_PROTOCOLS) {
            add(entries, TranslationResourceType.CHAT, TranslationOperation.CHAT_COMPLETION, attributePath, protocol, CanonicalIngressProtocol.GOOGLE_NATIVE, LosslessTranslationSupport.UNSUPPORTED, requirement, "unsupported_translation_attribute");
            add(entries, TranslationResourceType.CHAT, TranslationOperation.CHAT_COMPLETION, attributePath, CanonicalIngressProtocol.GOOGLE_NATIVE, protocol, LosslessTranslationSupport.UNSUPPORTED, requirement, "unsupported_translation_attribute");
            add(entries, TranslationResourceType.RESPONSE, TranslationOperation.RESPONSE_CREATE, attributePath, protocol, CanonicalIngressProtocol.GOOGLE_NATIVE, LosslessTranslationSupport.UNSUPPORTED, requirement, "unsupported_translation_attribute");
            add(entries, TranslationResourceType.RESPONSE, TranslationOperation.RESPONSE_CREATE, attributePath, CanonicalIngressProtocol.GOOGLE_NATIVE, protocol, LosslessTranslationSupport.UNSUPPORTED, requirement, "unsupported_translation_attribute");
        }
    }

    private static void addLossless(
            List<LosslessTranslationMatrixEntry> entries,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            String attributePath,
            List<CanonicalIngressProtocol> protocols) {
        for (CanonicalIngressProtocol source : protocols) {
            for (CanonicalIngressProtocol target : protocols) {
                if (source != target) {
                    add(entries, resourceType, operation, attributePath, source, target, LosslessTranslationSupport.LOSSLESS, "可通过 canonical resource 表达完整保留。", "");
                }
            }
        }
    }

    private static void addNative(
            List<LosslessTranslationMatrixEntry> entries,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            String attributePath,
            List<CanonicalIngressProtocol> protocols,
            String requirement,
            String failureCode) {
        for (CanonicalIngressProtocol source : protocols) {
            for (CanonicalIngressProtocol target : protocols) {
                if (source != target) {
                    add(entries, resourceType, operation, attributePath, source, target, LosslessTranslationSupport.NATIVE_REQUIRED, requirement, failureCode);
                }
            }
        }
    }

    private static void add(
            List<LosslessTranslationMatrixEntry> entries,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            String attributePath,
            CanonicalIngressProtocol source,
            CanonicalIngressProtocol target,
            LosslessTranslationSupport support,
            String requirement,
            String failureCode) {
        entries.add(new LosslessTranslationMatrixEntry(
                resourceType,
                operation,
                attributePath,
                source,
                target,
                support,
                requirement,
                failureCode
        ));
    }

    private List<String> attributePaths(InteropFeature feature) {
        if (feature == null) {
            return List.of("unknown");
        }
        return switch (feature) {
            case CHAT_TEXT -> List.of("message.role", "content.text");
            case TOOLS -> List.of("tool.function_schema.basic_object", "tool_result.text");
            case IMAGE_INPUT -> List.of("content.image.inline_data", "content.image.remote_url");
            case FILE_INPUT -> List.of("content.file.inline_data", "content.file.remote_url", "content.file.provider_file_id");
            case FILE_OBJECT -> List.of("file.object_lifecycle");
            case REASONING -> List.of("reasoning.thinking_budget", "reasoning.encrypted_content");
            case RESPONSE_OBJECT -> List.of("response.compaction", "response.hosted_tool.file_search");
            case EMBEDDINGS -> List.of("embedding.vector");
            case AUDIO_TRANSCRIPTION -> List.of("audio.transcription.request");
            case AUDIO_TRANSLATION -> List.of("audio.translation.request");
            case AUDIO_SPEECH -> List.of("audio.speech.request");
            case IMAGE_GENERATION -> List.of("image.generation.request");
            case IMAGE_EDIT -> List.of("image.edit.request");
            case IMAGE_VARIATION -> List.of("image.variation.request");
            case MODERATION -> List.of("moderation.request");
            case UPLOAD_CREATE -> List.of("upload.multipart_lifecycle");
            case RERANK -> List.of("rerank.document_scores");
            case VIDEO_GENERATION -> List.of("video.generation.request");
            case MUSIC_GENERATION -> List.of("music.generation.request");
            case ASYNC_TASK -> List.of("async_task.lifecycle");
            case WEB_SEARCH -> List.of("web_search.grounded_sources");
        };
    }

    private boolean requiresTranslationValidation(
            CanonicalIngressProtocol sourceProtocol,
            CanonicalIngressProtocol targetProtocol) {
        CanonicalIngressProtocol source = normalizeProtocol(sourceProtocol);
        CanonicalIngressProtocol target = normalizeProtocol(targetProtocol);
        return source != CanonicalIngressProtocol.UNKNOWN
                && target != CanonicalIngressProtocol.UNKNOWN
                && source != target;
    }

    private Set<String> requestAttributePaths(
            CanonicalIngressProtocol sourceProtocol,
            GatewayRequestSemantics semantics,
            JsonNode body) {
        LinkedHashSet<String> attributes = new LinkedHashSet<>();
        collectOperationAttributes(attributes, semantics);
        if (semantics == null
                || (semantics.resourceType() != TranslationResourceType.CHAT
                && semantics.resourceType() != TranslationResourceType.RESPONSE)) {
            return attributes;
        }
        collectCommonAttributes(attributes, body);
        switch (normalizeProtocol(sourceProtocol)) {
            case OPENAI -> collectOpenAiChatAttributes(attributes, body);
            case RESPONSES -> collectOpenAiResponsesAttributes(attributes, body);
            case ANTHROPIC_NATIVE -> collectAnthropicAttributes(attributes, body);
            case GOOGLE_NATIVE -> collectGeminiAttributes(attributes, body);
            default -> collectGenericAttributes(attributes, body);
        }
        return attributes;
    }

    private void collectOperationAttributes(Set<String> attributes, GatewayRequestSemantics semantics) {
        if (semantics == null) {
            return;
        }
        if (semantics.resourceType() == TranslationResourceType.CHAT
                || semantics.resourceType() == TranslationResourceType.RESPONSE) {
            return;
        }
        switch (semantics.operation()) {
            case IMAGE_GENERATION -> attributes.add("image.generation.request");
            case IMAGE_EDIT -> attributes.add("image.edit.request");
            case IMAGE_VARIATION -> attributes.add("image.variation.request");
            case AUDIO_TRANSCRIPTION -> attributes.add("audio.transcription.request");
            case AUDIO_TRANSLATION -> attributes.add("audio.translation.request");
            case AUDIO_SPEECH -> attributes.add("audio.speech.request");
            case WEB_SEARCH_CREATE -> attributes.add("web_search.grounded_sources");
            case FILE_CREATE, FILE_LIST, FILE_GET, FILE_CONTENT_GET, FILE_DELETE -> attributes.add("file.object_lifecycle");
            case UPLOAD_CREATE, UPLOAD_GET, UPLOAD_PART_ADD, UPLOAD_COMPLETE, UPLOAD_CANCEL -> attributes.add("upload.multipart_lifecycle");
            default -> {
            }
        }
    }

    private void collectCommonAttributes(Set<String> attributes, JsonNode body) {
        if (body == null || body.isMissingNode() || body.isNull()) {
            return;
        }
        if (body.has("reasoning") || body.has("reasoning_effort") || body.has("thinking")) {
            attributes.add("reasoning.thinking_budget");
        }
        JsonNode generationConfig = body.path("generationConfig");
        if (generationConfig.isObject()
                && (generationConfig.has("thinkingConfig")
                || generationConfig.has("thinkingBudget")
                || generationConfig.has("thinkingLevel"))) {
            attributes.add("reasoning.thinking_budget");
        }
        if (containsField(body, "encrypted_content")) {
            attributes.add("reasoning.encrypted_content");
        }
        JsonNode tools = body.path("tools");
        if (tools.isArray()) {
            boolean hasTool = false;
            for (JsonNode tool : tools) {
                String type = normalizeAttribute(tool.path("type").asText(null));
                if ("function".equals(type) || tool.has("function") || tool.has("functionDeclarations")) {
                    hasTool = true;
                }
                if ("file_search".equals(type)) {
                    attributes.add("response.hosted_tool.file_search");
                }
                if (type.contains("web_search") || tool.has("googleMaps")) {
                    attributes.add("web_search.grounded_sources");
                }
            }
            if (hasTool) {
                attributes.add("tool.function_schema.basic_object");
            }
        }
        JsonNode toolChoice = body.path("tool_choice");
        if (toolChoice.isObject() && "file_search".equalsIgnoreCase(toolChoice.path("type").asText(null))) {
            attributes.add("response.hosted_tool.file_search");
        }
        if (body.path("stream").asBoolean(false) && attributes.contains("tool.function_schema.basic_object")) {
            attributes.add("stream.tool_call_delta");
        }
    }

    private void collectOpenAiChatAttributes(Set<String> attributes, JsonNode body) {
        JsonNode messages = body == null ? null : body.path("messages");
        if (!isArray(messages)) {
            collectGenericAttributes(attributes, body);
            return;
        }
        for (JsonNode message : messages) {
            addRole(attributes, message);
            if (message.has("reasoning_content")) {
                attributes.add("reasoning.thinking_budget");
            }
            if (message.has("tool_call_id")) {
                attributes.add("tool_result.call_id");
            }
            if (isArray(message.path("tool_calls"))) {
                attributes.add("tool_result.call_id");
            }
            collectOpenAiContentAttributes(attributes, message.path("content"));
        }
    }

    private void collectOpenAiResponsesAttributes(Set<String> attributes, JsonNode body) {
        if (body == null || body.isMissingNode() || body.isNull()) {
            return;
        }
        if (body.has("instructions")) {
            attributes.add("content.text");
        }
        JsonNode input = body.path("input");
        if (input.isTextual()) {
            attributes.add("message.role");
            attributes.add("content.text");
            return;
        }
        if (input.isObject()) {
            collectOpenAiResponseItemAttributes(attributes, input);
            return;
        }
        if (input.isArray()) {
            for (JsonNode item : input) {
                collectOpenAiResponseItemAttributes(attributes, item);
            }
        }
    }

    private void collectOpenAiResponseItemAttributes(Set<String> attributes, JsonNode item) {
        if (item == null || !item.isObject()) {
            return;
        }
        addRole(attributes, item);
        if (item.has("content")) {
            collectOpenAiContentAttributes(attributes, item.path("content"));
        }
        String type = item.path("type").asText("");
        if ("input_text".equalsIgnoreCase(type) || "text".equalsIgnoreCase(type)) {
            attributes.add("content.text");
        }
        if ("input_image".equalsIgnoreCase(type) || "image_url".equalsIgnoreCase(type)) {
            collectOpenAiImageAttributes(attributes, item);
        }
        if ("input_file".equalsIgnoreCase(type)) {
            collectOpenAiFileAttributes(attributes, item);
        }
        if ("function_call_output".equalsIgnoreCase(type)) {
            attributes.add("tool_result.text");
            if (hasText(item, "call_id")) {
                attributes.add("tool_result.call_id");
            }
        }
    }

    private void collectOpenAiContentAttributes(Set<String> attributes, JsonNode content) {
        if (content == null || content.isMissingNode() || content.isNull()) {
            return;
        }
        if (content.isTextual()) {
            attributes.add("content.text");
            return;
        }
        if (!content.isArray()) {
            return;
        }
        for (JsonNode item : content) {
            String type = item.path("type").asText("");
            if ("text".equalsIgnoreCase(type) || "input_text".equalsIgnoreCase(type)) {
                attributes.add("content.text");
            } else if ("image_url".equalsIgnoreCase(type) || "input_image".equalsIgnoreCase(type)) {
                collectOpenAiImageAttributes(attributes, item);
            } else if ("input_file".equalsIgnoreCase(type)) {
                collectOpenAiFileAttributes(attributes, item);
            }
        }
    }

    private void collectOpenAiImageAttributes(Set<String> attributes, JsonNode item) {
        if (hasText(item, "file_id")) {
            attributes.add("content.file.provider_file_id");
            return;
        }
        String url = text(item, "image_url");
        if ((url == null || url.isBlank()) && item.path("image_url").isObject()) {
            url = text(item.path("image_url"), "url");
        }
        if ((url == null || url.isBlank())) {
            url = text(item, "file_url");
        }
        addUriAttribute(attributes, url, "content.image.inline_data", "content.image.remote_url");
    }

    private void collectOpenAiFileAttributes(Set<String> attributes, JsonNode item) {
        String fileId = text(item, "file_id");
        if ((fileId == null || fileId.isBlank()) && item.path("input_file").isObject()) {
            fileId = text(item.path("input_file"), "file_id");
        }
        if (fileId != null && !fileId.isBlank()) {
            attributes.add("content.file.provider_file_id");
            return;
        }
        String url = text(item, "file_url");
        if ((url == null || url.isBlank()) && item.path("input_file").isObject()) {
            JsonNode inputFile = item.path("input_file");
            url = text(inputFile, "url");
            if (url == null || url.isBlank()) {
                url = text(inputFile, "file_url");
            }
        }
        addUriAttribute(attributes, url, "content.file.inline_data", "content.file.remote_url");
    }

    private void collectAnthropicAttributes(Set<String> attributes, JsonNode body) {
        if (body == null || body.isMissingNode() || body.isNull()) {
            return;
        }
        if (body.has("system")) {
            attributes.add("content.text");
        }
        JsonNode messages = body.path("messages");
        if (!isArray(messages)) {
            collectGenericAttributes(attributes, body);
            return;
        }
        for (JsonNode message : messages) {
            addRole(attributes, message);
            JsonNode content = message.path("content");
            if (content.isTextual()) {
                attributes.add("content.text");
                continue;
            }
            if (!isArray(content)) {
                continue;
            }
            for (JsonNode block : content) {
                String type = block.path("type").asText("");
                if ("text".equalsIgnoreCase(type)) {
                    attributes.add("content.text");
                } else if ("image".equalsIgnoreCase(type)) {
                    collectAnthropicSourceAttributes(attributes, block.path("source"), true);
                } else if ("document".equalsIgnoreCase(type)) {
                    collectAnthropicSourceAttributes(attributes, block.path("source"), false);
                } else if ("tool_result".equalsIgnoreCase(type)) {
                    attributes.add("tool_result.text");
                    if (hasText(block, "tool_use_id")) {
                        attributes.add("tool_result.call_id");
                    }
                }
            }
        }
    }

    private void collectAnthropicSourceAttributes(Set<String> attributes, JsonNode source, boolean image) {
        if (source == null || !source.isObject()) {
            return;
        }
        if (hasText(source, "file_id")) {
            attributes.add("content.file.provider_file_id");
            return;
        }
        String type = source.path("type").asText("");
        if ("base64".equalsIgnoreCase(type)) {
            attributes.add(image ? "content.image.inline_data" : "content.file.inline_data");
            return;
        }
        String url = text(source, "url");
        if (url == null || url.isBlank()) {
            url = text(source, "uri");
        }
        addUriAttribute(attributes, url, image ? "content.image.inline_data" : "content.file.inline_data",
                image ? "content.image.remote_url" : "content.file.remote_url");
    }

    private void collectGeminiAttributes(Set<String> attributes, JsonNode body) {
        if (body == null || body.isMissingNode() || body.isNull()) {
            return;
        }
        if (body.has("systemInstruction")) {
            attributes.add("content.text");
        }
        JsonNode contents = body.path("contents");
        if (!isArray(contents)) {
            collectGenericAttributes(attributes, body);
            return;
        }
        for (JsonNode content : contents) {
            addRole(attributes, content);
            JsonNode parts = content.path("parts");
            if (!isArray(parts)) {
                continue;
            }
            for (JsonNode part : parts) {
                if (part.has("text")) {
                    attributes.add("content.text");
                }
                if (part.has("inlineData")) {
                    JsonNode inlineData = part.path("inlineData");
                    String mimeType = inlineData.path("mimeType").asText("");
                    attributes.add(mimeType.startsWith("image/") ? "content.image.inline_data" : "content.file.inline_data");
                }
                if (part.has("fileData")) {
                    JsonNode fileData = part.path("fileData");
                    String mimeType = fileData.path("mimeType").asText("");
                    if (hasText(fileData, "fileId")) {
                        attributes.add("content.file.provider_file_id");
                    } else {
                        addUriAttribute(attributes, text(fileData, "fileUri"),
                                mimeType.startsWith("image/") ? "content.image.inline_data" : "content.file.inline_data",
                                mimeType.startsWith("image/") ? "content.image.remote_url" : "content.file.remote_url");
                    }
                }
                if (part.has("functionResponse")) {
                    attributes.add("tool_result.text");
                    attributes.add("tool_result.call_id");
                }
            }
        }
    }

    private void collectGenericAttributes(Set<String> attributes, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            attributes.add("content.text");
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectGenericAttributes(attributes, item);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        addRole(attributes, node);
        if (hasText(node, "text") || hasText(node, "content")) {
            attributes.add("content.text");
        }
        if (hasText(node, "file_id") || hasText(node, "fileId")) {
            attributes.add("content.file.provider_file_id");
        }
        node.properties().forEach(entry -> collectGenericAttributes(attributes, entry.getValue()));
    }

    private void addRole(Set<String> attributes, JsonNode node) {
        if (hasText(node, "role")) {
            attributes.add("message.role");
        }
    }

    private void addUriAttribute(Set<String> attributes, String uri, String inlineAttribute, String remoteAttribute) {
        if (uri == null || uri.isBlank()) {
            return;
        }
        attributes.add(uri.startsWith("data:") ? inlineAttribute : remoteAttribute);
    }

    private boolean containsField(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isObject()) {
            if (node.has(fieldName)) {
                return true;
            }
            for (java.util.Map.Entry<String, JsonNode> entry : node.properties()) {
                if (containsField(entry.getValue(), fieldName)) {
                    return true;
                }
            }
            return false;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (containsField(item, fieldName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isArray(JsonNode node) {
        return node != null && node.isArray();
    }

    private boolean hasText(JsonNode node, String fieldName) {
        return text(node, fieldName) != null;
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    private CanonicalIngressProtocol normalizeProtocol(CanonicalIngressProtocol protocol) {
        return protocol == null ? CanonicalIngressProtocol.UNKNOWN : protocol;
    }

    private String normalizeAttribute(String attributePath) {
        if (attributePath == null || attributePath.isBlank()) {
            return "unknown";
        }
        return attributePath.trim().toLowerCase(Locale.ROOT);
    }
}
