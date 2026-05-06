# REQ-20260506-010 Realtime 与 Media 生产硬化

状态：Done  
关联任务：[TASK-20260506-011 Realtime 与 Media 生产硬化](../../tasks/done/TASK-20260506-011-realtime-media-production-hardening.md)  
来源报告：[REP-20260506 三个参考项目功能完成度复核](../reports/REP-20260506-reference-feature-completeness-review.md)

## 背景

Realtime WebSocket 和 Video/Music async task 已经具备接口契约、metadata、conformance 和上游 OpenAI-style 执行能力，但生产排障仍缺少足够明确的二进制帧策略、provider 错误归一、重试/超时/关闭语义、usage 统计和多媒体 provider 支持矩阵。

## 目标

- Realtime WebSocket adapter 在 metadata 和事件中体现二进制音频帧、错误归一、重试、超时和关闭语义。
- Realtime conformance 增加二进制帧和错误归一可观测检查，不破坏既有 mock 与 provider WebSocket 回归。
- Video/Music 暴露 provider support matrix，明确 OpenAI-compatible、Gemini、MiniMax、Suno/Midjourney 类 provider 的支持策略与限制。
- Media 上游任务 metadata 记录 provider support tier、provider family、site kind 和 smoke hint，方便运营核对真实 provider 行为。

## 范围

- `DefaultLiveSessionRuntimeAdapters` 与 `LiveSessionService` 的 runtime metadata、provider event 和 conformance 检查。
- `GatewayAsyncResourceService` 的 media provider matrix 与 media metadata 硬化。
- `GatewayPublicResourceService` 与 `GatewayMediaTasksController` 暴露 provider matrix API。
- `docs/realtime-provider-websocket.md` 与 `docs/media-provider-executors.md` 更新当前事实源。
- 单元测试覆盖错误、取消、超时、重试、二进制帧和 provider matrix。

## 非目标

- 不在仓库提交真实 provider 凭证。
- 不在本轮实现所有多媒体 provider 的专有 API。
- 不引入长连接池或后台调度器。

## 验收标准

- Realtime 测试覆盖 provider WebSocket connect、二进制音频帧、错误归一、timeout/retry、close/cancel 语义和 conformance。
- Media 测试覆盖 provider matrix 输出，以及上游 Video/Music create/get/cancel 的 provider metadata。
- 文档记录每个 provider 家族的支持状态、不可支持原因和 smoke 步骤。
- 任务文件回写实现结果、验证情况、遗留问题和后续建议，并移动到 `tasks/done/`。

## 实现结果

- Realtime provider WebSocket metadata 已记录二进制帧策略、错误归一、重试、关闭、latency 和 usage。
- Realtime conformance 已加入二进制帧、provider 错误归一和 retry 语义检查。
- Media 已新增 provider matrix，并通过 `GET /api/v1/media/provider-matrix` 暴露。
- 上游 Video/Music metadata 已记录 provider family、site kind、support tier、support status 和 smoke hint。
- `docs/realtime-provider-websocket.md` 与 `docs/media-provider-executors.md` 已更新。

## 验收结果

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.LiveSessionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests"
```

结论：本地实现和回归测试通过。真实 provider 长连接池、专有 Video/Music adapter 和真实凭证 smoke 仍需后续按环境补充。
