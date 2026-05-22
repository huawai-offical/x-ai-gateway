# REQ-20260521-011 控制台瘦身、品牌 Logo、上游凭证统一与热数据缓存

状态：Done  
日期：2026-05-21  
上游来源：用户指令“删除变更维护运维功能、删除首页状态/价格/文档、绘制项目 logo、上游凭证避免手填、修复已录入凭证显示不全、表格保持扁平、加强 Redis、删除重复错误规则导航、请求日志不单列 Codex 请求、xx id 尽量模糊搜索下拉”

## 背景

前置任务已经完成控制台重复功能第一轮下线、深浅主题、导航重分组、系统参数汉化和 Codex `auth.json` 导入。但现役界面仍残留几类问题：

1. “变更维护”系列属于旧运维编排面，与当前智能运维主线重复，用户明确要求删除。
2. 公开首页仍保留状态、价格、文档入口，当前产品主线不需要这些独立入口。
3. 浏览器标签页、公开首页和控制台仍使用默认或纯文字品牌，不利于识别。
4. 上游凭证页只展示 `/admin/credentials` 静态密钥，未展示 `/admin/accounts` 中已导入的 Codex `auth.json` 账号，导致 Codex 15 个账号不可见。
5. 上游凭证支持模型、代理 ID、TLS 指纹 ID 等字段仍存在手填或弱选择体验，容易输错。
6. 凭证表格操作按钮过多，换行后拉高行高，不符合扁平列表预期。
7. 请求日志存在 Codex 请求独立观测表，与统一请求日志方向重复。
8. 项目热数据更新频繁，需要进一步引入 Redis 承接高并发读写，并逐步同步到 PostgreSQL。

## 目标

- 删除控制台“变更维护”系列导航、路由和可见入口；旧路径统一跳转到现役总览或相关主路径。
- 删除公开首页的状态、价格、文档入口和独立公开路由，只保留现役需要的入口。
- 绘制并接入项目 Logo，用于 favicon、公开首页品牌和控制后台品牌。
- 上游凭证页统一展示 API Key/Secret 静态凭证与 Codex `auth.json` 账号，修复只显示 Gemini 5 个的问题。
- 上游凭证支持模型改为搜索 + 勾选，不再让用户手填一行一个模型。
- 代理 ID、TLS 指纹 ID 等可从系统已有列表获取的字段，改为模糊搜索下拉选择。
- 凭证表格保持一行扁平展示，将编辑、启停、刷新、删除、冻结、详情等操作收敛到详情弹窗。
- 删除重复的错误规则导航入口，保留治理策略中的统一规则管理。
- 请求日志不再单独列出 Codex 请求面板，Codex 请求只作为统一请求日志的筛选维度或详情字段。
- 梳理并落地首批 Redis 热数据增强点，明确 Redis 写入、读取和 PostgreSQL 回写边界。

## 范围

- `web/src/app/navigation.ts`
- `web/src/app/router.tsx`
- `web/src/app/route-surfaces.ts`
- `web/src/features/public/`
- `web/src/features/credentials/`
- `web/src/features/request-logs/`
- `web/src/features/error-rules/`
- `web/src/components/app/app-shell.tsx`
- `web/public/` 与 favicon/logo 相关资源
- 上游凭证、账号、模型目录、代理池、TLS 指纹相关前后端接口调用
- Redis 热数据写入与 PostgreSQL 回写相关 service、配置、测试

## 非目标

- 不删除 `vector/files/file_search` 主线支撑 API。
- 不删除 Codex `auth.json` 入库、刷新与反代所需后端能力。
- 不把真实 API Key、AT、RT、`auth.json` 内容写入仓库、文档或测试 fixture。
- 不恢复旧的“官方账号运行态”独立产品面。
- 不把所有系统字段一次性改造成复杂配置中心；本轮优先处理用户指出的上游凭证主路径。

## 风险

- 旧运维路由可能被测试、命令面板或 legacy redirect 断言引用，需要同步收口。
- 公开路由删除后，门户或旧链接若仍跳转 `/docs`、`/pricing`、`/status`，需要提供合理重定向。
- 上游凭证统一列表涉及两类不同实体，详情弹窗需要区分静态凭证与 OAuth/Codex 账号操作边界。
- 模型、代理、TLS 指纹可搜索选择依赖已有数据列表；列表为空时仍需可理解地展示为空状态。
- Redis 写入增强若一次性改动过大，可能影响账务、日志、配额等一致性；本轮采用首批热点和可回写边界推进。

## 验收标准

1. 控制台侧栏不再出现“变更维护”一级分组及其子菜单。
2. `/console/operations/*`、`/console/error-rules` 等重复旧路径不再加载旧页面，改为跳转现役主路径。
3. 公开首页不再展示状态、价格、文档入口；对应公开路由不再作为独立页面暴露。
4. favicon、公开首页和控制台品牌展示统一使用新 Logo。
5. 上游凭证页同时显示已录入的 15 个 Codex `auth.json` 账号和 5 个 Gemini 静态凭证。
6. 上游凭证创建/编辑的支持模型通过搜索 + 勾选选择，不出现“逐行手填模型”的用户输入面。
7. 上游凭证创建/编辑的代理和 TLS 指纹通过模糊搜索下拉选择。
8. 凭证列表单行高度稳定，操作按钮不再把表格撑成竖排。
9. 请求日志页不再出现 Codex 请求独立表格。
10. Redis 首批增强点有实现、测试或明确迁移边界，并记录 PostgreSQL 回写策略。

## 测试边界

- 前端：`npm run typecheck`
- 前端：`navigation`、`route-surfaces`、`credentials`、`request-logs`、`public` 相关定向 vitest
- 后端：Redis 热数据相关 service 定向测试
- 浏览器：验证 `/console/credentials`、`/console/request-logs`、公开首页、favicon/logo、旧路由跳转
- 检索：删除的导航与公开入口不再出现在现役 UI 文案中；敏感 token/key 不进入仓库内容

## 实现结果

- 控制台侧栏已删除“变更维护”分组和重复“错误规则”入口；`/console/error-rules` 重定向到治理策略，`/console/operations/*` 重定向到智能运维总览。
- 公开首页已删除状态、价格、文档入口；`/docs`、`/pricing`、`/status` 和公开 OpenAPI JSON 旧入口统一回到首页。
- 已新增 `web/public/logo.svg` 并替换 `favicon.svg`，公开首页和控制台侧栏均使用同一 Logo。
- 后端新增 `/admin/credentials/inventory` 统一投影，合并静态 `UpstreamCredentialEntity` 与 `UpstreamAccountEntity`，前端“已录入凭证”改为统一清单，避免只显示 Gemini 静态凭证。
- 上游凭证创建/编辑中的支持模型改为搜索 + 勾选；代理 ID、TLS 指纹 ID 改为基于 `/admin/network/proxies` 和 `/admin/network/tls-profiles` 的搜索下拉选择。
- 凭证列表操作收敛到详情弹窗，表格主行保持扁平展示。
- 请求日志页删除 Codex 独立观测面板、独立表格、脱敏包和恢复命令入口，Codex 相关客户端/会话信息回到统一请求日志列。
- Redis 首批增强落在凭证/账号运行指标链路：请求完成后将高频统计写入 Redis observability 队列，批量合并后回写 PostgreSQL；Redis 不作为唯一事实源，队列失败时仍回退同步写库。

## 验证结果

- 通过：`npm test -- --run src/features/request-logs/request-logs-page.test.tsx src/features/credentials/credentials-page.test.tsx src/app/navigation.test.ts src/app/operations-router.test.tsx src/app/public-router.test.tsx src/features/public/public-pages.test.tsx`
- 通过：`npm run typecheck`
- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.UpstreamCredentialInventoryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityAsyncPersistenceServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestLifecycleServiceTests"`

## 关联任务

- [TASK-20260521-011](../../tasks/done/TASK-20260521-011-console-surface-prune-logo-credential-redis.md)
- [TASK-20260521-011-01](../../tasks/done/TASK-20260521-011-01-console-public-route-prune.md)
- [TASK-20260521-011-02](../../tasks/done/TASK-20260521-011-02-brand-logo-favicon-console-public.md)
- [TASK-20260521-011-03](../../tasks/done/TASK-20260521-011-03-upstream-credential-unified-list-selectors.md)
- [TASK-20260521-011-04](../../tasks/done/TASK-20260521-011-04-request-log-codex-panel-prune.md)
- [TASK-20260521-011-05](../../tasks/done/TASK-20260521-011-05-redis-hot-data-writeback.md)
