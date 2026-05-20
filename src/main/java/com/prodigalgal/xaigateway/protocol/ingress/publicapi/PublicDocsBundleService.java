package com.prodigalgal.xaigateway.protocol.ingress.publicapi;

import com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoader;
import com.prodigalgal.xaigateway.admin.application.ProviderPresetDefinition;
import java.util.List;
import java.util.Locale;
import com.prodigalgal.xaigateway.gateway.core.cli.CloudCliClientDescriptor;
import com.prodigalgal.xaigateway.gateway.core.cli.CloudCliClientMatrixService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class PublicDocsBundleService {

    private final CloudCliClientMatrixService cloudCliClientMatrixService;
    private final ProviderCatalogLoader providerCatalogLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public PublicDocsBundleService(
            CloudCliClientMatrixService cloudCliClientMatrixService,
            ProviderCatalogLoader providerCatalogLoader) {
        this.cloudCliClientMatrixService = cloudCliClientMatrixService;
        this.providerCatalogLoader = providerCatalogLoader;
    }

    public PublicDocsBundleService() {
        this(new CloudCliClientMatrixService(), new ProviderCatalogLoader(new ObjectMapper()));
    }

    public PublicDocsBundleResponse bundle(String locale) {
        String normalized = normalizeLocale(locale);
        if ("en-US".equals(normalized)) {
            return english();
        }
        return chinese();
    }

    private PublicDocsBundleResponse chinese() {
        return new PublicDocsBundleResponse(
                "2026.05.18",
                "zh-CN",
                "x-ai-gateway 公开兼容文档",
                "/public/docs/openapi.json",
                "3.1.0",
                sdkTargets("zh-CN"),
                i18nPolicy("zh-CN"),
                List.of(
                        "创建或获取 Distributed Key。",
                        "把 OpenAI-compatible client 的 base URL 设置为 https://gateway.example.com/v1。",
                        "将 Authorization Bearer 设置为完整 Distributed Key secret。",
                        "先调用 /v1/chat/completions smoke，再按 OpenAI 标准功能区启用 Responses、Claude Messages、Gemini/Vertex generateContent 与 Codex Responses smoke。",
                        "失败时读取 error_code 和 requestId，再到管理端 trace 查询路由与账务记录。"
                ),
                compatibility(),
                providerPresets(),
                cliClients(),
                examples("zh-CN"),
                errorCodes("zh-CN"),
                List.of(
                        "路由优先遵守 Distributed Key 的 allowedModels、allowedProviderTypes、clientFamily 与 Route Policy runtime state。",
                        "Retry/Fallback 触发后，trace 中会保留 route decision、provider、credential 与 degradation level。",
                        "OpenAI Direct 可使用 response_format、tools/tool_choice、modalities/audio、web_search_options 等 typed Chat 参数；OpenAI-compatible 站点需以 provider capability matrix 为准。",
                        "Stored Chat list/messages 使用 OpenAI-compatible list envelope，limit 默认 20 且范围为 1 到 100，order 默认 asc；Chat Completion list 使用数据库游标查询下推租户、类型前缀、model、createdAt/id cursor 与排序，metadata 继续做 JSON 精确过滤。",
                        "Stored Responses 支持本地 retrieve/delete/cancel、input_items list、input_tokens deterministic estimate 与 compact emulation；带 OpenAI Direct upstream lineage 的 stored Response 会对 retrieve/delete/cancel/input_items 走远端 passthrough，未知远端 resp_ id 只有提供 model query 或 X-AI-Gateway-OpenAI-Model header 时才允许 route-hint passthrough。",
                        "Stored Responses retrieve 与 input_items 已接收 include query 参数；本地 stored baseline 对 include 采用 no-op acceptance，带 upstream lineage 或显式 route hint 的 OpenAI Direct 对象会把 include 原样转发到上游。",
                        "OpenAI Direct 非流式 Responses create 会优先返回上游原始 Responses JSON，并把 model 重写为 public model；OpenAI Direct stream=true 时透传上游原始 SSE 事件；无 native raw 能力时回退 canonical encoder。",
                        "Responses tools 当前执行 function tools；file_search 可校验本地 vector_store_ids 并把本地 search 结果注入上下文，但不声明 hosted file_search_call lifecycle；web_search_preview、mcp、custom、code_interpreter、computer_use_preview、image_generation、shell/apply_patch 等仍会显式拒绝。",
                        "OpenAI Conversations 使用 gateway local lineage，支持 conversation create/retrieve/update/delete 与 item create/list/retrieve/delete；item list 默认 order=desc、limit=20，一次最多追加 20 个 item。",
                        "OpenAI Vector Stores 使用 gateway local lifecycle 基线，支持 vector_store create/list/retrieve/update/delete、vector_store.file attach/list/retrieve/delete/content、本地 chunk ingestion、本地文本 search、Responses file_search 本地绑定以及 vector_store.file_batch create/retrieve/cancel/list files；真实 embedding/vector index 入库、语义向量检索和 hosted file_search_call lifecycle 仍按 TASK-20260514-023 后续拆分。",
                        "非流式 Chat/Responses 支持 Idempotency-Key 本地响应重放；同 key 不同请求体会被拒绝，幂等记录默认保留 24 小时。",
                        "Chat stream 支持 stream_options.include_usage，开启后会在 [DONE] 前输出 choices=[] 的 usage chunk；Responses canonical stream event 会携带本地单调递增 sequence_number，并按 stream_options.include_obfuscation 控制 delta event obfuscation 字段；OpenAI Direct raw SSE 保留上游原始 sequence 与 event shape。",
                        "OpenAI Webhooks 提供 POST /v1/webhooks/openai 接收入口，按 Standard Webhooks 校验 webhook-id、webhook-timestamp、webhook-signature，使用 raw body 验签，并把合法 event 保存为本地 WEBHOOK_EVENT；重复 delivery 或重复 event id 返回 duplicate=true 且不重复落库。",
                        "OpenAI path 本地限流命中会返回 429、rate_limit_error，并带 Retry-After 与 x-ratelimit remaining/reset headers。",
                        "Files、Uploads、Models、Vector Stores 和 Realtime client secret 仅作为对话、tools、RAG/file_search 的支撑能力公开；官方非核心 API 不纳入公开兼容面。",
                        "Anthropic、Gemini、Vertex 按 OpenAI 标准功能区收紧为 chat/messages/generateContent、tools、embeddings/files 等支撑面；Codex 单独限定为 Responses smoke/反代边界，拒绝 provider-specific async/admin/eval 和非 Responses Codex 内部接口。"
                ),
                List.of(
                        "usage_record 记录 tokens 与 completeness，billing rollup 可按 day/week/month 聚合。",
                        "支付订单进入 PAID 后会写入 gateway_user_balance_ledger，重复 webhook 通过幂等键保护。",
                        "限流命中时优先检查 key 的 rpm/tpm/concurrency 和 runtime rate limit 状态。"
                ),
                List.of(
                        "chat.openai-compatible",
                        "chat.openai-typed-parameters",
                        "chat.openai-stored-lifecycle",
                        "openai.list-pagination-envelope",
                        "openai.stored-chat-db-cursor-pagination",
                        "openai.idempotency-replay",
                        "openai.responses-local-lifecycle",
                        "openai.streaming-event-usage-sequence",
                        "openai.responses-stream-obfuscation",
                       "openai.responses-input-tokens-compact",
                       "openai.responses-input-tokens-native-passthrough",
                        "openai.responses-compact-native-passthrough",
                        "openai.responses-native-json-passthrough",
                        "openai.responses-native-stream-sse-passthrough",
                        "openai.responses-remote-lifecycle-passthrough",
                        "openai.responses-untracked-remote-lifecycle-route-hints",
                        "openai.responses-tool-registry-boundary",
                        "openai.responses-file-search-local-vector-store-binding",
                        "openai.conversations-local-lifecycle",
                        "openai.vector-stores-local-lifecycle",
                        "openai.vector-store-files-local-attachment",
                        "openai.vector-store-files-local-ingestion-artifact",
                        "openai.vector-store-file-content-local-read",
                        "openai.vector-store-search-local-text",
                        "openai.vector-store-file-batches-local-lifecycle",
                        "openai.webhook-signature-replay",
                        "openai.webhooks-ingress-event-persistence",
                        "claude.messages.translation",
                        "gemini.generate-content.translation",
                        "codex.responses-smoke-boundary",
                        "ollama.chat.native",
                        "web_search.provider-adapter",
                        "files.cache.operations"
                )
        );
    }

    private PublicDocsBundleResponse english() {
        return new PublicDocsBundleResponse(
                "2026.05.18",
                "en-US",
                "x-ai-gateway Public Compatibility Docs",
                "/public/docs/openapi.json",
                "3.1.0",
                sdkTargets("en-US"),
                i18nPolicy("en-US"),
                List.of(
                        "Create or retrieve a Distributed Key.",
                        "Set your OpenAI-compatible base URL to https://gateway.example.com/v1.",
                        "Use the full Distributed Key secret as the Authorization Bearer token.",
                        "Run /v1/chat/completions first, then enable Responses, Claude Messages, Gemini/Vertex generateContent, or Codex Responses smoke within the OpenAI standard functional zone.",
                        "On failures, inspect error_code and requestId, then query admin traces."
                ),
                compatibility(),
                providerPresets(),
                cliClients(),
                examples("en-US"),
                errorCodes("en-US"),
                List.of(
                        "Routing honors Distributed Key model/provider/client-family restrictions and Route Policy runtime state.",
                        "Retry/Fallback decisions are visible in traces with provider, credential and degradation details.",
                        "OpenAI Direct supports typed Chat parameters such as response_format, tools/tool_choice, modalities/audio and web_search_options; OpenAI-compatible sites still depend on the provider capability matrix.",
                        "Stored Chat list/messages use the OpenAI-compatible list envelope; limit defaults to 20 with range 1 to 100, and order defaults to asc. Chat Completion lists push tenant, key prefix, model, createdAt/id cursor and order into database queries while keeping exact JSON metadata filtering.",
                        "Stored Responses support local retrieve/delete/cancel, input_items lists, input_tokens deterministic estimates and compact emulation; stored Responses with OpenAI Direct upstream lineage use remote passthrough for retrieve/delete/cancel/input_items, and unknown remote resp_ ids require a model query or X-AI-Gateway-OpenAI-Model header for route-hint passthrough.",
                        "Stored Responses retrieve and input_items accept the include query parameter; the local stored baseline treats include as no-op acceptance, while OpenAI Direct objects with upstream lineage or explicit route hints forward include to upstream.",
                        "OpenAI Direct non-streaming Responses create prefers the upstream raw Responses JSON and rewrites model to the public model; OpenAI Direct stream=true passes through upstream raw SSE events; canonical encoding remains the fallback when no native raw capability exists.",
                        "Responses tools currently execute function tools; file_search can validate local vector_store_ids and inject local search context, but hosted file_search_call lifecycle is not claimed. web_search_preview, mcp, custom, code_interpreter, computer_use_preview, image_generation, shell and apply_patch remain explicitly rejected.",
                        "OpenAI Conversations use gateway local lineage and support conversation create/retrieve/update/delete plus item create/list/retrieve/delete; item lists default to order=desc and limit=20, and each create call accepts at most 20 items.",
                        "OpenAI Vector Stores use a gateway-local lifecycle baseline for vector_store create/list/retrieve/update/delete, vector_store.file attach/list/retrieve/delete/content, local chunk ingestion, local text search, Responses file_search local binding and vector_store.file_batch create/retrieve/cancel/list files; real embedding/vector index ingestion, semantic vector retrieval and hosted file_search_call lifecycle remain tracked under TASK-20260514-023.",
                        "Non-streaming Chat/Responses support local Idempotency-Key response replay; reusing a key with a different request body is rejected, and records are retained for 24 hours by default.",
                        "Chat streams support stream_options.include_usage by emitting a choices=[] usage chunk before [DONE]; Responses canonical stream events include a local monotonic sequence_number and honor stream_options.include_obfuscation for delta event obfuscation fields; OpenAI Direct raw SSE keeps upstream sequence and event shape.",
                        "OpenAI Webhooks expose POST /v1/webhooks/openai, verify webhook-id, webhook-timestamp and webhook-signature against the raw body, persist valid events as local WEBHOOK_EVENT records, and return duplicate=true without another write for duplicate deliveries or duplicate event ids.",
                        "Local rate limit hits on OpenAI paths return 429, rate_limit_error, Retry-After and x-ratelimit remaining/reset headers.",
                        "Files, Uploads, Models, Vector Stores and Realtime client secrets are exposed only as support surfaces for conversations, tools and RAG/file_search; official non-core APIs are outside the public compatibility surface.",
                        "Anthropic, Gemini and Vertex are narrowed to the OpenAI standard functional zone for chat/messages/generateContent, tools and embeddings/files support surfaces; Codex is separately limited to the Responses smoke/proxy boundary, while provider-specific async/admin/eval APIs and non-Responses Codex internals are rejected."
                ),
                List.of(
                        "usage_record stores token usage and completeness; billing rollup supports day/week/month windows.",
                        "A paid payment order appends gateway_user_balance_ledger entries with idempotent webhook protection.",
                        "For rate limit errors, check key rpm/tpm/concurrency and runtime rate limit state."
                ),
                List.of(
                        "chat.openai-compatible",
                        "chat.openai-typed-parameters",
                        "chat.openai-stored-lifecycle",
                        "openai.list-pagination-envelope",
                        "openai.stored-chat-db-cursor-pagination",
                        "openai.idempotency-replay",
                        "openai.responses-local-lifecycle",
                        "openai.streaming-event-usage-sequence",
                        "openai.responses-stream-obfuscation",
                        "openai.responses-input-tokens-compact",
                        "openai.responses-input-tokens-native-passthrough",
                        "openai.responses-compact-native-passthrough",
                        "openai.responses-native-json-passthrough",
                        "openai.responses-native-stream-sse-passthrough",
                        "openai.responses-remote-lifecycle-passthrough",
                        "openai.responses-untracked-remote-lifecycle-route-hints",
                        "openai.responses-tool-registry-boundary",
                        "openai.responses-file-search-local-vector-store-binding",
                        "openai.conversations-local-lifecycle",
                        "openai.vector-stores-local-lifecycle",
                        "openai.vector-store-files-local-attachment",
                        "openai.vector-store-files-local-ingestion-artifact",
                        "openai.vector-store-file-content-local-read",
                        "openai.vector-store-search-local-text",
                        "openai.vector-store-file-batches-local-lifecycle",
                        "openai.webhook-signature-replay",
                        "openai.webhooks-ingress-event-persistence",
                        "claude.messages.translation",
                        "gemini.generate-content.translation",
                        "codex.responses-smoke-boundary",
                        "ollama.chat.native",
                        "web_search.provider-adapter",
                        "files.cache.operations"
                )
        );
    }

    public JsonNode openApi() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("openapi", "3.1.0");
        ObjectNode info = root.putObject("info");
        info.put("title", "x-ai-gateway Public API");
        info.put("version", "2026.05.18");
        info.put("description", "公开接入面的最小 OpenAPI 事实源，按 OpenAI 标准功能区覆盖 docs、OpenAI-compatible、Claude/Gemini/Vertex 功能性入口、Codex Responses smoke 边界和 Media provider matrix。");
        root.putArray("servers")
                .addObject()
                .put("url", "https://gateway.example.com")
                .put("description", "生产网关地址示例");
        ObjectNode paths = root.putObject("paths");
        addPath(paths, "get", "/public/docs/compatibility", "读取公开兼容文档 bundle", false);
        addPath(paths, "get", "/public/docs/openapi.json", "读取公开 OpenAPI JSON", false);
        ObjectNode chatCreate = addPath(paths, "post", "/v1/chat/completions", "OpenAI-compatible Chat Completions", true);
        addChatCompletionRequestBody(chatCreate);
        addIdempotencyHeader(chatCreate);
        ObjectNode chatList = addPath(paths, "get", "/v1/chat/completions", "List stored Chat Completions", true);
        addStoredChatCompletionListParameters(chatList);
        ObjectNode chatGet = addPath(paths, "get", "/v1/chat/completions/{completionId}", "Retrieve stored Chat Completion", true);
        addCompletionIdPathParameter(chatGet);
        ObjectNode chatUpdate = addPath(paths, "post", "/v1/chat/completions/{completionId}", "Update stored Chat Completion metadata", true);
        addCompletionIdPathParameter(chatUpdate);
        ObjectNode chatDelete = addPath(paths, "delete", "/v1/chat/completions/{completionId}", "Delete stored Chat Completion", true);
        addCompletionIdPathParameter(chatDelete);
        ObjectNode chatMessages = addPath(paths, "get", "/v1/chat/completions/{completionId}/messages", "List stored Chat Completion messages", true);
        addCompletionIdPathParameter(chatMessages);
        addStoredChatMessageListParameters(chatMessages);
        ObjectNode responsesCreate = addPath(paths, "post", "/v1/responses", "OpenAI-compatible Responses", true);
        addResponsesRequestBody(responsesCreate);
        addIdempotencyHeader(responsesCreate);
        ObjectNode codexResponses = addPath(paths, "post", "/backend-api/codex/responses", "ChatGPT / Codex Responses native compatibility endpoint", true);
        codexResponses.put("description", "Exposes the ChatGPT / Codex native responses compatibility endpoint, using specialized fields like messages, parent_message_id, and reasoning_effort.");
        addCodexResponsesRequestBody(codexResponses);
        addIdempotencyHeader(codexResponses);
        ObjectNode responsesInputTokens = addPath(paths, "post", "/v1/responses/input_tokens", "Count Response input tokens", true);
        responsesInputTokens.put("description", "Counts Response input tokens. OpenAI Direct routes use native upstream counting when available; route-unavailable cases fall back to the local deterministic estimate.");
        addResponsesRequestBody(responsesInputTokens);
        ObjectNode responsesCompact = addPath(paths, "post", "/v1/responses/compact", "Compact Response input context", true);
        responsesCompact.put("description", "Compacts Response input context. OpenAI Direct routes use native upstream compaction when available; route-unavailable cases fall back to the local opaque marker emulation.");
        addResponsesRequestBody(responsesCompact);
        ObjectNode responsesGet = addPath(paths, "get", "/v1/responses/{responseId}", "Retrieve stored Response", true);
        responsesGet.put("description", "Returns local stored Responses from gateway cache; OpenAI Direct stored Responses with upstream lineage are synced from the original upstream Response first. If the local object is missing, model query or X-AI-Gateway-OpenAI-Model can explicitly route an unknown remote resp_ id to OpenAI Direct.");
        addResponseIdPathParameter(responsesGet);
        addResponsesIncludeParameter(responsesGet);
        addResponsesRemoteRouteHintParameters(responsesGet);
        ObjectNode responsesDelete = addPath(paths, "delete", "/v1/responses/{responseId}", "Delete stored Response", true);
        responsesDelete.put("description", "Deletes local stored Responses; OpenAI Direct stored Responses with upstream lineage issue DELETE to the original upstream Response before marking the local object deleted. Unknown remote resp_ ids require an explicit model route hint.");
        addResponseIdPathParameter(responsesDelete);
        addResponsesRemoteRouteHintParameters(responsesDelete);
        ObjectNode responsesCancel = addPath(paths, "post", "/v1/responses/{responseId}/cancel", "Cancel stored background Response", true);
        responsesCancel.put("description", "Cancels local background Responses or forwards cancel to the original OpenAI Direct upstream Response when lineage is available. Unknown remote resp_ ids require an explicit model route hint.");
        addResponseIdPathParameter(responsesCancel);
        addResponsesRemoteRouteHintParameters(responsesCancel);
        ObjectNode responsesInputItems = addPath(paths, "get", "/v1/responses/{responseId}/input_items", "List stored Response input items", true);
        responsesInputItems.put("description", "Lists local input items or forwards the query to the original OpenAI Direct upstream Response when lineage is available. Unknown remote resp_ ids require an explicit model route hint.");
        addResponseIdPathParameter(responsesInputItems);
        addResponseInputItemsListParameters(responsesInputItems);
        addResponsesRemoteRouteHintParameters(responsesInputItems);
        ObjectNode conversationCreate = addPath(paths, "post", "/v1/conversations", "Create a local OpenAI Conversation", true);
        conversationCreate.put("description", "Creates a gateway-local Conversation lineage object with optional metadata and up to 20 initial items.");
        addConversationRequestBody(conversationCreate, false);
        ObjectNode conversationGet = addPath(paths, "get", "/v1/conversations/{conversationId}", "Retrieve a local OpenAI Conversation", true);
        addConversationIdPathParameter(conversationGet);
        ObjectNode conversationUpdate = addPath(paths, "post", "/v1/conversations/{conversationId}", "Update local OpenAI Conversation metadata", true);
        addConversationIdPathParameter(conversationUpdate);
        addConversationRequestBody(conversationUpdate, false);
        ObjectNode conversationDelete = addPath(paths, "delete", "/v1/conversations/{conversationId}", "Delete a local OpenAI Conversation", true);
        addConversationIdPathParameter(conversationDelete);
        ObjectNode conversationItemsCreate = addPath(paths, "post", "/v1/conversations/{conversationId}/items", "Create local OpenAI Conversation items", true);
        conversationItemsCreate.put("description", "Adds up to 20 items to a gateway-local Conversation and returns an OpenAI-compatible list envelope.");
        addConversationIdPathParameter(conversationItemsCreate);
        addResponsesIncludeParameter(conversationItemsCreate);
        addConversationItemsRequestBody(conversationItemsCreate);
        ObjectNode conversationItemsList = addPath(paths, "get", "/v1/conversations/{conversationId}/items", "List local OpenAI Conversation items", true);
        conversationItemsList.put("description", "Lists gateway-local Conversation items. The local baseline accepts include as a no-op and supports after, limit and order.");
        addConversationIdPathParameter(conversationItemsList);
        addConversationItemsListParameters(conversationItemsList);
        ObjectNode conversationItemGet = addPath(paths, "get", "/v1/conversations/{conversationId}/items/{itemId}", "Retrieve a local OpenAI Conversation item", true);
        addConversationIdPathParameter(conversationItemGet);
        addConversationItemIdPathParameter(conversationItemGet);
        addResponsesIncludeParameter(conversationItemGet);
        ObjectNode conversationItemDelete = addPath(paths, "delete", "/v1/conversations/{conversationId}/items/{itemId}", "Delete a local OpenAI Conversation item", true);
        addConversationIdPathParameter(conversationItemDelete);
        addConversationItemIdPathParameter(conversationItemDelete);
        ObjectNode vectorStoreCreate = addPath(paths, "post", "/v1/vector_stores", "Create a local OpenAI Vector Store", true);
        vectorStoreCreate.put("description", "Creates a gateway-local Vector Store lifecycle object. File ids are attached as local references with local chunk ingestion metadata; local content read and local text search are available, while hosted OpenAI embedding/vector index ingestion is not implemented in this baseline.");
        addVectorStoreRequestBody(vectorStoreCreate);
        ObjectNode vectorStoreList = addPath(paths, "get", "/v1/vector_stores", "List local OpenAI Vector Stores", true);
        vectorStoreList.put("description", "Lists gateway-local Vector Stores for the current Distributed Key with OpenAI-compatible list envelope pagination.");
        addVectorStoreListParameters(vectorStoreList);
        ObjectNode vectorStoreGet = addPath(paths, "get", "/v1/vector_stores/{vectorStoreId}", "Retrieve a local OpenAI Vector Store", true);
        addVectorStoreIdPathParameter(vectorStoreGet);
        ObjectNode vectorStoreUpdate = addPath(paths, "post", "/v1/vector_stores/{vectorStoreId}", "Update a local OpenAI Vector Store", true);
        addVectorStoreIdPathParameter(vectorStoreUpdate);
        addVectorStoreRequestBody(vectorStoreUpdate);
        ObjectNode vectorStoreSearch = addPath(paths, "post", "/v1/vector_stores/{vectorStoreId}/search", "Search a local OpenAI Vector Store", true);
        vectorStoreSearch.put("description", "Searches persisted local ingestion chunks first with a deterministic UTF-8 lexical baseline, and falls back to raw gateway file text for legacy attachments. This does not perform hosted OpenAI semantic vector retrieval or rerank.");
        addVectorStoreIdPathParameter(vectorStoreSearch);
        addVectorStoreSearchRequestBody(vectorStoreSearch);
        ObjectNode vectorStoreDelete = addPath(paths, "delete", "/v1/vector_stores/{vectorStoreId}", "Delete a local OpenAI Vector Store", true);
        addVectorStoreIdPathParameter(vectorStoreDelete);
        ObjectNode vectorStoreFileCreate = addPath(paths, "post", "/v1/vector_stores/{vectorStoreId}/files", "Attach a local OpenAI Vector Store File", true);
        vectorStoreFileCreate.put("description", "Attaches a file id to a gateway-local Vector Store, reads the current gateway file, and persists local chunk ingestion metadata plus usage_bytes. This does not perform hosted OpenAI embedding/vector index ingestion.");
        addVectorStoreIdPathParameter(vectorStoreFileCreate);
        addVectorStoreFileRequestBody(vectorStoreFileCreate);
        ObjectNode vectorStoreFileList = addPath(paths, "get", "/v1/vector_stores/{vectorStoreId}/files", "List local OpenAI Vector Store Files", true);
        vectorStoreFileList.put("description", "Lists gateway-local Vector Store File attachments. Local chunk ingestion metadata, file content read and deterministic local text search are available; hosted OpenAI embedding/vector index ingestion and semantic vector search are not implemented in this baseline.");
        addVectorStoreIdPathParameter(vectorStoreFileList);
        addVectorStoreFileListParameters(vectorStoreFileList);
        ObjectNode vectorStoreFileGet = addPath(paths, "get", "/v1/vector_stores/{vectorStoreId}/files/{fileId}", "Retrieve a local OpenAI Vector Store File", true);
        addVectorStoreIdPathParameter(vectorStoreFileGet);
        addVectorStoreFileIdPathParameter(vectorStoreFileGet);
        ObjectNode vectorStoreFileContent = addPath(paths, "get", "/v1/vector_stores/{vectorStoreId}/files/{fileId}/content", "Retrieve local OpenAI Vector Store File content", true);
        vectorStoreFileContent.put("description", "Returns a gateway-local vector_store.file_content.page by reading the attached gateway file as a UTF-8 text page. This does not perform hosted OpenAI parsing, embedding or vector ingestion.");
        addVectorStoreIdPathParameter(vectorStoreFileContent);
        addVectorStoreFileIdPathParameter(vectorStoreFileContent);
        ObjectNode vectorStoreFileDelete = addPath(paths, "delete", "/v1/vector_stores/{vectorStoreId}/files/{fileId}", "Delete a local OpenAI Vector Store File", true);
        addVectorStoreIdPathParameter(vectorStoreFileDelete);
        addVectorStoreFileIdPathParameter(vectorStoreFileDelete);
        ObjectNode vectorStoreFileBatchCreate = addPath(paths, "post", "/v1/vector_stores/{vectorStoreId}/file_batches", "Create a local OpenAI Vector Store File Batch", true);
        vectorStoreFileBatchCreate.put("description", "Creates a gateway-local vector_store.file_batch and attaches multiple file ids after all duplicate checks pass, persisting local chunk ingestion metadata for each file. This does not perform hosted OpenAI embedding/vector index ingestion.");
        addVectorStoreIdPathParameter(vectorStoreFileBatchCreate);
        addVectorStoreFileBatchRequestBody(vectorStoreFileBatchCreate);
        ObjectNode vectorStoreFileBatchGet = addPath(paths, "get", "/v1/vector_stores/{vectorStoreId}/file_batches/{batchId}", "Retrieve a local OpenAI Vector Store File Batch", true);
        addVectorStoreIdPathParameter(vectorStoreFileBatchGet);
        addVectorStoreFileBatchIdPathParameter(vectorStoreFileBatchGet);
        ObjectNode vectorStoreFileBatchCancel = addPath(paths, "post", "/v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/cancel", "Cancel a local OpenAI Vector Store File Batch", true);
        vectorStoreFileBatchCancel.put("description", "Rejects cancel for gateway-local completed batches; future asynchronous ingestion may extend cancellable in-progress states.");
        addVectorStoreIdPathParameter(vectorStoreFileBatchCancel);
        addVectorStoreFileBatchIdPathParameter(vectorStoreFileBatchCancel);
        ObjectNode vectorStoreFileBatchFiles = addPath(paths, "get", "/v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/files", "List files in a local OpenAI Vector Store File Batch", true);
        vectorStoreFileBatchFiles.put("description", "Lists active vector_store.file attachments that were created by this gateway-local file batch.");
        addVectorStoreIdPathParameter(vectorStoreFileBatchFiles);
        addVectorStoreFileBatchIdPathParameter(vectorStoreFileBatchFiles);
        addVectorStoreFileListParameters(vectorStoreFileBatchFiles);
        ObjectNode openAiWebhook = addPath(paths, "post", "/v1/webhooks/openai", "Accept OpenAI webhook delivery", false);
        openAiWebhook.put("description", "Verifies Standard Webhooks headers against the raw request body and stores valid OpenAI events as gateway-local WEBHOOK_EVENT resources. Duplicate webhook deliveries or duplicate event ids return duplicate=true without another write.");
        addWebhookHeaders(openAiWebhook);
        addWebhookRequestBody(openAiWebhook);
        ObjectNode modelList = addPath(paths, "get", "/v1/models", "List accessible OpenAI-compatible models", true);
        modelList.put("description", "Lists public gateway model ids and aliases accessible to the current Distributed Key.");
        ObjectNode modelGet = addPath(paths, "get", "/v1/models/{model}", "Retrieve an accessible OpenAI-compatible model", true);
        modelGet.put("description", "Retrieves model metadata from the gateway model catalog when the current Distributed Key may access it.");
        addModelPathParameter(modelGet);
        addPath(paths, "post", "/v1/web_search", "Provider-governed Web Search", true);
        addPath(paths, "post", "/v1/messages", "Claude Messages compatible endpoint", true);
        addPath(paths, "post", "/v1beta/models/{model}:generateContent", "Gemini generateContent compatible endpoint", true);
        addPath(paths, "post", "/api/v1/videos/generations", "创建 Video async task", true);
        addPath(paths, "get", "/api/v1/videos/{videoId}", "读取 Video async task", true);
        addPath(paths, "post", "/api/v1/videos/{videoId}/cancel", "取消 Video async task", true);
        addPath(paths, "get", "/api/v1/videos/{videoId}/download", "下载 Video async task 产物引用", true);
        addPath(paths, "post", "/api/v1/music/generations", "创建 Music async task", true);
        addPath(paths, "get", "/api/v1/music/{musicId}", "读取 Music async task", true);
        addPath(paths, "post", "/api/v1/music/{musicId}/cancel", "取消 Music async task", true);
        addPath(paths, "get", "/api/v1/music/{musicId}/download", "下载 Music async task 产物引用", true);
        addPath(paths, "get", "/api/v1/media/provider-matrix", "读取 Video/Music provider support matrix", true);
        ObjectNode components = root.putObject("components");
        components.putObject("securitySchemes")
                .putObject("bearerAuth")
                .put("type", "http")
                .put("scheme", "bearer")
                .put("bearerFormat", "Distributed Key");
        return root;
    }

    private List<PublicDocsCompatibilityResponse> compatibility() {
        return List.of(
                new PublicDocsCompatibilityResponse(
                        "openai",
                        "/v1",
                        List.of("OpenAI SDK", "Codex", "OpenCode", "OpenClaw", "curl"),
                        List.of("chat.completions", "chat.typed-parameters", "stored_chat.completions", "responses", "responses.lifecycle", "responses.file_search_local_vector_store_binding", "conversations.local_lineage", "vector_stores.local_lifecycle", "vector_store_files.local_attachment", "vector_store_files.local_ingestion_artifact", "vector_store_files.local_content_read", "vector_stores.local_text_search", "vector_store_file_batches.local_lifecycle", "webhooks.ingress_event_persistence", "embeddings", "files", "models", "realtime"),
                        "OpenAI-compatible clients should use /v1 as base path. OpenAI Direct supports typed Chat parameters; third-party compatible sites are governed by provider capability."
                ),
                new PublicDocsCompatibilityResponse(
                        "claude",
                        "/v1",
                        List.of("Claude Code", "Anthropic SDK through compatible config"),
                        List.of("messages"),
                        "Claude native semantics are translated when provider capability allows it."
                ),
                new PublicDocsCompatibilityResponse(
                        "gemini",
                        "/v1",
                        List.of("Gemini CLI", "Google GenAI compatible clients"),
                        List.of("generateContent", "files", "cachedContents"),
                        "Gemini native objects are exposed through gateway resource lineage when needed."
                ),
                new PublicDocsCompatibilityResponse(
                        "ollama",
                        "/v1",
                        List.of("Ollama OpenAI-compatible clients"),
                        List.of("chat", "embeddings", "models"),
                        "Local Ollama deployments should be registered as provider sites before routing production traffic."
                ),
                new PublicDocsCompatibilityResponse(
                        "rerank",
                        "/v1",
                        List.of("Cohere-compatible clients", "Jina rerank clients", "curl"),
                        List.of("rerank"),
                        "Use dedicated rerank provider sites such as Cohere or Jina instead of general chat presets."
                ),
                new PublicDocsCompatibilityResponse(
                        "web_search",
                        "/v1",
                        List.of("OpenAI SDK", "Perplexity-compatible clients", "curl"),
                        List.of("web_search", "citations"),
                        "Use OpenAI or Perplexity provider presets; generic OpenAI-compatible sites are not assumed to support web search."
                )
        );
    }

    private List<PublicDocsProviderPresetResponse> providerPresets() {
        return providerCatalogLoader.load().presets().stream()
                .filter(preset -> !preset.deprecated())
                .map(this::toProviderPresetResponse)
                .toList();
    }

    private PublicDocsProviderPresetResponse toProviderPresetResponse(ProviderPresetDefinition preset) {
        return new PublicDocsProviderPresetResponse(
                preset.code(),
                preset.displayName(),
                preset.siteKind(),
                preset.compatibilitySurface(),
                preset.supportStrategy(),
                preset.capabilityTags(),
                preset.modelFamilies(),
                preset.pricingMetadata(),
                preset.unsupportedFeatures()
        );
    }

    private List<PublicDocsCliClientResponse> cliClients() {
        return cloudCliClientMatrixService.clients().stream()
                .map(this::toCliClientResponse)
                .toList();
    }

    private PublicDocsCliClientResponse toCliClientResponse(CloudCliClientDescriptor descriptor) {
        return new PublicDocsCliClientResponse(
                descriptor.client(),
                descriptor.clientFamily().name(),
                descriptor.protocol(),
                descriptor.basePath(),
                descriptor.requiredAuth(),
                descriptor.optionalMetadataHeaders(),
                descriptor.notes()
        );
    }

    private List<PublicDocsExampleResponse> examples(String locale) {
        boolean zh = "zh-CN".equals(locale);
        return List.of(
                new PublicDocsExampleResponse(
                        "curl",
                        "openai",
                        "shell",
                        zh ? "OpenAI-compatible Chat Smoke" : "OpenAI-compatible Chat Smoke",
                        """
                                curl https://gateway.example.com/v1/chat/completions \\
                                  -H "Authorization: Bearer $X_AI_GATEWAY_API_KEY" \\
                                  -H "Content-Type: application/json" \\
                                  -d '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"ping"}]}'
                                """.trim()
                ),
                new PublicDocsExampleResponse(
                        "openai-sdk",
                        "openai",
                        "javascript",
                        zh ? "OpenAI SDK 接入" : "OpenAI SDK Setup",
                        """
                                import OpenAI from "openai";
                                const client = new OpenAI({
                                  apiKey: process.env.X_AI_GATEWAY_API_KEY,
                                  baseURL: "https://gateway.example.com/v1"
                                });
                                """.trim()
                ),
                new PublicDocsExampleResponse(
                        "openai-sdk-advanced-chat",
                        "openai",
                        "javascript",
                        zh ? "OpenAI Chat 高级参数示例" : "OpenAI Chat Advanced Parameters",
                        """
                                const body = {
                                  model: "gpt-4o-mini",
                                  messages: [{ role: "user", content: "Return JSON." }],
                                  response_format: {
                                    type: "json_schema",
                                    json_schema: {
                                      name: "GatewayChatParity",
                                      strict: true,
                                      schema: {
                                        type: "object",
                                        additionalProperties: false,
                                        properties: { status: { type: "string" } },
                                        required: ["status"]
                                      }
                                    }
                                  },
                                  tools: [{ type: "function", function: { name: "record_gateway_check", parameters: { type: "object", properties: { status: { type: "string" } }, required: ["status"] } } }],
                                  tool_choice: "auto",
                                  store: false,
                                  metadata: { example: "chat-advanced-parameters" },
                                  web_search_options: { search_context_size: "medium" }
                                };
                                """.trim()
                ),
                new PublicDocsExampleResponse(
                        "claude-code",
                        "claude",
                        "shell",
                        zh ? "Claude Code 环境变量" : "Claude Code Environment",
                        """
                                export ANTHROPIC_API_KEY="$X_AI_GATEWAY_API_KEY"
                                export ANTHROPIC_BASE_URL="https://gateway.example.com/v1"
                                """.trim()
                ),
                new PublicDocsExampleResponse(
                        "gemini-cli",
                        "gemini",
                        "shell",
                        zh ? "Gemini CLI 环境变量" : "Gemini CLI Environment",
                        """
                                export GEMINI_API_KEY="$X_AI_GATEWAY_API_KEY"
                                export GEMINI_BASE_URL="https://gateway.example.com/v1"
                                """.trim()
                ),
                new PublicDocsExampleResponse(
                        "codex-cli",
                        "openai",
                        "shell",
                        zh ? "Codex CLI 云端代理接入" : "Codex CLI Cloud Gateway",
                        """
                                export OPENAI_API_KEY="$X_AI_GATEWAY_API_KEY"
                                export OPENAI_BASE_URL="https://gateway.example.com/v1"
                                """.trim()
                )
        );
    }

    private List<String> sdkTargets(String locale) {
        boolean zh = "zh-CN".equals(locale);
        return zh
                ? List.of("curl", "OpenAI SDK", "Claude Code", "Gemini CLI", "Codex CLI", "OpenCode", "Cursor")
                : List.of("curl", "OpenAI SDK", "Claude Code", "Gemini CLI", "Codex CLI", "OpenCode", "Cursor");
    }

    private List<String> i18nPolicy(String locale) {
        boolean zh = "zh-CN".equals(locale);
        return zh
                ? List.of(
                        "zh-CN 是管理端与 Portal 默认 UI 语言。",
                        "en-US 覆盖公开 docs bundle、OpenAPI 描述和 SDK 示例。",
                        "前端运行时语言切换尚未启用，后续先抽取导航、标题、表格列名、按钮和错误提示。"
                )
                : List.of(
                        "zh-CN is the default Admin and Portal UI language.",
                        "en-US covers the public docs bundle, OpenAPI descriptions and SDK examples.",
                        "Runtime frontend language switching is not enabled yet; navigation, titles, table headers, buttons and errors should be extracted first."
                );
    }

    private ObjectNode addPath(ObjectNode paths, String method, String path, String summary, boolean bearerAuth) {
        ObjectNode pathNode = paths.has(path) && paths.get(path).isObject()
                ? (ObjectNode) paths.get(path)
                : paths.putObject(path);
        ObjectNode operation = pathNode.putObject(method);
        operation.put("summary", summary);
        operation.put("operationId", operationId(method, path));
        operation.putArray("tags").add(path.startsWith("/public") ? "public-docs" : "gateway");
        if (bearerAuth) {
            operation.putArray("security").addObject().putArray("bearerAuth");
        }
        operation.putObject("responses")
                .putObject("200")
                .put("description", "OK");
        return operation;
    }

    private void addChatCompletionRequestBody(ObjectNode operation) {
        ObjectNode schema = operation.putObject("requestBody")
                .putObject("content")
                .putObject("application/json")
                .putObject("schema");
        schema.put("type", "object");
        schema.putArray("required").add("model").add("messages");
        ObjectNode properties = schema.putObject("properties");
        addProperty(properties, "model", "string", "OpenAI model name or gateway model alias.");
        addProperty(properties, "messages", "array", "Chat messages in OpenAI-compatible format.");
        addProperty(properties, "tools", "array", "Function tool definitions.");
        addProperty(properties, "tool_choice", "object", "Tool choice string or object.");
        addProperty(properties, "store", "boolean", "When true, persist the Chat Completion as a local chatcmpl_ resource.");
        addProperty(properties, "metadata", "object", "OpenAI metadata object; also used by stored Chat lifecycle.");
        addProperty(properties, "response_format", "object", "Typed response format: text, json_object or json_schema.");
        addProperty(properties, "modalities", "array", "Requested output modalities such as text or audio.");
        addProperty(properties, "audio", "object", "Audio voice and format when modalities includes audio.");
        addProperty(properties, "web_search_options", "object", "OpenAI web search context and approximate user location.");
        addProperty(properties, "service_tier", "string", "OpenAI service tier such as auto or default.");
        addProperty(properties, "parallel_tool_calls", "boolean", "Whether parallel function tool calls are allowed.");
        addProperty(properties, "stream_options", "object", "Streaming options such as include_usage; include_usage emits a choices=[] usage chunk before [DONE].");
        addProperty(properties, "prediction", "object", "OpenAI prediction object for compatible models.");
        addProperty(properties, "prompt_cache_key", "string", "Prompt cache affinity key.");
        addProperty(properties, "safety_identifier", "string", "OpenAI safety identifier.");
    }

    private void addResponsesRequestBody(ObjectNode operation) {
        ObjectNode schema = operation.putObject("requestBody")
                .putObject("content")
                .putObject("application/json")
                .putObject("schema");
        schema.put("type", "object");
        schema.putArray("required").add("model");
        ObjectNode properties = schema.putObject("properties");
        addProperty(properties, "model", "string", "OpenAI model name or gateway model alias.");
        addProperty(properties, "input", "object", "Responses input string, message array or item array.");
        addProperty(properties, "stream", "boolean", "When true, emit OpenAI Responses-style server-sent events. OpenAI Direct streams pass through upstream raw SSE events when native passthrough is available.");
        addProperty(properties, "stream_options", "object", "Responses streaming options such as include_obfuscation; obfuscation is included on delta events by default and can be disabled with include_obfuscation=false.");
        addProperty(properties, "store", "boolean", "When true, persist the Response as a local resp_ resource.");
        addProperty(properties, "background", "boolean", "When true, mark stored local Response as background-capable for cancel.");
        addProperty(properties, "metadata", "object", "OpenAI metadata object preserved by the gateway.");
        addProperty(properties, "include", "array", "Additional output fields requested by OpenAI Responses clients.");
        addProperty(properties, "previous_response_id", "string", "Previous Response id for conversation continuation.");
        addProperty(properties, "tools", "array", "Responses tool definitions. Function tools execute through canonical tool calling; file_search can bind local vector_store_ids and inject local search context; other hosted/MCP/custom tools are rejected explicitly instead of being ignored.");
        addProperty(properties, "tool_choice", "object", "Responses tool choice string or object. Non-function forced tool choices are rejected explicitly until their execution boundary is implemented.");
    }

    private void addCodexResponsesRequestBody(ObjectNode operation) {
        ObjectNode schema = operation.putObject("requestBody")
                .putObject("content")
                .putObject("application/json")
                .putObject("schema");
        schema.put("type", "object");
        schema.putArray("required").add("model");
        ObjectNode properties = schema.putObject("properties");
        addProperty(properties, "model", "string", "OpenAI model name or gateway model alias.");
        addProperty(properties, "messages", "array", "Codex conversation messages array.");
        addProperty(properties, "parent_message_id", "string", "Parent message ID for session thread affinity.");
        addProperty(properties, "reasoning_effort", "string", "Reasoning effort configuration (e.g. low, medium, high).");
        addProperty(properties, "stream", "boolean", "When true, emit Codex-style server-sent events.");
        addProperty(properties, "store", "boolean", "When true, persist the Response as a local resp_ resource.");
        addProperty(properties, "metadata", "object", "OpenAI metadata object preserved by the gateway.");
    }

    private void addConversationRequestBody(ObjectNode operation, boolean requireMetadata) {
        ObjectNode schema = operation.putObject("requestBody")
                .putObject("content")
                .putObject("application/json")
                .putObject("schema");
        schema.put("type", "object");
        if (requireMetadata) {
            schema.putArray("required").add("metadata");
        }
        ObjectNode properties = schema.putObject("properties");
        addProperty(properties, "metadata", "object", "OpenAI metadata object. Keys are capped at 64 chars and values at 512 chars.");
        addProperty(properties, "items", "array", "Initial Conversation items. The gateway accepts at most 20 items per call.");
    }

    private void addConversationItemsRequestBody(ObjectNode operation) {
        ObjectNode schema = operation.putObject("requestBody")
                .putObject("content")
                .putObject("application/json")
                .putObject("schema");
        schema.put("type", "object");
        schema.putArray("required").add("items");
        ObjectNode properties = schema.putObject("properties");
        addProperty(properties, "items", "array", "Conversation items to add. The gateway accepts at most 20 items per call.");
    }

    private void addVectorStoreRequestBody(ObjectNode operation) {
        ObjectNode schema = operation.putObject("requestBody")
                .putObject("content")
                .putObject("application/json")
                .putObject("schema");
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        addProperty(properties, "name", "string", "Optional Vector Store name.");
        addProperty(properties, "metadata", "object", "OpenAI metadata object preserved by the gateway.");
        addProperty(properties, "file_ids", "array", "Local file ids to associate as references. Local chunk ingestion metadata, content read and local text search are available; hosted OpenAI embedding/vector index ingestion is not implemented in this baseline.");
        addProperty(properties, "expires_after", "object", "OpenAI expires_after object, for example anchor=last_active_at and days=7.");
        addProperty(properties, "expires_at", "integer", "Optional Unix seconds expiration timestamp.");
    }

    private void addVectorStoreSearchRequestBody(ObjectNode operation) {
        ObjectNode schema = operation.putObject("requestBody")
                .putObject("content")
                .putObject("application/json")
                .putObject("schema");
        schema.put("type", "object");
        schema.putArray("required").add("query");
        ObjectNode properties = schema.putObject("properties");
        addProperty(properties, "query", "string", "Search query string or string array. Local baseline accepts either shape.");
        addProperty(properties, "filters", "object", "Attribute filter tree over vector_store.file attributes. Supports eq/ne/gt/gte/lt/lte/in/nin and and/or.");
        addProperty(properties, "max_num_results", "integer", "Maximum local results to return. Defaults to 10; valid range is 1 to 50.");
        addProperty(properties, "ranking_options", "object", "Local lexical score_threshold is honored; ranker is preserved as metadata only.");
        addProperty(properties, "rewrite_query", "boolean", "Accepted as a compatibility no-op in the local lexical baseline.");
    }

    private void addVectorStoreFileRequestBody(ObjectNode operation) {
        ObjectNode schema = operation.putObject("requestBody")
                .putObject("content")
                .putObject("application/json")
                .putObject("schema");
        schema.put("type", "object");
        schema.putArray("required").add("file_id");
        ObjectNode properties = schema.putObject("properties");
        addProperty(properties, "file_id", "string", "File id to attach to the local Vector Store.");
        addProperty(properties, "attributes", "object", "OpenAI Vector Store File attributes. Values may be strings, numbers, booleans or null.");
        addProperty(properties, "chunking_strategy", "object", "OpenAI chunking strategy object. Defaults locally to auto.");
    }

    private void addVectorStoreFileBatchRequestBody(ObjectNode operation) {
        ObjectNode schema = operation.putObject("requestBody")
                .putObject("content")
                .putObject("application/json")
                .putObject("schema");
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        addProperty(properties, "file_ids", "array", "File ids to attach in one local batch. Provide either file_ids or files, not both.");
        addProperty(properties, "files", "array", "Per-file batch entries. Each item requires file_id and may include attributes or chunking_strategy.");
        addProperty(properties, "attributes", "object", "Attributes applied to every file when file_ids is used.");
        addProperty(properties, "chunking_strategy", "object", "Chunking strategy applied to every file when file_ids is used.");
    }

    private void addWebhookHeaders(ObjectNode operation) {
        addHeaderParameter(operation, "webhook-id", "Unique Standard Webhooks delivery id. Duplicate delivery ids are accepted idempotently.");
        addHeaderParameter(operation, "webhook-timestamp", "Unix seconds timestamp used by the Standard Webhooks signature.");
        addHeaderParameter(operation, "webhook-signature", "Standard Webhooks signature header. Supports v1,base64 signatures and multiple space-separated candidates.");
    }

    private void addWebhookRequestBody(ObjectNode operation) {
        ObjectNode schema = operation.putObject("requestBody")
                .putObject("content")
                .putObject("application/json")
                .putObject("schema");
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        addProperty(properties, "id", "string", "OpenAI webhook event id. Used as the local resource key when present.");
        addProperty(properties, "object", "string", "OpenAI object type, typically event.");
        addProperty(properties, "type", "string", "OpenAI webhook event type such as response.completed.");
        addProperty(properties, "data", "object", "OpenAI event payload.");
    }

    private void addIdempotencyHeader(ObjectNode operation) {
        ObjectNode parameter = parameters(operation).addObject();
        parameter.put("name", "Idempotency-Key");
        parameter.put("in", "header");
        parameter.put("required", false);
        parameter.put("description", "Optional key for non-streaming response replay.");
        parameter.putObject("schema").put("type", "string");
    }

    private void addCompletionIdPathParameter(ObjectNode operation) {
        ObjectNode parameter = parameters(operation).addObject();
        parameter.put("name", "completionId");
        parameter.put("in", "path");
        parameter.put("required", true);
        parameter.put("description", "Stored Chat Completion id.");
        parameter.putObject("schema").put("type", "string");
    }

    private void addResponseIdPathParameter(ObjectNode operation) {
        ObjectNode parameter = parameters(operation).addObject();
        parameter.put("name", "responseId");
        parameter.put("in", "path");
        parameter.put("required", true);
        parameter.put("description", "Stored Response id.");
        parameter.putObject("schema").put("type", "string");
    }

    private void addConversationIdPathParameter(ObjectNode operation) {
        ObjectNode parameter = parameters(operation).addObject();
        parameter.put("name", "conversationId");
        parameter.put("in", "path");
        parameter.put("required", true);
        parameter.put("description", "Local Conversation id.");
        parameter.putObject("schema").put("type", "string");
    }

    private void addConversationItemIdPathParameter(ObjectNode operation) {
        ObjectNode parameter = parameters(operation).addObject();
        parameter.put("name", "itemId");
        parameter.put("in", "path");
        parameter.put("required", true);
        parameter.put("description", "Local Conversation item id.");
        parameter.putObject("schema").put("type", "string");
    }

    private void addVectorStoreIdPathParameter(ObjectNode operation) {
        ObjectNode parameter = parameters(operation).addObject();
        parameter.put("name", "vectorStoreId");
        parameter.put("in", "path");
        parameter.put("required", true);
        parameter.put("description", "Local Vector Store id.");
        parameter.putObject("schema").put("type", "string");
    }

    private void addVectorStoreFileIdPathParameter(ObjectNode operation) {
        ObjectNode parameter = parameters(operation).addObject();
        parameter.put("name", "fileId");
        parameter.put("in", "path");
        parameter.put("required", true);
        parameter.put("description", "File id attached to the local Vector Store.");
        parameter.putObject("schema").put("type", "string");
    }

    private void addVectorStoreFileBatchIdPathParameter(ObjectNode operation) {
        ObjectNode parameter = parameters(operation).addObject();
        parameter.put("name", "batchId");
        parameter.put("in", "path");
        parameter.put("required", true);
        parameter.put("description", "Local Vector Store File Batch id.");
        parameter.putObject("schema").put("type", "string");
    }

    private void addModelPathParameter(ObjectNode operation) {
        ObjectNode parameter = parameters(operation).addObject();
        parameter.put("name", "model");
        parameter.put("in", "path");
        parameter.put("required", true);
        parameter.put("description", "Model id or gateway model alias.");
        parameter.putObject("schema").put("type", "string");
    }

    private void addStoredChatCompletionListParameters(ObjectNode operation) {
        addQueryParameter(operation, "after", "string", "Cursor id of the last Chat Completion from the previous page.");
        addQueryParameter(operation, "limit", "integer", "Number of Chat Completions to return. Defaults to 20; valid range is 1 to 100.");
        addQueryParameter(operation, "model", "string", "Filter by model id.");
        addQueryParameter(operation, "order", "string", "Sort by creation time: asc or desc. Defaults to asc.");
        addQueryParameter(operation, "metadata[key]", "string", "Filter by metadata key, for example metadata[purpose]=qa.");
    }

    private void addStoredChatMessageListParameters(ObjectNode operation) {
        addQueryParameter(operation, "after", "string", "Cursor id of the last message from the previous page.");
        addQueryParameter(operation, "limit", "integer", "Number of messages to return. Defaults to 20; valid range is 1 to 100.");
        addQueryParameter(operation, "order", "string", "Sort by message order: asc or desc. Defaults to asc.");
    }

    private void addResponseInputItemsListParameters(ObjectNode operation) {
        addQueryParameter(operation, "after", "string", "Cursor id of the last input item from the previous page.");
        addResponsesIncludeParameter(operation);
        addQueryParameter(operation, "limit", "integer", "Number of input items to return. Defaults to 20; valid range is 1 to 100.");
        addQueryParameter(operation, "order", "string", "Sort by input item order: asc or desc. Defaults to desc.");
    }

    private void addConversationItemsListParameters(ObjectNode operation) {
        addQueryParameter(operation, "after", "string", "Cursor id of the last Conversation item from the previous page.");
        addResponsesIncludeParameter(operation);
        addQueryParameter(operation, "limit", "integer", "Number of Conversation items to return. Defaults to 20; valid range is 1 to 100.");
        addQueryParameter(operation, "order", "string", "Sort by item creation order: asc or desc. Defaults to desc.");
    }

    private void addVectorStoreListParameters(ObjectNode operation) {
        addQueryParameter(operation, "after", "string", "Cursor id of the last Vector Store from the previous page.");
        addQueryParameter(operation, "limit", "integer", "Number of Vector Stores to return. Defaults to 20; valid range is 1 to 100.");
        addQueryParameter(operation, "order", "string", "Sort by creation time: asc or desc. Defaults to desc.");
    }

    private void addVectorStoreFileListParameters(ObjectNode operation) {
        addQueryParameter(operation, "after", "string", "Cursor id of the last Vector Store File from the previous page.");
        addQueryParameter(operation, "limit", "integer", "Number of Vector Store Files to return. Defaults to 20; valid range is 1 to 100.");
        addQueryParameter(operation, "order", "string", "Sort by creation time: asc or desc. Defaults to desc.");
        addQueryParameter(operation, "filter", "string", "Optional local status filter such as completed or failed.");
    }

    private void addResponsesIncludeParameter(ObjectNode operation) {
        addQueryParameter(operation, "include", "array", "Additional OpenAI Responses fields to include. Local stored baseline accepts this parameter as a no-op; OpenAI Direct objects with upstream lineage or explicit route hints forward it upstream.");
    }

    private void addResponsesRemoteRouteHintParameters(ObjectNode operation) {
        addQueryParameter(operation, "model", "string", "Gateway route hint for unknown remote OpenAI Direct resp_ ids that do not have local lineage.");
        addOptionalHeaderParameter(operation, "X-AI-Gateway-OpenAI-Model", "Gateway route hint header for unknown remote OpenAI Direct resp_ ids that do not have local lineage.");
    }

    private void addQueryParameter(ObjectNode operation, String name, String type, String description) {
        ObjectNode parameter = parameters(operation).addObject();
        parameter.put("name", name);
        parameter.put("in", "query");
        parameter.put("required", false);
        parameter.put("description", description);
        parameter.putObject("schema").put("type", type);
    }

    private void addHeaderParameter(ObjectNode operation, String name, String description) {
        ObjectNode parameter = parameters(operation).addObject();
        parameter.put("name", name);
        parameter.put("in", "header");
        parameter.put("required", true);
        parameter.put("description", description);
        parameter.putObject("schema").put("type", "string");
    }

    private void addOptionalHeaderParameter(ObjectNode operation, String name, String description) {
        ObjectNode parameter = parameters(operation).addObject();
        parameter.put("name", name);
        parameter.put("in", "header");
        parameter.put("required", false);
        parameter.put("description", description);
        parameter.putObject("schema").put("type", "string");
    }

    private ArrayNode parameters(ObjectNode operation) {
        JsonNode existing = operation.path("parameters");
        if (existing.isArray()) {
            return (ArrayNode) existing;
        }
        return operation.putArray("parameters");
    }

    private void addProperty(ObjectNode properties, String name, String type, String description) {
        ObjectNode property = properties.putObject(name);
        property.put("type", type);
        property.put("description", description);
    }

    private String operationId(String method, String path) {
        return (method + "_" + path)
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private List<PublicDocsErrorCodeResponse> errorCodes(String locale) {
        boolean zh = "zh-CN".equals(locale);
        return List.of(
                new PublicDocsErrorCodeResponse(
                        "invalid_api_key",
                        401,
                        zh ? "Distributed Key 无效、过期或未启用。" : "The Distributed Key is invalid, expired or disabled.",
                        zh ? "检查完整 secret、key 状态和过期时间。" : "Check the full secret, key status and expiration."
                ),
                new PublicDocsErrorCodeResponse(
                        "rate_limit_exceeded",
                        429,
                        zh ? "请求触发 key 或 route policy 限流。" : "The request hit key or route policy rate limits.",
                        zh ? "检查 rpm/tpm/concurrency 与 runtime rate state。" : "Check rpm/tpm/concurrency and runtime rate state."
                ),
                new PublicDocsErrorCodeResponse(
                        "no_route_available",
                        503,
                        zh ? "没有可用 provider、site、credential 或模型候选。" : "No provider, site, credential or model candidate is available.",
                        zh ? "检查 provider site、credential health、模型别名和能力矩阵。" : "Check provider sites, credential health, model aliases and capability matrix."
                ),
                new PublicDocsErrorCodeResponse(
                        "insufficient_balance",
                        402,
                        zh ? "用户余额或订阅额度不足。" : "The user balance or subscription quota is insufficient.",
                        zh ? "检查账务 rollup、订单状态和余额流水。" : "Check billing rollup, payment order status and balance ledger."
                )
        );
    }

    private String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return "zh-CN";
        }
        String normalized = locale.trim().replace('_', '-');
        if ("en".equalsIgnoreCase(normalized) || "en-US".equalsIgnoreCase(normalized)) {
            return "en-US";
        }
        if ("zh".equalsIgnoreCase(normalized) || normalized.toLowerCase(Locale.ROOT).startsWith("zh-")) {
            return "zh-CN";
        }
        return "zh-CN";
    }
}
