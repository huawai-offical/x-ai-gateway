# REQ-20260506-021 桌面 Companion、MCP/Skills/Session/Workspace 可行性评估

状态：Done  
日期：2026-05-06  
关联任务：

- [TASK-20260506-025 桌面 Companion、MCP/Skills/Session/Workspace 可行性评估](../../tasks/done/TASK-20260506-025-desktop-companion-local-workflow-evaluation.md)

## 背景

参考项目在桌面 companion、本机 CLI、MCP、Skills、Session、Workspace、托盘和云同步方面能力丰富，但这些能力和 `x-ai-gateway` 云端服务端网关定位存在边界差异。该任务需要明确是否推进、如何推进和哪些能力不应进入服务端主线。

## 目标

- 输出做/不做/延后的判断。
- 给出最小 MVP、技术栈、安全边界和服务端 API 契约。
- 明确 MCP/Skills/Session/Workspace 哪些可以接入云端，哪些必须留在本机。
- 与现有 onboarding pack、client instance、plugin grant 建立关系。

## 范围

- 可行性报告与 ADR。
- Companion manifest/API schema 示例。
- 本地权限、workspace、session、secret 边界。
- 后续任务拆分建议。

## 非目标

- 不实现桌面应用。
- 不读取用户本地 workspace、session、IDE profile 或 secret。
- 不做设备指纹、风控规避或切号注入。

## 验收标准

- 评估报告明确推进策略。
- 若建议推进，提供 MVP 和接口清单。
- 若延后或不进主线，说明替代方案。
- 文档、任务状态和验证结果完成回写。

## 实现结果

- 新增 [REP-20260506 桌面 Companion、MCP/Skills/Session/Workspace 可行性评估](../reports/REP-20260506-desktop-companion-mcp-workflow-evaluation.md)，明确建议不把 Desktop Companion 直接并入服务端主线。
- 新增 [ADR-0009 Desktop Companion 不进入服务端主线](../decisions/ADR-0009-desktop-companion-out-of-mainline.md)，固化架构边界。
- 新增 `docs/companion/companion-manifest.schema.json` 与 `docs/companion/companion-manifest.example.json`，定义独立 companion/plugin MVP manifest、权限和隐私边界。
- 新增 `CompanionManifestSchemaTests`，确保 manifest schema/example 不允许 workspace、session、secret、IDE profile 默认上传。
- 与既有 client instance、plugin grant、Deep Link 授权能力形成衔接：服务端只保留实例、授权、审计和策略事实源，本机能力留在独立客户端或插件。

## 测试/验证

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.docs.CompanionManifestSchemaTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.docs.CompanionManifestSchemaTests"`

## 遗留问题

- 本轮按可行性任务边界不实现桌面应用、托盘、MCP runtime 或 workspace/session 读取。
- 若后续推进 MVP，建议以独立 companion/browser plugin 仓库承接，并复用现有 client instance 与一次性 grant API。
