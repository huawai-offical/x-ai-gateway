# TASK-20260518-006-02 Async resource service 非核心方法删除

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260518-006](TASK-20260518-006-non-core-api-code-eradication.md)
上游来源：[REQ-20260518-006](../../docs/requirements/REQ-20260518-006-non-core-api-code-eradication.md)

## 背景

`GatewayAsyncResourceService` 仍曾承载 OpenAI Batch、Fine-tuning/Tuning、Anthropic Message Batch 和 Google native batch view 的历史方法。公开入口删除后，这些内部方法不能继续作为 legacy 兼容层保留。

## 目标

- 删除 Batch/Tuning/Anthropic Message Batch create/get/list/cancel/status sync 方法。
- 删除 tuning event/checkpoint、fine-tuned model registry、Google native batch view 等 helper。
- 保留 Upload、Response、Conversation、Video、Music、Vector Store 与 Vector Store File Batch。

## 非目标

- 不删除对话、tools、RAG/file_search 所需的 async resource。
- 不做旧数据迁移或兼容转换。

## 输入

- `GatewayAsyncResourceService`
- 非核心 controller 删除后的编译错误和测试失败

## 输出

- 精简后的 async resource service。
- 删除的 fine-tuned model registry/delete service 和相关 tests。

## 影响范围

- `gateway/core/resource`
- `gateway/core/catalog`
- `protocol/ingress/openai`
- `protocol/ingress/anthropic`
- `protocol/ingress/google`

## 依赖

- 类型和 policy 清理完成。

## 风险

- Upload 本地完成会产出 Gateway File，测试需要真实落盘 stub 证明该路径仍可用。

## 验收标准

- Batch/Tuning/Message Batch 方法不再存在。
- Gemini local upload、Gateway File、public resource 定向测试通过。

## 测试边界

- `GatewayAsyncResourceServiceTests`
- `GatewayAsyncResourceCanonicalizerTests`
- `GatewayPublicResourceServiceTests`
- `GatewayFileServiceTests`

## 当前状态

- 2026-05-19：已完成并由父任务统一验证。
