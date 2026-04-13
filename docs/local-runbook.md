# 本地运行手册

## 环境要求

- `Java 25`
- `PostgreSQL 14+`
- `Redis 7+`
- `Bun 1.3.12`

## 默认配置

后端默认读取：

- [application.yaml](/D:/WorkSpace/Project/ai/x-ai-gateway/src/main/resources/application.yaml)
- [application-local.yaml](/D:/WorkSpace/Project/ai/x-ai-gateway/src/main/resources/application-local.yaml)

默认值：

- PostgreSQL：`jdbc:postgresql://localhost:5432/x_ai_gateway`
- Redis：`localhost:6379`
- Actuator 暴露：`health,info,metrics,prometheus,configprops`

## 启动步骤

1. 启动 PostgreSQL 与 Redis。
2. 在仓库根目录执行：

```bash
./gradlew bootRun
```

3. 前端开发环境执行：

```bash
cd web
bun install
bun run dev
```

## 常用入口

- OpenAI Chat Completions：`/v1/chat/completions`
- OpenAI Responses：`/v1/responses`
- Anthropic Messages：`/v1/messages`
- Gemini GenerateContent：`/v1beta/models/{model}:generateContent`
- 路由预演：`/admin/routing/preview`
- Translation Explain：`/admin/translation/explain`
- Observability Summary：`/admin/observability/summary`
- Dashboard Overview：`/admin/dashboard/overview`

## 常见排障

- `401/403`
  - 检查 DistributedKey 是否存在、前缀是否匹配、`secret_hash` 是否与请求 key 对齐。
- `当前 provider 候选无法满足请求特征`
  - 检查 `site_capability_snapshot` 与 `site_model_capability` 是否包含目标协议、模型和 feature。
- `Redis 连接失败`
  - 本地路由亲和依赖 Redis；若仅跑后端单测或 SpringBoot smoke，测试夹具会改用内存 affinity，不需要真实 Redis。
- `usage 统计不完整`
  - 查看 `usage_record.completeness` 是否为 `PARTIAL`，再结合 `cache_hit_log` 和 `request_log` 定位是 provider 未给终态 usage，还是请求被中断。

## 本轮新增观测

- `request_log`
- `usage_record`
- `audit_log`
- Micrometer 指标：
  - `gateway.request.total`
  - `gateway.request.duration`
  - `gateway.usage.total_tokens`
  - `gateway.usage.saved_input_tokens`
  - `gateway.cache.hit_tokens`
  - `gateway.cache.write_tokens`
