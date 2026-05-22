# TASK-20260507-004 Codex 实时请求、Usage 与过滤命中观测台

状态：Done  
优先级：Medium  
排期：P2-12  
来源：[REP-20260507 Codex 账户反代与 UI/UX 深度差距分析](../../docs/reports/REP-20260507-codex-proxy-uiux-gap-analysis.md)
本批需求：[REQ-20260507-007 第五批最高优先级任务闭环设计](../../docs/requirements/REQ-20260507-007-next3-observability-ux-preview-closure.md)

## 背景

`cli_proxy-master` Web UI 能看到实时请求、请求详情、响应 chunk、Token usage 和 filter 配置。当前项目已有 request logs、traces、dashboard、CloudCliRequestFilterResult，但 Codex 操作员需要跨多个页面排障，且 filter 命中和 usage 不够贴近实时 session。

## 目标

- 建立 Codex 视角的实时请求观测台。
- 聚合请求状态、SSE chunk 摘要、usage、账号、client instance、session affinity 和 filter 命中。
- 支持按 session、client instance、账号、模型、请求状态筛选。
- 对 DENY、REDACT、MASK、REMOVE 等过滤动作给出脱敏解释。

## 详细设计

- 复用 request log、trace、usage record 和 route decision，新增 Codex observability projection API。
- 对 streaming 请求记录生命周期事件：started、selected、streaming、completed、failed、filtered、denied。
- 前端增加 Codex Observability 页面或 dashboard tab，显示 live list、详情抽屉和 usage breakdown。
- filter 命中只显示 ruleId、target、path、action、summary，不显示原始敏感内容。
- 支持一键复制脱敏排障包，便于用户提交 issue 或内部排查。

## 本批落地范围

- 先在 request logs 页面实现 Codex 观测投影，复用既有 `/admin/observability/*` 查询，不新增敏感内容存储。
- 对后端已存在或后续可能补充的 `clientInstanceId`、`sessionAffinityKey`、`filterSummaryJson`、usage 字段做 optional 兼容。
- 增加 Codex-only、client instance、session、模型、状态筛选和脱敏排障包。
- 前端测试覆盖 Codex 观测区、筛选和排障包。

## 验收标准

- Codex 请求可以按 client instance/session/account 维度实时查看。
- filter 命中、路由原因、usage 和错误摘要在同一详情面板可见。
- 流式响应不会把完整敏感内容写入前端缓存或日志。
- 前端测试覆盖空态、筛选、详情抽屉和错误态。

## 风险

- 不能为了观测完整性保存未脱敏 prompt 或 secret。
- 实时推送应有分页/采样/上限，避免高并发下拖垮 UI。

## 实施记录

- 在 request logs 页面新增 Codex 观测台，聚合 request log、route decision、cache hit 的安全投影。
- 增加 Codex-only、client instance、session affinity、model、status 筛选。
- 增加 filter 命中、usage token、cache saved/hit/write、route summary、error summary 展示。
- 增加 Codex 脱敏排障包弹窗，明确不包含 prompt、token、secret、完整 auth.json 和完整 upstream 错误正文。
- 宽表改为横向滚动与稳定 `min-width`，移动端不再挤压操作列。

## 验证记录

- `bun run test -- src/features/request-logs/request-logs-page.test.tsx`：通过，覆盖 Codex 观测、筛选、usage/filter/cache 和脱敏包。
- `bun run typecheck`：通过。
- `bun run build`：通过。

## 2026-05-21 历史归档口径

- 本任务记录的是旧 `live/realtime` 观测台的历史实现与验证结果。
- 随着 Realtime 对外面板和相关控制台能力下线，本任务仅作为归档证据保留，不再代表现役产品面。
