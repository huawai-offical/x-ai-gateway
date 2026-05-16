# REQ-20260505-002 第六批最高优先级任务闭环设计

状态：Done
创建日期：2026-05-05
关联决策：[ADR-0007](../decisions/ADR-0007-sixth-batch-runtime-and-oauth-scope.md)
关联任务：

- [TASK-20260505-003 X-263 第二轮深度差距补齐总览](../../tasks/done/TASK-20260505-003-linear-x263-second-gap-overview.md)
- [TASK-20260501-028 Redis Route Policy Runtime Store 与跨实例一致性](../../tasks/done/TASK-20260501-028-redis-routing-policy-runtime-store.md)
- [TASK-20260501-027 社交 OAuth Provider 扩展、JWK 缓存与线上 Smoke](../../tasks/done/TASK-20260501-027-social-oauth-provider-expansion-jwk-cache-smoke.md)

## 背景

当前本地 backlog 中最高优先级任务集中在两类：一类是 X-263 第二轮差距的真实剩余范围校准；另一类是已经完成本地骨架但仍缺生产级闭环的运行时和账号安全能力。用户要求按推荐继续推进项目，因此本批选择以下三项：

- X-263 第二轮差距代码态审计。
- Redis Route Policy Runtime Store 与跨实例一致性。
- 社交 OAuth Provider 扩展、JWK 缓存与 smoke checklist。

## 目标

- 校准 X-263 中哪些范围已由 X-264 至 X-280 或当前代码闭环，哪些仍需拆分。
- 将 Route Policy runtime store 从单实例内存态推进为可配置、可替换、可测试的存储抽象，并提供 Redis 实现入口。
- 将社交 OAuth provider 从 Google/GitHub 扩展到 QQ、WeChat、Meta、X，并补齐 key/JWK 缓存、provider 错误标准化和本地 mock contract tests。

## 范围

### X-263 审计

- 对 X-263 列出的 Gateway Cache、Lineage、Operations/Tunings、Ops/Maintenance/Release、Workbench、Secret Export、Ollama Native、测试基线、Operations 路由策略做代码态检查。
- 标记已闭环、部分闭环、未闭环。
- 对仍未闭环项拆分后续本地 backlog。

### Redis Route Policy Runtime Store

- 抽象 `RoutingPolicyRuntimeStore`。
- 保留本地内存实现作为默认和测试 fallback。
- 增加 Redis store，支持 rate window、circuit state、reset 和 TTL。
- Redis 不可用策略先采用可配置 fallback；默认保守回退到本地内存。

### 社交 OAuth Provider 扩展

- 增加 QQ、WeChat、Meta、X provider 枚举、配置和 provider client。
- 为 OIDC/JWKS 类 provider 提供缓存和 kid miss refresh 机制。
- 为 OAuth2 userinfo 类 provider 提供统一 profile 标准化。
- 补充本地 mock contract tests 和 smoke checklist 文档，不保存真实 secret。

## 非目标

- 不恢复线上 Linear/Notion 作为默认事实来源。
- 不在本批内接入真实生产 Redis 集群或真实第三方 OAuth 凭证。
- 不把 X-263 的所有历史 Done 子任务重新实现一遍。
- 不在仓库中保存真实 `clientSecret`、回调域名私有配置或 OAuth access token。

## 风险

- 当前工作区已有大量未提交改动，本批只能在相关文件上增量协作，不能回滚无关变更。
- Redis runtime 需要兼顾多实例一致性与 Redis 不可用时的可预期行为。
- QQ、WeChat、Meta、X 的 OAuth 字段、审核、scope 和可用端点差异较大，线上 smoke 需要真实开发者账号。
- X-263 覆盖面很大，本批以审计和拆分为主，不把所有剩余缺口压进一个实现任务。

## 验收标准

- X-263 审计报告落地到本地，并更新 X-263 任务状态或拆分后续 backlog。
- Route Policy runtime store 有接口抽象、本地内存实现、Redis 实现入口和定向测试。
- 社交 OAuth 新 provider 至少有配置、client、错误标准化和 mock contract tests。
- 相关本地任务文件、需求文档、索引在交付后回写实现结果、测试结果、遗留问题和后续建议。

## 实现结果

- 完成 X-263 代码态审计，报告见 [REP-20260505](../reports/REP-20260505-x263-code-state-audit.md)，并拆分 3 个后续 backlog。
- 完成 Route Policy runtime store 抽象、默认内存实现、Redis 实现、half-open 探测锁、全局/定点 reset。
- 完成社交 OAuth QQ、WeChat、Meta、X provider client，补充 Google JWKS cache 与 X PKCE start 参数。
- 补充社交 OAuth smoke checklist：[testing-social-oauth-smoke](../testing-social-oauth-smoke.md)。

## 验证情况

- 已通过定向后端测试：
  - `RoutingPolicyRuntimeEnforcementServiceTests`
  - `RedisRoutingPolicyRuntimeStoreTests`
  - `GovernanceAdminServiceTests`
  - `SocialOAuthProfileClientTests`
  - `PortalSocialOAuthServiceTests`
- 已对本轮改动文件执行 `git diff --check`，未发现空白错误。

## 遗留问题

- 真实共享 Redis smoke、真实第三方 OAuth smoke 与 ops dry-run 串联已拆到并闭环到 [TASK-20260505-006](../../tasks/done/TASK-20260505-006-redis-oauth-ops-smoke-harness.md)。
- Ollama document/file 真支持、Ops/Maintenance/Release 真实演练证据仍在 backlog。
