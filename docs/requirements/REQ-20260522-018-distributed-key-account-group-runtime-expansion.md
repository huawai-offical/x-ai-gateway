# REQ-20260522-018 分发 Key 账号组运行时展开

## 背景

当前配置主链路已经形成：厂商协议入口 -> 上游凭证 -> 账号分组 -> 分发 Key。管理侧也要求分发 Key 启用前必须至少绑定一个账号组。但运行时 `DistributedKeyQueryService` 仍主要从旧的 `distributed_key_binding` 直接绑定表读取候选凭证；如果用户只按新设计把 Key 绑定到账号组，再把 API Key 凭证归属到账号组，路由层拿到的 `DistributedKeyView.bindings` 可能为空，导致新主链路在运行时断开。

## 目标

- `DistributedKeyQueryService` 读取 active Key 时，从 active 账号组绑定展开该组下 active API Key 凭证。
- 展开的凭证候选继承账号组绑定的 priority，并保持凭证自身 provider type 与 Base URL。
- 继续保留旧 Key->Credential 直接绑定作为显式覆盖/补充候选，但去重时同一凭证只出现一次。
- 账号组绑定中的 provider type 作为展开过滤条件，避免一个 Key 绑定某类账号组时拉入其他 provider 的凭证。
- 运行时模型目录和路由选择可以仅依赖 “Key -> 账号组 -> 凭证” 获得候选。

## 范围

- `DistributedKeyQueryService` 候选绑定构建逻辑。
- `UpstreamCredentialRepository` 增加按 groupId/providerType/active 查询。
- 单元测试覆盖账号组展开、直接绑定去重、无 active 账号组绑定不返回 active key。
- 本地任务、文档和索引回写。

## 非目标

- 不删除旧 `distributed_key_binding` 直接绑定表。
- 不改造 OAuth/auth.json 账号运行态候选。
- 不改造前端账号组详情页的布局。
- 不执行真实外部厂商 API 调用。

## 验收标准

- 只有账号组绑定、没有直接凭证绑定的 active Key，运行时查询仍能返回组内 API Key 凭证候选。
- 同一凭证既来自账号组展开又来自直接绑定时，只保留一个候选。
- 账号组绑定 provider type 与凭证 provider type 不匹配时，不展开该凭证。
- 相关后端测试和编译通过。

## 风险

- 直接绑定和账号组展开同时存在时，需要稳定排序和去重，避免路由权重漂移。
- 账号组绑定目前只有 provider type、priority、active 字段，本轮使用默认 weight=100；后续如需组级权重再单独扩展 schema。

## 当前状态

Done

## 实现结果

- `DistributedKeyQueryService` 构建 `DistributedKeyView.bindings` 时，先读取旧 Key->Credential 直接绑定，再从 active Key->账号组绑定展开组内 active、未删除的 API Key 凭证。
- 账号组展开使用绑定行的 `providerType` 过滤凭证，避免同一账号组内不同 provider 凭证被误拉入候选。
- 直接绑定与账号组展开出现同一 credential 时，保留直接绑定的 `bindingId`、priority 和 weight，账号组展开只作为补充候选。
- 账号组展开候选继承账号组绑定 priority，默认 weight 为 100；停用的账号组不会参与运行时展开。
- `DistributedKey` 运行时 active 查询和管理端启用校验均要求存在“绑定 active 且账号组 active”的账号组绑定。
- 删除账号组后，受影响 `DistributedKey` 的保活判断也同步使用“绑定 active 且账号组 active”的口径。
- `UpstreamCredentialRepository` 增加 `groupId + providerType + active + deleted=false` 查询方法。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyResolverTests" --tests "com.prodigalgal.xaigateway.admin.application.DistributedKeyAdminServiceTests"`

## 遗留问题与后续建议

- 本轮不删除旧 `distributed_key_binding` 直接绑定表，直接绑定仍作为显式覆盖路径保留。
- 账号组展开默认 weight=100；如后续需要账号组级权重，应新增 schema 字段并单独设计迁移。
- 本轮不执行真实外部厂商 API 调用，真实 MiMo/DeepSeek 双协议 smoke 可在后续测试窗口继续推进。
