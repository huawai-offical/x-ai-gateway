# TASK-20260516-004 OpenAI Stored Chat 数据库游标分页与过滤硬化

状态：Done  
优先级：Critical  
类型：子任务切片  
父任务：[TASK-20260514-017](../backlog/TASK-20260514-017-openai-chat-completions-full-parity.md)、[TASK-20260514-030](../backlog/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)  
上游来源：[TASK-20260515-004](../done/TASK-20260515-004-openai-stored-chat-lifecycle-foundation.md)、[TASK-20260515-010](../done/TASK-20260515-010-openai-list-pagination-envelope-cursor-baseline.md)

## 背景

Stored Chat Completions 已支持 list/get/update/delete/messages 和 OpenAI-compatible list envelope，但 `GET /v1/chat/completions` 当前先从 repository 拉取固定 scan window，再在内存中过滤 `object=chat.completion`、`model`、`metadata[key]` 与 `after`。数据量增长后，固定 scan window 可能导致匹配项落在窗口外而漏页，也会把非 Chat response 一并拉到内存里处理。

官方 Chat Completions list 仍要求 `after`、`limit`、`metadata[key]`、`model`、`order`；messages list 要求 `after`、`limit`、`order`。本切片需要在不引入数据库方言 JSON 查询的前提下，把 Chat list 的租户、类型前缀、model、cursor 和排序尽量下推到数据库，并保留 metadata 的精确 JSON 过滤。

## 目标

- 为 stored Chat list 增加 repository 级 `chatcmpl_` 前缀、`distributedKeyId`、`resourceType`、`requestModel`、`createdAt/id` 游标和排序查询。
- 移除固定 scan window 对结果完整性的影响；metadata 过滤保留精确 JSON 判断，但通过分页批次继续向后扫描，直到凑满 `limit + 1` 或数据库无更多候选。
- `after` cursor 必须仍按当前过滤集合判定；cursor 不存在、不是 stored Chat、model/metadata 不匹配时返回空 list，不误翻页。
- 增加实体索引，降低 stored Chat list 在大表里的扫描成本。
- 更新任务/报告，明确 metadata 过滤的精确性和数据库下推边界。

## 非目标

- 不引入 PostgreSQL JSONB/H2 JSON 函数或方言绑定查询。
- 不改变 messages list 的存储模型；messages 仍来自单个 stored Chat 的 request payload。
- 不实现 `before` cursor。
- 不做真实 OpenAI smoke；真实 smoke 继续归属 `TASK-20260514-031`。

## 输入

- OpenAI List Chat Completions API reference：`https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/list`
- OpenAI Get chat messages API reference：`https://developers.openai.com/api/reference/resources/chat/subresources/completions/subresources/messages/methods/list`
- `GatewayAsyncResourceService.listChatCompletions`
- `GatewayAsyncResourceRepository`
- `GatewayAsyncResourceEntity`
- `GatewayAsyncResourceStoredChatTests`

## 输出

- stored Chat list repository 查询方法和 service 游标扫描实现。
- `gateway_async_resource` stored Chat list 相关索引。
- repository/service 回归测试。
- 父任务、报告和任务索引回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/repository/GatewayAsyncResourceRepository.java`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/entity/GatewayAsyncResourceEntity.java`
- `src/main/resources/db/changelog/changes/db.changelog-0050-stored-chat-pagination-index.yaml`
- `src/main/resources/db/changelog/db.changelog-master.yaml`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/resource/GatewayAsyncResourceService.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/resource/GatewayAsyncResourceStoredChatTests.java`
- `docs/public-api-compatibility.md`
- `docs/migrations/MIG-20260516-stored-chat-pagination-index.md`
- `docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md`
- `tasks/backlog/TASK-20260514-017-openai-chat-completions-full-parity.md`
- `tasks/backlog/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md`

## 依赖

- `TASK-20260515-004` 的 stored Chat lifecycle。
- `TASK-20260515-010` 的 OpenAI list envelope、默认排序和参数校验。

## 风险

- `metadata[key]` 存在于 response payload JSON 中；如果强行用字符串或方言 JSON 函数查询，会牺牲可移植性或正确性。本切片采用数据库候选分页 + service 精确 JSON 过滤。
- `after` cursor 需要和当前过滤集合一致，否则可能跳过错误位置；实现必须先校验 cursor 是否也满足当前 model/metadata filter。
- 同一时间戳下只按 `createdAt` 排序可能不稳定；查询必须使用 `createdAt + id` 作为稳定游标。

## 验收标准

- stored Chat list 不再调用通用 `search(... PageRequest.of(0, scanSize))` 固定窗口。
- repository 级查询至少下推 `distributedKeyId`、`resourceType`、`resourceKey like chatcmpl_%`、`requestModel`、`createdAt/id` cursor 和 asc/desc 排序。
- metadata 过滤在候选批次上持续扫描，过滤稀疏时仍能返回窗口外的匹配项。
- `after` cursor 不存在或不匹配当前过滤条件时返回空 list envelope。
- Targeted tests 和 scoped `diff --check` 通过。

## 测试边界

- 使用 mock repository 验证 service 调用新的 stored Chat asc/desc repository 方法，不再调用旧 `search`。
- 构造稀疏 metadata 数据，验证第一批无匹配、第二批有匹配时仍能返回。
- 验证 cursor 不匹配 metadata filter 时返回空 list。
- 不跑真实数据库性能测试，不跑真实 OpenAI smoke。

## 当前状态

- 已完成现状审计和官方 list 参数复核。
- 已完成 repository/service/test/docs 实现，并完成任务闭环。

## 实现结果

- `GatewayAsyncResourceRepository` 新增 stored Chat 专用 asc/desc 游标查询，按 `distributedKeyId`、`resourceType`、`chatcmpl_` 前缀、`requestModel`、`createdAt/id` 和排序裁剪候选。
- `GatewayAsyncResourceService.listChatCompletions` 不再依赖固定 scan window，改为数据库候选分页 + service 精确 JSON metadata 过滤；当 metadata 稀疏时会继续跨批次扫描直到凑满 `limit + 1` 或无更多候选。
- `after` cursor 会先校验是否仍属于当前过滤集合；cursor 不存在、不是 stored Chat、model 或 metadata 不匹配时返回空 list envelope。
- `gateway_async_resource` 增加 `idx_gateway_async_resource_chat_list` 索引，并通过 Liquibase changeSet `0050-stored-chat-pagination-index` 管理。
- Public docs bundle、OpenAPI snapshot、兼容性文档、父任务和报告已回写数据库游标分页边界。

## 验证结果

- 已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceStoredChatTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"
```

- 曾额外尝试纳入 `XAiGatewayApplicationTests` 做更宽 full context 验证，但该测试被既有 `GlobalApiExceptionHandlerProbeController#anthropicMessages` 与 `AnthropicMessagesController#createMessage` 的 `/v1/messages` ambiguous mapping 阻断，和本任务 stored Chat 改动无直接关系。

## 遗留与后续

- metadata 过滤刻意保持 service 级 JSON 精确判断，未引入数据库方言 JSON/JSONB 查询；如未来确定生产数据库只支持 PostgreSQL，可另立任务评估 JSONB expression index。
- 真实 OpenAI smoke 证据继续归属 `TASK-20260514-031`，本切片不消费真实额度。
