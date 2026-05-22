# TASK-20260522-022 分发 Key 创建时初始账号组绑定

## 任务类型

子任务

## 背景

来源：`docs/requirements/REQ-20260522-021-distributed-key-create-initial-account-group-binding.md`

分发 Key 已在运行时、账号选择和鉴权层要求存在启用账号组绑定。但控制台创建访问密钥仍默认立即启用，却无法在同一次创建中完成账号组绑定，导致默认提交失败。账号组详情绑定现有 Key 时也缺少运行时 provider 显式选择，兼容厂商分组可能被误绑为 `OPENAI_DIRECT`。

## 目标

- 后端支持 Key 创建时携带初始账号组绑定。
- 创建 active Key 时在同一事务中完成绑定并通过 active 账号组校验。
- 前端创建 Key 流程增加初始账号组绑定选择。
- 账号组详情绑定 Key 时增加运行时 provider 选择。
- 补充定向测试并回写文档。

## 非目标

- 不改 secret、hash、一次性导出机制。
- 不新增数据库表或迁移历史绑定。
- 不做真实厂商 API smoke。

## 上游来源

- `docs/requirements/REQ-20260522-021-distributed-key-create-initial-account-group-binding.md`
- `tasks/done/TASK-20260522-019-distributed-key-account-group-runtime-expansion.md`
- `tasks/done/TASK-20260522-021-distributed-key-auth-active-group-guard.md`

## 输入

- `DistributedKeyRequest`
- `DistributedKeyAdminService`
- `DistributedKeyAccountGroupBindingEntity`
- 访问密钥创建页
- 账号组详情页绑定表单

## 输出

- 创建 Key 原子写入初始账号组绑定。
- 管理端 UI 提供初始账号组和运行时 provider 选择。
- 后端与前端测试覆盖。
- 文档和任务索引更新。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/admin/api/`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/DistributedKeyAdminService.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/DistributedKeyAdminServiceTests.java`
- `web/src/features/keys/`
- `web/src/features/accounts/account-group-detail-page.tsx`
- `docs/index.md`
- `tasks/index.md`

## 依赖

- `DistributedKeyAccountGroupBindingRepository`
- `UpstreamAccountGroupRepository`
- 现有账号组列表 API：`/admin/account-groups`

## 风险

- 创建 active Key 的校验更严格，历史自动化如果没有绑定信息会失败。
- 前端多一个账号组查询，测试 mock 和加载态需要覆盖。

## 验收标准

- [x] active Key 创建可携带初始账号组绑定。
- [x] active Key 创建未携带 active 账号组绑定会失败。
- [x] 账号组详情绑定请求发送显式运行时 providerType。
- [x] 前端创建 payload 包含 `initialAccountGroupBindings`。
- [x] 编译与定向测试通过。

## 测试边界

- 后端：`DistributedKeyAdminServiceTests`。
- 前端：`keys-page.test.tsx`、`account-group-detail-page.test.tsx`。
- 不执行真实外部 API。

## 关联文档

- `docs/requirements/REQ-20260522-021-distributed-key-create-initial-account-group-binding.md`

## 关联任务

- `tasks/done/TASK-20260522-019-distributed-key-account-group-runtime-expansion.md`
- `tasks/done/TASK-20260522-021-distributed-key-auth-active-group-guard.md`

## 当前状态

Done

## 实现结果

- 新增 `DistributedKeyInitialAccountGroupBindingRequest`，并让 `DistributedKeyRequest` 支持 `initialAccountGroupBindings`。
- `DistributedKeyAdminService.create` 在同一事务内完成 Key 保存、初始账号组绑定保存、active Key 账号组守卫校验和一次性 secret 导出授权。
- 访问密钥创建页增加初始账号组、运行时 provider、绑定优先级，并将绑定写入创建 payload。
- 账号组详情页绑定访问密钥时增加运行时 provider 下拉，兼容 `OPENAI_COMPATIBLE` 等厂商协议入口。
- MiMo 本地库凭证 `id=8`、`id=9` 已替换为用户新提供的两枚 token，未写入仓库文件；已通过解密一致性校验。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.DistributedKeyAdminServiceTests"`
- `bun run test -- ./src/features/keys/keys-page.test.tsx ./src/features/accounts/account-group-detail-page.test.tsx`

## 遗留边界

- 不迁移历史分发 Key 绑定数据。
- 不做真实外部 API smoke。
- 不改变 secret/hash/一次性导出策略。
