# x-ai-gateway

`x-ai-gateway` 是一个基于 `Spring Boot 3.5.7 + Spring AI 1.1.4` 的多协议、多厂商 AI 网关，当前已具备：

- OpenAI / Anthropic / Gemini 三套聊天协议入口
- 路由解释、translation explain、provider sites / capability matrix 后台
- Prompt cache / affinity / upstream cache reference 观测
- request / usage / audit 持久化结构与基础指标
- 协议编码分层、契约测试、SpringBoot smoke / 集成测试

## 技术栈

- 后端：`Java 25`、`Spring Boot`、`WebFlux`、`Spring AI`、`JPA`、`Liquibase`、`Micrometer`
- 存储：`PostgreSQL`、`Redis`
- 前端：`React 19`、`TypeScript`、`Vite 8`、`Bun`

## 本地启动

默认本地配置在 [application.yaml](/D:/WorkSpace/Project/ai/x-ai-gateway/src/main/resources/application.yaml) 和 [application-local.yaml](/D:/WorkSpace/Project/ai/x-ai-gateway/src/main/resources/application-local.yaml)。

先准备依赖：

- PostgreSQL：默认 `jdbc:postgresql://192.168.154.143:5432/hope_ircs?connectionTimeZone=UTC`
- Redis：默认 `192.168.154.143:6379`
- 可选环境变量：
  - `DB_URL`
  - `DB_USERNAME`
  - `DB_PASSWORD`
  - `REDIS_HOST`
  - `REDIS_PORT`
  - `REDIS_PASSWORD`
  - `GATEWAY_ENCRYPTION_KEY`

启动后端：

```bash
./gradlew bootRun
```

启动前端：

```bash
cd web
bun install
bun run dev
```

## 测试与验证

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

## 关键文档

- [本地运行手册](/D:/WorkSpace/Project/ai/x-ai-gateway/docs/local-runbook.md)
- [测试与契约说明](/D:/WorkSpace/Project/ai/x-ai-gateway/docs/testing-and-contracts.md)
- [前端说明](/D:/WorkSpace/Project/ai/x-ai-gateway/web/README.md)
