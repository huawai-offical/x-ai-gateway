# TASK-20260521-001 控制台导航、凭证、代理与模型目录 UX 收紧

## 背景

来源：`docs/requirements/REQ-20260521-001-console-navigation-credential-proxy-model-ux.md`

## 目标

完成控制台一级导航折叠、Codex 归并接入、凭证编辑入口、代理主流格式支持、Alias 治理位置调整。

## 非目标

不做整站重设计，不重构代理运行时，不调整非相关业务页面。

## 输入

- 用户对导航、凭证、代理、模型目录的 5 点修改要求。
- 当前前端 `web/src/components/app/app-shell.tsx`、`web/src/app/navigation.ts`、`web/src/features/credentials/*`、`web/src/features/network/proxies-page.tsx`、`web/src/features/models/models-page.tsx`。
- 当前后端 `CredentialAdminService`、`NetworkGovernanceService`。

## 输出

- 前端导航与页面交互修改。
- 后端凭证更新与代理 URL 基础校验修改。

## 影响范围

- 控制台侧边栏与搜索入口。
- 上游凭证列表和详情编辑。
- 代理池创建/编辑/拨测。
- 模型目录页面排序。

## 依赖

- 现有 REST API 和 React Query 数据刷新机制。

## 风险

- 不跑全量测试时可能存在未发现的测试快照或断言差异。
- 代理格式支持仍需后续确认运行时 HTTP client 是否完整消费代理配置。

## 验收标准

- 一级菜单分组可折叠。
- Codex 菜单项归入接入。
- 凭证列表提供编辑入口，详情页保存非 secret 字段不再必须重填 secret。
- 代理表单说明并支持 `http`、`https`、`socks`、`socks4`、`socks5`。
- Alias 映射治理位于模型目录列表上方。

## 测试边界

本任务计划先做代码修改，再执行轻量静态检查；如用户要求跳过测试，则仅做 diff 检查。

## 实现结果

- 侧边栏一级菜单支持折叠，展开分组记录到 localStorage。
- Codex 菜单项从独立一级菜单归并到接入组。
- 凭证列表新增编辑入口，凭证详情页 secret 改为可选更新。
- 凭证后端更新逻辑支持不提交 secret 时保留原密钥。
- 代理表单和后端保存/拨测支持 `http`、`https`、`socks`、`socks4`、`socks5`。
- Alias 映射治理区域显示顺序前置到模型目录上方。

## 验证情况

- `bun run typecheck` 通过。
- `.\gradlew.bat compileJava` 通过。

## 当前状态

已完成。
