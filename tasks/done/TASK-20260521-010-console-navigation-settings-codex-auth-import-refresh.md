# TASK-20260521-010 控制台导航可读性、系统参数汉化与 Codex auth.json 导入保活

状态：Done  
优先级：High  
上游来源：[REQ-20260521-010](../../docs/requirements/REQ-20260521-010-console-navigation-settings-codex-auth-import-refresh.md)

## 任务类型

父任务

## 背景

本轮承接用户对控制台可读性、导航层级、系统参数汉化、上游凭证创建入口和 Codex 账号保活的连续反馈。前置任务已完成主题支持、重复入口下线和部分汉化，但仍需要把可用性问题压到现役主路径上。

## 目标

- 修复 Console 侧栏高亮与文字颜色接近的问题。
- 重新拆分一二级导航，合并只有单个二级菜单的孤立栏目。
- 汉化系统参数页面剩余英文与内部字段名展示。
- 在上游凭证创建流程中接入 Codex `auth.json` 文件路径批量导入和粘贴导入。
- 固定 Codex AT/RT 保活口径：AT 必须存在，RT 可选，有 RT 时进入刷新/保活能力展示。

## 非目标

- 不恢复独立“官方账号运行态”页面。
- 不扩大非核心 API 支持面。
- 不落库或提交真实敏感凭证样本。

## 输入

- `web/src/app/navigation.ts`
- `web/src/components/app/app-shell.tsx`
- `web/src/features/settings/`
- `web/src/features/credentials/`
- `web/src/features/accounts/codex-onboarding-page.tsx`
- `web/src/lib/api.ts`
- 后端 Codex `auth.json` 导入相关 DTO、Controller、Service 与测试

## 输出

- 可读性更好的侧栏样式与重分组导航。
- 系统参数页面中文单语界面。
- 上游凭证创建入口内的 Codex `auth.json` 导入模式。
- AT/RT 导入与保活状态一致的前后端实现。
- 回写后的需求、任务与索引记录。

## 影响范围

- Console 信息架构与导航体验。
- 系统参数页面展示。
- 上游凭证创建弹窗与 Codex 导入链路。
- Codex 账号刷新/保活状态展示与导入校验。

## 依赖

- [TASK-20260521-004](../in-progress/TASK-20260521-004-upstream-credential-entry-and-official-account-clarity.md)
- [TASK-20260521-007](../in-progress/TASK-20260521-007-ui-chinese-only-localization.md)
- [TASK-20260521-008](./TASK-20260521-008-system-theme-dark-default-light-dual-mode.md)
- [TASK-20260521-009](./TASK-20260521-009-ops-overview-navigation-consolidation.md)

## 风险

- 当前工作区存在大量并行改动，需要避免误回退无关变更。
- 导航改动会牵动路由、命令面板和测试断言。
- Codex `auth.json` 导入必须严格避免真实 AT/RT 泄露到日志、文档或测试输出。

## 验收标准

- [x] 侧栏 active/hover/普通项在 dark/light 下可读。
- [x] 导航分组消除无意义单子项孤岛。
- [x] 系统参数页面剩余英文与内部字段名完成汉化。
- [x] 上游凭证创建支持 Codex `auth.json` 批量文件路径导入。
- [x] 上游凭证创建支持 Codex `auth.json` 粘贴导入。
- [x] Codex 导入 AT 必填、RT 可选；RT 存在时进入可刷新/保活展示。
- [x] `bun run typecheck` 通过。
- [x] 定向 vitest 和必要后端验证通过。

## 测试边界

- 前端：`bun run typecheck`
- 前端：`navigation`、`layout`、`settings`、`credentials`、`codex-onboarding` 定向 vitest
- 后端：Codex `auth.json` 导入相关定向测试或编译
- 静态检索：英文残留、导航孤岛、敏感 token 明文

## 关联任务

- [TASK-20260521-010-01](./TASK-20260521-010-01-console-sidebar-navigation-restyle-and-regroup.md)
- [TASK-20260521-010-02](./TASK-20260521-010-02-system-settings-localization.md)
- [TASK-20260521-010-03](./TASK-20260521-010-03-codex-auth-json-import-and-refresh-ui.md)

## 实现结果

- 已调整 Console 侧栏 active、hover、图标和分组标题色，默认 dark 下 active 项为高对比配色。
- 已将导航归并为 8 个一级分组，并把错误规则、TLS 指纹、网络拨测、运行手册等页面纳入更贴近工作流的分组。
- 已完成系统参数页本轮英文残留汉化。
- 已在“新增上游凭证”弹窗加入 `Codex auth.json` 类型，支持文件路径批量、粘贴 JSON 和本地 JSON 文件选择。
- 后端导入请求支持 auth.json 内容和文件路径；RT 可选，有 RT 时刷新状态为 `READY`，无 RT 时为 `ACCESS_ONLY`。
- 已修复上游凭证软删除后相同 fingerprint 无法重新导入的问题。
- 已真实导入 15 个 Codex `auth.json` 账号和 5 个 Gemini AI Studio 凭证到数据库。

## 验证结果

- `npm test -- --run src/features/credentials/credentials-page.test.tsx src/app/navigation.test.ts src/features/settings/system-settings-page.test.tsx`
- `npm run typecheck`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.AccountAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests"`
- 浏览器验证：`/console/credentials` 默认 dark、侧栏 active 对比清晰、Codex auth.json 文件路径导入入口可见。
- 敏感扫描未发现真实 AT/RT/API Key 连续明文进入本轮新增仓库内容。

## 当前状态

已完成


