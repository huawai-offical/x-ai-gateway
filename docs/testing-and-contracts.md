# 测试与契约说明

## 测试分层

### 1. 现有 controller / service 测试

- 覆盖三套协议 controller、后台 controller、route / observability / analytics service。
- 主要用于快速回归字段结构和入参解析。

### 2. 协议编码契约测试

- 文件：`src/test/java/com/prodigalgal/xaigateway/protocol/GatewayProtocolEncoderContractTests.java`
- 目标：
  - 锁定 OpenAI / Anthropic / Gemini non-stream 编码结构
  - 锁定 OpenAI Responses 的 reasoning / tool call SSE 结构
  - 锁定 usage、cached tokens、reasoning tokens 的映射

### 3. SpringBoot smoke / 集成测试

- 文件：`src/test/java/com/prodigalgal/xaigateway/integration/GatewayEndToEndSmokeTests.java`
- 特点：
  - 真实 Spring 容器
  - H2
  - 可替换的内存 affinity store
  - fake chat runtime
- 覆盖：
  - OpenAI / Anthropic / Gemini happy path
  - request / usage 持久化
  - observability summary
  - prefix affinity 复用与失败失效
  - route preview 与 translation explain 一致性
  - admin POST 审计日志

## 运行命令

后端：

```bash
./gradlew test
```

前端：

```bash
cd web
bun run test -- --run
bun run check
```

## 当前契约重点

- OpenAI Chat Completions
  - `choices[].message.content`
  - `finish_reason`
  - `prompt_tokens_details.cached_tokens`
  - `completion_tokens_details.reasoning_tokens`
- OpenAI Responses
  - `response.created / in_progress / completed`
  - `response.reasoning_summary_text.delta`
  - `response.function_call_arguments.delta / done`
- Anthropic Messages
  - `stop_reason`
  - `tool_use`
  - `cache_read_input_tokens`
  - `cache_creation_input_tokens`
- Gemini GenerateContent
  - `functionCall`
  - `usageMetadata.cachedContentTokenCount`
  - `usageMetadata.thoughtsTokenCount`

## 当前 smoke 验收点

- `request_log` 与 `usage_record` 在 happy path 下会落库
- `usage_record.completeness` 为 `FINAL`
- `admin/observability/summary` 会汇总 route / usage / cache 数据
- affinity 失效后，后续 preview 不再走 `PREFIX_AFFINITY`
