# MIG-20260524-001 Provider Runtime Profile 非持久化兼容记录

状态：Done  
日期：2026-05-24  
关联需求：[REQ-20260524-001](../requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md)  
关联任务：[TASK-20260524-001-06](../../tasks/done/TASK-20260524-001-06-provider-specific-runtime-profile-split.md)

## 背景

`TASK-20260524-001-06` 需要让 MiMo、DeepSeek、xAI 等 provider-specific OpenAI-compatible 厂商在运行时、smoke 与观测中区别于 generic `OPENAI_COMPATIBLE`。当前数据库中 `ProviderType` 仍作为粗粒度运行时枚举使用，直接扩展枚举会影响历史凭证、账号组绑定、成本模型和旧路由治理数据。

## 决策

本阶段不修改数据库枚举、不新增持久化表，也不迁移现有 `upstream_credential.provider_type`。新增的 `ProviderRuntimeProfile` 是运行时派生视图，由 `providerType`、`siteKind`、`vendorCode` 与 `baseUrl` 推断：

- 历史凭证仍可保存为 `OPENAI_COMPATIBLE`。
- 运行时候选、路由亲和、allowed provider 白名单、interop debug、observability summary 和 metric tag 可使用 `runtimeProviderKey` 区分 `XIAOMI_MIMO`、`DEEPSEEK`、`XAI`。
- functional provider smoke record/replay 使用 provider-specific 字符串记录证据，但不反写数据库枚举。

## 范围

- 适用：MiMo、DeepSeek、xAI 等已进入核心支持范围且具备 provider-specific native profile 的 OpenAI-compatible 厂商。
- 不适用：Dify、OpenRouter、Together、Fireworks、SiliconFlow 等非默认核心 provider，不能因 generic compatible alias 重新进入核心承诺。
- 不适用：Cohere/Jina embed/rerank native executor，仍由 `TASK-20260524-001-07` 独立闭环。

## 验证边界

- `ProviderRuntimeProfileTests` 覆盖 MiMo、DeepSeek、xAI runtime key 推断。
- `GatewayRouteSelectionServiceTests` 覆盖 runtime provider key 的路由亲和和白名单匹配。
- `GatewayInteropPlanServiceTests` 与 `GatewayObservabilityServiceTests` 覆盖 debug / observability 中的 runtime provider 输出。
- `FunctionalProviderSmokeHttpClientTests`、`FunctionalProviderSmokeCertificationServiceTests`、`FunctionalProviderSmokeRecordReplayFixtureVerifierTests` 覆盖 MiMo、DeepSeek、xAI 的 provider-specific smoke/fixture 证据。

## 后续

如果未来需要把 runtime provider key 持久化为独立模型，应另建 migration 任务，先设计历史凭证映射、账号组白名单兼容、成本模型归属和回滚策略。
