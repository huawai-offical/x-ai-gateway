# TASK-20260522-007-02 Model Policy 管理端、预览与冲突检测

## 任务类型

子任务

## 背景

第二阶段需要让策略可管理、可预览、可解释，避免只有运行时代码能理解模型映射和收缩结果。

## 目标

- 新增 Model Policy Admin CRUD。
- 新增有效模型预览接口。
- 新增冲突检测接口。
- Provider preset/catalog 可导入默认 policy。

## 非目标

- 不做完整前端页面重设计。
- 不接入线上 Notion/Linear。

## 上游来源

- `tasks/in-progress/TASK-20260522-007-model-policy-layered-resolution-parent.md`

## 输入

- `ModelPolicyRequest`
- `ModelPolicyPreviewRequest`
- provider catalog/preset policy 配置。

## 输出

- `ModelPolicyResponse`
- `ModelPolicyPreviewResponse`
- `ModelPolicyConflictResponse`

## 影响范围

- `admin/api`
- `admin/application`
- provider catalog loader/registry。

## 依赖

- 第一阶段 resolver。
- provider catalog 现有 preset loader。

## 风险

- 冲突检测过严会阻塞合法多上游 fallback。
- 预览接口需要脱敏，不能返回真实 key。

## 验收标准

- [x] CRUD 可用。
- [x] Preview 能展示最终候选和策略/评分解释。
- [x] Conflict detection 能识别重复映射、目标无候选、灰度权重非法、fallback 不可达。
- [x] preset 可导入默认策略。

## 测试边界

- `ModelPolicyAdminServiceTests`
- Provider catalog loader 相关测试。

## 关联文档

- `docs/requirements/REQ-20260522-005-model-policy-layered-resolution.md`

## 关联任务

- 父任务：`tasks/in-progress/TASK-20260522-007-model-policy-layered-resolution-parent.md`

## 当前状态

Done

## 实施结果

- 已新增 `/admin/model-policies` 管理端 API。
- 已新增 `ModelPolicyAdminService`、request/response/preview/conflict DTO。
- provider catalog loader 与 registry 已支持 `modelPolicies` 导入。

## 验证结果

- `ModelPolicyAdminServiceTests`：通过。
- `ProviderCatalogLoaderTests`、`ProviderSiteRegistryServiceTests`：通过。
