# TASK-20260523-004 厂商目录框线层级拆解

## 任务类型

子任务

## 背景

来源：`docs/requirements/REQ-20260523-003-provider-catalog-frame-flattening.md`

厂商目录界面在单一目录收敛后仍保留了多层视觉容器：外层 `PageSection`、内部圆角边框表格容器、表格行分隔和分页边框。用户反馈界面框线嵌套太多，需要拆解为更轻的扁平表格区。

## 目标

- 将厂商目录从卡片式 `PageSection` 改为页面内扁平 section。
- 去掉表格外层圆角边框容器，保留表头底线和行分隔。
- 给分页组件增加可选 className，以便厂商目录分页去掉额外边框。
- 保持原有交互和测试断言。

## 非目标

- 不改变厂商目录数据合并逻辑。
- 不改变导入、刷新、删除、管理路由。
- 不全局修改所有分页默认样式。

## 上游来源

- `docs/requirements/REQ-20260523-003-provider-catalog-frame-flattening.md`

## 输入

- 厂商目录截图反馈。
- 当前 `provider-sites-page.tsx`。

## 输出

- 扁平化后的厂商目录 UI。
- 定向验证记录。
- 文档与任务索引回写。

## 影响范围

- `web/src/features/provider-sites/provider-sites-page.tsx`
- `web/src/components/app/table-pagination.tsx`
- `web/src/features/provider-sites/provider-sites-page.test.tsx`
- `docs/index.md`
- `tasks/index.md`

## 依赖

- 现有 `PaginatedRows` 分页组件。
- 现有厂商目录行模型。

## 风险

- 去掉框线后信息密度变高，需要保留足够的行分隔。
- 分页样式扩展必须保持默认行为不变，避免影响其他表格。

## 验收标准

- [x] 厂商目录区域不再使用卡片嵌套圆角表格框。
- [x] 表格表头和行仍清晰可扫。
- [x] 目录操作按钮保持不变。
- [x] 类型检查、测试、eslint 和浏览器抽查完成。
- [x] 文档与任务状态回写。

## 测试边界

- 前端：厂商管理列表页定向测试。
- 类型：`bun run typecheck`。
- Lint：相关 provider-sites 和 table-pagination 文件。
- 浏览器：`/console/provider-sites` 首屏视觉抽查。

## 关联文档

- `docs/requirements/REQ-20260523-003-provider-catalog-frame-flattening.md`

## 关联任务

- `tasks/done/TASK-20260523-003-provider-site-ui-simplification.md`

## 实现结果

- `provider-sites-page.tsx` 中的厂商目录列表已从 `PageSection` 改为扁平 `section`。
- 表格外层只保留 `overflow-x-auto`，去掉内层圆角边框容器。
- 表头和行通过横向分隔线保持扫描性。
- `PaginatedRows` 支持传入 `paginationClassName`，厂商目录分页去掉额外边框，其他页面默认样式不受影响。

## 验证记录

- `bun run typecheck`
- `bun run test -- --run src/features/provider-sites/provider-sites-page.test.tsx src/components/app/table-pagination.test.tsx`
- `bunx eslint src/features/provider-sites/provider-sites-page.tsx src/features/provider-sites/provider-sites-page.test.tsx src/components/app/table-pagination.tsx src/components/app/table-pagination.test.tsx`
- 浏览器验证：`http://127.0.0.1:5173/console/provider-sites`
  - 目标区域无卡片祖先。
  - 表格外层无圆角和边框类。
  - 管理、刷新、删除按钮仍可见。
  - 控制台 error 日志为空。

## 当前状态

Done
