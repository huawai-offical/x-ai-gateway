# REQ-20260523-010 MiMo 资源能力矩阵与已实现 OpenAI-style 入口对齐

状态：Done  
日期：2026-05-23  
上游来源：用户反馈小米 MiMo 入口能力矩阵仍把 audio/image/file/upload 等已推进资源入口显示为 blocked，并质疑“之前实现了很多 API，矩阵是否更新”。

## 背景

当前 MiMo 预设使用 `OPENAI_COMPATIBLE_GENERIC` 站点类型，入口能力矩阵中 audio/image/files/uploads 的公开路径已经存在，渲染层也显示为 `NATIVE`，但执行层被 `SiteCapabilityTruthService` 依据旧 `capability snapshot` 阻断，导致界面显示“当前站点声明不支持”。

这会把两类事实混在一起：

- gateway 已实现并可转发或编排的 OpenAI-style 功能性资源入口。
- 某个真实上游站点是否在官方文档、模型列表或真实 smoke 中证明了原生支持。

## 目标

- 让 MiMo 这类 OpenAI-compatible 站点的能力矩阵如实显示 gateway 已实现的功能性资源入口。
- 避免旧 capability snapshot 因默认值未刷新，继续把 `files/uploads` 等已开放 orchestration 面显示成 blocked。
- 保留非功能性 API 的排除边界，不把 admin、tuning、batch、eval 等管理面纳入。
- 保留真实上游失败的运行时反馈：如果 MiMo 上游对某个 passthrough 资源返回 401/404/unsupported，应由真实请求或 smoke 结果体现，不在矩阵里伪装成“没有实现”。

## 范围

包含：

- MiMo/OpenAI-compatible generic 的 audio/image/moderation passthrough 能力展示。
- MiMo/OpenAI-compatible generic 的 files/uploads gateway orchestration 能力展示。
- 凭证刷新模型后站点 snapshot 与最新 policy 的同步。
- Provider catalog 中 MiMo capability tags 与说明同步。
- 后端单元测试覆盖矩阵和 snapshot 行为。

不包含：

- 真实调用小米 MiMo billable audio/image 资源做 live smoke。
- 实现 admin、tuning、fine-tuning、eval、batch 等管理或训练类 API。
- 将 MiMo 官方未稳定声明的专有接口伪装为厂商 native API。

## 方案

1. 调整 OpenAI-compatible generic policy，使已实现的 OpenAI-style audio/images/moderation passthrough 在能力快照中可声明。
2. 保持 files/uploads 由 gateway orchestration 承接，并确保 MiMo preset refresh 会写入 `supports_files=true` 和 `supports_uploads=true`。
3. 将 SiteCapabilityTruth 的 OpenAI-compatible file/upload 阻断原因保留给真正未声明的站点，但不再让 MiMo 旧预设持续落在旧状态。
4. 更新文档和任务，标注“矩阵显示 gateway 可执行能力，不等于上游真实模型一定成功”。

## 风险

- 一些 OpenAI-compatible 站点虽然路径兼容，但真实上游不支持某个资源族；运行时仍可能返回上游错误。
- 旧数据库中的 snapshot 需要通过刷新模型或刷新站点能力写回最新 policy。
- UI 文案需要避免把 passthrough/orchestration 误读为官方原生能力。

## 验收标准

- MiMo/OpenAI-compatible generic 的 audio transcription/translation、image generation/edit/variation、files/uploads 在站点矩阵中不再因旧 snapshot 默认值显示为 blocked。
- `ExecutionSupportMatrixServiceTests`、`SiteCapabilityTruthServiceTests`、`ProviderSiteRegistryServiceTests`、`ProviderSiteAdminServiceTests` 相关回归通过。
- 文档说明矩阵语义与真实上游支持之间的差异。

## 关联任务

- [TASK-20260523-011 MiMo 资源能力矩阵与 OpenAI-compatible 实现对齐](../../tasks/done/TASK-20260523-011-mimo-resource-capability-matrix-alignment.md)

## 实现结果

- 已将 `OPENAI_COMPATIBLE_GENERIC` 的 policy 拆出独立分支，明确声明 gateway 已支持的 audio/images/moderation passthrough，以及 files/uploads orchestration 能力。
- 已更新 MiMo preset 的 capability tags 和说明，明确 audio/images/moderation 是 OpenAI-style passthrough，files/uploads 是 gateway-local orchestration，不伪装为 MiMo 官方原生 object store。
- 已补回归测试，覆盖 MiMo/openai-compatible snapshot 刷新后写入 `supports_audio/supports_images/supports_moderation/supports_files/supports_uploads`，以及管理矩阵 surfaces 不再 blocked。
- 已更新功能性 API 覆盖文档，补充矩阵语义和真实上游失败边界。

## 验证记录

通过：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests"
```

## 后续建议

- 运行中已有 MiMo 站点需要重新执行“刷新模型”或“刷新站点能力”，才能把数据库里的旧 snapshot 更新成最新 policy。
