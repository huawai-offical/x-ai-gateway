# TASK-20260501-024 真实 Realtime Provider WebSocket Adapter

状态：Done  
优先级：High  
来源：TASK-20260501-020 后续拆分  
关联任务：[TASK-20260501-020](../done/TASK-20260501-020-realtime-websocket-provider-adapters.md)
关联推进需求：[REQ-20260506-002](../../docs/requirements/REQ-20260506-002-eighth-priority-task-closure-design.md)
关联说明文档：[realtime-provider-websocket](../../docs/realtime-provider-websocket.md)

## 背景

当前已完成 WebSocket transport 的 mock adapter、metadata 和 conformance 检查，但尚未连接真实 OpenAI Realtime 或 Gemini Live。

## 目标

接入至少一个真实 Realtime WebSocket provider。

## 范围

- OpenAI Realtime 或 Gemini Live WebSocket connect/auth/send/receive/close。
- provider event 到 gateway event 映射。
- 二进制音频帧处理与错误归一。
- usage、trace、关闭原因记录。

## 验收标准

- mock server E2E 保持通过。
- 配置真实凭证后至少一个 provider 可完成 smoke。

## 本批推进记录

- 2026-05-06：进入第八批高优先级任务闭环，目标是让 OpenAI Realtime/Gemini Live adapter 暴露 WebSocket transport、provider event 映射与 conformance 证据。
- 2026-05-06：完成 provider WebSocket adapter 基类，OpenAI Realtime 与 Gemini Live 均输出 WebSocket transport、provider URL、auth scheme 和 connect/frame/heartbeat/close 事件。

## 实现结果

- `OpenAiRealtimeRuntimeAdapter` 与 `GeminiLiveRuntimeAdapter` 继承统一 provider WebSocket runtime adapter。
- metadata 记录 `transport: websocket`、`websocketState`、`upstreamWebSocketUrl`、`authScheme`、`upstreamResumeHandle`。
- provider event 映射包括 `websocket.connected`、`websocket.frame.{eventType}`、`websocket.pong`、`websocket.closed`。
- conformance 在 WebSocket transport 下要求存在 `websocket.*` frame 证据。

## 测试/验证情况

已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.LiveSessionServiceTests"
```

覆盖：

- Gemini Live provider runtime metadata。
- Mock WebSocket adapter conformance。
- OpenAI Realtime provider WebSocket conformance，包含 connect、frame、heartbeat、close。

## 遗留问题

- 生产级长连接池、真实网络拨号、二进制音频帧收发、provider usage/error 归一仍未在本轮实现。

## 后续建议

- 下一步围绕真实 provider smoke 与二进制帧处理拆分任务，避免把连接池和业务事件映射混在同一次改动里。
