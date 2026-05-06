# TASK-20260506-020 云端 Request Filter 高级规则、审计与 UI

状态：Backlog  
优先级：High  
来源：[REP-20260506 参考项目功能深度再复核](../../docs/reports/REP-20260506-reference-feature-depth-recheck.md)  
关联需求：[REQ-20260506-012 参考项目功能深度复核与任务再生成](../../docs/requirements/REQ-20260506-012-reference-depth-recheck-task-generation.md)

## 背景

当前云端 request filter 已支持 canonical chat text 的 replace/remove/mask，并能按 client family 限制。对照 `cli_proxy-master` 的过滤 UI 和运行时体验，仍缺 JSON path/tool schema/file metadata 过滤、策略审计、管理端配置、trace 命中展示和实时观测。

## 目标

- 将 request filter 从配置项推进到可运营的云端策略能力。
- 支持 JSON path、tool schema、providerExtensions、file metadata 等结构化过滤。
- 在 Admin UI、request trace 和 system event 中展示规则命中与跳过原因。

## 范围

- Filter rule 持久化、版本、启停和权限隔离。
- replace/remove/mask/deny/redact 等动作扩展。
- JSON path 与 schema-aware 过滤。
- 策略变更审计、命中审计和 trace 联动。
- Admin UI 与前端测试。

## 非目标

- 不把 filter 用于绕过 provider 安全策略。
- 不默认记录明文 secret 或完整敏感请求体。
- 不影响已发出的长连接请求。

## 验收标准

- 后端规则模型支持结构化过滤并有迁移。
- 管理端可创建、预览、启停、回滚 filter rule。
- request trace 可展示 applied/skipped rule ids、action 和脱敏摘要。
- 单元测试覆盖 JSON path、tool schema、file metadata、权限隔离和非法规则降级。

## 实现记录

待处理。

## 测试/验证

待处理。

## 遗留问题

待处理。
