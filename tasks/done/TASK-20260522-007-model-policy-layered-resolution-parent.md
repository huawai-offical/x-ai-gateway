# TASK-20260522-007 Model Policy 分层收敛父任务

## 任务类型

父任务

## 背景

来源：`docs/requirements/REQ-20260522-005-model-policy-layered-resolution.md`

用户确认“逐步收缩”的方案，并要求按最完善方案推进到第三阶段。该任务承接模型白名单、模型映射、协议画像、上游凭证、账号分组、分发 key 与运行态治理的整体闭环。

## 目标

- 建立统一 Model Policy 数据模型。
- 接入运行时路由模型解析。
- 提供管理端 CRUD、预览和冲突检测。
- 推进自动探测、健康裁剪、模型级限流额度、fallback chain 和灰度路由。

## 非目标

- 不重构所有已有路由、alias、capability 事实源。
- 不做完整前端控制台重设计。
- 不硬编码真实 API key。

## 上游来源

- `docs/requirements/REQ-20260522-005-model-policy-layered-resolution.md`
- 用户关于 MiMo、DeepSeek、Codex `/responses` 兼容和账号层模型白名单/映射的设计讨论。

## 输入

- `distributed_key.allowed_models_json`
- `upstream_account_group.supported_models_json`
- `upstream_account.supported_models_json`
- `upstream_credential.supported_models_json`
- `upstream_site_profile.conversation_profile_json`
- `site_model_capability`
- `model_alias`
- 现有 route selection、account selection、governance、cost/rate limit 服务。

## 输出

- Model Policy schema、实体、repository、resolver。
- 路由解析接入。
- Admin 管理 API 与预览/检测能力。
- 第三阶段运行态治理能力与测试。

## 影响范围

- `src/main/resources/db/changelog/changes/db.changelog-0001-baseline.yaml`
- `src/main/resources/db/changelog/changes/db.changelog-0002-model-policy.yaml`
- `src/main/resources/db/changelog/db.changelog-master.yaml`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/entity/`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/repository/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/routing/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/model/`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/`
- 对应测试文件。

## 依赖

- 现有 `ModelCatalogQueryService`
- 现有 `GatewayRouteSelectionService`
- 现有 `AccountSelectionService`
- 现有 `RoutingPolicyRuntimeEnforcementService`
- 现有 provider catalog 和 site capability 事实源。

## 风险

- 策略优先级错误可能导致模型误开放或误拦截。
- fallback 与灰度如果和现有 weighted hash 混用不当，可能造成路由不可解释。
- 限流/额度如果和已有 key 级治理重复，会造成双重拦截。

## 验收标准

- [x] 第一阶段子任务完成。
- [x] 第二阶段子任务完成。
- [x] 第三阶段子任务完成。
- [x] targeted backend tests 通过。
- [x] `compileJava` 与 `compileTestJava` 通过。
- [x] requirement 与任务文件回写实施结果。

## 测试边界

- Resolver 单元测试覆盖 allow/deny/intersection/mapping。
- Route selection 测试覆盖空策略兼容、策略映射、候选裁剪。
- Admin service 测试覆盖 CRUD、preview、conflict detection。
- 第三阶段测试覆盖 discovery、health pruning、quota/rate limit、fallback chain、canary routing。

## 关联文档

- `docs/requirements/REQ-20260522-005-model-policy-layered-resolution.md`

## 关联任务

- `tasks/in-progress/TASK-20260522-007-01-model-policy-runtime-resolution.md`
- `tasks/in-progress/TASK-20260522-007-02-model-policy-admin-preview-conflict.md`
- `tasks/in-progress/TASK-20260522-007-03-model-policy-runtime-governance.md`

## 当前状态

Done

## 实施结果

- 新增 `model_policy` schema、实体、repository 和六层 scope enum。
- `model_policy` schema 已从已执行 baseline 拆分为独立 `0002` changeset，并为既有 baseline checksum 增加白名单，避免现有库启动校验失败。
- 新增 `ModelPolicyResolver` 与 `ModelPolicyRuntimeStateService`，覆盖策略映射、逐层 allow/deny 收缩、legacy supportedModels 兼容、fallback chain、canary weight、credential/account quota、模型级 rpm 和健康裁剪。
- `GatewayRouteSelectionService` 已消费 Model Policy 解析结果，并在最终 `RouteSelectionResult` 中使用选中候选的 upstream model key。
- `OpenAiNativeGatewayChatRuntime` 主构造器已显式 `@Autowired`，修复 Spring 上下文多构造器实例化失败。
- 新增 `/admin/model-policies` CRUD、preview、conflicts。
- provider catalog 支持 `modelPolicies`，MiMo 与 DeepSeek preset 已补默认策略。
- credential model discovery 已把发现模型登记为 Credential scope 的 `DISCOVERED` policy。

## 验证结果

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyResolverTests"`：通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ModelPolicyAdminServiceTests"`：通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests.shouldRouteWithModelPolicyMappedUpstreamModel"`：通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests"`：通过。
- `.\gradlew.bat compileJava compileTestJava`：通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.XAiGatewayApplicationTests.contextLoads"`：通过。
- `.\gradlew.bat bootRun --args="--server.port=0"`：真实 PostgreSQL 启动验证通过，日志出现 `Started XAiGatewayApplication`；随后由验证脚本主动停止进程。

## 遗留边界

- 前端完整 Model Policy 页面未纳入本轮，后端 Admin API 已可管理。
- 通用 request/response override 的 runtime 注入后续可在 `GatewayChatRuntimeContext` 上补 execution overlay。
