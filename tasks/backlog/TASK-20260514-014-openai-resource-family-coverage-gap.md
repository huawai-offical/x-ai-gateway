# TASK-20260514-014 OpenAI 官方资源族覆盖差距补齐

状态：Backlog  
优先级：High  
类型：子任务  
父任务：[REQ-20260514-008](../../docs/requirements/REQ-20260514-008-openai-api-compatibility-deep-audit.md)  
上游来源：[REP-20260514 OpenAI API 完整兼容性深度审计](../../docs/reports/REP-20260514-openai-api-compatibility-deep-audit.md)

## 背景

OpenAI 官方 API Reference 已覆盖 Vector Stores、Evals、Containers、Realtime calls、Administration、Conversations、Chat Completions 对象生命周期、Fine-tuning events/checkpoints/pause/resume、Batch list、Models delete 等资源面。当前项目只覆盖其中一部分基础路径。

## 目标

- 建立 OpenAI 官方资源族覆盖矩阵，逐项标注 `Supported`、`Partial`、`Missing`、`Out of scope`。
- 对缺失资源族拆分实现顺序：先补客户常用对象生命周期，再补管理/企业 API。
- Batch list 已由 `TASK-20260516-011` 补齐，Models delete 的 gateway registry 边界已由 `TASK-20260516-012` 补齐，Fine-tuning events/checkpoints 本地 lineage 已由 `TASK-20260516-013` 补齐；继续为 Fine-tuning pause/resume/permissions、Models upstream owner-role delete passthrough、Conversations/Items、Vector Stores、Containers、Evals、Realtime calls 给出实现或明确非目标声明。
- Provider catalog 不再把 OpenAI Direct 误标为全量无缺口。

## 非目标

- 不在一个任务内一次性实现所有资源族代码。
- 不把本项目自身 Admin Console 等同于 OpenAI Administration API。

## 输入

- 官方 OpenAI API Reference 资源导航。
- `GatewayRequestFeatureService`、`AsyncLifecycleGatewayResourceExecutor`、OpenAI resource controllers、Endpoint conformance matrix。
- 当前 provider catalog 与 public OpenAPI。

## 输出

- OpenAI resource family coverage matrix。
- 资源族实现拆分任务或 out-of-scope 决策。
- Batch list、Models delete gateway registry 边界与 Fine-tuning events/checkpoints 本地 lineage 已完成实现切片；继续补齐 Fine-tuning pause/resume/permissions 与 Models upstream owner-role delete passthrough 的明确任务边界。
- Provider catalog `unsupportedFeatures` 与文档声明更新。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/`
- `src/test/resources/conformance/`
- `src/main/resources/provider-catalog.json`
- `docs/public-api-compatibility.md`

## 依赖

- 官方 OpenAI key 与可用组织权限，用于真实 smoke 分类。
- 对对象生命周期数据模型的存储策略决定：本地编排、上游 passthrough、或混合 lineage。

## 风险

- Administration API 涉及组织级权限和敏感数据，不应默认暴露给普通用户。
- Vector Stores、Containers、Evals 可能引入大对象、文件内容和运行成本，需要配额、审计和清理策略。

## 验收标准

- 每个官方资源族都有明确状态与任务归属。
- 已实现资源族和未实现资源族在 provider catalog、public docs、conformance matrix 中一致。
- 对 Batch/Fine-tuning/Models 的已知缺口不再只停留在 accepted exception。

## 测试边界

- 静态 coverage matrix test。
- Endpoint conformance matrix 更新。
- 新增资源入口采用 controller/service 单测；真实 smoke 在缺 key 或缺权限时必须输出 skipped reason。

## 关联文档

- [REQ-20260514-008](../../docs/requirements/REQ-20260514-008-openai-api-compatibility-deep-audit.md)
- [REP-20260514 OpenAI API 完整兼容性深度审计](../../docs/reports/REP-20260514-openai-api-compatibility-deep-audit.md)

## 下游细分任务

- [TASK-20260514-020 OpenAI Audio、Images、Embeddings、Moderations 参数 parity](TASK-20260514-020-openai-audio-images-embeddings-moderations-parity.md)
- [TASK-20260514-021 OpenAI Files、Uploads、Batches、Models 对象生命周期](TASK-20260514-021-openai-files-uploads-batches-models-lifecycle.md)
- [TASK-20260514-022 OpenAI Fine-tuning 全生命周期](TASK-20260514-022-openai-fine-tuning-full-lifecycle.md)
- [TASK-20260514-023 OpenAI Vector Stores 全栈兼容](TASK-20260514-023-openai-vector-stores-full-stack.md)
- [TASK-20260514-024 OpenAI Containers 与 Code Interpreter 文件](TASK-20260514-024-openai-containers-code-interpreter-files.md)
- [TASK-20260514-025 OpenAI Videos API 兼容面](TASK-20260514-025-openai-videos-api-parity.md)
- [TASK-20260514-026 OpenAI Evals、Graders 与 Runs API](TASK-20260514-026-openai-evals-graders-runs.md)
- [TASK-20260514-027 OpenAI Skills API 与工具分发](TASK-20260514-027-openai-skills-api-tool-distribution.md)
- [TASK-20260514-028 OpenAI Administration API 权限隔离与只读优先](TASK-20260514-028-openai-administration-api-boundary.md)

## 已完成切片

- [TASK-20260516-011 OpenAI Batches List Envelope 与本地游标分页](../done/TASK-20260516-011-openai-batches-list-envelope.md)：OpenAI Direct `GET /v1/batches` 已从 accepted exception 转为本地 lineage list 能力。
- [TASK-20260516-012 OpenAI Models Delete 与 Fine-tuned Model 删除边界](../done/TASK-20260516-012-openai-models-delete-finetuned-boundary.md)：OpenAI Direct `DELETE /v1/models/{model}` 已转为当前 DistributedKey 下 gateway-registered fine-tuned model registry delete。
- [TASK-20260516-013 OpenAI Fine-tuning Events/Checkpoints 本地 Lineage 列表](../done/TASK-20260516-013-openai-fine-tuning-events-checkpoints-local-lineage.md)：OpenAI Direct `GET /v1/fine_tuning/jobs/{jobId}/events` 与 `GET /v1/fine_tuning/jobs/{jobId}/checkpoints` 已转为当前 DistributedKey 下 gateway-tracked tuning job 的本地 lineage list。
