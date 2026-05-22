# TASK-20260522-012 默认厂商 API 入口引导

## 任务类型

子任务

## 背景

来源：`docs/requirements/REQ-20260522-011-default-provider-site-bootstrap.md`

用户指出部分厂商/API 入口仍需要手动导入，和当前“厂商层作为主路径”的架构不一致。当前 provider catalog 已具备 OpenAI、MiMo、DeepSeek、Anthropic、Gemini、Perplexity 等预设，应该在系统启动时自动落库为 API 入口。

## 目标

- 默认资源引导阶段自动导入非 deprecated provider preset。
- 保证导入幂等且不覆盖用户配置。
- 保证无凭证时只生成入口和站点级快照，不触发真实外部模型发现。
- 补充单元测试和文档回写。

## 非目标

- 不自动写入 MiMo/DeepSeek/OpenAI 等真实 key。
- 不改变凭证创建、账号组绑定、分发 Key 授权模型。
- 不执行真实外部 API smoke。

## 上游来源

- `docs/requirements/REQ-20260522-011-default-provider-site-bootstrap.md`
- 用户问题：“我看有些站点还需要用户手动导入？为什么不一开始就默认是导入的呢？”

## 输入

- `ProviderCatalogLoader`
- `ProviderSiteRegistryService#importPreset`
- `DefaultResourceBootstrapService`
- `ProviderSiteRegistryServiceTests`
- `DefaultResourceBootstrapServiceTests`

## 输出

- 默认厂商 API 入口自动引导实现。
- 批量导入接口与 deprecated 跳过逻辑。
- 后端单元测试。
- 文档和任务索引更新。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/admin/application/DefaultResourceBootstrapService.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/ProviderSiteRegistryService.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/DefaultResourceBootstrapServiceTests.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/ProviderSiteRegistryServiceTests.java`
- `docs/index.md`
- `tasks/index.md`

## 依赖

- 现有 provider catalog preset。
- 现有 `upstream_site_profile.profile_code` 唯一约束。
- 现有模型刷新幂等修复。

## 风险

- 自动导入后 UI 中会出现更多未绑定凭证的 API 入口，需要通过绑定凭证数和健康状态清晰表达。
- 如果 marketplace preset code 变更，会生成新入口；该问题依赖 catalog 版本治理继续约束。

## 验收标准

- [x] `DefaultResourceBootstrapService` 会调用默认 provider preset 导入。
- [x] `ProviderSiteRegistryService` 只导入非 deprecated preset。
- [x] 已存在 preset 不被覆盖。
- [x] 默认导入刷新站点级能力快照但不生成真实模型能力。
- [x] 后端 targeted tests 通过。
- [x] 文档和任务状态回写完成。

## 测试边界

- 后端：`DefaultResourceBootstrapServiceTests` 与 `ProviderSiteRegistryServiceTests`。
- 不执行真实外部 API 调用。

## 关联文档

- `docs/requirements/REQ-20260522-011-default-provider-site-bootstrap.md`
- `docs/requirements/REQ-20260522-007-vendor-management-center.md`

## 关联任务

- `tasks/done/TASK-20260522-011-vendor-management-center.md`
- `tasks/done/TASK-20260522-008-model-refresh-idempotency.md`

## 当前状态

Done

## 实施记录

- 已在默认资源引导中加入 provider preset 默认导入，启动时自动生成非 deprecated 厂商 API 入口。
- 已新增 `ProviderSiteRegistryService#importDefaultPresets()`，复用单个 preset 导入逻辑，确保 profile code 幂等。
- 已把 preset 建档来源从 `MANUAL` 调整为 `PRESET`，前端显示“预设建档”。
- 已保留手动导入入口，用于 marketplace catalog 新增后人工重试；默认启动引导已经覆盖初始化场景。
- 已修正 preset model policy 去重逻辑，禁用过的 preset 策略不会被启动引导重新创建。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.DefaultResourceBootstrapServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests"`：通过。
- `bun run typecheck`：通过。
