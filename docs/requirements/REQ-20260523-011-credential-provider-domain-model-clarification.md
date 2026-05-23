# REQ-20260523-011 凭证与厂商领域模型梳理

状态：Done  
日期：2026-05-23  
上游来源：用户反馈当前凭证体系设计非常复杂，并进一步指出不止厂商、协议入口、凭证三件事，还必须关注账号分组，需要先梳理这些概念之间的关系。

## 背景

当前系统已经具备 Provider Preset、Provider Site、Provider Protocol Endpoint、Account Group、Credential、Distributed Key、Capability Snapshot、Model Policy 等对象。它们在后端治理上都有存在价值，但在管理界面里同时暴露后，用户会难以判断：

- 哪些是自己需要直接管理的业务对象。
- 哪些只是系统内部支撑对象。
- MiMo 这类一个厂商多个协议入口的情况，应该在哪里挂账号分组和凭证。
- 刷新模型、能力快照、模型策略和路由选择之间到底是什么关系。

## 目标

- 梳理凭证与厂商体系的核心领域对象和基数关系。
- 明确用户可见对象与内部支撑对象的边界。
- 明确账号分组在厂商、协议入口、凭证、Distributed Key 之间的位置。
- 产出后续 UI 与后端收敛任务，不在本轮直接改代码。

## 非目标

- 不在本轮重构数据库 schema。
- 不在本轮改造前端页面。
- 不删除现有 Provider Site、Protocol Endpoint、Capability Snapshot、Model Policy 等实现。
- 不改变现有运行时路由行为。

## 关键结论

用户主对象应收敛为四类：

1. 厂商 Vendor：回答“这是谁”，例如 Xiaomi MiMo、Google Gemini、Anthropic。
2. 协议入口 Protocol Endpoint：回答“怎么调用”，例如 OpenAI-compatible、Anthropic-compatible、Google native。
3. 账号分组 Account Group：回答“这些账号或 key 归哪个池子”，用于环境、额度、路由池和治理隔离。
4. 凭证 Credential：回答“具体用哪个 key/token 发请求”。

内部支撑对象不应作为用户主入口：

- Provider Preset：厂商模板和初始化来源。
- Provider Site：厂商运行档案，可在高级调试中保留。
- Capability Snapshot：能力快照，用于矩阵和路由判断。
- Model Policy：模型映射、fallback、allow/deny 策略。
- Distributed Key：对外发放的访问 key 和路由绑定，不应混同于上游凭证。

## 建议领域关系

```text
Vendor 厂商
  └─ Protocol Endpoint 协议入口
       └─ Credential 凭证

Account Group 账号分组
  └─ Credential 凭证

Distributed Key
  └─ Account Group Binding
```

当前实现里，账号分组不是直接挂在协议入口表下面，而是通过凭证形成交集：凭证同时绑定 `group_id` 和 `protocol_endpoint_id`。因此更准确的产品关系是：

- 厂商定义品牌边界。
- 协议入口定义调用方式、Base URL、鉴权策略和路径策略。
- 账号分组定义上游账号或 key 的治理池、路由池和授权池。
- 凭证同时属于一个账号分组，并绑定一个协议入口。
- Distributed Key 不直接等于上游凭证，而是通过账号分组绑定拿到可用凭证池。

账号分组需要作为一等对象展示，至少覆盖这些类型：

| 分组类型 | 用途 | 示例 |
| --- | --- | --- |
| 默认分组 | 历史凭证和新手路径兜底。 | 默认 OpenAI-compatible 分组 |
| 环境分组 | 隔离生产、测试、预发或个人试用。 | MiMo 生产组、MiMo 测试组 |
| 协议入口分组 | 对同一厂商不同协议入口做路由隔离。 | MiMo OpenAI 入口组、MiMo Anthropic 入口组 |
| 成本/额度分组 | 按预算、额度、团队或客户隔离。 | 高额度组、低成本组 |
| 健康/备用分组 | 主备切换、冷却隔离、故障绕行。 | 主力组、备用组 |
| 客户端授权分组 | 控制 Distributed Key 能访问哪些上游池。 | Portal 客户 A 可用组 |

同时存在跨层策略：

```text
Capability Snapshot 能力快照
  - 首选挂在协议入口
  - 可由凭证刷新模型/真实 smoke 更新
  - 可聚合为厂商视图

Model Policy 模型策略
  - 默认来源：厂商 preset
  - 可覆盖层级：凭证 > 账号分组 > 协议入口 > 厂商默认

Distributed Key
  - 绑定一个或多个账号分组/凭证池
  - 面向客户端访问，不属于厂商目录本体
```

## 验收标准

- 形成可供后续实现引用的关系报告。
- 拆出 UI 收敛、后端模型边界、数据迁移/兼容三个后续任务。
- 文档明确本轮只梳理，不改运行逻辑。

## 关联报告

- [REP-20260523 凭证与厂商领域模型关系梳理](../reports/REP-20260523-credential-provider-domain-model.md)

## 关联任务

- [TASK-20260523-012 凭证与厂商领域模型梳理父任务](../../tasks/done/TASK-20260523-012-credential-provider-domain-model-clarification-parent.md)
