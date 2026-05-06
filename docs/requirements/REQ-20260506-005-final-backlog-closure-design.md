# REQ-20260506-005 剩余任务闭环设计

状态：Done  
创建日期：2026-05-06  
关联任务：

- [TASK-20260501-016 Provider Catalog Marketplace 与签名更新](../../tasks/done/TASK-20260501-016-provider-catalog-marketplace.md)
- [TASK-20260506-007 Provider Catalog Marketplace 签名 Smoke 与回滚证据](../../tasks/done/TASK-20260506-007-provider-catalog-marketplace-smoke-pack.md)
- [TASK-20260501-012 国际化、公开文档与兼容性样例](../../tasks/done/TASK-20260501-012-i18n-public-docs-compatibility.md)

## 背景

按 `tasks/index.md` 中所有未完成任务排序，当前只剩两个现存 Backlog：`TASK-20260501-016` 为 Medium，`TASK-20260501-012` 为 Low。为了符合“挑选三个任务推进并闭环”的节奏，同时遵守“未完成即拆分”的本地任务规则，本轮从最高优的 `TASK-20260501-016` 拆出 `TASK-20260506-007`，专门承接签名 smoke、缓存回滚证据和本地可验证夹具。

## 目标

- 将 Provider Catalog 从 classpath/fallback 扩展为可分发、可签名校验、可缓存、可回滚的 marketplace。
- 为 marketplace 增加签名 smoke、缓存状态、回滚证据和文档，避免只完成主流程。
- 为公开文档与兼容性样例补齐本地可访问接口、SDK/curl/CLI 示例、错误码说明和双语 docs 基础结构。

## 范围

### Provider Catalog Marketplace

- 增加 marketplace 更新请求、状态响应、签名校验和缓存写入。
- 支持 HMAC-SHA256 签名校验，签名不通过时拒绝更新。
- Provider Catalog 加载顺序变为：已签名缓存优先，失败后回退 classpath，再回退 builtin fallback。
- Admin API 展示当前版本、来源、更新时间、校验状态和可用 preset 数。

### 签名 Smoke 与回滚证据

- 增加 dry-run 验签与 mock catalog 夹具。
- 更新成功前保留 previous cache，支持显式回滚到上一版。
- 文档记录签名生成、更新、dry-run、失败回退与回滚路径。

### 国际化、公开文档与兼容性样例

- 增加公开 docs bundle 接口，支持 `zh-CN` 与 `en-US`。
- 示例覆盖 OpenAI、Claude、Gemini、Ollama、curl、SDK、CLI。
- 文档覆盖错误码、限流、计费、路由行为和 conformance 入口。
- 本轮不实现完整前端 i18n 翻译矩阵，只补后端 docs bundle 与本地 Markdown。

## 非目标

- 不引入外部 provider executor。
- 不默认连接真实远程 marketplace。
- 不把签名密钥写入仓库。
- 不承诺所有第三方 SDK 的完整兼容性。

## 风险

- HMAC 适合作为首版本地可验证签名方案，后续正式分发仍建议升级到非对称签名。
- 远程拉取依赖网络、证书和代理，本轮提供 URL 拉取入口，但测试以本地 JSON 和 dry-run 为主。
- 公开文档接口首版是结构化说明，不等价于完整 OpenAPI 生成器。

## 验收标准

- 三个任务均移动到 `tasks/done/`，并回写实现结果、测试/验证、遗留问题和后续建议。
- Marketplace 更新必须校验签名，成功后可被 preset 列表读取。
- 签名失败时不覆盖当前缓存；回滚可恢复 previous cache。
- 公开 docs bundle 可按语言返回兼容性样例、错误码与接入步骤。
- 目标单元测试通过，文档索引和任务索引同步更新。

## 实现结果

- `TASK-20260501-016`：新增 Provider Catalog Marketplace 管理端接口，支持 status、signed update、rollback；`ProviderCatalogLoader` 现在优先读取已签名缓存，失败后回退 classpath/builtin。
- `TASK-20260506-007`：新增 HMAC-SHA256 dry-run、坏签名不覆盖 current cache、成功更新保留 previous cache、rollback 恢复 previous 的本地 smoke 覆盖。
- `TASK-20260501-012`：新增公开 docs bundle 接口 `/public/docs/compatibility`，支持 `zh-CN` 与 `en-US`，覆盖主流协议兼容矩阵、SDK/curl/CLI 示例、错误码、限流、计费、路由和 conformance 说明。
- 已新增本地说明文档：[provider-catalog-marketplace](../provider-catalog-marketplace.md)、[public-api-compatibility](../public-api-compatibility.md)。

## 验证情况

已通过目标测试：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogMarketplaceServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests"
```

验证覆盖：

- Marketplace dry-run 验签不写缓存。
- 正确签名可写入 current cache，并被 Provider Catalog Loader 读取。
- 错误签名不覆盖 current cache。
- 第二次成功更新会保留 previous cache，rollback 可恢复 previous。
- 公开 docs bundle 默认中文、英文切换、兼容矩阵、示例、错误码和 conformance checklist。

## 遗留问题

- 公开 marketplace 正式分发建议后续升级到非对称签名，并增加签名 public key 配置与轮换策略。
- 远程 URL 拉取的代理、证书、超时和镜像源策略后续可继续增强。
- `/public/docs/compatibility` 是结构化 docs bundle，不等价于完整 OpenAPI 生成器；前端语言切换组件也可后续单独补。
