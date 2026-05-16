# REQ-20260513-002 高优缺口闭环实施批次

状态：Done  
日期：2026-05-13  
关联报告：

- [REP-20260513 参考项目、翻译能力、后台与门户完整度复核](../reports/REP-20260513-reference-translation-admin-portal-audit.md)

关联任务：

- [TASK-20260513-002 主流 API 翻译 Conformance Matrix 与缺口硬化](../../tasks/done/TASK-20260513-002-mainstream-api-translation-conformance-matrix.md)
- [TASK-20260513-003 Admin Console 菜单精简与无效运维能力下线](../../tasks/done/TASK-20260513-003-admin-console-menu-simplification-ops-prune.md)
- [TASK-20260513-004 客户门户完整度补齐](../../tasks/done/TASK-20260513-004-portal-customer-completeness-hardening.md)

## 背景

2026-05-13 深度复核确认，当前项目主线服务端能力已经领先多数参考项目，但仍有三个高优缺口会直接影响“可解释、易使用、可交付”的产品完整度：主流 API 翻译支持边界、后台菜单与无效运维能力收敛、客户门户自助能力补齐。

## 目标

- 为主流 API 翻译能力建立可展示、可测试的 Conformance Matrix。
- 删除或隐藏 Admin Console 中无明确业务语义的空间/环境切换，并重排默认菜单。
- 补齐客户门户安全中心、用量、服务状态、订单和 Codex 接入配置等自助页面。
- 完成对应测试与浏览器 smoke，回写任务状态。

## 非目标

- 不在本批次内实现全部 Provider 专属 Adapter。
- 不在本批次内实现真实支付通道。
- 不在本批次内删除后端低频运维 API，只收敛默认前端入口。
- 不在本批次内实现 Public Site、Docs、Pricing、Status 公开入口。

## 方案

1. 后端增加 Translation Conformance Matrix 的事实源 API，并补充测试。
2. 前端在 Native Compatibility 或 Workbench 暴露矩阵，明确 `native`、`emulated`、`lossy`、`unsupported`。
3. Admin Shell 删除默认 Context Switcher 与无业务含义 badge，导航改为用户任务导向分组。
4. Portal 增加安全中心、用量、服务状态、订单和 Codex 接入相关页面，并接入已有 Portal API。

## 风险

- 前端菜单重排可能影响旧测试和用户记忆路径，需要保留必要路由与重定向。
- Portal 安全中心涉及 MFA、Passkey、OAuth identities，操作要避免误触发高风险变更。
- Conformance Matrix 不能过度承诺，需要准确标记 lossy/unsupported。

## 验收标准

- 三个任务的实现结果、测试结果和遗留问题均回写任务文件。
- `tasks/index.md` 中三项任务状态与真实进度一致。
- 后端测试覆盖 Conformance Matrix API。
- 前端构建或类型检查通过。
- 浏览器 smoke 覆盖 Console 菜单与 Portal 新页面。

## 验收结果

- 已新增主流 API 翻译 Conformance Matrix，并在 Native 兼容页展示。
- 已删除 Admin Shell 默认 Workspace/Environment/Tenant Slot 切换，导航重排为更清晰的用户任务分组，低频运维入口从默认菜单移除。
- 已新增 Portal 安全中心、用量明细、服务状态、订单页面，并接入现有 Portal API。
- 已修复 Portal dev proxy 与前端路由冲突，补充 `/portal` API 代理 bypass。
- 已将 Ops Realtime Provider 收敛到 Console 布局，避免 Portal 页面连接后台 WebSocket。
- 验证通过：
  - `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.NativeCompatibilityServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.TranslationExplainAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.TranslationExplainServiceTests"`
  - `bun run typecheck`
  - `bun run lint`
  - `bun run test -- src/app/navigation.test.ts src/app/layout.test.tsx src/features/portal/portal-home-page.test.tsx`
  - Browser smoke：Console Native Compatibility、Portal Security/Usage/Status/Orders。
