# TASK-20260523-016 账号分组分类、入口覆盖反推与 Distributed Key 授权展示

状态：Backlog  
优先级：High  
父任务：[TASK-20260523-012](../done/TASK-20260523-012-credential-provider-domain-model-clarification-parent.md)  
上游来源：[REP-20260523](../../docs/reports/REP-20260523-credential-provider-domain-model.md)

## 背景

用户指出凭证体系不能只看厂商、协议入口和凭证，还必须明确“有哪些账号分组”。当前 `UpstreamAccountGroupEntity` 已承载 provider 类型、模型、协议、客户端族和 active 状态，但没有显式厂商或协议入口绑定字段；系统实际通过凭证同时绑定 `group_id` 与 `protocol_endpoint_id` 来形成分组与入口的交集。

## 目标

- 在管理端聚合视图中展示账号分组分类、覆盖入口、凭证数量、运行状态和 Distributed Key 授权情况。
- 让用户能看出每个账号分组属于默认、环境、协议入口、成本/额度、健康/备用或客户端授权哪类用途。
- 短期通过组内凭证反推账号分组覆盖的厂商和协议入口；中期评估是否需要显式绑定表。

## 非目标

- 不在本任务中立即做破坏性 schema 迁移。
- 不改变 Distributed Key 现有运行时展开逻辑。
- 不把账号分组简化为凭证标签；它仍然是路由、授权和治理池。

## 输入

- `UpstreamAccountGroupEntity`
- `UpstreamCredentialEntity.groupId`
- `UpstreamCredentialEntity.protocolEndpointId`
- `ProviderProtocolEndpointEntity`
- `DistributedKeyAccountGroupBindingEntity`
- [REP-20260523 凭证与厂商领域模型关系梳理](../../docs/reports/REP-20260523-credential-provider-domain-model.md)

## 输出

- 后端账号分组聚合字段设计：分组类型、覆盖厂商、覆盖协议入口、凭证数、绑定 Distributed Key 数、状态摘要。
- 前端账号分组展示方案：厂商详情内和账号分组详情内都能看到同一套关系。
- 是否需要新增显式 `account_group_protocol_endpoint_binding` 或同类绑定表的决策记录。

## 影响范围

- 后端 Admin 账号分组查询与厂商详情聚合 API。
- 前端厂商详情、账号分组详情、凭证创建/编辑流程。
- 文档和任务索引。

## 依赖

- TASK-20260523-013 厂商目录 UI 收敛为 Vendor -> Endpoint -> Group -> Credential。
- TASK-20260523-014 厂商领域 API 命名与对象边界收敛。

## 风险

- 如果不区分“当前凭证反推范围”和“未来显式绑定范围”，UI 可能给用户造成账号分组已经严格隶属于某个协议入口的误解。
- Distributed Key 绑定账号分组已经参与鉴权和路由，展示层修改时不能弱化 active 分组守卫。

## 验收标准

- 账号分组列表能清楚展示每个分组覆盖哪些厂商和协议入口。
- 厂商详情能按协议入口看到相关分组，也能按分组看到相关凭证。
- Distributed Key 详情或授权区能显示绑定了哪些账号分组，以及这些分组最终会展开到哪些协议入口和凭证。
- 文案明确“覆盖入口”来自当前凭证反推，直到显式绑定表落地。

## 测试边界

- 后端聚合 API 单元测试覆盖无凭证分组、单入口分组、多入口分组、停用分组和 Distributed Key 绑定分组。
- 前端交互测试覆盖厂商详情与账号分组详情中的关系一致性。

## 当前状态

待排期。本任务先作为账号分组专项，避免后续 UI 收敛时只处理厂商、入口和凭证而漏掉账号分组。
