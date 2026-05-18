# x-ai-gateway 本地任务索引

## 任务状态

- Backlog：待排期，位于 `tasks/backlog/`
- In Progress：进行中，位于 `tasks/in-progress/`
- Done：已完成，位于 `tasks/done/`

## 当前 Backlog 优先级队列

| 排期 | 任务 | 状态 | 执行定位 |
| --- | --- | --- | --- |
| P0-01 | [TASK-20260508-001 Codex Observability Projection API 与前端直连](done/TASK-20260508-001-codex-observability-projection-api.md) | Done | Codex 观测后端事实源 |
| P0-02 | [TASK-20260508-002 Codex Runtime 批量恢复执行 API、容错与系统事件审计](done/TASK-20260508-002-codex-runtime-batch-recovery-api-audit.md) | Done | 批量操作可信执行 |
| P0-03 | [TASK-20260508-003 Codex 前后端联调 Smoke、后端测试与浏览器回归证据](done/TASK-20260508-003-codex-e2e-smoke-backend-frontend-evidence.md) | Done | 联调证据闭环 |
| P0-01 | [TASK-20260507-002 Codex CLI 请求保真、Session 粘性与反向代理兼容](done/TASK-20260507-002-codex-cli-request-fidelity-sticky-session.md) | Done | Codex 反代协议地基 |
| P0-02 | [TASK-20260507-009 Portal/Admin 路由、身份与产品面边界基线](done/TASK-20260507-009-portal-admin-route-identity-boundary.md) | Done | 产品面边界地基 |
| P0-03 | [TASK-20260507-013 Portal/Admin API 权限隔离、审计与越权回归](done/TASK-20260507-013-portal-admin-permission-audit-regression.md) | Done | 安全边界地基 |
| P0-04 | [TASK-20260507-001 Codex 官方账号真实适配、配额刷新与反代 Smoke](done/TASK-20260507-001-codex-official-account-real-adapter-smoke.md) | Done | 真实账号闭环 |
| P1-05 | [TASK-20260507-014 Portal/Console UI/UX 验收体系与组件硬化](done/TASK-20260507-014-portal-console-ux-acceptance-system.md) | Done | UI 验收基线 |
| P1-06 | [TASK-20260507-011 Admin Console 命名空间迁移与旧路由兼容](done/TASK-20260507-011-admin-console-namespace-legacy-route-migration.md) | Done | Console 路由骨架 |
| P0-05 | [TASK-20260507-016 Codex 真实 auth.json 长期测试账号入库与详测](done/TASK-20260507-016-codex-real-auth-db-long-term-test.md) | Done | 真实测试账号基线 |
| P1-07 | [TASK-20260507-006 管理端 UI 信息架构与角色化工作台重整](done/TASK-20260507-006-admin-ui-information-architecture-workbench.md) | Done | Admin UI 父级收口 |
| P1-08 | [TASK-20260507-012 Admin Console 角色化工作台与导航体系](done/TASK-20260507-012-admin-console-role-workbench-navigation.md) | Done | Admin UI 具体落地 |
| P1-09 | [TASK-20260507-005 Codex 接入向导、Client Instance 与 Deep Link UI 闭环](done/TASK-20260507-005-codex-onboarding-client-instance-deeplink-ui.md) | Done | Codex 接入主路径 |
| P1-10 | [TASK-20260507-010 Community Portal Codex 自助接入与个人用量界面](done/TASK-20260507-010-community-portal-codex-self-service-surface.md) | Done | 社区用户主路径 |
| P2-11 | [TASK-20260507-003 Codex 账号池热切换、负载均衡与失败恢复 UI](done/TASK-20260507-003-codex-account-pool-hot-switch-failover-ui.md) | Done | 管理员运营效率 |
| P2-12 | [TASK-20260507-004 Codex 实时请求、Usage 与过滤命中观测台](done/TASK-20260507-004-codex-realtime-usage-filter-observability.md) | Done | 排障与观测效率 |
| P2-13 | [TASK-20260507-017 Codex Runtime 批量预检与脱敏审计闭环](done/TASK-20260507-017-codex-runtime-batch-preflight-audit.md) | Done | 批量操作可信性 |
| P3-14 | [TASK-20260507-007 前端可用性验收、表单友好性与移动端体验硬化](done/TASK-20260507-007-frontend-usability-form-mobile-hardening.md) | Done | 全局体验收口 |

## 对标增强任务

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260501-000 差距增强总览](done/TASK-20260501-000-gap-enhancement-overview.md) | Done | High | X-281 |
| [TASK-20260501-001 Provider Registry 2.0](done/TASK-20260501-001-provider-registry-2.md) | Done | High | X-282 |
| [TASK-20260501-002 非 Chat 资源族扩展](done/TASK-20260501-002-non-chat-resources.md) | Done | High | X-283 |
| [TASK-20260501-003 Realtime 与 Streaming 真实代理闭环](done/TASK-20260501-003-realtime-streaming-proxy.md) | Done | High | X-284 |
| [TASK-20260501-004 SaaS 计费与支付闭环](done/TASK-20260501-004-billing-payment-loop.md) | Done | High | X-285 |
| [TASK-20260501-005 Portal 用户自助增强](done/TASK-20260501-005-portal-self-service.md) | Done | Medium | X-286 |
| [TASK-20260501-006 编程类账号身份治理](done/TASK-20260501-006-programming-account-identity.md) | Done | Medium | X-287 |
| [TASK-20260501-007 客户端接入包](done/TASK-20260501-007-client-onboarding-pack.md) | Done | Medium | X-288 |
| [TASK-20260501-008 路由策略 2.0](done/TASK-20260501-008-routing-policy-2.md) | Done | High | X-289 |
| [TASK-20260501-009 安全体系增强](done/TASK-20260501-009-security-system.md) | Done | High | Notion 待创建 |
| [TASK-20260501-010 生产部署与升级体系](done/TASK-20260501-010-production-deployment-upgrade.md) | Done | Medium | Notion 待创建 |
| [TASK-20260501-011 监控与账务 rollup](done/TASK-20260501-011-monitoring-billing-rollup.md) | Done | Medium | Notion 待创建 |
| [TASK-20260501-012 国际化、公开文档与兼容性样例](done/TASK-20260501-012-i18n-public-docs-compatibility.md) | Done | Low | Notion 待创建 |
| [TASK-20260501-013 动态 Provider Catalog 与 Conformance Loader](done/TASK-20260501-013-dynamic-provider-catalog.md) | Done | High | 本地拆分 |
| [TASK-20260501-014 Async Media Provider Executors 与任务状态存储](done/TASK-20260501-014-async-media-executors.md) | Done | High | 本地拆分 |
| [TASK-20260501-015 路由策略配置 UI、Retry/Fallback 与熔断模型](done/TASK-20260501-015-routing-policy-config-ui.md) | Done | High | 本地拆分 |
| [TASK-20260501-016 Provider Catalog Marketplace 与签名更新](done/TASK-20260501-016-provider-catalog-marketplace.md) | Done | Medium | 本地拆分 |
| [TASK-20260506-007 Provider Catalog Marketplace 签名 Smoke 与回滚证据](done/TASK-20260506-007-provider-catalog-marketplace-smoke-pack.md) | Done | Medium | TASK-016 本地拆分 |
| [TASK-20260501-017 真实 Video/Music Provider Executors 与产物闭环](done/TASK-20260501-017-real-media-provider-executors.md) | Done | High | 本地拆分 |
| [TASK-20260501-018 路由策略 UI 与 Retry/Fallback 运行时执行](done/TASK-20260501-018-routing-policy-ui-runtime.md) | Done | High | 本地拆分 |
| [TASK-20260501-019 社交 OAuth 真实 Token Exchange 与账号解绑](done/TASK-20260501-019-social-oauth-real-token-exchange.md) | Done | High | 本地拆分 |
| [TASK-20260501-020 Realtime WebSocket Provider Adapter](done/TASK-20260501-020-realtime-websocket-provider-adapters.md) | Done | High | 本地拆分 |
| [TASK-20260501-021 真实支付渠道、签名验签与对账](done/TASK-20260501-021-payment-real-provider-reconcile.md) | Done | High | 本地拆分 |
| [TASK-20260501-022 Passkey/TOTP/验证码/邮箱验证](done/TASK-20260501-022-passkey-totp-captcha-email-verification.md) | Done | High | 本地拆分 |
| [TASK-20260501-023 真实社交 OAuth Provider Client 与 JWK 验签](done/TASK-20260501-023-social-oauth-real-provider-clients.md) | Done | High | 本地拆分 |
| [TASK-20260501-024 真实 Realtime Provider WebSocket Adapter](done/TASK-20260501-024-realtime-real-provider-websocket.md) | Done | High | 本地拆分 |
| [TASK-20260501-025 分布式 Route Policy 熔断/限流执行器](done/TASK-20260501-025-distributed-routing-circuit-rate-limit.md) | Done | High | 本地拆分 |
| [TASK-20260501-026 Passkey/WebAuthn、注册策略与安全审计](done/TASK-20260501-026-passkey-webauthn-registration-policy-audit.md) | Done | High | 本地拆分 |
| [TASK-20260501-027 社交 OAuth Provider 扩展、JWK 缓存与线上 Smoke](done/TASK-20260501-027-social-oauth-provider-expansion-jwk-cache-smoke.md) | Done | High | 本地拆分 |
| [TASK-20260501-028 Redis Route Policy Runtime Store 与跨实例一致性](done/TASK-20260501-028-redis-routing-policy-runtime-store.md) | Done | High | 本地拆分 |
| [TASK-20260505-003 X-263 第二轮深度差距补齐总览](done/TASK-20260505-003-linear-x263-second-gap-overview.md) | Done | High | Linear X-263 |
| [TASK-20260505-004 Ops/Maintenance/Release 真实演练证据补齐](done/TASK-20260505-004-ops-maintenance-release-real-drill-evidence.md) | Done | High | X-263 代码态审计 |
| [TASK-20260505-005 Ollama Native document/file 真支持](done/TASK-20260505-005-ollama-native-document-file-support.md) | Done | High | X-263 代码态审计 |
| [TASK-20260505-006 Redis/OAuth/Ops Smoke Harness 硬化](done/TASK-20260505-006-redis-oauth-ops-smoke-harness.md) | Done | High | X-263 代码态审计 |

## Linear 历史任务归档

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260505-002 Linear 全量历史任务归档](done/TASK-20260505-002-linear-all-issue-history-archive.md) | Done | High | Linear project export |

## 收尾审计任务

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260506-008 剩余任务清零回归审计](done/TASK-20260506-008-backlog-zero-regression-audit.md) | Done | High | User Request |

## 复核新增待办

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260506-009 Provider 生态广度与 Conformance 完善](done/TASK-20260506-009-provider-ecosystem-conformance.md) | Done | High | REP-20260506 |
| [TASK-20260506-010 支付生产闭环完善](done/TASK-20260506-010-production-payment-closure.md) | Done | High | REP-20260506 |
| [TASK-20260506-011 Realtime 与 Media 生产硬化](done/TASK-20260506-011-realtime-media-production-hardening.md) | Done | High | REP-20260506 |
| [TASK-20260506-012 CLI/客户端生态与云端接入工具链补齐](done/TASK-20260506-012-cloud-cli-client-access-tooling.md) | Done | High | REP-20260506 |
| [TASK-20260506-013 文档、i18n 与 OpenAPI 事实源修复](done/TASK-20260506-013-docs-i18n-openapi-truth-source.md) | Done | Medium | REP-20260506 |
| [TASK-20260506-014 CLI 云端代理接入、热切换、过滤与模型路由](done/TASK-20260506-014-cloud-cli-proxy-access-hot-switch-filtering.md) | Done | High | REP-20260506 五项目深度分析 |
| [TASK-20260506-015 AI IDE/CLI 云端账号配额、多实例与插件联动运营面](done/TASK-20260506-015-ai-ide-account-quota-instance-operator-plane.md) | Done | High | REP-20260506 五项目深度分析 |

## 再次深度复核任务

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260506-016 参考项目功能深度复核与任务再生成](done/TASK-20260506-016-reference-depth-recheck-task-generation.md) | Done | High | User Request |

## 再次深度复核新增待办

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260506-017 Provider 真实凭证 Smoke 与价格同步自动化](done/TASK-20260506-017-provider-smoke-pricing-sync.md) | Done | High | REP-20260506 深度再复核 |
| [TASK-20260506-018 支付定时对账、订阅发票与跨币种结算](done/TASK-20260506-018-payment-scheduled-reconcile-invoice-currency.md) | Done | High | REP-20260506 深度再复核 |
| [TASK-20260506-019 Realtime 长连接池与专有 Media Adapter](done/TASK-20260506-019-realtime-pool-media-adapters.md) | Done | High | REP-20260506 深度再复核 |
| [TASK-20260506-020 云端 Request Filter 高级规则、审计与 UI](done/TASK-20260506-020-cloud-request-filter-audit-ui.md) | Done | High | REP-20260506 深度再复核 |
| [TASK-20260506-021 AI IDE/CLI 官方账号导入与配额刷新](done/TASK-20260506-021-ai-ide-account-import-quota-refresh.md) | Done | High | REP-20260506 深度再复核 |
| [TASK-20260506-022 Client Instance 管理与插件/Deep Link 授权下发](done/TASK-20260506-022-client-instance-plugin-deeplink.md) | Done | Medium | REP-20260506 深度再复核 |
| [TASK-20260506-023 OpenAPI 自动生成、SDK 示例与前端 i18n 抽取](done/TASK-20260506-023-openapi-sdk-frontend-i18n.md) | Done | Medium | REP-20260506 深度再复核 |
| [TASK-20260506-024 Linux/systemd 部署、数据管理与迁移兼容](done/TASK-20260506-024-linux-systemd-data-migration.md) | Done | Medium | REP-20260506 深度再复核 |
| [TASK-20260506-025 桌面 Companion、MCP/Skills/Session/Workspace 可行性评估](done/TASK-20260506-025-desktop-companion-local-workflow-evaluation.md) | Done | Low | REP-20260506 深度再复核 |

## Codex 反代与 UI/UX 深度复核

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260507-000 Codex 账户反代与 UI/UX 深度差距分析](done/TASK-20260507-000-codex-proxy-uiux-gap-analysis.md) | Done | High | User Request |
| [TASK-20260507-001 Codex 官方账号真实适配、配额刷新与反代 Smoke](done/TASK-20260507-001-codex-official-account-real-adapter-smoke.md) | Done | High | REP-20260507 |
| [TASK-20260507-002 Codex CLI 请求保真、Session 粘性与反向代理兼容](done/TASK-20260507-002-codex-cli-request-fidelity-sticky-session.md) | Done | High | REP-20260507 |
| [TASK-20260507-003 Codex 账号池热切换、负载均衡与失败恢复 UI](done/TASK-20260507-003-codex-account-pool-hot-switch-failover-ui.md) | Done | High | REP-20260507 |
| [TASK-20260507-004 Codex 实时请求、Usage 与过滤命中观测台](done/TASK-20260507-004-codex-realtime-usage-filter-observability.md) | Done | Medium | REP-20260507 |
| [TASK-20260507-005 Codex 接入向导、Client Instance 与 Deep Link UI 闭环](done/TASK-20260507-005-codex-onboarding-client-instance-deeplink-ui.md) | Done | High | REP-20260507 |
| [TASK-20260507-006 管理端 UI 信息架构与角色化工作台重整](done/TASK-20260507-006-admin-ui-information-architecture-workbench.md) | Done | High | REP-20260507 |
| [TASK-20260507-007 前端可用性验收、表单友好性与移动端体验硬化](done/TASK-20260507-007-frontend-usability-form-mobile-hardening.md) | Done | Medium | REP-20260507 |
| [TASK-20260507-017 Codex Runtime 批量预检与脱敏审计闭环](done/TASK-20260507-017-codex-runtime-batch-preflight-audit.md) | Done | High | REQ-20260507-007 |
| [TASK-20260508-001 Codex Observability Projection API 与前端直连](done/TASK-20260508-001-codex-observability-projection-api.md) | Done | High | REQ-20260508-001 |
| [TASK-20260508-002 Codex Runtime 批量恢复执行 API、容错与系统事件审计](done/TASK-20260508-002-codex-runtime-batch-recovery-api-audit.md) | Done | High | REQ-20260508-001 |
| [TASK-20260508-003 Codex 前后端联调 Smoke、后端测试与浏览器回归证据](done/TASK-20260508-003-codex-e2e-smoke-backend-frontend-evidence.md) | Done | High | REQ-20260508-001 |

## Codex 导入可信性与审计追踪

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260508-004 Codex auth.json 导入去重与脱敏加固](done/TASK-20260508-004-codex-auth-json-import-dedupe-sanitization.md) | Done | High | REQ-20260508-002 |
| [TASK-20260508-005 Codex 前端导入流官方化与结果反馈](done/TASK-20260508-005-codex-console-import-official-feedback.md) | Done | High | REQ-20260508-002 |
| [TASK-20260508-006 Codex 批量恢复审计事件追踪](done/TASK-20260508-006-codex-batch-audit-event-tracing.md) | Done | High | REQ-20260508-002 |

## 文档一致性清理任务

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260508-007 文档状态与任务链接一致性清理](done/TASK-20260508-007-doc-status-link-cleanup.md) | Done | Medium | REQ-20260508-003 |

## 2026-05-13 复核任务

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260513-001 参考项目、翻译能力、后台与门户完整度复核](done/TASK-20260513-001-reference-translation-admin-portal-audit.md) | Done | High | REQ-20260513-001 |

## 2026-05-13 复核新增待办

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260513-002 主流 API 翻译 Conformance Matrix 与缺口硬化](done/TASK-20260513-002-mainstream-api-translation-conformance-matrix.md) | Done | High | REQ-20260513-002 |
| [TASK-20260513-003 Admin Console 菜单精简与无效运维能力下线](done/TASK-20260513-003-admin-console-menu-simplification-ops-prune.md) | Done | High | REQ-20260513-002 |
| [TASK-20260513-004 客户门户完整度补齐](done/TASK-20260513-004-portal-customer-completeness-hardening.md) | Done | High | REQ-20260513-002 |
| [TASK-20260513-005 Provider/Media/价格同步参考差距补齐](done/TASK-20260513-005-provider-media-pricing-reference-gap-closure.md) | Done | Medium | REQ-20260513-003 |
| [TASK-20260513-006 Public Site、Docs、Pricing、Status 客户入口](done/TASK-20260513-006-public-site-docs-pricing-status-surface.md) | Done | Medium | REQ-20260513-003 |

## 2026-05-14 质量硬化任务

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260514-001 前端路由 HydrateFallback 告警清理](done/TASK-20260514-001-router-hydrate-fallback-cleanup.md) | Done | High | REQ-20260514-001 |
| [TASK-20260514-002 参考项目实现细节深度对比](done/TASK-20260514-002-reference-implementation-detail-comparison.md) | Done | High | REQ-20260514-002 |

## 2026-05-14 实现细节复核新增待办

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260514-003 Provider 长尾 Preset、Web/Search 与 Native Adapter 追平](done/TASK-20260514-003-provider-long-tail-web-search-native-adapter.md) | Done | High | REP-20260514 / REQ-20260514-003 |
| [TASK-20260514-004 专有 Media Task Adapter 与真实产物 Smoke](done/TASK-20260514-004-provider-specific-media-task-adapters-smoke.md) | Done | High | REP-20260514 / REQ-20260514-004 |
| [TASK-20260514-005 官方价格源版本化同步与人工批准快照](done/TASK-20260514-005-provider-pricing-versioned-sync.md) | Done | Medium | REP-20260514 / REQ-20260514-005 |
| [TASK-20260514-006 社区首页体验与主流 API 文档复核](done/TASK-20260514-006-community-home-api-docs-refresh.md) | Done | High | REQ-20260514-006 |
| [TASK-20260514-007 社区首页排版、对比度与链接硬化](done/TASK-20260514-007-community-home-ui-route-hardening.md) | Done | High | REQ-20260514-006 |
| [TASK-20260514-008 主流厂商 API/changelog 复核与参数差距闭环](done/TASK-20260514-008-mainstream-api-docs-changelog-refresh.md) | Done | High | REQ-20260514-006 |
| [TASK-20260514-009 OpenAI/xAI Responses 字段 parity 与 cache/header 保真](done/TASK-20260514-009-openai-xai-responses-field-parity.md) | Done | High | REP-20260514 API/changelog |
| [TASK-20260514-010 Anthropic MCP/service tier/container/context management 字段下发](done/TASK-20260514-010-anthropic-mcp-service-tier-field-parity.md) | Done | High | REP-20260514 API/changelog |
| [TASK-20260514-011 Gemini thinkingConfig/toolConfig/URL context/Grounding 参数 parity](done/TASK-20260514-011-gemini-thinking-toolconfig-grounding-parity.md) | Done | High | REP-20260514 API/changelog |
| [TASK-20260514-012 OpenAI API 完整兼容性深度审计](done/TASK-20260514-012-openai-api-compatibility-deep-audit.md) | Done | High | REQ-20260514-008 |
| [TASK-20260514-013 OpenAI Chat/Responses 参数全量保真与原生 Responses 边界](backlog/TASK-20260514-013-openai-chat-responses-native-parity.md) | Backlog | High | REP-20260514 OpenAI API 审计 |
| [TASK-20260514-014 OpenAI 官方资源族覆盖差距补齐](backlog/TASK-20260514-014-openai-resource-family-coverage-gap.md) | Backlog | High | REP-20260514 OpenAI API 审计 |
| [TASK-20260514-015 OpenAI 公开 OpenAPI、catalog 与 conformance 事实源校准](backlog/TASK-20260514-015-openai-openapi-conformance-truth-source-hardening.md) | Backlog | Medium | REP-20260514 OpenAI API 审计 |

## OpenAI API 全量覆盖任务体系

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260514-016 OpenAI API 全量覆盖总控父任务](backlog/TASK-20260514-016-openai-full-api-coverage-parent.md) | Backlog | Critical | REQ-20260514-009 |
| [TASK-20260514-017 OpenAI Chat Completions 全参数与对象生命周期](backlog/TASK-20260514-017-openai-chat-completions-full-parity.md) | Backlog | Critical | TASK-20260514-016 / 013 |
| [TASK-20260514-018 OpenAI Responses 原生执行器与生命周期](backlog/TASK-20260514-018-openai-responses-native-lifecycle.md) | Backlog | Critical | TASK-20260514-016 / 013 |
| [TASK-20260514-019 OpenAI Conversations、Webhooks 与 Responses 工具生态](backlog/TASK-20260514-019-openai-conversations-webhooks-tools.md) | Backlog | High | TASK-20260514-016 / 013 |
| [TASK-20260514-020 OpenAI Audio、Images、Embeddings、Moderations 参数 parity](backlog/TASK-20260514-020-openai-audio-images-embeddings-moderations-parity.md) | Backlog | High | TASK-20260514-016 / 014 |
| [TASK-20260514-021 OpenAI Files、Uploads、Batches、Models 对象生命周期](backlog/TASK-20260514-021-openai-files-uploads-batches-models-lifecycle.md) | Backlog | High | TASK-20260514-016 / 014 |
| [TASK-20260516-012 OpenAI Models Delete 与 Fine-tuned Model 删除边界](done/TASK-20260516-012-openai-models-delete-finetuned-boundary.md) | Done | High | TASK-20260514-021 / 014 |
| [TASK-20260514-022 OpenAI Fine-tuning 全生命周期](backlog/TASK-20260514-022-openai-fine-tuning-full-lifecycle.md) | Backlog | High | TASK-20260514-016 / 014 |
| [TASK-20260516-013 OpenAI Fine-tuning Events/Checkpoints 本地 Lineage 列表](done/TASK-20260516-013-openai-fine-tuning-events-checkpoints-local-lineage.md) | Done | High | TASK-20260514-022 / 014 |
| [TASK-20260514-023 OpenAI Vector Stores 全栈兼容](backlog/TASK-20260514-023-openai-vector-stores-full-stack.md) | Backlog | High | TASK-20260514-016 / 014 |
| [TASK-20260517-003 OpenAI Vector Stores 本地 Lifecycle 基线](done/TASK-20260517-003-openai-vector-stores-local-lifecycle-baseline.md) | Done | High | TASK-20260514-023 |
| [TASK-20260517-004 OpenAI Vector Store Files 本地 Attachment Lifecycle 基线](done/TASK-20260517-004-openai-vector-store-files-local-attachment-lifecycle.md) | Done | High | TASK-20260514-023 |
| [TASK-20260517-005 OpenAI Vector Store File Batches 本地 Lifecycle 基线](done/TASK-20260517-005-openai-vector-store-file-batches-local-lifecycle.md) | Done | High | TASK-20260514-023 |
| [TASK-20260518-001 OpenAI Vector Store File Content 本地读取基线](done/TASK-20260518-001-openai-vector-store-file-content-local-read-baseline.md) | Done | High | TASK-20260514-023 |
| [TASK-20260518-002 OpenAI Vector Store Search 本地文本检索基线](done/TASK-20260518-002-openai-vector-store-search-local-text-baseline.md) | Done | High | TASK-20260514-023 |
| [TASK-20260518-003 OpenAI Responses File Search 本地 Vector Store 绑定基线](done/TASK-20260518-003-openai-responses-file-search-local-vector-store-binding.md) | Done | High | TASK-20260514-023 |
| [TASK-20260514-024 OpenAI Containers 与 Code Interpreter 文件](backlog/TASK-20260514-024-openai-containers-code-interpreter-files.md) | Backlog | High | TASK-20260514-016 / 014 |
| [TASK-20260514-025 OpenAI Videos API 兼容面](backlog/TASK-20260514-025-openai-videos-api-parity.md) | Backlog | Medium | TASK-20260514-016 / 014 |
| [TASK-20260514-026 OpenAI Evals、Graders 与 Runs API](backlog/TASK-20260514-026-openai-evals-graders-runs.md) | Backlog | Medium | TASK-20260514-016 / 014 |
| [TASK-20260514-027 OpenAI Skills API 与工具分发](backlog/TASK-20260514-027-openai-skills-api-tool-distribution.md) | Backlog | Medium | TASK-20260514-016 / 014 |
| [TASK-20260514-028 OpenAI Administration API 权限隔离与只读优先](backlog/TASK-20260514-028-openai-administration-api-boundary.md) | Backlog | Medium | TASK-20260514-016 / 014 |
| [TASK-20260514-029 OpenAI OpenAPI、Catalog、Conformance 与 SDK 事实源统一](backlog/TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md) | Backlog | Medium | TASK-20260514-016 / 015 |
| [TASK-20260514-030 OpenAI 横切协议兼容](backlog/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md) | Backlog | Critical | TASK-20260514-016 |
| [TASK-20260514-031 OpenAI 真实 Smoke 与认证成本防护](backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md) | Backlog | Critical | TASK-20260514-016 / 015 |
| [TASK-20260515-001 OpenAI 错误 Envelope 与 Request Id 基线](done/TASK-20260515-001-openai-error-request-id-foundation.md) | Done | Critical | TASK-20260514-030 |
| [TASK-20260515-002 OpenAI 官方 Headers 与 Idempotency-Key 下发基线](done/TASK-20260515-002-openai-official-headers-idempotency-foundation.md) | Done | Critical | TASK-20260514-030 |
| [TASK-20260515-003 OpenAI Chat Completions Create 参数保真基线](done/TASK-20260515-003-openai-chat-create-parameter-parity-foundation.md) | Done | Critical | TASK-20260514-017 |
| [TASK-20260515-004 OpenAI Stored Chat Completions 生命周期基线](done/TASK-20260515-004-openai-stored-chat-lifecycle-foundation.md) | Done | Critical | TASK-20260514-017 |
| [TASK-20260515-005 OpenAI Chat legacy functions/function_call 语义兼容](done/TASK-20260515-005-openai-chat-legacy-functions-tool-choice-compatibility.md) | Done | Critical | TASK-20260514-017 |
| [TASK-20260515-006 OpenAI Chat response_format 强类型映射](done/TASK-20260515-006-openai-chat-response-format-typed-mapping.md) | Done | Critical | TASK-20260514-017 |
| [TASK-20260515-007 OpenAI Chat modalities/audio/web_search_options 强类型映射](done/TASK-20260515-007-openai-chat-modalities-audio-web-search-typed-mapping.md) | Done | Critical | TASK-20260514-017 |
| [TASK-20260515-008 OpenAI Chat 参数兼容证明、公开文档与 SDK 示例](done/TASK-20260515-008-openai-chat-conformance-docs-sdk-evidence.md) | Done | Critical | TASK-20260514-017 / 029 |
| [TASK-20260515-009 OpenAI Idempotency-Key 本地响应持久化与重放基线](done/TASK-20260515-009-openai-idempotency-key-response-replay.md) | Done | Critical | TASK-20260514-030 |
| [TASK-20260515-010 OpenAI List Pagination Envelope 与 Cursor 参数基线](done/TASK-20260515-010-openai-list-pagination-envelope-cursor-baseline.md) | Done | Critical | TASK-20260514-030 |
| [TASK-20260515-011 OpenAI Rate Limit Headers 与 429 错误基线](done/TASK-20260515-011-openai-rate-limit-headers-baseline.md) | Done | Critical | TASK-20260514-030 |
| [TASK-20260515-012 OpenAI Idempotency-Key TTL 与清理策略](done/TASK-20260515-012-openai-idempotency-ttl-cleanup.md) | Done | Critical | TASK-20260514-030 |
| [TASK-20260515-013 OpenAI Webhook Signature 与 Replay 防护基线](done/TASK-20260515-013-openai-webhook-signature-replay-baseline.md) | Done | Critical | TASK-20260514-030 / 019 |
| [TASK-20260515-014 OpenAI Streaming Event Usage 与 Sequence 基线](done/TASK-20260515-014-openai-streaming-event-usage-sequence-baseline.md) | Done | Critical | TASK-20260514-030 / 017 / 018 |
| [TASK-20260515-015 OpenAI Protocol Path Matcher 覆盖防遗漏基线](done/TASK-20260515-015-openai-protocol-path-matcher-coverage-baseline.md) | Done | Critical | TASK-20260514-030 |
| [TASK-20260515-016 OpenAI/Codex Real Smoke 分类与预算阻断基线](done/TASK-20260515-016-openai-codex-real-smoke-classification-budget-guard.md) | Done | Critical | TASK-20260514-031 |
| [TASK-20260515-017 OpenAI Responses Native HTTP Create 基线](done/TASK-20260515-017-openai-responses-native-http-create-baseline.md) | Done | Critical | TASK-20260514-018 |
| [TASK-20260515-018 OpenAI Responses 本地生命周期 cancel/input_items 基线](done/TASK-20260515-018-openai-responses-local-lifecycle-cancel-input-items.md) | Done | Critical | TASK-20260514-018 |
| [TASK-20260515-019 OpenAI Responses Stream Obfuscation 字段基线](done/TASK-20260515-019-openai-responses-stream-obfuscation-baseline.md) | Done | Critical | TASK-20260514-018 |
| [TASK-20260515-020 OpenAI Responses input_tokens 与 compact 本地基线](done/TASK-20260515-020-openai-responses-input-tokens-compact-baseline.md) | Done | Critical | TASK-20260514-018 |
| [TASK-20260515-021 OpenAI Responses include Query 参数基线](done/TASK-20260515-021-openai-responses-include-query-baseline.md) | Done | Critical | TASK-20260514-018 |
| [TASK-20260515-022 OpenAI Responses Native JSON 原始对象透传基线](done/TASK-20260515-022-openai-responses-native-json-passthrough-baseline.md) | Done | Critical | TASK-20260514-018 |
| [TASK-20260515-023 OpenAI Responses Native Stream SSE 透明转发基线](done/TASK-20260515-023-openai-responses-native-stream-sse-passthrough-baseline.md) | Done | Critical | TASK-20260514-018 |
| [TASK-20260516-001 OpenAI Responses 远端生命周期 Passthrough 基线](done/TASK-20260516-001-openai-responses-remote-lifecycle-passthrough-baseline.md) | Done | Critical | TASK-20260514-018 |
| [TASK-20260516-002 OpenAI Responses input_tokens Native Passthrough](done/TASK-20260516-002-openai-responses-input-tokens-native-passthrough.md) | Done | Critical | TASK-20260514-018 |
| [TASK-20260516-003 OpenAI Responses compact Native Passthrough](done/TASK-20260516-003-openai-responses-compact-native-passthrough.md) | Done | Critical | TASK-20260514-018 |
| [TASK-20260517-002 OpenAI Responses 无 Lineage 远端 Lifecycle Route Hint](done/TASK-20260517-002-openai-responses-untracked-remote-lifecycle-route-hints.md) | Done | Critical | TASK-20260514-018 |
| [TASK-20260516-004 OpenAI Stored Chat 数据库游标分页与过滤硬化](done/TASK-20260516-004-openai-stored-chat-db-pagination-filter-hardening.md) | Done | Critical | TASK-20260514-017 / 030 |
| [TASK-20260516-005 OpenAI Direct Key Vault 权限探测与 Secret 引用 Smoke](done/TASK-20260516-005-openai-direct-key-vault-permission-smoke.md) | Done | Critical | TASK-20260514-031 |
| [TASK-20260516-006 OpenAI Direct 资源族 Smoke Runner 分类骨架](done/TASK-20260516-006-openai-direct-resource-family-smoke-runner.md) | Done | Critical | TASK-20260514-031 |
| [TASK-20260516-007 OpenAI Direct Smoke Certification 与脱敏 Fixture 基线](done/TASK-20260516-007-openai-direct-smoke-certification-fixture.md) | Done | Critical | TASK-20260514-031 |
| [TASK-20260516-008 OpenAI Realtime WebSocket 入口与事件代理基线](done/TASK-20260516-008-openai-realtime-websocket-ingress-event-proxy.md) | Done | Critical | TASK-20260514-030 |
| [TASK-20260516-009 OpenAI Direct 显式 Billable/Write Smoke Probe](done/TASK-20260516-009-openai-direct-explicit-billable-write-smoke-probes.md) | Done | Critical | TASK-20260514-031 |
| [TASK-20260516-010 OpenAI Provider Catalog 覆盖边界校准](done/TASK-20260516-010-openai-provider-catalog-coverage-boundary.md) | Done | High | TASK-20260514-015 / 029 |
| [TASK-20260516-011 OpenAI Batches List Envelope 与本地游标分页](done/TASK-20260516-011-openai-batches-list-envelope.md) | Done | High | TASK-20260514-021 / 015 |
| [TASK-20260516-014 OpenAI Responses Tools Registry 与非 function Tool 显式边界](done/TASK-20260516-014-openai-responses-tool-registry-boundary.md) | Done | High | TASK-20260514-019 / 013 |
| [TASK-20260516-015 OpenAI Conversations 本地 Lifecycle](done/TASK-20260516-015-openai-conversations-local-lifecycle.md) | Done | High | TASK-20260514-019 |
| [TASK-20260516-016 OpenAI Webhooks 接收入口与事件落库](done/TASK-20260516-016-openai-webhooks-ingress-event-persistence.md) | Done | High | TASK-20260514-019 |
| [TASK-20260516-017 OpenAI Direct Smoke Record/Replay Fixture 固化](done/TASK-20260516-017-openai-direct-smoke-record-replay-fixture.md) | Done | Critical | TASK-20260514-031 |
| [TASK-20260517-001 OpenAI Direct Smoke Record/Replay CI 校验器](done/TASK-20260517-001-openai-direct-smoke-record-replay-ci-verifier.md) | Done | Critical | TASK-20260514-031 |

## Portal/Admin 角色化界面任务体系

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260507-008 Portal/Admin 角色化界面任务体系拆分](done/TASK-20260507-008-portal-admin-task-system-breakdown.md) | Done | High | User Request |
| [TASK-20260507-009 Portal/Admin 路由、身份与产品面边界基线](done/TASK-20260507-009-portal-admin-route-identity-boundary.md) | Done | High | REQ-20260507-001 |
| [TASK-20260507-010 Community Portal Codex 自助接入与个人用量界面](done/TASK-20260507-010-community-portal-codex-self-service-surface.md) | Done | High | REQ-20260507-001 |
| [TASK-20260507-011 Admin Console 命名空间迁移与旧路由兼容](done/TASK-20260507-011-admin-console-namespace-legacy-route-migration.md) | Done | High | REQ-20260507-001 |
| [TASK-20260507-012 Admin Console 角色化工作台与导航体系](done/TASK-20260507-012-admin-console-role-workbench-navigation.md) | Done | High | REQ-20260507-001 |
| [TASK-20260507-013 Portal/Admin API 权限隔离、审计与越权回归](done/TASK-20260507-013-portal-admin-permission-audit-regression.md) | Done | High | REQ-20260507-001 |
| [TASK-20260507-014 Portal/Console UI/UX 验收体系与组件硬化](done/TASK-20260507-014-portal-console-ux-acceptance-system.md) | Done | Medium | REQ-20260507-001 |
| [TASK-20260507-016 Codex 真实 auth.json 长期测试账号入库与详测](done/TASK-20260507-016-codex-real-auth-db-long-term-test.md) | Done | High | User Request |

## 排期管理任务

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260507-015 当前 Backlog 优先级队列编排](done/TASK-20260507-015-backlog-priority-roadmap.md) | Done | High | User Request |

## 本地化流程任务

- [REQ-20260501-001 本地化协作流程迁移](../docs/requirements/REQ-20260501-001-local-workflow-migration.md)
- [TASK-20260505-001 Notion/Linear 回迁到本地](done/TASK-20260505-001-notion-linear-back-migration.md) | Done | High | User Request
