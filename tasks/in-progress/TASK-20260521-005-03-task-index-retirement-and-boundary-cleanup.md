# TASK-20260521-005-03 任务索引下线能力清理与边界补充

## 任务类型

子任务

## 背景

父任务 [TASK-20260521-005](TASK-20260521-005-console-feature-retirement-and-vector-scope-prune.md) 已明确一批控制台能力处于下线或待彻底删除状态，但 `tasks/index.md` 和部分历史任务仍把这些能力写成当前优先级、现役控制台入口或默认产品面，容易让后续排期与实现判断失真。

## 目标

- 清理 `tasks/index.md` 中直接绑定已下线或准备删除能力的现役引用。
- 为需要保留的历史任务补充“控制台已下线、后端/API 暂保留”一类边界说明。
- 保留历史实现证据，不误删仍承担后端边界说明职责的任务。

## 非目标

- 不修改 `src/`、`web/`、`docs/openapi/` 或运行代码。
- 不直接删除仍承担后端/API 范围决策职责的任务文档。
- 不重写与本轮清理无关的历史任务结构。

## 输入

- `tasks/index.md`
- `tasks/done/` 中与 `官方账号运行态`、`能力矩阵`、`Native 命名空间兼容`、`Provider 参考差距`、`站点档案`、`成本路由策略中心`、`向量检索排障沙盒`、旧 `account pool`、`live/realtime`、`provider-sites`、`reference-gap` 相关的任务文件
- `tasks/in-progress/TASK-20260521-004-upstream-credential-entry-and-official-account-clarity.md`
- `tasks/backlog/TASK-20260521-005-02-official-account-vector-api-eradication-boundary.md`

## 输出

- 更新后的 `tasks/index.md`
- 补充边界说明后的历史任务文档
- 完整记录本轮哪些任务被从现役索引移除、哪些仅收紧口径

## 影响范围

- `tasks/` 本地任务体系
- 历史任务的检索口径
- 当前控制台能力退役后的任务索引准确性

## 依赖

- 父任务 [TASK-20260521-005](TASK-20260521-005-console-feature-retirement-and-vector-scope-prune.md) 的下线口径
- 已完成的只读审计结论

## 风险

- 若把历史实现任务直接删掉，会丢失参考证据和回归线索。
- 若只改 `tasks/index.md` 不改任务正文，后续仍可能从单个任务文件误读现行边界。

## 验收标准

- `tasks/index.md` 不再把已下线或准备删除的控制台能力挂在当前优先级或现役产品面。
- 需要保留的 `官方账号` 与 `向量 API` 相关任务补充“控制台已下线、后端/API 暂保留”边界。
- 任务正文与索引口径一致，不再把 `realtime/live`、旧 `account pool`、`provider-sites/reference-gap/capability matrix` 页面写成当前功能主入口。

## 测试边界

- 执行 `git diff -- tasks/index.md tasks/in-progress/TASK-20260521-005-console-feature-retirement-and-vector-scope-prune.md tasks/in-progress/TASK-20260521-005-03-task-index-retirement-and-boundary-cleanup.md` 复核变更范围。
- 只做文档一致性检查，不运行代码测试。

## 当前状态

进行中
