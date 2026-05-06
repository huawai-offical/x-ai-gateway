# REQ-20260506-002 第八批高优先级任务闭环设计

状态：Done
创建日期：2026-05-06
关联任务：

- [TASK-20260501-017 真实 Video/Music Provider Executors 与产物闭环](../../tasks/done/TASK-20260501-017-real-media-provider-executors.md)
- [TASK-20260501-021 真实支付渠道、签名验签与对账](../../tasks/done/TASK-20260501-021-payment-real-provider-reconcile.md)
- [TASK-20260501-024 真实 Realtime Provider WebSocket Adapter](../../tasks/done/TASK-20260501-024-realtime-real-provider-websocket.md)

## 背景

当前剩余 High backlog 中，`TASK-20260501-017`、`021`、`024` 都已有本地骨架或 mock 运行态，适合本轮推进到“真实 provider 可接入、mock 可验证”的闭环。`TASK-20260501-026` 涉及 WebAuthn 浏览器 ceremony、RP ID、credential 存储、前端设置页和安全审计，范围更大，保留到下一批单独推进。

## 目标

- Video/Music 异步资源支持上游 provider create/get/cancel，并保留本地 fallback contract。
- Payment 支持真实 Stripe 签名 webhook 与通用 HMAC 签名 webhook，覆盖幂等、签名错误、金额不一致。
- Realtime adapter 从模拟运行态推进到 provider WebSocket 事件映射与 conformance 可验证状态。

## 范围

### Media Provider Executors

- 增加上游 provider 模式，通过 capability matrix 选择 OpenAI-style video/music endpoint。
- `create/get/cancel` 支持上游对象映射、状态同步、metadata lineage。
- 保留无 provider 时的本地 async task contract。

### Payment Provider

- 新增 provider webhook 请求模型。
- 支持 Stripe `Stripe-Signature` HMAC 校验。
- 支持通用 `sha256=` HMAC webhook，作为国内支付 provider 适配基础。
- 统一订单状态机、幂等键、金额/币种校验和审计事件。

### Realtime Provider WebSocket

- OpenAI Realtime 与 Gemini Live adapter 使用 WebSocket transport 语义。
- provider event 映射包含 connect、frame、heartbeat、close。
- conformance 能识别真实 provider websocket frames。

## 非目标

- 不保存真实支付密钥到仓库。
- 不在本轮接入真实外部支付沙箱账号。
- 不实现浏览器 WebAuthn。
- 不实现长连接真实网络拨号的生产连接池。

## 风险

- 真实 provider API 细节可能随供应商变化，需要保持 OpenAI-style/generic HMAC 兼容层。
- WebSocket adapter 当前以 provider event 映射和 conformance 为闭环，真实持久连接池仍需后续强化。
- 支付 webhook secret 本轮通过请求传入用于本地 smoke，生产密钥存储需接入加密配置中心。

## 验收标准

- 三个任务均进入 Done，并回写实现结果、验证情况、遗留问题。
- 定向后端测试通过。
- 本地说明文档覆盖 media/payment/realtime 的复现方式和边界。

## 实现结果

- `TASK-20260501-017`：Video/Music async resource 在显式上游模式下支持 OpenAI-style provider create/get/cancel，本地模式继续保留原有 contract 与终态保护；capability truth 与 execution support matrix 已补齐 Video/Music/Async Task 能力。
- `TASK-20260501-021`：新增 provider webhook 请求模型与 admin endpoint，支持 Stripe 签名验签、通用 HMAC provider webhook、统一幂等键、金额/币种/provider 校验和审计事件。
- `TASK-20260501-024`：OpenAI Realtime 与 Gemini Live adapter 暴露 WebSocket transport、provider URL、auth scheme、connect/frame/heartbeat/close 事件映射，并进入 conformance 检查。
- 已新增本地说明文档：[media-provider-executors](../media-provider-executors.md)、[payment-provider-webhooks](../payment-provider-webhooks.md)、[realtime-provider-websocket](../realtime-provider-websocket.md)。

## 验证情况

已通过目标测试：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.PaymentAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.LiveSessionServiceTests"
```

验证覆盖：

- 本地 Video/Music task fallback、终态保护、上游 create/get/cancel 路径与 metadata lineage。
- mock payment、Stripe signed webhook、generic HMAC webhook、重复通知、签名错误和金额不一致。
- mock WebSocket、Gemini Live metadata、OpenAI Realtime WebSocket conformance。

## 遗留问题

- Video/Music 本轮完成 OpenAI-style provider executor 与状态同步，真实产物下载到 gateway file storage、后台轮询 worker、usage 细化仍可后续增强。
- Payment 本轮完成 webhook 入账闭环，退款、争议、渠道主动对账和生产密钥加密配置仍可后续增强。
- Realtime 本轮完成 provider WebSocket 事件契约与 conformance，真实长连接池、二进制帧收发和 provider usage/error 归一仍可后续增强。
