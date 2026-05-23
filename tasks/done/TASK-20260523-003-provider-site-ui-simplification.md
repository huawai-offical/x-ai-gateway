# TASK-20260523-003 厂商管理界面与编辑界面收敛

## 任务类型

父任务

## 背景

来源：`docs/requirements/REQ-20260523-002-provider-site-ui-simplification.md`

厂商管理当前把厂商聚合、API 入口、协议入口、预设导入、模型能力和高级诊断全部堆在同一个信息层级里，编辑弹窗也直接暴露过多底层字段。用户明确反馈列表界面和编辑界面都太混乱，需要收敛为更清晰的运营主路径。

用户进一步指出：厂商聚合和预设导入本质上是一个东西，却展示在两个表格；厂商聚合的编辑和预设导入的查看入口得到的结果也不一样。该任务需要把列表口径调整为单一厂商目录，并统一操作入口。

## 目标

- 列表页采用单一厂商目录，把已导入入口和可导入预设合并为同一行状态。
- 已导入厂商统一进入详情页管理；未导入预设统一执行导入。
- 已导入入口表格压缩为少量关键列，并把协议入口做成更易读的摘要。
- 新增/编辑 API 入口弹窗做分组，减少低频字段干扰。
- 详情页增加视图模式，把高级诊断内容折叠到明确入口。
- 协议入口编辑弹窗做字段分组。

## 非目标

- 不改后端接口契约和业务逻辑。
- 不调整模型发现、凭证绑定、路由选择。
- 不下线任何已有能力。

## 上游来源

- `docs/requirements/REQ-20260523-002-provider-site-ui-simplification.md`

## 输入

- 厂商管理列表页。
- API 入口详情页。
- 新增/编辑 API 入口弹窗。
- 新增/编辑协议入口弹窗。

## 输出

- 更清晰的厂商管理列表与预设导入切换。
- 更分层的 API 入口编辑体验。
- 更聚焦的 API 入口详情页。
- 测试、浏览器验证和文档回写。

## 影响范围

- `web/src/features/provider-sites/provider-sites-page.tsx`
- `web/src/features/provider-sites/provider-site-detail-page.tsx`
- `web/src/features/provider-sites/provider-sites-page.test.tsx`
- `web/src/features/provider-sites/provider-site-detail-page.test.tsx`
- `docs/index.md`
- `tasks/index.md`

## 依赖

- 现有 React Query 数据加载。
- 现有 `PageSection`、`InfoGrid`、`StatusBadge`、`PaginatedRows`、`Dialog` 组件。

## 风险

- 分层后测试定位文本可能变化，需要同步调整定向测试。
- 当前后端如未重启，预设导入 endpoint preview 的浏览器验证可能仍使用旧响应，需要在验证记录中说明。

## 验收标准

- [x] 厂商管理列表页合并厂商聚合与预设导入为单一目录。
- [x] 已导入与未导入厂商使用一致的状态和操作口径。
- [x] 已导入入口表格列数和字段层级收敛。
- [x] API 入口新增/编辑弹窗分组清晰。
- [x] API 入口详情页默认隐藏高级诊断，只在切换后展示 surface/feature。
- [x] 协议入口编辑弹窗字段分组清晰。
- [x] 前端测试、类型检查、eslint 与浏览器抽查完成。
- [x] 文档与任务状态回写。

## 实现结果

- 列表页改为单一“厂商与 API 入口”目录，按 `site + preset` 合并生成厂商目录行。
- 预设导入不再作为独立表格展示；未导入时在目录行内提供“导入”，已导入时提供“管理 / 刷新 / 删除”。
- 已导入厂商不再在列表内打开编辑弹窗，统一进入 `/console/provider-sites/:id` 详情页维护。
- 新增弹窗重命名为“新增自定义 API 入口”，并拆成“基本信息 / 连接方式 / 高级配置”。
- 详情页拆成“概览 / 协议入口 / 模型能力 / 高级诊断”，surface/feature 默认隐藏。
- 协议入口新增/编辑弹窗拆成“基本信息 / 运行时策略 / 高级 JSON”。

## 验证记录

- 通过：`bun run typecheck`
- 通过：`bun run test -- --run src/features/provider-sites/provider-sites-page.test.tsx src/features/provider-sites/provider-site-detail-page.test.tsx`
- 通过：`bunx eslint src/features/provider-sites/provider-sites-page.tsx src/features/provider-sites/provider-site-detail-page.tsx src/features/provider-sites/provider-sites-page.test.tsx src/features/provider-sites/provider-site-detail-page.test.tsx src/features/provider-sites/types.ts`
- 浏览器抽查：`http://localhost:5173/console/provider-sites` 登录后只有“厂商与 API 入口”单一表格；已导入 Xiaomi MiMo 行展示两个协议入口并通过“管理”进入详情。
- 浏览器抽查：`http://localhost:5173/console/provider-sites/2` 默认显示概览，协议入口、模型能力、高级诊断均在独立 Tab；新增协议入口弹窗分为三段。
- 浏览器限制：当前本地数据已将所有 catalog preset 导入，未导入行的导入按钮由定向测试覆盖。
- 浏览器控制台：本轮抽查无 warning/error。

## 测试边界

- 前端：厂商管理列表页与详情页定向测试。
- 类型：`bun run typecheck`。
- Lint：相关 provider-sites 文件。
- 浏览器：`/console/provider-sites` 和 `/console/provider-sites/1` 主流程抽查。

## 关联文档

- `docs/requirements/REQ-20260523-002-provider-site-ui-simplification.md`

## 关联任务

- `tasks/done/TASK-20260523-002-provider-site-preset-display-consistency.md`

## 当前状态

Done
