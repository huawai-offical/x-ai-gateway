# Client Instance 与插件/Deep Link 授权下发

日期：2026-05-06  
关联需求：[REQ-20260506-018 Client Instance 管理与插件/Deep Link 授权下发](requirements/REQ-20260506-018-client-instance-plugin-deeplink.md)  
关联任务：[TASK-20260506-022 Client Instance 管理与插件/Deep Link 授权下发](../tasks/done/TASK-20260506-022-client-instance-plugin-deeplink.md)

## 背景

CLI/IDE 接入云端代理时，`clientFamily` 只能说明客户端类型，不能说明具体设备、插件实例或 workspace。`client_instance` 将实例注册、授权下发、撤销和 trace/usage 聚合从请求 metadata 提升为后端可运营对象。

## 核心模型

| 模型 | 表 | 说明 |
| --- | --- | --- |
| `ClientInstanceEntity` | `client_instance` | 记录 `DistributedKey` 下的实例、客户端族、workspace hint、插件信息和生命周期状态。 |
| `ClientInstanceGrantEntity` | `client_instance_grant` | 插件或 Deep Link 领取客户端配置的一次性授权，只保存 token hash 和加密后的完整 key。 |

`request_log` 与 `usage_record` 已补充 `client_family`、`client_instance`、`workspace_hint` 字段，用于后续按实例聚合 trace 和 usage。

## Admin API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/client-instances?distributedKeyId=` | 查询实例列表。 |
| `POST` | `/admin/client-instances` | 注册 client instance。 |
| `GET` | `/admin/client-instances/{id}` | 查询实例详情。 |
| `PUT` | `/admin/client-instances/{id}` | 更新实例元数据。 |
| `POST` | `/admin/client-instances/{id}/status` | 启用或禁用实例。 |
| `DELETE` | `/admin/client-instances/{id}` | 撤销实例，并撤销未消费授权。 |
| `POST` | `/admin/client-instances/{id}/authorizations` | 发行插件/Deep Link 一次性授权。 |
| `POST` | `/admin/client-instances/{id}/authorizations/{grantToken}/consume` | 消费一次性授权并领取客户端配置。 |
| `DELETE` | `/admin/client-instances/{id}/authorizations/{grantToken}` | 撤销一次性授权。 |

## 授权下发

发行授权时必须提供其一：

- `fullKey`：创建或轮换 DistributedKey 后仍在当前安全会话中持有完整 key。
- `secretExportGrantToken`：复用 DistributedKey 已有的一次性完整 secret export token，服务端验证后消费源 token，并派生 client instance 专属 grant。

`grantToken` 默认 10 分钟有效，最长 15 分钟。消费成功后会立即写入 `consumedAt`，不能重复消费。

## Deep Link Schema

Deep Link 示例：

```text
xag://authorize/client-instance?grantToken=<one-time-grant>&baseUrl=https%3A%2F%2Fgateway.example.com%2Fv1&clientFamily=CODEX&clientInstance=codex-main&workspaceHint=repo-a&format=env
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `grantToken` | 一次性授权 token，不是长期 secret。 |
| `baseUrl` | 云端 gateway `/v1` endpoint。 |
| `clientFamily` | 规范化后的客户端族，例如 `CODEX`、`CURSOR`。 |
| `clientInstance` | 归一化实例标识。 |
| `workspaceHint` | 用户显式提供的 workspace hint，不扫描本地目录。 |
| `format` | 配置格式：`config_toml`、`auth_json`、`env`、`curl`。 |

## Plugin Message Schema

插件授权消息示例：

```json
{
  "type": "x-ai-gateway.client_authorization",
  "grantToken": "<one-time-grant>",
  "baseUrl": "https://gateway.example.com/v1",
  "clientFamily": "CODEX",
  "clientInstance": "codex-main",
  "workspaceHint": "repo-a",
  "format": "env",
  "expiresAt": "2026-05-06T10:10:00Z",
  "secretPolicy": "one_time_grant"
}
```

消费后返回 `x-ai-gateway.client_config`，其中 `config` 才包含完整 key；该响应只能通过一次性 grant 获取一次。

## 安全边界

- 服务端不保存 grant token 明文，只保存 `CredentialCryptoService.fingerprint(token)`。
- 完整 DistributedKey 只以密文写入 `client_instance_grant.full_key_ciphertext`。
- Deep Link 和 plugin message 不携带完整长期 secret。
- 撤销 instance 会同步撤销未消费 grant。
- 本能力不读取、扫描或上传用户 workspace。
