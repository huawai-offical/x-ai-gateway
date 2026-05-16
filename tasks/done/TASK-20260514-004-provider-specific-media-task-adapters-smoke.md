# TASK-20260514-004 专有 Media Task Adapter 与真实产物 Smoke

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260514-002](../done/TASK-20260514-002-reference-implementation-detail-comparison.md)  
上游来源：[REQ-20260514-002](../../docs/requirements/REQ-20260514-002-reference-implementation-detail-comparison.md)、[REQ-20260514-004](../../docs/requirements/REQ-20260514-004-provider-specific-media-task-adapters-smoke-closure.md)、[REP-20260514](../../docs/reports/REP-20260514-reference-implementation-detail-comparison.md)

## 背景

当前项目已具备 Video/Music async lifecycle、OpenAI-style upstream executor、Gemini/Veo 本地 provider-specific adapter 和 media provider matrix。但对照 `new-api-main/relay/channel/task/*`，Suno、MiniMax、Kling、Vidu、Hailuo、Jimeng、Sora、Midjourney-like 等专有任务通道仍未形成真实 adapter 宽度。

## 目标

- 按 provider 分批实现专有 Media Task Adapter。
- 统一 create/get/cancel/download、任务状态、错误归一、产物 metadata 与 usage 归因。
- 每个 adapter 必须声明 support tier、capability、pricing source、smoke hint。
- 增加真实产物 smoke 证据入口，真实 key 缺失时可 skip。

## 非目标

- 不绕过 provider 官方授权、登录或付费限制。
- 不把本地 fake lifecycle 当成真实 provider 成功。
- 不一次性承诺所有媒体 provider 全量完成。

## 输入

- `docs/media-provider-executors.md`
- `GatewayAsyncResourceService`
- `GatewayAsyncResourceExecutor` 相关实现。
- `ProviderReferenceGapService.mediaRows`
- 参考项目 `new-api-main/relay/channel/task/*`

## 输出

- Provider-specific media adapter。
- Media provider matrix 更新。
- 真实 smoke 脱敏报告。
- 后台资源详情和 trace 可解释 metadata。

## 影响范围

- Video/Music async resource lifecycle。
- Gateway resource storage / lineage。
- Provider capability matrix。
- Request trace 和 usage 归因。

## 依赖

- 目标 provider 的测试账号或 mock transport。
- 对象存储或产物下载引用策略。

## 风险

- 媒体任务常为长耗时异步流程，需要处理轮询、超时、取消、幂等和重复回调。
- 产物下载可能涉及大文件、过期 URL 和内容安全审核。
- 真实 smoke 可能产生费用，必须显式启用。

## 验收标准

- 至少一个非 Gemini/Veo 的专有 media adapter 闭环 create/get/cancel/download。
- Media provider matrix 能区分 `SUPPORTED`、`ADAPTER_REQUIRED`、`NOT_NATIVE`。
- 真实 smoke 入口默认不消耗额度；只有显式环境变量启用才发起远程请求。
- 失败按认证、额度、网络、参数不支持、provider 限流分类。

## 测试边界

- Gateway async resource service 单测。
- Adapter fake transport 生命周期测试。
- Provider matrix 测试。
- 可选真实产物 smoke。

## 关联文档

- [REP-20260514](../../docs/reports/REP-20260514-reference-implementation-detail-comparison.md)
- [REQ-20260514-004](../../docs/requirements/REQ-20260514-004-provider-specific-media-task-adapters-smoke-closure.md)
- [media-provider-executors](../../docs/media-provider-executors.md)

## 关联任务

- 父任务：[TASK-20260514-002](../done/TASK-20260514-002-reference-implementation-detail-comparison.md)
- 相关任务：[TASK-20260506-019](../done/TASK-20260506-019-realtime-pool-media-adapters.md)

## 当前状态

Done。

## 实现结果

- 新增 `SunoMusicMediaProviderAdapter`，覆盖 Suno-like Music create、get、cancel、download 本地生命周期。
- `GatewayAsyncResourceService#mediaProviderMatrix` 将 Suno-like Music 标记为 `SUPPORTED` / `provider_specific_adapter`，并补充 `capability`、`pricing_source`、`smoke_hint`。
- `ProviderReferenceGapService.mediaRows` 将 Music 从 `ORCHESTRATED_LIMITED` 提升为 `PROVIDER_ADAPTER`，并明确 MiniMax/Udio 等仍属于后续拆分。
- 新增 `SunoMusicProviderSmokeHarnessTests`，仅在 `XAG_SMOKE_SUNO=true`、存在 baseUrl 和 key 时访问真实远端；默认通过 JUnit assumption skip，不消耗额度。
- metadata 固定记录 `provider_capability=music_generation`、`provider_pricing_source=operator_configured_suno_music_pricing` 与五类标准失败分类。

## 验证结果

已通过：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderReferenceGapServiceTests" --tests "com.prodigalgal.xaigateway.provider.adapter.suno.SunoMusicProviderSmokeHarnessTests"
```

覆盖点：

- Suno-like Music adapter create/get/cancel/download 生命周期。
- Music matrix 中 Suno-like support tier、capability、pricing source、smoke hint。
- 参考差距面板 Music provider adapter 状态。
- 真实 smoke 入口默认不访问远端。

## 遗留边界

- 真实 Suno-like 远端协议仍需在拿到目标 provider/baseUrl/key 后执行 `XAG_SMOKE_SUNO=true` 真实 smoke。
- MiniMax、Kling、Vidu、Hailuo、Jimeng、Sora、Midjourney-like 等专有 media adapter 继续作为后续扩展，不并入本任务。
