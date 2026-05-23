# TASK-20260523-013 厂商目录 UI 收敛为 Vendor -> Endpoint -> Group -> Credential

状态：In Progress  
优先级：High  
上游来源：[REP-20260523](../../docs/reports/REP-20260523-credential-provider-domain-model.md)

## 目标

将厂商管理界面重构为以厂商为第一层、协议入口为第二层、账号分组为第三层、凭证为第四层的用户主路径。

## 范围

- 合并厂商聚合与预设导入的常驻表格。
- 厂商详情内展示协议入口、账号分组、凭证和能力摘要。
- 凭证创建流程按厂商、协议入口、账号分组、key 引导。

## 非目标

- 不改后端 schema。
- 不实现真实 smoke。

## 验收标准

- 用户不需要理解 Provider Site / Preset / Snapshot 才能完成 key 管理。
- MiMo 多协议入口能在一个厂商详情中清晰展示。

## 本轮实施切片

2026-05-23 已开始第一切片：厂商目录页接入新的 `/admin/provider-sites/domain-catalog` 聚合事实源，目录表格优先展示协议入口、账号分组、凭证和 Distributed Key 绑定摘要。该切片保留原有预设导入、自定义入口、刷新和管理动作，不直接重写厂商详情页。

### 已完成

- 厂商目录统计增加协议入口、账号分组、绑定凭证口径。
- 厂商目录表格增加账号分组摘要列。
- 厂商目录规模信息增加 Distributed Key 绑定数。
- 目录搜索纳入账号分组、协议入口覆盖和 Distributed Key 绑定名称。

### 下一步

- 厂商详情页按“协议入口、账号分组、凭证、能力矩阵、模型策略”重新组织。
- 凭证创建/编辑流程改为“厂商 -> 协议入口 -> 账号分组 -> key”。
- 移除页面中仍面向 Provider Site / Preset 的内部化文案。
