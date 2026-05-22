# TASK-20260522-020 账号类选择 active 账号组运行时对齐

## 任务类型

子任务

## 背景

来源：`docs/requirements/REQ-20260522-019-account-selection-active-group-runtime-alignment.md`

分发 Key 通过账号组展开 API Key 凭证候选已经改为只认可启用账号组，但账号类运行时选择仍可能通过 active 绑定行访问停用账号组内的账号。为了让“停用账号组”在 API Key 和账号类两条运行时路径上语义一致，需要收紧 `AccountSelectionService` 与相关策略上下文。

## 目标

- 账号类选择排除停用账号组。
- sticky account 不能绕过停用账号组。
- 账号健康绑定检查与选择口径一致。
- Model Policy 账号组上下文排除停用账号组。
- 补充后端单元测试。

## 非目标

- 不新增账号组级权重。
- 不调整账号选择负载均衡算法。
- 不执行真实厂商调用。
- 不改前端布局。

## 上游来源

- `docs/requirements/REQ-20260522-019-account-selection-active-group-runtime-alignment.md`
- `tasks/done/TASK-20260522-019-distributed-key-account-group-runtime-expansion.md`

## 输入

- `AccountSelectionService`
- `ModelPolicyResolver`
- `DistributedKeyAccountGroupBindingRepository`
- `UpstreamAccountRepository`

## 输出

- 账号类运行时选择 active 账号组口径。
- sticky account active 账号组归属校验。
- 后端测试覆盖停用账号组与 sticky 绕过防护。
- 文档与任务索引回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/account/AccountSelectionService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/model/ModelPolicyResolver.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/account/AccountSelectionServiceTests.java`
- `docs/index.md`
- `tasks/index.md`

## 依赖

- `TASK-20260522-019` 已完成分发 Key API Key 候选 active 账号组口径。
- 现有账号组绑定 repository 可读取 provider type 下的 active 绑定。

## 风险

- 没有账号组绑定时，`hasHealthyAccountBinding` 当前语义是 true，本轮不改变。
- Redis sticky key 不主动删除，只在选择时失效。

## 验收标准

- [x] 停用账号组不会被 `resolveActiveAccount` 选中。
- [x] 停用账号组不会让 `hasHealthyAccountBinding` 返回 true。
- [x] sticky account 不属于当前 active 账号组绑定时不会复用。
- [x] Model Policy 账号组上下文不包含停用账号组。
- [x] 后端定向测试与编译通过。

## 测试边界

- 后端：`AccountSelectionServiceTests`、`ModelPolicyResolverTests`。
- 编译：`.\gradlew.bat compileJava compileTestJava`。
- 不执行真实外部 API。

## 关联文档

- `docs/requirements/REQ-20260522-019-account-selection-active-group-runtime-alignment.md`

## 关联任务

- `tasks/done/TASK-20260522-019-distributed-key-account-group-runtime-expansion.md`

## 当前状态

Done

## 实现结果

- `AccountSelectionService` 账号选择只遍历 active group bindings，并排除停用账号组。
- `hasHealthyAccountBinding` 保留“无绑定返回 true”的历史语义，同时对“有绑定但全部账号组停用”返回 false。
- sticky account 会校验账号仍属于当前 active 账号组绑定，并重新检查客户端家族、治理策略与网络健康。
- `ModelPolicyResolver` 账号组上下文过滤停用账号组，避免停用组继续参与策略匹配。
- 补充 `AccountSelectionServiceTests` 覆盖停用账号组、健康绑定检查和 sticky 绕过防护。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyResolverTests" --tests "com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests"`

## 遗留边界

- 不主动删除 Redis sticky key。
- 不新增账号组级 weight。
- 不执行真实外部厂商 API 调用。
