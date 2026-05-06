# REQ-20260506-016 云端 Request Filter 高级规则、审计与 UI

状态：Done  
日期：2026-05-06  
关联任务：

- [TASK-20260506-020 云端 Request Filter 高级规则、审计与 UI](../../tasks/done/TASK-20260506-020-cloud-request-filter-audit-ui.md)

## 背景

当前云端 request filter 已支持 canonical chat text 的 replace/remove/mask，并可按 client family 限制。对照参考项目的云端过滤体验，仍缺 JSON path、tool schema、file metadata、providerExtensions 等结构化过滤，以及命中审计、跳过原因、trace 展示和管理端配置事实源。

## 目标

- 将 request filter 从纯文本替换推进到结构化规则能力。
- 支持 `replace`、`remove`、`mask`、`deny`、`redact` 等动作。
- 记录 applied/skipped rule ids、action、target 和脱敏摘要，便于 trace 与 system event 后续消费。
- 提供后端可验证的规则预览/运行时基础，前端 UI 可在后续基础上接入。

## 范围

- 后端 filter runtime、规则模型和测试。
- JSON path、tool schema、file metadata、providerExtensions 过滤。
- 策略命中审计和跳过原因摘要。
- 本地任务/需求/文档回写。

## 非目标

- 不绕过 provider 自身安全策略。
- 不默认保存完整敏感请求体或明文 secret。
- 不在本轮完成复杂可视化编辑器。
- 不影响已经建立的 Realtime 长连接请求。

## 方案

1. 将 `TASK-020` 移入 `in-progress`。
2. 复用现有 request filter 运行时入口，扩展结构化目标匹配。
3. 引入可序列化的 rule hit summary，后续 trace/UI 直接读取。
4. 增加单元测试覆盖 JSON path、tool schema、file metadata、providerExtensions、deny 和非法规则降级。

## 风险

- JSON path 过滤如果过度复杂，容易引入不可控规则语义；本轮先支持明确、可测的点路径和数组通配。
- 规则审计必须脱敏，不能把原始 secret 写入日志或 metadata。
- 前端工作区已有大量改动，本轮优先完成后端事实源与测试，避免扩大冲突面。

## 验收标准

- 后端规则模型支持结构化过滤。
- 运行时能输出 applied/skipped rule ids、action、target 和脱敏摘要。
- 单元测试覆盖 JSON path、tool schema、file metadata、providerExtensions、deny 和非法规则降级。
- 文档和任务状态完成回写。

## 实现结果

- 扩展 `CloudCliRequestFilterRule`，新增 `target` 与 `path`，在保持旧构造器兼容的前提下支持结构化过滤目标。
- 扩展 `CloudCliRequestFilterAction`，新增 `REDACT` 与 `DENY`。
- 新增 `CloudCliRequestFilterHit` 与扩展后的 `CloudCliRequestFilterResult`，运行时可返回 `hits`、`denied`、`denyRuleId` 和 `denyReason`。
- `CloudCliRequestFilterService` 支持 canonical message text、`provider_extensions`、`tool_schema`、`file_metadata` 与 `json_path` 目标，覆盖 `replace`、`remove`、`mask`、`redact`、`deny` 动作。
- `GatewayProperties.Cli.Rule` 支持 `target` 与 `path` 配置，配置文件可直接声明结构化规则。
- `GatewayChatExecutionService` 在普通请求和 stream 请求中统一执行过滤结果校验，`DENY` 命中时拒绝继续转发，并在 `x_ai_gateway_filter` 中写入命中摘要。
- 新增 [cloud-cli-request-filter](../cloud-cli-request-filter.md) 使用说明，记录规则目标、路径语义、审计字段与安全边界。

## 测试/验证

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.cli.CloudCliRequestFilterServiceTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.cli.CloudCliRequestFilterServiceTests"`

## 遗留问题

- 本轮完成后端规则事实源、运行时审计和 trace metadata；复杂 Admin 可视化编辑器未在本轮扩展，后续可基于 `target/path/action/hits` 契约接入。
- 结构化路径采用受控点路径和 `[*]` 数组通配，未引入完整 JSONPath 引擎，避免规则语义不可控。
