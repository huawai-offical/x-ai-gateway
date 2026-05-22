# TASK-20260521-006-02 冗余协议接口与向量能力边界清理

## 任务类型

子任务 / 并行实施

## 背景

父任务：`tasks/in-progress/TASK-20260521-006-redundant-api-eradication-and-baseline-refresh.md`

这部分聚焦 `protocol/ingress`、OpenAPI、public docs 与旧协议边界的收口，优先判断哪些协议入口仍被主线依赖，哪些已经可以删除；其中 `vector stores`、`files` 与 Responses `file_search` 已确认保留。

## 目标

- 审视 protocol/public API 中的冗余入口。
- 判断旧兼容面和附属公共接口的去留，并实施可确认删除部分。

## 当前候选

- 已失去产品面意义的 `publicapi` 附属控制器
- 与旧 `Native Compatibility` / `Provider Reference Gap` 展示面直接耦合的协议说明接口
- 需要同步更正的文档口径：所有把 `vector stores` / `files` 写成候选删除面的描述

## 边界说明

- 保留公开 `/v1/vector_stores*`
- 保留 `OpenAiVectorStoresController`
- 保留 `OpenAiResponsesFileSearchBindingService`
- 保留 `OpenAiFilesController` 与 file 相关持久化能力
- 本子任务仅清理误导性文档口径和真正闲置的旧协议/说明入口

## 当前状态

进行中
