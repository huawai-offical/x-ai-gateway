# TASK-20260516-006 OpenAI Direct 资源族 Smoke Runner 分类骨架

状态：Done  
优先级：Critical  
类型：子任务切片  
父任务：[TASK-20260514-031](../backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)  
上游来源：[TASK-20260516-005](../done/TASK-20260516-005-openai-direct-key-vault-permission-smoke.md)

## 背景

`TASK-20260516-005` 已完成 OpenAI Direct credential key vault 引用、dry-run preview、低成本 `GET /v1/models` 权限探测和标准分类。`TASK-20260514-031` 仍要求真实 smoke 至少覆盖 Chat、Responses、Files、Batches、Vector Stores、Realtime client secret 的资源族分类。

这些资源族成本和副作用不同：Files、Batches、Vector Stores 可用只读 list probe 做低风险权限验证；Chat/Responses 会消耗 tokens；Realtime client secret 是写操作，会生成临时凭证。为避免误触高成本或写操作，本切片先建立资源族 runner 与统一分类报告，live 模式只执行低风险只读 list probe，高成本/写操作资源族默认输出 `BUDGET_BLOCKED` 并记录明确 skipped reason。

## 目标

- 新增后台入口，按 `credentialId` 生成 OpenAI Direct 资源族 smoke 报告。
- 复用 `UpstreamCredential` 加密存储、指纹和 eligibility 判断，不传递明文 key。
- 覆盖 Chat、Responses、Files、Batches、Vector Stores、Realtime client secret 六类资源族的分类项。
- live 模式只对 Files、Batches、Vector Stores 执行只读 list probe；Chat/Responses/Realtime client secret 只给出预算/写操作保护分类。
- 所有 request preview、错误摘要和持久化字段不得包含明文 key、Authorization header、organization/project 原值。
- 回写任务、文档和父任务，明确本切片不是完整真实生成 smoke。

## 非目标

- 不执行 Chat/Responses 生成请求。
- 不创建 Realtime client secret。
- 不做 record/replay fixture。
- 不实现 UI 展示；本切片先完成后台 API 与测试。

## 输入

- OpenAI Models/Realtime 官方文档与当前 API Reference 资源族导航。
- `TASK-20260516-005` 的 OpenAI Direct credential smoke。
- `CredentialAdminController`
- `CredentialAdminService`
- `OpenAiDirectSmokeHttpClient`

## 输出

- `POST /admin/credentials/{id}/openai-direct/resource-smoke` 后台入口。
- 资源族 smoke request/response DTO。
- OpenAI Direct 资源族只读 probe HTTP client。
- service/controller/http client 回归测试。
- `docs/testing-smoke-harness.md`、`TASK-20260514-031` 和报告回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/admin/api/CredentialAdminController.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/OpenAiDirectResourceSmokeRequest.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/OpenAiDirectResourceSmokeResponse.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/OpenAiDirectResourceSmokeItemResponse.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminService.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/OpenAiDirectResourceSmokeHttpClient.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/api/CredentialAdminControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminServiceTests.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/OpenAiDirectResourceSmokeHttpClientTests.java`
- `docs/testing-smoke-harness.md`
- `docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md`
- `tasks/backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md`

## 依赖

- `TASK-20260516-005` 的 credential 引用、权限探测和标准分类。
- 既有 OpenAI/Codex smoke 分类语义。

## 风险

- 如果把 Chat/Responses 或 Realtime client secret 混入默认 live probe，会造成真实额度消耗或写操作副作用。
- 只读 list probe 只能证明 list 权限，不证明 create/cancel/delete 等完整生命周期。
- 上游 404/403/429 需要区分 `UNSUPPORTED`、`NO_PERMISSION`、`BUDGET_BLOCKED`，不能一律记为 FAIL。

## 验收标准

- dry-run 返回六类资源族 item，全部不解密、不访问上游。
- live 模式对 Files/Batches/Vector Stores 分别请求 `/v1/files?limit=1`、`/v1/batches?limit=1`、`/v1/vector_stores?limit=1`。
- live 模式下 Chat/Responses 输出 `BUDGET_BLOCKED` + `BILLABLE_PROBE_BLOCKED`，Realtime client secret 输出 `BUDGET_BLOCKED` + `WRITE_PROBE_BLOCKED`。
- 401/403 归类为 `NO_PERMISSION`，429 或 rate-limit/quota 类失败归类为 `BUDGET_BLOCKED`，404 归类为 `UNSUPPORTED`。
- 响应 summary 统计 PASS/FAIL/SKIPPED/UNSUPPORTED/NO_PERMISSION/BUDGET_BLOCKED 数量。
- Targeted tests 和 scoped `diff --check` 通过。

## 测试边界

- 使用本地 HTTP server 验证只读 list probes 的 URL、header、classification 和脱敏。
- 使用 mock service 验证 controller endpoint wiring。
- 使用 mock repository/crypto 验证 dry-run 不解密、live 只解密一次、非 OpenAI Direct 不访问上游。
- 不使用真实 OpenAI key。

## 当前状态

- 已完成任务拆分与边界设计。
- 已完成 DTO、HTTP client、service/controller、测试与文档回写，并完成任务闭环。

## 实现结果

- 新增 `POST /admin/credentials/{id}/openai-direct/resource-smoke`，按 `credentialId` 引用已入库 `OPENAI_DIRECT` credential。
- 新增 `OpenAiDirectResourceSmokeRequest`、`OpenAiDirectResourceSmokeResponse`、`OpenAiDirectResourceSmokeItemResponse`，输出资源族 item、标准分类、summary、request preview、HTTP status、request id、证据摘要和脱敏错误。
- 新增 `OpenAiDirectResourceSmokeHttpClient`，统一生成六类资源族 preview，并只对 Files/Batches/Vector Stores 执行只读 list probe：
  - `GET /v1/files?limit=1`
  - `GET /v1/batches?limit=1`
  - `GET /v1/vector_stores?limit=1`
- Chat Completions 与 Responses 默认输出 `BUDGET_BLOCKED / BILLABLE_PROBE_BLOCKED`，Realtime client secret 默认输出 `BUDGET_BLOCKED / WRITE_PROBE_BLOCKED`。
- `CredentialAdminService.openAiDirectResourceSmoke` 复用 credential eligibility：非 `OPENAI_DIRECT` 为 `UNSUPPORTED`，inactive 为 `SKIPPED`，cooldown 为 `BUDGET_BLOCKED`；dry-run 不解密、不访问上游、不保存状态。
- 只读 live probe 的 401/403 归类为 `NO_PERMISSION`，429 或 rate/quota/limit 归类为 `BUDGET_BLOCKED`，404 归类为 `UNSUPPORTED`。
- `docs/testing-smoke-harness.md`、父任务和全量覆盖报告已回写资源族 smoke 的默认阻断边界。

## 验证结果

已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.OpenAiDirectResourceSmokeHttpClientTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests"
```

覆盖点：

- dry-run 返回六类资源族 item，且不解密、不访问上游。
- Files list probe 校验 URL、Bearer header、request id 与 evidence。
- Vector Stores 404 归类为 `UNSUPPORTED` 并脱敏 secret。
- Chat/Responses/Realtime client secret 默认阻断，不发 HTTP 请求。
- service live 模式只解密一次，summary 正确统计 PASS、NO_PERMISSION、BUDGET_BLOCKED。
- controller endpoint wiring 已覆盖。

## 遗留与后续

- 本切片没有执行 Chat/Responses 生成请求，也没有创建 Realtime client secret；这类 billable/write probe 必须单独显式批准并设置预算。
- record/replay fixture 与 certification report 仍归属 `TASK-20260514-031` 后续切片。
