# TASK-20260521-004 上游凭证入口统一与官方账号概念澄清

## 任务类型

父任务 + 本轮实现子任务

## 背景

来源：`docs/requirements/REQ-20260521-004-upstream-credential-entry-and-official-account-clarity.md`

用户要求继续收紧接入侧概念边界：

1. 导入 Codex 凭证和创建凭证放在一个入口。
2. 官方账号会话的功能定位需要澄清，避免与上游凭证形成重复概念。
3. 页面里用于解释这些概念边界的说明性文案全部删除，不再保留。

## 目标

- 把 `上游凭证` 页的新增动作收敛为单入口。
- 让官方账号页面退回“导入后运行态管理”定位。
- 通过导航、页面标题、命令搜索减少概念割裂。
- 删除现役页面中为解释产品边界而新增的说明性正文，改为只保留必要操作、状态和错误反馈。

## 非目标

- 不合并 `UpstreamCredentialEntity` 与 `UpstreamAccountEntity`。
- 不改动官方账号 quota refresh、runtime reset、Codex smoke 等后端能力。
- 不扩展新的 provider 导入流程。

## 输入

- `web/src/features/credentials/credentials-page.tsx`
- `web/src/features/accounts/oauth-sessions-page.tsx`
- `web/src/app/navigation.ts`
- `web/src/components/app/app-shell.tsx`
- 现役控制台页面中的说明性提示块与默认解释文案
- 相关前端测试文件

## 输出

- 单入口的上游凭证新增动作。
- 降歧义后的导航与页面命名。
- 删除解释性正文后的页面展示。
- 更新后的需求/任务索引与完成记录。

## 影响范围

- 控制台接入信息架构。
- 上游凭证页与官方账号页的用户心智。
- 前端测试快照与文案断言。

## 依赖

- 现有 `/admin/credentials` 创建链路。
- 现有 `/admin/accounts/import-auth-json` 与 `/admin/accounts` 运行态链路。

## 风险

- 入口统一过于轻量会让用户仍然找不到官方账号运行态页面。
- 文案调整过猛如果误伤错误提示或校验反馈，会影响操作可理解性。

## 验收标准

- [ ] `上游凭证` 页只保留一个新增入口。
- [ ] 单入口内可清楚区分“普通密钥/Secret 凭证创建”和“Codex 官方账号导入”。
- [ ] 导航与页面信息架构明确官方账号页是运行态管理，而不是新的凭证类型。
- [ ] 现役页面中这类解释性正文已删除，仅保留必要标题、操作、状态与错误反馈。
- [ ] `bun run typecheck` 通过。
- [ ] 相关 vitest 用例通过。

## 测试边界

- 前端：`bun run typecheck`
- 前端：针对 `navigation`、`credentials-page`、`oauth-sessions-page` 的 vitest
- 前端：针对当前删改页面的文案回归检索
- 不运行后端集成测试；本轮未改动后端服务逻辑。

## 当前状态

进行中

## 2026-05-21 口径补充

- 本任务保留，是因为 `官方账号` 相关后端导入、quota refresh、smoke 与运行态治理链路仍可能继续存在。
- 本任务不等于继续保留独立的 `官方账号运行态` 控制台产品面；当前目标是把它收敛为上游凭证导入后的后端运行态管理概念。
- 当前进一步收口为：不再在控制台现役页面用正文解释这些概念边界，只通过入口收敛、命名和页面结构表达。
