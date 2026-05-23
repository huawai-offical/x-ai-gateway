# REP-20260523 凭证与厂商领域模型关系梳理

状态：Done  
日期：2026-05-23  
关联需求：[REQ-20260523-011](../requirements/REQ-20260523-011-credential-provider-domain-model-clarification.md)

## 一句话结论

当前凭证体系复杂的根因，是“用户主对象”和“系统内部治理对象”混在同一层展示。后续应把用户主路径收敛为：厂商、协议入口、账号分组、凭证；把站点档案、预设、能力快照、模型策略、Distributed Key 放回支撑层或高级调试层。

## 用户主对象

| 对象 | 用户问题 | 说明 | 是否主入口 |
| --- | --- | --- | --- |
| 厂商 Vendor | 这是谁？ | Xiaomi MiMo、Gemini、Anthropic、OpenAI 等品牌或服务商。 | 是 |
| 协议入口 Protocol Endpoint | 怎么调用？ | 同一厂商可有多个入口，例如 MiMo OpenAI-compatible 与 MiMo Anthropic-compatible。 | 是 |
| 账号分组 Account Group | 哪些 key 归一个池子？ | 用于生产/测试/备用/个人/团队/区域等隔离，也承载路由池、额度池、健康治理语义。 | 是 |
| 凭证 Credential | 具体用哪个密钥？ | API key、OAuth token、服务账号等真实上游认证材料。 | 是 |

## 系统支撑对象

| 对象 | 职责 | 用户可见性建议 |
| --- | --- | --- |
| Provider Preset | 厂商模板、默认入口、默认策略、默认文案。 | 只在“导入/恢复默认厂商”中出现，不作为常驻表格。 |
| Provider Site | 运行档案，保存 siteKind、auth、path、error schema 等。 | 合并进厂商详情，高级调试可展开。 |
| Capability Snapshot | 记录当前入口或凭证刷新得到的能力事实。 | 在能力矩阵展示结果，不单独成为管理对象。 |
| Model Policy | 模型映射、fallback、allow/deny、请求/响应覆盖。 | 放到“模型策略”子页或高级设置，不混在凭证列表。 |
| Distributed Key | 对外访问凭据，绑定账号分组或凭证池。 | 属于客户端接入/路由配置，不属于厂商目录主线。 |

## 推荐关系

```text
Vendor
  1 ── n Protocol Endpoint
Protocol Endpoint
  1 ── n Credential
Account Group
  1 ── n Credential
Distributed Key
  n ── n Account Group 或 Credential Binding
```

注意：从产品视角看，账号分组可以出现在厂商和协议入口上下文里；但从当前实现看，账号分组表本身还没有 `vendor_id` 或 `protocol_endpoint_id`，真实交集由凭证承接。也就是说，凭证同时绑定 `group_id` 和 `protocol_endpoint_id`，系统可以从凭证反推出“某账号分组当前覆盖哪些协议入口”。

因此本轮建议不是立即把账号分组物理移动到协议入口下面，而是先把 UI 和 API 展示改成用户能理解的关系：

```text
Vendor
  ├─ Protocol Endpoint
  │    └─ Credentials bound to this endpoint
  ├─ Account Groups
  │    └─ Credentials in this group
  └─ Matrix / Policies / Diagnostics
```

对于用户操作，最顺的填写路径仍应是：

```text
厂商 -> 协议入口 -> 账号分组 -> 凭证
```

这样既符合当前数据模型，也能表达同一厂商不同协议入口的鉴权、baseUrl、路径和能力差异。例如：

```text
Xiaomi MiMo
  ├─ OpenAI-compatible Endpoint
  │    ├─ MiMo OpenAI 生产组
  │    │    ├─ Key 1
  │    │    └─ Key 2
  │    └─ MiMo OpenAI 测试组
  └─ Anthropic-compatible Endpoint
       └─ MiMo Anthropic 生产组
            └─ Key 3
```

## 能力与刷新语义

能力不应只挂在“厂商”这一层，因为同一厂商不同协议入口的能力不同。建议语义：

- 厂商能力：聚合展示，用于目录卡片和筛选。
- 协议入口能力：主要事实源，用于入口能力矩阵。
- 账号分组能力：由组内凭证聚合，用于路由选择。
- 凭证能力：来自模型刷新、真实 smoke、最近错误和冷却状态。

刷新动作也应拆清楚：

| 动作 | 作用对象 | 结果 |
| --- | --- | --- |
| 刷新厂商预设 | Vendor / Protocol Endpoint | 恢复或更新默认入口、说明、默认策略。 |
| 刷新站点能力 | Protocol Endpoint | 更新 capability snapshot，不一定访问真实上游。 |
| 刷新模型 | Credential | 访问真实上游模型列表，写回模型能力和凭证状态。 |
| 真实 smoke | Credential 或 Account Group | 验证某类 API 是否真的可调用，写入验证证据。 |

## 账号分组分类

账号分组不是简单的“凭证文件夹”。它应该承担路由池、授权池、额度池和运行治理池的语义。后续 UI 需要让用户知道自己在创建哪一类分组。

| 分组类型 | 解决的问题 | 典型字段/行为 | 示例 |
| --- | --- | --- | --- |
| 默认分组 | 历史凭证和新手路径不需要先理解治理概念。 | 系统自动创建；可作为未分类凭证兜底。 | 默认 OpenAI-compatible 分组 |
| 环境分组 | 生产、测试、预发隔离，避免试用 key 混入生产。 | active、description、支持模型、支持协议。 | MiMo 生产组、MiMo 测试组 |
| 协议入口分组 | 同一厂商多个入口时按调用协议隔离。 | 通过组内凭证绑定的 protocolEndpointId 聚合入口范围。 | MiMo OpenAI-compatible 组 |
| 成本/额度分组 | 按客户、预算、团队或额度等级拆池。 | 支持模型、Model Policy、后续 quota policy。 | 低成本组、高额度组 |
| 健康/备用分组 | 主备切换、故障绕行、冷却状态隔离。 | active、priority、Distributed Key 绑定优先级。 | 主力组、备用组 |
| 客户端授权分组 | 控制某个 Distributed Key 能访问哪些上游池。 | `distributed_key_account_group_binding`。 | 客户 A 可用组 |

当前 `UpstreamAccountGroupEntity` 已有 `providerType`、`supportedModels`、`supportedProtocols`、`allowedClientFamilies`、`active` 和 `description`。缺口是它还缺少显式的厂商/协议入口范围字段，导致前端只能从凭证反推分组归属。短期应在聚合 API 中反推出分组覆盖的厂商与入口；中期再评估是否补充显式绑定表。

## 当前实现映射

| 领域概念 | 当前实体/API | 当前状态 | 建议 |
| --- | --- | --- | --- |
| 厂商 Vendor | `ProviderSiteProfileEntity` / catalog preset 中的 provider code | 已存在，但命名偏“站点档案”。 | 产品文案统一叫厂商，Provider Site 退到高级调试。 |
| 协议入口 Protocol Endpoint | `ProviderProtocolEndpointEntity` | 已存在，凭证可绑定 `protocol_endpoint_id`。 | 作为厂商详情下的一等 Tab。 |
| 账号分组 Account Group | `UpstreamAccountGroupEntity` | 已存在，凭证可绑定 `group_id`，Distributed Key 可绑定分组。 | UI 显式展示分组类型、入口覆盖和凭证数。 |
| 凭证 Credential | `UpstreamCredentialEntity` | 已同时绑定 `group_id` 与 `protocol_endpoint_id`。 | 创建/编辑路径按厂商、入口、分组、key 收敛。 |
| 对外 Key Distributed Key | `DistributedKeyEntity` + `distributed_key_account_group_binding` | 已通过账号分组展开运行时凭证池。 | 放在客户端接入/授权配置，不混入厂商目录主表。 |
| 能力快照 Capability Snapshot | Site capability snapshot + model catalog | 已存在，但站点级、入口级、凭证级语义混合。 | 拆出来源和刷新证据。 |
| 模型策略 Model Policy | `ModelPolicyScopeType` | 已支持 VENDOR、SITE_PROFILE、ACCOUNT_GROUP、CREDENTIAL 等层级。 | 从主路径隐藏到策略/高级页。 |

## UI 收敛建议

### 厂商目录

- 一行一个厂商。
- 展开后展示协议入口、账号分组、凭证数量和能力摘要。
- 不再单独展示“厂商聚合”和“预设导入”两个并列表。

### 厂商详情

- Tab 1：协议入口
- Tab 2：账号分组
- Tab 3：凭证
- Tab 4：能力矩阵
- Tab 5：模型策略
- 高级调试：站点档案、snapshot 原始值、policy 来源、最近刷新证据。

### 凭证列表

- 默认列：凭证名、厂商、协议入口、账号分组、状态、模型数、最近刷新、最近错误。
- 创建凭证时按“厂商 -> 协议入口 -> 账号分组 -> key”填写。
- 若厂商只有一个入口，可自动选择默认入口。

## 后端边界建议

- 不急于删除 `ProviderSite`，可以先把它定位为 `Protocol Endpoint` 背后的运行档案。
- `ProviderProtocolEndpoint` 应成为用户可理解的协议入口对象。
- `AccountGroup` 应明确可挂在厂商或协议入口上下文中，最终更推荐挂协议入口。
- `Credential` 必须同时可追溯到账号分组和协议入口。
- `CapabilitySnapshot` 后续应支持入口级与凭证级证据来源，避免“厂商聚合能力”和“具体 key 能力”互相污染。

## 后续任务拆分

| 任务 | 目标 |
| --- | --- |
| TASK-20260523-013 | 厂商目录 UI 收敛为 Vendor -> Endpoint -> Group -> Credential 主路径。 |
| TASK-20260523-014 | 后端领域模型命名与 API 响应重塑，减少 Provider Site / Preset 暴露。 |
| TASK-20260523-015 | 能力快照与刷新语义重构为入口级、凭证级和组级聚合。 |
| TASK-20260523-016 | 账号分组分类、入口覆盖反推与 Distributed Key 授权展示。 |
