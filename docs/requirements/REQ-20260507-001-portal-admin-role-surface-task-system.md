# REQ-20260507-001 社区 Portal 与后台 Console 角色化界面任务体系

状态：Done  
日期：2026-05-07  
关联任务：

- [TASK-20260507-008 Portal/Admin 角色化界面任务体系拆分](../../tasks/done/TASK-20260507-008-portal-admin-task-system-breakdown.md)
- [TASK-20260507-009 Portal/Admin 路由、身份与产品面边界基线](../../tasks/done/TASK-20260507-009-portal-admin-route-identity-boundary.md)
- [TASK-20260507-010 Community Portal Codex 自助接入与个人用量界面](../../tasks/done/TASK-20260507-010-community-portal-codex-self-service-surface.md)
- [TASK-20260507-011 Admin Console 命名空间迁移与旧路由兼容](../../tasks/done/TASK-20260507-011-admin-console-namespace-legacy-route-migration.md)
- [TASK-20260507-012 Admin Console 角色化工作台与导航体系](../../tasks/done/TASK-20260507-012-admin-console-role-workbench-navigation.md)
- [TASK-20260507-013 Portal/Admin API 权限隔离、审计与越权回归](../../tasks/done/TASK-20260507-013-portal-admin-permission-audit-regression.md)
- [TASK-20260507-014 Portal/Console UI/UX 验收体系与组件硬化](../../tasks/done/TASK-20260507-014-portal-console-ux-acceptance-system.md)

## 背景

当前前端已经有两套入口：

- 社区用户 Portal：`/portal/*`，包括登录、注册、概览、订阅、访问密钥、兑换和公告详情。
- 后台管理员 Console：`/login` 与根路径下的管理路由，由 `RequireAdminAuth` 和 `AppLayout` 保护，覆盖账号、Key、账号池、模型、Provider、日志、治理、运维和集成。

现状的问题不是“完全没有区分”，而是区分还停留在路由和 API namespace 层面。社区用户使用系统时应围绕“我怎么接入、我的 Key/额度/用量/账单/公告是什么”；后台管理员使用系统时应围绕“系统怎么运营、账号池怎么调度、请求怎么排障、成本和风险怎么治理”。如果继续把所有能力按后端实体平铺，会导致社区用户看到过重概念，管理员也难以快速完成高频运营任务。

## 目标

- 建立明确的产品面：Public Site、Community Portal、Admin Console。
- 明确 Anonymous、Community User、Team Owner、Support Admin、Platform Admin 的权限边界。
- 将现有 Codex 反代与 UI/UX backlog 组织为可推进的任务体系。
- 让后续实现按“边界先行、Portal 自助、Console 运营、权限回归、体验验收”的顺序闭环。
- 保留现有路由兼容，避免一次性破坏当前管理端入口和测试。

## 产品面定义

| 产品面 | 建议路由 | 用户 | 核心问题 | 不应暴露 |
| --- | --- | --- | --- | --- |
| Public Site | `/`、`/docs`、`/pricing`、`/status` | 未登录访客 | 这是什么、怎么开始、状态是否正常 | 管理数据、个人 Key、上游账号 |
| Community Portal | `/portal/*` | 社区用户、Team Owner | 我的订阅、Key、用量、账单、Codex 接入、公告 | 上游账号 secret、账号池内部候选、全局日志、Provider 凭证 |
| Admin Console | `/console/*`，旧根管理路由重定向 | Support Admin、Platform Admin | 接入治理、账号池、路由策略、观测排障、计费、系统配置 | 用户长期 secret 明文、无审计代管操作 |

## 身份与权限矩阵

| 能力 | Anonymous | Community User | Team Owner | Support Admin | Platform Admin |
| --- | --- | --- | --- | --- | --- |
| 查看公开文档、价格、状态 | 允许 | 允许 | 允许 | 允许 | 允许 |
| 注册和登录 Portal | 允许 | 允许 | 允许 | 不适用 | 不适用 |
| 查看自己的订阅、余额、Key、用量 | 禁止 | 允许 | 允许 | 只读代查并审计 | 允许并审计 |
| 创建/撤销自己的访问 Key | 禁止 | 允许 | 允许 | 代操作需审计 | 允许并审计 |
| 使用 Codex 接入向导 | 禁止 | 允许 | 允许 | 可协助但不读取长期 secret | 允许 |
| 查看全局账号池和官方账号 | 禁止 | 禁止 | 禁止 | 允许 | 允许 |
| 导入上游官方账号与凭证 | 禁止 | 禁止 | 禁止 | 受限或禁止 | 允许 |
| 修改路由、过滤、熔断、成本策略 | 禁止 | 禁止 | 禁止 | 受限 | 允许 |
| 查看全局请求日志和 trace | 禁止 | 仅自己的摘要 | 团队范围摘要 | 允许并脱敏 | 允许并脱敏 |
| 系统设置、部署、迁移、回滚 | 禁止 | 禁止 | 禁止 | 受限 | 允许 |

## 任务体系

### A. 边界与安全基线

- `TASK-20260507-009`：确定产品面、路由、登录态、导航边界和术语映射。
- `TASK-20260507-013`：为 Portal/Admin API 补权限矩阵、审计字段和越权回归。

验收关注：

- 普通用户永远不能通过前端或 API 看到上游账号、Provider secret、账号池内部候选和全局日志。
- 管理端代查和代操作必须留下审计记录。
- 路由命名和 API namespace 能从结构上区分用户面与管理面。

### B. Community Portal 自助闭环

- `TASK-20260507-010`：重整 Portal 首屏、Codex 自助接入、个人用量、Key、订阅与故障解释。
- 复用 `TASK-20260507-005`：Codex 接入向导、Client Instance 与 Deep Link UI 闭环。

验收关注：

- 新用户进入 Portal 后能看到“下一步做什么”。
- 用户不需要理解 distributed key、client instance、grant token 等内部对象，也能完成 Codex CLI 接入。
- Portal 空态、错误态和 smoke 失败态都有明确的下一步动作。

### C. Admin Console 运营闭环

- `TASK-20260507-011`：将管理端收敛到 `/console/*` 命名空间，并保留旧根管理路由重定向。
- `TASK-20260507-012`：建设角色化工作台、导航分组、任务入口、搜索和上下文链接。
- 复用 `TASK-20260507-003`：Codex 账号池热切换、负载均衡与失败恢复 UI。
- 复用 `TASK-20260507-004`：Codex 实时请求、Usage 与过滤命中观测台。

验收关注：

- 管理员首屏能直接进入接入 Codex、排查失败请求、查看用量和处理告警。
- 高级治理能力不丢失，但低频配置收纳到清晰二级入口。
- 管理端是高密度、可扫描、适合重复操作的运营界面，不做营销式首页。

### D. UI/UX 验收体系

- `TASK-20260507-014`：建立 Portal/Console 可用性验收矩阵、组件硬化和桌面/移动回归。
- 复用 `TASK-20260507-007`：前端可用性验收、表单友好性与移动端体验硬化。

验收关注：

- 高风险字段使用 picker、combobox、multi-select 或校验型 field array，减少裸 ID 和 CSV 输入。
- 桌面和移动 viewport 下无明显文字溢出、操作遮挡和表格失控。
- 核心页面具备 loading、empty、error、success 和 destructive confirm 状态。

## 执行顺序建议

1. 先做 `TASK-20260507-009` 与 `TASK-20260507-013`，把边界和权限锁住。
2. 再做 `TASK-20260507-011` 与 `TASK-20260507-012`，让后台管理端有明确 Console 形态。
3. 并行推进 `TASK-20260507-010` 与 `TASK-20260507-005`，形成社区用户自助 Codex 接入路径。
4. 接着推进 `TASK-20260507-003`、`TASK-20260507-004`，补齐管理员日常运营与排障。
5. 最后用 `TASK-20260507-014` 与 `TASK-20260507-007` 做跨页面体验验收和组件收口。

## 风险

- 路由迁移如果一次性替换所有路径，可能破坏已有收藏、测试和部署文档；必须保留旧路由重定向。
- Portal 为了“易用”不能泄露管理端能力；任何聚合 API 都必须从权限上先验约束。
- Admin Console 为了“友好”不能降低信息密度；运营人员需要快速扫描、批量处理和排障。
- Codex Deep Link 与 grant 只能携带短期一次性授权，不得在 URL、localStorage 或历史记录中放长期 secret。

## 验收标准

- 本需求文档与任务索引能清楚回答：谁使用哪个界面、能做什么、不能看什么、后续任务按什么顺序推进。
- 每个子任务都具备背景、目标、范围、详细设计、验收标准和风险。
- 现有 `TASK-20260507-005/006/007` 被纳入体系，不再孤立推进。
- 新增任务能直接进入后续实现排期，不需要再二次解释边界。

## 收口记录

- `TASK-20260507-009` 至 `TASK-20260507-014` 均已完成并归档到 `tasks/done/`。
- Community Portal 与 Admin Console 的产品面边界、路由命名空间、角色化工作台、API 权限隔离和 UI/UX 验收体系已分别在对应任务中闭环。
- 本文档状态已从 `Ready` 修正为 `Done`，任务链接已从历史 backlog 路径修正为 done 路径。
