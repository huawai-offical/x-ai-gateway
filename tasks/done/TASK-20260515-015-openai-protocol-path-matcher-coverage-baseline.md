# TASK-20260515-015 OpenAI Protocol Path Matcher 覆盖防遗漏基线

状态：Done  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260514-030](../backlog/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)  
上游来源：[TASK-20260514-016](../backlog/TASK-20260514-016-openai-full-api-coverage-parent.md)

## 背景

OpenAI-style error envelope、429 headers 与部分横切协议行为依赖 `GlobalApiExceptionHandler` 判断当前 path 是否属于 OpenAI protocol。当前判断逻辑是 handler 内部私有方法，缺少独立契约测试。随着 `/v1/audio`、`/v1/images`、`/v1/realtime`、`/v1/uploads`、`/v1/vector_stores` 等 endpoint 继续增加，如果 path 清单遗漏，新增入口会退回 gateway 自有错误体，破坏 OpenAI SDK 兼容。

## 目标

- 将 OpenAI protocol path 判断提取为可复用、可单测的 matcher。
- 覆盖当前已实现和规划中的 OpenAI endpoint root/path family。
- 明确排除 Anthropic-compatible `/v1/messages`、public docs、media provider 等非 OpenAI 协议路径。
- 保持 `GlobalApiExceptionHandler` 行为不变，只替换为 matcher 调用。

## 非目标

- 不新增 OpenAI endpoint。
- 不改变错误 envelope 字段结构。
- 不把所有 `/v1/*` 都视为 OpenAI path，避免误伤 Anthropic-compatible `/v1/messages`。

## 输入

- 当前 `protocol/ingress/openai` controller 路径。
- `TASK-20260514-030` 的 OpenAI 横切协议要求。
- `GlobalApiExceptionHandler` 的 OpenAI-style error/rate-limit header 行为。

## 输出

- `OpenAiProtocolPathMatcher`。
- matcher 单元测试覆盖 positive/negative path。
- `GlobalApiExceptionHandler` 改为调用 matcher。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/infra/config/web/GlobalApiExceptionHandler.java`
- `src/main/java/com/prodigalgal/xaigateway/infra/config/web/OpenAiProtocolPathMatcher.java`
- `src/test/java/com/prodigalgal/xaigateway/infra/config/web/OpenAiProtocolPathMatcherTests.java`
- `src/test/java/com/prodigalgal/xaigateway/infra/config/web/GlobalApiExceptionHandlerTests.java`
- `tasks/index.md`

## 依赖

- 不依赖真实 OpenAI key。
- JUnit 参数化测试可用于路径矩阵。

## 风险

- 如果 matcher 过宽，会把非 OpenAI `/v1` 路径误套 OpenAI error envelope。
- 如果 matcher 过窄，新增 OpenAI endpoint 会漏掉横切错误契约。

## 验收标准

- 当前 Chat/Responses/Audio/Images/Embeddings/Files/Uploads/Batches/Models/Fine-tuning/Realtime 等 OpenAI path 均返回 true。
- `/v1/messages`、`/public/docs/openapi.json`、`/api/v1/media/provider-matrix` 返回 false。
- `GlobalApiExceptionHandlerTests` 仍覆盖 OpenAI 与 Anthropic-compatible envelope 分流。

## 测试边界

- matcher 单元测试。
- `GlobalApiExceptionHandlerTests`。

## 关联文档

- [TASK-20260514-030](../backlog/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

## 实现结果

- 新增 `OpenAiProtocolPathMatcher`，把 OpenAI protocol path 判断从 `GlobalApiExceptionHandler` 私有方法提取为可复用、可单测组件。
- `GlobalApiExceptionHandler` 的 OpenAI-style error envelope 与 429 headers 判断改为调用 matcher。
- 增加正向 path matrix，覆盖 Chat/Responses/Conversations/Webhooks/Completions/Embeddings/Audio/Images/Videos/Moderations/Files/Uploads/Batches/Models/Fine-tuning/Vector Stores/Containers/Evals/Skills/Realtime/Assistants/Threads。
- 增加反向 path matrix，明确 `/v1/messages`、public docs、media provider、Google/Anthropic native 等路径不套 OpenAI error envelope。
- 修正 `/v1/videos` 根路径原先未命中的问题，避免未来 Videos API 根路径出现非 OpenAI-style 错误体。

## 验证结果

已通过：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.infra.config.web.OpenAiProtocolPathMatcherTests" --tests "com.prodigalgal.xaigateway.infra.config.web.GlobalApiExceptionHandlerTests"
```

覆盖项：

- matcher positive/negative path matrix。
- OpenAI path 与 `/v1/messages` 的 error envelope 分流。
- OpenAI path 的 429 rate-limit headers。

## 遗留问题

- 后续新增 OpenAI endpoint 时必须同步更新 matcher matrix；如果 endpoint 在 controller 已落地但 matcher 未补，测试应继续作为防遗漏入口。
