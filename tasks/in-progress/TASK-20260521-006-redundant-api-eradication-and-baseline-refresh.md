# TASK-20260521-006 冗余接口清理与清库前 Baseline 重建

## 任务类型

父任务 + 并行子任务

## 背景

来源：`docs/requirements/REQ-20260521-006-redundant-api-eradication-and-baseline-refresh.md`

用户要求在已完成控制台重复功能前端下线的基础上，继续：

1. 仔细审视仓库里还剩哪些多余接口。
2. 分派给 subagent 并行清理。
3. 在其清库前，重新生成一版更收敛的 Liquibase baseline。

## 目标

- 清理失去产品面或与当前主线重复的接口。
- 保证接口清理与文档、OpenAPI、schema、baseline 同步收口。
- 为用户清库后的空库初始化准备最终 baseline。

## 关联子任务

- [TASK-20260521-006-01 冗余 Admin 接口审计与清理](TASK-20260521-006-01-admin-api-prune-and-alignment.md)
- [TASK-20260521-006-02 冗余协议接口与向量能力边界清理](TASK-20260521-006-02-protocol-vector-boundary-prune.md)
- [TASK-20260521-006-03 清理后 Liquibase Baseline 重建](TASK-20260521-006-03-baseline-regenerate-after-api-prune.md)

## 非目标

- 不恢复任何已下线控制台能力。
- 不支持旧库无损升级。
- 不把仍被主线依赖的 `vector stores`、`files`、Responses `file_search`、官方账号接入支撑能力误判为冗余。

## 输入

- `src/main/java/com/prodigalgal/xaigateway/admin/api/`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/`
- `src/main/resources/db/changelog/`
- `docs/openapi/public-openapi.json`
- `docs/requirements/REQ-20260521-005-console-feature-retirement-and-vector-scope-prune.md`
- `tasks/backlog/TASK-20260521-005-02-official-account-vector-api-eradication-boundary.md`

## 输出

- 冗余接口清理结果。
- baseline 重建结果。
- 清库前操作说明与验证记录。

## 影响范围

- Admin API
- public/protocol API
- OpenAPI / docs
- Liquibase schema baseline

## 依赖

- `TASK-20260521-005-01` 已完成前端下线。
- 用户将清库，因此 baseline 可按空库初始化生成。
- 最新边界已确认：`vector` 与 `file` 相关 API 保留，本轮不做这部分后端删减。

## 风险

- 删除接口与 schema 时可能误伤仍被主线使用的 `vector` / `file` / `file_search` 能力。
- baseline 生成时若漏掉仍需保留的表，会导致用户清库后初始化失败。

## 验收标准

- [x] 冗余接口清单与删除边界明确。
- [x] 对应代码、文档、OpenAPI、测试完成同步清理。
- [x] baseline 已完成校准确认，`db.changelog-master.yaml` 仍只引用单一 baseline，且保留 `vector` / `file` 主线支撑表。
- [x] 至少一轮编译或定向测试通过。
- [x] 任务与需求文档完成回写。

## 测试边界

- Java：`.\gradlew.bat compileJava compileTestJava`
- 前端：仅在受影响时运行定向 `bun run typecheck` / `vitest`
- Liquibase：校验 master 与 baseline 结构；用户清库后再执行启动验证

## 当前状态

进行中（本轮接口清理、baseline 校准确认与定向验证已完成，待后续任务索引归档）
