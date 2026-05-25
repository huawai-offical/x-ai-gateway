# Realtime Provider WebSocket Adapter

> 2026-05-24 当前状态：历史归档 / 当前下线。本文记录的是旧 Realtime WebSocket adapter、连接池和 conformance 基线，不代表当前公开 API 可用能力。按照 REQ-20260524-001，`/v1/realtime` 只有在真实 provider native route、真实 WebSocket/WebRTC/SIP 语义和 smoke 证据重新闭环后才能重新公开；当前不得用本地 Live Session 事件代理、`session.created` 或 metadata 标记伪造成 Realtime 成功。

关联需求：[REQ-20260506-002 第八批高优先级任务闭环设计](requirements/REQ-20260506-002-eighth-priority-task-closure-design.md)  
生产硬化需求：[REQ-20260506-010 Realtime 与 Media 生产硬化](requirements/REQ-20260506-010-realtime-media-production-hardening.md)  
关联任务：[TASK-20260501-024 真实 Realtime Provider WebSocket Adapter](../tasks/done/TASK-20260501-024-realtime-real-provider-websocket.md)
当前增强任务：[TASK-20260506-011 Realtime 与 Media 生产硬化](../tasks/done/TASK-20260506-011-realtime-media-production-hardening.md)
连接池增强任务：[TASK-20260506-019 Realtime 长连接池与专有 Media Adapter](../tasks/done/TASK-20260506-019-realtime-pool-media-adapters.md)
OpenAI-compatible WebSocket 入口任务：[TASK-20260516-008](../tasks/done/TASK-20260516-008-openai-realtime-websocket-ingress-event-proxy.md)

## 实现范围

- `OpenAiRealtimeRuntimeAdapter` 与 `GeminiLiveRuntimeAdapter` 统一继承 provider WebSocket runtime adapter。
- adapter transport 明确为 `websocket`，metadata 记录 provider、runtime state、websocket state、auth scheme、upstream WebSocket URL 和 upstream resume handle。
- OpenAI Realtime upstream URL：`wss://api.openai.com/v1/realtime`。
- Gemini Live upstream URL：`wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent`。
- provider WebSocket adapter metadata 增加：
  - `binaryFramePolicy`: `json_control_plus_binary_audio_frames`
  - `errorSchema`: `gateway_realtime_error_v1`
  - `retryPolicy`: `resume_token_with_backoff`
  - `closePolicy`: `client_close_or_provider_close`
  - `latencyMs`、`usageInputAudioBytes`、`usageOutputAudioBytes`
  - `normalizedProviderErrorCode`、`retryable`、`retryAfterMs`
  - `closeReason`、`cancelSemantic`
- `LiveSessionConnectionPool` 提供单进程连接池租约：
  - 按 `distributed_key:{id}` 作为 tenant 维度隔离。
  - 默认每 tenant 最多 4 个活跃连接。
  - 支持 acquire、touch、release、cancel、sweepExpired。
  - connect 失败会释放 lease，close 会释放 lease。
- Live Session metadata 增加：
  - `connectionPoolLeaseId`
  - `connectionPoolTenant`
  - `connectionPoolState`
  - `connectionPoolExpiresAt`
  - `connectionPoolActive`
  - `connectionPoolMaxPerTenant`

## OpenAI-compatible WebSocket 入口（历史归档）

- `/v1/realtime?model=...` 曾映射为 WebSocket handler，默认 model 为 `gpt-realtime`；当前公开 API 不声明该入口可用。
- WebSocket 握手复用 Distributed Key `Authorization` 鉴权；缺失或无效鉴权只返回 OpenAI-style `error` event，不创建 Live Session。
- 握手成功后会创建并 connect `openai_realtime` Live Session，并把首个 outbound event 编码为 `session.created`。
- JSON text client event 会保留原始 payload，按 `type` 转发给 `LiveSessionService#sendRuntimeEvent`；`input_audio_buffer.append` 这类事件会从 `audio` 或 `delta` base64 字段估算 `audioBytes`。
- `session.update` 会返回 `session.updated`，并把客户端请求的 `session` 配置放入 `client_update`，方便调试本地代理接受了哪些字段。
- `input_audio_buffer.commit` 返回 `input_audio_buffer.committed` 基线事件。
- 非 JSON、缺少 `type` 或非文本帧返回 OpenAI-style `error` event，避免把内部异常、Authorization 或 key 信息泄露给客户端。
- WebSocket close 会释放对应 Live Session。

## 事件映射

- connect 输出 `websocket.connected`。
- client runtime event 会输出 `websocket.frame.{gatewayEventType}`，并在 payload 中记录 provider event type。
- timeout、provider error 等错误会输出 `websocket.error`，payload 中记录归一后的 `normalizedProviderErrorCode`。
- retry 事件会输出 `websocket.retry`，metadata 和 payload 记录 `retryAfterMs`。
- heartbeat 输出 `websocket.pong`。
- close 输出 `websocket.closed`。
- `audio.*` gateway event 会映射为 `{provider}.audio.*` provider event type，便于后续接真实二进制音频帧。
- OpenAI-compatible WebSocket 入口对外只输出 `session.created`、`session.updated`、`input_audio_buffer.committed` 与错误事件基线；内部 `websocket.frame.*` 仍作为 Live Session 观测与 conformance 证据保存。

## Conformance

- `LiveSessionService#conformance` 会读取 metadata 中的 `transport`。
- transport 为 `websocket` 时，必须存在 `websocket.*` 事件才能获得 `websocket frames available` 检查项。
- 发现带 `audioBytes` 的 websocket frame 时，增加 `binary audio frames accounted` 检查项。
- 发现 `websocket.error` 或 `normalizedProviderErrorCode` 时，增加 `provider errors normalized` 检查项。
- 发现 `websocket.retry` 或 `retryAfterMs` 时，增加 `retry semantics available` 检查项。
- 新增 OpenAI Realtime provider WebSocket conformance 回归，确保 connect、frame、heartbeat、close 均可被本地验证。

## 验证

已通过目标测试：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.LiveSessionServiceTests"
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.LiveSessionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests"
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiRealtimeWebSocketBridgeTests" --tests "com.prodigalgal.xaigateway.infra.config.OpenAiRealtimeWebSocketConfigurationTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiRealtimeControllerTests"
```

覆盖点：

- Gemini Live adapter 暴露 WebSocket runtime metadata。
- Mock WebSocket adapter conformance 保持通过。
- OpenAI Realtime provider WebSocket conformance 返回 `PASS`。
- timeout/error 被归一为 `UPSTREAM_TIMEOUT` 并输出 `websocket.error`。
- retry 语义输出 `websocket.retry` 和 `retryAfterMs`。
- close/cancel 语义记录 `gateway_close_as_client_cancel`。
- 二进制音频帧计数进入 conformance 检查。
- 连接池租户隔离、连接上限、释放、取消和过期清理。
- connect/close 写入连接池 lease metadata。
- OpenAI-compatible `/v1/realtime` WebSocket bridge 覆盖鉴权创建、`session.created`、`session.updated`、非法事件错误、base64 audioBytes 估算和 close 释放。

## 当前下线边界

- 当前 public docs/OpenAPI 不发布 `/v1/realtime` 可用入口。
- 历史 `session.created`、`session.updated`、本地 audioBytes 估算和 conformance 事件只作为归档证据，不能代表真实 OpenAI Realtime 或 Gemini Live 成功。
- 未来恢复时必须先完成真实 native provider route、真实网络拨号、二进制帧透传、错误归一和 smoke 验收。

## 后续边界

- 本轮新增单进程连接池 runtime 语义；多实例部署还需要 Redis/数据库租约或分布式锁。
- 真实网络拨号、真实二进制帧透传和 provider SDK 级重连仍需在部署环境补 smoke。
- WebRTC、SIP、Realtime calls、translation/transcription session 的完整官方入口仍按后续 OpenAI 资源族任务推进。
