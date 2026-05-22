# REQ-20260522-011 默认厂商 API 入口引导

## 背景

厂商管理中心已经恢复为“厂商 -> API 入口 -> 上游凭证 -> 账号组 -> 分发 Key”的主配置路径，但当前 provider catalog 中的厂商预设只作为模板展示，需要用户手动点击导入后才会生成真实 `upstream_site_profile`。在该架构下，用户进入系统后仍看到部分厂商需要手动导入，会造成“厂商层没有默认初始化完成”的体验断点。

## 目标

- 应用启动默认幂等导入 provider catalog 中的非 deprecated 厂商预设。
- 默认生成 API 入口和站点级能力快照，便于 UI 直接展示厂商、入口策略、conversation profile 和能力矩阵。
- 保持凭证、账号组和分发 Key 的授权关系不变；没有绑定凭证的 API 入口不参与真实路由。
- 保持手动导入能力，作为重试或新 marketplace catalog preset 落库入口。

## 范围

- `DefaultResourceBootstrapService` 默认资源引导。
- `ProviderSiteRegistryService` preset 批量导入能力。
- 单元测试覆盖默认引导调用和 deprecated preset 跳过。
- 文档与任务索引回写。

## 非目标

- 不自动创建上游凭证，不把 API key 写入仓库或默认配置。
- 不自动调用真实外部厂商接口做模型发现。
- 不删除管理端手动导入入口；该入口保留用于 marketplace 更新后的人工重试。
- 不新增独立 vendor 表。

## 验收标准

- 应用启动后，catalog 中非 deprecated 的 provider preset 会自动生成对应 API 入口。
- 默认导入是幂等的，重复启动不会覆盖用户已修改的 API 入口配置。
- 默认导入会刷新站点级能力快照，但空模型列表不会清空已有模型能力。
- deprecated preset 不自动导入。
- 后端测试覆盖默认引导和批量导入规则。

## 风险

- 默认导入过多厂商会让 UI 首屏信息增加；当前用厂商管理中心承接，后续可增加筛选和折叠。
- marketplace catalog 更新后会在下次启动自动落库新增非 deprecated preset，需要确保 preset code 稳定。
- 默认导入只代表“入口配置可见”，不代表已有凭证可用；UI 需要继续通过绑定凭证数区分是否可实际路由。

## 当前状态

Done

## 实施结果

- `DefaultResourceBootstrapService` 已在默认资源引导阶段调用 provider preset 默认导入。
- `ProviderSiteRegistryService` 新增 `importDefaultPresets()`，默认导入 catalog 中非 deprecated 的 preset。
- 默认导入复用既有 `profileCode` 幂等查找：已存在 API 入口时不覆盖用户配置。
- 默认导入会刷新站点级 capability snapshot；无发现模型时不写入或清空模型能力。
- preset 导入的 API 入口来源改为 `SiteProfileSource.PRESET`，前端展示为“预设建档”。
- preset 模型策略导入改为检查包含禁用项在内的既有 preset policy，避免用户禁用后下次启动又被自动补回。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.DefaultResourceBootstrapServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests"`：通过。
- `bun run typecheck`：通过。
