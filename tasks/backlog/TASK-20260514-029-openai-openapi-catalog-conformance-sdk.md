# TASK-20260514-029 OpenAI OpenAPI、Catalog、Conformance 与 SDK 事实源统一

状态：Backlog  
优先级：Medium  
类型：子任务  
父任务：[TASK-20260514-016](TASK-20260514-016-openai-full-api-coverage-parent.md)  
上游来源：[TASK-20260514-015](TASK-20260514-015-openai-openapi-conformance-truth-source-hardening.md)

## 背景

当前 public OpenAPI、provider catalog、conformance accepted exceptions 和实际 controller 覆盖面不一致。全量覆盖需要把公开事实源和执行事实源收敛到同一套矩阵。

## 目标

- 建立 OpenAI endpoint coverage matrix 的机器可读文件。
- 从 coverage matrix 派生 public OpenAPI、provider catalog unsupportedFeatures、docs bundle 和 conformance fixtures。
- 更新 SDK examples，区分 OpenAI Direct native、OpenAI-compatible Generic 和本项目自定义 API。
- 在门户展示兼容等级，不再只写“OpenAI-compatible”。

## 非目标

- 不直接实现缺失 API。
- 不用文档伪装已实现能力。

## 输入

- `docs/openapi/public-openapi.json`
- `src/main/resources/provider-catalog.json`
- conformance fixtures 和 accepted exceptions。
- 所有 OpenAI 子任务状态。

## 输出

- Coverage matrix source。
- 自动或半自动生成的 OpenAPI/catalog/docs/conformance 更新。
- SDK examples 与客户文档。

## 影响范围

- docs、provider catalog、public API bundle、portal docs、tests。

## 依赖

- `TASK-20260514-030` 横切协议字段。
- 各子任务完成状态。

## 风险

- 手写多份事实源会再次漂移。
- 公开未稳定 API 会形成错误兼容承诺。

## 验收标准

- public OpenAPI 与 controller 覆盖差异可解释。
- Catalog unsupportedFeatures 不为空且与 coverage matrix 一致。
- Conformance accepted exceptions 都有关联任务或 out-of-scope 决策。

## 测试边界

- Docs bundle tests。
- Provider catalog loader tests。
- Coverage matrix consistency tests。

## 已完成切片

- [TASK-20260515-008 OpenAI Chat 参数兼容证明、公开文档与 SDK 示例](../done/TASK-20260515-008-openai-chat-conformance-docs-sdk-evidence.md)：先完成 Chat 参数证明切片，新增参数级 parity matrix、public OpenAPI Chat request schema、runtime docs bundle typed parameter 说明、JavaScript advanced 示例和漂移防护测试。

## 剩余切片

- 全量 OpenAI coverage matrix 仍需覆盖 Chat 以外的 Responses、Files/Uploads/Batches、Fine-tuning、Vector Stores、Realtime、Administration 等资源族，并继续推动 public OpenAPI、catalog unsupportedFeatures、conformance accepted exceptions 和 SDK examples 的统一派生。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
