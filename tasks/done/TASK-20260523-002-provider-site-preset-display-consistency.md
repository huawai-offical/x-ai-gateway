# TASK-20260523-002 厂商管理与预设导入口径一致性

## 任务类型

父任务

## 背景

来源：`docs/requirements/REQ-20260523-001-provider-site-preset-display-consistency.md`

厂商管理页面同时展示已落库的厂商/API 入口和可导入的 provider preset。小米 MiMo 属于多协议厂商：站点默认类型是 `OPENAI_COMPATIBLE_GENERIC`，但 preset 下挂的厂商协议入口还包含 Anthropic-compatible endpoint。当前页面把这些字段都叫“协议入口”，导致用户误以为数据不一致。

## 目标

- 查清 MiMo preset、已导入站点和前端展示的字段来源。
- 统一页面展示口径，区分站点类型、协议簇和运行时 provider type。
- 必要时补齐预设响应里的 protocol endpoints，让预设导入能展示即将导入的协议入口列表。
- 更新测试与本地文档。

## 非目标

- 不改变 MiMo preset 的实际多协议能力。
- 不修改现有凭证或访问密钥授权。
- 不重做厂商管理整体信息架构。

## 上游来源

- `docs/requirements/REQ-20260523-001-provider-site-preset-display-consistency.md`

## 输入

- `provider-catalog.json`
- `/admin/provider-sites`
- `/admin/provider-sites/presets`
- 厂商管理前端页面

## 输出

- 口径一致的厂商管理展示。
- 需求、任务与索引回写。
- 定向验证记录。

## 影响范围

- `src/main/resources/provider-catalog.json`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/ProviderSitePresetResponse.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/ProviderSiteRegistryService.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/ProviderSiteAdminService.java`
- `web/src/features/provider-sites/provider-sites-page.tsx`
- `web/src/features/provider-sites/types.ts`
- 相关测试

## 依赖

- 现有厂商多协议入口数据模型。
- 现有 provider preset import 幂等逻辑。

## 风险

- 后端 preset response 若不返回 endpoint 列表，前端只能展示不完整信息。
- UI 文案需要避免新增解释性长文，保持操作页面可扫描。

## 验收标准

- [x] 完成 MiMo 数据源和 UI 字段来源核对。
- [x] 厂商聚合表格区分站点类型与厂商协议入口。
- [x] 预设导入表格展示将导入的 protocol endpoints。
- [x] 测试/类型检查通过或记录无法执行原因。
- [x] 文档和任务状态回写。

## 实现结果

- 确认 MiMo 的 `OPENAI_COMPATIBLE_GENERIC` 是顶层默认站点类型，`ANTHROPIC_DIRECT` 是下挂 Anthropic-compatible 协议入口的运行时 provider type，二者不是同一字段。
- `ProviderSitePresetResponse` 新增 `protocolEndpoints`，`ProviderSiteRegistryService` 通过 `protocolEndpointSeeds` 生成预设导入前的 endpoint preview。
- 厂商聚合表格新增“站点类型”“厂商协议入口”，预设导入表格新增“默认站点类型”“将导入协议入口”。
- 前端类型允许预设 endpoint 的 `id` / `siteProfileId` 为 `null`，并补充 MiMo 多协议渲染测试。

## 验证记录

- 通过：`.\gradlew.bat compileJava compileTestJava test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ProviderSiteAdminControllerTests"`
- 通过：`bun run typecheck`
- 通过：`bun run test -- --run src/features/provider-sites/provider-sites-page.test.tsx`
- 通过：`bunx eslint src/features/provider-sites/provider-sites-page.tsx src/features/provider-sites/provider-sites-page.test.tsx src/features/provider-sites/types.ts`
- 浏览器抽查：`http://localhost:5173/console/provider-sites` 登录后可看到新列；已导入 MiMo 行显示 `xiaomi_mimo.openai_compatible`、`xiaomi_mimo.anthropic_compatible` 和 `ANTHROPIC_DIRECT`；页面无 console warning/error 与框架 overlay。
- 浏览器限制：当前 8080 为本轮改动前旧后端进程，预设接口响应未包含新 `protocolEndpoints` 字段，预设行仍走前端兼容兜底。代码级和接口测试已覆盖重启后端后的新响应。

## 测试边界

- 后端：provider preset response / import 映射定向测试。
- 前端：厂商管理页面表格渲染测试。
- 浏览器：厂商管理页面视觉和交互抽查。

## 关联文档

- `docs/requirements/REQ-20260523-001-provider-site-preset-display-consistency.md`

## 关联任务

- `tasks/done/TASK-20260522-014-provider-protocol-endpoints.md`
- `tasks/done/TASK-20260522-018-credential-multi-protocol-endpoint-binding.md`

## 当前状态

Done
