# TASK-20260522-017 功能性 Provider Smoke 协议入口地址对齐

## 任务类型

子任务

## 背景

来源：`docs/requirements/REQ-20260522-016-functional-provider-smoke-endpoint-alignment.md`

厂商协议入口与本地数据库已使用新的 MiMo token-plan 地址，但功能性 provider smoke harness 仍保留旧 MiMo 默认域名。为了让后续 MiMo/DeepSeek 真实 smoke 与厂商配置一致，需要先把 dry-run 和文档默认值收敛到当前协议入口。

## 目标

- 更新功能性 smoke 中 MiMo 默认 OpenAI-compatible 与 Anthropic-compatible Base URL。
- 更新 smoke harness 文档示例。
- 更新测试断言，覆盖未显式传 `baseUrl` 的默认 preview。

## 非目标

- 不执行真实 MiMo live 调用。
- 不改动真实 token 或凭证密文。
- 不扩展 DeepSeek smoke family。
- 不修改 record/replay 历史 fixture。

## 上游来源

- `docs/requirements/REQ-20260522-016-functional-provider-smoke-endpoint-alignment.md`
- `docs/requirements/REQ-20260522-013-provider-protocol-endpoints.md`
- 用户提供的 MiMo token-plan Base URL。

## 输入

- `FunctionalProviderSmokeHttpClient`
- `FunctionalProviderSmokeHttpClientTests`
- `docs/testing-smoke-harness.md`

## 输出

- smoke 默认 Base URL 与厂商协议入口一致。
- 更新后的文档示例。
- 后端测试结果。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/admin/application/FunctionalProviderSmokeHttpClient.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/FunctionalProviderSmokeHttpClientTests.java`
- `docs/testing-smoke-harness.md`
- `docs/index.md`
- `tasks/index.md`

## 依赖

- 厂商多协议入口默认值已确定。

## 风险

- 历史 fixture 仍使用旧域名，本任务不把旧 fixture 当成当前默认值事实源。

## 验收标准

- [x] MiMo OpenAI-compatible dry-run 默认 preview 使用 token-plan OpenAI 地址。
- [x] MiMo Anthropic-compatible dry-run 默认 preview 使用 token-plan Anthropic 地址。
- [x] smoke 文档示例地址更新。
- [x] 后端 smoke client 测试通过。

## 测试边界

- 后端：`FunctionalProviderSmokeHttpClientTests`。
- 不执行真实外部 API 调用。

## 关联文档

- `docs/requirements/REQ-20260522-016-functional-provider-smoke-endpoint-alignment.md`

## 关联任务

- `tasks/done/TASK-20260522-014-provider-protocol-endpoints.md`

## 当前状态

Done

## 实现结果

- 已将功能性 provider smoke 的 MiMo 默认 OpenAI-compatible 地址改为 `https://token-plan-sgp.xiaomimimo.com/v1`。
- 已将功能性 provider smoke 的 MiMo 默认 Anthropic-compatible 地址改为 `https://token-plan-sgp.xiaomimimo.com/anthropic`。
- 已更新 smoke harness 文档中的 MiMo 地址矩阵和 `baseUrl` 参数说明。
- 已补充 Anthropic-compatible dry-run 默认地址回归测试。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeHttpClientTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeCertificationServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeRecordReplayFixtureVerifierTests"`

## 遗留边界

- 不包含真实 live smoke。
- 不包含历史 record/replay fixture 回写。
