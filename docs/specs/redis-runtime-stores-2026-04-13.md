# X-23：Redis 运行态存储事实来源（2026-04-13）

## 目标
- 将 Redis 用法收敛为独立运行态存储层。
- 覆盖鉴权缓存、路由缓存、限流状态、健康冷却。

## 存储抽象
### AuthCacheStore
- 实现：`RedisAuthCacheStore`
- key：`xag:auth:key:{keyPrefix}`
- value：`DistributedKeyAuthSnapshot`
- TTL：`10m`
- 模式：read-through
- 内容：只缓存 keyPrefix、keyName、maskedKey、secretHash、allowedClientFamilies

### RouteCacheStore
- 实现：`RedisRouteCacheStore`
- key：`xag:route:plan:{sha256(signature)}`
- value：`RoutePlanSnapshot`
- TTL：`2m`
- 内容：静态候选规划快照，不含实时治理计数

### RateLimitStore
- 实现：`RedisRateLimitStore`
- 兼容键空间：
  - `xag:governance:budget:{distributedKeyId}`
  - `xag:governance:rpm:{distributedKeyId}`
  - `xag:governance:tpm:{distributedKeyId}`
  - `xag:governance:concurrency:{distributedKeyId}`
- 用途：预算窗口、RPM、TPM、并发 reservation

### HealthStateStore
- 实现：`RedisHealthStateStore`
- key：`xag:health:credential:{credentialId}`
- value：`CredentialHealthState`
- TTL：`5m`
- 用途：fallback 后的短期 cooldown

## 降级原则
- Redis 不可用时：
  - `AuthCacheStore` 返回 miss，直接回退数据库
  - `RouteCacheStore` 返回 miss，不中断选路
  - `RateLimitStore` 退化为本地无状态计数结果
  - `HealthStateStore` 退化为无 cooldown
- 目标：不让本地测试和无 Redis 环境直接 500

## 兼容边界
- 现有 `AffinityBindingStore` 保持不变
- 现有 `xag:governance:*` 键空间保持兼容
- 不新增数据库表

## 关键测试锚点
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/auth/RedisAuthCacheStoreTests.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/auth/DistributedKeyAuthenticationServiceTests.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/auth/RedisRateLimitStoreTests.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/routing/RedisRouteCacheStoreTests.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/routing/RedisHealthStateStoreTests.java`
