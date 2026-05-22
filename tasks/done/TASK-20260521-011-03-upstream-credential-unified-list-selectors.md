# TASK-20260521-011-03 上游凭证统一列表与可搜索选择器

状态：Done  
优先级：Critical  
上游来源：[TASK-20260521-011](./TASK-20260521-011-console-surface-prune-logo-credential-redis.md)

## 任务类型

子任务

## 背景

上游凭证页当前只读取 `/admin/credentials`，因此只能看到 Gemini 静态凭证，看不到已经导入到 `/admin/accounts` 的 Codex `auth.json` 账号。用户同时要求支持模型不能手填，代理 ID、指纹 ID 等应从已有数据中模糊搜索选择。

## 目标

- 上游凭证页合并展示静态凭证和 Codex 账号。
- 凭证表格保持单行，操作收敛到详情弹窗。
- 支持模型改为搜索 + 勾选选择。
- 代理 ID 和 TLS 指纹 ID 改为模糊搜索下拉选择。

## 非目标

- 不把 Codex 账号强行改写成静态凭证表记录。
- 不在前端展示真实 secret、AT、RT。
- 不一次性改完全系统所有 ID 输入框；本轮优先上游凭证主路径。

## 输入

- `web/src/features/credentials/credentials-page.tsx`
- `web/src/features/credentials/types.ts`
- `web/src/features/accounts/account-group-detail-page.tsx`
- `web/src/features/network/proxies-page.tsx`
- `web/src/features/network/tls-profiles-page.tsx`
- `/admin/credentials`、`/admin/accounts/group/{groupId}`、`/admin/network/proxies`、`/admin/network/tls-profiles`

## 输出

统一列表、详情弹窗、模型勾选器、代理/TLS 搜索下拉、测试更新。

## 验收标准

- [x] Codex 账号和 Gemini 凭证都出现在“已录入凭证”。
- [x] 表格操作列只有详情入口或不拉高行高的扁平操作。
- [x] 创建和编辑时支持模型不再通过一行一个模型手填。
- [x] 创建和编辑时代理、TLS 指纹从列表搜索选择。

## 测试边界

- `web/src/features/credentials/credentials-page.test.tsx`
- `npm run typecheck`
- 浏览器验证真实数据库中的 15 + 5 条数据展示

## 当前状态

已完成。已新增统一 inventory 后端接口与前端统一表格，凭证页定向测试和 typecheck 通过。
