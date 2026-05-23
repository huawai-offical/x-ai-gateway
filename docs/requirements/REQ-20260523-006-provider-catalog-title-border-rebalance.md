# REQ-20260523-006 厂商目录标题层级与表格边界再平衡

状态：Done  
日期：2026-05-23  
关联任务：`tasks/done/TASK-20260523-007-provider-catalog-title-border-rebalance.md`

## 背景

用户反馈厂商管理界面的“厂商目录”出现多层大小标题嵌套，并且表格框线不见了。复核当前实现后确认：

- 页面外壳已经展示路由标题“厂商管理”。
- 页面内容顶部 `PageSection` 又展示“上游接入 / 厂商管理中心”。
- 厂商目录列表区继续展示“厂商目录 / 厂商与 API 入口”。
- 上一轮 `REQ-20260523-003` 为解决框线嵌套过重，刻意去掉了厂商目录表格外层圆角边框，仅保留表头底线和行分隔；在标题层级未同步收敛时，这会让页面显得既重复又缺少表格边界。

## 目标

- 将厂商管理页收敛为清晰的单一内容标题层级，避免同一屏出现多个“厂商目录 / 厂商管理”主标题。
- 恢复厂商目录表格的轻量外框和表头背景，让表格边界可见。
- 保留上一轮减少卡片嵌套的方向，不恢复多层卡片包表格。
- 不改变厂商目录数据、筛选、分页和操作路径。

## 范围

- `web/src/features/provider-sites/provider-sites-page.tsx`
- `web/src/features/provider-sites/provider-sites-page.test.tsx`
- `docs/index.md`
- `tasks/index.md`
- 本需求和关联任务文档。

## 非目标

- 不修改后端 ProviderSite API、导入逻辑或 capability matrix。
- 不重做详情页、协议入口编辑弹窗或全局 AppShell 标题。
- 不全局修改所有表格样式。

## 方案

- 列表页顶部概览区保留操作按钮、错误提示、统计和筛选，但去掉会与 AppShell 标题重复的 `PageSection` 标题容器。
- 厂商目录区只保留一个简洁标题“厂商目录”，不再同时展示“厂商目录 / 厂商与 API 入口”两级标题。
- 表格使用单层 `overflow-x-auto` 容器，恢复 `rounded-xl border border-border/60 bg-card/92`，并增加 `thead bg-muted/30`，让边界清楚但不过度嵌套。
- 分页继续使用轻量样式，避免底部再增加独立卡片感。

## 风险

- 测试中若依赖“厂商管理中心”文本，需要同步调整为新的标题口径。
- 恢复表格外框后需要确认不会回到上一轮被投诉的多层框线状态。

## 验收标准

- 首屏不再出现多层“厂商目录 / 厂商与 API 入口 / 厂商管理中心”标题堆叠。
- 厂商目录表格外边界、表头底线和行分隔清晰可见。
- 厂商目录仍可展示已导入和可导入行，管理、刷新、删除、导入操作保持不变。
- 前端定向测试、类型检查和浏览器抽查通过或记录限制。

## 实现结果

- 厂商管理列表页顶部从 `PageSection` 改为普通 `section`，移除重复的“上游接入 / 厂商管理中心”标题。
- 厂商目录区只保留一个 `H2 厂商目录`，删除“厂商目录 / 厂商与 API 入口”两级标题组合。
- 厂商目录表格恢复单层轻量边界：`overflow-x-auto rounded-xl border border-border/60 bg-card/92`。
- 表头增加 `bg-muted/30`，首列表头改为“厂商 / API 入口”，避免和区块标题再次重复。
- 原有统计、筛选、分页、导入、管理、刷新、删除、新增自定义入口操作保持不变。

## 测试/验证

- 通过：`bun run typecheck`
- 通过：`bun run test -- --run src/features/provider-sites/provider-sites-page.test.tsx`
- 通过：`bunx eslint src/features/provider-sites/provider-sites-page.tsx src/features/provider-sites/provider-sites-page.test.tsx`
- 浏览器验证：`http://127.0.0.1:5173/console/provider-sites`
  - 使用临时本地 mock Admin API 验证 React 真实渲染，未写入仓库。
  - DOM 标题为 `H1 厂商管理` 和 `H2 厂商目录`。
  - 旧标题“厂商管理中心”“厂商与 API 入口”计数为 0。
  - 表格容器存在单层 border，表头背景存在，渲染 2 行样例厂商数据。
  - 浏览器 console warning/error 为空。

## 遗留问题

- 未启动真实 `x-ai-gateway` 后端做数据联调；当前本机 8082 端口属于另一个项目进程，不是本仓库控制台 API。真实页面视觉由临时 mock Admin API 覆盖，业务接口行为由现有组件测试覆盖。
