# TASK-20260522-008 模型刷新幂等性修复

## 任务类型

子任务

## 背景

来源：`docs/requirements/REQ-20260522-005-model-policy-layered-resolution.md`

用户在本地运行服务后，多次点击同一个 key 的刷新模型动作，PostgreSQL 报错：

```text
ERROR: duplicate key value violates unique constraint "uk_site_model_capability_profile_model_key"
Key (site_profile_id, model_key)=(2, mimo-v2-omni) already exists.
```

同时日志中出现 Java 25 对 Netty native library 的 restricted method warning，需要区分业务错误和运行时告警。

## 目标

- 让 `site_model_capability` 刷新对重复点击、重复模型和并发请求保持幂等。
- 保留站点删除时的显式 capability 清理，不扩大到其他数据清理。
- 让自动发现模型写入 `DISCOVERED` Model Policy 时按 `model_key` 去重。
- 关闭未使用的 Redis Repository 扫描，避免启动日志持续输出 JPA repository 无法归属 Redis store 的提示。
- 明确 Java 25 Netty native-access warning 的处理方式。

## 非目标

- 不修改用户已经录入的 MiMo、DeepSeek key。
- 不清理生产或本地数据库中的既有数据。
- 不重做前端刷新按钮的交互节流；本任务先保证后端幂等。
- 不处理协议 adapter 的 `/responses` 与 `/chat/completions` 事件转换。

## 上游来源

- `docs/requirements/REQ-20260522-005-model-policy-layered-resolution.md`
- `tasks/done/TASK-20260522-007-model-policy-layered-resolution-parent.md`

## 输入

- `ProviderSiteRegistryService.refreshCapabilities`
- `CredentialModelDiscoveryService.refreshDiscoveredModelPolicies`
- `site_model_capability` 唯一约束 `(site_profile_id, model_key)`
- 用户提供的启动与刷新错误日志。

## 输出

- 幂等的模型 capability 刷新逻辑。
- 重复模型合并逻辑。
- 站点维度刷新串行化保护。
- 针对重复刷新和重复模型的单元测试。
- 文档和任务状态回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/admin/application/ProviderSiteRegistryService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/catalog/CredentialModelDiscoveryService.java`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/repository/UpstreamSiteProfileRepository.java`
- `src/main/resources/application.yaml`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/ProviderSiteRegistryServiceTests.java`
- 必要时补充 discovery service 测试。

## 依赖

- 现有 `UpstreamSitePolicyService`
- 现有 `SiteModelCapabilityRepository`
- 现有 `ModelPolicyRepository`

## 风险

- 如果空模型列表被误判为刷新失败，可能错误下线已有 capability。
- 如果并发保护只在 JVM 内生效，多实例部署仍可能出现插入竞争；应优先使用数据库行锁。
- 如果合并重复模型时丢失 capability 标志，模型能力展示会偏保守。

## 验收标准

- [x] 重复 `model_key` 的模型列表只保存一条 capability。
- [x] 已存在的 capability 被更新而不是删除重建。
- [x] 本次刷新未返回但此前存在的模型被标记为 inactive，而不是触发唯一约束冲突。
- [x] 空模型列表不清空已有 capability。
- [x] `DISCOVERED` Model Policy 写入对重复模型保持幂等。
- [x] 启动时不再扫描 Redis Repository。
- [x] targeted tests 与编译验证通过。

## 测试边界

- 单元测试覆盖重复模型、已有模型更新、空列表不清空。
- 编译验证覆盖 repository 查询方法与 Spring Data JPA 方法签名。
- 不执行真实外部 MiMo/DeepSeek 调用，避免消耗用户 key。

## 关联文档

- `docs/requirements/REQ-20260522-005-model-policy-layered-resolution.md`

## 关联任务

- `tasks/done/TASK-20260522-007-model-policy-layered-resolution-parent.md`

## 当前状态

Done

## 实施结果

- `ProviderSiteRegistryService.refreshCapabilities` 已使用站点行级悲观锁串行化同一站点刷新。
- `site_model_capability` 写入已改为读取现有模型后按 `model_key` upsert 式更新；重复上游模型会先合并 capability，历史模型在本次刷新缺失时标记 inactive。
- 空模型列表只刷新 `site_capability_snapshot`，不触碰已有模型能力记录。
- `CredentialModelDiscoveryService` 已对 probe、refresh 和 `DISCOVERED` policy 写入统一按 `model_key` 去重。
- `application.yaml` 已关闭 Redis Repository auto-scan，保留 RedisTemplate 使用路径。

## 验证结果

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.catalog.CredentialModelDiscoveryServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryServiceTests"`：通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.XAiGatewayApplicationTests.contextLoads"`：通过。
- `.\gradlew.bat compileJava compileTestJava`：通过。

## 遗留边界

- Netty `System::loadLibrary` restricted method warning 来自 Java 25 native-access 策略，不是业务失败；如需隐藏，可在运行配置中加入 `--enable-native-access=ALL-UNNAMED`。
