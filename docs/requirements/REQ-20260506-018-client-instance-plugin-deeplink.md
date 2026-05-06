# REQ-20260506-018 Client Instance 管理与插件/Deep Link 授权下发

状态：Done  
日期：2026-05-06  
关联任务：

- [TASK-20260506-022 Client Instance 管理与插件/Deep Link 授权下发](../../tasks/done/TASK-20260506-022-client-instance-plugin-deeplink.md)

## 背景

当前 onboarding pack 已经能输出 `X-AI-Gateway-Client-Family`、`X-AI-Gateway-Client-Instance` 和 `X-AI-Gateway-Workspace-Hint`，并且 `DistributedKey` 已有一次性 secret 导出授权。对照参考项目的多实例管理、插件联动和 Deep Link 导入能力，系统还缺少可运营的 client instance 生命周期、实例级一次性授权、授权消费审计和实例维度的 trace/usage 聚合事实源。

## 目标

- 建立 client instance 注册、查看、禁用、撤销和审计模型。
- 支持插件或 Deep Link 通过一次性授权获取云端接入配置。
- 确保一次性 grant 只能消费一次，过期和撤销后不可用。
- 将 client family、client instance、workspace hint 接入 request log 和 usage record，支撑后续 trace/usage 聚合。

## 范围

- 后端实体、repository、service、Admin API 和 Liquibase 迁移。
- 一次性 plugin/deep link authorization grant 的发行、消费、撤销和审计。
- onboarding pack 使用真实 client instance 值生成配置和 deep link schema。
- request log 与 usage record 持久化 client family、client instance、workspace hint。
- 单元测试覆盖生命周期、一次性消费、过期/撤销和配置不泄露长期 secret。

## 非目标

- 不要求用户安装本地 companion。
- 不读取、扫描或上传 workspace 内容。
- Deep Link 不携带完整长期 secret。
- 本轮不扩展复杂前端管理页；后端响应和文档先成为 UI 事实源。

## 方案

1. 将 `TASK-022` 移入 `in-progress`。
2. 新增 `ClientInstanceEntity` 与 `ClientInstanceGrantEntity`，关联 `DistributedKeyEntity`。
3. 新增 `ClientInstanceAdminService` 与 `/admin/client-instances` API，覆盖注册、列表、禁用、撤销、授权发行、授权消费。
4. 复用 `CredentialCryptoService` 对 grant token 做 fingerprint，不保存明文 token。
5. 扩展 `RequestLogEntity` 和 `UsageRecordEntity`，为 trace/usage 聚合保存 client family、client instance 和 workspace hint。
6. 更新 onboarding 文档，明确 plugin message 与 deep link schema。

## 风险

- Deep Link 如果携带长期 secret 会扩大泄露面，本轮只下发一次性 grant token 和 endpoint 元数据。
- client instance identifier 需要归一化，避免任意长字符串污染索引。
- 现有前端工作区已有大量改动，本轮优先落后端事实源和测试，避免扩大冲突面。

## 验收标准

- Client instance 可注册、查看、禁用和撤销。
- 一次性 secret grant 只能消费一次，过期后不可用，撤销后不可用。
- 授权消费返回配置但不在 deep link 中携带完整长期 secret。
- request log 与 usage record 可保存 client family、client instance 和 workspace hint。
- 文档、任务状态和测试结果完成回写。

## 实现结果

- 新增 `ClientInstanceEntity`、`ClientInstanceGrantEntity`，并通过 `db.changelog-0046-client-instance.yaml` 建表。
- 新增 `ClientInstanceRepository`、`ClientInstanceGrantRepository`。
- 新增 `ClientInstanceAdminService` 与 `/admin/client-instances` API，覆盖注册、列表、详情、更新、禁用、撤销、授权发行、授权消费和授权撤销。
- 插件/Deep Link grant 只保存 token hash，完整 DistributedKey 只以密文写入 `client_instance_grant.full_key_ciphertext`。
- 授权发行支持 `fullKey` 或既有 `secretExportGrantToken` 两种来源；后者会先消费源 token，再派生 client instance 专属一次性 grant。
- 授权消费返回包含真实 key 的客户端配置，并立即写入 `consumedAt`，二次消费会被拒绝。
- 撤销 client instance 会同步撤销未消费 grant。
- `request_log` 与 `usage_record` 增加 `client_family`、`client_instance`、`workspace_hint` 字段和索引，为后续 trace/usage 聚合提供事实源。
- 新增 [client-instance-plugin-deeplink](../client-instance-plugin-deeplink.md) 文档，并在 [client-onboarding-pack](../client-onboarding-pack.md) 中补充链接。

## 测试/验证

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ClientInstanceAdminServiceTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ClientInstanceAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.DistributedKeyAdminServiceTests"`
- `git diff --check`：仅有 Windows LF/CRLF 提示，无 whitespace error。

## 遗留问题

- 本轮完成后端事实源、迁移、授权下发和测试；复杂 Admin/Portal 前端列表与批量操作 UI 未扩展。
- request/usage 已具备实例维度字段和索引；真实入口请求头写入这些字段可作为后续小任务继续增强。
