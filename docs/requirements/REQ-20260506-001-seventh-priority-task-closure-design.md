# REQ-20260506-001 第七批高优先级任务闭环设计

状态：Done
创建日期：2026-05-06
关联任务：

- [TASK-20260505-004 Ops/Maintenance/Release 真实演练证据补齐](../../tasks/done/TASK-20260505-004-ops-maintenance-release-real-drill-evidence.md)
- [TASK-20260505-005 Ollama Native document/file 真支持](../../tasks/done/TASK-20260505-005-ollama-native-document-file-support.md)
- [TASK-20260501-000 x-ai-gateway 对标差距增强总览](../../tasks/done/TASK-20260501-000-gap-enhancement-overview.md)

## 背景

当前 High backlog 中仍有真实 provider、支付、Passkey/WebAuthn、Ops 演练、Ollama document/file 等任务。为在本轮形成可验证进度，优先选择两个上轮 X-263 审计直接拆出的实现缺口，并同步闭环父级差距总览任务，避免任务树长期挂着总览型 backlog。

## 目标

- Ops/Maintenance/Release 至少具备可重放演练证据、artifact/checksum、审计调用与升级失败自动回滚测试。
- Ollama Native 对文本类 document/file 有明确 capability gate、可控文本注入策略和不支持类型标准化错误。
- 差距增强总览父任务更新子任务状态和剩余 backlog，归档为 Done。

## 范围

### Ops 演练证据

- 扩展维护运行测试，覆盖 `PRECHECK`、`BACKUP`、`RESTORE_DRY_RUN`、`UPGRADE_CHECK`、`ROLLBACK_PLAN`。
- 补充升级失败自动回滚测试，验证 rollback plan 生成和事件发布。
- 文档化本地复现命令和 dry-run/真实 artifact 边界。

### Ollama document/file

- 对文本类 file/document 输入读取本地 `GatewayFileService` 内容，并注入到 Ollama prompt。
- 对二进制或不支持 MIME 类型返回标准化错误。
- 补充 runtime 测试，验证文本注入、错误码和 usage/error parity。

### 总览父任务

- 更新 `TASK-20260501-000` 子任务路径、完成状态和剩余项。
- 标记父任务 Done，剩余实现继续由独立 backlog 承接。

## 非目标

- 不接入真实生产升级系统。
- 不实现完整 OCR、复杂文档解析或二进制文件理解。
- 不在本轮实现 Passkey/WebAuthn 浏览器 ceremony。
- 不把真实 provider/payment/OAuth secret 写入仓库。

## 风险

- Ops dry-run 与真实执行仍有差异，需要文档保留边界。
- Ollama 本地模型对文件能力不统一，需要通过 MIME/capability gate 保守启用。
- 父任务归档不能误表示所有子任务都完成，只表示“总览和拆分”闭环。

## 验收标准

- 三个任务文件都进入 Done 并回写实现结果、验证情况、遗留问题。
- 定向后端测试通过。
- 文档索引和任务索引更新，无断链到旧 `backlog`/`in-progress` 路径。

## 实现结果

- Ops 演练证据已补齐 maintenance run dry-run/真实 backup artifact 测试、审计断言和升级失败自动回滚测试。
- Ollama Native 已支持文本类 `gateway://`/`data:` document/file prompt 注入，并对远程 URL、PDF/二进制文件返回标准化不支持错误。
- 差距增强总览父任务已更新子任务状态和剩余 backlog，并归档为 Done。
- 新增文档：[Ops/Maintenance/Release 演练证据](../operations-drill-evidence.md)、[Ollama Native 文本 document/file 支持](../ollama-document-file-support.md)。

## 验证情况

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.MaintenanceRunServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.PlatformChangePlanServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OllamaGatewayChatRuntimeTests"
```

结果：通过。

## 遗留问题

- Ops 自动回滚仍是本地 mock 演练证据，真实部署系统接入留给生产部署任务。
- Ollama 文件支持限定在文本类输入；PDF/OCR/Office 解析不在本轮范围。
- 仍有 High backlog：真实媒体 provider、真实支付渠道、真实 Realtime provider WebSocket、Passkey/WebAuthn。
