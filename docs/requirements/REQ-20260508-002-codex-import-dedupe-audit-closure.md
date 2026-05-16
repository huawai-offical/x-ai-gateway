# REQ-20260508-002 Codex 导入去重、可信前端与审计追踪闭环

状态：Done  
创建日期：2026-05-08  
关联任务：
- [TASK-20260508-004 Codex auth.json 导入去重与脱敏加固](../../tasks/done/TASK-20260508-004-codex-auth-json-import-dedupe-sanitization.md)
- [TASK-20260508-005 Codex 前端导入流官方化与结果反馈](../../tasks/done/TASK-20260508-005-codex-console-import-official-feedback.md)
- [TASK-20260508-006 Codex 批量恢复审计事件追踪](../../tasks/done/TASK-20260508-006-codex-batch-audit-event-tracing.md)

## 背景

上一批已经把 Codex 观测投影、Runtime 批量恢复 API 和前后端联调 Smoke 闭环。继续复核时发现三个更贴近日常真实运营的高优缺口：

1. `auth.json` 可能来自同一 Codex 账号的多次登录，token、account_id 或文件结构可能变化，导入入口必须以稳定身份去重。
2. 账号池详情页当前的通用 `auth.json` 导入路径更偏“原始账号导入”，不够贴近 Codex 官方账号的配额、Smoke、身份摘要和脱敏边界。
3. 批量恢复已经写入系统事件，但管理员从批量弹窗跳到系统事件追踪时缺少精准过滤，审计证据还不够顺手。

## 目标

- 后端导入 Codex `auth.json` 时优先使用 subject、email、account_id 等稳定身份生成 canonical identity，重复导入更新既有账号，不产生重复账号。
- 所有 `auth.json` 导入路径都不能把 access token、refresh token、authorization、cookie、secret 等敏感值写入 metadata、header snapshot 或 last refresh result。
- Codex 账号池前端导入使用官方账号导入 API，展示账号 ID、外部身份、quota 状态、路由状态和导入/更新结果。
- 系统事件 API 与页面支持 `eventType`、`entityType`、`entityRef` 过滤；批量恢复弹窗能跳转到对应审计事件视图。

## 非目标

- 不自动执行真实 live smoke；前端仍默认 dry-run 或显式操作。
- 不展示真实 token、raw `auth.json`、prompt、cookie 或 upstream 完整错误。
- 不引入外部 Notion、Linear 或 SaaS 流程。
- 不对既有非相关脏工作区做回退或格式化。

## 详细设计

### TASK-20260508-004

- 在后端通用 `AccountAdminService.importAuthJson` 中增加 Codex 感知：
  - 当目标账号池为 `CODEX_OAUTH` 时复用 `CodexAuthJsonParser`，从 raw metadata 中提取 token、account_id、subject、email 和 identityKey。
  - `STRONG` 身份用 `identityKey` 作为 canonical externalAccountId；`WEAK_TOKEN` 只作为弱证据，不跨账号合并。
  - 查找顺序：provider + canonical externalAccountId、provider + legacy accountId、metadata 中的 `account_identity.identityKey` 或 `codex_auth_json.identityKey`。
  - 命中既有账号时更新密文、quota/refresh/runtime 字段和脱敏 metadata；未命中时创建新账号。
- 同步加固 `OfficialAccountAdminService.importOfficialAccount` 的 Codex 幂等更新逻辑，避免官方导入与通用导入行为分裂。
- 增加统一 JSON 脱敏工具，写入 metadata、header snapshot、lastRefreshResultJson 前递归移除敏感值。

### TASK-20260508-005

- 账号池详情页识别 Codex pool 后，导入提交调用 `/admin/accounts/official/import`。
- Codex 导入 payload 保留 raw `auth.json` 在 `metadataJson` 中给后端解析，但前端预览只展示 masked token。
- 导入成功后展示结果条：账号 ID、外部身份、quotaStatus、routeEligible、导入状态摘要。
- 前端测试覆盖 Codex pool 调用官方导入 API、非 Codex pool 保留通用导入路径、不会在预览中展示明文 token。

### TASK-20260508-006

- 扩展 `GET /admin/ops/system-events` 查询参数：`eventType`、`entityType`、`entityRef`。
- `OpsTimelineService.listEvents` 在内存过滤层支持新增条件，保持现有 Top500 查询模型。
- 系统事件页面读取 URL query 初始化过滤条件，并把新增条件传给后端。
- Codex 批量恢复弹窗显示审计事件入口，跳转到 `/console/ops/system-events?eventType=CODEX_RUNTIME_BATCH_RECOVERY&entityRef=account-pool:{poolId}`。

## 风险与约束

- 真实 Codex 凭证已进入长期测试环境，任何日志、页面、测试断言都不能输出明文。
- 如果历史账号只保存了 legacy `externalAccountId`，需要通过 metadata identity 兼容，不能只依赖新字段。
- 批量恢复审计过滤不能破坏已有 severity/source/from/to 查询。
- 工作区已有大量未提交修改，本批只处理本需求关联文件。

## 验收标准

- 后端测试证明同一 Codex 身份的不同 `auth.json` 会更新同一账号，弱 token 不跨账号合并。
- 后端测试证明通用导入和官方导入不会把 token 写入 metadata/header/result。
- 前端测试证明 Codex 导入调用官方 API，并能展示脱敏结果；非 Codex 导入仍可用。
- 系统事件后端和前端测试覆盖 `eventType/entityRef` 过滤；批量恢复弹窗能提供审计跳转。
- 定向后端测试、前端测试、typecheck/build 通过；浏览器联调确认账号池详情页和系统事件页可访问。

## 实施记录

- 新增 `SensitiveJsonSanitizer`，用于导入类 JSON 的递归脱敏，覆盖 token、secret、api_key、authorization、cookie、password、session_key 和常见 Bearer/API key 字符串。
- `AccountAdminService.importAuthJson` 增加 Codex 感知：Codex pool 会解析 raw `auth.json`，用 `identityKey` 作为强身份 canonical externalAccountId，命中历史账号时更新旧账号而不是创建重复账号；`WEAK_TOKEN` 不做 metadata 扫描合并。
- `OfficialAccountAdminService.importOfficialAccount` 同步使用 Codex canonical identity 去重，并在 metadata 写入 `account_identity`、`codex_auth_json`、`import_status` 和 `import_dedupe_key` 的脱敏摘要。
- 账号池详情页在 Codex pool 下改走 `/admin/accounts/official/import`，非 Codex pool 保留 `/admin/accounts/import-auth-json`；导入成功后展示账号 ID、外部身份、quota/refresh 状态和路由状态。
- `OpsTimelineAdminController` 与 `OpsTimelineService` 扩展 `eventType`、`entityType`、`entityRef` 过滤。
- Codex Runtime 批量恢复事件统一写入 `entityType=ACCOUNT_POOL`、`entityRef=account-pool:{id}`；批量恢复弹窗新增“查看审计事件”跳转，系统事件页从 URL query 初始化过滤条件。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.AccountAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.OfficialAccountAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.AccountPoolAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.OpsTimelineServiceTests"`：通过。
- `bun run test ./src/features/accounts/account-pool-detail-page.test.tsx ./src/features/ops/system-events-page.test.tsx`：2 个文件、7 个测试通过。
- `bun run typecheck`：通过。
- `bun run build`：通过。
- `bunx eslint ./src/features/accounts/account-pool-detail-page.tsx ./src/features/accounts/account-pool-detail-page.test.tsx ./src/features/ops/system-events-page.tsx ./src/features/ops/system-events-page.test.tsx`：通过。
- 浏览器联调：重启后端并打开 `http://127.0.0.1:5173/console/account-pools/5`，登录后执行 Codex 批量恢复预检；弹窗出现 `/console/ops/system-events?eventType=CODEX_RUNTIME_BATCH_RECOVERY&entityRef=account-pool%3A5` 审计入口；跳转后系统事件页保留过滤条件并展示对应 Codex Runtime 批量恢复事件。

## 遗留问题与后续建议

- 本批没有执行真实 live smoke 或真实 `auth.json` 再导入，以避免不必要地刷新长期测试账号；相关能力已由单元测试和浏览器 dry-run 路径覆盖。
- 若历史库中已经存在旧格式 `entityRef=5` 的批量恢复事件，仍可通过系统事件页清空 `entityRef` 或按 `eventType` 查询；新事件统一使用 `account-pool:{id}`。
