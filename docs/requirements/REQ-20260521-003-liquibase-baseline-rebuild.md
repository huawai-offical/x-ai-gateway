# REQ-20260521-003 Liquibase 新 Baseline 重建

## 背景

本地启动在 Liquibase 阶段失败：

- 当前 changelog 将旧 `account-pool` 命名改为 `account-group`。
- 已存在的本地数据库记录过旧 `0008-upstream-account-pool` changeset。
- 当前文件名和 changeset id 被改成 `0008-upstream-account-group` 后，Liquibase 将它识别为全新 changeset 并尝试重新创建 `upstream_account`，触发 `ERROR: relation "upstream_account" already exists`。

用户随后明确决定清库并重做 baseline，因此本轮不再维护旧库兼容迁移，而是将历史 changelog 清理为一个新的 baseline。

## 目标

- 清理旧的多文件增量 changelog。
- 生成一个面向清库后初始化的新 baseline。
- Baseline 只保留当前产品边界：账号分组、上游凭证、当前控制台/Portal/网关资源，不再包含 Live / Realtime 已下线 schema。

## 范围

- `src/main/resources/db/changelog/db.changelog-master.yaml`
- `src/main/resources/db/changelog/changes/db.changelog-0001-baseline.yaml`
- 旧 `src/main/resources/db/changelog/changes/db.changelog-*.yaml` 增量文件。

## 非目标

- 不恢复 Live / Realtime。
- 不保留应用层 `AccountPool` 类型或前端旧路由。
- 不支持旧库无损升级；用户本轮会清库重新初始化。

## 方案

- 以现有 changelog 为输入材料，机械合并最终 schema。
- 合并 `createTable` 与后续 `addColumn/dropNotNull/addNotNull` 的最终列定义。
- 保留当前有效的 unique constraint、foreign key、index 和必要 seed SQL。
- 过滤已经下线的 `live_session`、`live_session_event` 及其约束、索引。
- master changelog 只 include 新 baseline。

## 风险

- 清库前必须确认不需要保留旧数据。
- Baseline 是新库初始化口径，不适合直接跑在已有数据的库上。

## 验收标准

- master changelog 只引用新的 baseline。
- baseline 中不包含旧账号池、Live、Realtime schema。
- 清库后 Liquibase 从空库执行 baseline。
- `.\gradlew.bat compileJava compileTestJava` 通过。
- 本地启动不再因旧 changeset 身份冲突卡在 `0008`。

## 实施结果

- 已将 master changelog 收敛为单一 baseline include。
- 已生成 `db.changelog-0001-baseline.yaml`，覆盖当前有效 schema。
- 已删除旧 `db.changelog-0001` 到 `0050` 增量文件。
- baseline 中不包含旧账号池、Live、Realtime schema。
- 当前旧库未清，不执行启动验证；清库后以新 baseline 初始化。

## 验证结果

- YAML 结构解析通过。
- baseline 结构检查：`80` 张表，包含 `upstream_account_group` 和 `distributed_key_account_group_binding`，不包含 `live_session`。
- `.\gradlew.bat compileJava compileTestJava`：通过。
