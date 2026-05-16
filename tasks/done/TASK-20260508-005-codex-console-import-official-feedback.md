# TASK-20260508-005 Codex 前端导入流官方化与结果反馈

状态：Done  
优先级：High  
排期：P0-02  
来源：User Request / REQ-20260508-002  
关联需求：[REQ-20260508-002 Codex 导入去重、可信前端与审计追踪闭环](../../docs/requirements/REQ-20260508-002-codex-import-dedupe-audit-closure.md)

## 背景

账号池详情页已有 `auth.json` 导入向导，但 Codex pool 仍走通用账号导入路径。为了让真实 Codex 账号导入具备 quota、Smoke 和身份去重语义，需要前端改走官方账号 API，并展示更明确的脱敏结果。

## 目标

- Codex pool 导入调用 `/admin/accounts/official/import`。
- 非 Codex pool 保持 `/admin/accounts/import-auth-json`。
- 导入完成后展示账号 ID、externalAccountId、quotaStatus、routeEligible 和结果摘要。
- 预览与错误态不展示明文 token。

## 范围

- `web/src/features/accounts/account-pool-detail-page.tsx`
- 对应 Vitest 覆盖

## 非目标

- 不在前端解析 JWT 作为最终身份事实源。
- 不默认触发 live smoke。

## 验收标准

- Codex 导入请求体包含 `accountType=CODEX`、poolId 和 raw metadataJson，由后端负责脱敏落库。
- UI 显示导入成功结果。
- 预览中 token 被 mask。

## 实现记录

- Codex pool 导入提交改为调用 `/admin/accounts/official/import`，请求包含 `accountType=CODEX`、poolId、模型列表和 `refreshQuotaAfterImport=true`。
- 非 Codex pool 继续使用 `/admin/accounts/import-auth-json`。
- 导入成功后在账号池详情页展示脱敏保存提示、账号 ID、账号名称、外部身份和路由状态。
- 前端 `auth.json` 候选解析补充 `codex`、`codex_oauth`、`openai_oauth` 等 Codex 常见节点。

## 测试/验证

- `account-pool-detail-page.test.tsx` 覆盖 Codex pool 官方导入、非 Codex pool 通用导入、导入结果反馈。
- 前端定向测试、typecheck、build、定向 ESLint 通过。

## 遗留问题

- 本批未在浏览器中提交真实或假 `auth.json` 入库，避免污染当前长期测试数据库；前端交互路径由 Vitest 覆盖。
