# REQ-20260522-016 功能性 Provider Smoke 协议入口地址对齐

## 背景

厂商多协议入口已经把 MiMo 默认 OpenAI-compatible 地址切换到 `https://token-plan-sgp.xiaomimimo.com/v1`，Anthropic-compatible 地址切换到 `https://token-plan-sgp.xiaomimimo.com/anthropic`。但历史功能性 provider smoke harness 仍保留旧 MiMo 示例域名，容易导致后续 dry-run、live smoke 与厂商协议入口配置分叉。

## 目标

- 将功能性 provider smoke 的 MiMo 默认 Base URL 与当前 provider catalog、协议入口默认值保持一致。
- 更新 smoke harness 文档中的 MiMo OpenAI-compatible 与 Anthropic-compatible 示例地址。
- 补充或更新测试，确保 dry-run preview 输出当前默认地址。

## 范围

- `FunctionalProviderSmokeHttpClient` 的默认 MiMo Base URL。
- `FunctionalProviderSmokeHttpClientTests` 中 MiMo dry-run 断言。
- `docs/testing-smoke-harness.md` 中 MiMo 地址和示例。
- 文档索引与任务索引回写。

## 非目标

- 不执行真实外部 API 调用。
- 不写入、打印或持久化真实 MiMo token。
- 不扩展新的 smoke resource family。
- 不改变 `allowLive` 与 `allowBillableProbes` 防护策略。

## 风险

- 历史 record/replay fixture 仍可能保留旧域名作为旧样本来源；本轮只更新当前默认 smoke 行为，不回写历史 fixture。

## 验收标准

- dry-run 未显式传 `baseUrl` 时，MiMo OpenAI-compatible preview 使用 `https://token-plan-sgp.xiaomimimo.com`。
- dry-run 未显式传 `baseUrl` 时，MiMo Anthropic-compatible preview 使用 `https://token-plan-sgp.xiaomimimo.com/anthropic`。
- 文档示例与当前 provider catalog 地址一致。
- 后端 smoke client 测试通过。

## 当前状态

Done

## 实现结果

- `FunctionalProviderSmokeHttpClient` 的 MiMo 默认 OpenAI-compatible Base URL 已更新为 `https://token-plan-sgp.xiaomimimo.com/v1`。
- `FunctionalProviderSmokeHttpClient` 的 MiMo 默认 Anthropic-compatible Base URL 已更新为 `https://token-plan-sgp.xiaomimimo.com/anthropic`。
- `docs/testing-smoke-harness.md` 中 MiMo 默认地址和 `baseUrl` 示例已与当前 provider catalog / 协议入口保持一致。
- 新增测试覆盖未显式传 `baseUrl` 时 Anthropic-compatible dry-run preview 的默认地址。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeHttpClientTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeCertificationServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeRecordReplayFixtureVerifierTests"`

## 遗留问题与后续建议

- 本轮没有执行真实 live smoke；后续可使用已入库 MiMo 凭证在显式 `allowLive=true` 与 `allowBillableProbes=true` 时执行最小 chat/messages 探测。
- 历史 record/replay sample fixture 仍保留旧域名作为历史样本来源，本轮不回写 fixture。
