# TASK-20260521-005-01 控制台重复功能前端下线

## 任务类型

子任务 / 已完成

## 背景

父任务：`tasks/in-progress/TASK-20260521-005-console-feature-retirement-and-vector-scope-prune.md`

用户已经明确要求删除 `官方账号运行态`、`能力矩阵`、`Native 命名空间兼容`、`Provider 参考差距`、`站点档案`、`成本路由策略中心`、`向量检索排障沙盒` 等控制台功能。本轮按父任务既定假设，先执行低风险版本：只下线控制台导航、路由、页面、页内跳转、残留文案和对应前端测试，不直接删除高风险后端/API。

## 目标

- 把上述功能从控制台可见产品面中移除。
- 保留历史路由 redirect，避免已有书签直接落到 404。
- 清理仍存活页面中的旧文案和错误跳转，避免继续暴露已下线概念。

## 非目标

- 不删除 `/admin/accounts*`、`/admin/provider-sites*`、`/admin/cost-routing*`、`/admin/vector-stores*`。
- 不删除公开 `/v1/vector_stores*`、Responses `file_search` 绑定、OpenAPI 和后端持久化实现。
- 不重构 `account groups`、`credentials`、`models` 等保留主路径的交互模型。

## 上游来源

- `docs/requirements/REQ-20260521-005-console-feature-retirement-and-vector-scope-prune.md`
- `tasks/in-progress/TASK-20260521-005-console-feature-retirement-and-vector-scope-prune.md`

## 输入

- `web/src/app/navigation.ts`
- `web/src/app/router.tsx`
- `web/src/features/accounts/`
- `web/src/features/provider-sites/`
- `web/src/features/ops/cost-routing-page.tsx`
- `web/src/features/workbench/VectorStoreSandbox.tsx`
- 相关前端测试文件与公共文案文件

## 输出

- 已下线功能对应的导航、路由、页面源码和测试文件清理结果。
- 存活页面中旧概念文案与回跳路径修正结果。
- 前端残留扫描与定向验证记录。

## 影响范围

- Console 信息架构。
- 账号分组、上游凭证、模型目录、工作台等保留页面的跳转语义。
- 前端测试口径。

## 依赖

- 父任务已经确认本轮采用“前端先下线、后端暂保留”的执行假设。

## 风险

- 页面文件删除后，如仍有隐藏 import 或测试引用，`typecheck`/`vitest` 会直接失败。
- OAuth 回调与保留页之间如果还指向旧 `/accounts` 语义，用户会被 redirect 但不知道该去哪里继续。

## 验收标准

- [x] 已下线功能不再出现在 Console 导航、路由入口、页面链接和公共说明文案中。
- [x] 对应前端页面与测试文件完成清理，且没有残留 import。
- [x] 历史路由仍能通过 redirect 落到保留页面。
- [x] `bun run typecheck` 通过。
- [x] 相关定向 vitest 用例通过。

## 实施结果

- 已从 `navigation.ts`、`router.tsx`、Dashboard/Workbench/公共页文案中下线 `官方账号运行态`、`能力矩阵`、`Native 命名空间兼容`、`Provider 参考差距`、`站点档案`、`成本路由策略中心`、`向量检索排障沙盒` 对应前端入口。
- 已删除不再被路由加载的前端页面与测试文件，包括 `accounts/oauth-sessions`、`provider-sites/*` 相关页面、`ops/cost-routing-page.tsx`、`workbench/VectorStoreSandbox.tsx` 等。
- 已把存活页面中的旧概念改成保留主路径语义，例如 OAuth 回调返回 `账号分组`，`Site Profile ID` 替代 `站点档案 ID`，公开页不再把 `能力矩阵`、`参考差距` 等写成活跃控制台能力。

## 测试边界

- 前端：`bun run typecheck`
- 前端：`bun run test -- src/app/navigation.test.ts src/features/credentials/credentials-page.test.tsx src/features/workbench/workbench-page.test.tsx src/features/accounts/account-groups-page.test.tsx src/features/accounts/account-group-detail-page.test.tsx src/features/accounts/oauth-connect-page.test.tsx`
- 前端：残留关键词与路径扫描

## 验证结果

- `bun run typecheck`：通过
- `bun run test -- src/app/navigation.test.ts src/features/credentials/credentials-page.test.tsx src/features/workbench/workbench-page.test.tsx src/features/accounts/account-groups-page.test.tsx src/features/accounts/account-group-detail-page.test.tsx src/features/accounts/oauth-connect-page.test.tsx`：通过（6 个文件，16 个测试）
- 残留扫描：`web/src` 中未再发现已删除页面 import；仅保留后端/API 仍存在的概念性术语或历史兼容路径

## 当前状态

已完成（2026-05-21）
