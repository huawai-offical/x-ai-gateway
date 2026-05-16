# REQ-20260508-001 Codex 观测后端化、批量恢复与联调 Smoke 闭环

状态：Done  
创建日期：2026-05-08  
关联任务：
- [TASK-20260508-001 Codex Observability Projection API 与前端直连](../../tasks/done/TASK-20260508-001-codex-observability-projection-api.md)
- [TASK-20260508-002 Codex Runtime 批量恢复执行 API、容错与系统事件审计](../../tasks/done/TASK-20260508-002-codex-runtime-batch-recovery-api-audit.md)
- [TASK-20260508-003 Codex 前后端联调 Smoke、后端测试与浏览器回归证据](../../tasks/done/TASK-20260508-003-codex-e2e-smoke-backend-frontend-evidence.md)

## 背景

上一批已经在前端闭环 Codex 观测投影、批量恢复预检和 UX 验收。但观测台仍主要由前端从多个通用接口拼接，批量恢复也只停留在 dry-run 预检。为了把系统从“可见”推进到“可信可执行”，本批将两类能力下沉为后端事实源，并补齐前后端联调证据。

## 目标

1. 提供后端 Codex Observability Projection API，将 request log、route decision、usage、cache 和 filter metadata 合成脱敏投影。
2. 提供 Codex Runtime 批量恢复 API，支持预检与执行两种模式，并对 partial success、blocked、failed 做结构化结果。
3. 将前端观测台与 Runtime 面板改为直连后端 API，并用后端单测、前端测试、构建和浏览器烟测形成联调证据。

## 非目标

- 不保存未脱敏 prompt、token、auth.json 或完整 upstream 错误。
- 不绕过已有单账号恢复、quota refresh、runtime reset 逻辑。
- 不引入新的外部 SaaS 工作流或在线任务系统。

## 详细设计

### TASK-20260508-001

- 新增 `/admin/observability/codex-requests`。
- 查询参数支持 `distributedKeyId`、`providerType`、`requestId`、`clientInstance`、`sessionAffinityKey`、`model`、`status`、`from`、`to`。
- 响应按 requestId 聚合 request log、route decision、cache hit、usage record。
- `filterSummaryJson` 从 route candidate/filter metadata 中抽取；缺失时返回“未记录 filter 命中”，不返回原始 request body。
- `diagnosticJson` 只包含脱敏排障字段。

### TASK-20260508-002

- 新增账号池级 Codex Runtime batch recovery API：
  - `POST /admin/account-pools/{id}/codex-runtime/batch-recovery-preflight`
  - `POST /admin/account-pools/{id}/codex-runtime/batch-recovery`
- 后端统一计算 `safe`、`blocked`、`alreadyReady`。
- `execute=false` 只预检；`execute=true` 对 safe 账号执行 reset runtime，可选 quota refresh。
- 单账号失败不阻断整批，响应中记录 failed 项并继续处理后续账号。
- 写入 `ops_system_event`，detail JSON 只包含脱敏统计和账号 ID/名称/原因，不包含 secret。

### TASK-20260508-003

- 前端 request logs 页面优先读取 `/admin/observability/codex-requests`，保留通用日志 tab。
- 前端账号池 Runtime 面板调用后端预检/执行 API，展示 dry-run/执行结果。
- 增加后端单测与前端 Vitest，覆盖脱敏、过滤、blocked、partial success。
- 联调验证包含后端测试、前端测试、typecheck、build、浏览器登录页或可访问页面烟测。

## 风险与约束

- 批量执行必须保守：权限、策略、安全、禁用类错误默认 blocked。
- 批量执行必须容错：单个账号失败不能让整批不可用。
- 观测投影必须以脱敏字段为边界，不能为了排障暴露 prompt/token。
- 工作区已有大量未提交改动，本批只处理本需求相关文件，不回退无关变更。

## 验收标准

- 后端 Codex Projection API 返回 usage/cache/route/filter/session/client instance 线索和脱敏 diagnostic JSON。
- 后端 batch recovery API 能 dry-run、execute safe 项、阻断 blocked 项，并记录系统事件。
- 前端直连新 API，原有 Codex 观测台和 Runtime 批量预检/执行仍可操作。
- 后端定向测试、前端定向测试、typecheck、build 通过；浏览器烟测记录当前可达性和限制。

## 实施记录

- 新增 `GET /admin/observability/codex-requests`，由后端聚合 request log、route decision、usage record、cache hit，输出 Codex 视角投影和脱敏 `diagnosticJson`。
- 新增账号池级 `POST /admin/account-pools/{id}/codex-runtime/batch-recovery-preflight` 与 `POST /admin/account-pools/{id}/codex-runtime/batch-recovery`，后端统一分类 `safe`、`blocked`、`alreadyReady`，执行时只处理 safe 项。
- 批量恢复会写入 `ops_system_event`，detail JSON 仅包含统计、账号 ID、分类、执行状态和脱敏原因。
- 前端请求日志页优先直连 Codex Projection API；账号池详情页从本地 dry-run 推演升级为后端预检/执行结果展示。
- 当前真实联调环境中 `codex-long-term-test` 账号池有 1 个 alreadyReady 账号，批量执行按钮按 safe=0 自动禁用，避免误改真实账号运行态。

## 验证记录

- `./gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ObservabilityQueryServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.AccountPoolAdminServiceTests"`：通过。
- `./gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.api.ObservabilityAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.ObservabilityQueryServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.AccountPoolAdminServiceTests"`：通过。
- `bun run test ./src/features/request-logs/request-logs-page.test.tsx ./src/features/accounts/account-pool-detail-page.test.tsx`：7 个前端测试通过。
- `bun run typecheck`：通过。
- `bun run build`：通过。
- `bunx eslint ./src/features/request-logs/request-logs-page.tsx ./src/features/accounts/account-pool-detail-page.tsx ./src/features/request-logs/request-logs-page.test.tsx ./src/features/accounts/account-pool-detail-page.test.tsx`：通过。
- `bun run lint` 项目级扫描仍失败于既有无关文件 `web/src/app/router.tsx` 与 `web/src/features/accounts/codex-onboarding-page.tsx`，本轮改动文件定向 ESLint 已通过。
- 浏览器联调：前端 `http://127.0.0.1:5173` 与后端 `http://127.0.0.1:8080` 启动成功；登录控制台后验证 `/console/request-logs` Codex 面板可加载、无框架错误覆盖；验证 `/console/account-pools/5` 批量恢复预检可调用真实后端并返回 `auditEventId=2`。截图采集受 in-app browser CDP `Page.captureScreenshot` 超时限制，使用 DOM snapshot 与控制台日志作为证据。

## 后续建议

- 后续可继续补齐真实 Codex 请求样本后的 Projection API 数据回放测试，覆盖非空 request/usage/cache 场景的浏览器端视觉回归。
- 若要在真实库执行批量恢复，需要先人工确认 safe 候选确实允许解冻；当前 UI 已在 safe=0 时禁用执行。
