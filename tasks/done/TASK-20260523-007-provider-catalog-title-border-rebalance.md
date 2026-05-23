# TASK-20260523-007 厂商目录标题层级与表格边界再平衡

状态：Done  
优先级：High  
来源：User Feedback  
关联需求：`docs/requirements/REQ-20260523-006-provider-catalog-title-border-rebalance.md`  
关联报告：无

## 任务类型

子任务

## 背景

用户指出厂商管理界面的厂商目录出现多层大小标题嵌套，并且表格框线不见了。当前问题由两次 UI 收敛叠加造成：列表页仍保留 AppShell 标题、概览卡片标题和目录标题三层文字，而上一轮框线拆解已经去掉表格外框。

## 目标

- 收敛厂商管理列表页标题层级。
- 恢复厂商目录表格的轻量可见边界。
- 保留单一厂商目录、筛选、分页和操作按钮。
- 回写需求、任务索引和验证记录。

## 非目标

- 不调整后端 API、ProviderSite 数据结构或导入流程。
- 不改变 `/console/provider-sites/:id` 详情页。
- 不改全局 AppShell 或所有表格默认样式。

## 上游来源

- `docs/requirements/REQ-20260523-006-provider-catalog-title-border-rebalance.md`
- 用户反馈截图：“厂商目录为什么大小标题嵌套这么多层，并且表格的框线不见了”

## 输入

- `web/src/features/provider-sites/provider-sites-page.tsx`
- `web/src/features/provider-sites/provider-sites-page.test.tsx`
- 已完成任务 `tasks/done/TASK-20260523-004-provider-catalog-frame-flattening.md`

## 输出

- 标题层级收敛后的厂商管理列表页。
- 带轻量外框的厂商目录表格。
- 更新后的定向测试和文档索引。

## 影响范围

- `web/src/features/provider-sites/provider-sites-page.tsx`
- `web/src/features/provider-sites/provider-sites-page.test.tsx`
- `docs/index.md`
- `tasks/index.md`
- `docs/requirements/REQ-20260523-006-provider-catalog-title-border-rebalance.md`
- `tasks/in-progress/TASK-20260523-007-provider-catalog-title-border-rebalance.md`

## 依赖

- 现有 React Query 数据加载。
- 现有 `InfoGrid`、`StatusBadge`、`PaginatedRows`、`Dialog` 等组件。
- 上一轮厂商目录框线拆解后的单一目录模型。

## 风险

- 标题减少后页面需要仍能表达当前区域用途。
- 恢复外框时不能重新形成“卡片套卡片”的观感。

## 验收标准

- [x] 顶部概览区不再展示重复的“厂商管理中心”大标题。
- [x] 厂商目录区只保留单一标题。
- [x] 表格外框恢复为单层轻量边界，表头背景和行分隔可见。
- [x] 管理、刷新、删除、导入操作不变。
- [x] 定向测试、类型检查和浏览器抽查完成。

## 测试边界

- 前端：`provider-sites-page` 定向测试。
- 类型：`bun run typecheck`。
- 浏览器：`/console/provider-sites` 首屏视觉抽查。

## 关联文档

- `docs/requirements/REQ-20260523-006-provider-catalog-title-border-rebalance.md`
- `docs/requirements/REQ-20260523-003-provider-catalog-frame-flattening.md`

## 关联任务

- `tasks/done/TASK-20260523-004-provider-catalog-frame-flattening.md`
- `tasks/done/TASK-20260523-003-provider-site-ui-simplification.md`

## 当前状态

Done

## 实现记录

- 移除 `provider-sites-page.tsx` 对 `PageSection` 的依赖，顶部区域改为普通 section，仅保留操作按钮、错误提示、统计和筛选。
- 厂商目录标题收敛为单一 `H2 厂商目录`，删除“厂商与 API 入口”重复标题。
- 表格外层恢复为单层轻量边框容器，并为表头增加背景色。
- 首列表头调整为“厂商 / API 入口”，避免和区块标题重复。
- `provider-sites-page.test.tsx` 增加断言：旧“厂商管理中心”不再出现，新的目录标题和首列表头存在。

## 测试/验证

- `bun run typecheck`：通过。
- `bun run test -- --run src/features/provider-sites/provider-sites-page.test.tsx`：通过，1 个文件 2 个用例。
- `bunx eslint src/features/provider-sites/provider-sites-page.tsx src/features/provider-sites/provider-sites-page.test.tsx`：通过。
- 浏览器抽查：`http://127.0.0.1:5173/console/provider-sites`
  - 使用临时 mock Admin API 验证真实 React 页面渲染。
  - 页面标题层级为 `H1 厂商管理`、`H2 厂商目录`。
  - 旧标题“厂商管理中心”“厂商与 API 入口”计数为 0。
  - 表格容器 class 为 `overflow-x-auto rounded-xl border border-border/60 bg-card/92`，存在单层边框和表头背景。
  - 页面渲染 OpenAI 主站和 MiMo OpenAI 入口两行样例数据，操作按钮可见。
  - 浏览器 console warning/error 为空。

## 遗留问题

- 当前本机 8082 端口是 `Camera_AI_Recognition` 项目进程，非本仓库后端；因此本轮浏览器验证使用临时 mock Admin API，不覆盖真实数据库数据联调。
