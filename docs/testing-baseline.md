# x-ai-gateway 测试基线

## 目标

本基线用于保证本地与 CI 在没有外部 Redis、没有真实上游模型账号的情况下，仍然可以稳定执行核心单元测试、E2E smoke 与前端路由测试。

## 后端验证

- 后端全量测试命令：`.\gradlew.bat test`
- Secret Export 专项：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.DistributedKeyAdminServiceTests"`
- Gateway E2E smoke：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.integration.GatewayEndToEndSmokeTests"`
- Ollama Native runtime：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.execution.OllamaGatewayChatRuntimeTests"`

## 前端验证

- Operations 路由专项：在 `web` 目录运行 `bun run test -- operations-router.test.tsx`
- 全量前端测试：在 `web` 目录运行 `bun run test`

## Redis 基线

默认测试配置仍指向 `localhost:6379`，但 E2E smoke 不依赖外部 Redis 成功连接：

- 认证、路由、健康与亲和性等关键路径使用内存测试替身或同步 fallback。
- observability 热路径 Redis 写入失败时应回退同步写库，不应导致请求失败。
- 如果新增测试直接依赖 Redis 行为，应优先 mock `StringRedisTemplate`，或在测试配置中提供内存替身。

如需连接共享 Redis 进行严格本地验证，不要把明文密码写入仓库，使用环境变量覆盖：

```powershell
$env:SPRING_DATA_REDIS_HOST="192.168.154.143"
$env:SPRING_DATA_REDIS_PORT="6379"
$env:SPRING_DATA_REDIS_PASSWORD="<从安全配置读取>"
$env:SPRING_DATA_REDIS_DATABASE="0"
.\gradlew.bat test
```

Spring 配置里的安全写法应为 `host: ${REDIS_HOST:192.168.154.143}`，不要写成 `host: ${192.168.154.143}`，后者会被解析成名为 `192.168.154.143` 的占位符变量。

## 默认资源基线

首次启动会通过 `DefaultResourceBootstrapService` 幂等创建系统默认资源：

- `default` 账号池：用于空库启动、控制台选择和门户默认 Key 创建。
- 默认账号池不会创建真实上游账号、真实 API Key 或可直接访问的分发 Key。
- 已存在 `default` 账号池时不会覆盖用户配置。

## E2E Seed 约束

新架构要求分发 Key 必须绑定账号池才能启用路由。E2E seed 必须同时准备：

- `DistributedKeyEntity`
- `DistributedKeyBindingEntity`
- `UpstreamAccountPoolEntity`
- `DistributedKeyAccountPoolBindingEntity`
- 对应站点能力快照与模型能力清单

缺少账号池绑定时，`DistributedKeyQueryService.findActiveByKeyPrefix` 会将 Key 判定为不可用，这是生产规则，不应在测试中绕过。

## 验收标准

- 后端全量测试通过。
- Operations legacy 子路由不再重定向到变更编排页，而是能独立加载页面。
- Prometheus smoke 能访问 `/actuator/prometheus` 并包含网关请求指标。
- 没有真实 Redis 时，测试日志允许出现 Redis fallback 警告，但不能导致用例失败。
