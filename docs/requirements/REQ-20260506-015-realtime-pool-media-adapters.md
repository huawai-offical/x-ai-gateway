# REQ-20260506-015 Realtime 长连接池与专有 Media Adapter

状态：Done  
日期：2026-05-06  
关联任务：

- [TASK-20260506-019 Realtime 长连接池与专有 Media Adapter](../../tasks/done/TASK-20260506-019-realtime-pool-media-adapters.md)

## 背景

Realtime 与 Media 已完成 provider WebSocket metadata、错误归一、provider matrix 和 OpenAI-style async task。对照参考项目的 Realtime、Sora、Suno、Kling、Vidu、Hailuo、Jimeng 等任务通道，当前仍缺生产级长连接池、连接租户隔离、泄漏保护、provider-specific media adapter 与真实 smoke 报告入口。

## 目标

- 建立 Realtime 长连接池的最小 runtime 模型，覆盖连接上限、租户隔离、租约释放、取消关闭和泄漏清理。
- 为高价值 media provider 建立至少一个专有 adapter 的 create/get/cancel/download 生命周期闭环。
- 保持 provider matrix 与 docs 可追溯，明确专有 adapter 支持状态。
- 真实凭证 smoke 只通过环境变量注入，不保存密钥、不保存真实素材。

## 范围

- Realtime service/runtime 层连接池、统计和测试。
- Media adapter 抽象与一个 provider-specific adapter 的本地可验证实现。
- create/get/cancel/download 生命周期 metadata 与 provider matrix 文档。
- 单元测试覆盖并发、释放、取消、泄漏清理和 adapter 生命周期。

## 非目标

- 不一次性覆盖所有 Realtime provider 或所有媒体 provider。
- 不提交真实 provider key、真实 media 产物或用户素材。
- 不绕过 provider 官方限制。
- 不在本轮引入对象存储、内容审核或完整 CDN 生命周期。

## 方案

1. 将 `TASK-019` 移入 `in-progress`。
2. 先梳理现有 `LiveSessionService`、runtime adapter 与 `GatewayAsyncResourceService` 的边界。
3. 新增本地可测的 Realtime 连接池组件，提供 acquire/release/close/cancel/sweep 语义。
4. 新增一个 provider-specific media adapter，优先选择 Gemini/Veo 或 MiniMax 这类 Video adapter，以本地 fake transport 完成 create/get/cancel/download。
5. 将 adapter 能力写入 provider matrix 与文档。

## 风险

- 长连接池如果直接接真实 WebSocket，测试稳定性会受网络影响；本轮先以本地 runtime 抽象和 fake transport 验证语义。
- 专有 media provider API 差异大，本轮只做单 provider 生命周期闭环，后续再扩展其他 provider。
- 真实 smoke 需要用户在本机注入 key，本轮不能把 key 写入仓库或日志。

## 验收标准

- Realtime 长连接池有连接上限、租户隔离、释放、取消和泄漏清理测试。
- 至少一个专有 Media provider 完成 create/get/cancel/download 的本地测试闭环。
- provider matrix 或文档能体现该专有 adapter 的支持状态。
- 任务和需求文档回写实现结果、测试/验证、遗留问题和后续建议。

## 实现结果

- 新增 `LiveSessionConnectionPool`：
  - 按 tenant 维度限制活跃连接数。
  - 支持 acquire、touch、release、cancel、sweepExpired 和 snapshot。
  - `LiveSessionService` 在 connect、send、heartbeat、close 时写入连接池 lease metadata。
- Realtime session metadata 新增：
  - `connectionPoolLeaseId`
  - `connectionPoolTenant`
  - `connectionPoolState`
  - `connectionPoolExpiresAt`
  - `connectionPoolActive`
  - `connectionPoolMaxPerTenant`
- 新增 Gemini/Veo provider-specific media adapter：
  - 通过 `provider_mode=adapter` 或 `provider_family=gemini` 触发。
  - 支持 Video create/get/cancel/download 本地生命周期。
  - metadata 标记 `object_mode=provider_specific_media_adapter`、`provider_adapter=gemini_veo`、`provider_support_status=SUPPORTED`。
- `GET /api/v1/media/provider-matrix` 中 Gemini / Vertex Video 状态从 `ADAPTER_REQUIRED` 更新为 `SUPPORTED` / `provider_specific_adapter`。
- 新增公开产物引用接口：
  - `GET /api/v1/videos/{videoId}/download`
  - `GET /api/v1/music/{musicId}/download`

## 测试/验证

已通过：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.LiveSessionConnectionPoolTests" --tests "com.prodigalgal.xaigateway.admin.application.LiveSessionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests"
```

覆盖点：

- Realtime 连接池租户隔离、租户上限、release、cancel 和过期清理。
- Live Session connect/close 写入连接池 metadata。
- Gemini/Veo adapter create/get/cancel/download 生命周期。
- provider matrix 体现 `provider_specific_adapter` 支持状态。

## 遗留问题

- 真实 OpenAI Realtime / Gemini Live 网络拨号 smoke 仍需在用户本机通过环境变量注入 key 后执行。
- Gemini/Veo adapter 当前是本地生命周期闭环，未直接访问真实 Veo API 或下载真实产物。
- Suno、MiniMax、Kling、Vidu、Hailuo、Jimeng 等专有 adapter 仍需后续逐个扩展。
- 多实例连接池需要 Redis/数据库租约或分布式锁；本轮先完成单进程 runtime 语义。
