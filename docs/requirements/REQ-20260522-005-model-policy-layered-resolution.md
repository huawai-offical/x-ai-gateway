# REQ-20260522-005 Model Policy 分层收敛与第三阶段路由治理

## 背景

Codex 新版本只支持 `/v1/responses`，但部分上游只兼容 `/v1/chat/completions`；同时 MiMo 等厂商要求多轮 tool call 历史中回传 assistant 的 `reasoning_content`。项目已经引入上游凭证、账号分组、厂商接入画像和分发 key 的串联关系，需要把模型白名单、模型映射、协议差异、健康裁剪、限流额度、fallback 和灰度路由统一纳入一个可追溯的 Model Policy 体系。

## 目标

- 新增统一 `model_policy` 策略事实源，覆盖 Vendor、ProviderSite/Profile、Credential、Account、AccountGroup、DistributedKey 六层作用域。
- 在路由阶段按 DistributedKey、AccountGroup、Account/Credential、ProviderSite/Profile、Vendor 逐层收缩有效模型。
- 支持模型别名映射优先级：DistributedKey -> AccountGroup -> Account/Credential -> ProviderSite/Profile -> Vendor -> 原始模型透传。
- 提供管理端 CRUD、冲突检测、有效模型预览和策略预设导入能力。
- 推进第三阶段治理能力：自动探测模型列表、按健康度裁剪候选、模型级限流/额度、fallback chain 和灰度路由。

## 范围

- 后端数据库 baseline、JPA Entity、Repository、核心 resolver、路由选择、管理端 API。
- 与现有 `distributed_key.allowed_models_json`、`upstream_account_group.supported_models_json`、`upstream_account.supported_models_json`、`upstream_credential.supported_models_json`、`site_model_capability`、`model_alias` 保持兼容。
- 模型策略的请求/响应 override 只作为执行上下文与路由依据落库和解析；具体所有协议转换细节仍由 ProviderSite/Profile 的 conversation profile 和 runtime adapter 承担。

## 非目标

- 本需求不重做整套前端控制台信息架构。
- 本需求不废弃已有 `model_alias` 与 `site_model_capability`，而是在其上叠加更高优先级的策略层。
- 本需求不引入外部 SaaS 工作流。
- 本需求不泄露或硬编码真实上游 API key。

## 分层策略

有效模型计算：

```text
effective_models =
  DistributedKey.allowlist
  ∩ AccountGroup.allowlist
  ∩ Account_or_Credential.allowlist
  ∩ ProviderSite/Profile.capability
  ∩ Vendor.default_capability
  - denylist
```

模型映射优先级：

```text
DistributedKey mapping
-> AccountGroup mapping
-> Account / Credential mapping
-> ProviderSite/Profile mapping
-> Vendor mapping
-> raw passthrough
```

## 三阶段落地

### 第一阶段：策略表与运行时解析

- 新增 `model_policy` 表、实体和 repository。
- 新增 Model Policy resolver，输出有效模型、上游模型、候选裁剪理由和 request/response override。
- 接入 `GatewayRouteSelectionService`，让路由在候选评估前应用逐层收缩与映射。

### 第二阶段：管理端与可解释性

- 新增 Admin CRUD。
- 新增有效模型预览接口，展示各层 allow/deny/mapping 命中结果。
- 新增冲突检测，至少覆盖同层重复映射、显式 deny 覆盖 allow、映射目标无候选、灰度权重非法、fallback chain 无可用目标。
- Provider catalog/preset 可以导入策略默认值。

### 第三阶段：运行态治理

- 自动探测模型列表时写入策略或策略元数据。
- 根据 credential/account 健康状态、quota、runtime policy 裁剪候选。
- 支持模型级 rpm/tpm/request/token quota。
- 支持 fallback chain 指定模型和候选顺序。
- 支持 canary/灰度权重，让同一个 public model 可以按策略流量切到不同 upstream model。

## 风险

- 当前工作区存在大量无关改动，本需求只触碰 Model Policy 相关文件，不回滚其他变更。
- 既有路由依赖 `model_alias` 与 `site_model_capability`，新策略需要兼容旧事实源，避免让已有模型不可达。
- 第三阶段的限流/额度需要与现有 DistributedKey 和 RouteGuard policy 协同，避免重复扣减或误拦截。
- 对 `/v1/responses` -> `/v1/chat/completions` 的 streaming 事件形态转换仍属于协议 adapter 后续增强，不在 Model Policy 本身完成。

## 验收标准

- 可通过策略把一个 public model 映射到不同 upstream model，并按优先级选择。
- DistributedKey、AccountGroup、Credential、Account、ProviderSite/Profile、Vendor 任一层 deny 都能阻断模型。
- 空策略时维持旧 catalog/alias 路由行为。
- Admin API 可创建、更新、删除、查询、预览、检测冲突。
- 自动探测结果可进入策略体系，并能在 preview 中解释来源。
- 健康裁剪、模型级限流/额度、fallback chain、灰度权重至少有核心单元测试覆盖。

## 实施结果

- 已新增 `model_policy` baseline schema、`ModelPolicyEntity`、`ModelPolicyRepository` 与 `ModelPolicyScopeType`，支持 `VENDOR / SITE_PROFILE / CREDENTIAL / ACCOUNT / ACCOUNT_GROUP / DISTRIBUTED_KEY` 六层作用域。
- 已将 `model_policy` DDL 从已执行 baseline 中拆分到独立 `db.changelog-0002-model-policy.yaml`，并在 baseline changeset 上声明既有 checksum 白名单，避免现有数据库因 baseline checksum 变化启动失败。
- 已新增 `ModelPolicyResolver`，在旧 `model_alias`、`site_model_capability` 基础上叠加策略层；空策略时保持旧路由行为，有策略时按当前 DistributedKey 可达的账号组、账号、凭证、站点和厂商收缩候选。
- 已接入 `GatewayRouteSelectionService`：策略存在时绕过 route plan 缓存即时解析，候选评估阶段应用 allow/deny、legacy supportedModels、fallback chain、canary weight、credential/account quota、模型级 rpm 和健康裁剪。
- 已修正策略映射后的执行模型：`RouteSelectionResult.resolvedModelKey` 使用最终选中候选的 upstream `modelKey`，支持同一 public model 映射多个 upstream model 并由路由策略选择。
- 已为 `OpenAiNativeGatewayChatRuntime` 主构造器显式标记 `@Autowired`，避免 Spring Boot 4 / Spring 7 在多构造器场景下误走默认构造器实例化。
- 已新增 `/admin/model-policies` 管理端 API，覆盖 CRUD、preview 和 conflicts。
- 已把 provider catalog 扩展为可声明 `modelPolicies`；MiMo preset 默认 base URL 对齐到 `https://token-plan-sgp.xiaomimimo.com/v1`，并内置 `gpt-5-codex -> mimo-v2.5-pro` 的 Responses/Codex 策略映射；DeepSeek preset 内置 `deepseek-chat`、`deepseek-reasoner` allow 策略。
- 已在 `CredentialModelDiscoveryService.refreshCredential` 中把自动发现模型登记为 Credential scope 的 `DISCOVERED` 策略，保留原有 `site_model_capability` 刷新路径。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyResolverTests"`：通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ModelPolicyAdminServiceTests"`：通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests.shouldRouteWithModelPolicyMappedUpstreamModel"`：通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests"`：通过。
- `.\gradlew.bat compileJava compileTestJava`：通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.XAiGatewayApplicationTests.contextLoads"`：通过，覆盖 H2 测试上下文。
- `.\gradlew.bat bootRun --args="--server.port=0"`：真实 PostgreSQL 启动验证通过，日志出现 `Started XAiGatewayApplication`；验证脚本随后主动停止进程，因此 Gradle 任务以手动终止状态结束。

## 遗留问题

- 本轮后端主链路已推进到第三阶段；完整前端 Model Policy 管理页面尚未实现，当前可先通过 Admin API 管理与预览。
- `request_overrides_json`、`response_overrides_json` 已可存储、导入和展示；具体协议转换仍由 ProviderSite/Profile 的 `conversation_profile_json` 与 runtime adapter 消费，后续如需做通用 override 注入，可在 `GatewayChatRuntimeContext` 上补 execution overlay。
- `/v1/responses` streaming 到只支持 Chat Completions 上游的事件形态转换仍属于协议 adapter 后续增强，不由 Model Policy 本身解决。

## 运行期补充：模型刷新幂等性

2026-05-22 运行日志暴露出多次点击同一凭证的模型刷新时，`site_model_capability` 可能因 `(site_profile_id, model_key)` 唯一约束冲突失败。刷新链路需要满足：

- 同一站点档案的模型能力刷新必须按 `model_key` 幂等更新，不再采用先删后插的整体替换。
- 上游返回重复模型时，需要先按归一化 `model_key` 合并能力，再写入数据库。
- 并发刷新同一站点时，需要在站点维度串行化写入，避免两个事务同时插入同一个新模型。
- 空模型列表只刷新站点快照，不应清空已有模型能力；删除站点时仍由管理端显式清理 capability 数据。
- 自动发现写入 `DISCOVERED` Model Policy 时也需要按 `model_key` 去重，避免重复策略写入。
- Java 25 下 Netty `System::loadLibrary` native-access warning 属于运行时告警，可通过 JVM 参数抑制，不作为业务失败处理。

### 补充实施结果

- 已将 `ProviderSiteRegistryService.refreshCapabilities` 从先删后插改为按 `model_key` 合并后更新已有行；新模型插入，已存在模型更新，本次未返回的历史模型标记为 inactive。
- 已在刷新前通过 `UpstreamSiteProfileRepository.findByIdForUpdate` 对站点档案行加 `PESSIMISTIC_WRITE` 锁，同一站点的并发刷新会在数据库层串行化。
- 已将空模型列表处理为仅刷新站点快照，不再清空 `site_model_capability`。
- 已将 `CredentialModelDiscoveryService` 的 probe、refresh 和 `DISCOVERED` policy 写入统一按 `model_key` 去重，避免响应中大小写不同的重复模型造成前端重复或策略重复写入。
- 已关闭 `spring.data.redis.repositories.enabled`，避免启动时把所有 JPA repository 反复尝试归属到 Redis store。

### 补充验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.catalog.CredentialModelDiscoveryServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryServiceTests"`：通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.XAiGatewayApplicationTests.contextLoads"`：通过。
- `.\gradlew.bat compileJava compileTestJava`：通过。

### 补充遗留说明

- Java 25 的 Netty native-access warning 不是本次 23505 的原因，也不会阻断启动；如需压掉告警，可在 IntelliJ 运行配置 VM options 中加入 `--enable-native-access=ALL-UNNAMED`。
