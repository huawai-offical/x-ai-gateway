# TASK-20260501-020 Realtime WebSocket Provider Adapter

状态：Done  
优先级：High  
来源：TASK-20260501-003 后续拆分  
关联任务：[TASK-20260501-003](TASK-20260501-003-realtime-streaming-proxy.md)  
关联需求：[REQ-20260501-005](../../docs/requirements/REQ-20260501-005-fourth-priority-task-closure-design.md)

## 背景

当前 Realtime 已有 mock conformance、SSE replay 和 Live Session 状态闭环，但尚未连接真实 WebSocket 上游。

## 目标

接入真实 OpenAI Realtime 或 Gemini Live provider adapter 的本地可验证适配层。

## 范围

- WebSocket connect / send / receive / close。
- provider event 到 gateway event 映射。
- 二进制音频帧与错误归一。
- trace、usage、关闭原因记录。

## 非目标

- 本轮不直接连接真实 OpenAI Realtime 或 Gemini Live 网络服务。
- 本轮不实现真实二进制音频编码转换和背压调优。

## 实现结果

- `LiveSessionRuntimeAdapter` 新增 `transport()` 默认方法。
- 新增 `MockWebSocketRealtimeRuntimeAdapter`：
  - protocol：`mock_websocket_realtime`
  - transport：`websocket`
  - 支持 connect/send/heartbeat/close mock WebSocket 帧。
- `LiveSessionService` runtime metadata 写入 `transport` 与 WebSocket 状态。
- `LiveSessionConformanceResponse` 新增 `transport` 字段。
- conformance 对 WebSocket transport 增加 `websocket frames available` 检查。

## 测试/验证情况

- `LiveSessionServiceTests`

## 遗留问题

- 真实 OpenAI Realtime/Gemini Live WebSocket adapter 的认证、二进制帧、背压和 provider 错误映射尚未接入。

## 后续建议

- 继续推进 [TASK-20260501-024 真实 Realtime Provider WebSocket Adapter](../backlog/TASK-20260501-024-realtime-real-provider-websocket.md)。
