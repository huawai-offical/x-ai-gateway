# x-ai-gateway：X-155 ~ X-161 本地回写记录（2026-04-12）

## 说明
- 本文档用于暂存本轮原计划回写到 Notion 的实现结果、验证情况、遗留问题与后续建议。
- 由于 Notion MCP 在回写时返回 `Auth required`，本次先写入本地 `docs/` 记录页，待认证恢复后再同步到正式 Notion 页面。
- 目标 Notion 页面：
  - 主线页：<https://www.notion.so/33f79afa4790816e998ddccc9da7cd7e>
  - 实施计划页：<https://www.notion.so/34079afa479081279aaee76f71321275>

## 本轮实现范围
- 本轮实际重点推进了 `X-155`、`X-158`、`X-159`，并顺带为 `X-156 / X-157 / X-160 / X-161` 先补齐底座。
- 实现原则仍保持不变：
  - 优先收口统一计划语言与 capability 真相源。
  - 再把资源执行与异步对象编排挂到同一套语义上。
  - 暂不为旧路径保留长期双轨兼容。

## 已完成实现
### 1. 统一 Translation IR / Execution Plan 语言
- 扩展 `TranslationExecutionPlan`，新增：
  - `resourceType`
  - `operation`
  - `upstreamObjectMode`
- 扩展 `InteropPlanResponse`，让 `/api/v1/interop/plan` 对外暴露与 `TranslationExecutionPlan` 对齐的资源语义字段。
- `TranslationExplainService` 已切到新结构，`/admin/translation/explain` 与 `interop plan` 共用同一计划语言。

### 2. capability 真相源收口
- 重写 `SiteCapabilityTruthService`：
  - 不再只是按 `siteKind` 做简单启发式判断。
  - 开始联合使用 `site profile`、`site capability snapshot`、模型能力与已落地执行能力来返回 capability level。
  - 统一负责构建 `TranslationExecutionPlan`，避免 `GatewayInteropPlanService` 与真相源重复造一套执行判定。
- `GatewayInteropPlanService` 已删除内部旧的 fallback capability 判定逻辑，改为统一依赖 `SiteCapabilityTruthService`。

### 3. 模型目录与后台矩阵口径收口
- `ModelCatalogQueryService` 改为通过统一真相源推导 `/v1/models` 中的：
  - `capabilityLevel`
  - `supportsChat`
  - `supportsEmbeddings`
- `ProviderSiteAdminService` 的 capability matrix 也改为通过统一真相源推导真实支持情况，而不再直接信任 snapshot 布尔字段。

### 4. non-chat 执行链路底座补齐
- `GatewayRequestFeatureService` 新增：
  - `/v1/files` -> `FILE_OBJECT`
  - `/v1/uploads*`
  - `/v1/batches*`
  - `/v1/fine_tuning/jobs*`
  - `/v1/realtime/client_secrets*`
- `GatewayOpenAiPassthroughService` 改为按站点 `authStrategy / pathStrategy` 感知执行：
  - 不再只靠 OpenAI/OpenAI-compatible family 的硬编码。
  - 已补 Azure embeddings 路径处理。

### 5. files 双层编排
- 重写 `GatewayFileService`：
  - `createFile` 现在会先本地落盘，再尝试同步创建 upstream file object。
  - 成功后写入 `gateway_file_binding`，形成“本地文件对象 + upstream file object”的双层编排。
  - `deleteFile` 会优先尝试删除 upstream file object，再执行本地删除。
- 当前 files 编排路径默认要求 DistributedKey 绑定的上游里存在支持 `FILE_OBJECT` 的站点。

### 6. uploads / batches / tuning / realtime 双层编排
- 重写 `GatewayAsyncResourceService`：
  - `uploads / batches / tuning / realtime` 不再只是本地状态机。
  - 现在会为每个网关 resource key 记录 upstream object id / upstream status / sync 时间。
  - 对外继续返回网关 resource key，内部通过 metadata 挂接 upstream object。
- 已补齐：
  - batch / tuning 对 gateway file id -> upstream file id 的引用改写
  - get/cancel/complete 等动作优先走 upstream object，再同步回本地状态

## 本轮影响到的主要代码
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/TranslationExecutionPlan.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/interop/InteropPlanResponse.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/SiteCapabilityTruthService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/GatewayInteropPlanService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/catalog/ModelCatalogQueryService.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/ProviderSiteAdminService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/GatewayOpenAiPassthroughService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/file/GatewayFileService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/resource/GatewayAsyncResourceService.java`

## 验证结果
### 后端
- `./gradlew test`：通过

### 前端
- `bun run test`：通过
- `bun run check`：通过
- `bun run build`：通过

## 已同步到 Linear 的状态
- `X-155`：已更新为 `In Progress`
- `X-158`：已更新为 `In Progress`
- `X-152`：已追加本轮实现进展评论

## 当前遗留问题
### 1. audio / images / moderations 仍未完全拆成 provider-specific executor
- 当前它们已经具备更强的站点感知执行底座。
- 但还没有完全拆成和 embeddings 同级的细粒度 provider-specific executors。
- 这部分仍属于下一轮 `X-157` 的主要工作。

### 2. 首批站点档案扩面还未真正铺开
- 本轮主要做的是底座与语义收口。
- `X-160` 中的首批站点扩面顺序尚未真正逐站实施：
  - `Azure OpenAI / OpenRouter / DeepSeek / Together / Fireworks`
  - 然后 `Grok / Mistral / Cohere / Vertex AI`

### 3. conformance harness 还未落地
- 本轮补的是测试底座和统一口径，不是完整 `X-161` 矩阵。
- 真实的跨协议 × provider × site conformance harness 仍需下一轮单独展开。

### 4. Notion MCP 当前仍不可回写
- 本轮回写 Notion 时返回 `Auth required`。
- 已先用本地文档兜底。

## 下一步建议
- 优先继续推进 `X-157`：
  - 把 `audio / images / moderations` 从站点感知 passthrough 继续拆成 provider-specific executor。
- 再推进 `X-160`：
  - 先铺 `Azure OpenAI / OpenRouter / DeepSeek / Together / Fireworks`。
- 最后推进 `X-161`：
  - 用统一真相源和双层对象编排结果搭建 conformance harness。

## 回写 Notion 时建议补充的段落
- 实现结果
- 验证结果
- 当前遗留问题
- 后续建议
- 本轮对应 Linear 进度变化

## 第二阶段推进补充（X-157 / X-156 / X-158）
### X-157：non-chat 资源执行层
- `GatewayResourceExecutionService` 已从“单类混合实现”收口为“选路 + 账号解析 + executor 分发”的编排门面。
- 已新增统一资源执行抽象 `GatewayResourceExecutor` 与执行上下文 `GatewayResourceExecutionContext`。
- 已落地 4 个 executor：
  - `EmbeddingsGatewayResourceExecutor`
  - `OpenAiAudioGatewayResourceExecutor`
  - `OpenAiImagesGatewayResourceExecutor`
  - `OpenAiModerationsGatewayResourceExecutor`
- 当前真实支持矩阵已经和真相源对齐：
  - `embeddings`：`OPENAI_DIRECT / OPENAI_COMPATIBLE / GEMINI_DIRECT` 可执行
  - `audio / images / moderations`：当前仅 `OPENAI_DIRECT` 可执行
  - 其余 provider/site 统一走 blocker，不再静默落到 passthrough

### X-156：Ollama 聊天闭环
- `ChatExecutionRequest` 已新增 `executionMetadata`，用于把 `reasoning` 相关请求语义带入执行层。
- `OpenAiChatCompletionsController` 和 `OpenAiResponsesController` 已把 reasoning 元数据传入 `GatewayChatExecutionService`。
- `GatewayChatExecutionService` 的 route body 构造已开始透传 `reasoning / reasoning_effort`，让 route-time capability 和 runtime blocker 使用同一信号。
- `OllamaGatewayChatRuntime` 已补齐以下阻断：
  - `tools`
  - `media input`
  - `reasoning`
- `Ollama` 流式语义已调整为：
  - 不再追加额外空 terminal stop chunk
  - `done=true` 的真实 chunk 直接作为 terminal chunk
  - terminal chunk 携带最终 `usage + doneReason`

### X-158：异步资源与文件编排
- `GatewayFileService` 已补服务层测试，覆盖：
  - create 时本地文件 + upstream binding 同步
  - delete 时 upstream delete 调用
- `GatewayAsyncResourceService` 已补服务层测试，覆盖：
  - batch 创建时 gateway file id -> upstream file id 改写
  - upstream object metadata 持久化

### 本轮新增测试
- `SiteCapabilityTruthServiceTests`
- `GatewayResourceExecutionServiceTests`
- `OllamaGatewayChatRuntimeTests`
- `GatewayFileServiceTests`
- `GatewayAsyncResourceServiceTests`

### 本轮验证结果
- `./gradlew test`：通过
- `bun run test`：通过
- `bun run check`：通过
- `bun run build`：通过
