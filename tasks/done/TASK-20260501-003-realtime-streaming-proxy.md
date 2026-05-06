# TASK-20260501-003 Realtime 与 Streaming 真实代理闭环：WebSocket/SSE、事件映射和 conformance

状态：Done  
优先级：High  
来源：Linear X-284  
来源 URL：https://linear.app/x-ai/issue/X-284/realtime-与-streaming-真实代理闭环websocketsse事件映射和-conformance  
关联报告：[REP-20260501](../../docs/reports/REP-20260501-open-source-gap-analysis.md)  
关联设计：[REQ-20260501-004](../../docs/requirements/REQ-20260501-004-third-priority-task-closure-design.md)

## 背景

当前 `x-ai-gateway` 已有 OpenAI Realtime client secret、Live Session 管理和多协议流式响应基础，但相对 `new-api` 的 Realtime API 支持与 `CC Switch` 的本地代理/stream check，真实 WebSocket/SSE 转发、事件映射、错误归一和兼容性测试仍需补深。

## 目标

补齐 Realtime / streaming 的真实代理执行闭环，使 OpenAI Realtime、Gemini Live 和兼容站点可以被可靠路由、观测、计费和调试。

## 范围

- 设计 WebSocket/SSE 代理通道与鉴权上下文传递。
- 统一事件类型、usage、audio bytes、tool call、错误和关闭语义。
- 将 Live Session 从模拟/管理能力升级为真实执行可接入。
- 建立 conformance fixtures 和端到端 smoke。

## 非目标

- 不一次性覆盖所有上游 Realtime 方言。
- 不暴露绕过治理的裸 socket 代理。

## 验收标准

- 至少一个真实 Realtime provider 可完成连接、事件转发、错误归一和 trace 记录。
- 控制台能查看会话事件、音频/Token 用量和关闭原因。
- E2E smoke 可在无真实上游时使用 mock server 验证协议。

## 实现记录

- 新增 `mock_realtime` runtime adapter，可在无真实上游时完成 Live Session connect/send/close smoke。
- 新增 `LiveSessionConformanceResponse` 与 `/admin/live-sessions/{sessionKey}/conformance`，检查 connected、streaming、closed、SSE replay、input/output event、audio bytes。
- Live Session 测试覆盖 mock realtime conformance PASS。

## 测试/验证

- 通过：`LiveSessionServiceTests`

## 遗留问题

- 本轮不接真实 WebSocket 上游，真实 OpenAI Realtime / Gemini Live adapter 后续拆分。
- 二进制音频帧、上游错误归一、真实 usage 仍待后续实现。
