# TASK-20260522-023 功能性 Provider Smoke 认证策略对齐

## 任务类型

子任务

## 背景

来源：`docs/requirements/REQ-20260522-022-functional-provider-smoke-auth-strategy.md`

MiMo、DeepSeek 等 OpenAI-compatible 厂商入口已经进入厂商协议入口与凭证链路。功能性 Provider Smoke 是这条链路进入真实验证的下一步，但当前 OpenAI-compatible smoke 使用 `api-key` header，与运行时和主流厂商 Bearer token 认证不一致。

## 目标

- 修正 OpenAI-compatible 功能性 smoke 的认证 header。
- 保持 Gemini 与 Anthropic-compatible 的现有认证行为。
- 增加单元测试覆盖 dry-run 预览和 live 请求 header。
- 回写需求、任务和索引。

## 非目标

- 不执行真实厂商 API 大额调用。
- 不改变运行时执行器。
- 不调整数据库结构。
- 不扩大到前端 UI 改造。

## 上游来源

- `docs/requirements/REQ-20260522-022-functional-provider-smoke-auth-strategy.md`
- `tasks/done/TASK-20260522-017-functional-provider-smoke-endpoint-alignment.md`
- `tasks/done/TASK-20260522-022-distributed-key-create-initial-account-group-binding.md`

## 输入

- `FunctionalProviderSmokeHttpClient`
- `CredentialAdminService.functionalProviderSmoke`
- MiMo/DeepSeek OpenAI-compatible 厂商协议入口

## 输出

- OpenAI-compatible smoke 请求使用 Bearer token。
- 后端测试覆盖认证策略。
- 文档和任务索引更新。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/admin/application/FunctionalProviderSmokeHttpClient.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminServiceTests.java`
- `docs/requirements/REQ-20260522-022-functional-provider-smoke-auth-strategy.md`
- `docs/index.md`
- `tasks/index.md`

## 依赖

- 已有 `FunctionalProviderSmokeHttpClient`。
- 已有 MiMo/DeepSeek 厂商协议入口默认配置。

## 风险

- 某些非主流 OpenAI-compatible 站点可能使用 `api-key`，后续需通过 endpoint `authStrategy` 精细化；本轮以当前运行时和已接入厂商为准。

## 验收标准

- [x] OpenAI-compatible smoke dry-run 预览显示 `Authorization: Bearer ***`。
- [x] OpenAI-compatible live smoke 发送 Bearer token。
- [x] Gemini 与 Anthropic-compatible 认证行为保持不变。
- [x] 编译和后端定向测试通过。

## 测试边界

- 后端：`CredentialAdminServiceTests`。
- 不执行真实外部 API。

## 关联文档

- `docs/requirements/REQ-20260522-022-functional-provider-smoke-auth-strategy.md`

## 关联任务

- `tasks/done/TASK-20260522-017-functional-provider-smoke-endpoint-alignment.md`
- `tasks/done/TASK-20260522-022-distributed-key-create-initial-account-group-binding.md`

## 当前状态

Done

## 实现结果

- `OPENAI_COMPATIBLE` 功能性 smoke 预览与真实请求已从 `api-key` header 对齐为 Bearer token。
- 增加 Anthropic-compatible dry-run 预览测试，确认其仍使用 Anthropic-style `api-key` header。
- 调整 OpenAI-compatible live smoke 测试，通过本地 HTTP server 捕获 `Authorization: Bearer mimo-secret`。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests"`
- `.\gradlew.bat compileJava compileTestJava`

## 遗留边界

- 不包含真实外部 API smoke。
- 不包含 endpoint `authStrategy` 驱动的多认证策略扩展。
