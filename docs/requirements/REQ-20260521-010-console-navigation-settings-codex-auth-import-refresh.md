# REQ-20260521-010 控制台导航可读性、系统参数汉化与 Codex auth.json 导入保活

状态：Done  
日期：2026-05-21  
上游来源：用户指令“控制台导航栏样式颜色修改、重新分割一二级导航栏、系统参数页面汉化、创建上游凭证支持 Codex auth.json 文件/粘贴导入、注意 Codex RT/AT 保活机制”

## 背景

现役控制台已经完成重复功能下线、深浅主题支持和部分 UI 汉化，但用户进一步指出：

1. 侧栏导航在深色主题下文字颜色与高亮颜色接近，可读性不足。
2. 一二级导航仍存在层级拆分不自然的问题，部分一级栏目只有单个二级菜单。
3. 系统参数页面仍有英文或内部字段名直接暴露给用户。
4. 上游凭证创建入口需要承接 Codex `auth.json` 导入，既支持文件路径批量导入，也支持复制粘贴 JSON 内容。
5. Codex 账号保活需要明确 AT/RT 口径：AT 可用于当前访问；RT 非必填，但存在时应参与刷新与保活。

## 目标

- 提升 Console 侧栏在 dark/light 双主题下的 active、hover、分组标题和普通项可读性。
- 重新梳理导航一级/二级分组，合并只有单个二级菜单的孤立栏目，减少无意义层级。
- 完成系统参数页面剩余英文、camelCase 和内部字段名展示的汉化。
- 在上游凭证创建流程中提供 Codex `auth.json` 导入模式，支持批量文件路径和粘贴内容两种输入。
- 保持 Codex `auth.json` 导入的敏感内容不落文档、不进测试快照、不进入日志；RT 缺失时不阻断导入，RT 存在时标记可刷新并进入保活口径。

## 范围

- `web/src/app/navigation.ts`
- `web/src/components/app/app-shell.tsx`
- `web/src/features/settings/`
- `web/src/features/credentials/`
- `web/src/features/accounts/codex-onboarding-page.tsx`
- `web/src/lib/api.ts`
- Codex `auth.json` 导入相关后端 Admin API、请求 DTO、service 与测试

## 非目标

- 不恢复“官方账号运行态”独立控制台产品面。
- 不删除 `vector/files/file_search` 主线支撑 API。
- 不把真实 `auth.json`、AT、RT 或 API Key 写入仓库、文档、测试 fixture 或日志。
- 不引入完整 i18n 框架；本轮仍以现役中文单语界面为准。

## 风险

- 导航重分组若只改导航不改路由测试，可能留下命令面板、面包屑或旧入口断言漂移。
- 系统参数页面如果误翻译技术值、枚举或配置 key，会影响排障可读性。
- `auth.json` 导入若没有区分文件路径和粘贴内容，容易造成用户把本地路径当 JSON 或把 JSON 当路径提交。
- RT 非必填但可保活的语义若后端和前端不一致，可能让可刷新账号被误判为静态 AT。

## 验收标准

1. Console 侧栏在 dark/light 下 active 项文字、图标、背景和边框对比清晰。
2. 导航一级分组不再保留无意义的单子项孤岛，现役栏目按工作流重新归并。
3. 系统参数页面用户可见英文和内部字段名完成汉化，技术术语与配置 key 保持可辨认。
4. 上游凭证创建入口内可选择 Codex `auth.json` 导入，并支持批量文件路径与粘贴 JSON 内容。
5. Codex `auth.json` 导入时 AT 必须存在；RT 可选，存在时前后端展示为可刷新/可保活，不存在时不阻断导入。
6. `bun run typecheck` 通过，相关前端 vitest 通过。
7. 后端如涉及 DTO/service 改动，相关 Java 测试或编译验证通过。

## 测试边界

- 前端：`bun run typecheck`
- 前端：`navigation`、`layout`、`system-settings`、`credentials`、`codex-onboarding` 相关定向 vitest
- 后端：Codex `auth.json` 导入请求与 service 相关定向测试或编译
- 检索：系统参数英文残留、导航孤立分组、敏感 token 明文残留

## 实现结果

- Console 侧栏改为 `sidebar-primary`/`sidebar-primary-foreground` 高亮配色，dark 默认主题下 active 项文字和背景对比清晰。
- 导航重新按“智能运维、接入与模型、路由治理、观测记录、用户与计费、系统工具、变更维护、集成扩展”归组，消除现役导航中的单子项孤岛。
- 系统参数页完成剩余英文标题和内部字段名汉化，例如 `Prefix Affinity`、`Fingerprint Max Tokens`、`Upstream Runtime` 等已改为中文标签。
- 上游凭证创建入口新增 `Codex auth.json` 类型，支持多行文件路径批量导入、粘贴 JSON 和选择多个本地 JSON 文件。
- 后端 `AccountImportAuthJsonRequest` 支持 `authJsonContent`、`authJsonFilePath`、`authJsonFilePaths`；AT 仍必填，RT 可选，无 RT 时为 `ACCESS_ONLY`，有 RT 时为 `READY` 并进入刷新/保活口径。
- 修复上游凭证软删除后被数据库唯一索引阻断重建的问题：相同 fingerprint 的软删除凭证会恢复复用，避免 Gemini key 重新导入失败。
- 已将用户提供的 15 个 Codex `auth.json` 账号导入数据库；15 个均带 RT，刷新状态为 `READY`。
- 已将用户提供的 5 个 Gemini AI Studio key 导入数据库，归入 `Gemini AI Studio` 分组。
- 未将真实 `auth.json`、AT、RT 或 API Key 写入仓库文档、测试 fixture 或页面摘要。

## 验证结果

- `npm test -- --run src/features/credentials/credentials-page.test.tsx src/app/navigation.test.ts src/features/settings/system-settings-page.test.tsx`
- `npm run typecheck`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.AccountAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests"`
- 浏览器验证：`http://localhost:5173/console/credentials` 默认 dark，侧栏 active 项对比清晰，创建上游凭证弹窗可切换 `Codex auth.json` 并进入文件路径导入面板。
- 敏感扫描：未发现真实 token/key 连续明文进入本轮新增仓库内容。

## 关联文档

- [REQ-20260521-004](./REQ-20260521-004-upstream-credential-entry-and-official-account-clarity.md)
- [REQ-20260521-007](./REQ-20260521-007-ui-chinese-only-localization.md)
- [REQ-20260521-008](./REQ-20260521-008-system-theme-dark-default-light-dual-mode.md)
- [REQ-20260521-009](./REQ-20260521-009-ops-overview-navigation-consolidation.md)
