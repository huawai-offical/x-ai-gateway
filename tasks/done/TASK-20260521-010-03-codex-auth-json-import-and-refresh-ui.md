# TASK-20260521-010-03 上游凭证入口 Codex auth.json 导入与保活口径

状态：Done  
优先级：High  
上游来源：[TASK-20260521-010](./TASK-20260521-010-console-navigation-settings-codex-auth-import-refresh.md)

## 任务类型

子任务

## 背景

用户要求创建上游凭证时，如果选择 Codex `auth.json` 导入路径，需要提供导入文件路径；支持批量导入文件，也支持复制粘贴 `auth.json` 内容。同时需要注意 Codex 账号保活机制：AT 必须满足当前访问，RT 非必填，但存在时应执行刷新/保活。

## 目标

- 在上游凭证创建入口中加入 Codex `auth.json` 导入模式。
- 支持多行文件路径批量导入。
- 支持粘贴单个或多个 `auth.json` JSON 内容。
- 前后端保持 AT/RT 口径：AT 必填，RT 可选；有 RT 时可刷新/可保活。
- 后端导入链路接受前端输入并保持敏感字段脱敏。

## 非目标

- 不保存真实 token 样本到仓库。
- 不恢复官方账号运行态独立页面。
- 不实现新的 Provider OAuth 授权流程。

## 输入

- `web/src/features/credentials/credentials-page.tsx`
- `web/src/features/accounts/codex-onboarding-page.tsx`
- `web/src/lib/api.ts`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/AccountImportAuthJsonRequest.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/AccountAdminController.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/AccountAdminService.java`
- Codex 导入相关测试

## 输出

- 上游凭证创建弹窗内的 Codex `auth.json` 导入 UI。
- 支持文件路径和粘贴内容的请求模型。
- RT 可选、有则刷新/保活的展示与导入结果。

## 影响范围

- 上游凭证创建入口。
- Codex 账号导入链路。
- Codex 账号刷新/保活状态展示。

## 依赖

- [TASK-20260521-004](../in-progress/TASK-20260521-004-upstream-credential-entry-and-official-account-clarity.md)
- [TASK-20260508-004](./TASK-20260508-004-codex-auth-json-import-dedupe-sanitization.md)

## 风险

- 粘贴 JSON 和路径批量导入共享同一入口时，校验提示需要足够明确。
- RT 可选语义必须前后端一致，否则会误判可保活能力。

## 验收标准

- [x] 创建上游凭证时可选择 Codex `auth.json` 导入模式。
- [x] 可输入多个本地 `auth.json` 文件路径并批量导入。
- [x] 可粘贴 `auth.json` 内容导入。
- [x] AT 缺失会被拦截；RT 缺失不阻断。
- [x] RT 存在时导入结果展示为可刷新/可保活。
- [x] 敏感 token 不出现在文档、日志、测试断言和页面摘要中。

## 测试边界

- 前端：`credentials` 与 `codex-onboarding` 定向 vitest
- 后端：Codex `auth.json` 导入相关定向测试或编译
- 静态检索：真实 token 明文与敏感字段泄露

## 实现结果

- `AccountImportAuthJsonRequest` 新增 `authJsonContent`、`authJsonFilePath`、`authJsonFilePaths`，并移除 DTO 层 `accessToken` 强制校验，由 service 在解析 auth.json 后统一校验 AT。
- `AccountAdminService` 支持读取服务器可访问的本地 auth.json 路径，也支持粘贴 JSON 内容；RT 可选，解析到 RT 时默认 `READY`，否则 `ACCESS_ONLY`。
- “新增上游凭证”弹窗新增 `Codex auth.json` 类型，可在文件路径和粘贴 JSON 两种模式间切换；粘贴模式还支持选择多个本地 `.json` 文件。
- 凭证创建逻辑支持恢复相同 fingerprint 的软删除凭证，避免 API key 重新导入被数据库唯一索引阻断。
- 已真实导入 15 个 Codex `auth.json` 账号到 `CODEX_OAUTH` 分组，15 个均为 `READY`。
- 已真实导入 5 个 Gemini AI Studio key 到 `Gemini AI Studio` 分组。

## 验证结果

- `npm test -- --run src/features/credentials/credentials-page.test.tsx`
- `npm run typecheck`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.AccountAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests"`
- 浏览器验证创建弹窗可切换 `Codex auth.json` 并进入文件路径导入面板。
- 敏感扫描未发现真实 AT/RT/API Key 连续明文进入本轮新增仓库内容。

## 当前状态

已完成

