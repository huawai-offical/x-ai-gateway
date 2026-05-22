# REQ-20260522-022 功能性 Provider Smoke 认证策略对齐

## 背景

厂商、协议入口、上游凭证、账号组与分发 Key 的配置链路已经串起来。下一步需要让功能性 Provider Smoke 成为可用的低成本验收入口，用于验证 MiMo、DeepSeek 等厂商协议入口是否能真实承接 Chat Completions、streaming 和 tools。

当前 `FunctionalProviderSmokeHttpClient` 对 `OPENAI_COMPATIBLE` 协议发送 `api-key` header。MiMo、DeepSeek 这类 OpenAI-compatible endpoint 与主运行时一样使用 Bearer token；如果 smoke 与运行时认证方式不一致，会出现配置链路正常但 smoke 误报认证失败的问题。

## 目标

- `OPENAI_COMPATIBLE` 功能性 smoke 使用 `Authorization: Bearer ***` 预览和 `Authorization: Bearer <secret>` 真实请求。
- 保持 `GEMINI_NATIVE` 使用 `x-goog-api-key`。
- 保持 `ANTHROPIC_COMPATIBLE` 使用 Anthropic-style header，不扩大协议行为。
- 后端测试覆盖 OpenAI-compatible smoke 的认证 header 和预览脱敏。
- 为后续 MiMo/DeepSeek 真实低成本 smoke 提供正确前置条件。

## 非目标

- 不执行大额或不可控真实调用。
- 不改变运行时 `OpenAiNativeGatewayChatRuntime` 的上游请求逻辑。
- 不调整厂商协议入口 schema。
- 不重写 smoke certification 记录结构。

## 范围

- `src/main/java/com/prodigalgal/xaigateway/admin/application/FunctionalProviderSmokeHttpClient.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminServiceTests.java`
- `docs/index.md`
- `tasks/index.md`

## 风险

- 若个别 OpenAI-compatible 聚合站点使用非 Bearer 认证，后续需要在协议入口层引入显式 `authStrategy` 到 smoke 请求计划；本轮先对齐当前运行时与主流 OpenAI-compatible 行为。
- 真实 smoke 仍可能因为模型名、额度、权限或厂商限流失败；本轮只修认证头错配。

## 验收标准

- [x] OpenAI-compatible smoke dry-run 预览显示 Bearer 认证且不泄露 secret。
- [x] OpenAI-compatible live smoke 请求发送 `Authorization: Bearer <secret>`。
- [x] Gemini 与 Anthropic-compatible smoke header 行为不回退。
- [x] 后端定向测试通过。

## 验证方式

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests"`
- `.\gradlew.bat compileJava compileTestJava`

## 当前状态

Done

## 实现结果

- `FunctionalProviderSmokeHttpClient` 的 `OPENAI_COMPATIBLE` 协议请求预览改为 `Authorization: Bearer ***`。
- `FunctionalProviderSmokeHttpClient` 的 `OPENAI_COMPATIBLE` live 请求改为发送 `Authorization: Bearer <secret>`。
- `ANTHROPIC_COMPATIBLE` 继续使用 `anthropic-version` 与 `api-key`，`GEMINI_NATIVE` 继续使用 `x-goog-api-key`。
- `CredentialAdminServiceTests` 增加 Anthropic-compatible 预览守卫，并把 OpenAI-compatible live smoke 断言改为捕获 Bearer header。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests"`
- `.\gradlew.bat compileJava compileTestJava`

## 遗留边界

- 本轮不执行真实 MiMo/DeepSeek 外部调用。
- 若后续要支持少数使用 `api-key` 的 OpenAI-compatible 聚合站点，需要把 endpoint `authStrategy` 引入 smoke request plan。

## 关联任务

- `tasks/done/TASK-20260522-023-functional-provider-smoke-auth-strategy.md`
