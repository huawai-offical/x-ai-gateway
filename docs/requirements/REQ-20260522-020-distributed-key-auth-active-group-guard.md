# REQ-20260522-020 分发 Key 鉴权 active 账号组守卫

## 背景

分发 Key 的运行时路由查询和账号类选择已经改为只认可“绑定 active 且账号组 active”的账号组绑定。但 `DistributedKeyAuthenticationService` 当前只按 Key active 与 secret hash 做鉴权，并会使用 Redis auth snapshot 缓存。对于只依赖 token 鉴权的资源类入口，或者账号组停用后的缓存 TTL 窗口，active Key 仍可能通过鉴权，直到后续业务层再失败。需要把“必须存在启用账号组绑定”的守卫前移到鉴权层，并让缓存命中也能感知账号组停用。

## 目标

- `DistributedKeyAuthenticationService` 加载 Key snapshot 时要求存在启用账号组绑定。
- 缓存命中后重新确认当前 Key 仍存在启用账号组绑定，避免账号组停用后 TTL 内继续放行。
- auth snapshot 记录加载时的启用账号组绑定数量，便于审计和测试。
- 补充单元测试覆盖 cache miss、cache hit、无启用账号组绑定拒绝。

## 范围

- `DistributedKeyAuthenticationService`
- `DistributedKeyAuthSnapshot`
- `DistributedKeyAccountGroupBindingRepository`
- `DistributedKeyAuthenticationServiceTests`
- 本地文档、任务和索引回写

## 非目标

- 不新增 Redis 主动失效机制。
- 不改变 secret hash 算法。
- 不改变 GatewayTokenAuthenticationResolver 的入口协议兼容。
- 不执行真实外部厂商 API 调用。

## 验收标准

- cache miss 加载 Key 时，没有启用账号组绑定会拒绝鉴权。
- cache hit 时，如果当前启用账号组绑定数为 0，也会拒绝鉴权。
- cache hit 时仍会校验 secret hash。
- cache snapshot 保存启用账号组绑定数量。
- 后端定向测试和编译通过。

## 风险

- 缓存命中增加一次轻量 count 查询；这是为了保证账号组停用后的权限立即收敛。
- 如果历史 Key 没有账号组绑定，会在鉴权层被拒绝；这与当前“分发 Key 必须绑定账号组”的设计一致。

## 当前状态

Done

## 实现结果

- `DistributedKeyAuthenticationService` 在 cache miss 加载 Key 时，会先检查当前 Key 是否存在启用账号组绑定；没有则拒绝鉴权。
- cache hit 后也会重新执行启用账号组绑定 count 查询，避免账号组停用后在 auth cache TTL 内继续放行。
- `DistributedKeyAuthSnapshot` 增加 `activeAccountGroupBindingCount` 字段，记录加载 snapshot 时的启用账号组绑定数量。
- 补充 `DistributedKeyAuthenticationServiceTests` 覆盖 cache miss 无启用账号组绑定、cache hit 后绑定消失、cache hit 继续校验 secret hash。
- 更新 `RedisAuthCacheStoreTests` 适配新的 auth snapshot 字段。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.auth.RedisAuthCacheStoreTests" --tests "com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionServiceTests"`

## 遗留问题与后续建议

- 本轮不主动删除 Redis auth cache；账号组停用后的权限收敛通过 cache hit 重新 count 实现。
- cache hit 增加一次轻量 count 查询，这是为了让账号组停用立即生效。
- 本轮不执行真实外部厂商 API 调用。
