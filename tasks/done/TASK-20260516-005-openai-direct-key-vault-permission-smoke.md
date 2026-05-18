# TASK-20260516-005 OpenAI Direct Key Vault 权限探测与 Secret 引用 Smoke

状态：Done  
优先级：Critical  
类型：子任务切片  
父任务：[TASK-20260514-031](../backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)  
上游来源：[TASK-20260515-016](../done/TASK-20260515-016-openai-codex-real-smoke-classification-budget-guard.md)、[REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)

## 背景

当前真实 smoke 已有 Codex App API responses 专项分类与预算阻断基线，但 OpenAI Direct API key 仍缺少一个可复用的后台入口：使用已入库 `UpstreamCredential` 作为 key vault 引用，先做低成本权限探测，再输出统一 `PASS/FAIL/SKIPPED/UNSUPPORTED/NO_PERMISSION/BUDGET_BLOCKED` 分类。

直接把真实 key 放进脚本或测试环境变量会造成长期维护和泄漏风险；本切片需要复用既有 `UpstreamCredential` 加密存储、指纹、active/cooldown 状态与 admin credential API，在不消费生成额度的前提下，为后续 Chat、Responses、Files、Batches、Vector Stores、Realtime client secret smoke runner 建立 OpenAI Direct 认证前置。

## 目标

- 在 admin credential 面新增 OpenAI Direct smoke 入口，按 `credentialId` 引用已加密保存的真实 key。
- dry-run 默认只返回脱敏 request preview、secret fingerprint、route/credential eligibility，不解密或发起真实请求。
- live permission probe 使用低成本 `GET /v1/models`，校验 key 可用性、组织/项目 header 下发和基础权限。
- 输出标准分类：`PASS`、`FAIL`、`SKIPPED`、`UNSUPPORTED`、`NO_PERMISSION`、`BUDGET_BLOCKED`。
- 返回与持久化结果不得包含明文 key、Authorization header、原始错误中的 key。
- 补齐文档、父任务和报告回写。

## 非目标

- 不在本切片执行 Chat/Responses 生成请求，不消费模型 tokens。
- 不实现 record/replay fixture。
- 不实现 Files/Batches/Vector Stores/Realtime client secret 具体 runner。
- 不新增新的 secret 表；先复用 `upstream_credential` 作为 OpenAI Direct key vault。

## 输入

- OpenAI Models API 文档：`https://developers.openai.com/api/docs/models`
- `CredentialAdminController`
- `CredentialAdminService`
- `UpstreamCredentialRepository`
- `CredentialCryptoService`
- `UpstreamCredentialEntity`

## 输出

- OpenAI Direct credential smoke request/response DTO。
- OpenAI Direct permission probe HTTP client。
- `POST /admin/credentials/{id}/openai-direct/smoke` 后台入口。
- service/controller/http client 回归测试。
- `docs/testing-smoke-harness.md` 与 `TASK-20260514-031` 回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/admin/api/CredentialAdminController.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/OpenAiDirectSmokeRequest.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/OpenAiDirectSmokeResponse.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminService.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/OpenAiDirectSmokeHttpClient.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/api/CredentialAdminControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminServiceTests.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/OpenAiDirectSmokeHttpClientTests.java`
- `docs/testing-smoke-harness.md`
- `docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md`
- `tasks/backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md`

## 依赖

- 既有 `UpstreamCredential` 加密存储、指纹查重和 active/cooldown 状态。
- 既有 OpenAI/Codex smoke 分类语义。

## 风险

- 错误消息可能回显 token 或 Authorization；必须统一脱敏。
- `GET /v1/models` 只能证明 key 基础认证和模型列表权限，不能证明所有资源族可用。
- OpenAI organization/project 权限可能不足，需归类为 `NO_PERMISSION`，而不是误判为功能失败。
- 429/rate limit 应归类为 `BUDGET_BLOCKED`，避免自动重试扩大影响。

## 验收标准

- dry-run 不调用 `CredentialCryptoService.decrypt`，响应只含 `Bearer ***` 和 fingerprint。
- live probe 只对 `OPENAI_DIRECT` 且 active、未 cooldown、未 deleted 的 credential 执行 `GET /v1/models`。
- `OpenAI-Organization`、`OpenAI-Project` header 只在请求显式传入时下发，preview 中脱敏。
- 401/403 归类为 `NO_PERMISSION`，429 或 rate-limit/quota 类失败归类为 `BUDGET_BLOCKED`。
- live probe 响应和 `lastErrorMessage` 不包含明文 secret。
- Targeted tests 和 scoped `diff --check` 通过。

## 测试边界

- 使用本地 HTTP server 测试 success、permission denied、rate limited 与脱敏。
- 使用 mock repository/crypto 验证 dry-run 不解密、live 解密一次、非 OpenAI Direct 拒绝。
- 使用 WebFlux controller test 验证 endpoint wiring。
- 不使用真实 OpenAI key；真实 smoke 后续手工在本地/受控 CI 打开。

## 当前状态

- 已完成任务拆分与边界设计。
- 已完成 DTO、service、HTTP client、测试与文档回写，并完成任务闭环。

## 实现结果

- 新增 `POST /admin/credentials/{id}/openai-direct/smoke`，按 `credentialId` 引用已入库 `OPENAI_DIRECT` credential，不要求脚本或测试环境携带明文 key。
- 新增 `OpenAiDirectSmokeRequest` / `OpenAiDirectSmokeResponse`，输出 method、path、baseUrl、providerType、fingerprint、classification、skippedReason、HTTP status、request id、modelsCount、sampleModels 与脱敏 request preview。
- 新增 `OpenAiDirectSmokeHttpClient`，live probe 只执行 `GET /v1/models`，支持可选 `OpenAI-Organization` 与 `OpenAI-Project` header，下发真实值但 preview 只展示 `***`。
- `CredentialAdminService.openAiDirectSmoke` 增加 credential eligibility 判断：非 `OPENAI_DIRECT` 归类为 `UNSUPPORTED`，inactive 归类为 `SKIPPED`，cooldown 归类为 `BUDGET_BLOCKED`；dry-run 不解密、不访问上游、不保存状态。
- live probe 401/403 或 auth/permission 类错误归类为 `NO_PERMISSION`，429/rate/quota/limit 类错误归类为 `BUDGET_BLOCKED`，成功归类为 `PASS`。
- live probe 成功会清理 credential error 并记录 `lastUsedAt`；失败只写入脱敏后的 `lastErrorCode/lastErrorMessage/lastErrorAt`。
- `docs/testing-smoke-harness.md` 已补充 OpenAI Direct Credential Permission Smoke 的手工执行方式和敏感信息约束。

## 验证结果

已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.OpenAiDirectSmokeHttpClientTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests"
```

覆盖点：

- dry-run preview 脱敏，且不调用 `CredentialCryptoService.decrypt`。
- live `GET /v1/models` 下发 Bearer、organization、project header，并解析模型样本。
- 429 rate limit 归类为 `BUDGET_BLOCKED`，错误摘要不含明文 key。
- 非 `OPENAI_DIRECT` credential 不解密、不访问上游，归类为 `UNSUPPORTED`。

## 遗留与后续

- 本切片只证明 OpenAI Direct key 的基础认证、模型列表权限与 secret 引用，不证明 Chat/Responses/Files/Batches/Vector Stores/Realtime client secret 全部可用。
- record/replay fixture 与 certification report 仍归属 `TASK-20260514-031` 后续切片。
