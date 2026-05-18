# REQ-20260516-013 OpenAI Fine-tuning Events/Checkpoints 本地 Lineage 列表

## 背景

OpenAI Fine-tuning API 暴露 `GET /v1/fine_tuning/jobs/{fine_tuning_job_id}/events` 与 `GET /v1/fine_tuning/jobs/{fine_tuning_job_id}/checkpoints`，两者均返回 `object=list` 的分页列表，并支持 `after`、`limit` 查询参数。

当前项目已实现 Fine-tuning create/list/get/cancel，但 events/checkpoints 仍作为 `accepted-exceptions.json` 中的未公开缺口，导致 ingress、conformance、provider catalog 和公开文档的能力边界不一致。项目已有 `gateway_async_resource.metadata_json.events`、fine-tuned model 注册 metadata 和 tuning response payload，可以先提供当前 DistributedKey 下 gateway-tracked tuning job 的本地 lineage 视图。

## 目标

- 暴露 OpenAI-compatible `GET /v1/fine_tuning/jobs/{jobId}/events`。
- 暴露 OpenAI-compatible `GET /v1/fine_tuning/jobs/{jobId}/checkpoints`。
- 返回当前 DistributedKey 下指定 tuning job 的本地 lineage list envelope，支持 `after` 与 `limit`。
- events 从 `gateway_async_resource.metadata_json.events` 派生，补齐 OpenAI-style `fine_tuning.job.event` item。
- checkpoints 从已完成 tuning 的本地 response/metadata 中的 `fine_tuned_model`、`registered_model_name` 或 checkpoint metadata 派生，无法证明存在 checkpoint 时返回空列表。
- 回收 `openai-unexposed-list-events-checkpoints` accepted exception，并同步 provider catalog、public docs、OpenAPI 与审计报告。

## 非目标

- 不在本切片主动调用真实 OpenAI 上游 events/checkpoints passthrough。
- 不实现 pause/resume、checkpoint permissions、grader/integrations 全量能力。
- 不把 OpenAI-compatible 第三方站点扩展为 Fine-tuning 对象生命周期通用替代。
- 不发起真实 fine-tuning 训练 smoke，避免成本和耗时。

## 范围

- OpenAI ingress controller：`/v1/fine_tuning/jobs/{jobId}/events`、`/v1/fine_tuning/jobs/{jobId}/checkpoints`。
- Interop semantics：`TranslationOperation`、`GatewayRequestFeatureService`、`GatewayRequestSemantics`、`CanonicalExecutionPlan`。
- Async lifecycle executor 与 `GatewayAsyncResourceService` 本地 list 构造。
- Conformance matrix、accepted exceptions、provider catalog、公开文档与 public OpenAPI。

## 风险

- 本地 lineage events 不等于 OpenAI 上游完整事件历史，必须在文档中标注。
- checkpoint 只能在 gateway 已登记 fine-tuned model 或 metadata 明确存在时派生，不能伪造训练过程中的全部 checkpoint。
- jobId 必须严格校验 DistributedKey 归属，避免跨租户枚举事件和模型信息。
- list cursor 不能泄漏跨租户对象；找不到 cursor 时采用空页，保持防推断边界。

## 验收标准

- controller tests 覆盖 events/checkpoints endpoint 的 query 参数传递。
- service tests 覆盖 events list、cursor/limit、checkpoint empty 和 completed checkpoint 派生。
- interop tests 覆盖 normalizePath、extractPathParams、operation 与 `STORED_LINEAGE`。
- conformance matrix 包含两个 endpoint，`accepted-exceptions.json` 不再包含 `openai-unexposed-list-events-checkpoints`。
- provider catalog 与 public docs 不再声明 Fine-tuning events/checkpoints 未暴露，而是声明当前为 gateway local lineage。

## 测试边界

- 不依赖真实 OpenAI key。
- 不调用上游 OpenAI API。
- 以单元测试和 conformance fixture 校验本地行为、文档事实源和 OpenAPI snapshot。

## 官方参考

- OpenAI Fine-tuning events API Reference：`https://developers.openai.com/api/reference/resources/fine_tuning/subresources/jobs/methods/list_events`
- OpenAI Fine-tuning checkpoints API Reference：`https://developers.openai.com/api/reference/resources/fine_tuning/subresources/jobs/subresources/checkpoints/methods/list`

## 关联任务

- [TASK-20260516-013](../../tasks/done/TASK-20260516-013-openai-fine-tuning-events-checkpoints-local-lineage.md)
- [TASK-20260514-022 OpenAI Fine-tuning 全生命周期](../../tasks/backlog/TASK-20260514-022-openai-fine-tuning-full-lifecycle.md)
- [TASK-20260514-014 OpenAI 官方资源族覆盖差距补齐](../../tasks/backlog/TASK-20260514-014-openai-resource-family-coverage-gap.md)

## 当前状态

Done。已暴露 Fine-tuning events/checkpoints 本地 lineage endpoint，并回收对应 accepted exception、provider catalog、public docs 与 OpenAPI 事实源。
