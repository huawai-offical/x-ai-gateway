# REQ-20260513-001 参考项目、翻译能力、后台与门户完整度复核

状态：Done  
日期：2026-05-13  
关联任务：

- [TASK-20260513-001 参考项目、翻译能力、后台与门户完整度复核](../../tasks/done/TASK-20260513-001-reference-translation-admin-portal-audit.md)

关联报告：

- [REP-20260513 参考项目、翻译能力、后台与门户完整度复核](../reports/REP-20260513-reference-translation-admin-portal-audit.md)

## 背景

用户要求再次深度对比参考项目功能实现细节，并回答当前项目是否已经完全超越或实现；同时重点确认全自动 AI API 翻译、管理后台完善度、一级/二级菜单排布、可删除的运维能力，以及客户门户是否功能齐全。

## 目标

- 对比 `D:/WorkSpace/Project/ai/参考` 下参考项目的实现细节与当前项目功能面。
- 判断当前项目是否“完全超越”或仍有生产细节差距。
- 审计全自动 AI API 翻译能力是否覆盖 OpenAI、Anthropic 及主流 API 场景。
- 审计 Admin Console 功能完整度、菜单信息架构和可精简运维能力。
- 审计 Community Portal 对客户是否功能齐全。
- 将缺失或不完善项转化为本地 backlog 任务。

## 范围

- 当前仓库代码、前端路由、菜单、Portal/Console 页面与历史 docs/tasks。
- 参考项目：`new-api-main`、`sub2api-main`、`cc-switch-main`、`cockpit-tools-main`、`cli_proxy-master`。
- 本轮只做代码态审计、文档沉淀和任务拆分，不直接改业务代码。

## 非目标

- 不在本需求内直接实现新功能。
- 不把桌面客户端项目能力机械搬到服务端网关。
- 不承诺任何未知 Provider API 自动无损翻译。

## 风险

- 当前工作区存在大量历史未提交和未跟踪文件，需要避免误回退。
- 参考项目定位不同，不能把桌面工具或单机场景能力机械映射到服务端网关。
- “全自动翻译”涉及多协议 payload shape、streaming、tool calling、vision/file/audio 等复杂场景，需要按实现入口和测试证据判断。

## 验收标准

- 报告回答用户提出的 6 个问题，并给出可执行判断。
- 对不完善项拆分本地 backlog 任务。
- 更新 `docs/index.md` 与 `tasks/index.md`。
- 本轮复核任务完成后归档到 `tasks/done/`，需求文档回写验证结果。

## 验收结果

- 已完成参考项目和当前项目代码态复核。
- 已形成 [REP-20260513](../reports/REP-20260513-reference-translation-admin-portal-audit.md)。
- 已新增 5 个 backlog 任务：
  - [TASK-20260513-002](../../tasks/done/TASK-20260513-002-mainstream-api-translation-conformance-matrix.md)
  - [TASK-20260513-003](../../tasks/done/TASK-20260513-003-admin-console-menu-simplification-ops-prune.md)
  - [TASK-20260513-004](../../tasks/done/TASK-20260513-004-portal-customer-completeness-hardening.md)
  - [TASK-20260513-005](../../tasks/backlog/TASK-20260513-005-provider-media-pricing-reference-gap-closure.md)
  - [TASK-20260513-006](../../tasks/backlog/TASK-20260513-006-public-site-docs-pricing-status-surface.md)
- 本需求已闭环，后续进入新增 backlog 任务实施。
