# TASK-20260522-021 分发 Key 鉴权 active 账号组守卫

## 任务类型

子任务

## 背景

来源：`docs/requirements/REQ-20260522-020-distributed-key-auth-active-group-guard.md`

运行时路由和账号选择已经收敛到启用账号组绑定，但鉴权缓存仍只校验 active Key 与 secret hash。需要把账号组启用绑定校验前移到鉴权层，避免资源类入口或缓存 TTL 窗口绕过停用账号组。

## 目标

- Key 鉴权要求存在启用账号组绑定。
- auth snapshot cache hit 时重新确认当前启用账号组绑定数。
- auth snapshot 记录加载时的启用账号组绑定数量。
- 补充后端单元测试。

## 非目标

- 不主动删除 Redis auth cache。
- 不改 secret hash 算法。
- 不执行真实厂商调用。
- 不改前端布局。

## 上游来源

- `docs/requirements/REQ-20260522-020-distributed-key-auth-active-group-guard.md`
- `tasks/done/TASK-20260522-019-distributed-key-account-group-runtime-expansion.md`
- `tasks/done/TASK-20260522-020-account-selection-active-group-runtime-alignment.md`

## 输入

- `DistributedKeyAuthenticationService`
- `DistributedKeyAuthSnapshot`
- `DistributedKeyAccountGroupBindingRepository`

## 输出

- 鉴权层 active 账号组绑定守卫。
- auth snapshot 扩展字段。
- 后端单元测试覆盖 cache miss 与 cache hit。
- 文档与任务索引回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/auth/DistributedKeyAuthenticationService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/auth/DistributedKeyAuthSnapshot.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/auth/DistributedKeyAuthenticationServiceTests.java`
- `docs/index.md`
- `tasks/index.md`

## 依赖

- `DistributedKeyAccountGroupBindingRepository.countByDistributedKey_IdAndActiveTrueAndGroup_ActiveTrue` 已存在。
- Key 启用管理端已经要求绑定启用账号组。

## 风险

- cache hit 增加一次 count 查询。
- 历史未绑定账号组的 active Key 会被拒绝，这是设计收敛后的期望行为。

## 验收标准

- [x] cache miss 没有启用账号组绑定时拒绝鉴权。
- [x] cache hit 当前没有启用账号组绑定时拒绝鉴权。
- [x] cache hit 仍校验 secret hash。
- [x] snapshot 写入启用账号组绑定数量。
- [x] 后端定向测试与编译通过。

## 测试边界

- 后端：`DistributedKeyAuthenticationServiceTests`。
- 编译：`.\gradlew.bat compileJava compileTestJava`。
- 不执行真实外部 API。

## 关联文档

- `docs/requirements/REQ-20260522-020-distributed-key-auth-active-group-guard.md`

## 关联任务

- `tasks/done/TASK-20260522-019-distributed-key-account-group-runtime-expansion.md`
- `tasks/done/TASK-20260522-020-account-selection-active-group-runtime-alignment.md`

## 当前状态

Done

## 实现结果

- `DistributedKeyAuthenticationService` cache miss 加载 snapshot 前检查启用账号组绑定数量。
- auth cache hit 后重新 count 当前启用账号组绑定，账号组停用后不会继续放行。
- `DistributedKeyAuthSnapshot` 增加 `activeAccountGroupBindingCount`。
- 补充 `DistributedKeyAuthenticationServiceTests` 与 `RedisAuthCacheStoreTests`。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.auth.RedisAuthCacheStoreTests" --tests "com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionServiceTests"`

## 遗留边界

- 不主动删除 Redis auth cache。
- 不改变 secret hash。
- 不执行真实外部 API。
