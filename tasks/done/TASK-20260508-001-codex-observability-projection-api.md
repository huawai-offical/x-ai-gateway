# TASK-20260508-001 Codex Observability Projection API 与前端直连

状态：Done  
优先级：High  
排期：P0-01  
来源：[REQ-20260508-001 Codex 观测后端化、批量恢复与联调 Smoke 闭环](../../docs/requirements/REQ-20260508-001-codex-observability-batch-api-smoke-closure.md)

## 背景

上一批前端已能从通用 request logs、route decisions、cache hits 拼出 Codex 观测台，但这不是后端事实源。运维排障需要一个稳定 API，直接返回 Codex 视角投影和脱敏排障包。

## 目标

- 新增 Codex Observability Projection API。
- 聚合 request log、route decision、usage record 和 cache hit。
- 支持 client instance、session affinity、model、status 等筛选。
- 返回脱敏 diagnostic JSON，不暴露 prompt、token、secret、auth.json。
- 前端观测台优先直连新 API。

## 验收标准

- `/admin/observability/codex-requests` 可返回 Codex-only 请求投影。
- usage/cache/route/filter/client/session 字段在单个响应中可见。
- model/status/client/session 筛选可用。
- 后端测试覆盖脱敏和筛选；前端测试覆盖新 API 数据渲染。

## 实施记录

- 新增 `CodexObservabilityRequestResponse` 与 `/admin/observability/codex-requests`。
- `ObservabilityQueryService` 新增 Codex 投影聚合逻辑，按 requestId 补齐 route decision、cache hit、usage record，并输出 client instance、session affinity、route/cache/usage/filter 摘要。
- `filterSummaryJson` 只从 route candidate summary 中提取脱敏线索；错误摘要、diagnostic JSON 均做 token/Bearer/JWT 脱敏。
- `RequestLogsPage` 新增后端 Codex Projection 查询，Codex 面板优先使用后端事实源，保留通用 request logs/route/cache tab 作为旁路观测。

## 验证记录

- 后端：`ObservabilityQueryServiceTests.shouldBuildCodexObservabilityProjectionWithUsageCacheAndRedaction` 覆盖 usage/cache/filter/redaction/client/session 筛选。
- 前端：`request-logs-page.test.tsx` 覆盖后端 Codex API 数据渲染、筛选空态、脱敏包。
- 联调：浏览器登录后进入 `/console/request-logs`，Codex 面板与空态正常渲染，无框架错误覆盖。
