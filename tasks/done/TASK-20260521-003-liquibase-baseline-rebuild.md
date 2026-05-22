# TASK-20260521-003 Liquibase 新 Baseline 重建

## 任务类型

父任务 + 本轮实现子任务

## 背景

来源：`docs/requirements/REQ-20260521-003-liquibase-baseline-rebuild.md`

本地启动失败于 Liquibase：`db.changelog-0008-upstream-account-group.yaml::0008-upstream-account-group::codex` 被当成新 changeset，重新创建已存在的 `upstream_account` 表。

用户随后明确要求清库并重做 baseline，因此本任务从“旧库兼容”改为“清理旧 changelog 并生成新 baseline”。

## 目标

- 清理旧多文件增量 changelog。
- 生成单一 baseline changeset。
- baseline 只包含当前 schema，不包含已下线的 Live / Realtime 和旧账号池命名。

## 非目标

- 不支持旧库无损升级。
- 不恢复旧账号池应用层命名。
- 不恢复 Live / Realtime。
- 不执行完整测试套件。

## 输入

- IntelliJ 启动日志中的 Liquibase 异常。
- 当前 `src/main/resources/db/changelog` 作为 baseline 生成输入。
- 旧 `HEAD` 中的 `account-pool` changeset 内容。

## 输出

- 单一 baseline changelog。
- 编译验证记录。
- 本地任务归档记录。

## 影响范围

- Liquibase baseline。
- 账号分组相关数据库表名、列名、约束名和索引名。

## 依赖

- Liquibase baseline 从空库执行。
- 用户清库后重新初始化。

## 风险

- baseline 不适合直接跑在已有数据的库上。
- 清库前需要确认无需保留历史数据。

## 验收标准

- [x] master changelog 只引用新 baseline。
- [x] baseline 不包含旧账号池、Live、Realtime schema。
- [x] `.\gradlew.bat compileJava compileTestJava` 通过。
- [ ] 清库后本地启动不再因 `upstream_account` 已存在而失败。

## 测试边界

- 执行编译检查。
- 解析 master 与 baseline YAML，确认文件结构有效。
- 当前数据库尚未清库，不对旧库执行启动验证；清库后再执行一次应用启动。

## 实施结果

- 清理旧多文件增量 changelog，`src/main/resources/db/changelog/changes/` 当前只保留 `db.changelog-0001-baseline.yaml`。
- `src/main/resources/db/changelog/db.changelog-master.yaml` 当前只 include 新 baseline。
- baseline 由现有 changelog 机械合并生成，保留当前有效表、列、unique constraint、foreign key、index 和必要 seed SQL。
- baseline 过滤了已经下线的 `live_session`、`live_session_event`，并确认不含旧账号池、Realtime schema。

## 验证记录

- YAML 结构解析：通过。
- baseline 表数量检查：`80` 张表，`has_live=False`，账号分组表和分组绑定表存在。
- `.\gradlew.bat compileJava compileTestJava`：通过。
- 未执行本地启动：当前连接的数据库仍是旧库，用户准备清库后再跑 baseline。

## 当前状态

Done，等待清库后的启动验证。
