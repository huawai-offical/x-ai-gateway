# TASK-20260516-009 OpenAI Direct 显式 Billable/Write Smoke Probe

状态：Done  
优先级：Critical  
类型：子任务切片  
父任务：[TASK-20260514-031](../backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)  
关联任务：[TASK-20260516-006](../done/TASK-20260516-006-openai-direct-resource-family-smoke-runner.md)、[TASK-20260516-007](../done/TASK-20260516-007-openai-direct-smoke-certification-fixture.md)

## 背景

`TASK-20260516-006` 已覆盖 Chat、Responses、Files、Batches、Vector Stores、Realtime client secret 六类 smoke 分类，但 live 默认只执行只读 list probe。Chat/Responses 属于 billable generation probe，Realtime client secret 属于写操作 probe，因此当前都被 `BUDGET_BLOCKED` 保护阻断。

为了让真实 OpenAI Direct smoke 可以在受控手工/CI 环境中给出完整证据，需要新增显式开关：默认继续阻断，只有请求明确允许 billable 或 write probe 时才发起最小成本/最小写入调用。

## 目标

- 在 `OpenAiDirectResourceSmokeRequest` 增加显式允许字段，默认 `false`。
- Chat Completions 与 Responses live probe 仅在 `allowBillableProbes=true` 时执行。
- Realtime client secret live probe 仅在 `allowWriteProbes=true` 时执行。
- 执行的 billable probe 必须使用最小输出 token 与无存储/无工具/无音频的安全 payload。
- Realtime client secret probe 必须使用短 TTL、text-only session 与最小输出 token。
- request preview、fixture 和 metadata 不包含 secret、Authorization、organization/project 原值。

## 非目标

- 不默认开启任何 billable 或 write probe。
- 不在本切片执行真实 OpenAI 网络 smoke。
- 不新增 UI 开关。
- 不实现 Realtime WebSocket 真实模型输出。

## 输入

- `OpenAiDirectResourceSmokeRequest`
- `OpenAiDirectResourceSmokeHttpClient`
- `CredentialAdminService#openAiDirectResourceSmoke`
- 官方 Chat Completions、Responses、Realtime client secrets API 文档

## 输出

- 受控 billable/write probe 执行逻辑。
- 最小成本 request payload 与脱敏 request preview。
- http client/service 回归测试。
- smoke harness 文档、父任务和覆盖报告回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/admin/api/OpenAiDirectResourceSmokeRequest.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/OpenAiDirectResourceSmokeHttpClient.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminService.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/OpenAiDirectResourceSmokeHttpClientTests.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminServiceTests.java`
- `docs/testing-smoke-harness.md`
- `tasks/backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md`
- `docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md`

## 依赖

- `TASK-20260516-006` 的资源族 item/summary 基线。
- `TASK-20260516-007` 的 certification 和脱敏 fixture 基线。

## 风险

- 批量执行时如果默认开启 billable/write probe，会产生不可控成本或写操作。
- 模型名不可用、权限不足、额度不足都不能误判为 gateway 逻辑失败。
- 失败摘要和 preview 不能泄露真实 key 或 org/project。

## 验收标准

- 未设置 allow flag 时，Chat/Responses 仍返回 `BUDGET_BLOCKED / BILLABLE_PROBE_BLOCKED`，Realtime client secret 仍返回 `BUDGET_BLOCKED / WRITE_PROBE_BLOCKED`。
- 设置 `allowBillableProbes=true` 时，Chat/Responses 会 POST 到对应 OpenAI path，并使用最小 token payload。
- 设置 `allowWriteProbes=true` 时，Realtime client secret 会 POST 到 `/v1/realtime/client_secrets`，并使用短 TTL text-only session payload。
- service 层能把 allow flags 传递到 http client。
- Targeted tests 和 scoped `diff --check` 通过。

## 测试边界

- 使用本地 `HttpServer` 捕获 path、method、body、header，不使用真实 OpenAI key。
- 覆盖默认阻断、显式允许执行、失败分类与脱敏。
- 不验证真实模型是否可用或实际账单。

## 关联官方文档

- https://platform.openai.com/docs/api-reference/chat/create-chat-completion
- https://platform.openai.com/docs/api-reference/responses/create
- https://platform.openai.com/docs/api-reference/realtime-sessions/create-realtime-client-secret

## 当前状态

- 已完成 request flags、受控 probe、测试和文档回写。

## 实现结果

- `OpenAiDirectResourceSmokeRequest` 新增 `allowBillableProbes` 与 `allowWriteProbes`，并保留旧构造器兼容既有调用。
- `OpenAiDirectResourceSmokeHttpClient#executeProbe` 统一处理 GET/POST probe；未显式允许时，Chat/Responses/Realtime client secret 继续返回预算或写操作阻断。
- Chat Completions 显式 billable probe 使用 `gpt-4o-mini`、`max_completion_tokens=1`、`store=false`。
- Responses 显式 billable probe 使用 `gpt-4o-mini`、`max_output_tokens=1`、`store=false`。
- Realtime client secret 显式 write probe 使用 `gpt-realtime-mini`、`output_modalities=["text"]`、`max_output_tokens=1`、`expires_after.seconds=60`。
- service 层已把 allow flags 传递到 http client；request preview 保留 body 但不暴露 Authorization、organization/project 原值。

## 验证记录

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.OpenAiDirectResourceSmokeHttpClientTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.OpenAiDirectSmokeCertificationServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests"
```

结果：通过。

## 遗留与后续

- 本切片使用本地 `HttpServer` 验证 payload 和分类，不执行真实 OpenAI 网络 smoke。
- 真实线上执行仍需受控环境提供真实 OpenAI Direct key、预算和明确手工/CI 开关。
- 仓库内 record/replay fixture 文件固化仍需独立任务。
