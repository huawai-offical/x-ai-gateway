# TASK-20260515-016 OpenAI/Codex Real Smoke 分类与预算阻断基线

状态：Done  
优先级：Critical  
类型：子任务切片  
父任务：[TASK-20260514-031](../backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)  
上游来源：[TASK-20260514-016](../backlog/TASK-20260514-016-openai-full-api-coverage-parent.md)、[REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

## 背景

`TASK-20260514-031` 要求真实 smoke 支持 `PASS/FAIL/SKIPPED/UNSUPPORTED/NO_PERMISSION/BUDGET_BLOCKED` 分类，并且真实请求必须具备认证、预算和脱敏保护。当前 Codex App API responses smoke 已能 dry-run、真实请求和 usage keepalive，但结果仍主要依赖 `LIVE_SMOKE_OK/LIVE_SMOKE_FAILED` 等状态，缺少统一分类，也没有在 usage probe 命中额度阻断时保证不继续发起 responses 请求。

## 目标

- 为 Codex App API responses smoke 输出标准 `classification`：`PASS`、`FAIL`、`SKIPPED`、`UNSUPPORTED`、`NO_PERMISSION`、`BUDGET_BLOCKED`。
- 为 dry-run、路由阻断、usage 限额、权限失败输出 `skippedReason`，避免把可跳过/不可执行状态误判为功能失败。
- 在 Codex usage probe 判断 `allowed=false`、`limit_reached=true`、HTTP 429、HTTP 401/403 时阻断后续 `/backend-api/codex/responses` POST。
- 保持凭证、Authorization、access token 和真实响应错误脱敏，不把真实密钥写入响应、日志或 `lastRefreshResultJson`。
- 在账号池详情页展示标准分类和跳过原因，让管理员区分 dry-run、预算阻断、权限不足和真实失败。

## 非目标

- 不在本切片补齐 Files、Batches、Vector Stores、Realtime client secret 等全部资源族 smoke。
- 不把真实 OpenAI/Codex key 写入仓库、fixtures 或默认配置。
- 不改变现有 dry-run 默认为安全预检的行为。

## 输入

- `OfficialAccountAdminService.codexResponsesSmoke` 当前 dry-run/live smoke 实现。
- `CodexResponsesSmokeHttpClient` 当前 Codex App API usage keepalive 与 responses POST 实现。
- 账号池详情页对 smoke 结果的展示。

## 输出

- 后端 smoke 响应新增标准分类与跳过原因。
- Codex usage probe 预算/权限阻断逻辑与单元测试。
- 前端 smoke 结果显示分类、状态和阻断原因。
- 本任务实现结果、验证命令和遗留边界回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/admin/api/OfficialCodexResponsesSmokeResponse.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/OfficialAccountAdminService.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/CodexResponsesSmokeHttpClient.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/CodexLongTermTestImportRunner.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/*Smoke*Tests.java`
- `web/src/features/accounts/account-pool-detail-page.tsx`

## 依赖

- `TASK-20260507-016` 已落地的 Codex 真实 `auth.json` 长期测试账号入库与详测。
- `TASK-20260514-031` 的真实 smoke 总体验收口径。

## 风险

- 如果 usage probe 失败原因不明确，贸然阻断可能影响排障；本切片只对明确权限/限额类失败阻断。
- 新增字段需要保持向后兼容，避免破坏已有前端和 CLI runner。
- 真实 smoke 可能仍受上游 ChatGPT/Codex App API 行为变化影响，因此测试以本地 HTTP server 模拟权限、限额和模型不支持分类。

## 验收标准

- dry-run 返回 `classification=SKIPPED`、`skippedReason=DRY_RUN`。
- 路由配额耗尽返回 `classification=BUDGET_BLOCKED`，且不解密、不发起真实请求。
- usage probe 返回 `allowed=false`、`limit_reached=true` 或 HTTP 429 时，不调用 responses POST，并返回 `classification=BUDGET_BLOCKED`。
- usage probe 返回 HTTP 401/403 时，不调用 responses POST，并返回 `classification=NO_PERMISSION`。
- 模型不支持继续归类为 `UNSUPPORTED`，普通 5xx/网络错误归类为 `FAIL`。
- 前端账号池详情页能显示标准分类和跳过原因。

## 测试边界

- 单元测试覆盖 dry-run、路由预算阻断、usage 预算阻断、usage 权限阻断、模型不支持分类。
- 不在默认测试中访问真实远端；真实 Codex smoke 仍由显式 live 开关或手工操作触发。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

## 实现结果

- `OfficialCodexResponsesSmokeResponse` 新增 `classification` 与 `skippedReason`，后端和前端均展示标准分类。
- `OfficialAccountAdminService.codexResponsesSmoke` 统一输出 `PASS/FAIL/SKIPPED/UNSUPPORTED/NO_PERMISSION/BUDGET_BLOCKED`，并将分类和跳过原因写入 `lastRefreshResultJson`。
- 路由配额耗尽时返回 `BUDGET_BLOCKED`，并且只对密文做 fingerprint，不解密 access token，不发起真实请求。
- `CodexResponsesSmokeHttpClient` 在 Codex usage probe 命中 `allowed=false`、`limit_reached=true`、HTTP 429、HTTP 401/403 或权限类 failure type 时，阻断后续 `/backend-api/codex/responses` POST。
- 账号池详情页展示标准分类、旧状态、路由状态与跳过原因，管理员可以直接区分 dry-run、预算阻断、权限不足、模型不支持和真实失败。
- `CodexLongTermTestImportRunner` 日志补充 `classification` 与 `skippedReason`，长期测试输出更容易审计。

## 验证结果

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.CodexResponsesSmokeHttpClientTests" --tests "com.prodigalgal.xaigateway.admin.application.OfficialAccountAdminServiceTests"
```

结果：通过。

```powershell
bun run typecheck
```

结果：通过。

```powershell
bun run lint
```

结果：通过。

## 遗留边界

- 本切片只闭环 Codex App API responses smoke 的分类和预算/权限阻断基线。
- `TASK-20260514-031` 仍需继续拆分 OpenAI API key vault、Chat/Responses/Files/Batches/Vector Stores/Realtime client secret 的资源族 smoke runner、record/replay fixture 和 certification report。
- 默认测试仍不访问真实远端；真实 Codex smoke 继续由显式 live 开关或管理端手工操作触发。
