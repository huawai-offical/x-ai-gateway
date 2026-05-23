# TASK-20260523-014 厂商领域 API 命名与对象边界收敛

状态：In Progress  
优先级：High  
上游来源：[REP-20260523](../../docs/reports/REP-20260523-credential-provider-domain-model.md)

## 目标

重塑管理 API 响应和文案边界，让前端面向 Vendor、Protocol Endpoint、Account Group、Credential 组织数据，减少 Provider Site / Preset 等内部概念外泄。

## 范围

- 增加或调整厂商详情聚合响应。
- 明确 Provider Site 作为协议入口运行档案的内部定位。
- 将 Preset 导入改成厂商初始化动作。

## 非目标

- 不删除旧 API。
- 不做破坏性迁移。

## 验收标准

- 前端能用一个聚合 API 渲染厂商详情主路径。
- 旧 API 仍兼容现有页面和测试。

## 本轮实施切片

2026-05-23 开始实施第一切片：新增管理端只读聚合事实源，用于表达 `Vendor -> Protocol Endpoint -> Account Group -> Credential` 关系，并把账号分组覆盖的协议入口明确标记为由当前凭证反推。该切片不做 schema 迁移，不删除旧 API，不改变运行时路由。

### 已完成

- 新增厂商领域聚合 API，供后续厂商目录 UI 使用。
- 聚合响应包含厂商、协议入口、账号分组、凭证摘要、Distributed Key 账号组绑定摘要。
- 覆盖账号分组无显式入口绑定时的反推语义。

### 实现记录

- 新增 `/admin/provider-sites/domain-catalog` 只读接口。
- 新增 `ProviderDomainCatalogService` 与 `ProviderDomainCatalogResponse`。
- 响应中账号分组的 `endpointCoverage.source` 标记为 `credential_protocol_endpoint_id` 或 `credential_protocol_endpoint_missing`，避免误导为已有显式绑定表。
- 厂商目录页已接入该接口的摘要字段，作为 UI 收敛第一步。

### 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderDomainCatalogServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ProviderSiteAdminControllerTests"`
- `bun run test -- provider-sites-page.test.tsx`，工作目录 `web/`

### 剩余工作

- 厂商详情页尚未完全改为新聚合 API。
- 旧 `/admin/provider-sites`、`/presets` 仍作为兼容和导入动作存在。
- 是否新增账号分组与协议入口显式绑定表，留给 TASK-20260523-016 决策。
