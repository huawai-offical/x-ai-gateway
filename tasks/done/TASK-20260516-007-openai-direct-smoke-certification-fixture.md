# TASK-20260516-007 OpenAI Direct Smoke Certification 与脱敏 Fixture 基线

状态：Done  
优先级：Critical  
类型：子任务切片  
父任务：[TASK-20260514-031](../backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)  
上游来源：[TASK-20260516-006](../done/TASK-20260516-006-openai-direct-resource-family-smoke-runner.md)

## 背景

`TASK-20260516-006` 已能按资源族输出标准分类，但这些结果目前只是一次 API 响应。`TASK-20260514-031` 仍要求把真实 smoke 结果沉淀为可审计 certification report，并为后续 conformance/record-replay 提供脱敏 fixture。

本切片不新增真实调用类型，而是在 006 的资源族 smoke 上建立 certification 与 redacted fixture 基线：把每个资源族 item 的 method/path/status/classification/evidence/request preview 脱敏后封装成稳定记录，并在 live 模式下把最近一次 certification 摘要写入 credential metadata，便于后台和 CI 读取。

## 目标

- 新增后台 certification endpoint，执行或复用资源族 smoke 后生成 certification report。
- 为每个资源族 item 生成脱敏 fixture snapshot。
- certification status 能区分 `DRY_RUN`、`CERTIFIED`、`PARTIAL_CERTIFIED`、`NO_PERMISSION`、`BUDGET_BLOCKED`、`UNSUPPORTED`、`FAILED`。
- live certification 将安全摘要写入 `credentialMetadataJson.openai_direct_smoke_certification`。
- 响应、fixture 和 metadata 不包含明文 secret、Authorization、organization/project 原值。

## 非目标

- 不写入仓库内 fixture 文件。
- 不新增真实 Chat/Responses/Reatime 写操作 probe。
- 不实现 UI 展示。

## 输入

- `TASK-20260516-006` 的资源族 smoke response。
- `CredentialAdminService`
- `OpenAiDirectResourceSmokeItemResponse`
- `UpstreamCredentialEntity.credentialMetadataJson`

## 输出

- `POST /admin/credentials/{id}/openai-direct/resource-smoke/certification` 后台入口。
- Certification response 与 fixture DTO。
- Certification service。
- service/controller 回归测试。
- `docs/testing-smoke-harness.md`、`TASK-20260514-031` 与报告回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/admin/api/CredentialAdminController.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/OpenAiDirectSmokeCertificationFixture.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/OpenAiDirectSmokeCertificationResponse.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminService.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/OpenAiDirectSmokeCertificationService.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/api/CredentialAdminControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminServiceTests.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/OpenAiDirectSmokeCertificationServiceTests.java`
- `docs/testing-smoke-harness.md`
- `docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md`
- `tasks/backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md`

## 依赖

- `TASK-20260516-006` 已提供稳定资源族 item 与 summary。

## 风险

- 如果 certification 保存原始 response，可能把 header 或错误体中的 secret 带入 metadata。
- 如果 certification status 只看顶层 classification，可能忽略部分资源族被 budget guard 阻断。
- dry-run 不应写入 credential metadata，避免把预览误当成真实证据。

## 验收标准

- certification endpoint 返回 smoke response、summary、certificationStatus 和 fixtureSnapshots。
- fixture snapshot 只包含脱敏 request preview 与安全 evidence，不包含明文 secret、`Bearer sk-`、organization/project 原值。
- live certification 会写入 `credentialMetadataJson.openai_direct_smoke_certification`；dry-run 不写入。
- `PARTIAL_CERTIFIED` 能表达只读资源族通过但 billable/write probe 被阻断的状态。
- Targeted tests 和 scoped `diff --check` 通过。

## 测试边界

- certification service 单测覆盖 status 聚合和递归脱敏。
- service 单测覆盖 live metadata 写入、dry-run 不写入。
- controller 单测覆盖 endpoint wiring。
- 不使用真实 OpenAI key。

## 当前状态

- 已完成后台 certification endpoint：`POST /admin/credentials/{id}/openai-direct/resource-smoke/certification`。
- 已完成 certification DTO、fixture snapshot DTO 与 `OpenAiDirectSmokeCertificationService`。
- 已完成 live certification metadata 写入；dry-run 不写入 credential metadata。
- 已完成递归脱敏：Authorization、token、secret、api key、organization/project header，以及 `Bearer ...`、`sk-...`、`org-*`、`proj-*` 文本形态均会脱敏。
- 已完成 service/controller 回归测试。

## 实现结果

- `CredentialAdminController` 新增 certification 入口，并复用 `OpenAiDirectResourceSmokeRequest` 作为 dry-run/live 与资源族选择输入。
- `CredentialAdminService` 在 live certification 成功生成后，将安全摘要写入 `credentialMetadataJson.openai_direct_smoke_certification`。
- `OpenAiDirectSmokeCertificationService` 基于资源族 smoke summary 生成 `DRY_RUN`、`CERTIFIED`、`PARTIAL_CERTIFIED`、`NO_PERMISSION`、`BUDGET_BLOCKED`、`UNSUPPORTED`、`FAILED` 等顶层 certification status。
- fixture snapshot 保留资源族、method/path、classification、skippedReason、HTTP status、request id、duration、failure 摘要、安全 evidence 与脱敏 request preview，不持久化明文上游 secret 或 org/project 原值。

## 验证记录

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.OpenAiDirectSmokeCertificationServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.OpenAiDirectResourceSmokeHttpClientTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests"
```

结果：通过。

## 遗留与后续

- Chat/Responses 低成本 billable generation probe 与 Realtime client secret 写操作 probe 已由 [TASK-20260516-009](TASK-20260516-009-openai-direct-explicit-billable-write-smoke-probes.md) 增加显式 allow flag；默认仍保持阻断。
- 本切片只提供响应与 credential metadata 中的脱敏 fixture 基线；如后续需要仓库内 record/replay fixture 文件，需要另开独立任务定义保存位置、更新策略与脱敏审计。
