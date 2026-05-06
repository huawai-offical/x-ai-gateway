# Realtime Provider WebSocket Adapter

关联需求：[REQ-20260506-002 第八批高优先级任务闭环设计](requirements/REQ-20260506-002-eighth-priority-task-closure-design.md)  
生产硬化需求：[REQ-20260506-010 Realtime 与 Media 生产硬化](requirements/REQ-20260506-010-realtime-media-production-hardening.md)  
关联任务：[TASK-20260501-024 真实 Realtime Provider WebSocket Adapter](../tasks/done/TASK-20260501-024-realtime-real-provider-websocket.md)
当前增强任务：[TASK-20260506-011 Realtime 与 Media 生产硬化](../tasks/done/TASK-20260506-011-realtime-media-production-hardening.md)
连接池增强任务：[TASK-20260506-019 Realtime 长连接池与专有 Media Adapter](../tasks/done/TASK-20260506-019-realtime-pool-media-adapters.md)

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

## 事件映射

- connect 输出 `websocket.connected`。
- client runtime event 会输出 `websocket.frame.{gatewayEventType}`，并在 payload 中记录 provider event type。
- timeout、provider error 等错误会输出 `websocket.error`，payload 中记录归一后的 `normalizedProviderErrorCode`。
- retry 事件会输出 `websocket.retry`，metadata 和 payload 记录 `retryAfterMs`。
- heartbeat 输出 `websocket.pong`。
- close 输出 `websocket.closed`。
- `audio.*` gateway event 会映射为 `{provider}.audio.*` provider event type，便于后续接真实二进制音频帧。

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

## 后续边界

- 本轮新增单进程连接池 runtime 语义；多实例部署还需要 Redis/数据库租约或分布式锁。
- 真实网络拨号、真实二进制帧透传和 provider SDK 级重连仍需在部署环境补 smoke。
