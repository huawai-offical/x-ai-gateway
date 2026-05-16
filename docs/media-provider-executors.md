# Video/Music Provider Executors

关联需求：[REQ-20260506-002 第八批高优先级任务闭环设计](requirements/REQ-20260506-002-eighth-priority-task-closure-design.md)  
生产硬化需求：[REQ-20260506-010 Realtime 与 Media 生产硬化](requirements/REQ-20260506-010-realtime-media-production-hardening.md)  
Suno-like adapter 需求：[REQ-20260514-004 专有 Media Task Adapter 与真实产物 Smoke 闭环](requirements/REQ-20260514-004-provider-specific-media-task-adapters-smoke-closure.md)
关联任务：[TASK-20260501-017 真实 Video/Music Provider Executors 与产物闭环](../tasks/done/TASK-20260501-017-real-media-provider-executors.md)
当前增强任务：[TASK-20260506-011 Realtime 与 Media 生产硬化](../tasks/done/TASK-20260506-011-realtime-media-production-hardening.md)
专有 adapter 增强任务：[TASK-20260506-019 Realtime 长连接池与专有 Media Adapter](../tasks/done/TASK-20260506-019-realtime-pool-media-adapters.md)
Suno-like adapter 任务：[TASK-20260514-004 专有 Media Task Adapter 与真实产物 Smoke](../tasks/done/TASK-20260514-004-provider-specific-media-task-adapters-smoke.md)

## 实现范围

- `GatewayAsyncResourceService#createVideoTask` 与 `createMusicTask` 保留本地 async task contract，同时增加 OpenAI-style 上游 provider executor。
- 请求中包含 `provider_mode: "upstream"`、`provider_mode: "provider"` 或 `preferred_credential_id` 时进入上游 provider 模式；否则继续走本地模拟任务。
- Video 上游路径为 `/v1/videos/generations`，Music 上游路径为 `/v1/music/generations`。
- `getVideoTask`、`getMusicTask` 会基于 metadata 中的 `upstream_object_id` 同步上游状态，并把 gateway 侧 `id` 保持为本地资源 key。
- `cancelVideoTask`、`cancelMusicTask` 会在上游任务存在时调用 `{basePath}/{upstream_object_id}/cancel`；本地任务仍使用本地取消逻辑并保留终态保护。
- 新增 `GET /api/v1/media/provider-matrix`，返回结构化 Video/Music provider support matrix。
- 新增 provider-specific media adapter：
  - `GeminiVeoMediaProviderAdapter` 支持 `provider_mode=adapter` 或 `provider_family=gemini` 的 Video create/get/cancel/download 本地生命周期。
  - `SunoMusicMediaProviderAdapter` 支持 `provider_mode=adapter` 且未声明其他 provider，或 `provider_family=suno` / `provider=suno-like` 的 Music create/get/cancel/download 本地生命周期。
  - `GET /api/v1/videos/{videoId}/download` 返回 Video 产物下载引用。
  - `GET /api/v1/music/{musicId}/download` 返回 Music 产物下载引用。

## 状态与元数据

- 上游返回的对象会被包装为 gateway 本地资源，metadata 记录 `upstream_object_id`、credential、site profile、provider lineage 与同步事件。
- 本地任务取消会写入 `cancel_reason: user_cancelled`，已失败、已完成、已取消等终态不会被取消操作覆盖。
- capability truth 与 execution support matrix 已把 OpenAI direct / OpenAI compatible generic 的 Video、Music、Async Task 能力标记为可原生执行。
- 上游 Video/Music metadata 增加：
  - `provider_family`
  - `site_kind`
  - `provider_support_tier`
  - `provider_support_status`
  - `provider_smoke_hint`
- 本地 Video/Music contract metadata 标记 `provider_support_tier=local_contract` 与 `provider_support_status=LOCAL_ONLY`。
- Gemini/Veo 专有 adapter metadata 增加：
  - `object_mode=provider_specific_media_adapter`
  - `provider_mode=adapter`
  - `provider_family=gemini`
  - `provider_adapter=gemini_veo`
  - `provider_task_id`
  - `provider_support_tier=provider_specific_adapter`
  - `provider_support_status=SUPPORTED`
- Suno-like Music 专有 adapter metadata 增加：
  - `object_mode=provider_specific_media_adapter`
  - `provider_mode=adapter`
  - `provider_family=suno`
  - `provider_adapter=suno_music`
  - `provider_task_id`
  - `provider_fetch_mode=batch_polling`
  - `provider_support_tier=provider_specific_adapter`
  - `provider_support_status=SUPPORTED`
  - `provider_capability=music_generation`
  - `provider_pricing_source=operator_configured_suno_music_pricing`
  - `provider_failure_classes=[AUTHENTICATION_FAILED, QUOTA_EXCEEDED, NETWORK_ERROR, PARAMETER_UNSUPPORTED, PROVIDER_RATE_LIMITED]`

## Provider Matrix

| 资源 | provider 家族 | 状态 | 支持层级 | 说明 |
| --- | --- | --- | --- | --- |
| Video | OpenAI-compatible | `SUPPORTED` | `native_openai_style` | 走 `/v1/videos/generations`，支持 create/get/cancel。 |
| Video | Gemini / Vertex | `SUPPORTED` | `provider_specific_adapter` | 支持 `provider_mode=adapter` 的 Gemini/Veo 本地生命周期；真实 smoke 需环境变量注入凭证。 |
| Video | MiniMax | `ADAPTER_REQUIRED` | `provider_specific_adapter_required` | 可先通过兼容层接入，专有 API 需单独适配。 |
| Video | Midjourney-like | `NOT_NATIVE` | `external_async_bridge_required` | 需要外部 async bridge 提供任务状态。 |
| Music | OpenAI-compatible | `SUPPORTED` | `native_openai_style` | 走 `/v1/music/generations`，支持 create/get/cancel。 |
| Music | Suno-like | `SUPPORTED` | `provider_specific_adapter` | 支持 `provider_mode=adapter` 的 Suno-like Music 本地生命周期；真实 smoke 需显式环境变量和测试 key。 |
| Music | MiniMax | `ADAPTER_REQUIRED` | `provider_specific_adapter_required` | 需要 provider-specific adapter。 |
| Music | Gemini | `NOT_SUPPORTED` | `provider_capability_absent` | 当前不标记为原生 Music 任务。 |

## 验证

已通过目标测试：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests"
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.LiveSessionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests"
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderReferenceGapServiceTests" --tests "com.prodigalgal.xaigateway.provider.adapter.suno.SunoMusicProviderSmokeHarnessTests"
```

覆盖点：

- 本地 Video 创建与取消仍保留 `gateway_local_async_task` 与 `user_cancelled` 元数据。
- 本地 Music 失败终态取消时不会被覆盖。
- 上游 Video/Music create、get、cancel 请求路径与状态同步可回归验证。
- provider matrix 输出包含 OpenAI-compatible、Gemini、MiniMax、Suno/Midjourney 类支持状态。
- 上游 Video/Music metadata 记录 `provider_support_tier`、`provider_support_status` 和 smoke hint。
- Gemini/Veo adapter create/get/cancel/download 生命周期。
- Suno-like Music adapter create/get/cancel/download 生命周期、matrix 字段、标准失败分类和默认跳过真实 smoke 入口。
- Video/Music download API 编译纳入公开文档 bundle。

## 后续边界

- 本轮不提交真实多媒体产物或密钥。
- Gemini/Veo 已具备本地 provider-specific adapter 生命周期；真实 API 调用仍需按真实账号补 smoke 后推进。
- MiniMax、Kling、Vidu、Hailuo、Jimeng、Midjourney-like 专有 API adapter 仍需后续推进；Suno-like 已具备本地 provider-specific adapter 生命周期，真实远端协议仍需按目标网关补充。
- 产物落库、对象存储生命周期和内容审核链路仍作为后续增强项。
