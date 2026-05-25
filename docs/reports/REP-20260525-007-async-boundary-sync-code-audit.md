# REP-20260525-007 异步边界同步代码排查报告

日期：2026-05-25  
状态：Done  
关联需求：`docs/requirements/REQ-20260525-007-async-boundary-sync-code-audit.md`

## 结论

项目当前不是端到端异步。更准确的描述是：`spring-boot-starter-webflux` 提供了 WebFlux 入口和 streaming 支撑，但核心非流式请求链路、资源执行器、JPA/Redis 访问、文件 I/O、部分 OAuth/运维调用仍是同步实现。

最高风险集中在公开网关热路径：

- `/v1/chat/completions`、`/v1/responses` 非流式链路在返回 `ResponseEntity` 前同步完成认证、路由、DB、上游 HTTP 和响应编码。
- `GatewayChatRuntime.execute(...)` 是同步接口，多个 runtime 在里面执行阻塞 HTTP。
- `GatewayResourceExecutor` 的 JSON/Binary 执行接口是同步 `ResponseEntity`，多个 executor 使用 `WebClient.block()` 或同步 SDK。
- 同步 JPA 与 `StringRedisTemplate` 已进入认证、路由、账号选择、限流/熔断等运行时路径。

## 扫描口径

执行过的主要扫描：

```powershell
rg -n "\.block\s*\(|Thread\.sleep\s*\(|HttpClient\.newHttpClient\(\)\.send|client\.send\(" src/main/java
rg -n "StringRedisTemplate|RedisTemplate|JpaRepository|CrudRepository|PagingAndSortingRepository|JavaMailSender|Files\.readAllBytes|Files\.write|Files\.copy|Files\.walkFileTree" src/main/java/com/prodigalgal/xaigateway/gateway src/main/java/com/prodigalgal/xaigateway/protocol src/main/java/com/prodigalgal/xaigateway/portal src/main/java/com/prodigalgal/xaigateway/admin/application
rg -n "return Mono\.fromCallable|subscribeOn\(Schedulers\.boundedElastic\)|subscribeOn\(" src/main/java
rg -n "XMLHttpRequest|async:\s*false|Atomics\.wait|while\s*\(" web/src
```

## 高风险发现

### 1. Chat/Responses 非流式公开热路径是同步执行

证据：

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsController.java:69` 返回 `ResponseEntity<?>`。
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsController.java:116` 到 `119` 在 controller 内直接调用 `gatewayChatExecutionService.executeGatewayResponse(...)` 并编码返回。
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesController.java:77` 返回 `ResponseEntity<?>`，`134` 到 `137` 同步调用 `executeGatewayResponse(...)`。
- `src/main/java/com/prodigalgal/xaigateway/admin/application/GatewayChatExecutionService.java:315` 到 `321` 直接调用 `runtime.execute(...)`。
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/GatewayChatRuntime.java:17` 定义同步 `CanonicalResponse execute(...)`。

影响：

- WebFlux event loop 线程可能被上游 HTTP、数据库、Redis、文件 I/O 或 SDK 调用阻塞。
- 高并发下容易出现延迟抖动、线程堆积和吞吐下降。

建议：

- 将 `GatewayChatRuntime.execute(...)` 改为 `Mono<CanonicalResponse> executeAsync(...)`，同步 legacy runtime 先用 `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` 包住。
- 将 `GatewayChatExecutionService.executeGatewayResponse(...)` 和公开 controller 非流式入口改为返回 `Mono<ResponseEntity<?>>`。
- 后续再逐步把上游调用替换成真正 reactive `WebClient` 或 async SDK。

### 2. OpenAI native/runtime 非流式上游调用使用同步 HTTP

证据：

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/OpenAiNativeGatewayChatRuntime.java:105` 到 `112` 的 `execute(...)` 会进入 raw/native HTTP 分支。
- `OpenAiNativeGatewayChatRuntime.java:553` 使用 `HttpClient.newHttpClient().send(...)` 执行 raw Chat Completions create。
- `OpenAiNativeGatewayChatRuntime.java:826` 使用 `HttpClient.newHttpClient().send(...)` 执行 native Responses create。
- `OpenAiNativeGatewayChatRuntime.java:627`、`899` 的 stream 分支也使用同步 `send(...)`，但外层 `Flux.using(...).subscribeOn(Schedulers.boundedElastic())` 已把读取放到 elastic 线程，风险低于非流式分支。
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/OllamaGatewayChatRuntime.java:64` 到 `73` 使用 `WebClient...bodyToMono(...).block()`。

影响：

- OpenAI-compatible、Responses native 和 Ollama 非流式请求会阻塞调用线程。
- stream 分支不会直接阻塞 event loop，但会占用 bounded elastic worker；高并发长流仍需容量控制。

建议：

- 非流式 raw/native HTTP 改为 `HttpClient.sendAsync(...)` 或 `WebClient.exchangeToMono(...)`。
- stream 分支保留 elastic 隔离时，需要显式限流和超时；更理想是用 reactive HTTP 客户端读取 SSE。

### 3. 资源执行器接口本身是同步形态

证据：

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/GatewayResourceExecutor.java:34` 到 `45` 定义同步 `ResponseEntity<JsonNode>` 和 `ResponseEntity<byte[]>`。
- `GatewayResourceExecutor.java:48` 到 `53` 只有 multipart 是 `Mono<ResponseEntity<JsonNode>>`。
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiEmbeddingsController.java:30` 到 `35` 直接返回同步 `ResponseEntity<JsonNode>`。
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/EmbeddingsGatewayResourceExecutor.java:84` 到 `89` 使用 `WebClient...block()`。
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/EmbedRerankNativeGatewayResourceExecutor.java:92` 到 `97` 使用 `WebClient...block()`。
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/GatewayOpenAiPassthroughService.java:225` 到 `230`、`243` 到 `246`、`258` 到 `263` 使用 `block()`。

影响：

- embeddings、rerank、audio/image/file passthrough、resource lifecycle 等公开资源接口存在同步阻塞。
- 由于接口定义同步，单个 executor 改写无法彻底修复，需要先改契约。

建议：

- 将 `GatewayResourceExecutor` JSON/Binary 契约改为 `Mono<ResponseEntity<...>>`。
- `GatewayResourceExecutionService` 统一以 reactive pipeline 编排 executor、trace、路由成功/失败记录。
- 不能立即 reactive 的同步 SDK 先隔离到 `boundedElastic`，并在任务中标记技术债。

### 4. 同步 JPA 与同步 Redis 已进入运行时路径

证据：

- `build.gradle` 同时引入 `spring-boot-starter-webflux`、`spring-boot-starter-data-jpa`、`spring-boot-starter-data-redis`。
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/GatewayResourceExecutionService.java:61` 到 `66` 是 `@Transactional` service，并注入 `UpstreamCredentialRepository`。
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/account/AccountSelectionService.java:108` 读取 `StringRedisTemplate` sticky account，`135` 写入 sticky account。
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/routing/RedisRoutingPolicyRuntimeStore.java` 使用同步 Redis 操作维护限流、熔断、锁、运行态列表。
- 多个 repository 继承 `JpaRepository`，属于同步持久化栈。

影响：

- 认证、路由、账号选择、运行态治理和观测写入都可能阻塞 WebFlux 线程。
- `RedisRoutingPolicyRuntimeStore` 中 `keys(...)` 类操作在数据量变大时还有 Redis 侧阻塞风险。

建议：

- 短期：对公开请求热路径的 JPA/Redis 调用用 `boundedElastic` 隔离，并明确事务边界。
- 中期：路由/认证/限流缓存迁移到 reactive Redis 或内存快照 + 异步回写。
- 长期：如果坚持端到端 reactive，数据库访问需要 R2DBC 或独立 worker/service 边界。

### 5. 文件与异步资源服务存在同步落盘、读全量和阻塞上游调用

证据：

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/file/GatewayFileService.java:221` 使用 `Files.write(...)`。
- `GatewayFileService.java:439`、`489`、`694` 使用 `Files.readAllBytes(...)`。
- `GatewayFileService.java:455` 到 `461` 使用 multipart `WebClient...block()` 同步上传。
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/resource/GatewayAsyncResourceService.java:848`、`860`、`909`、`3607` 使用 `block()`。
- `GatewayAsyncResourceService.java:3274`、`3375` 使用 `Files.write(...)`，`3720` 使用 `Files.readAllBytes(...)`。

影响：

- 文件上传/下载、Responses lifecycle、Uploads parts 等路径对大文件或远端慢响应敏感。
- 外层返回 `Mono` 的方法里，部分 `map(...)` 后续仍可能执行同步落盘/持久化。

建议：

- 文件写入改为 `DataBufferUtils.write(...)` 或在 elastic 线程执行。
- 文件下载尽量用 streaming `Resource`/`Flux<DataBuffer>`，避免 `readAllBytes`。
- 上游 lifecycle 调用改为 reactive `Mono`，不要在 service 内 `block()`。

## 中风险发现

### 1. Portal 社交 OAuth 链路表面返回 Mono，profile client 内部阻塞

证据：

- `src/main/java/com/prodigalgal/xaigateway/portal/api/PortalAuthController.java:169` 到 `183` OAuth callback 返回 `Mono`。
- `src/main/java/com/prodigalgal/xaigateway/portal/application/GenericOAuth2SocialOAuthProfileClient.java:83` 到 `90` 和 `179` 到 `186` 使用 `WebClient.block()`。
- `GoogleSocialOAuthProfileClient.java:80` 到 `99`、`GitHubSocialOAuthProfileClient.java:51` 到 `68`、`SocialOAuthJwksCache.java:45` 到 `50` 均有 `block()`。

影响：

- 登录/绑定路径低于公开推理热路径，但用户交互上仍会阻塞。

建议：

- 将 `SocialOAuthProfileClient` 改为返回 `Mono<SocialOAuthProfile>`，或短期用 elastic 包裹同步 profile client。

### 2. 管理端、smoke、运维拨测存在同步 HTTP

证据：

- `FunctionalProviderSmokeHttpClient.java:347`、`OpenAiDirectSmokeHttpClient.java:74`、`OpenAiDirectResourceSmokeHttpClient.java:151`、`CodexResponsesSmokeHttpClient.java:107`/`153` 使用 `HttpClient.send(...)`。
- `src/main/java/com/prodigalgal/xaigateway/admin/application/OpsTimelineService.java:116` 使用 `httpClient.send(...)`。
- `src/main/java/com/prodigalgal/xaigateway/admin/application/integrations/WebhookDispatcher.java:53` 到 `69` 使用 `WebClient.block()`。

影响：

- 多数属于后台管理或 smoke 路径，不是主网关流量，但会占用请求线程或调度线程。

建议：

- 管理端 live smoke 可保留同步但需隔离线程池和超时。
- Webhook dispatch 更适合改成异步队列或 reactive fire-and-record。

### 3. `Thread.sleep` 出现在公开等待语义中

证据：

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/resource/GatewayPublicResourceService.java:379` 到 `386` 使用 `Thread.sleep(...)`，等待上限由 `boundedWaitMillis(...)` 限制到 2000ms。

影响：

- 这是显式 wait API 语义，但仍会阻塞处理线程。

建议：

- 改为 `Mono.delay(...)` 式轮询，或将 wait endpoint 转为 async response。

## 低风险或排除项

- `src/test/java` 中大量 `.block()` 是测试等待 reactive 结果的常见写法，本轮不归为生产缺陷。
- 前端 `web/src` 未扫到同步 XHR、`async: false` 或 `Atomics.wait`。`localStorage/sessionStorage` 使用属于轻量 UI 状态读写，不构成本次“同步阻塞代码”主风险。
- `OpenAiNativeGatewayChatRuntime` stream 分支已使用 `subscribeOn(Schedulers.boundedElastic())`，不是 event loop 直接阻塞，但仍应纳入后续容量和超时治理。

## 后续修复任务

- `tasks/backlog/TASK-20260525-007-03-chat-runtime-reactive-boundary.md`
- `tasks/backlog/TASK-20260525-007-04-resource-executor-reactive-boundary.md`
- `tasks/backlog/TASK-20260525-007-05-blocking-infra-isolation-guardrails.md`

## 验证情况

- 已完成静态扫描和关键调用链人工复核。
- 本轮未修改业务代码，未运行自动化测试。

