# TASK-20260516-008 OpenAI Realtime WebSocket 入口与事件代理基线

状态：Done
优先级：Critical
类型：子任务切片
父任务：[TASK-20260514-030](../done/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)
关联任务：[TASK-20260514-018](../done/TASK-20260514-018-openai-responses-native-lifecycle.md)、[TASK-20260501-024](../done/TASK-20260501-024-realtime-real-provider-websocket.md)

## 背景

`TASK-20260514-030` 的横切协议剩余项中仍有 Realtime WebSocket event 代理。项目已有 `LiveSessionService`、`OpenAiRealtimeRuntimeAdapter` 与 WebSocket transport metadata，但 OpenAI-compatible 入口目前只公开 `POST /v1/realtime/client_secrets`，缺少官方 WebSocket 形态的 `/v1/realtime?model=...` 接入面。

官方 Realtime 文档显示 WebSocket server-to-server 连接使用 `wss://api.openai.com/v1/realtime?model=gpt-realtime`，通过 Authorization header 鉴权，连接建立后服务器首先发送 `session.created`；客户端与服务端通过 JSON serialized events 交互，`session.update` 会返回 `session.updated`。

## 目标

- 新增 OpenAI-compatible WebSocket handler：`/v1/realtime?model=...`。
- WebSocket 握手后复用 gateway distributed key 鉴权，创建并 connect `openai_realtime` Live Session。
- 连接建立后发送 OpenAI-style `session.created` server event。
- 接收 JSON text client events，提取 `type`、保留原始 payload，并转发给 `LiveSessionService#sendRuntimeEvent`。
- 对 `session.update` 返回 OpenAI-style `session.updated` server event。
- 对缺失 `type`、非法 JSON、非文本帧输出 OpenAI-style `error` event，不把内部异常或 secret 泄露给客户端。
- WebSocket 关闭时释放 Live Session。

## 非目标

- 不在本切片实现真实 OpenAI 上游网络拨号。
- 不实现 WebRTC、SIP、Realtime calls、translation/transcription session 的完整入口。
- 不承诺所有 Realtime server event 的生成语义，只建立连接、session update、错误与事件转发基线。
- 不处理真实二进制音频帧透传；本切片只从 JSON audio/base64 字段估算 audioBytes。

## 输入

- `GatewayTokenAuthenticationResolver`
- `LiveSessionService`
- `LiveSessionCreateRequest`
- 官方 Realtime WebSocket 与 server/client events 文档

## 输出

- `/v1/realtime` WebSocket handler 与 handler mapping。
- Realtime WebSocket bridge/codec。
- Bridge unit tests 与 handler mapping tests。
- `docs/realtime-provider-websocket.md`、`docs/public-api-compatibility.md`、`TASK-20260514-030` 和覆盖报告回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/`
- `src/main/java/com/prodigalgal/xaigateway/infra/config/`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/`
- `docs/realtime-provider-websocket.md`
- `docs/public-api-compatibility.md`
- `tasks/done/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md`
- `docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md`

## 依赖

- `TASK-20260501-024` 已提供 `openai_realtime` Live Session WebSocket runtime adapter。
- `TASK-20260515-015` 已把 `/v1/realtime` 纳入 OpenAI protocol path matcher。

## 风险

- WebSocket handler 如果绕过 gateway token 鉴权，会把 Realtime 通道暴露为未授权入口。
- 如果将内部 provider event 伪装成完整模型输出，可能误导客户以为已经完成真实 Realtime 生成。
- 如果错误事件包含原始 Authorization 或 payload 中的 secret，会破坏 smoke/certification 的脱敏边界。

## 验收标准

- `/v1/realtime` WebSocket mapping 存在，并与 `/v1/realtime/client_secrets` REST endpoint 不冲突。
- 有效 Authorization 会创建并 connect `openai_realtime` Live Session，首个 outbound event 为 `session.created`。
- `session.update` JSON event 会被转发为 runtime event，并返回 `session.updated`。
- 非法 JSON 或缺失 `type` 返回 OpenAI-style `error` event。
- WebSocket close 会调用 `LiveSessionService#close` 释放 session。
- Targeted tests 和 scoped `diff --check` 通过。

## 测试边界

- Bridge unit tests 覆盖鉴权、session 创建、事件转发、`session.updated`、错误事件和 close。
- Handler mapping tests 覆盖 `/v1/realtime` URL 映射。
- 不使用真实 OpenAI key、不拨号真实上游。

## 关联官方文档

- https://platform.openai.com/docs/guides/realtime-websocket
- https://platform.openai.com/docs/api-reference/realtime-server-events/response/created
- https://platform.openai.com/docs/api-reference/realtime-client-events/session

## 当前状态

- 已完成 WebSocket bridge、handler、mapping、测试和文档回写。
- 已确认 `/v1/realtime` WebSocket mapping 与 `/v1/realtime/client_secrets` REST endpoint 不冲突。

## 实现结果

- 新增 `OpenAiRealtimeWebSocketBridge`，负责 Distributed Key 鉴权、Live Session 创建/连接、OpenAI-style event 编码、JSON event 解析和错误事件输出。
- 新增 `OpenAiRealtimeWebSocketHandler`，负责 WebSocket text frame 收发、非文本帧拒绝和 close 时释放 Live Session。
- 新增 `OpenAiRealtimeWebSocketConfiguration`，把 `/v1/realtime` 映射到 WebSocket handler。
- `session.created` / `session.updated` 不暴露 gateway key prefix、内部 resume token 或 Authorization。
- `input_audio_buffer.append` 会从 `audio` 或 `delta` base64 字段估算 `audioBytes`，写入 Live Session 观测链路。

## 验证记录

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiRealtimeWebSocketBridgeTests" --tests "com.prodigalgal.xaigateway.infra.config.OpenAiRealtimeWebSocketConfigurationTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiRealtimeControllerTests"
```

结果：通过。

## 遗留与后续

- 本切片不生成真实模型输出，也不拨号 OpenAI 上游 WebSocket；真实 provider 网络拨号和二进制音频帧透传仍需独立 smoke/adapter 切片。
- WebRTC、SIP、Realtime calls、Realtime translation/transcription session 仍需按 OpenAI 资源族覆盖任务继续拆分。
