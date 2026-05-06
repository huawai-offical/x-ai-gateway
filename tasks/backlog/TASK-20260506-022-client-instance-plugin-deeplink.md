# TASK-20260506-022 Client Instance 管理与插件/Deep Link 授权下发

状态：Backlog  
优先级：Medium  
来源：[REP-20260506 参考项目功能深度再复核](../../docs/reports/REP-20260506-reference-feature-depth-recheck.md)  
关联需求：[REQ-20260506-012 参考项目功能深度复核与任务再生成](../../docs/requirements/REQ-20260506-012-reference-depth-recheck-task-generation.md)

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

待处理。

## 测试/验证

待处理。

## 遗留问题

待处理。
