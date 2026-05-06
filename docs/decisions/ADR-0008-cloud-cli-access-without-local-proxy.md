# ADR-0008 云端代理优先的 CLI 接入架构

状态：Accepted  
日期：2026-05-06  
关联需求：[REQ-20260506-007 CLI 云端代理接入闭环设计](../requirements/REQ-20260506-007-cli-cloud-access-closure-design.md)

## 背景

`cli_proxy-master` 和 `cockpit-tools-main` 展示了本地 proxy、本地桌面账号管理、多实例和插件联动的成熟体验。但 `x-ai-gateway` 的产品定位是云端代理：CLI/AI IDE 通过云端兼容 endpoint 接入，由服务端统一完成鉴权、账号池、路由策略、过滤、模型映射、用量、审计和可观测。

用户已明确要求：任何 CLI 都可以接入进来，不能把方案设计成部署在本地的 proxy。

## 决策

- `x-ai-gateway` 的 CLI 接入默认走云端 endpoint，不要求用户部署本地 proxy、本地 agent 或桌面 companion。
- Claude Code、Codex、Gemini CLI、OpenCode、OpenClaw、Cursor、Windsurf、Kiro 等客户端通过兼容协议、base URL、API key 和可选 client metadata 接入。
- CLI 热切换语义定义为：云端 route policy / provider site / account pool / model mapping 更新后，对后续新请求生效；不承诺修改已经建立的长连接或流式请求。
- request filter、model mapping、site mapping、account pool selection 必须在云端执行，并进入审计与可观测。
- 本机 workspace、session、IDE profile、设备指纹、账号 secret 默认不读取、不扫描、不上传。

## 影响

- `TASK-20260506-012`、`TASK-20260506-014`、`TASK-20260506-015` 均按 High 优先级推进。
- `cli_proxy-master` 的价值转为体验参考，不作为部署拓扑参考。
- onboarding pack 和公开 docs 必须优先描述云端 endpoint 接入，而不是本地 proxy 启动步骤。
- 后续如需插件或 Deep Link，也只用于获取云端配置、一次性 secret 或 client metadata，不接管本机 profile。

## 验收

- 文档和任务中不得再把 CLI 相关核心方案描述为“本地 proxy 部署”。
- 后端至少提供云端 CLI 接入矩阵、metadata 或策略执行的一项可验证增强。
- 自动化测试覆盖云端 CLI 接入相关规则。
