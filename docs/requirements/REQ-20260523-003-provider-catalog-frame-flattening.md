# REQ-20260523-003 厂商目录框线层级拆解

## 背景

用户反馈厂商目录界面嵌套了太多框。当前“厂商与 API 入口”区域使用外层 `PageSection` 卡片，内部又包一层圆角边框表格，表格行和分页区域继续叠加边框，导致视觉层级过重。

## 目标

- 厂商目录列表从卡片嵌套表格改为扁平列表区。
- 表格只保留必要的横向分隔线和表头底线。
- 分页区域去掉额外边框，降低界面框线噪音。
- 不改变数据、操作路径、导入逻辑和详情页逻辑。

## 范围

- `web/src/features/provider-sites/provider-sites-page.tsx`
- `web/src/components/app/table-pagination.tsx`
- `web/src/features/provider-sites/provider-sites-page.test.tsx`
- `tasks/index.md`
- `docs/index.md`

## 非目标

- 不重做厂商管理整体信息架构。
- 不调整详情页 Tabs。
- 不修改后端接口或数据模型。
- 不影响其他页面默认分页样式，除非调用方显式传入样式。

## 验收标准

- 厂商目录区域不再出现外层大卡片嵌套内层圆角表格框。
- 厂商目录表格仍能清晰区分表头和行。
- 管理、刷新、删除、导入等操作保持不变。
- 类型检查、定向测试、eslint 和浏览器抽查通过或记录限制。

## 当前状态

Done

## 实现结果

- 厂商目录区域从 `PageSection` 卡片改为普通 `section`，取消外层大卡片。
- 表格容器从圆角边框卡片改为仅负责横向滚动的 `overflow-x-auto` 容器。
- 表头和行保留横向分隔线，删除内层表格外框和圆角框。
- `PaginatedRows` 新增可选 `paginationClassName`，厂商目录分页显式去除额外边框；默认分页样式不变。
- 厂商目录标题、表头、管理、刷新、删除、导入等操作语义保持不变。

## 验证记录

- `bun run typecheck`
- `bun run test -- --run src/features/provider-sites/provider-sites-page.test.tsx src/components/app/table-pagination.test.tsx`
- `bunx eslint src/features/provider-sites/provider-sites-page.tsx src/features/provider-sites/provider-sites-page.test.tsx src/components/app/table-pagination.tsx src/components/app/table-pagination.test.tsx`
- 浏览器验证：`http://127.0.0.1:5173/console/provider-sites`
  - 厂商目录目标区域无卡片祖先。
  - 表格外层为 `overflow-x-auto`，无圆角和边框类。
  - 页面可见 Xiaomi MiMo 行及管理、刷新、删除操作。
  - 控制台 error 日志为空。

## 遗留问题

- 本轮只拆解厂商目录列表框线层级；上方筛选摘要区域仍保留 `PageSection` 卡片，属于厂商管理中心的概览与过滤容器，不在本轮范围内。
