package com.prodigalgal.xaigateway.admin.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.admin.api.AdminChatExecuteRequest;
import com.prodigalgal.xaigateway.admin.api.AdminChatExecuteResponse;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatRuntime;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatRuntimeContext;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatRuntimeResult;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamChunk;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResource;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityService;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.provider.adapter.PreparedChatExecution;
import com.prodigalgal.xaigateway.provider.adapter.ProviderExecutionSupportService;
import com.prodigalgal.xaigateway.provider.adapter.anthropic.AnthropicChatModelFactory;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory;
import com.prodigalgal.xaigateway.provider.adapter.openai.OpenAiChatModelFactory;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

@Service
@Transactional
public class GatewayChatExecutionService {

    private final GatewayRouteSelectionService gatewayRouteSelectionService;
    private final ProviderExecutionSupportService providerExecutionSupportService;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final GatewayObservabilityService gatewayObservabilityService;
    private final DistributedKeyGovernanceService distributedKeyGovernanceService;
    private final DistributedKeyQueryService distributedKeyQueryService;
    private final AccountSelectionService accountSelectionService;
    private final GatewayFileService gatewayFileService;
    private final OpenAiChatModelFactory openAiChatModelFactory;
    private final AnthropicChatModelFactory anthropicChatModelFactory;
    private final GeminiChatModelFactory geminiChatModelFactory;
    private final List<GatewayChatRuntime> gatewayChatRuntimes;

    public GatewayChatExecutionService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            ProviderExecutionSupportService providerExecutionSupportService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            GatewayObservabilityService gatewayObservabilityService,
            DistributedKeyGovernanceService distributedKeyGovernanceService,
            DistributedKeyQueryService distributedKeyQueryService,
            AccountSelectionService accountSelectionService,
            GatewayFileService gatewayFileService,
            OpenAiChatModelFactory openAiChatModelFactory,
            AnthropicChatModelFactory anthropicChatModelFactory,
            GeminiChatModelFactory geminiChatModelFactory,
            List<GatewayChatRuntime> gatewayChatRuntimes) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.providerExecutionSupportService = providerExecutionSupportService;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.gatewayObservabilityService = gatewayObservabilityService;
        this.distributedKeyGovernanceService = distributedKeyGovernanceService;
        this.distributedKeyQueryService = distributedKeyQueryService;
        this.accountSelectionService = accountSelectionService;
        this.gatewayFileService = gatewayFileService;
        this.openAiChatModelFactory = openAiChatModelFactory;
        this.anthropicChatModelFactory = anthropicChatModelFactory;
        this.geminiChatModelFactory = geminiChatModelFactory;
        this.gatewayChatRuntimes = gatewayChatRuntimes;
    }

    public AdminChatExecuteResponse execute(AdminChatExecuteRequest request) {
        ChatExecutionResponse response = execute(new ChatExecutionRequest(
                request.distributedKeyPrefix(),
                request.protocol(),
                request.requestPath(),
                request.requestedModel(),
                buildAdminMessages(request.systemPrompt(), request.userPrompt()),
                List.of(),
                null,
                request.temperature(),
                request.maxTokens()
        ));
        return new AdminChatExecuteResponse(
                response.requestId(),
                response.routeSelection(),
                response.text(),
                response.usage(),
                response.toolCalls()
        );
    }

    public ChatExecutionResponse execute(ChatExecutionRequest request) {
        String requestId = gatewayObservabilityService.nextRequestId();
        RouteSelectionResult selectionResult = gatewayRouteSelectionService.select(new RouteSelectionRequest(
                request.distributedKeyPrefix(),
                request.protocol(),
                request.requestPath(),
                request.requestedModel(),
                buildRouteBody(request),
                GatewayClientFamily.GENERIC_OPENAI,
                true
        ));
        gatewayObservabilityService.recordRouteDecision(requestId, selectionResult);

        UpstreamCredentialEntity credential = getRequiredCredential(selectionResult.selectedCandidate().candidate().credentialId());
        String apiKey = accountSelectionService.resolveActiveAccount(
                        selectionResult.distributedKeyId(),
                        selectionResult.selectedCandidate().candidate().providerType(),
                        selectionResult.clientFamily(),
                        300)
                .map(account -> credentialCryptoService.decrypt(account.getAccessTokenCiphertext()))
                .orElseGet(() -> credentialCryptoService.decrypt(credential.getApiKeyCiphertext()));

        try {
            GatewayChatRuntime runtime = resolveRuntime(selectionResult.selectedCandidate().candidate());
            GatewayChatRuntimeResult result = runtime.execute(new GatewayChatRuntimeContext(
                    selectionResult,
                    credential,
                    apiKey,
                    request
            ));
            gatewayRouteSelectionService.recordSuccessfulSelection(selectionResult);
            gatewayObservabilityService.recordCacheUsage(
                    requestId,
                    selectionResult,
                    result.usage(),
                    "prompt_cache",
                    null
            );
            return new ChatExecutionResponse(
                    requestId,
                    selectionResult,
                    result.text(),
                    result.usage(),
                    result.toolCalls(),
                    result.reasoning()
            );
        } catch (RuntimeException exception) {
            gatewayRouteSelectionService.invalidateSelection(selectionResult);
            throw exception;
        } finally {
            distributedKeyGovernanceService.releaseConcurrency(selectionResult.governanceReservationKey());
        }
    }

    public ChatExecutionStreamResponse executeStream(ChatExecutionRequest request) {
        String requestId = gatewayObservabilityService.nextRequestId();
        RouteSelectionResult selectionResult = gatewayRouteSelectionService.select(new RouteSelectionRequest(
                request.distributedKeyPrefix(),
                request.protocol(),
                request.requestPath(),
                request.requestedModel(),
                buildRouteBody(request),
                GatewayClientFamily.GENERIC_OPENAI,
                true
        ));
        gatewayObservabilityService.recordRouteDecision(requestId, selectionResult);

        UpstreamCredentialEntity credential = getRequiredCredential(selectionResult.selectedCandidate().candidate().credentialId());
        String apiKey = accountSelectionService.resolveActiveAccount(
                        selectionResult.distributedKeyId(),
                        selectionResult.selectedCandidate().candidate().providerType(),
                        selectionResult.clientFamily(),
                        300)
                .map(account -> credentialCryptoService.decrypt(account.getAccessTokenCiphertext()))
                .orElseGet(() -> credentialCryptoService.decrypt(credential.getApiKeyCiphertext()));

        GatewayChatRuntime runtime = resolveRuntime(selectionResult.selectedCandidate().candidate());
        Flux<ChatExecutionStreamChunk> chunks = runtime.executeStream(new GatewayChatRuntimeContext(
                        selectionResult,
                        credential,
                        apiKey,
                        request
                ))
                .map(chunk -> {
                    gatewayObservabilityService.recordCacheUsage(
                            requestId,
                            selectionResult,
                            chunk.usage(),
                            "prompt_cache",
                            null
                    );
                    return chunk;
                })
                .doOnComplete(() -> gatewayRouteSelectionService.recordSuccessfulSelection(selectionResult))
                .doOnError(error -> gatewayRouteSelectionService.invalidateSelection(selectionResult))
                .doOnCancel(() -> gatewayRouteSelectionService.invalidateSelection(selectionResult))
                .doFinally(signalType -> distributedKeyGovernanceService.releaseConcurrency(selectionResult.governanceReservationKey()));

        return new ChatExecutionStreamResponse(requestId, selectionResult, chunks);
    }

    private GatewayChatRuntime resolveRuntime(com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView candidate) {
        return gatewayChatRuntimes.stream()
                .filter(runtime -> runtime.supports(candidate))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到匹配的聊天运行时：" + candidate.providerType()));
    }

    private ChatResponse executeOpenAi(RouteSelectionResult selectionResult, String baseUrl, String apiKey, ChatExecutionRequest request) {
        OpenAiChatOptions baseOptions = OpenAiChatOptions.builder()
                .model(selectionResult.resolvedModelKey())
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .build();
        PreparedChatExecution<OpenAiChatOptions> prepared = providerExecutionSupportService.prepareOpenAi(
                selectionResult,
                baseOptions,
                request.tools(),
                request.toolChoice()
        );
        OpenAiChatModel model = openAiChatModelFactory.create(baseUrl, apiKey, prepared.options());
        return call(model, buildPrompt(prepared.options(), request));
    }

    private ChatResponse executeAnthropic(RouteSelectionResult selectionResult, String baseUrl, String apiKey, ChatExecutionRequest request) {
        AnthropicChatOptions baseOptions = AnthropicChatOptions.builder()
                .model(selectionResult.resolvedModelKey())
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .build();
        PreparedChatExecution<AnthropicChatOptions> prepared = providerExecutionSupportService.prepareAnthropic(
                selectionResult,
                baseOptions,
                request.tools(),
                request.toolChoice()
        );
        AnthropicChatModel model = anthropicChatModelFactory.create(baseUrl, apiKey, prepared.options());
        return call(model, buildPrompt(prepared.options(), request));
    }

    private ChatResponse executeGemini(RouteSelectionResult selectionResult, String baseUrl, String apiKey, ChatExecutionRequest request) {
        GoogleGenAiChatOptions baseOptions = GoogleGenAiChatOptions.builder()
                .model(selectionResult.resolvedModelKey())
                .temperature(request.temperature())
                .maxOutputTokens(request.maxTokens())
                .build();
        PreparedChatExecution<GoogleGenAiChatOptions> prepared = providerExecutionSupportService.prepareGemini(
                selectionResult,
                baseOptions,
                request.tools()
        );
        GoogleGenAiChatModel model = geminiChatModelFactory.create(baseUrl, apiKey, prepared.options());
        return call(model, buildPrompt(prepared.options(), request));
    }

    private ChatResponse call(ChatModel model, Prompt prompt) {
        try {
            return model.call(prompt);
        } finally {
            closeModel(model, SignalType.ON_COMPLETE);
        }
    }

    private void closeModel(ChatModel model, SignalType signalType) {
        if (signalType == null) {
            return;
        }

        if (model instanceof AutoCloseable closeable) {
            try {
                closeable.close();
                return;
            } catch (Exception ignored) {
                // ignore
            }
        }

        if (model instanceof DisposableBean disposableBean) {
            try {
                disposableBean.destroy();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private Prompt buildPrompt(Object options, ChatExecutionRequest request) {
        List<Message> messages = request.messages().stream()
                .filter(this::isUsableMessage)
                .map(message -> toPromptMessage(request.distributedKeyPrefix(), message))
                .toList();
        return new Prompt(messages, (org.springframework.ai.chat.prompt.ChatOptions) options);
    }

    private JsonNode buildRouteBody(ChatExecutionRequest request) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("model", request.requestedModel());

        switch (request.protocol().trim().toLowerCase()) {
            case "openai", "responses" -> writeOpenAiMessages(root, request.messages());
            case "anthropic_native" -> writeAnthropicMessages(root, request.messages());
            case "google_native" -> writeGeminiMessages(root, request.messages());
            default -> root.put("prompt", lastUserMessage(request.messages()));
        }

        if (request.tools() != null && !request.tools().isEmpty()) {
            var tools = root.putArray("tools");
            for (GatewayToolDefinition tool : request.tools()) {
                var node = tools.addObject();
                node.put("type", "function");
                var function = node.putObject("function");
                function.put("name", tool.name());
                if (tool.description() != null) {
                    function.put("description", tool.description());
                }
                if (tool.inputSchema() != null) {
                    function.set("parameters", tool.inputSchema());
                }
                if (tool.strict() != null) {
                    function.put("strict", tool.strict());
                }
            }
        }

        if (request.toolChoice() != null) {
            root.set("tool_choice", request.toolChoice());
        }

        if (request.executionMetadata() != null && request.executionMetadata().isObject()) {
            JsonNode reasoning = request.executionMetadata().get("reasoning");
            if (reasoning != null && !reasoning.isNull()) {
                root.set("reasoning", reasoning);
            }
            JsonNode reasoningEffort = request.executionMetadata().get("reasoning_effort");
            if (reasoningEffort != null && !reasoningEffort.isNull()) {
                root.set("reasoning_effort", reasoningEffort);
            }
        }

        return root;
    }

    private List<ChatExecutionRequest.MessageInput> buildAdminMessages(String systemPrompt, String userPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return List.of(new ChatExecutionRequest.MessageInput("user", userPrompt, null, null, List.of()));
        }
        return List.of(
                new ChatExecutionRequest.MessageInput("system", systemPrompt, null, null, List.of()),
                new ChatExecutionRequest.MessageInput("user", userPrompt, null, null, List.of())
        );
    }

    private Message toPromptMessage(String distributedKeyPrefix, ChatExecutionRequest.MessageInput message) {
        return switch (message.role().trim().toLowerCase()) {
            case "system" -> new SystemMessage(message.content() == null ? "" : message.content().trim());
            case "assistant", "model" -> new org.springframework.ai.chat.messages.AssistantMessage(message.content() == null ? "" : message.content().trim());
            case "tool" -> ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse(
                            message.toolCallId() == null ? "tool-call" : message.toolCallId(),
                            message.toolName() == null ? "tool" : message.toolName(),
                            message.content() == null ? "" : message.content().trim()
                    )))
                    .build();
            default -> {
                if (message.media() != null && !message.media().isEmpty()) {
                    List<Media> media = message.media().stream()
                            .filter(item -> item.url() != null && !item.url().isBlank())
                            .map(item -> toMedia(distributedKeyPrefix, item))
                            .toList();
                    yield UserMessage.builder()
                            .text(message.content() == null ? "" : message.content().trim())
                            .media(media)
                            .build();
                }
                yield new UserMessage(message.content() == null ? "" : message.content().trim());
            }
        };
    }

    private boolean isUsableMessage(ChatExecutionRequest.MessageInput message) {
        boolean hasText = message.content() != null && !message.content().isBlank();
        boolean hasMedia = message.media() != null && !message.media().isEmpty();
        return hasText || hasMedia;
    }

    private Media toMedia(String distributedKeyPrefix, ChatExecutionRequest.MediaInput item) {
        if (item.url() != null && item.url().startsWith("gateway://")) {
            String fileKey = item.url().substring("gateway://".length());
            Long distributedKeyId = distributedKeyQueryService.findActiveByKeyPrefix(distributedKeyPrefix)
                    .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"))
                    .id();
            GatewayFileResource resource = gatewayFileService.resolveFileResource(fileKey, distributedKeyId);
            return Media.builder()
                    .mimeType(MimeTypeUtils.parseMimeType(resource.mimeType()))
                    .data(resource.resource())
                    .name(resource.filename())
                    .build();
        }

        return Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType(item.mimeType() == null || item.mimeType().isBlank()
                        ? ("file".equalsIgnoreCase(item.kind()) ? "application/octet-stream" : "image/*")
                        : item.mimeType()))
                .data(URI.create(item.url()))
                .name(item.name())
                .build();
    }

    private void writeOpenAiMessages(ObjectNode root, List<ChatExecutionRequest.MessageInput> messages) {
        var array = root.putArray("messages");
        for (ChatExecutionRequest.MessageInput message : messages) {
            boolean hasText = message.content() != null && !message.content().isBlank();
            boolean hasMedia = message.media() != null && !message.media().isEmpty();
            if (!hasText && !hasMedia) {
                continue;
            }
            var item = array.addObject().put("role", normalizeRole(message.role()));
            if (hasMedia) {
                var contentArray = item.putArray("content");
                if (hasText) {
                    contentArray.addObject()
                            .put("type", "text")
                            .put("text", message.content());
                }
                for (ChatExecutionRequest.MediaInput media : message.media()) {
                    if ("file".equalsIgnoreCase(media.kind())) {
                        var inputFile = contentArray.addObject()
                                .put("type", "input_file")
                                .putObject("input_file");
                        if (media.url().startsWith("gateway://")) {
                            inputFile.put("file_id", media.url().substring("gateway://".length()));
                        } else {
                            inputFile.put("url", media.url());
                        }
                        if (media.mimeType() != null && !media.mimeType().isBlank()) {
                            inputFile.put("mime_type", media.mimeType());
                        }
                        if (media.name() != null && !media.name().isBlank()) {
                            inputFile.put("filename", media.name());
                        }
                    } else {
                        contentArray.addObject()
                                .put("type", "image_url")
                            .putObject("image_url")
                            .put("url", media.url());
                    }
                }
            } else {
                item.put("content", message.content());
            }
            if (message.toolCallId() != null) {
                item.put("tool_call_id", message.toolCallId());
            }
        }
    }

    private void writeAnthropicMessages(ObjectNode root, List<ChatExecutionRequest.MessageInput> messages) {
        String systemPrompt = messages.stream()
                .filter(message -> "system".equalsIgnoreCase(message.role()))
                .map(ChatExecutionRequest.MessageInput::content)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("");
        root.put("system", systemPrompt);

        var array = root.putArray("messages");
        for (ChatExecutionRequest.MessageInput message : messages) {
            String role = normalizeRole(message.role());
            boolean hasText = message.content() != null && !message.content().isBlank();
            boolean hasMedia = message.media() != null && !message.media().isEmpty();
            if ("system".equals(role) || (!hasText && !hasMedia)) {
                continue;
            }
            if ("tool".equals(role)) {
                var content = JsonNodeFactory.instance.arrayNode();
                content.addObject()
                        .put("type", "tool_result")
                        .put("tool_use_id", message.toolCallId() == null ? "tool-use" : message.toolCallId())
                        .put("content", message.content());
                array.addObject()
                        .put("role", "user")
                        .set("content", content);
            } else {
                var item = array.addObject().put("role", "assistant".equals(role) ? "assistant" : "user");
                if (hasMedia) {
                    var content = JsonNodeFactory.instance.arrayNode();
                    if (hasText) {
                        content.addObject()
                                .put("type", "text")
                                .put("text", message.content());
                    }
                    for (ChatExecutionRequest.MediaInput media : message.media()) {
                        if ("file".equalsIgnoreCase(media.kind())) {
                            var block = content.addObject()
                                    .put("type", "document")
                                    .put("title", media.name() == null ? "document" : media.name());
                            block.putObject("source")
                                    .put("type", media.url().startsWith("gateway://") ? "file_id" : "url")
                                    .put(media.url().startsWith("gateway://") ? "file_id" : "url",
                                            media.url().startsWith("gateway://") ? media.url().substring("gateway://".length()) : media.url())
                                    .put("media_type", media.mimeType() == null ? "application/octet-stream" : media.mimeType());
                        } else {
                            var block = content.addObject().put("type", "image");
                            block.putObject("source")
                                    .put("type", media.url().startsWith("gateway://") ? "file_id" : "url")
                                    .put(media.url().startsWith("gateway://") ? "file_id" : "url",
                                            media.url().startsWith("gateway://") ? media.url().substring("gateway://".length()) : media.url())
                                    .put("media_type", media.mimeType() == null ? "image/*" : media.mimeType());
                        }
                    }
                    item.set("content", content);
                } else {
                    item.put("content", message.content());
                }
            }
        }
    }

    private void writeGeminiMessages(ObjectNode root, List<ChatExecutionRequest.MessageInput> messages) {
        String systemPrompt = messages.stream()
                .filter(message -> "system".equalsIgnoreCase(message.role()))
                .map(ChatExecutionRequest.MessageInput::content)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("");
        root.putObject("systemInstruction").put("text", systemPrompt);

        var array = root.putArray("contents");
        for (ChatExecutionRequest.MessageInput message : messages) {
            String role = normalizeRole(message.role());
            boolean hasText = message.content() != null && !message.content().isBlank();
            boolean hasMedia = message.media() != null && !message.media().isEmpty();
            if ("system".equals(role) || (!hasText && !hasMedia)) {
                continue;
            }
            String geminiRole = "assistant".equals(role) ? "model" : "user";
            var content = array.addObject().put("role", "tool".equals(role) ? "user" : geminiRole);
            var parts = content.putArray("parts");
            if ("tool".equals(role)) {
                parts.addObject()
                        .putObject("functionResponse")
                        .put("name", message.toolName() == null ? "tool" : message.toolName())
                        .putObject("response")
                        .put("content", message.content());
            } else {
                if (hasText) {
                    parts.addObject().put("text", message.content());
                }
                for (ChatExecutionRequest.MediaInput media : message.media()) {
                    parts.addObject()
                            .putObject("fileData")
                            .put("mimeType", media.mimeType() == null ? "application/octet-stream" : media.mimeType())
                            .put(media.url().startsWith("gateway://") ? "fileId" : "fileUri",
                                    media.url().startsWith("gateway://") ? media.url().substring("gateway://".length()) : media.url());
                }
            }
        }
    }

    private String lastUserMessage(List<ChatExecutionRequest.MessageInput> messages) {
        return messages.stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()))
                .map(ChatExecutionRequest.MessageInput::content)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "user";
        }
        return role.trim().toLowerCase();
    }

    private UpstreamCredentialEntity getRequiredCredential(Long credentialId) {
        Optional<UpstreamCredentialEntity> credential = upstreamCredentialRepository.findById(credentialId);
        if (credential.isEmpty() || credential.get().isDeleted()) {
            throw new IllegalArgumentException("未找到对应的上游凭证。");
        }
        return credential.get();
    }
}
