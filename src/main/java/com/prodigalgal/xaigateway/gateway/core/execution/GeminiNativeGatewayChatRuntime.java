package com.prodigalgal.xaigateway.gateway.core.execution;

import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Blob;
import com.google.genai.types.Content;
import com.google.genai.types.FileData;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalPartType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolCall;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolDefinition;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalUsage;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileContent;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class GeminiNativeGatewayChatRuntime implements GatewayChatRuntime {

    private final GeminiChatModelFactory geminiChatModelFactory;
    private final GatewayFileService gatewayFileService;
    private final DistributedKeyQueryService distributedKeyQueryService;

    public GeminiNativeGatewayChatRuntime(
            GeminiChatModelFactory geminiChatModelFactory,
            GatewayFileService gatewayFileService,
            DistributedKeyQueryService distributedKeyQueryService) {
        this.geminiChatModelFactory = geminiChatModelFactory;
        this.gatewayFileService = gatewayFileService;
        this.distributedKeyQueryService = distributedKeyQueryService;
    }

    @Override
    public ExecutionBackend backend() {
        return ExecutionBackend.NATIVE;
    }

    @Override
    public boolean supports(CatalogCandidateView candidate) {
        return candidate.providerType() == ProviderType.GEMINI_DIRECT;
    }

    @Override
    public CanonicalResponse execute(GatewayChatRuntimeContext context) {
        CanonicalRequest request = context.canonicalRequest();
        try (Client client = geminiChatModelFactory.createClient(
                context.selectionResult().selectedCandidate().candidate().siteKind(),
                context.credential().getBaseUrl(),
                context.credentialMaterial())) {
            GenerateContentResponse response = client.models.generateContent(
                    context.selectionResult().resolvedModelKey(),
                    toContents(request),
                    toConfig(request)
            );
            return toCanonicalResponse(context, response);
        }
    }

    @Override
    public Flux<CanonicalStreamEvent> executeStream(GatewayChatRuntimeContext context) {
        CanonicalRequest request = context.canonicalRequest();
        return Flux.using(
                () -> geminiChatModelFactory.createClient(
                        context.selectionResult().selectedCandidate().candidate().siteKind(),
                        context.credential().getBaseUrl(),
                        context.credentialMaterial()
                ),
                client -> Flux.using(
                        () -> client.models.generateContentStream(
                                context.selectionResult().resolvedModelKey(),
                                toContents(request),
                                toConfig(request)
                        ),
                        stream -> Flux.fromIterable(stream).flatMap(this::toStreamEvents),
                        ResponseStream::close
                ),
                Client::close
        );
    }

    private List<Content> toContents(CanonicalRequest request) {
        List<Content> contents = new ArrayList<>();
        for (CanonicalMessage message : request.messages()) {
            if (message.role() == CanonicalMessageRole.SYSTEM) {
                continue;
            }
            List<Part> parts = new ArrayList<>();
            for (CanonicalContentPart part : message.parts()) {
                switch (part.type()) {
                    case TEXT -> {
                        if (part.text() != null && !part.text().isBlank()) {
                            parts.add(Part.builder().text(part.text()).build());
                        }
                    }
                    case IMAGE, FILE -> parts.add(toMediaPart(request.distributedKeyPrefix(), part));
                    case TOOL_RESULT -> parts.add(Part.builder()
                            .functionResponse(FunctionResponse.builder()
                                    .name(part.toolName() == null ? "tool" : part.toolName())
                                    .response(parseToolResponse(part.text()))
                                    .build())
                            .build());
                }
            }
            if (!parts.isEmpty()) {
                contents.add(Content.builder()
                        .role(message.role() == CanonicalMessageRole.ASSISTANT ? "model" : "user")
                        .parts(parts)
                        .build());
            }
        }
        return List.copyOf(contents);
    }

    private GenerateContentConfig toConfig(CanonicalRequest request) {
        GenerateContentConfig.Builder builder = GenerateContentConfig.builder();
        if (request.temperature() != null) {
            builder.temperature(request.temperature().floatValue());
        }
        if (request.maxTokens() != null) {
            builder.maxOutputTokens(request.maxTokens());
        }
        Content systemInstruction = toSystemInstruction(request);
        if (systemInstruction != null) {
            builder.systemInstruction(systemInstruction);
        }
        List<Tool> tools = toTools(request.tools());
        if (!tools.isEmpty()) {
            builder.tools(tools);
        }
        return builder.build();
    }

    private Content toSystemInstruction(CanonicalRequest request) {
        List<Part> parts = new ArrayList<>();
        for (CanonicalMessage message : request.messages()) {
            if (message.role() != CanonicalMessageRole.SYSTEM || message.parts() == null) {
                continue;
            }
            for (CanonicalContentPart part : message.parts()) {
                if (part.type() == CanonicalPartType.TEXT && part.text() != null && !part.text().isBlank()) {
                    parts.add(Part.builder().text(part.text()).build());
                }
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return Content.builder().role("user").parts(parts).build();
    }

    private List<Tool> toTools(List<CanonicalToolDefinition> toolDefinitions) {
        if (toolDefinitions == null || toolDefinitions.isEmpty()) {
            return List.of();
        }
        List<FunctionDeclaration> functionDeclarations = toolDefinitions.stream()
                .map(tool -> FunctionDeclaration.builder()
                        .name(tool.name())
                        .description(tool.description())
                        .parametersJsonSchema(tool.inputSchema() == null
                                ? Schema.builder().type("OBJECT").build()
                                : Schema.fromJson(tool.inputSchema().toString()))
                        .build())
                .toList();
        return List.of(Tool.builder().functionDeclarations(functionDeclarations).build());
    }

    private Part toMediaPart(String distributedKeyPrefix, CanonicalContentPart part) {
        if (part.uri() != null && part.uri().startsWith("gateway://")) {
            GatewayFileContent content = resolveGatewayFile(distributedKeyPrefix, part);
            if (part.type() == CanonicalPartType.IMAGE) {
                return Part.builder()
                        .inlineData(Blob.builder()
                                .mimeType(content.mimeType())
                                .data(content.bytes())
                                .build())
                        .build();
            }
            return Part.builder()
                    .inlineData(Blob.builder()
                            .mimeType(content.mimeType())
                            .data(content.bytes())
                            .build())
                    .build();
        }

        return Part.builder()
                .fileData(FileData.builder()
                        .mimeType(part.mimeType())
                        .fileUri(part.uri())
                        .build())
                .build();
    }

    private Map<String, Object> parseToolResponse(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        try {
            return new tools.jackson.databind.ObjectMapper().convertValue(
                    new tools.jackson.databind.ObjectMapper().readTree(text),
                    Map.class
            );
        } catch (Exception ignored) {
            return Map.of("text", text);
        }
    }

    private GatewayFileContent resolveGatewayFile(String distributedKeyPrefix, CanonicalContentPart part) {
        return gatewayFileService.getFileContent(
                part.uri().substring("gateway://".length()),
                distributedKeyQueryService.findActiveByKeyPrefix(distributedKeyPrefix)
                        .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"))
                        .id()
        );
    }

    private CanonicalResponse toCanonicalResponse(GatewayChatRuntimeContext context, GenerateContentResponse response) {
        StringBuilder text = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<CanonicalToolCall> toolCalls = new ArrayList<>();

        if (response != null && response.candidates().isPresent() && !response.candidates().get().isEmpty()) {
            for (Part part : response.candidates().get().getFirst().content().flatMap(Content::parts).orElse(List.of())) {
                if (part.functionCall().isPresent()) {
                    FunctionCall functionCall = part.functionCall().get();
                    toolCalls.add(new CanonicalToolCall(
                            null,
                            "function",
                            functionCall.name().orElse(null),
                            functionCall.args().isPresent() ? functionCall.args().get().toString() : "{}"
                    ));
                    continue;
                }
                if (part.thought().orElse(false)) {
                    reasoning.append(part.text().orElse(""));
                    continue;
                }
                text.append(part.text().orElse(""));
            }
        }

        return new CanonicalResponse(
                null,
                context.selectionResult().publicModel(),
                text.isEmpty() ? null : text.toString(),
                reasoning.isEmpty() ? null : reasoning.toString(),
                List.copyOf(toolCalls),
                toUsage(response == null ? null : response.usageMetadata().orElse(null)),
                null
        );
    }

    private Flux<CanonicalStreamEvent> toStreamEvents(GenerateContentResponse response) {
        List<CanonicalStreamEvent> events = new ArrayList<>();
        if (response != null && response.candidates().isPresent() && !response.candidates().get().isEmpty()) {
            for (Part part : response.candidates().get().getFirst().content().flatMap(Content::parts).orElse(List.of())) {
                if (part.functionCall().isPresent()) {
                    FunctionCall functionCall = part.functionCall().get();
                    events.add(new CanonicalStreamEvent(
                            CanonicalStreamEventType.TOOL_CALLS,
                            null,
                            null,
                            List.of(new CanonicalToolCall(
                                    null,
                                    "function",
                                    functionCall.name().orElse(null),
                                    functionCall.args().isPresent() ? functionCall.args().get().toString() : "{}"
                            )),
                            CanonicalUsage.empty(),
                            false,
                            null,
                            null,
                            null
                    ));
                    continue;
                }
                if (part.thought().orElse(false) && part.text().isPresent() && !part.text().get().isBlank()) {
                    events.add(new CanonicalStreamEvent(
                            CanonicalStreamEventType.REASONING_DELTA,
                            null,
                            part.text().get(),
                            List.of(),
                            CanonicalUsage.empty(),
                            false,
                            null,
                            null,
                            null
                    ));
                    continue;
                }
                if (part.text().isPresent() && !part.text().get().isBlank()) {
                    events.add(new CanonicalStreamEvent(
                            CanonicalStreamEventType.TEXT_DELTA,
                            part.text().get(),
                            null,
                            List.of(),
                            CanonicalUsage.empty(),
                            false,
                            null,
                            null,
                            null
                    ));
                }
            }
        }
        if (response != null && response.usageMetadata().isPresent()) {
            events.add(new CanonicalStreamEvent(
                    CanonicalStreamEventType.COMPLETED,
                    null,
                    null,
                    List.of(),
                    toUsage(response.usageMetadata().get()),
                    true,
                    null,
                    null,
                    null
            ));
        }
        return Flux.fromIterable(events);
    }

    private CanonicalUsage toUsage(com.google.genai.types.GenerateContentResponseUsageMetadata usage) {
        if (usage == null) {
            return CanonicalUsage.empty();
        }
        int promptTokens = usage.promptTokenCount().orElse(0);
        int completionTokens = usage.candidatesTokenCount().orElse(0);
        int totalTokens = usage.totalTokenCount().orElse(promptTokens + completionTokens);
        int cacheHitTokens = usage.cachedContentTokenCount().orElse(0);
        int reasoningTokens = usage.thoughtsTokenCount().orElse(0);
        return new CanonicalUsage(
                true,
                promptTokens,
                completionTokens,
                totalTokens,
                cacheHitTokens,
                0,
                reasoningTokens
        );
    }
}
