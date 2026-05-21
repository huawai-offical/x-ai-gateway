# Provider Catalog Marketplace 与签名更新（后端保留说明）

> 当前状态：控制台中的 `站点档案`、`Provider 参考差距` 与相关治理入口已下线。本文保留的是 `/admin/provider-sites/*` 背后的 catalog marketplace 后端说明，不再表示这些能力仍以控制台页面形式对外提供。

## 管理端入口

```text
GET /admin/provider-sites/catalog-marketplace/status
POST /admin/provider-sites/catalog-marketplace/updates
POST /admin/provider-sites/catalog-marketplace/rollback
```

## 更新请求

```json
{
  "remoteUrl": "https://example.com/provider-catalog.json",
  "catalogJson": "{...}",
  "signature": "sha256=<hmac-sha256-hex>",
  "signingKey": "<只在请求中提供，不落盘>",
  "dryRun": true
}
```

`remoteUrl` 与 `catalogJson` 至少提供一个。生产建议使用 `remoteUrl`，本地 smoke 可直接传入 `catalogJson`。

## 签名规则

首版采用 HMAC-SHA256：

```text
signature = HMAC_SHA256(catalogJson, signingKey)
```

接口接受裸 hex 或 `sha256=<hex>` 格式。签名失败时返回 `SIGNATURE_FAILED`，不会覆盖 `current.json`。

## 缓存与回滚

缓存目录位于：

```text
${gateway.storage.file-root}/provider-catalog-marketplace/
```

关键文件：

- `current.json`：当前生效 catalog。
- `previous.json`：上一次成功 catalog，用于回滚。
- `manifest.json`：版本、来源、hash、签名状态和更新时间。

加载顺序：

1. 已签名 marketplace cache。
2. `classpath:provider-catalog.json`。
3. builtin fallback。

## Dry-run

`dryRun=true` 时只校验签名和 catalog schema，不写入缓存。适合 CI、发布前检查或人工导入前预检。

## 回滚

`POST /admin/provider-sites/catalog-marketplace/rollback` 会把 `previous.json` 恢复为 `current.json`。如果不存在 previous cache，则返回 `ROLLBACK_UNAVAILABLE`。

## 当前取舍

HMAC 能满足本地闭环和私有化部署首版校验，后续公开 marketplace 分发建议升级到 Ed25519/RSA 非对称签名，并把 signing public key 固定到配置或发布制品中。
