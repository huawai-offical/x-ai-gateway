# TASK-20260501-016 Provider Catalog Marketplace 与签名更新

状态：Done  
优先级：Medium  
来源：TASK-20260501-013 后续拆分  
关联任务：[TASK-20260501-013](../done/TASK-20260501-013-dynamic-provider-catalog.md)  
关联需求：[REQ-20260506-005](../../docs/requirements/REQ-20260506-005-final-backlog-closure-design.md)

## 背景

当前 Provider Catalog 已支持 classpath 本地加载与 fallback，但还不是可分发、可校验、可更新的 marketplace。

## 目标

支持远程 catalog 拉取、签名校验、版本比对、灰度更新和回滚。

## 范围

- catalog manifest 与 signature schema。
- remote fetch 与本地缓存。
- 版本变更审计。
- catalog 更新失败回退到最近可用版本。

## 非目标

- 不在此任务中实现具体 provider executor。

## 验收标准

- 可校验远程 catalog 签名并缓存。
- 失败时可继续使用本地 fallback 或最近成功版本。
- Admin API 可展示当前版本、来源、更新时间和校验状态。

## 实现记录

已完成首版 marketplace 闭环：

- 新增 `GET /admin/provider-sites/catalog-marketplace/status`。
- 新增 `POST /admin/provider-sites/catalog-marketplace/updates`。
- 新增 `POST /admin/provider-sites/catalog-marketplace/rollback`。
- `ProviderCatalogLoader` 优先读取 `${gateway.storage.file-root}/provider-catalog-marketplace/current.json`，失败后回退 classpath，再回退 builtin fallback。
- 更新接口支持 `remoteUrl` 或 `catalogJson`，并强制 HMAC-SHA256 签名校验。
- 成功更新写入 `current.json`、`manifest.json`，并在覆盖前保留 `previous.json`。
- 新增文档：[provider-catalog-marketplace](../../docs/provider-catalog-marketplace.md)。

## 测试/验证

已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogMarketplaceServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests"
```

覆盖签名更新、cache 加载、坏签名不覆盖、previous cache 与回滚。

## 遗留问题

后续公开 marketplace 分发建议升级到非对称签名，并增加远程拉取超时、代理、镜像源和 public key 轮换配置。
