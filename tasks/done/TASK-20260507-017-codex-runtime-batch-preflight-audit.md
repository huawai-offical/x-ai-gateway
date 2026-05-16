# TASK-20260507-017 Codex Runtime 批量预检与脱敏审计闭环

状态：Done  
优先级：High  
排期：P2-13  
来源：[REQ-20260507-007 第五批最高优先级任务闭环设计](../../docs/requirements/REQ-20260507-007-next3-observability-ux-preview-closure.md)

## 背景

Codex 账号池已经具备单账号隔离、恢复、quota 刷新和 dry-run smoke。用户进一步强调批量操作的可信性、批量过程容错性，以及重复账号/多份 auth.json 可能导致判定复杂。真实批量动作上线前，需要先把“会操作哪些账号、哪些账号必须阻断、为什么阻断、排障包是否脱敏”做成可验证能力。

## 目标

- 在 Codex Runtime 面板增加批量恢复预检。
- 对候选账号按 safe、blocked、alreadyReady 分类。
- 对权限、策略、安全、禁用类错误默认阻断，避免批量误恢复。
- 输出脱敏审计 JSON，便于长期测试和后续批量动作 API 复用。

## 详细设计

- 前端基于当前账号池运行态生成预检结果，不提交破坏性操作。
- `safe` 候选包括 frozen、cooldown、unhealthy、refreshFailureCount > 0 的账号。
- `blocked` 候选包括 lastErrorMessage 命中 policy、permission、security、forbidden、disabled、revoked 等安全/权限关键词的账号。
- `alreadyReady` 账号进入审计包但不作为操作候选。
- 审计包只包含 accountId、accountName、status、reason、错误摘要和建议动作，不包含 token、auth.json 或完整错误内容。

## 验收标准

- Codex Runtime 面板显示“批量恢复预检”入口。
- 预检弹窗展示 safe、blocked、alreadyReady 计数和候选列表。
- blocked 账号不会被标记为可批量恢复。
- Vitest 覆盖预检弹窗和脱敏审计包关键字段。

## 风险

- 预检不能被误解为已经执行恢复动作，界面文案必须明确“未提交变更”。
- 错误摘要需要脱敏，不能把完整 upstream 错误或凭证信息暴露到审计包。

## 实施记录

- 在 Codex Runtime 面板新增“批量恢复预检”入口。
- 基于当前账号运行态生成 `safe`、`blocked`、`alreadyReady` 三类候选。
- 对 policy、permission、security、forbidden、disabled、revoked、unauthorized 等错误默认阻断。
- 预检弹窗展示分类计数、候选表格和脱敏 `runtime-batch-preflight.redacted.json`。
- 明确标注 dry-run only，不提交任何恢复动作。

## 验证记录

- `bun run test -- src/features/accounts/account-pool-detail-page.test.tsx`：通过，覆盖 Key picker、单账号恢复动作、批量预检和阻断候选。
- `bun run typecheck`：通过。
- `bun run build`：通过。
