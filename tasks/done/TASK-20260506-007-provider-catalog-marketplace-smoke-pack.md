# TASK-20260506-007 Provider Catalog Marketplace 签名 Smoke 与回滚证据

状态：Done  
优先级：Medium  
来源：TASK-20260501-016 本地拆分  
关联任务：[TASK-20260501-016](TASK-20260501-016-provider-catalog-marketplace.md)  
关联需求：[REQ-20260506-005](../../docs/requirements/REQ-20260506-005-final-backlog-closure-design.md)

## 背景

`TASK-20260501-016` 覆盖 marketplace 主能力，但签名 smoke、更新失败保护和回滚证据需要独立验收，否则容易只完成 happy path。

## 目标

为 Provider Catalog Marketplace 增加本地可重复验证的签名 smoke、dry-run、失败不覆盖和回滚能力。

## 范围

- HMAC-SHA256 签名示例与测试夹具。
- Dry-run 验签，不写入缓存。
- 成功更新前保留 previous cache。
- 签名失败或 catalog 非法时不覆盖 current cache。
- 回滚接口可恢复 previous cache。

## 非目标

- 不存储真实生产签名密钥。
- 不实现非对称签名基础设施。

## 验收标准

- 签名正确时 dry-run 可返回 PASS 且不写入缓存。
- 签名错误时返回失败并保留当前版本。
- 回滚后当前版本恢复到 previous。
- 文档提供签名生成和回滚步骤。

## 实现记录

已完成签名 smoke 与回滚证据：

- Dry-run 验签通过时返回 `DRY_RUN_PASS`，不写入缓存。
- 签名失败时返回 `SIGNATURE_FAILED`，不覆盖当前版本。
- 第二次成功更新前会把当前版本复制到 `previous.json`。
- `rollback` 会把 `previous.json` 恢复为 current，并保留 rolled-back 证据文件。
- 文档补充签名生成、dry-run、缓存文件和回滚路径。

## 测试/验证

已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogMarketplaceServiceTests"
```

覆盖 dry-run、不覆盖、previous cache 和 rollback。

## 遗留问题

当前使用 HMAC 作为本地可验证 smoke 方案；后续正式 marketplace 可升级到 Ed25519/RSA。
