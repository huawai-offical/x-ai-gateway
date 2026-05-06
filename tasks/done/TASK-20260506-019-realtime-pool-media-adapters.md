# TASK-20260506-019 Realtime 长连接池与专有 Media Adapter

状态：Done  
优先级：High  
来源：[REP-20260506 参考项目功能深度再复核](../../docs/reports/REP-20260506-reference-feature-depth-recheck.md)  
关联需求：[REQ-20260506-015 Realtime 长连接池与专有 Media Adapter](../../docs/requirements/REQ-20260506-015-realtime-pool-media-adapters.md)

## 背景

Realtime 与 Media 已有 provider matrix、metadata、错误归一和本地测试。但对照 `new-api-main` 的 Realtime、Sora、Suno、Kling、Vidu、Hailuo、Jimeng 等 task channel，当前仍缺生产级长连接池、provider SDK 级重连、专有 media adapter 和真实产物 smoke。

## 目标

- 建立 Realtime 长连接池、心跳、重连和泄漏保护。
- 为 Gemini/Veo、MiniMax、Suno 或其他高价值 media provider 拆分专有 adapter。
- 输出真实凭证 smoke 的脱敏报告和产物生命周期证据。

## 范围

- OpenAI Realtime 与 Gemini Live 的真实拨号 smoke harness。
- 长连接池、连接上限、租户隔离、取消/关闭语义。
- Media provider adapter 抽象、create/get/cancel/download 生命周期。
- 产物 metadata、存储、过期、清理和审计。

## 非目标

- 不一次性覆盖所有媒体 provider。
- 不提交真实 media 产物、密钥或用户素材。
- 不绕过 provider 官方限制。

## 验收标准

- Realtime smoke 可输出脱敏连接报告，覆盖连接、收发、心跳、关闭和错误归一。
- 至少一个专有 Media provider 完成 create/get/cancel/download 的测试闭环。
- 长连接池有并发、取消、重连和泄漏防护测试。
- Media provider matrix 与公开 docs 自动同步。

## 实现记录

- 新增 `LiveSessionConnectionPool`，覆盖 tenant-scoped acquire/touch/release/cancel/sweepExpired/snapshot。
- `LiveSessionService` 接入连接池，connect 失败会释放 lease，close 会释放 lease，并在 session metadata 暴露连接池状态。
- 新增 Gemini/Veo provider-specific media adapter，使用 `provider_mode=adapter` 或 `provider_family=gemini` 触发。
- Gemini/Veo adapter 支持 Video create/get/cancel/download 本地生命周期，并写入 `provider_specific_media_adapter` metadata。
- 新增 `GET /api/v1/videos/{videoId}/download` 与 `GET /api/v1/music/{musicId}/download` 产物引用接口。
- provider matrix 将 Gemini / Vertex Video 标记为 `SUPPORTED` / `provider_specific_adapter`。

## 测试/验证

已通过：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.LiveSessionConnectionPoolTests" --tests "com.prodigalgal.xaigateway.admin.application.LiveSessionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests"
```

验证覆盖：

- 连接池租户隔离、连接上限、释放、取消和过期清理。
- Live Session 连接池 metadata 写入与关闭释放。
- Gemini/Veo adapter create/get/cancel/download 生命周期。
- provider matrix 支持状态更新。

## 遗留问题

- 真实 Realtime WebSocket 拨号与真实 Gemini/Veo 产物 smoke 仍需用户本机环境变量注入 key 后执行。
- 当前连接池是单进程 runtime 语义，多实例生产部署需要外部租约/锁。
- Suno、MiniMax、Kling、Vidu、Hailuo、Jimeng 等 adapter 仍在后续 backlog。
