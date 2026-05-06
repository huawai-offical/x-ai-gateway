# TASK-20260501-001 Provider Registry 2.0：插件化供应商目录、预设导入与厂商元数据

状态：Done  
优先级：High  
来源：Linear X-282（已迁移到本地）  
关联报告：[REP-20260501](../../docs/reports/REP-20260501-open-source-gap-analysis.md)  
关联设计：[REQ-20260501-002](../../docs/requirements/REQ-20260501-002-priority-task-closure-design.md)

## 背景

`new-api` 已在 `constant/channel.go`、`relay/channel/*` 中覆盖大量厂商、聚合站和特殊渠道；`CC Switch` 也有 50+ 供应商预设。当前 `x-ai-gateway` 的 `ProviderFamily` 仍主要是 `OPENAI / ANTHROPIC / GEMINI / OLLAMA`，`UpstreamSiteKind` 虽扩展了一批 OpenAI-compatible 站点，但缺少统一的供应商目录、预设导入、厂商元数据和 conformance 绑定。

## 本轮目标

把 Provider Registry 从枚举驱动推进到可查询、可导入、可幂等落库的 provider preset 目录。

## 本轮完成范围

- 新增 `ProviderSitePresetResponse` 与 `ProviderSitePresetImportRequest`。
- 新增 provider preset catalog，内置 OpenAI、Azure OpenAI、DeepSeek、OpenRouter、Anthropic、Gemini 六个代表性预设。
- 新增 Admin API：
  - `GET /admin/provider-sites/presets`
  - `GET /admin/provider-sites/presets/{code}`
  - `POST /admin/provider-sites/presets/{code}/import`
- 导入使用 `preset:{code}`，重复导入不覆盖用户已有配置。
- 导入后可刷新基础能力快照，能力矩阵能立即展示该站点基础 metadata。

## 非目标

- 不一次性实现所有参考项目的 30+ provider adapter。
- 不改变现有分发 Key 与账号池核心模型。
- 不在本轮把静态 preset 升级为数据库可配置 catalog。

## 验收结果

- Provider preset 可列表、详情、导入。
- 预设导入具备幂等保护。
- 代表性预设数量大于 5。

## 测试/验证

已通过：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests"
```

关键覆盖：

- `shouldListPresetCatalogWithImportedFlag`
- `shouldReturnExistingPresetWithoutOverwritingUserConfiguration`
- `shouldCreatePresetProfileAndRefreshSnapshot`

## 遗留问题

- 动态 provider catalog、catalog 版本化和 provider conformance 仍需后续任务承接。
