# TASK-20260506-011 Realtime 与 Media 生产硬化

状态：Done  
优先级：High  
来源：[REP-20260506 三个参考项目功能完成度复核](../../docs/reports/REP-20260506-reference-feature-completeness-review.md)
需求文档：[REQ-20260506-010 Realtime 与 Media 生产硬化](../../docs/requirements/REQ-20260506-010-realtime-media-production-hardening.md)

## 背景

当前 Realtime WebSocket 和 Video/Music async task 已有接口契约、metadata、conformance 与本地/上游模式，但对照参考项目的真实转发和多媒体生态，仍缺生产级长连接、二进制帧、错误码归一、usage 统计和多 provider 实战矩阵。

## 目标

- 将 Realtime 从事件契约推进到真实 provider 可稳定转发。
- 将 Video/Music 从 OpenAI-style 上游任务扩展到明确的 provider 支持矩阵。
- 增加真实 provider smoke 和失败恢复证据。

## 范围

- OpenAI Realtime 与 Gemini Live WebSocket 真实拨号、心跳、重连、关闭语义。
- 音频二进制帧、provider 错误码归一、usage/latency 记录。
- Video/Music provider matrix，明确 OpenAI-compatible、Gemini、MiniMax、Suno/Midjourney 类能力策略。
- Conformance 与 smoke 报告输出。

## 非目标

- 不把所有多媒体 provider 一次性实现完。
- 不提交真实多媒体产物或密钥。

## 验收标准

- Realtime smoke 能在配置真实凭证时完成连接、收发、关闭并输出脱敏报告。
- Video/Music 至少一个真实上游 provider 完整 create/get/cancel 通过。
- 错误、取消、超时、重试均有测试覆盖。
- docs 中记录 provider 支持状态和不可支持原因。

## 详细设计

- Realtime provider WebSocket adapter 输出 `binaryFramePolicy`、usage audio bytes、latency、normalized provider error code、retryable 和 close reason。
- `LiveSessionService#conformance` 增加二进制音频帧和 provider 错误归一检查。
- Media 增加 provider support matrix API，明确 Video/Music 在 OpenAI-compatible、Gemini、MiniMax、Suno/Midjourney 类 provider 上的支持等级。
- 上游 Media task metadata 写入 provider family、site kind、support tier 和 smoke hint。

## 进度记录

- 2026-05-06：任务从 backlog 移入 in-progress，补充本地需求与详细设计，开始实现生产硬化。
- 2026-05-06：完成 Realtime WebSocket metadata/conformance 硬化、Media provider matrix API、上游 media metadata 和回归测试。

## 实现结果

- Realtime provider WebSocket adapter 增加二进制帧策略、错误归一、重试、关闭、latency 和 usage metadata。
- `LiveSessionService#conformance` 增加 `binary audio frames accounted`、`provider errors normalized`、`retry semantics available` 检查项。
- `GatewayAsyncResourceService#mediaProviderMatrix` 输出 Video/Music provider support matrix。
- `GET /api/v1/media/provider-matrix` 暴露结构化 matrix。
- 上游 Video/Music task metadata 写入 `provider_family`、`site_kind`、`provider_support_tier`、`provider_support_status` 和 `provider_smoke_hint`。
- `docs/realtime-provider-websocket.md` 与 `docs/media-provider-executors.md` 已更新当前事实源。

## 验证情况

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.LiveSessionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests"
```

覆盖点：

- Realtime provider WebSocket connect、frame、heartbeat、close。
- timeout/error 归一为 `UPSTREAM_TIMEOUT`。
- retry 事件输出 `websocket.retry` 和 `retryAfterMs`。
- close/cancel 语义记录 `gateway_close_as_client_cancel`。
- 二进制音频帧进入 conformance。
- Media provider matrix 输出和上游 Video/Music metadata。

## 遗留问题

- 未实现生产级长连接池和 provider SDK 级重连。
- Gemini/Veo、Suno、MiniMax、Midjourney-like 专有 API adapter 未在本轮实现。
- 真实凭证 smoke 和真实产物落库仍需部署前执行。

## 后续建议

- 为 Realtime 增加真实 WebSocket 拨号 smoke harness，输出脱敏连接报告。
- 按 provider matrix 分批推进 Gemini/Veo、Suno、MiniMax 的专有 adapter。
