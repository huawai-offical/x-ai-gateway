# TASK-20260521-006-01 冗余 Admin 接口审计与清理

## 任务类型

子任务 / 并行实施

## 背景

父任务：`tasks/in-progress/TASK-20260521-006-redundant-api-eradication-and-baseline-refresh.md`

这部分聚焦 `admin/api` 与其直接绑定的 service/test/docs 清理，优先处理只服务于已下线控制台能力、或与当前主线重复的 Admin API。

## 目标

- 识别并删除冗余 Admin API。
- 同步清理相关 application/service、DTO、tests 与 docs/openapi 残留。

## 当前候选

- `CapabilityMatrixAdminController`
- `ProviderReferenceGapAdminController`
- `NativeCompatibilityAdminController`
- `CostRoutingAdminController`
- `ProviderSiteAdminController` 中只服务于旧控制台页面的附属入口
- `AccountAdminController` 中仅服务于旧官方账号运行态、且已不再被保留主路径使用的附属入口

## 本轮结果

- 已删除：
  - `CapabilityMatrixAdminController`
  - `ProviderReferenceGapAdminController`
  - `NativeCompatibilityAdminController`
  - `CostRoutingAdminController`
- 已收口：
  - `ProviderSiteAdminController` 仅保留 `GET /admin/provider-sites` 与 `GET /admin/provider-sites/{id}/capabilities`
  - `AccountAdminController` 删除 `list/refresh/network/export/programming-identity/official-quota` 等旧附属入口，仅保留当前账号分组、导入、冻结、runtime-reset、quota-refresh 与 smoke 主路径
- 已同步：
  - 删除 `provider reference gap` / `native compatibility` / `provider dossier` 的无入口 service、DTO 与对应测试
  - 修正 Google / Anthropic native namespace 的提示文案，改为指向 `/public/docs/compatibility`

## 当前状态

进行中（本轮清理已完成，待与父任务一起归档）
