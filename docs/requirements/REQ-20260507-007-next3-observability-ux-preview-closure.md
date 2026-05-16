# REQ-20260507-007 第五批最高优先级任务闭环设计

状态：Done  
创建日期：2026-05-07  
关联任务：
- [TASK-20260507-004 Codex 实时请求、Usage 与过滤命中观测台](../../tasks/done/TASK-20260507-004-codex-realtime-usage-filter-observability.md)
- [TASK-20260507-007 前端可用性验收、表单友好性与移动端体验硬化](../../tasks/done/TASK-20260507-007-frontend-usability-form-mobile-hardening.md)
- [TASK-20260507-017 Codex Runtime 批量预检与脱敏审计闭环](../../tasks/done/TASK-20260507-017-codex-runtime-batch-preflight-audit.md)

## 背景

上一批已经闭环 Codex 接入向导、Portal 自助入口和账号池 Runtime 面板。当前剩余 Backlog 中最高优先级是 Codex 排障观测与前端可用性硬化。结合用户对“批量操作可信性、批量过程容错性、重复账号判定”的提醒，本批新增一张运行态批量预检任务，避免账号恢复、隔离、quota 刷新等批量动作只凭操作员经验执行。

## 目标

1. 在 Console 中提供 Codex 视角的请求观测台，聚合 request log、route decision、usage/cache 收益、filter 命中、client instance 与 session 线索。
2. 将关键裸 ID/长表格/空态风险收敛到 UX 验收矩阵和页面实现中，优先覆盖 Codex 接入、账号池、request logs。
3. 为 Codex Runtime 增加批量恢复预检与脱敏审计包，先说明候选账号、阻断原因和可执行影响，再允许后续扩展真实批量动作。

## 非目标

- 本批不新增存储未脱敏 prompt、token、secret 的后端表。
- 本批不实现真实批量恢复提交 API，只闭环“预检、解释、导出、测试”能力，为后续破坏性批量动作建立可信前置。
- 本批不重构所有 Console 页面，只覆盖本轮最高风险路径。

## 详细设计

### Codex 请求观测台

- 在 request logs 页面新增 Codex 观测区，前端从 request logs、route decisions、cache hits 中构建安全投影。
- 支持 Codex-only、client instance、session affinity、本地模型/状态筛选。
- 详情中提供“脱敏排障包”，只包含 requestId、模型、provider、credentialId、路由候选摘要、filter summary、usage/cache 数字和错误摘要。
- 对未知后端字段采用 forward-compatible optional 字段，后端逐步补充字段时前端无需再次大改。

### UI/UX 硬化

- `ux-acceptance` 增加 Codex onboarding、Codex observability、account pool runtime 的关键页面验收。
- 将账号池绑定访问 Key 的裸 `distributedKeyId` 输入改为分布式 Key picker，显示 keyName、maskedKey、id 和协议范围。
- 宽表使用横向滚动与稳定最小宽度，避免移动端挤压导致操作列不可用。
- 空态增加下一步动作或清空筛选入口。

### 批量预检与审计

- 在 Codex Runtime 面板增加“批量恢复预检”。
- 按账号运行态计算 `safe`、`blocked`、`alreadyReady` 三类：
  - `safe`：冻结、冷却、健康异常、刷新失败等可通过恢复/重置/quota 刷新尝试修复。
  - `blocked`：最近错误包含 policy、permission、security、forbidden、disabled 等安全/权限语义。
  - `alreadyReady`：当前健康且未冻结、未冷却。
- 输出脱敏 JSON 审计包，隐藏完整错误文本，只保留错误摘要和阻断原因。

## 风险与约束

- 观测台不能为了可视化而展示未脱敏原始内容。
- 批量预检必须默认保守，权限/策略类错误一律阻断。
- UX 硬化应保持现有信息密度，避免把运营页改成低密度营销页。

## 验收标准

- `TASK-20260507-004`：Codex 请求观测区可筛选、可显示 filter/usage/cache/route 线索，并能打开脱敏排障包。
- `TASK-20260507-007`：UX 矩阵覆盖新增 Codex 页面，账号池绑定不再要求手写裸分布式 Key ID，宽表移动端不挤压操作列。
- `TASK-20260507-017`：Codex Runtime 面板可生成批量恢复预检，区分 safe/blocked/alreadyReady，并输出脱敏审计包。
- 前端定向测试、typecheck 和 build 通过；如存在既有噪音需明确区分。

## 实施记录

- `web/src/features/request-logs/request-logs-page.tsx` 增加 Codex 观测投影、Codex-only/client/session/model/status 筛选、filter/usage/cache/route 摘要和脱敏排障包。
- `web/src/features/accounts/account-pool-detail-page.tsx` 将分布式 Key 绑定改为 picker，并新增 Codex Runtime 批量恢复预检弹窗与脱敏审计 JSON。
- `web/src/app/ux-acceptance.ts` 增加 `mobile-table-overflow` 规则，补齐 Codex onboarding、Codex observability、account pool runtime 三个关键页面。
- 新增/更新 Vitest，覆盖 Codex 观测台、Key picker、批量预检阻断候选和 UX 矩阵。

## 验证记录

- `bun run test -- src/features/request-logs/request-logs-page.test.tsx src/features/accounts/account-pool-detail-page.test.tsx src/app/ux-acceptance.test.ts`：通过，3 个文件 9 个测试。
- `bun run typecheck`：通过。
- `bun run build`：通过。
- In-app browser 烟测：`http://127.0.0.1:5173/login?redirect=%2Fconsole%2Faccounts%2Fconnect%2Fcodex` 可达，登录页渲染正常；当前后端 `/auth/session` 返回“服务暂时不可用”，因此未进入需登录的 Console 页面做浏览器级真实数据操作。

## 后续建议

- 后续若要把批量预检升级为真实批量恢复 API，应复用本批 `safe/blocked/alreadyReady` 分类，并在后端写入系统事件审计。
- 后端可继续补充 `clientInstanceId`、`sessionAffinityKey`、`filterSummaryJson`、usage token 字段，前端已按 optional 字段兼容。
