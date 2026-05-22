# TASK-20260522-019 分发 Key 账号组运行时展开

## 任务类型

子任务

## 背景

来源：`docs/requirements/REQ-20260522-018-distributed-key-account-group-runtime-expansion.md`

用户确认“分发 key 再关联到账号组，这样整体就串起来了”。当前管理侧已经有 DistributedKey 与账号组绑定，但运行时查询候选凭证仍主要依赖旧的 Key->Credential 直接绑定关系。为了让新架构真的跑通，需要让 `DistributedKeyQueryService` 从账号组绑定展开组内 API Key 凭证。

## 目标

- active DistributedKey 通过 active 账号组绑定展开组内 active API Key 凭证。
- 保留直接绑定候选，并对同一 credential 去重。
- 账号组绑定 provider type 过滤凭证 provider type。
- 补充后端单元测试。

## 非目标

- 不删除旧直接绑定能力。
- 不改造 OAuth/auth.json 账号候选。
- 不做真实厂商调用。
- 不改前端布局。

## 上游来源

- `docs/requirements/REQ-20260522-018-distributed-key-account-group-runtime-expansion.md`
- `tasks/done/TASK-20260522-018-credential-multi-protocol-endpoint-binding.md`

## 输入

- `DistributedKeyQueryService`
- `DistributedKeyAccountGroupBindingRepository`
- `UpstreamCredentialRepository`
- `DistributedKeyView`
- `DistributedCredentialBindingView`

## 输出

- runtime 查询可通过账号组绑定拿到凭证候选。
- 后端测试覆盖账号组展开和去重。
- 文档与任务索引回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/auth/DistributedKeyQueryService.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/DistributedKeyAdminService.java`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/repository/DistributedKeyAccountGroupBindingRepository.java`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/repository/UpstreamCredentialRepository.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/auth/DistributedKeyQueryServiceTests.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/DistributedKeyAdminServiceTests.java`
- `docs/index.md`
- `tasks/index.md`

## 依赖

- API Key 凭证已归属账号组。
- 分发 Key 已支持账号组绑定。
- 协议簇授权和模型白名单已在 `DistributedKeyView` 中解析。

## 风险

- 需要避免直接绑定和账号组展开重复产生候选。
- 账号组展开默认 weight=100，后续需要组级权重时再扩表。

## 验收标准

- [x] 仅有账号组绑定时，`findActiveByKeyPrefix` 返回组内凭证候选。
- [x] 直接绑定和账号组展开同一凭证时只保留一次。
- [x] provider type 不匹配的组内凭证不参与候选。
- [x] 后端定向测试与编译通过。

## 测试边界

- 后端：`DistributedKeyQueryServiceTests`。
- 编译：`.\gradlew.bat compileJava compileTestJava`。
- 不执行真实外部 API。

## 关联文档

- `docs/requirements/REQ-20260522-018-distributed-key-account-group-runtime-expansion.md`

## 关联任务

- `tasks/done/TASK-20260522-018-credential-multi-protocol-endpoint-binding.md`
- `tasks/done/TASK-20260522-009-protocol-suite-authorization-migration.md`

## 当前状态

Done

## 实现结果

- `DistributedKeyQueryService` 已从 active 账号组绑定展开组内 active API Key 凭证候选，并保留旧直接绑定作为显式覆盖。
- 展开查询使用 `groupId + providerType + deleted=false + active=true`，确保账号组绑定的 provider type 是候选过滤条件。
- 直接绑定优先去重；同一 credential 同时来自直接绑定和账号组展开时，只保留直接绑定的 `bindingId`、priority 和 weight。
- 停用账号组不会参与运行时展开，active Key 查询也要求存在启用中的账号组绑定。
- `DistributedKeyAdminService` 启用校验同步改为要求“已启用的账号分组绑定”，避免管理端和运行时口径不一致。
- `AccountGroupAdminService` 删除账号组后的 Key 保活判断同步使用启用账号组绑定口径，避免已停用账号组误保活。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyResolverTests" --tests "com.prodigalgal.xaigateway.admin.application.DistributedKeyAdminServiceTests"`

## 遗留边界

- 不删除旧 `distributed_key_binding` 表。
- 不引入账号组级 weight 字段。
- 不执行真实外部厂商 API 调用。
