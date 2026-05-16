# TASK-20260507-003 Codex 账号池热切换、负载均衡与失败恢复 UI

状态：Done  
优先级：High  
排期：P2-11  
来源：[REP-20260507 Codex 账户反代与 UI/UX 深度差距分析](../../docs/reports/REP-20260507-codex-proxy-uiux-gap-analysis.md)

## 背景

`cli_proxy-master` 支持 active config 热切换、模型路由、weight-based 负载均衡、失败阈值、自动恢复和手动 reset。当前项目有账号池、route policy runtime、账号健康状态和治理页，但 Codex 用户无法在一个页面看到“当前 Codex 正在用哪个账号池、如何热切换、哪些账号被隔离、如何恢复”。

## 目标

- 为 Codex 账号池提供热切换与故障恢复操作面。
- 让管理员能按 Codex 模型、client instance、workspace hint 查看账号选择结果。
- 支持手动禁用/恢复账号、清理失败计数、设置权重或优先级。
- 与 route policy runtime、账号健康和 request/usage log 联动。

## 详细设计

- 后端提供 Codex pool runtime summary API：active pool、candidate accounts、weights、failure counts、cooldown、last selected、last error。
- 前端新增 Codex 账号池视图或在 account pool detail 中增加 `Codex Runtime` tab。
- 操作包括：设为 active、手动隔离、恢复账号、重置失败、运行 preview、运行 smoke。
- preview 输入包括 model、clientFamily、clientInstance、sessionAffinityKey、workspaceHint，输出候选列表和选路原因。
- 所有操作写入 system event，便于审计。

## 验收标准

- 管理员可在 UI 内完成 Codex 账号池切换和失败恢复。
- 候选账号、阻断原因、冷却时间、权重和最近请求可见。
- preview/smoke 能解释为什么选择或跳过某个账号。
- 后端测试覆盖失败阈值、手动隔离、恢复和 preview。

## 风险

- 热切换不能绕过 distributed key 的 allowed client family、model、provider 限制。
- UI 操作必须防止误恢复被安全策略冻结的账号。

## 本批实施设计

- 关联需求：[REQ-20260507-006 第四批最高优先级任务闭环设计](../../docs/requirements/REQ-20260507-006-next3-codex-onboarding-portal-runtime-closure.md)
- 在账号池详情页新增 Codex Runtime 面板，展示候选账号、冻结/冷却/失败计数、最近错误、quota、成功率和最近使用。
- 手动隔离调用现有账号冻结 API；恢复账号新增最小后端 runtime reset API，清理 frozen、healthy、lastError、refreshFailureCount、cooldown 和 nextRefreshAfter。
- dry-run smoke 调用现有 Codex App API responses smoke；quota 刷新调用现有官方账号 quota-refresh。
- UI 显示操作影响范围、失败原因和下一步，避免把安全冻结误恢复成普通健康账号。
- 测试覆盖 Runtime 面板、隔离、恢复和 smoke 操作。

## 进度记录

- 2026-05-07：进入第四批最高优先级任务闭环，开始实现 Codex 账号池运营恢复面。
- 2026-05-07：账号池详情新增 Codex Runtime 面板，展示候选、可路由、冻结、失败、quota、成功率、缓存命中和最近错误。
- 2026-05-07：新增后端 runtime reset API，恢复普通运行态故障时清理 frozen、healthy、lastError、refreshFailureCount、cooldown 和 nextRefreshAfter，同时保留 encrypted secret。
- 2026-05-07：Runtime 面板接入隔离账号、重置运行态、刷新 quota、dry-run smoke 四个操作。
- 2026-05-07：补充前端 Runtime 操作测试和后端 runtime reset 回归测试。

## 验证记录

- `bun run test -- src/features/accounts/codex-onboarding-page.test.tsx src/features/accounts/account-pool-detail-page.test.tsx src/features/portal/portal-home-page.test.tsx`
- `bun run typecheck`
- `bun run build`
- `./gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.AccountAdminServiceTests"`

## 交付结果

- 更新 `web/src/features/accounts/account-pool-detail-page.tsx`。
- 更新 `web/src/features/accounts/account-pool-detail-page.test.tsx`。
- 更新 `src/main/java/com/prodigalgal/xaigateway/admin/api/AccountAdminController.java`。
- 更新 `src/main/java/com/prodigalgal/xaigateway/admin/application/AccountAdminService.java`。
- 更新 `src/test/java/com/prodigalgal/xaigateway/admin/application/AccountAdminServiceTests.java`。

## 后续建议

- 后续可补候选 preview、权重/优先级编辑、system event 审计和批量恢复预检，进一步提升批量操作可信度。
