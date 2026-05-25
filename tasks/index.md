# x-ai-gateway 本地任务索引

## 任务状态

- Backlog：待排期，位于 `tasks/backlog/`
- In Progress：进行中，位于 `tasks/in-progress/`
- Done：已完成，位于 `tasks/done/`

## 当前最高优先级：Codex + UI/UX 功能性服务 API

用户已要求先不做测试、全速推进项目任务，并将 Codex 相关任务提升到最高优先。2026-05-21 进度复核后，Codex 与 UI/UX 相关任务继续作为最高优先队列：Codex 队列只围绕对话、streaming、tools、Responses、多模态与必要支撑能力推进，不恢复 Fine-tuning、Batches、Evals、Admin 或非 Responses Codex 内部 API；UI/UX 已归档成果作为后续控制台与门户交互验收基线。

| 排期 | 任务 | 状态 | 执行定位 |
| --- | --- | --- | --- |
| P0-CODEX-01 | [TASK-20260519-002 Codex 功能性服务 API 最高优先推进](done/TASK-20260519-002-codex-priority-functional-service-api.md) | Done | Codex-first 父任务与执行队列已归档 |
| P0-CODEX-02 | [TASK-20260519-002-01 Codex 功能性服务 API 事实源优先收紧](done/TASK-20260519-002-01-codex-functional-truth-source-priority.md) | Done | 第一执行切片已归档；测试按用户当前策略延后 |
| P0-CODEX-03 | [TASK-20260514-029 对话与 Tools OpenAPI、Catalog、Conformance 与 SDK 事实源统一](done/TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md) | Done | `TASK-20260514-029-01/02/03/04` 已全部归档 |
| P0-CODEX-03a | [TASK-20260514-029-02 Codex OpenAPI, Catalog & Conformance 深度融合](done/TASK-20260514-029-02-codex-openapi-catalog-conformance.md) | Done | Codex OpenAPI/Catalog 收敛与本地配置最佳实践 |
| P0-CODEX-03b | [TASK-20260514-029-03 Codex 运营控制台体验对标与 Session 恢复桥接](done/TASK-20260514-029-03-codex-console-session-recovery.md) | Done | 终端 CLI 恢复指令 API 及前端审计/详情面板与运行态专区 UI 闭环 |
| P0-CODEX-03c | [TASK-20260514-029-04 OpenAPI 路径补全与 SDK 三模式示例归口](done/TASK-20260514-029-04-openapi-coverage-sdk-finalization.md) | Done | 已收尾 public OpenAPI、SDK 示例与 coverage matrix 派生状态 |
| P0-CODEX-04 | [TASK-20260519-002-02 Codex Smoke、Record/Replay 与成本防护复核](done/TASK-20260519-002-02-codex-smoke-record-replay-priority.md) | Done | 第二执行切片已归档；测试按用户当前策略延后 |
| P0-UI-UX-01 | [TASK-20260520-001 UI/UX 体验专项硬化与增强计划](done/TASK-20260520-001-ui-ux-console-portal-experience.md) | Done | Critical；对标并硬化所有前端 UI/UX 控制台及沙盒视图交互已归口归档 |
| P0-SCOPE-01 | [TASK-20260521-001 功能性服务 API Backlog 收口与优先级清理](done/TASK-20260521-001-functional-scope-backlog-closeout.md) | Done | 已清理旧 full parity/full stack backlog 口径，保留可执行功能性支撑任务 |
| P0-SCOPE-02 | [TASK-20260521-002 上游凭证与账号分组收敛](done/TASK-20260521-002-upstream-credentials-group-live-scope.md) | Done | 上游凭证弹窗编辑、Codex 归入接入、账号分组文案和 Live/Realtime 下线已完成 |
| P0-SCOPE-03 | [TASK-20260521-003 Liquibase 新 Baseline 重建](done/TASK-20260521-003-liquibase-baseline-rebuild.md) | Done | 清库重建 baseline，旧增量 changelog 已清理为单一入口 |
| P0-SCOPE-04 | [TASK-20260521-004 上游凭证入口统一与官方账号概念澄清](in-progress/TASK-20260521-004-upstream-credential-entry-and-official-account-clarity.md) | In Progress | 统一新增入口，并把官方账号收敛为后端运行态管理概念，不恢复独立控制台产品面 |
| P0-SCOPE-05 | [TASK-20260521-005 控制台重复功能下线与向量能力范围收窄](in-progress/TASK-20260521-005-console-feature-retirement-and-vector-scope-prune.md) | In Progress | 父任务：统一收口删除边界，并编排前端下线与后端/API 清理 |
| P0-SCOPE-05a | [TASK-20260521-005-01 控制台重复功能前端下线](done/TASK-20260521-005-01-console-feature-retirement-frontend-surface-prune.md) | Done | 已完成前端入口、页面、文案和测试清理；高风险后端/API 仍暂保留 |
| P0-SCOPE-05b | [TASK-20260521-005-02 官方账号与向量 API 后端清理边界审计](backlog/TASK-20260521-005-02-official-account-vector-api-eradication-boundary.md) | Backlog | 历史边界审计保留；`vector/files/file_search` 主线支撑已确认保留，仅继续复核官方账号其余非主路径后端面 |
| P0-SCOPE-05c | [TASK-20260521-005-03 任务索引下线能力清理与边界补充](in-progress/TASK-20260521-005-03-task-index-retirement-and-boundary-cleanup.md) | In Progress | 清理 tasks 现役引用，并为保留的后端/API 任务补充退役边界说明 |
| P0-SCOPE-06 | [TASK-20260521-006 冗余接口清理与清库前 Baseline 重建](in-progress/TASK-20260521-006-redundant-api-eradication-and-baseline-refresh.md) | In Progress | 审视并清理多余接口，在用户清库前重建最终 baseline |
| P0-SCOPE-06a | [TASK-20260521-006-01 冗余 Admin 接口审计与清理](in-progress/TASK-20260521-006-01-admin-api-prune-and-alignment.md) | In Progress | 收口只服务于已下线控制台或与主线重复的 Admin API |
| P0-SCOPE-06b | [TASK-20260521-006-02 冗余协议接口与向量能力边界清理](in-progress/TASK-20260521-006-02-protocol-vector-boundary-prune.md) | In Progress | 审视 protocol/public API 冗余面，排除 `vector/files/file_search` 主线支撑后收口旧协议说明入口 |
| P0-SCOPE-06c | [TASK-20260521-006-03 清理后 Liquibase Baseline 重建](in-progress/TASK-20260521-006-03-baseline-regenerate-after-api-prune.md) | In Progress | 基于清理后的 schema 重建 baseline，供用户清库后初始化 |
| P0-UI-UX-02 | [TASK-20260521-007 现役 UI 全界面汉化与说明性文案收口](in-progress/TASK-20260521-007-ui-chinese-only-localization.md) | In Progress | 深度汉化、解释性文案删除与中文单语界面收口 |
| P0-UI-UX-02a | [TASK-20260521-007-01 Console/Admin 界面汉化](in-progress/TASK-20260521-007-01-console-admin-ui-chinese-localization.md) | In Progress | Console/Admin 范围深度汉化与解释文案清理 |
| P0-UI-UX-02b | [TASK-20260521-007-02 Portal/Public/Workbench 界面汉化](in-progress/TASK-20260521-007-02-portal-public-workbench-ui-chinese-localization.md) | In Progress | Portal/Public/Workbench 范围深度汉化与说明收口 |
| P0-UI-UX-02c | [TASK-20260521-007-03 运维观测链路界面汉化](done/TASK-20260521-007-03-ops-observability-ui-chinese-localization.md) | Done | 运维观测链路第二轮深度汉化与说明性正文删除 |
| P0-UI-UX-03 | [TASK-20260521-008 系统默认深色主题与深浅双样式支持](done/TASK-20260521-008-system-theme-dark-default-light-dual-mode.md) | Done | 默认 `dark` 与 `dark/light` 双主题支持 |
| P0-UI-UX-04 | [TASK-20260521-009 智能运维总览导航整合与重复面板收口](done/TASK-20260521-009-ops-overview-navigation-consolidation.md) | Done | 收敛总览导航，并把重复总览信息并入智能运维总览 |
| P0-UI-UX-04a | [TASK-20260521-009-01 智能运维总览页面合并与导航收口](done/TASK-20260521-009-01-ops-overview-surface-merge.md) | Done | `/dashboard`、`/ops`、`/incidents` 合并与导航去重 |
| P0-UI-UX-05 | [TASK-20260521-010 控制台导航可读性、系统参数汉化与 Codex auth.json 导入保活](done/TASK-20260521-010-console-navigation-settings-codex-auth-import-refresh.md) | Done | 修复侧栏可读性、重分组导航、系统参数汉化，并把 Codex auth.json 文件/粘贴导入收敛到上游凭证创建入口 |
| P0-UI-UX-05a | [TASK-20260521-010-01 Console 侧栏可读性与导航重分组](done/TASK-20260521-010-01-console-sidebar-navigation-restyle-and-regroup.md) | Done | 侧栏 dark/light 对比度与一二级导航归并 |
| P0-UI-UX-05b | [TASK-20260521-010-02 系统参数页面深度汉化](done/TASK-20260521-010-02-system-settings-localization.md) | Done | 系统参数页面英文和内部字段名汉化 |
| P0-UI-UX-05c | [TASK-20260521-010-03 上游凭证入口 Codex auth.json 导入与保活口径](done/TASK-20260521-010-03-codex-auth-json-import-and-refresh-ui.md) | Done | 创建上游凭证支持 Codex auth.json 文件路径批量导入和粘贴导入，RT 可选有则保活 |
| P0-UI-UX-06 | [TASK-20260521-011 控制台瘦身、品牌 Logo、上游凭证统一与热数据缓存](done/TASK-20260521-011-console-surface-prune-logo-credential-redis.md) | Done | 已删除重复运维/公开入口，接入 Logo，统一上游凭证列表与选择器，并落地 Redis 热数据增强 |
| P0-UI-UX-06a | [TASK-20260521-011-01 控制台与公开重复入口下线](done/TASK-20260521-011-01-console-public-route-prune.md) | Done | 已下线变更维护、公开状态/价格/文档和重复错误规则入口 |
| P0-UI-UX-06b | [TASK-20260521-011-02 品牌 Logo 与 favicon 接入](done/TASK-20260521-011-02-brand-logo-favicon-console-public.md) | Done | 已绘制并接入 favicon、公开首页和控制台 Logo |
| P0-UI-UX-06c | [TASK-20260521-011-03 上游凭证统一列表与可搜索选择器](done/TASK-20260521-011-03-upstream-credential-unified-list-selectors.md) | Done | API Key 凭证与 Codex auth.json 账号统一展示，模型/代理/TLS 改为搜索选择 |
| P0-UI-UX-06d | [TASK-20260521-011-04 请求日志 Codex 独立面板删除](done/TASK-20260521-011-04-request-log-codex-panel-prune.md) | Done | 已删除 Codex 请求独立面板，回到统一请求日志 |
| P0-ARCH-01 | [TASK-20260521-011-05 Redis 热数据增强与 PostgreSQL 回写边界](done/TASK-20260521-011-05-redis-hot-data-writeback.md) | Done | 已将凭证/账号运行指标接入 Redis 队列并批量回写 PG |
| P0-UI-UX-07 | [TASK-20260521-012 控制台健康评分、Codex 模型、TLS 画像与调试台体验修正](done/TASK-20260521-012-console-health-codex-model-tls-workbench-ux.md) | Done | 健康评分表格、Codex 模型刷新、账号分组/治理表格收敛、TLS 默认画像与白盒调试台间距 |
| P0-UI-UX-07a | [TASK-20260521-012-01 健康评分统一表格](done/TASK-20260521-012-01-health-score-unified-table.md) | Done | 健康评分覆盖静态凭证与账号类凭证 |
| P0-UI-UX-07b | [TASK-20260521-012-02 Codex 模型刷新入口](done/TASK-20260521-012-02-codex-model-refresh-entry.md) | Done | Codex 类型账号刷新支持模型 |
| P0-UI-UX-07c | [TASK-20260521-012-03 账号分组与治理表格操作收敛](done/TASK-20260521-012-03-account-groups-governance-table-detail-actions.md) | Done | 表格主行保持扁平，复杂操作进入详情 |
| P0-UI-UX-07d | [TASK-20260521-012-04 TLS 默认画像与键值对 Header 编辑](done/TASK-20260521-012-04-tls-default-profiles-key-value-editor.md) | Done | 默认常用 TLS/header 画像与键值对编辑器 |
| P0-UI-UX-07e | [TASK-20260521-012-05 白盒调试工作台 Tab 间距优化](done/TASK-20260521-012-05-workbench-tabs-spacing.md) | Done | 调试台 tab 和内容布局间距修正 |
| P0-UI-UX-08 | [TASK-20260521-013 全局表格分页](done/TASK-20260521-013-global-table-pagination.md) | Done | 所有列表表格默认每页 50 条并支持切换每页条数 |
| P0-UI-UX-09 | [TASK-20260522-001 账号分组详情入口统一](done/TASK-20260522-001-account-group-detail-entry-unification.md) | Done | 统一凭证详情与账号分组列表进入的完整账号分组详情页 |
| P0-UI-UX-10 | [TASK-20260522-002 网络代理拨测功能下线](done/TASK-20260522-002-network-proxy-probe-retirement.md) | Done | 删除网络代理拨测页面、任务、接口和 baseline 表 |
| P0-UI-UX-11 | [TASK-20260522-003 提示气泡化与超大文件组件化拆分](done/TASK-20260522-003-toast-feedback-and-component-splitting.md) | Done | `InlineError` 改为左上角 toast，调试工作台拆出预设和展示组件 |
| P0-UI-UX-11a | [TASK-20260522-024 全局操作结果提示收敛](done/TASK-20260522-024-action-feedback-toast-coverage-parent.md) | Done | React Query mutation 成功/失败默认 toast，凭证刷新模型成功提示与失败去重 |
| P1-UI-UX-12 | [TASK-20260522-004 原生确认弹窗与通知入口统一](done/TASK-20260522-004-confirm-dialog-notification-unification.md) | Done | 已迁移业务删除确认到统一确认弹窗，`window.confirm` 清零 |
| P0-UI-UX-13 | [TASK-20260523-008 前端路由错误页自定义](done/TASK-20260523-008-router-error-boundary-customization.md) | Done | 替换 React Router 默认错误页，覆盖动态模块加载失败、未匹配路由与 route 层异常 |
| P1-ARCH-02 | [TASK-20260522-005 超大文件分批组件化与服务拆分](backlog/TASK-20260522-005-giant-file-decomposition-roadmap.md) | Backlog | 按 P0/P1 清单继续拆前端巨页和后端超级服务 |
| P0-ARCH-03 | [TASK-20260522-010 Observability Redis 超时退避与同步兜底](done/TASK-20260522-010-observability-redis-timeout-backoff.md) | Done | Redis 热路径队列超时后退避，并让请求观测写入直接同步兜底 |
| P2-INFRA-01 | [TASK-20260522-006 .gitignore 通配符化整理](done/TASK-20260522-006-gitignore-wildcard-cleanup.md) | Done | 将逐文件缓存 ignore 清单归并为目录级和通配符规则 |
| P0-CODEX-06 | [TASK-20260522-007 Model Policy 分层收敛父任务](done/TASK-20260522-007-model-policy-layered-resolution-parent.md) | Done | 覆盖模型策略表、逐层收缩、Admin API、自动探测、健康裁剪、模型级限流、fallback chain 与灰度路由 |
| P0-CODEX-06a | [TASK-20260522-007-01 Model Policy 策略表与运行时解析](done/TASK-20260522-007-01-model-policy-runtime-resolution.md) | Done | 策略 schema、resolver、路由模型解析与候选裁剪 |
| P0-CODEX-06b | [TASK-20260522-007-02 Model Policy 管理端、预览与冲突检测](done/TASK-20260522-007-02-model-policy-admin-preview-conflict.md) | Done | `/admin/model-policies` CRUD、preview、conflicts 与 preset 导入 |
| P0-CODEX-06c | [TASK-20260522-007-03 Model Policy 第三阶段运行态治理](done/TASK-20260522-007-03-model-policy-runtime-governance.md) | Done | discovery policy、健康/额度裁剪、模型级 rpm、fallback chain 与 canary weight |
| P0-CODEX-06d | [TASK-20260522-008 模型刷新幂等性修复](done/TASK-20260522-008-model-refresh-idempotency.md) | Done | 修复重复点击刷新模型触发 `site_model_capability` 唯一约束冲突，并关闭 Redis Repository 扫描噪音 |
| P0-CODEX-06e | [TASK-20260522-009 分发 Key 协议簇授权迁移](done/TASK-20260522-009-protocol-suite-authorization-migration.md) | Done | 将 Key/访问组的 `allowedProtocols` 直接迁移为厂商协议簇 `allowedProtocolSuites`，不保留旧字段兼容 |
| P0-CODEX-06f | [TASK-20260522-011 厂商管理中心补齐](done/TASK-20260522-011-vendor-management-center.md) | Done | 恢复并补齐厂商管理、API 入口 CRUD、预设导入、能力刷新和 capability matrix |
| P0-CODEX-06g | [TASK-20260522-012 默认厂商 API 入口引导](done/TASK-20260522-012-default-provider-site-bootstrap.md) | Done | 启动时自动导入非 deprecated provider preset，生成默认厂商 API 入口和站点级快照 |
| P0-CODEX-06h | [TASK-20260522-013 上游凭证绑定厂商 API 入口](done/TASK-20260522-013-credential-vendor-site-binding.md) | Done | 新增/编辑 API Key 上游凭证改为选择厂商/API 入口，并由入口派生 provider type 与 Base URL |
| P0-CODEX-06i | [TASK-20260522-014 厂商多协议入口与对话兼容画像升级](done/TASK-20260522-014-provider-protocol-endpoints.md) | Done | 厂商/API 入口下挂多协议 endpoint，MiMo/DeepSeek 支持 OpenAI-compatible + Anthropic-compatible，凭证绑定 protocolEndpointId |
| P0-CODEX-06j | [TASK-20260522-015 协议入口对话兼容画像下发运行时](done/TASK-20260522-015-protocol-endpoint-conversation-profile-runtime.md) | Done | 凭证绑定协议入口时合并 endpoint conversation profile 到 credential metadata，运行时可读取 |
| P0-CODEX-06k | [TASK-20260522-016 存量凭证协议入口保守回填](done/TASK-20260522-016-credential-protocol-endpoint-backfill.md) | Done | 启动默认资源引导时唯一匹配回填历史凭证 protocolEndpointId 和入口 conversation profile |
| P0-CODEX-06l | [TASK-20260522-017 功能性 Provider Smoke 协议入口地址对齐](done/TASK-20260522-017-functional-provider-smoke-endpoint-alignment.md) | Done | 将 MiMo 功能性 smoke 默认地址与当前 token-plan 协议入口对齐 |
| P0-CODEX-06m | [TASK-20260522-018 上游凭证多协议入口绑定](done/TASK-20260522-018-credential-multi-protocol-endpoint-binding.md) | Done | API Key 凭证创建支持多选协议入口，并让运行时候选优先读取 endpoint 元数据 |
| P0-CODEX-06n | [TASK-20260522-019 分发 Key 账号组运行时展开](done/TASK-20260522-019-distributed-key-account-group-runtime-expansion.md) | Done | runtime 查询从 DistributedKey 账号组绑定展开组内 API Key 凭证候选 |
| P0-CODEX-06o | [TASK-20260522-020 账号类选择 active 账号组运行时对齐](done/TASK-20260522-020-account-selection-active-group-runtime-alignment.md) | Done | OAuth/auth.json 账号选择、sticky account 与 Model Policy 上下文排除停用账号组 |
| P0-CODEX-06p | [TASK-20260522-021 分发 Key 鉴权 active 账号组守卫](done/TASK-20260522-021-distributed-key-auth-active-group-guard.md) | Done | Key 鉴权和 auth cache 命中均要求存在启用账号组绑定 |
| P0-CODEX-06q | [TASK-20260522-022 分发 Key 创建时初始账号组绑定](done/TASK-20260522-022-distributed-key-create-initial-account-group-binding.md) | Done | 创建访问密钥时原子写入初始账号组绑定，并支持账号组详情显式选择运行时 provider |
| P0-CODEX-06r | [TASK-20260522-023 功能性 Provider Smoke 认证策略对齐](done/TASK-20260522-023-functional-provider-smoke-auth-strategy.md) | Done | OpenAI-compatible 功能性 smoke 改为 Bearer 认证，避免 MiMo/DeepSeek 真实验证误报 |
| P0-CODEX-06s | [TASK-20260522-025 MiMo OpenAI Key 刷新模型 401 排查](done/TASK-20260522-025-mimo-openai-key-refresh-401-diagnosis-parent.md) | Done | 脱敏对比 Key 1/Key 2 绑定、指纹、刷新痕迹与 401 状态写回缺口 |
| P1-CODEX-06t | [TASK-20260523-001 刷新模型失败健康状态写回](backlog/TASK-20260523-001-refresh-model-failure-health-writeback.md) | Backlog | 刷新模型失败时写回 last_error、记录系统事件并展示凭证异常 |
| P0-CODEX-06u | [TASK-20260523-002 厂商管理与预设导入口径一致性](done/TASK-20260523-002-provider-site-preset-display-consistency.md) | Done | 区分默认站点类型与厂商协议入口，并让预设导入返回将导入的 protocol endpoints |
| P0-CODEX-06v | [TASK-20260523-003 厂商管理界面与编辑界面收敛](done/TASK-20260523-003-provider-site-ui-simplification.md) | Done | 厂商管理改为单一厂商目录，已导入统一进入详情管理，详情页和协议入口编辑按 Tab 分层 |
| P0-CODEX-06w | [TASK-20260523-004 厂商目录框线层级拆解](done/TASK-20260523-004-provider-catalog-frame-flattening.md) | Done | 厂商目录拆掉卡片嵌套和内层圆角表格框，保留表头底线、行分隔和无边框分页 |
| P0-CODEX-06x | [TASK-20260523-005 厂商 Audio、Files、Images 资源型接口覆盖](done/TASK-20260523-005-provider-audio-file-image-resource-coverage-parent.md) | Done | 已覆盖 audio、file*、image* 资源型接口，按直连、编排和互转完成能力收口 |
| P0-CODEX-06x1 | [TASK-20260523-005-01 OpenAI-style Audio 与 Images 资源入口补齐](done/TASK-20260523-005-01-openai-style-audio-image-resource-endpoints.md) | Done | 已补齐 audio translations、image edits、image variations 的 OpenAI-style ingress、语义和能力矩阵 |
| P1-CODEX-06x2 | [TASK-20260523-005-02 厂商 file* 编排覆盖与能力矩阵对齐](done/TASK-20260523-005-02-provider-file-object-orchestration-coverage.md) | Done | 已对齐 OpenAI、OpenAI-compatible、Anthropic、Gemini/Vertex 的 file* 生命周期、uploads 和展示事实源 |
| P1-CODEX-06x3 | [TASK-20260523-005-03 厂商原生 Audio 与 Images 互转适配补齐](done/TASK-20260523-005-03-provider-native-media-adapter-parity.md) | Done | Gemini/Vertex image edit 原生互转、OpenAI-compatible passthrough 与 Anthropic/Gemini 不可等价 blocked reason 已收口 |
| P0-CODEX-06y | [TASK-20260523-006 资源入口事实源去重](done/TASK-20260523-006-resource-surface-registry-dedup.md) | Done | 已收敛入口解析、入口能力矩阵、默认 path/surface/default model，并删除厂商详情独立特性解析重复区块 |
| P0-CODEX-06z | [TASK-20260523-007 厂商目录标题层级与表格边界再平衡](done/TASK-20260523-007-provider-catalog-title-border-rebalance.md) | Done | 厂商管理页标题收敛为单一目录标题，并恢复厂商目录表格单层轻量边界 |
| P0-CODEX-06aa | [TASK-20260523-009 协议入口兼容画像结构化编辑](done/TASK-20260523-009-protocol-endpoint-structured-profile-editor.md) | Done | 已移除协议入口高级 JSON 编辑，改为结构化兼容画像、thinking/reasoning 控件和只读运行时画像预览 |
| P0-CODEX-06aa1 | [TASK-20260525-003 协议入口运行时策略页签汉化](done/TASK-20260525-003-protocol-endpoint-runtime-policy-localization.md) | Done | 已将协议入口新增/编辑弹窗“运行时策略”字段标签收口为中文，并用前端定向测试防回退 |
| P0-CODEX-06aa2 | [TASK-20260525-004 上游凭证详情编辑合并与可用性探测](done/TASK-20260525-004-credential-detail-probe-parent.md) | Done | 已将 API Key 凭证详情与编辑合并，补用量/探测展示与保存凭证联通性测试持久化 |
| P0-OBS-01 | [TASK-20260525-005 请求详情追踪与转换审计](done/TASK-20260525-005-request-trace-detail-audit-parent.md) | Done | 建立 requestId 维度的请求详情追踪，并补充凭证/总体最近窗口可用率与成功率统计 |
| P0-OBS-01c | [TASK-20260525-005-03 凭证与运维健康统计](done/TASK-20260525-005-03-observability-health-metrics.md) | Done | 基于 request_log 最近窗口统计总体、Provider、凭证维度成功率、可用率、失败率与平均耗时 |
| P0-OBS-02 | [TASK-20260525-006 请求详情追踪保留、采样与归档增强](done/TASK-20260525-006-trace-detail-retention-sampling-archive-parent.md) | Done | 为 trace detail 增加 TTL、采样、归档摘要和 metadata 精确截断/脱敏字段 |
| P0-OBS-03 | [TASK-20260525-008 ADR-0011 请求日志与请求详情拆表闭环](done/TASK-20260525-008-adr0011-request-log-trace-detail-split-closure.md) | Done | 审计并闭环 request_log / request_trace_detail / archive 拆表决策，补齐 payload 来源与限制 metadata |
| P0-ARCH-04 | [TASK-20260525-007 异步边界同步代码排查](done/TASK-20260525-007-async-boundary-sync-code-audit-parent.md) | Done | 已确认 WebFlux 入口与同步服务核心混合形态，并输出阻塞热路径报告 |
| P0-ARCH-04a | [TASK-20260525-007-03 Chat Runtime 响应式边界改造](backlog/TASK-20260525-007-03-chat-runtime-reactive-boundary.md) | Backlog | 将公开 chat/responses 非流式热路径迁移为 reactive 执行 |
| P0-ARCH-04b | [TASK-20260525-007-04 Resource Executor 响应式边界改造](backlog/TASK-20260525-007-04-resource-executor-reactive-boundary.md) | Backlog | 将 embeddings/rerank/passthrough 等 resource executor 从同步契约迁移为 reactive |
| P0-ARCH-04c | [TASK-20260525-007-05 阻塞基础设施隔离与防回退护栏](backlog/TASK-20260525-007-05-blocking-infra-isolation-guardrails.md) | Backlog | 建立 JPA/Redis/file/OAuth 阻塞隔离和 no-block 静态护栏 |
| P0-CODEX-06ab | [TASK-20260523-010 功能性媒体 API 厂商互转补齐](done/TASK-20260523-010-functional-media-provider-translation-parity.md) | Done | Gemini/Vertex audio_translation 与 image_variation 已进入 native executor；Anthropic audio/image 资源边界已拆清 |
| P0-FUNC-01 | [TASK-20260514-020 OpenAI 多模态支撑参数边界收紧](done/TASK-20260514-020-openai-multimodal-supporting-parameters.md) | Done | 已清理 Audio translations 与 Images edits/variations 残留，并固定多模态支撑参数 |
| P0-FUNC-02 | [TASK-20260514-021 OpenAI Files、Uploads、Models 功能性支撑面](done/TASK-20260514-021-openai-files-uploads-models-functional-support.md) | Done | 已补 Files list 参数与 envelope，并确认 Batches/Fine-tuning 不回到公开支持面 |
| P0-CODEX-05 | [TASK-20260514-031 OpenAI 真实 Smoke 与认证成本防护](backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md) | Backlog | Critical；承接 Codex/OpenAI-compatible smoke 机制，等待恢复测试或真实 key 执行窗口 |


## 当前 Backlog 优先级队列

| 排期 | 任务 | 状态 | 执行定位 |
| --- | --- | --- | --- |
| P0-01 | [TASK-20260508-001 Codex Observability Projection API 与前端直连](done/TASK-20260508-001-codex-observability-projection-api.md) | Done | Codex 观测后端事实源 |
| P0-02 | [TASK-20260508-002 Codex Runtime 批量恢复执行 API、容错与系统事件审计](done/TASK-20260508-002-codex-runtime-batch-recovery-api-audit.md) | Done | 批量操作可信执行 |
| P0-03 | [TASK-20260508-003 Codex 前后端联调 Smoke、后端测试与浏览器回归证据](done/TASK-20260508-003-codex-e2e-smoke-backend-frontend-evidence.md) | Done | 联调证据闭环 |
| P0-01 | [TASK-20260507-002 Codex CLI 请求保真、Session 粘性与反向代理兼容](done/TASK-20260507-002-codex-cli-request-fidelity-sticky-session.md) | Done | Codex 反代协议地基 |
| P0-02 | [TASK-20260507-009 Portal/Admin 路由、身份与产品面边界基线](done/TASK-20260507-009-portal-admin-route-identity-boundary.md) | Done | 产品面边界地基 |
| P0-03 | [TASK-20260507-013 Portal/Admin API 权限隔离、审计与越权回归](done/TASK-20260507-013-portal-admin-permission-audit-regression.md) | Done | 安全边界地基 |
| P0-04 | [TASK-20260507-001 Codex 官方账号真实适配、配额刷新与反代 Smoke](done/TASK-20260507-001-codex-official-account-real-adapter-smoke.md) | Done | 官方账号后端导入、quota 与 smoke 基线；不代表保留独立控制台运行态页面 |
| P1-05 | [TASK-20260507-014 Portal/Console UI/UX 验收体系与组件硬化](done/TASK-20260507-014-portal-console-ux-acceptance-system.md) | Done | UI 验收基线 |
| P0-05 | [TASK-20260507-016 Codex 真实 auth.json 长期测试账号入库与详测](done/TASK-20260507-016-codex-real-auth-db-long-term-test.md) | Done | 真实测试账号基线 |
| P1-07 | [TASK-20260507-006 管理端 UI 信息架构与角色化工作台重整](done/TASK-20260507-006-admin-ui-information-architecture-workbench.md) | Done | Admin UI 父级收口 |
| P1-08 | [TASK-20260507-012 Admin Console 角色化工作台与导航体系](done/TASK-20260507-012-admin-console-role-workbench-navigation.md) | Done | Admin UI 具体落地 |
| P1-09 | [TASK-20260507-005 Codex 接入向导、Client Instance 与 Deep Link UI 闭环](done/TASK-20260507-005-codex-onboarding-client-instance-deeplink-ui.md) | Done | Codex 接入主路径 |
| P1-10 | [TASK-20260507-010 Community Portal Codex 自助接入与个人用量界面](done/TASK-20260507-010-community-portal-codex-self-service-surface.md) | Done | 社区用户主路径 |
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
| [TASK-20260506-010 支付生产闭环完善](done/TASK-20260506-010-production-payment-closure.md) | Done | High | REP-20260506 |
| [TASK-20260506-011 Realtime 与 Media 生产硬化](done/TASK-20260506-011-realtime-media-production-hardening.md) | Done | High | REP-20260506 |
| [TASK-20260506-012 CLI/客户端生态与云端接入工具链补齐](done/TASK-20260506-012-cloud-cli-client-access-tooling.md) | Done | High | REP-20260506 |
| [TASK-20260506-013 文档、i18n 与 OpenAPI 事实源修复](done/TASK-20260506-013-docs-i18n-openapi-truth-source.md) | Done | Medium | REP-20260506 |
| [TASK-20260506-014 CLI 云端代理接入、热切换、过滤与模型路由](done/TASK-20260506-014-cloud-cli-proxy-access-hot-switch-filtering.md) | Done | High | REP-20260506 五项目深度分析 |
| [TASK-20260506-015 AI IDE/CLI 云端账号配额、多实例与插件联动运营面](done/TASK-20260506-015-ai-ide-account-quota-instance-operator-plane.md) | Done | High | REP-20260506 五项目深度分析；旧 account pool 运营面历史方案，现仅保留后端语义参考 |

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
| [TASK-20260506-021 AI IDE/CLI 官方账号导入与配额刷新](done/TASK-20260506-021-ai-ide-account-import-quota-refresh.md) | Done | High | REP-20260506 深度再复核；保留后端导入与 quota 边界，不代表保留独立官方账号控制台 |
| [TASK-20260506-022 Client Instance 管理与插件/Deep Link 授权下发](done/TASK-20260506-022-client-instance-plugin-deeplink.md) | Done | Medium | REP-20260506 深度再复核 |
| [TASK-20260506-023 OpenAPI 自动生成、SDK 示例与前端 i18n 抽取](done/TASK-20260506-023-openapi-sdk-frontend-i18n.md) | Done | Medium | REP-20260506 深度再复核 |
| [TASK-20260506-024 Linux/systemd 部署、数据管理与迁移兼容](done/TASK-20260506-024-linux-systemd-data-migration.md) | Done | Medium | REP-20260506 深度再复核 |
| [TASK-20260506-025 桌面 Companion、MCP/Skills/Session/Workspace 可行性评估](done/TASK-20260506-025-desktop-companion-local-workflow-evaluation.md) | Done | Low | REP-20260506 深度再复核 |

## Codex 反代与 UI/UX 深度复核

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260507-000 Codex 账户反代与 UI/UX 深度差距分析](done/TASK-20260507-000-codex-proxy-uiux-gap-analysis.md) | Done | High | User Request |
| [TASK-20260507-001 Codex 官方账号真实适配、配额刷新与反代 Smoke](done/TASK-20260507-001-codex-official-account-real-adapter-smoke.md) | Done | High | REP-20260507；保留后端导入、quota 与 smoke 基线 |
| [TASK-20260507-002 Codex CLI 请求保真、Session 粘性与反向代理兼容](done/TASK-20260507-002-codex-cli-request-fidelity-sticky-session.md) | Done | High | REP-20260507 |
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
| [TASK-20260513-003 Admin Console 菜单精简与无效运维能力下线](done/TASK-20260513-003-admin-console-menu-simplification-ops-prune.md) | Done | High | REQ-20260513-002 |
| [TASK-20260513-004 客户门户完整度补齐](done/TASK-20260513-004-portal-customer-completeness-hardening.md) | Done | High | REQ-20260513-002 |
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
| [TASK-20260514-014 OpenAI 对话与 Tools 支撑资源族覆盖差距补齐](done/TASK-20260514-014-openai-resource-family-coverage-gap.md) | Done | High | REP-20260514 OpenAI API 审计 / REQ-20260518-005 |
| [TASK-20260514-015 OpenAI 公开 OpenAPI、catalog 与 conformance 事实源校准](done/TASK-20260514-015-openai-openapi-conformance-truth-source-hardening.md) | Done | Medium | REP-20260514 OpenAI API 审计 / REQ-20260518-005 |

## 对话与 Tools 功能性 API 任务体系

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260518-005 对话与 Tools 功能性服务 API 范围清理](done/TASK-20260518-005-functional-service-api-scope-pruning.md) | Done | Critical | REQ-20260518-005 |
| [TASK-20260518-006 非核心 API 兼容代码彻底清理](done/TASK-20260518-006-non-core-api-code-eradication.md) | Done | Critical | REQ-20260518-006 |
| [TASK-20260519-001 Gemini 与 MiMo 功能性服务 API 真实 Smoke](done/TASK-20260519-001-functional-real-smoke-gemini-mimo.md) | Done | Critical | REQ-20260519-001 / TASK-20260514-031 |
| [TASK-20260519-002 Codex 功能性服务 API 最高优先推进](done/TASK-20260519-002-codex-priority-functional-service-api.md) | Done | Critical | REQ-20260519-002 |
| [TASK-20260519-002-01 Codex 功能性服务 API 事实源优先收紧](done/TASK-20260519-002-01-codex-functional-truth-source-priority.md) | Done | Critical | TASK-20260519-002 / TASK-20260514-029 |
| [TASK-20260519-002-02 Codex Smoke、Record/Replay 与成本防护复核](done/TASK-20260519-002-02-codex-smoke-record-replay-priority.md) | Done | Critical | TASK-20260519-002 / TASK-20260514-031 |
| [TASK-20260514-016 对话与 Tools 功能性 API 覆盖总控父任务](backlog/TASK-20260514-016-functional-service-api-coverage-parent.md) | Backlog | Critical | REQ-20260514-009 / REQ-20260518-005 |
| [TASK-20260514-013 OpenAI Chat/Responses 参数保真与原生 Responses 边界](done/TASK-20260514-013-openai-chat-responses-native-parity.md) | Done | High | TASK-20260514-016 / REP-20260518 |
| [TASK-20260514-017 OpenAI Chat Completions 参数与对象生命周期](done/TASK-20260514-017-openai-chat-completions-full-parity.md) | Done | Critical | TASK-20260514-016 / 013 |
| [TASK-20260514-018 OpenAI Responses 原生执行器与生命周期](done/TASK-20260514-018-openai-responses-native-lifecycle.md) | Done | Critical | TASK-20260514-016 / 013 |
| [TASK-20260514-019 OpenAI Conversations、Webhooks 与 Responses 工具生态](done/TASK-20260514-019-openai-conversations-webhooks-tools.md) | Done | High | TASK-20260514-016 / 013 |
| [TASK-20260514-020 OpenAI 多模态支撑参数边界收紧](done/TASK-20260514-020-openai-multimodal-supporting-parameters.md) | Done | High | TASK-20260514-016 / 014 / REP-20260521 |
| [TASK-20260514-021 OpenAI Files、Uploads、Models 功能性支撑面](done/TASK-20260514-021-openai-files-uploads-models-functional-support.md) | Done | High | TASK-20260514-016 / 014 / REP-20260521 |
| [TASK-20260514-023 OpenAI Vector Stores 对话 RAG 支撑面](done/TASK-20260514-023-openai-vector-stores-full-stack.md) | Done | High | TASK-20260514-016 / 014；仅保留 gateway-local RAG 后端支撑，不代表保留独立向量控制台或 Responses hosted file_search 本地成功 |
| [TASK-20260517-003 OpenAI Vector Stores 本地 Lifecycle 基线](done/TASK-20260517-003-openai-vector-stores-local-lifecycle-baseline.md) | Done | High | TASK-20260514-023；控制台已下线，后端支撑面暂保留 |
| [TASK-20260517-004 OpenAI Vector Store Files 本地 Attachment Lifecycle 基线](done/TASK-20260517-004-openai-vector-store-files-local-attachment-lifecycle.md) | Done | High | TASK-20260514-023；控制台已下线，后端支撑面暂保留 |
| [TASK-20260517-005 OpenAI Vector Store File Batches 本地 Lifecycle 基线](done/TASK-20260517-005-openai-vector-store-file-batches-local-lifecycle.md) | Done | High | TASK-20260514-023；控制台已下线，后端支撑面暂保留 |
| [TASK-20260518-001 OpenAI Vector Store File Content 本地读取基线](done/TASK-20260518-001-openai-vector-store-file-content-local-read-baseline.md) | Done | High | TASK-20260514-023；控制台已下线，后端支撑面暂保留 |
| [TASK-20260518-002 OpenAI Vector Store Search 本地文本检索基线](done/TASK-20260518-002-openai-vector-store-search-local-text-baseline.md) | Done | High | TASK-20260514-023；控制台已下线，后端支撑面暂保留 |
| [TASK-20260518-003 OpenAI Responses File Search 本地 Vector Store 绑定基线](done/TASK-20260518-003-openai-responses-file-search-local-vector-store-binding.md) | Done | High | TASK-20260514-023；历史基线已由 TASK-20260524-001-04 supersede，当前 Responses hosted file_search 必须 native-required |
| [TASK-20260518-004 OpenAI Vector Store 本地 Ingestion 产物基线](done/TASK-20260518-004-openai-vector-store-local-ingestion-artifact-baseline.md) | Done | High | TASK-20260514-023；控制台已下线，后端支撑面暂保留 |
| [TASK-20260514-029 对话与 Tools OpenAPI、Catalog、Conformance 与 SDK 事实源统一](done/TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md) | Done | Critical | TASK-20260514-016 / 015 / TASK-20260519-002 |
| [TASK-20260514-029-01 功能性服务 API Coverage Matrix Source](done/TASK-20260514-029-01-functional-service-api-coverage-matrix-source.md) | Done | Critical | TASK-20260514-029 |
| [TASK-20260514-029-02 Codex OpenAPI, Catalog & Conformance 深度融合](done/TASK-20260514-029-02-codex-openapi-catalog-conformance.md) | Done | Critical | TASK-20260514-029 |
| [TASK-20260514-029-03 Codex 运营控制台体验对标与 Session 恢复桥接](done/TASK-20260514-029-03-codex-console-session-recovery.md) | Done | Critical | TASK-20260514-029 |
| [TASK-20260514-029-04 OpenAPI 路径补全与 SDK 三模式示例归口](done/TASK-20260514-029-04-openapi-coverage-sdk-finalization.md) | Done | Critical | TASK-20260514-029 / REP-20260521 |
| [TASK-20260514-030 OpenAI 横切协议兼容](done/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md) | Done | Critical | TASK-20260514-016 |
| [TASK-20260514-031 OpenAI 真实 Smoke 与认证成本防护](backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md) | Backlog | Critical | TASK-20260514-016 / 015 |
| [TASK-20260521-001 功能性服务 API Backlog 收口与优先级清理](done/TASK-20260521-001-functional-scope-backlog-closeout.md) | Done | Critical | REP-20260521 |
| [TASK-20260519-001-01 Smoke 范围矩阵与 Provider Auth 设计](done/TASK-20260519-001-01-smoke-scope-provider-auth-design.md) | Done | Critical | TASK-20260519-001 |
| [TASK-20260519-001-02 Gemini/MiMo Provider-aware Smoke Runner](done/TASK-20260519-001-02-gemini-mimo-provider-aware-smoke-runner.md) | Done | Critical | TASK-20260519-001 |
| [TASK-20260519-001-03 Record/Replay、脱敏与成本防护验证](done/TASK-20260519-001-03-smoke-record-replay-redaction-budget.md) | Done | High | TASK-20260519-001 |
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
| [TASK-20260523-011 MiMo 资源能力矩阵与 OpenAI-compatible 实现对齐](done/TASK-20260523-011-mimo-resource-capability-matrix-alignment.md) | Done | High | REQ-20260523-010 |
| [TASK-20260523-012 凭证与厂商领域模型梳理父任务](done/TASK-20260523-012-credential-provider-domain-model-clarification-parent.md) | Done | High | REQ-20260523-011 |
| [TASK-20260523-013 厂商目录 UI 收敛为 Vendor -> Endpoint -> Group -> Credential](in-progress/TASK-20260523-013-provider-catalog-vendor-endpoint-group-credential-ui.md) | In Progress | High | REP-20260523 |
| [TASK-20260523-014 厂商领域 API 命名与对象边界收敛](in-progress/TASK-20260523-014-provider-domain-api-naming-boundary.md) | In Progress | High | REP-20260523 |
| [TASK-20260523-015 能力快照与刷新语义重构](backlog/TASK-20260523-015-capability-snapshot-refresh-semantics-redesign.md) | Backlog | Medium | REP-20260523 |
| [TASK-20260523-016 账号分组分类、入口覆盖反推与 Distributed Key 授权展示](backlog/TASK-20260523-016-account-group-taxonomy-endpoint-coverage.md) | Backlog | High | REP-20260523 |
| [TASK-20260523-017 控制台 Apple-level UI/UX 精修](done/TASK-20260523-017-console-apple-level-uiux-polish.md) | Done | High | REQ-20260523-012 |
| [TASK-20260524-001 头部自有模型厂商 Native 与无损翻译网关总控父任务](in-progress/TASK-20260524-001-head-provider-native-lossless-gateway-parent.md) | In Progress | Critical | REQ-20260524-001；当前口径为头部自研模型厂商 native/provider-specific profile + lossless-only hard-fail |
| [TASK-20260524-001-01 核心厂商目录收敛与非模型 Provider 清理](done/TASK-20260524-001-01-provider-catalog-core-vendor-prune.md) | Done | Critical | TASK-20260524-001 |
| [TASK-20260524-001-02 支持厂商 Native Adapter 最小契约](done/TASK-20260524-001-02-native-adapter-minimum-contract.md) | Done | Critical | TASK-20260524-001；nativeAdapterContract、provider-specific smoke protocol/path adapter、Admin/Public 透出与 contract drift 验证已闭环 |
| [TASK-20260524-001-03 跨协议资源属性无损翻译矩阵](done/TASK-20260524-001-03-lossless-translation-matrix.md) | Done | Critical | TASK-20260524-001；Lossless Matrix、blocked plan、mapper negative、smoke 分类、public docs/OpenAPI 与 conformance 主线验证已闭环 |
| [TASK-20260524-001-04 不可对应能力直接失败与假成功清理](done/TASK-20260524-001-04-unsupported-capability-hard-fail.md) | Done | Critical | TASK-20260524-001；Responses compact/input_tokens/file_search、resource blocked plan、media native route required 与 Realtime current-down 口径已闭环 |
| [TASK-20260524-001-05 文档、OpenAPI 与 Smoke 范围对齐](done/TASK-20260524-001-05-docs-openapi-smoke-alignment.md) | Done | High | TASK-20260524-001 |
| [TASK-20260524-001-06 Provider-specific OpenAI-compatible Runtime Profile 拆分](done/TASK-20260524-001-06-provider-specific-runtime-profile-split.md) | Done | Critical | TASK-20260524-001；MiMo/DeepSeek/xAI runtime profile、smoke fixture、interop debug、observability 与非持久化迁移记录已闭环 |
| [TASK-20260524-001-07 Cohere / Jina Native Executor 与 Smoke 闭环](in-progress/TASK-20260524-001-07-native-executor-smoke-for-embed-rerank-providers.md) | In Progress | High | TASK-20260524-001；executor/smoke/test、fixture 样本与结构证据已落地，待真实 key live smoke |
| [TASK-20260524-001-08 Degraded 能力层与无损翻译矩阵隔离](done/TASK-20260524-001-08-degraded-capability-layer-isolation.md) | Done | Critical | TASK-20260524-001；已完成 blocked plan 守门、catalog 旧 hint 迁移、错误规则阻断语义与 targeted 回归 |
| [TASK-20260524-002 删除 Admin 厂商 OAuth 连接并配置化 Portal 社交 OAuth](done/TASK-20260524-002-admin-oauth-removal-portal-social-oauth-config-parent.md) | Done | Critical | REQ-20260524-002；删除误导性上游厂商 OAuth 连接，保留并配置化 Portal 社交 OAuth，已补注册渠道治理与绑定入口 |
| [TASK-20260524-002-01 删除 Admin 厂商 OAuth 连接入口](done/TASK-20260524-002-01-remove-admin-upstream-oauth-connection.md) | Done | Critical | TASK-20260524-002 |
| [TASK-20260524-002-02 Portal 社交 OAuth 后台配置](done/TASK-20260524-002-02-portal-social-oauth-admin-config.md) | Done | High | TASK-20260524-002 |
| [TASK-20260524-002-03 Portal 已注册用户社交 OAuth 绑定](done/TASK-20260524-002-03-portal-social-oauth-account-binding.md) | Done | High | TASK-20260524-002 |
| [TASK-20260524-002-04 Portal 注册渠道策略与邀请码渠道](done/TASK-20260524-002-04-portal-registration-channel-policy.md) | Done | High | TASK-20260524-002 |
| [TASK-20260524-003 Portal 完整邀请码系统](done/TASK-20260524-003-portal-invitation-code-system-parent.md) | Done | High | REQ-20260524-003；将邀请码从注册策略内存白名单升级为持久化库存、核销记录和 Admin 管理 |
| [TASK-20260524-003-01 邀请码数据模型与 Admin 服务](done/TASK-20260524-003-01-invitation-code-data-service.md) | Done | High | TASK-20260524-003 |
| [TASK-20260524-003-02 Portal 注册邀请码核销](done/TASK-20260524-003-02-portal-registration-invitation-redemption.md) | Done | High | TASK-20260524-003 |
| [TASK-20260524-003-03 Admin 邀请码管理页面](done/TASK-20260524-003-03-admin-invitation-code-ui.md) | Done | High | TASK-20260524-003 |
| [TASK-20260524-004 邀请码归属、OAuth 首次注册与奖励赠品](done/TASK-20260524-004-invitation-code-owner-oauth-rewards-parent.md) | Done | High | REQ-20260524-004；把邀请码升级为所有首次注册渠道共享的归属与奖励载体 |
| [TASK-20260524-004-01 邀请码归属人与奖励后端模型](done/TASK-20260524-004-01-invitation-code-owner-reward-backend.md) | Done | High | TASK-20260524-004 |
| [TASK-20260524-004-02 社交 OAuth 首次注册邀请码支持](done/TASK-20260524-004-02-social-oauth-invitation-registration.md) | Done | High | TASK-20260524-004 |
| [TASK-20260524-004-03 邀请码归属奖励与 OAuth 注册前端](done/TASK-20260524-004-03-invitation-owner-reward-frontend.md) | Done | High | TASK-20260524-004 |
| [TASK-20260524-005 邀请增长系统完整化](done/TASK-20260524-005-invitation-growth-system-parent.md) | Done | High | REQ-20260524-005；套餐、返佣、邀请层级、排行榜与访问组/账号组边界收口已闭环 |
| [TASK-20260524-005-01 增长奖励后端模型与核销服务](done/TASK-20260524-005-01-growth-backend-model-service.md) | Done | High | TASK-20260524-005 |
| [TASK-20260524-005-02 增长系统 Admin 与 Portal API](done/TASK-20260524-005-02-growth-admin-portal-api.md) | Done | High | TASK-20260524-005 |
| [TASK-20260524-005-03 增长系统前端与访问组/账号组命名收口](done/TASK-20260524-005-03-growth-frontend-and-naming.md) | Done | High | TASK-20260524-005；Admin 邀请码奖励字段/排行榜、Portal 我的邀请页面和核心命名收口已完成，`bun run typecheck` 通过 |
| [TASK-20260525-001 Admin 邀请树展示补齐父任务](done/TASK-20260525-001-admin-invitation-tree-ui-parent.md) | Done | High | REQ-20260525-001；补齐 Admin 邀请树查询与展示，focused test 与 typecheck 通过 |
| [TASK-20260525-001-01 Admin 邀请树前端接入](done/TASK-20260525-001-01-admin-invitation-tree-frontend.md) | Done | High | TASK-20260525-001 |
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

## 已下线控制台能力历史归档

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260507-003 Codex 账号分组热切换、负载均衡与失败恢复 UI](done/TASK-20260507-003-codex-account-group-hot-switch-failover-ui.md) | Done | High | REP-20260507；旧控制台账号分组运行态历史实现，当前不再作为现役产品面 |
| [TASK-20260507-004 Codex 实时请求、Usage 与过滤命中观测台](done/TASK-20260507-004-codex-realtime-usage-filter-observability.md) | Done | Medium | REP-20260507；`live/realtime` 观测台历史实现，现仅保留实现证据 |
| [TASK-20260507-011 Admin Console 命名空间迁移与旧路由兼容](done/TASK-20260507-011-admin-console-namespace-legacy-route-migration.md) | Done | High | REQ-20260507-001；旧控制台路由迁移历史记录，不再代表现役能力入口 |
| [TASK-20260506-009 Provider 生态广度与 Conformance 完善](done/TASK-20260506-009-provider-ecosystem-conformance.md) | Done | High | REP-20260506；涉及 provider-sites、capability matrix、站点档案的历史实现归档 |
| [TASK-20260513-002 主流 API 翻译 Conformance Matrix 与缺口硬化](done/TASK-20260513-002-mainstream-api-translation-conformance-matrix.md) | Done | High | REQ-20260513-002；Native Compatibility 控制台展示已退役，保留后端翻译边界证据 |
| [TASK-20260513-005 Provider/Media/价格同步参考差距补齐](done/TASK-20260513-005-provider-media-pricing-reference-gap-closure.md) | Done | Medium | REQ-20260513-003；`provider-reference-gap` 控制台与 API 历史能力归档 |

## 排期管理任务

| 任务 | 状态 | 优先级 | 来源 |
| --- | --- | --- | --- |
| [TASK-20260507-015 当前 Backlog 优先级队列编排](done/TASK-20260507-015-backlog-priority-roadmap.md) | Done | High | User Request |

## 本地化流程任务

- [REQ-20260501-001 本地化协作流程迁移](../docs/requirements/REQ-20260501-001-local-workflow-migration.md)
- [TASK-20260505-001 Notion/Linear 回迁到本地](done/TASK-20260505-001-notion-linear-back-migration.md) | Done | High | User Request
