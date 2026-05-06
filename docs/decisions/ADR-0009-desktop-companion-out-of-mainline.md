# ADR-0009 Desktop Companion 不进入服务端主线

日期：2026-05-06  
状态：Accepted  
关联报告：[REP-20260506 桌面 Companion、MCP/Skills/Session/Workspace 可行性评估](../reports/REP-20260506-desktop-companion-mcp-workflow-evaluation.md)

## 背景

参考项目在桌面、本机 CLI、MCP、Skills、Session、Workspace 和云同步方面能力丰富。但 `x-ai-gateway` 当前主线是云端网关服务，核心职责是 provider routing、account pool、usage、trace、governance 和 client onboarding。

## 决策

Desktop Companion、MCP/Skills/Session/Workspace 本机能力不进入服务端主线。若推进，应建立独立 companion / IDE 插件项目，并通过现有 client instance 与 plugin grant API 接入云端。

## 理由

- 避免云端服务端直接接触本机文件、session、IDE profile 和 secret。
- 保持服务端部署、权限、审计边界清晰。
- 已有 client instance、Deep Link、plugin message schema 能承接独立客户端接入。

## 后果

- 服务端继续维护授权、实例、trace/usage 和配置下发事实源。
- 本机 workspace/session/MCP/Skills 由 companion 本地处理。
- 后续新增功能必须先证明不扩大云端数据采集边界。
