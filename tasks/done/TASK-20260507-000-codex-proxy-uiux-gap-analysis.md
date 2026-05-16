# TASK-20260507-000 Codex 账户反代与 UI/UX 深度差距分析

状态：Done  
优先级：High  
来源：User Request  
关联报告：[REP-20260507 Codex 账户反代与 UI/UX 深度差距分析](../../docs/reports/REP-20260507-codex-proxy-uiux-gap-analysis.md)

## 背景

用户要求再次深度对比当前项目与参考项目的差距，特别关注 Codex 账户反代能力以及 UI/UX 用户友好性。此前 `TASK-20260506-020` 到 `025` 已经补齐 request filter、官方账号导入、client instance、OpenAPI、Linux 部署和 companion 评估等基础闭环，本轮需要避免重复，把剩余差距定位到真实产品可用层。

## 目标

- 深度复核 `cli_proxy-master` 的 Codex 代理、模型路由、热切换、请求过滤、usage 和实时观测。
- 复核 `cc-switch-main`、`cockpit-tools-main` 的 Codex/AI IDE 账号、实例、配额、Deep Link 和本机体验。
- 复核 `new-api-main`、`sub2api-main` 的 Web UI、账号分发、计费和后台可用性。
- 对照当前仓库已有代码、文档和任务状态，拆出下一层 backlog。

## 范围

- 参考目录：`D:/WorkSpace/Project/ai/参考`
- 当前仓库：`src/`、`web/`、`docs/`、`tasks/`
- 重点能力：Codex `/v1/responses` 反代、官方账号导入/刷新、账号池热切换、session 粘性、request filter、实时观测、接入向导、UI 信息架构。

## 非目标

- 不实现新功能代码。
- 不读取用户本机 `~/.codex`、IDE profile、workspace 或 session。
- 不同步线上 Notion/Linear。

## 验收标准

- 生成新的深度差距报告。
- 缺口转化为本地 backlog task。
- 更新 `docs/index.md` 和 `tasks/index.md`。
- 本分析任务归档到 `tasks/done/`。

## 实现记录

- 已生成 [REP-20260507 Codex 账户反代与 UI/UX 深度差距分析](../../docs/reports/REP-20260507-codex-proxy-uiux-gap-analysis.md)。
- 已新增 7 个 backlog task，覆盖 Codex 真实反代、请求保真、账号池热切换、实时观测、接入向导、UI 信息架构和可用性验收。

## 测试/验证

- 已复核 `cli_proxy-master` README、Codex proxy、base proxy、UI server、filter、usage、routing/loadbalance 关键实现。
- 已复核 `cc-switch-main`、`cockpit-tools-main` README 与关键目录，确认桌面、本机账号、MCP/Skills/Session/Deep Link 方向边界。
- 已复核当前项目 `OpenAiResponsesController`、`GatewayChatExecutionService`、`OfficialAccountAdminService`、client instance 文档、request filter 文档和前端导航/关键页面。
- 已将报告与任务索引回写到本地 Markdown。

## 遗留问题

- 本轮只做分析和任务拆分，不进入代码实现。
- 工作区存在较多历史未提交改动，本轮没有清理无关文件。
