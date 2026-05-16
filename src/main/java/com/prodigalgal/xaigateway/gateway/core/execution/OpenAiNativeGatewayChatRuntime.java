package com.prodigalgal.xaigateway.gateway.core.execution;

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
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileContent;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import com.prodigalgal.xaigateway.provider.adapter.ProviderExecutionSupportService;
import com.prodigalgal.xaigateway.provider.adapter.openai.OpenAiChatModelFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class OpenAiNativeGatewayChatRuntime implements GatewayChatRuntime {

    private final OpenAiChatModelFactory openAiChatModelFactory;
    private final ProviderExecutionSupportService providerExecutionSupportService;
    private final GatewayFileService gatewayFileService;
    private final DistributedKeyQueryService distributedKeyQueryService;
    private final ObjectMapper objectMapper;

    public OpenAiNativeGatewayChatRuntime(
            OpenAiChatModelFactory openAiChatModelFactory,
            ProviderExecutionSupportService providerExecutionSupportService,
            GatewayFileService gatewayFileService,
            DistributedKeyQueryService distributedKeyQueryService,
            ObjectMapper objectMapper) {
        this.openAiChatModelFactory = openAiChatModelFactory;
        this.providerExecutionSupportService = providerExecutionSupportService;
        this.gatewayFileService = gatewayFileService;
        this.distributedKeyQueryService = distributedKeyQueryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExecutionBackend backend() {
        return ExecutionBackend.NATIVE;
    }

    @Override
    public boolean supports(CatalogCandidateView candidate) {
        return candidate.providerType() == ProviderType.OPENAI_DIRECT || candidate.providerType() == ProviderType.OPENAI_COMPATIBLE;
    }

    @Override
    public CanonicalResponse execute(GatewayChatRuntimeContext context) {
        CanonicalRequest request = context.canonicalRequest();
        if (shouldUseNativeResponsesHttp(context)) {
            return executeNativeResponsesCreate(context);
        }
        OpenAiApi api = openAiChatModelFactory.createApi(
                context.credential().getBaseUrl(),
                context.apiKey(),
                upstreamHeaders(context.selectionResult().selectedCandidate().candidate().siteKind(), request)
        );
        OpenAiApi.ChatCompletion response = api.chatCompletionEntity(buildRequest(
                        request,
                        context.selectionResult().resolvedModelKey(),
                        false,
                        context.selectionResult().selectedCandidate().candidate().providerType()
                ))
                .getBody();
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("OpenAI native 响应为空。");
        }
        OpenAiApi.ChatCompletion.Choice choice = response.choices().getFirst();
        OpenAiApi.ChatCompletionMessage message = choice.message();
        return new CanonicalResponse(
                null,
                context.selectionResult().publicModel(),
                message == null ? null : message.content(),
                message == null ? null : message.reasoningContent(),
                toolCalls(message == null ? List.of() : message.toolCalls()),
                toUsage(response.usage()),
                com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason.fromRaw(
                        choice.finishReason() == null ? null : choice.finishReason().toString()
                )
        );
    }

    @Override
    public Flux<CanonicalStreamEvent> executeStream(GatewayChatRuntimeContext context) {
        CanonicalRequest request = context.canonicalRequest();
        OpenAiApi api = openAiChatModelFactory.createApi(
                context.credential().getBaseUrl(),
                context.apiKey(),
                upstreamHeaders(context.selectionResult().selectedCandidate().candidate().siteKind(), request)
        );
        return api.chatCompletionStream(buildRequest(
                        request,
                        context.selectionResult().resolvedModelKey(),
                        true,
                        context.selectionResult().selectedCandidate().candidate().providerType()
                ))
                .flatMap(chunk -> {
                    if (chunk == null || chunk.choices() == null || chunk.choices().isEmpty()) {
                        return Flux.empty();
                    }
                    OpenAiApi.ChatCompletionChunk.ChunkChoice choice = chunk.choices().getFirst();
                    OpenAiApi.ChatCompletionMessage delta = choice.delta();
                    List<CanonicalStreamEvent> events = new ArrayList<>();
                    if (delta != null && delta.content() != null && !delta.content().isBlank()) {
                        events.add(new CanonicalStreamEvent(
                                CanonicalStreamEventType.TEXT_DELTA,
                                delta.content(),
                                null,
                                List.of(),
                                CanonicalUsage.empty(),
                                false,
                                null,
                                null,
                                null
                        ));
                    }
                    if (delta != null && delta.reasoningContent() != null && !delta.reasoningContent().isBlank()) {
                        events.add(new CanonicalStreamEvent(
                                CanonicalStreamEventType.REASONING_DELTA,
                                null,
                                delta.reasoningContent(),
                                List.of(),
                                CanonicalUsage.empty(),
                                false,
                                null,
                                null,
                                null
                        ));
                    }
                    if (delta != null && delta.toolCalls() != null && !delta.toolCalls().isEmpty()) {
                        events.add(new CanonicalStreamEvent(
                                CanonicalStreamEventType.TOOL_CALLS,
                                null,
                                null,
                                toolCalls(delta.toolCalls()),
                                CanonicalUsage.empty(),
                                false,
                                null,
                                null,
                                null
                        ));
                    }
                    if (choice.finishReason() != null) {
                        events.add(new CanonicalStreamEvent(
                                CanonicalStreamEventType.COMPLETED,
                                null,
                                null,
                                List.of(),
                                toUsage(chunk.usage()),
                                true,
                                com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason.fromRaw(choice.finishReason().toString()),
                                delta == null ? null : delta.content(),
                                delta == null ? null : delta.reasoningContent()
                        ));
                    }
                    return Flux.fromIterable(events);
                });
    }

    OpenAiApi.ChatCompletionRequest buildRequest(
            CanonicalRequest request,
            String model,
            boolean stream,
            ProviderType providerType) {
        List<OpenAiApi.ChatCompletionMessage> messages = request.messages().stream()
                .map(message -> toMessage(request.distributedKeyPrefix(), message))
                .toList();
        List<OpenAiApi.FunctionTool> tools = request.tools() == null
                ? List.of()
                : request.tools().stream().map(this::toTool).toList();
        JsonNode extensions = request.providerExtensions();
        Map<String, String> metadata = responsesMetadata(request);
        Boolean store = optionalBoolean(extensions, "store");
        Double frequencyPenalty = optionalDouble(extensions, "frequency_penalty");
        Map<String, Integer> logitBias = optionalIntegerMap(extensions, "logit_bias");
        Integer topLogprobs = optionalInt(extensions, "top_logprobs");
        Boolean logprobs = topLogprobs == null ? optionalBoolean(extensions, "logprobs") : Boolean.TRUE;
        Integer maxCompletionTokens = optionalInt(extensions, "max_completion_tokens");
        Integer n = optionalInt(extensions, "n");
        List<OpenAiApi.OutputModality> outputModalities = outputModalities(extensions);
        OpenAiApi.ChatCompletionRequest.AudioParameters audioParameters = audioParameters(extensions, outputModalities);
        Double presencePenalty = optionalDouble(extensions, "presence_penalty");
        Integer seed = optionalInt(extensions, "seed");
        String serviceTier = optionalText(extensions, "service_tier");
        List<String> stop = optionalStringList(extensions, "stop");
        Double topP = optionalDouble(extensions, "top_p");
        Boolean parallelToolCalls = optionalBoolean(extensions, "parallel_tool_calls");
        String user = optionalText(extensions, "user");
        String promptCacheKey = promptCacheKey(request);
        String safetyIdentifier = optionalText(extensions, "safety_identifier");
        ResponseFormat responseFormat = responseFormat(extensions);
        OpenAiApi.ChatCompletionRequest.WebSearchOptions webSearchOptions = webSearchOptions(extensions);
        Map<String, Object> extraBody = chatExtraBody(extensions, providerType);
        OpenAiApi.ChatCompletionRequest chatCompletionRequest = new OpenAiApi.ChatCompletionRequest(
                messages,
                model,
                store,
                metadata.isEmpty() ? null : metadata,
                frequencyPenalty,
                logitBias,
                logprobs,
                topLogprobs,
                request.maxTokens(),
                maxCompletionTokens,
                n,
                outputModalities,
                audioParameters,
                presencePenalty,
                responseFormat,
                seed,
                serviceTier,
                stop,
                stream,
                stream ? OpenAiApi.ChatCompletionRequest.StreamOptions.INCLUDE_USAGE : null,
                request.temperature(),
                topP,
                tools,
                request.toolChoice(),
                parallelToolCalls,
                user,
                request.reasoning() == null ? null : request.reasoning().effort(),
                webSearchOptions,
                optionalText(extensions, "verbosity"),
                promptCacheKey,
                safetyIdentifier,
                extraBody
        );
        return stream ? chatCompletionRequest.streamOptions(OpenAiApi.ChatCompletionRequest.StreamOptions.INCLUDE_USAGE) : chatCompletionRequest;
    }

    Map<String, String> upstreamHeaders(UpstreamSiteKind siteKind, CanonicalRequest request) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        if (siteKind == UpstreamSiteKind.OPENAI_DIRECT && request.metadata() != null) {
            put(headers, "OpenAI-Organization", request.metadata().openAiOrganization());
            put(headers, "OpenAI-Project", request.metadata().openAiProject());
            put(headers, "Idempotency-Key", request.metadata().idempotencyKey());
        }
        String promptCacheKey = promptCacheKey(request);
        if (siteKind == UpstreamSiteKind.GROK && promptCacheKey != null && !promptCacheKey.isBlank()) {
            headers.put("x-grok-conv-id", promptCacheKey);
        }
        return Map.copyOf(headers);
    }

    private Map<String, String> responsesMetadata(CanonicalRequest request) {
        JsonNode metadataNode = request.providerExtensions() == null ? null : request.providerExtensions().path("metadata");
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        if (metadataNode != null && metadataNode.isObject()) {
            metadataNode.properties().forEach(entry -> {
                JsonNode value = entry.getValue();
                if (value != null && value.isValueNode() && !value.isNull()) {
                    metadata.put(entry.getKey(), value.asText());
                }
            });
        }
        if (request.metadata() != null) {
            put(metadata, "gateway.client_family", request.metadata().clientFamily());
            put(metadata, "gateway.session_affinity_source", request.metadata().sessionAffinitySource());
            put(metadata, "gateway.session_affinity_key", request.metadata().sessionAffinityKey());
        }
        return metadata;
    }

    private Map<String, Object> chatExtraBody(JsonNode extensions, ProviderType providerType) {
        if (extensions == null || !extensions.isObject()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> extraBody = new LinkedHashMap<>();
        copyJson(extraBody, extensions, "stream_options");
        copyJson(extraBody, extensions, "prediction");
        if (providerType == ProviderType.OPENAI_COMPATIBLE) {
            copyJson(extraBody, extensions, "functions");
            copyJson(extraBody, extensions, "function_call");
            copyJson(extraBody, extensions, "truncation");
            copyJson(extraBody, extensions, "text");
            copyJson(extraBody, extensions, "prompt_cache_retention");
            copyJson(extraBody, extensions, "include");
            copyJson(extraBody, extensions, "previous_response_id");
        }
        return Map.copyOf(extraBody);
    }

    private List<OpenAiApi.OutputModality> outputModalities(JsonNode extensions) {
        if (extensions == null || !extensions.has("modalities") || extensions.get("modalities").isNull()) {
            return null;
        }
        JsonNode value = extensions.get("modalities");
        if (!value.isArray()) {
            throw new IllegalArgumentException("modalities 必须是 JSON array。");
        }
        List<OpenAiApi.OutputModality> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (item == null || !item.isTextual()) {
                throw new IllegalArgumentException("modalities 每一项必须是 text 或 audio。");
            }
            String modality = item.asText();
            switch (modality) {
                case "text" -> result.add(OpenAiApi.OutputModality.TEXT);
                case "audio" -> result.add(OpenAiApi.OutputModality.AUDIO);
                default -> throw new IllegalArgumentException("modalities 每一项必须是 text 或 audio。");
            }
        }
        return result.isEmpty() ? null : List.copyOf(result);
    }

    private OpenAiApi.ChatCompletionRequest.AudioParameters audioParameters(
            JsonNode extensions,
            List<OpenAiApi.OutputModality> outputModalities) {
        if (extensions == null || !extensions.has("audio") || extensions.get("audio").isNull()) {
            if (outputModalities != null && outputModalities.contains(OpenAiApi.OutputModality.AUDIO)) {
                throw new IllegalArgumentException("modalities 包含 audio 时必须提供 audio 参数。");
            }
            return null;
        }
        JsonNode value = extensions.get("audio");
        if (!value.isObject()) {
            throw new IllegalArgumentException("audio 必须是 JSON object。");
        }
        String voice = value.path("voice").asText(null);
        String format = value.path("format").asText(null);
        if (voice == null || voice.isBlank()) {
            throw new IllegalArgumentException("audio.voice 不能为空。");
        }
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("audio.format 不能为空。");
        }
        return new OpenAiApi.ChatCompletionRequest.AudioParameters(
                enumValue(OpenAiApi.ChatCompletionRequest.AudioParameters.Voice.class, voice, "audio.voice"),
                enumValue(OpenAiApi.ChatCompletionRequest.AudioParameters.AudioResponseFormat.class, format, "audio.format")
        );
    }

    private OpenAiApi.ChatCompletionRequest.WebSearchOptions webSearchOptions(JsonNode extensions) {
        if (extensions == null || !extensions.has("web_search_options") || extensions.get("web_search_options").isNull()) {
            return null;
        }
        JsonNode value = extensions.get("web_search_options");
        if (!value.isObject()) {
            throw new IllegalArgumentException("web_search_options 必须是 JSON object。");
        }
        OpenAiApi.ChatCompletionRequest.WebSearchOptions.SearchContextSize searchContextSize = null;
        if (value.has("search_context_size") && !value.get("search_context_size").isNull()) {
            searchContextSize = enumValue(
                    OpenAiApi.ChatCompletionRequest.WebSearchOptions.SearchContextSize.class,
                    value.get("search_context_size").asText(null),
                    "web_search_options.search_context_size"
            );
        }
        return new OpenAiApi.ChatCompletionRequest.WebSearchOptions(
                searchContextSize,
                userLocation(value.path("user_location"))
        );
    }

    private OpenAiApi.ChatCompletionRequest.WebSearchOptions.UserLocation userLocation(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isObject()) {
            throw new IllegalArgumentException("web_search_options.user_location 必须是 JSON object。");
        }
        String type = value.path("type").asText(null);
        if (type != null && !type.isBlank() && !"approximate".equals(type)) {
            throw new IllegalArgumentException("web_search_options.user_location.type 只支持 approximate。");
        }
        JsonNode approximate = value.path("approximate");
        OpenAiApi.ChatCompletionRequest.WebSearchOptions.UserLocation.Approximate approximateValue = null;
        if (!approximate.isMissingNode() && !approximate.isNull()) {
            if (!approximate.isObject()) {
                throw new IllegalArgumentException("web_search_options.user_location.approximate 必须是 JSON object。");
            }
            approximateValue = new OpenAiApi.ChatCompletionRequest.WebSearchOptions.UserLocation.Approximate(
                    optionalObjectText(approximate, "city"),
                    optionalObjectText(approximate, "country"),
                    optionalObjectText(approximate, "region"),
                    optionalObjectText(approximate, "timezone")
            );
        }
        return new OpenAiApi.ChatCompletionRequest.WebSearchOptions.UserLocation(
                type == null || type.isBlank() ? "approximate" : type,
                approximateValue
        );
    }

    private ResponseFormat responseFormat(JsonNode extensions) {
        if (extensions == null || !extensions.has("response_format") || extensions.get("response_format").isNull()) {
            return null;
        }
        JsonNode value = extensions.get("response_format");
        if (!value.isObject()) {
            throw new IllegalArgumentException("response_format 必须是 JSON object。");
        }
        String type = value.path("type").asText(null);
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("response_format.type 不能为空。");
        }
        return switch (type) {
            case "text" -> ResponseFormat.builder().type(ResponseFormat.Type.TEXT).build();
            case "json_object" -> ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build();
            case "json_schema" -> ResponseFormat.builder()
                    .type(ResponseFormat.Type.JSON_SCHEMA)
                    .jsonSchema(responseJsonSchema(value.path("json_schema")))
                    .build();
            default -> throw new IllegalArgumentException("response_format.type 只支持 text、json_object 或 json_schema。");
        };
    }

    private ResponseFormat.JsonSchema responseJsonSchema(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException("response_format.json_schema 必须是 JSON object。");
        }
        String name = value.path("name").asText(null);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("response_format.json_schema.name 不能为空。");
        }
        JsonNode schema = value.path("schema");
        if (!schema.isObject()) {
            throw new IllegalArgumentException("response_format.json_schema.schema 必须是 JSON object。");
        }
        ResponseFormat.JsonSchema.Builder builder = ResponseFormat.JsonSchema.builder()
                .name(name)
                .schema(objectMapper.convertValue(schema, Map.class));
        if (value.has("strict") && !value.get("strict").isNull()) {
            builder.strict(value.get("strict").asBoolean());
        }
        return builder.build();
    }

    private <E extends Enum<E>> E enumValue(Class<E> enumType, String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空。");
        }
        try {
            return Enum.valueOf(enumType, value.replace("-", "_").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(fieldName + " 不支持值 " + value + "。", exception);
        }
    }

    private boolean shouldUseNativeResponsesHttp(GatewayChatRuntimeContext context) {
        if (context == null || context.canonicalRequest() == null || context.selectionResult() == null) {
            return false;
        }
        if (context.canonicalRequest().ingressProtocol() != com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol.RESPONSES) {
            return false;
        }
        var selected = context.selectionResult().selectedCandidate();
        if (selected == null || selected.candidate() == null) {
            return false;
        }
        return selected.candidate().providerType() == ProviderType.OPENAI_DIRECT;
    }

    private CanonicalResponse executeNativeResponsesCreate(GatewayChatRuntimeContext context) {
        CanonicalRequest request = context.canonicalRequest();
        ObjectNode payload = nativeResponsesPayload(request, context.selectionResult().resolvedModelKey());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(responsesUrl(context.credential().getBaseUrl())))
                .timeout(Duration.ofSeconds(90))
                .header("authorization", "Bearer " + context.apiKey())
                .header("content-type", "application/json")
                .header("accept", "application/json");
        upstreamHeaders(context.selectionResult().selectedCandidate().candidate().siteKind(), request)
                .forEach(builder::header);
        try {
            HttpResponse<String> upstreamResponse = HttpClient.newHttpClient().send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload))).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (upstreamResponse.statusCode() < 200 || upstreamResponse.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI Responses native 请求失败：HTTP "
                        + upstreamResponse.statusCode() + " " + truncate(upstreamResponse.body(), 240));
            }
            JsonNode body = objectMapper.readTree(upstreamResponse.body());
            return new CanonicalResponse(
                    firstNonBlank(
                            text(body.path("id")),
                            upstreamResponse.headers().firstValue("x-request-id").orElse(null),
                            upstreamResponse.headers().firstValue("request-id").orElse(null)
                    ),
                    context.selectionResult().publicModel(),
                    responsesOutputText(body),
                    responsesReasoning(body),
                    responsesToolCalls(body),
                    responsesUsage(body),
                    responsesFinishReason(body)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI Responses native 请求被中断。", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException) {
                throw stateException;
            }
            throw new IllegalStateException("OpenAI Responses native 请求失败：" + truncate(exception.getMessage(), 240), exception);
        }
    }

    private ObjectNode nativeResponsesPayload(CanonicalRequest request, String resolvedModel) {
        ObjectNode payload = request.providerExtensions() != null && request.providerExtensions().isObject()
                ? ((ObjectNode) request.providerExtensions()).deepCopy()
                : objectMapper.createObjectNode();
        payload.put("model", resolvedModel);
        payload.put("stream", false);
        return payload;
    }

    private String responsesUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        if (normalized.endsWith("/v1/responses")) {
            return normalized;
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/responses";
        }
        return normalized + "/v1/responses";
    }

    private String responsesOutputText(JsonNode body) {
        String direct = text(body.path("output_text"));
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        List<String> values = new ArrayList<>();
        JsonNode output = body.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode part : content) {
                    String type = text(part.path("type"));
                    if ("output_text".equals(type) || "text".equals(type)) {
                        String text = text(part.path("text"));
                        if (text != null && !text.isBlank()) {
                            values.add(text);
                        }
                    }
                }
            }
        }
        return values.isEmpty() ? null : String.join("\n", values);
    }

    private String responsesReasoning(JsonNode body) {
        List<String> values = new ArrayList<>();
        JsonNode output = body.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                if (!"reasoning".equals(text(item.path("type")))) {
                    continue;
                }
                JsonNode summary = item.path("summary");
                if (summary.isArray()) {
                    for (JsonNode part : summary) {
                        String text = text(part.path("text"));
                        if (text != null && !text.isBlank()) {
                            values.add(text);
                        }
                    }
                }
            }
        }
        return values.isEmpty() ? null : String.join("\n", values);
    }

    private List<CanonicalToolCall> responsesToolCalls(JsonNode body) {
        List<CanonicalToolCall> result = new ArrayList<>();
        JsonNode output = body.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                if (!"function_call".equals(text(item.path("type")))) {
                    continue;
                }
                result.add(new CanonicalToolCall(
                        firstNonBlank(text(item.path("call_id")), text(item.path("id"))),
                        "function",
                        text(item.path("name")),
                        text(item.path("arguments"))
                ));
            }
        }
        return List.copyOf(result);
    }

    private CanonicalUsage responsesUsage(JsonNode body) {
        JsonNode usage = body.path("usage");
        if (!usage.isObject()) {
            return CanonicalUsage.empty();
        }
        int inputTokens = usage.path("input_tokens").asInt(0);
        int outputTokens = usage.path("output_tokens").asInt(0);
        int totalTokens = usage.path("total_tokens").asInt(inputTokens + outputTokens);
        int cachedTokens = usage.path("input_tokens_details").path("cached_tokens").asInt(0);
        int reasoningTokens = usage.path("output_tokens_details").path("reasoning_tokens").asInt(0);
        return new CanonicalUsage(true, inputTokens, outputTokens, totalTokens, cachedTokens, 0, reasoningTokens);
    }

    private com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason responsesFinishReason(JsonNode body) {
        String status = text(body.path("status"));
        if ("failed".equals(status)) {
            return com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason.ERROR;
        }
        if ("cancelled".equals(status) || "canceled".equals(status)) {
            return com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason.CANCELED;
        }
        if ("incomplete".equals(status)) {
            return com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason.fromRaw(
                    text(body.path("incomplete_details").path("reason"))
            );
        }
        return com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason.STOP;
    }

    private String optionalObjectText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private void copyJson(Map<String, Object> target, JsonNode source, String field) {
        if (source.has(field) && !source.get(field).isNull()) {
            target.put(field, objectMapper.convertValue(source.get(field), Object.class));
        }
    }

    private String promptCacheKey(CanonicalRequest request) {
        String explicit = optionalText(request.providerExtensions(), "prompt_cache_key");
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        return request.metadata() == null ? null : request.metadata().sessionAffinityKey();
    }

    private String optionalText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private Integer optionalInt(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asInt();
    }

    private Boolean optionalBoolean(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asBoolean();
    }

    private Double optionalDouble(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asDouble();
    }

    private List<String> optionalStringList(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value.isTextual()) {
            String text = value.asText();
            return text == null || text.isBlank() ? null : List.of(text);
        }
        if (!value.isArray()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            String text = item == null || item.isNull() ? null : item.asText(null);
            if (text != null && !text.isBlank()) {
                result.add(text);
            }
        }
        return result.isEmpty() ? null : List.copyOf(result);
    }

    private Map<String, Integer> optionalIntegerMap(JsonNode node, String field) {
        if (node == null || !node.has(field) || !node.get(field).isObject()) {
            return null;
        }
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        node.get(field).properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            if (value != null && value.isNumber()) {
                result.put(entry.getKey(), value.asInt());
            }
        });
        return result.isEmpty() ? null : Map.copyOf(result);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void put(Map<String, String> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }

    private OpenAiApi.ChatCompletionMessage toMessage(String distributedKeyPrefix, CanonicalMessage message) {
        if (message.role() == CanonicalMessageRole.TOOL) {
            CanonicalContentPart toolResult = message.parts().stream()
                    .filter(part -> part.type() == CanonicalPartType.TOOL_RESULT)
                    .findFirst()
                    .orElse(null);
            return new OpenAiApi.ChatCompletionMessage(
                    toolResult == null ? "" : toolResult.text(),
                    OpenAiApi.ChatCompletionMessage.Role.TOOL,
                    null,
                    toolResult == null ? null : toolResult.toolCallId(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        List<CanonicalContentPart> mediaParts = message.parts() == null
                ? List.of()
                : message.parts().stream()
                .filter(part -> part.type() == CanonicalPartType.IMAGE || part.type() == CanonicalPartType.FILE)
                .toList();
        String text = message.parts() == null
                ? ""
                : message.parts().stream()
                .filter(part -> part.type() == CanonicalPartType.TEXT)
                .map(CanonicalContentPart::text)
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        if (mediaParts.isEmpty()) {
            return new OpenAiApi.ChatCompletionMessage(text, role(message.role()));
        }
        List<OpenAiApi.ChatCompletionMessage.MediaContent> rawContent = new ArrayList<>();
        if (!text.isBlank()) {
            rawContent.add(new OpenAiApi.ChatCompletionMessage.MediaContent(text));
        }
        for (CanonicalContentPart part : mediaParts) {
            rawContent.add(toMediaContent(distributedKeyPrefix, part));
        }
        return new OpenAiApi.ChatCompletionMessage(rawContent, role(message.role()));
    }

    private OpenAiApi.ChatCompletionMessage.MediaContent toMediaContent(String distributedKeyPrefix, CanonicalContentPart part) {
        if (part.type() == CanonicalPartType.FILE) {
            GatewayFileContent content = resolveGatewayFile(distributedKeyPrefix, part);
            return new OpenAiApi.ChatCompletionMessage.MediaContent(
                    new OpenAiApi.ChatCompletionMessage.MediaContent.InputFile(
                            content.metadata().filename(),
                            Base64.getEncoder().encodeToString(content.bytes())
                    )
            );
        }
        if (part.uri() != null && part.uri().startsWith("gateway://")) {
            GatewayFileContent content = resolveGatewayFile(distributedKeyPrefix, part);
            String dataUrl = "data:" + content.mimeType() + ";base64," + Base64.getEncoder().encodeToString(content.bytes());
            return new OpenAiApi.ChatCompletionMessage.MediaContent(
                    new OpenAiApi.ChatCompletionMessage.MediaContent.ImageUrl(dataUrl)
            );
        }
        return new OpenAiApi.ChatCompletionMessage.MediaContent(
                new OpenAiApi.ChatCompletionMessage.MediaContent.ImageUrl(part.uri())
        );
    }

    private GatewayFileContent resolveGatewayFile(String distributedKeyPrefix, CanonicalContentPart part) {
        if (part.uri() == null || !part.uri().startsWith("gateway://")) {
            throw new IllegalArgumentException("当前 native OpenAI 仅支持 gateway:// 文件解析。");
        }
        return gatewayFileService.getFileContent(
                part.uri().substring("gateway://".length()),
                distributedKeyQueryService.findActiveByKeyPrefix(distributedKeyPrefix)
                        .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"))
                        .id()
        );
    }

    private OpenAiApi.FunctionTool toTool(CanonicalToolDefinition tool) {
        OpenAiApi.FunctionTool.Function function = new OpenAiApi.FunctionTool.Function(
                tool.description(),
                tool.name(),
                tool.inputSchema() == null ? Map.of("type", "object") : objectMapper.convertValue(tool.inputSchema(), Map.class),
                tool.strict()
        );
        return new OpenAiApi.FunctionTool(function);
    }

    private List<CanonicalToolCall> toolCalls(List<OpenAiApi.ChatCompletionMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        return toolCalls.stream()
                .map(toolCall -> new CanonicalToolCall(
                        toolCall.id(),
                        toolCall.type(),
                        toolCall.function() == null ? null : toolCall.function().name(),
                        toolCall.function() == null ? null : toolCall.function().arguments()
                ))
                .toList();
    }

    private CanonicalUsage toUsage(OpenAiApi.Usage usage) {
        if (usage == null) {
            return CanonicalUsage.empty();
        }
        int promptTokens = usage.promptTokens() == null ? 0 : usage.promptTokens();
        int completionTokens = usage.completionTokens() == null ? 0 : usage.completionTokens();
        int totalTokens = usage.totalTokens() == null ? promptTokens + completionTokens : usage.totalTokens();
        int cacheHitTokens = usage.promptTokensDetails() == null || usage.promptTokensDetails().cachedTokens() == null
                ? 0
                : usage.promptTokensDetails().cachedTokens();
        int reasoningTokens = usage.completionTokenDetails() == null || usage.completionTokenDetails().reasoningTokens() == null
                ? 0
                : usage.completionTokenDetails().reasoningTokens();
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

    private OpenAiApi.ChatCompletionMessage.Role role(CanonicalMessageRole role) {
        if (role == null) {
            return OpenAiApi.ChatCompletionMessage.Role.USER;
        }
        return switch (role) {
            case SYSTEM -> OpenAiApi.ChatCompletionMessage.Role.SYSTEM;
            case USER -> OpenAiApi.ChatCompletionMessage.Role.USER;
            case ASSISTANT -> OpenAiApi.ChatCompletionMessage.Role.ASSISTANT;
            case TOOL -> OpenAiApi.ChatCompletionMessage.Role.TOOL;
        };
    }
}
