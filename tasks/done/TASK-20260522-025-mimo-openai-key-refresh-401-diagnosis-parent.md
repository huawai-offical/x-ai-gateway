# TASK-20260522-025 MiMo OpenAI Key 刷新模型 401 排查父任务

## 任务类型

父任务

## 背景

来源：`docs/requirements/REQ-20260522-024-mimo-openai-key-refresh-401-diagnosis.md`

`Xiaomi MiMo OpenAI Key 1` 刷新模型时收到 MiMo 上游 `/v1/models` 的 `401 Unauthorized`，但 `Xiaomi MiMo OpenAI Key 2` 正常，且控制台中两条 Key 都显示正常。需要从本地数据库与刷新模型链路解释差异。

## 目标

- 脱敏对比两条凭证的 provider type、Base URL、协议入口、站点、健康状态和 key fingerprint。
- 核对刷新模型链路如何构造 `GET /v1/models` 请求和认证头。
- 确认控制台“正常”与真实刷新模型失败之间是否存在状态记录缺口。
- 输出结论与建议。

## 非目标

- 不修改真实 Key。
- 不输出 secret、密文全文或可还原认证材料。
- 不登录外部 MiMo 控制台。
- 不把本轮排查扩展为完整 provider smoke 改造。

## 上游来源

- `docs/requirements/REQ-20260522-024-mimo-openai-key-refresh-401-diagnosis.md`

## 输入

- 用户提供的错误信息和 traceId。
- 本地 `upstream_credential`、`provider_protocol_endpoint`、`upstream_site_profile` 脱敏查询结果。
- 后端刷新模型实现。

## 输出

- Key 1/Key 2 差异结论。
- 控制台状态与刷新失败之间的解释。
- 文档与任务状态回写。

## 影响范围

- 文档：`docs/requirements/REQ-20260522-024-mimo-openai-key-refresh-401-diagnosis.md`
- 任务：`tasks/done/TASK-20260522-025-mimo-openai-key-refresh-401-diagnosis-parent.md`
- 可能只读参考：
  - `src/main/java/com/prodigalgal/xaigateway/gateway/core/catalog/CredentialModelDiscoveryService.java`
  - `src/main/java/com/prodigalgal/xaigateway/gateway/core/credential/CredentialMaterialResolver.java`
  - `src/main/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminService.java`

## 依赖

- 能访问当前本地 PostgreSQL 数据库。
- 查询输出必须脱敏。

## 风险

- 数据库连接不可用时只能给出代码链路层面的解释。
- 上游 401 的根因可能在 MiMo 侧权限、额度、区域或 Key 状态，无法仅靠本地代码完全证明。
- 本地健康字段可能不是刷新模型失败的完整事实源。

## 验收标准

- [x] 完成脱敏数据对比。
- [x] 完成刷新模型链路核对。
- [x] 给出 Key 1 失败、Key 2 正常的主要原因判断。
- [x] 回写需求与任务状态。

## 测试边界

- 本轮以只读数据库查询和代码链路核对为主。
- 不执行真实上游模型刷新重放，除非用户明确要求。

## 关联文档

- `docs/requirements/REQ-20260522-024-mimo-openai-key-refresh-401-diagnosis.md`

## 关联任务

- `tasks/done/TASK-20260522-016-credential-protocol-endpoint-backfill.md`
- `tasks/done/TASK-20260522-017-functional-provider-smoke-endpoint-alignment.md`
- `tasks/done/TASK-20260522-023-functional-provider-smoke-auth-strategy.md`

## 排查结果

- Key 1：`id=8`，OpenAI-compatible，base URL 为 `https://token-plan-sgp.xiaomimimo.com/v1`，绑定 `protocol_endpoint_id=4`，active，未删除，无 cooldown，无 last error，`last_used_at=null`。
- Key 2：`id=9`，OpenAI-compatible，base URL 为 `https://token-plan-sgp.xiaomimimo.com/v1`，同样绑定 `protocol_endpoint_id=4`，active，未删除，无 cooldown，无 last error，`last_used_at=2026-05-22 23:18:48.568618`。
- 两条凭证的 endpoint、site、provider type、base URL、auth strategy 均一致，均走 `Authorization: Bearer <secret>` 请求 `GET https://token-plan-sgp.xiaomimimo.com/v1/models`。
- 两条凭证的 `api_key_fingerprint` 不同，说明本地实际存储的 Key 1 与 Key 2 是不同 secret。
- 站点级能力 `site_model_capability.site_profile_id=2` 已在 `2026-05-22 23:18:48.565465` 刷出 8 个 MiMo 模型；Key 2 有 8 条 discovered `model_policy`，Key 1 没有对应成功刷新痕迹。
- 用户提供的 traceId 未出现在 `request_log` 或 `ops_system_event`，说明刷新模型失败没有进入普通请求日志或系统事件沉淀。

## 结论

本地配置层面已排除绑定错误：Key 1 与 Key 2 走同一个 MiMo OpenAI-compatible endpoint，同一个 base URL，同一种 Bearer 鉴权。Key 1 失败、Key 2 成功的主要差异是本地存储 secret 不同，且 Key 1 从未成功刷新过。

Key 1 的 401 更可能来自 MiMo 上游对 Key 1 当前 secret 的拒绝，例如本地保存值与控制台当前 Key 不一致、Key 1 被轮换/撤销、权限范围不含 `/v1/models`，或 MiMo 控制台“正常”不代表模型列表接口授权正常。

控制台仍显示正常的原因是当前 `refreshCredential` 只有成功后才写 `last_used_at` 并清空错误；上游 401 抛出时没有写回 `last_error_*`、`cooldown_until` 或系统事件。

## 后续任务

- `tasks/backlog/TASK-20260523-001-refresh-model-failure-health-writeback.md`

## 验证记录

- 只读 JDBC 查询 `upstream_credential`、`provider_protocol_endpoint`、`upstream_site_profile`。
- 只读 JDBC 查询 `credential_model_catalog`、`site_model_capability`、`model_policy`。
- 只读 JDBC 查询 `request_log`、`ops_system_event`。
- 代码核对 `CredentialMaterialResolver#resolveStored`。
- 代码核对 `CredentialModelDiscoveryService#refreshCredential`、`discoverOpenAiCompatible`、`buildOpenAiCompatibleClient`、`resolveOpenAiModelsPath`。

## 当前状态

Done
