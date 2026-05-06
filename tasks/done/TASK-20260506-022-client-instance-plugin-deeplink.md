# TASK-20260506-022 Client Instance 管理与插件/Deep Link 授权下发

状态：Done  
优先级：Medium  
来源：[REP-20260506 参考项目功能深度再复核](../../docs/reports/REP-20260506-reference-feature-depth-recheck.md)  
关联需求：[REQ-20260506-018 Client Instance 管理与插件/Deep Link 授权下发](../../docs/requirements/REQ-20260506-018-client-instance-plugin-deeplink.md)

## 背景

当前 onboarding pack 已输出 client family、client instance 和 workspace hint，但这些还只是请求 metadata。对照 `cockpit-tools-main` 的多实例管理和插件联动，以及 `cc-switch-main` 的 Deep Link 导入，`x-ai-gateway` 需要把云端 client instance 管理、一次性 secret 和插件/Deep Link 授权下发做成可运营能力。

## 目标

- 建立 client instance 注册、绑定、吊销和审计模型。
- 支持插件或 Deep Link 通过一次性授权获取云端接入配置。
- 将 client instance 维度接入 usage、trace、route policy 和 account pool。

## 范围

- Client instance 数据模型、生命周期和权限。
- 一次性 secret grant、过期、消费、吊销和审计。
- Deep Link schema 与插件 message schema。
- Admin/Portal 中的实例列表、接入状态、最近请求和撤销操作。

## 非目标

- 不要求用户安装本地 companion。
- 不读取、扫描或上传 workspace 内容。
- Deep Link 不携带完整长期 secret。

## 验收标准

- Client instance 可注册、查看、禁用和撤销。
- 一次性 secret grant 只能消费一次，过期后不可用。
- Trace/usage 可按 client family 和 instance 聚合。
- Deep Link 和插件 schema 有安全文档与测试。

## 实现记录

- 新增 `client_instance` 与 `client_instance_grant` 数据模型、repository 和 Liquibase 迁移。
- 新增 `/admin/client-instances` 管理 API，覆盖注册、查看、更新、禁用、撤销。
- 新增实例级插件/Deep Link 一次性授权发行、消费和撤销 API。
- 授权 token 只保存 hash；完整 key 只以密文保存在 grant 中；Deep Link 和 plugin message 不携带长期 secret。
- 支持从 `fullKey` 或既有 `secretExportGrantToken` 派生实例级一次性 grant。
- `request_log` 与 `usage_record` 新增 `client_family`、`client_instance`、`workspace_hint` 字段和索引。
- 新增 [client-instance-plugin-deeplink](../../docs/client-instance-plugin-deeplink.md) 文档，并更新 [client-onboarding-pack](../../docs/client-onboarding-pack.md)。

## 测试/验证

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ClientInstanceAdminServiceTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ClientInstanceAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.DistributedKeyAdminServiceTests"`
- `git diff --check`：仅有 Windows LF/CRLF 提示，无 whitespace error。

## 遗留问题

- 本轮未扩展复杂 Admin/Portal 前端列表与批量操作 UI；后端 API 和文档已可作为 UI 事实源。
- request/usage 字段和索引已落地；入口请求头到字段的完整自动写入可在后续任务继续接入。
