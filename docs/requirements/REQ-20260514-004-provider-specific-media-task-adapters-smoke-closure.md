# REQ-20260514-004 专有 Media Task Adapter 与真实产物 Smoke 闭环

## 背景

`REP-20260514` 对照参考项目 `new-api-main/relay/channel/task/*` 后确认：当前项目已有 Video/Music async lifecycle、OpenAI-style upstream executor、Gemini/Veo provider-specific adapter 和 provider matrix，但 Music/Video 专有任务通道仍缺少更宽的 adapter 证据。参考项目的 Suno 通道具备 submit、batch fetch、公开 task id 映射、状态轮询和产物字段映射语义，本轮优先选择 Suno-like Music 作为非 Gemini/Veo 的闭环代表。

## 目标

- 新增一个非 Gemini/Veo 的 provider-specific media adapter，覆盖 create、get、cancel、download 生命周期。
- 在 metadata 和 matrix 中声明 support tier、capability、pricing source、smoke hint 与标准失败分类。
- 真实 smoke 入口默认跳过，不在缺少显式环境变量和 key 时访问远端或消耗额度。
- 更新后台参考差距矩阵，使 Music 能从 `ORCHESTRATED_LIMITED` 提升到可追踪的 provider adapter 状态。

## 非目标

- 不在本轮实现 Suno 官方或第三方 API 的全量真实远端协议。
- 不一次性覆盖 MiniMax、Kling、Vidu、Hailuo、Jimeng、Sora、Midjourney-like 等全部媒体 provider。
- 不引入真实媒体文件落库、内容审核或对象存储生命周期策略。

## 方案

1. 在现有 `MediaProviderAdapter` 抽象下补充 Suno-like Music adapter，以本地可验证 fake transport 固定任务生命周期。
2. 参考 Suno task 通道字段，保留 `prompt`、`title`、`tags`、`mv`、`make_instrumental`、`duration_seconds`、`audio_url`、`image_url` 等产物与请求线索。
3. 更新 `GatewayAsyncResourceService#mediaProviderMatrix` 和 `ProviderReferenceGapService#mediaRows`，把 Suno-like Music 标记为已具备 provider adapter。
4. 增加 smoke harness：只有 `XAG_SMOKE_SUNO=true` 且存在 `XAG_SMOKE_SUNO_API_KEY`/`SUNO_API_KEY` 时才允许真实 smoke；否则以 JUnit assumption 跳过。

## 范围

- `GatewayAsyncResourceService`
- `MediaProviderAdapter`
- `ProviderReferenceGapService`
- `GatewayAsyncResourceServiceTests`
- `ProviderReferenceGapServiceTests`
- Suno-like smoke harness 测试
- `docs/media-provider-executors.md`
- `tasks/in-progress/TASK-20260514-004-provider-specific-media-task-adapters-smoke.md`

## 风险

- Suno-like 生态并非单一官方 OpenAI-compatible surface，真实接入必须区分官方、第三方聚合和自托管网关。
- 媒体任务状态可能经历 submitted、queueing、processing、success、failed 等多套命名，需要 gateway 侧先做规范化。
- 真实 smoke 可能产生费用或触发限流，必须保持显式启用和脱敏报告。

## 验收标准

- `provider_mode=adapter` + `provider_family=suno` 的 Music 任务可 create、get、cancel、download。
- Music matrix 中 Suno-like 为 `SUPPORTED` / `provider_specific_adapter`，MiniMax 等未实现项仍保持 `ADAPTER_REQUIRED`。
- adapter metadata 包含 capability、pricing source、smoke hint 和 `AUTHENTICATION_FAILED`、`QUOTA_EXCEEDED`、`NETWORK_ERROR`、`PARAMETER_UNSUPPORTED`、`PROVIDER_RATE_LIMITED`。
- smoke harness 默认跳过，显式 env + key 才会走远端。
- 定向测试通过并回写任务文档。

## 关联任务

- [TASK-20260514-004 专有 Media Task Adapter 与真实产物 Smoke](../../tasks/done/TASK-20260514-004-provider-specific-media-task-adapters-smoke.md)
- [TASK-20260514-002 参考项目实现细节深度对比](../../tasks/done/TASK-20260514-002-reference-implementation-detail-comparison.md)

## 状态

Done。已完成 Suno-like Music provider-specific adapter、media matrix、参考差距行、默认跳过真实 smoke harness 和定向回归验证。
