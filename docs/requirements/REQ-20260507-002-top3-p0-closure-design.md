# REQ-20260507-002 前三 P0 任务闭环设计

状态：Done  
日期：2026-05-07  
关联任务：

- [TASK-20260507-002 Codex CLI 请求保真、Session 粘性与反向代理兼容](../../tasks/done/TASK-20260507-002-codex-cli-request-fidelity-sticky-session.md)
- [TASK-20260507-009 Portal/Admin 路由、身份与产品面边界基线](../../tasks/done/TASK-20260507-009-portal-admin-route-identity-boundary.md)
- [TASK-20260507-013 Portal/Admin API 权限隔离、审计与越权回归](../../tasks/done/TASK-20260507-013-portal-admin-permission-audit-regression.md)

## 背景

当前 Backlog 优先级队列已经将 `TASK-20260507-002`、`TASK-20260507-009`、`TASK-20260507-013` 排为前三个 P0 任务。它们分别对应 Codex 反代协议地基、Portal/Admin 产品面边界和 API 权限安全边界。若跳过这三项直接做 UI 向导或真实账号 adapter，后续会在请求元数据、路由命名和权限隔离上产生返工。

## 目标

- 让 Codex CLI 关键请求头在 `/v1/responses` ingress 层被规范采集、脱敏落库，并作为 session affinity 输入。
- 建立前端路由产品面事实源，明确 Public、Portal、Console 三类界面边界和旧管理路由兼容策略。
- 为 Portal/Admin API 建立越权回归和审计基线，避免普通用户访问管理面或跨用户数据。
- 完成实现、测试、文档和任务归档闭环。

## 范围

- 后端 Codex CLI header canonicalization、session affinity metadata、request log/trace 可解释字段。
- Codex CLI conformance tests，覆盖 header 保真、client family 识别、SSE 和 session 粘性。
- Nginx/反向代理兼容文档，尤其是 `underscores_in_headers on;`。
- 前端 route surface 常量、旧管理路由到 `/console/*` 的映射事实源，以及对应测试。
- Portal/Admin API 权限矩阵、关键 controller guard 和越权回归测试。

## 非目标

- 本批次不实现真实 Codex 官方账号 adapter；该能力由 `TASK-20260507-001` 承接。
- 本批次不完成完整 Console UI 重构；`TASK-20260507-011/012` 承接路由迁移和工作台。
- 本批次不完成社区用户 Codex 向导；`TASK-20260507-005/010` 承接。
- 不在 URL、localStorage、metadata、日志或 trace 中保存长期 secret、完整 prompt 或完整 session 内容。

## 详细设计

### Codex CLI 请求保真与 Session 粘性

1. 在 `/v1/responses` ingress 层读取 `openai-beta`、`originator`、`session_id`、`conversation_id`、`user-agent` 等 Codex CLI 关键 header。
2. 将 header 归一化为 canonical metadata，只保留必要、脱敏、长度受控的字段。
3. session affinity key 优先级为 `session_id`、`conversation_id`、client instance/workspace hint fallback。
4. 对 session affinity key 做 fingerprint 或 mask，避免 request log 暴露完整 session。
5. 将 affinity key 接入账号选择上下文或 routing metadata，确保相同 session 在可用窗口内具备稳定选路依据。
6. 增加 conformance tests 覆盖 Codex CLI user-agent、underscore header、SSE 响应和 metadata 落库。

### Portal/Admin 产品面边界

1. 在前端建立 route surface 事实源，标记 `public`、`portal`、`console`。
2. 输出 legacy admin route 到 `/console/*` 的映射常量，后续迁移路由时可复用。
3. 明确 `/portal/login` 与 `/login` 不共享用户身份语义。
4. 为导航、breadcrumb、测试 helper 提供可查询的产品面定义。
5. 补充文档说明 Portal 不暴露上游账号、Provider secret、账号池内部候选、全局日志和全局策略。

### Portal/Admin API 权限隔离与审计

1. 梳理现有 `/portal/*` 与 `/admin/*` controller。
2. Portal API 必须绑定当前 portal user 或 team scope，不信任请求方传入任意 userId。
3. Admin API 必须要求 admin 身份；敏感代管或配置操作写入 system event 或审计记录。
4. request log、usage、trace 输出不得包含长期 secret 明文。
5. 增加回归测试覆盖匿名访问、普通用户访问 Admin API、跨用户资源访问和管理员敏感操作审计。

## 风险

- 下划线 header 在 Nginx 中默认可能被丢弃，需要同时落文档和 smoke。
- session 粘性不能长期锁死异常账号，必须能过期或降级。
- 仅前端隐藏路由不是安全边界，Portal/Admin API 必须有后端回归。
- 当前工作区已有大量前端改动，本批次需要小心避开无关重构。

## 验收标准

- 三个任务文件移动到 `tasks/in-progress/`，实现完成后移动到 `tasks/done/`。
- Codex CLI header 和 session affinity 有后端测试覆盖。
- route surface 和 legacy console route map 有前端单元测试覆盖。
- Portal/Admin 越权路径有后端测试覆盖。
- 文档记录实现结果、验证命令、遗留问题和后续建议。

## 实现结果

- 新增 `CanonicalRequestMetadata`，`/v1/responses` ingress 采集 `openai-beta`、`originator`、`session_id`、`conversation_id`、`User-Agent`、client instance 与 workspace hint，并将 session affinity 指纹化后进入 canonical request。
- `RouteSelectionRequest`、`RouteSelectionResult`、`CredentialMaterialResolver` 与 `AccountSelectionService` 已支持基于脱敏 session affinity key 的账号粘性；旧构造器保持兼容。
- `GatewayChatExecutionService` 在 route body 中写入 `x_ai_gateway_ingress` 解释性 metadata，request log 新增 `session_affinity_source/session_affinity_key` 字段，并补充 Liquibase `0047` 迁移。
- 前端新增 `web/src/app/route-surfaces.ts`，明确 `public`、`portal`、`console` 三类 surface，并提供 legacy console path 到未来 `/console/*` 的 canonical mapping。
- 新增 `PortalApiSecurityBoundaryWebFilter`，对非公开 `/portal/*` API 统一要求 `portalUserId` session，并对匿名越权访问写入 `PORTAL_API_BOUNDARY` 审计。
- 修复 `LiveSessionService` 多构造器在 Spring 7 全量上下文中缺少显式 `@Autowired` 的启动问题，不改变业务逻辑。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionServiceTests" --tests "com.prodigalgal.xaigateway.infra.config.web.PortalApiSecurityBoundaryWebFilterTests"`：通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestLifecycleServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityAsyncPersistenceServiceTests"`：通过。
- `.\gradlew.bat test`：通过，580 tests completed，4 skipped。
- `bun run typecheck`：通过。
- `bun run test -- src/app/route-surfaces.test.ts`：通过，11 tests。
- `bun run test`：通过，50 files / 105 tests。

## 遗留问题

- `TASK-20260507-001` 仍需接入真实 Codex 官方账号 adapter、配额刷新和真实反代 smoke。
- `TASK-20260507-011/012` 后续可基于本批次 route surface 事实源推进 `/console/*` 命名空间迁移和角色化工作台。
- Nginx `underscores_in_headers on;` 的部署说明仍建议在后续生产部署文档任务中集中补充到运维手册。
