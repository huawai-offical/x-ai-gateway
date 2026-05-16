# TASK-20260507-002 Codex CLI 请求保真、Session 粘性与反向代理兼容

状态：Done  
优先级：High  
排期：P0-01  
来源：[REP-20260507 Codex 账户反代与 UI/UX 深度差距分析](../../docs/reports/REP-20260507-codex-proxy-uiux-gap-analysis.md)
关联需求：[REQ-20260507-002 前三 P0 任务闭环设计](../../docs/requirements/REQ-20260507-002-top3-p0-closure-design.md)

## 背景

`cli_proxy-master` 的 Codex smoke 明确模拟 Codex CLI 请求头：`openai-beta: responses=experimental`、`originator: codex_cli_rs`、`conversation_id`、`session_id` 和 Codex CLI user-agent。`sub2api-main` 也提醒 Nginx 默认会丢弃包含下划线的请求头，影响多账号粘性。当前项目已有 `/v1/responses` 和 client family resolver，但还没有把 Codex CLI 请求保真、session 粘性和反代兼容做成显式契约。

## 目标

- 建立 Codex CLI 请求头契约和 conformance 测试。
- 保留 `session_id`、`conversation_id`、`originator`、`openai-beta` 等关键语义，用于账号粘性和 trace。
- 明确 Nginx、Cloudflare、Spring header binding 等反向代理兼容要求。
- 将 session 粘性接入账号池选择或 routing context。

## 详细设计

- 在 OpenAI Responses ingress 层记录 Codex CLI headers 到 canonical request metadata，不泄露敏感 authorization。
- 将 `session_id` 或 `conversation_id` 归一化为 session affinity key，优先用于 `AccountSelectionService` 的 Codex 账号粘性。
- 增加部署文档：Nginx 需要 `underscores_in_headers on;`，并给出 smoke 命令。
- 增加 contract tests：Codex CLI user-agent + header 能被识别为 `GatewayClientFamily.CODEX`，streaming response 保持 SSE。
- 在 request log/trace 中展示脱敏后的 session affinity key 和 client family。

## 验收标准

- Codex CLI 关键请求头不会在 ingress 层丢失。
- 相同 session affinity key 在同一窗口内可稳定命中同一账号或给出解释性降级。
- Nginx 兼容配置和 smoke 命令写入文档。
- 测试覆盖 header 保真、family resolver、SSE 和 session 粘性。

## 风险

- 不能把完整 session 内容或用户 prompt 写入 routing metadata。
- session 粘性必须可过期、可清理，避免长期锁死到异常账号。

## 实现结果

- `/v1/responses` 已采集 Codex CLI 关键 headers，并写入 `CanonicalRequestMetadata`。
- `session_id` 优先、`conversation_id` 次之、client instance 兜底生成脱敏 `sessionAffinityKey`，不会记录原始 session 内容。
- `RouteSelectionRequest/Result`、`CredentialMaterialResolver` 与 `AccountSelectionService` 已接入 session 粘性，Redis sticky key 追加 `:session:{hash}`。
- `GatewayChatExecutionService` route body 增加 `x_ai_gateway_ingress`，request log 通过 `0047` 迁移记录 `session_affinity_source/session_affinity_key`。

## 验证记录

- `OpenAiResponsesControllerTests.shouldPreserveCodexCliIngressMetadataForRoutingAndTracing` 覆盖 header 保真、Codex family、metadata 脱敏。
- `AccountSelectionServiceTests.shouldScopeStickyAccountBySessionAffinityKey` 覆盖 session 维度 sticky key。
- `.\gradlew.bat test`：通过。

## 遗留问题

- 真实 Codex 官方账号 adapter、真实账号 smoke 与配额刷新继续由 `TASK-20260507-001` 承接。
- Nginx 下划线 header 配置建议后续并入部署手册统一说明。
