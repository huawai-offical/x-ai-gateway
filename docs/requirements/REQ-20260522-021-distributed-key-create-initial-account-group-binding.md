# REQ-20260522-021 分发 Key 创建时初始账号组绑定

## 背景

分发 Key 的运行时链路已经收敛为：Key 必须关联启用账号组，账号组内的上游凭证再按运行时 `providerType` 展开候选。上一阶段同时把鉴权层也收紧到“存在启用账号组绑定”。

当前管理端仍存在一个体验与一致性缺口：访问密钥创建页默认勾选“创建后立即启用”，但后端要求启用前必须已有启用账号组绑定。用户按默认表单创建会失败，并且需要先创建 inactive Key、再进入账号组详情绑定、最后回到 Key 页面启用，链路过长。账号组详情现有绑定表单还会按账号来源类型推断运行时 `providerType`，对 MiMo、DeepSeek 等 OpenAI-compatible 凭证分组容易误绑成 `OPENAI_DIRECT`。

## 目标

- 分发 Key 创建请求支持携带初始账号组绑定。
- 创建 Key、写入账号组绑定、启用校验必须在同一个事务内完成。
- 创建 active Key 时必须至少包含一个 active 且所属账号组 active 的初始绑定。
- 控制台创建访问密钥弹窗允许选择初始账号组和绑定运行时 provider。
- 账号组详情绑定现有 Key 时允许显式选择运行时 provider，避免 MiMo/DeepSeek 等兼容厂商被错误绑定。
- 补充后端与前端回归测试。

## 非目标

- 不改分发 Key secret 生成、hash 或一次性导出策略。
- 不调整账号组与凭证表结构。
- 不迁移历史账号组绑定数据。
- 不执行真实厂商 API 调用。

## 方案

### 后端

- 在 `DistributedKeyRequest` 中增加 `initialAccountGroupBindings`。
- 新增 `DistributedKeyInitialAccountGroupBindingRequest`，字段包含 `groupId`、`providerType`、`priority`、`active`。
- `DistributedKeyAdminService.create` 先保存 Key，再保存初始账号组绑定；当请求 active 时，用刚写入的绑定校验 active 账号组归属。
- 重复的 `groupId + providerType` 初始绑定直接拒绝，避免创建阶段埋重复路由权重。

### 前端

- 访问密钥创建弹窗加载 `/admin/account-groups` 作为初始绑定候选。
- 创建表单增加初始账号组、运行时 provider 和优先级。
- 若选择“创建后立即启用”，提交前要求已选择初始账号组绑定。
- 账号组详情页现有绑定表单增加运行时 provider 选择框，并随请求传给 `/admin/account-groups/{id}/bindings`。

## 范围

- `src/main/java/com/prodigalgal/xaigateway/admin/api/DistributedKeyRequest.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/DistributedKeyInitialAccountGroupBindingRequest.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/DistributedKeyAdminService.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/DistributedKeyAdminServiceTests.java`
- `web/src/features/keys/keys-page.tsx`
- `web/src/features/keys/keys-page.test.tsx`
- `web/src/features/accounts/account-group-detail-page.tsx`
- `web/src/features/accounts/account-group-detail-page.test.tsx`

## 风险

- `DistributedKeyAdminService` 构造参数增加账号组仓库，相关单元测试需要同步。
- 前端创建弹窗多一个账号组查询，测试 mock 需要补齐。
- active Key 创建现在更严格，未选择初始账号组会被前端与后端共同拒绝。

## 验收标准

- [x] active Key 可在创建请求中携带初始账号组绑定并成功创建。
- [x] active Key 创建时没有 active 初始账号组绑定会失败。
- [x] 初始绑定的运行时 providerType 按用户选择保存。
- [x] 账号组详情绑定 Key 时可选择 `OPENAI_COMPATIBLE` 等运行时 provider。
- [x] 后端定向测试、前端定向测试与编译通过。

## 验证方式

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.DistributedKeyAdminServiceTests"`
- `bun run test -- ./src/features/keys/keys-page.test.tsx ./src/features/accounts/account-group-detail-page.test.tsx`

## 实现结果

- `DistributedKeyRequest` 增加 `initialAccountGroupBindings`，创建请求可携带账号组、运行时 provider、优先级与启用状态。
- `DistributedKeyAdminService.create` 在同一事务中保存 Key、写入初始账号组绑定，并对 active Key 执行启用账号组绑定校验。
- 访问密钥创建页加载账号组候选，默认选择 active 账号组，并在创建 payload 中下发初始账号组绑定。
- 账号组详情页绑定访问密钥时增加运行时 provider 下拉，支持 `OPENAI_COMPATIBLE` 等兼容厂商口径。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.DistributedKeyAdminServiceTests"`
- `bun run test -- ./src/features/keys/keys-page.test.tsx ./src/features/accounts/account-group-detail-page.test.tsx`

## 关联任务

- `tasks/done/TASK-20260522-022-distributed-key-create-initial-account-group-binding.md`
