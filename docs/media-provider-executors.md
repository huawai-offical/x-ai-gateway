# Video/Music Provider Executors

关联需求：[REQ-20260506-002 第八批高优先级任务闭环设计](requirements/REQ-20260506-002-eighth-priority-task-closure-design.md)  
生产硬化需求：[REQ-20260506-010 Realtime 与 Media 生产硬化](requirements/REQ-20260506-010-realtime-media-production-hardening.md)  
Suno-like adapter 需求：[REQ-20260514-004 专有 Media Task Adapter 与真实产物 Smoke 闭环](requirements/REQ-20260514-004-provider-specific-media-task-adapters-smoke-closure.md)
关联任务：[TASK-20260501-017 真实 Video/Music Provider Executors 与产物闭环](../tasks/done/TASK-20260501-017-real-media-provider-executors.md)
当前增强任务：[TASK-20260506-011 Realtime 与 Media 生产硬化](../tasks/done/TASK-20260506-011-realtime-media-production-hardening.md)
专有 adapter 增强任务：[TASK-20260506-019 Realtime 长连接池与专有 Media Adapter](../tasks/done/TASK-20260506-019-realtime-pool-media-adapters.md)
Suno-like adapter 任务：[TASK-20260514-004 专有 Media Task Adapter 与真实产物 Smoke](../tasks/done/TASK-20260514-004-provider-specific-media-task-adapters-smoke.md)

## 实现范围

> 2026-05-24 口径提示：本文记录的是历史 Video/Music executor 与 provider matrix 实现事实，不代表当前默认核心网关定义。当前公开网关定义以头部自有模型厂商 native、provider-specific native profile、Lossless Translation Matrix 和 hard-fail 为准；OpenAI-compatible/generic 行不再作为默认核心支持承诺，未声明 native/profile 或不可无损的能力必须失败。Media 创建路径已不再默认创建 gateway-local completed/download artifact，本地 adapter 也不得在没有真实 provider 响应证据时自动完成。

- `GatewayAsyncResourceService#createVideoTask` 与 `createMusicTask` 只允许 native upstream/provider route 或 provider-specific native profile 证据路径成功。
- 请求中包含 `provider_mode: "upstream"`、`provider_mode: "provider"` 或 `preferred_credential_id` 时进入上游 provider 模式；否则返回 `native_route_required`，不再走 gateway-local async task contract。
- Video 上游路径为 `/v1/videos/generations`，Music 上游路径为 `/v1/music/generations`。
- `getVideoTask`、`getMusicTask` 会基于 metadata 中的 `upstream_object_id` 同步上游状态，并把 gateway 侧 `id` 保持为本地资源 key。
- `cancelVideoTask`、`cancelMusicTask` 会在上游任务存在时调用 `{basePath}/{upstream_object_id}/cancel`；本地任务仍使用本地取消逻辑并保留终态保护。
- 新增 `GET /api/v1/media/provider-matrix`，返回结构化 Video/Music provider support matrix。
- provider-specific media adapter 当前只接受真实 provider 响应证据：
  - `GeminiVeoMediaProviderAdapter` 要求 `provider_task_id`，`completed` 状态还必须带真实 `output_url` / `video_url` / `download_url`。
  - `SunoMusicMediaProviderAdapter` 要求 `provider_task_id`，`completed` 状态还必须带真实 `audio_url` / `output_url` / `download_url`。
  - `GET /api/v1/videos/{videoId}/download` 只有真实 provider artifact URL 存在时返回 Video 产物下载引用。
  - `GET /api/v1/music/{musicId}/download` 只有真实 provider artifact URL 存在时返回 Music 产物下载引用。

## 状态与元数据

- 上游返回的对象会被包装为 gateway 本地资源，metadata 记录 `upstream_object_id`、credential、site profile、provider lineage 与同步事件。
- 历史本地任务取消逻辑只保留为旧 lineage 兼容；新建 Video/Music 默认无 native route 时不会创建本地任务。
- 历史 capability truth 与 execution support matrix 曾把 OpenAI direct / OpenAI compatible generic 的 Video、Music、Async Task 能力标记为可执行；2026-05-24 之后应按 provider-specific native profile 与 Lossless Translation Matrix 重新判定，不能从 generic 兼容性自动推导。
- 上游 Video/Music metadata 增加：
  - `provider_family`
  - `site_kind`
  - `provider_support_tier`
  - `provider_support_status`
  - `provider_smoke_hint`
- Gemini/Veo 专有 adapter metadata 增加：
  - `object_mode=provider_specific_media_adapter`
  - `provider_mode=adapter`
  - `provider_family=gemini`
  - `provider_adapter=gemini_veo`
  - `provider_task_id`
  - `provider_support_tier=provider_specific_native_profile_required`
  - `provider_support_status=NATIVE_REQUIRED`
- Suno-like Music 专有 adapter metadata 增加：
  - `object_mode=provider_specific_media_adapter`
  - `provider_mode=adapter`
  - `provider_family=suno`
  - `provider_adapter=suno_music`
  - `provider_task_id`
  - `provider_fetch_mode=batch_polling`
  - `provider_support_tier=provider_specific_native_profile_required`
  - `provider_support_status=NATIVE_REQUIRED`
  - `provider_capability=music_generation`
  - `provider_pricing_source=operator_configured_suno_music_pricing`
  - `provider_failure_classes=[AUTHENTICATION_FAILED, QUOTA_EXCEEDED, NETWORK_ERROR, PARAMETER_UNSUPPORTED, PROVIDER_RATE_LIMITED]`

## Provider Matrix

| 资源 | provider 家族 | 状态 | 支持层级 | 说明 |
| --- | --- | --- | --- | --- |
| Video | Provider-specific OpenAI-style | `CONDITIONAL` | `provider_specific_native_profile_required` | 只有目标厂商 profile 明确支持 `/v1/videos/generations` 等 native surface 时才执行；generic compatible 不默认支持。 |
| Video | Gemini / Vertex | `NATIVE_REQUIRED` | `provider_specific_native_profile_required` | 必须由 Gemini/Veo native task 响应提供 provider task id 和 artifact；不创建本地 completed/download 假产物。 |
| Video | MiniMax | `ADAPTER_REQUIRED` | `provider_specific_adapter_required` | 需要 provider-specific adapter 或可证明的 native profile；不能仅凭 generic compatible 成功。 |
| Video | Midjourney-like | `NON_CORE` | `external_async_bridge_required` | 历史候选，需要外部 async bridge 提供任务状态；不进入默认核心网关承诺。 |
| Music | Provider-specific OpenAI-style | `CONDITIONAL` | `provider_specific_native_profile_required` | 只有目标厂商 profile 明确支持 `/v1/music/generations` 等 native surface 时才执行；generic compatible 不默认支持。 |
| Music | Suno-like | `NATIVE_REQUIRED` | `provider_specific_native_profile_required` | 必须由 Suno native/profile 响应提供 provider task id 和 artifact；不创建本地 completed/download 假产物。 |
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

- 默认本地 Video/Music 请求缺少 native upstream/profile 时返回 `native_route_required`，不再保存 `gateway_local_async_task`。
- 上游 Video/Music create、get、cancel 请求路径与状态同步可回归验证。
- provider matrix 输出包含 provider-specific OpenAI-style、Gemini、MiniMax、Suno/Midjourney 类支持状态，并需避免把 generic compatible 解释成默认核心能力。
- 上游 Video/Music metadata 记录 `provider_support_tier`、`provider_support_status` 和 smoke hint。
- Gemini/Veo adapter 缺少 `provider_task_id` 或真实 artifact URL 时 hard-fail；读取 queued 任务不会自动推进到 completed。
- Suno-like Music adapter 缺少 `provider_task_id` 或真实 artifact URL 时 hard-fail；download 只返回真实 provider artifact URL。
- Video/Music download API 编译纳入公开文档 bundle。

## 后续边界

- 本轮不提交真实多媒体产物或密钥。
- Gemini/Veo 已具备本地 provider-specific adapter 生命周期；真实 API 调用仍需按真实账号补 smoke 后推进。
- MiniMax、Kling、Vidu、Hailuo、Jimeng、Midjourney-like 专有 API adapter 仍需后续推进；Suno-like 已具备本地 provider-specific adapter 生命周期，真实远端协议仍需按目标网关补充。
- 产物落库、对象存储生命周期和内容审核链路仍作为后续增强项。
