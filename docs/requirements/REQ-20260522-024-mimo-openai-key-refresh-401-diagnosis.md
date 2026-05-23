# REQ-20260522-024 MiMo OpenAI Key 刷新模型 401 排查

## 背景

用户反馈在上游凭证页面刷新 `Xiaomi MiMo OpenAI Key 1` 模型时失败：

```text
401 Unauthorized from GET https://token-plan-sgp.xiaomimimo.com/v1/models traceId: 439de3c7-ef11-4228-af5d-fdbb6345bd60
```

同一控制台中 `Xiaomi MiMo OpenAI Key 2` 刷新模型正常，且用户在控制台查看两条 Key 都显示正常。需要解释 Key 1 与 Key 2 在实际刷新模型链路中的差异，避免只依据前端“正常”状态判断。

## 目标

- 对比两条 MiMo OpenAI 凭证的真实数据库字段、协议入口绑定、Base URL、认证策略、健康字段和密钥指纹。
- 结合后端刷新模型实现，确认 401 是上游认证失败、凭证绑定错误、协议入口错误，还是控制台状态没有记录刷新失败导致的误判。
- 排查过程中不输出 API Key 明文、密文全文或可还原 secret。
- 给出用户可执行的结论与下一步处理建议。

## 范围

- `upstream_credential` 中两条 MiMo OpenAI Key 的脱敏字段对比。
- `provider_protocol_endpoint` 与 `upstream_site_profile` 的绑定信息对比。
- `CredentialModelDiscoveryService#refreshCredential` 与认证头构造逻辑。
- 与 traceId 相关的本地日志或数据库错误记录，若存在则脱敏引用。

## 非目标

- 不直接修改或替换用户的真实上游 API Key。
- 不输出真实 secret、密文全文或任何可用于重放请求的认证材料。
- 不对 MiMo 控制台做外部登录操作。
- 未经确认不改变凭证健康状态、cooldown 或运行时路由策略。

## 风险

- MiMo 控制台“正常”可能只代表 Key 未被删除或未禁用，不代表 `/v1/models` 端点具备访问权限。
- 如果本地刷新模型失败路径未写回 `last_error`，控制台可能继续显示正常状态，造成观感不一致。
- 两条凭证可能绑定同一协议入口但持有不同 secret，必须通过密钥指纹和脱敏字段对比确认。

## 验收标准

- 能说明 Key 1 与 Key 2 在刷新模型请求中的关键差异。
- 能解释为什么控制台看起来正常但刷新模型返回 401。
- 排查记录不泄露敏感凭证材料。
- 本地需求与任务记录完成回写。

## 排查结果

### 数据库脱敏对比

- `Xiaomi MiMo OpenAI Key 1`：`id=8`，`provider_type=OPENAI_COMPATIBLE`，`base_url=https://token-plan-sgp.xiaomimimo.com/v1`，`site_profile_id=2`，`protocol_endpoint_id=4`，`group_id=4`，`is_active=true`，`deleted=false`，`cooldown_until=null`，`last_error_code=null`，`last_error_message=null`，`last_used_at=null`。
- `Xiaomi MiMo OpenAI Key 2`：`id=9`，`provider_type=OPENAI_COMPATIBLE`，`base_url=https://token-plan-sgp.xiaomimimo.com/v1`，`site_profile_id=2`，`protocol_endpoint_id=4`，`group_id=4`，`is_active=true`，`deleted=false`，`cooldown_until=null`，`last_error_code=null`，`last_error_message=null`，`last_used_at=2026-05-22 23:18:48.568618`。
- 两条凭证绑定同一个 `xiaomi_mimo.openai_compatible` 协议入口，入口 `auth_strategy=BEARER`，`path_strategy=OPENAI_V1`，`endpoint_base_url=https://token-plan-sgp.xiaomimimo.com/v1`。
- 两条凭证的 `api_key_fingerprint` 不同，说明本地实际用于请求的 secret 不是同一个。Key 1 的脱敏 fingerprint 为 `4d0424a8f406...0bd78aca`，Key 2 为 `ad1ec4590b12...f24f35bc`。
- Key 2 的成功刷新已写入站点级能力：`site_model_capability.site_profile_id=2` 有 8 个 active 模型，`source_refreshed_at=2026-05-22 23:18:48.565465`；Key 2 还有 8 条 `CREDENTIAL` scope 的 discovered `model_policy`。Key 1 没有成功刷新痕迹。
- `traceId=439de3c7-ef11-4228-af5d-fdbb6345bd60` 未在 `request_log` 或 `ops_system_event` 中找到记录。刷新模型属于管理端模型发现调用，不走普通网关请求生命周期日志。

### 代码链路结论

- `CredentialMaterialResolver#resolveStored` 会解密 `upstream_credential.api_key_ciphertext`，并把该 secret 作为当前凭证的认证材料。
- `CredentialModelDiscoveryService#refreshCredential` 先调用 `discover(credential.getProviderType(), credential.getBaseUrl(), credentialMaterial)`；只有上游模型发现成功后，才会清空 `last_error_*`、清空 `cooldown_until` 并写入 `last_used_at`。
- OpenAI-compatible 模型发现会对 MiMo base URL 发起 `GET /models`。由于 base URL 已以 `/v1` 结尾，实际请求地址就是 `https://token-plan-sgp.xiaomimimo.com/v1/models`。
- MiMo 入口策略为 Bearer，因此实际认证头是 `Authorization: Bearer <Key 1 或 Key 2 的本地存储 secret>`。

## 结论

本地配置层面已排除 `baseUrl`、`providerType`、协议入口、站点绑定和认证策略差异；Key 1 与 Key 2 的关键差异是本地存储的 API Key secret 不同，并且只有 Key 2 具备成功刷新记录。

因此 Key 1 的 401 更像是 MiMo 上游对 Key 1 当前 secret 的拒绝：可能是本地保存的 Key 1 与 MiMo 控制台当前值不一致、复制/轮换后未更新、Key 1 被上游撤销或权限范围不包含 `/v1/models`，也可能是 MiMo 控制台“正常”只表示 Key 记录存在而不代表模型列表端点授权通过。

控制台仍显示两条 Key 正常，是因为当前刷新模型失败路径不会把上游 401 写回 `upstream_credential.last_error_*` 或 `cooldown_until`；失败只作为管理接口异常返回给前端，数据库健康字段仍保持空值。

## 后续建议

- 重新从 MiMo 控制台复制 `Xiaomi MiMo OpenAI Key 1` 的完整 Key，覆盖保存到本地后再刷新模型。
- 若覆盖后仍 401，需要在 MiMo 侧核对 Key 1 是否具有 OpenAI-compatible `/v1/models` 权限、是否绑定了正确区域/项目/计划，以及是否与 Key 2 属于相同产品权限。
- 新增后续增强任务：刷新模型失败时写回凭证 `last_error_*` 并记录系统事件，避免控制台继续显示“正常”造成误判。

## 验证记录

- 只读 JDBC 查询 `upstream_credential`、`provider_protocol_endpoint`、`upstream_site_profile`，完成两条凭证脱敏对比。
- 只读 JDBC 查询 `credential_model_catalog`、`site_model_capability`、`model_policy`，确认 Key 2 有成功刷新结果，Key 1 无成功刷新痕迹。
- 只读 JDBC 查询 `request_log` 与 `ops_system_event`，确认用户提供 traceId 未落库。
- 代码核对：
  - `src/main/java/com/prodigalgal/xaigateway/gateway/core/credential/CredentialMaterialResolver.java`
  - `src/main/java/com/prodigalgal/xaigateway/gateway/core/catalog/CredentialModelDiscoveryService.java`

## 当前状态

Done
