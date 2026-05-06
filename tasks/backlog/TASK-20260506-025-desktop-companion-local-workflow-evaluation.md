# TASK-20260506-025 桌面 Companion、MCP/Skills/Session/Workspace 可行性评估

状态：Backlog  
优先级：Low  
来源：[REP-20260506 参考项目功能深度再复核](../../docs/reports/REP-20260506-reference-feature-depth-recheck.md)  
关联需求：[REQ-20260506-012 参考项目功能深度复核与任务再生成](../../docs/requirements/REQ-20260506-012-reference-depth-recheck-task-generation.md)

## 背景

`cc-switch-main` 与 `cockpit-tools-main` 在桌面、本机 CLI、MCP、Skills、Session、Workspace、托盘、云同步和多实例方面非常丰富。但这些能力与 `x-ai-gateway` 的云端服务端网关定位不同，不应直接塞进主线实现。用户要求缺失或不完善项转为 task，因此本项仅作为条件性可行性评估。

## 目标

- 判断是否需要独立桌面 companion 或浏览器插件来承接本机能力。
- 明确 MCP/Skills/Session/Workspace 哪些可以安全接入云端，哪些只能留在本机。
- 给出是否推进、如何推进和安全边界建议。

## 范围

- 本机 MCP/Skills 管理、Session 浏览、Workspace 编辑、Deep Link、云同步的产品价值评估。
- 隐私、安全、权限、合规和数据边界评估。
- 与现有云端 onboarding pack、client instance、plugin grant 的关系。
- 若建议推进，拆分独立客户端项目或插件任务。

## 非目标

- 本任务不实现桌面应用。
- 不读取用户本地 workspace、session、IDE profile 或 secret。
- 不做设备指纹、风控规避或切号注入。

## 验收标准

- 输出评估报告，明确做/不做/延后。
- 如建议做，给出最小 MVP、技术栈、安全边界和与服务端 API 的接口清单。
- 如不建议做，说明替代方案和不进入主线的原因。

## 实现记录

待处理。

## 测试/验证

待处理。

## 遗留问题

待处理。
