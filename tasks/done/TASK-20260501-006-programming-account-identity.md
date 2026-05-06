# TASK-20260501-006 编程类账号身份治理：Codex / Antigravity / Copilot / Claude Plan OAuth 与额度同步

状态：Done  
优先级：Medium  
来源：Linear X-287  
来源 URL：https://linear.app/x-ai/issue/X-287/编程类账号身份治理codex-antigravity-copilot-claude-plan-oauth-与额度同步  
关联报告：[REP-20260501](../../docs/reports/REP-20260501-open-source-gap-analysis.md)
关联推进需求：[REQ-20260506-003](../../docs/requirements/REQ-20260506-003-ninth-priority-task-closure-design.md)
关联说明文档：[programming-account-identity](../../docs/programming-account-identity.md)

## 背景

`Sub2API` 代码中已有 OpenAI/Gemini/Antigravity OAuth、AuthIdentity、IdentityAdoptionDecision 等对象；`CC Switch` 也包含 Codex OAuth、Copilot quota、Claude/Gemini CLI 配置与用量查询。当前 `x-ai-gateway` 账号 provider 主要是 OpenAI OAuth、Gemini OAuth、Claude Account，缺少更完整的编程类账号身份治理。

## 目标

为 Codex、Antigravity、Copilot、Claude Plan 等编程场景账号建立统一身份、授权、刷新、额度同步和风控隔离能力。

## 范围

- 设计 `AuthIdentity` / `AccountIdentityBinding` 类模型。
- 支持 OAuth session refresh、quota sync、identity adoption 审核。
- 支持按账号类型配置代理、TLS profile、client family 和混合调度限制。
- 在账号池与路由决策中展示身份来源和额度状态。

## 非目标

- 不保存用户不可恢复的 OAuth 明文 token。
- 不绕过上游服务条款和风控要求。

## 验收标准

- 至少一个新增编程类账号类型完成授权、刷新、额度展示和路由可用性判断。
- 管理端可查看身份绑定、授权状态、额度窗口和最近刷新错误。
- 具备安全审计与失败降级策略。

## 实现记录

- 2026-05-06：进入第九批任务闭环，目标是补齐编程类账号 provider type、identity summary、quota/adoption 视图与 client family 路由可用性判断。
- 2026-05-06：完成 Codex/Antigravity/Copilot/Claude Plan provider type、默认 refresh adapter、identity summary 与路由可用性判断。

## 测试/验证

已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.AccountAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.OAuthSessionRefreshServiceTests"
```

覆盖：

- Codex OAuth identity subject、identity email、quota、client family 与 route eligible。
- Codex 默认 refresh adapter 刷新并写入 header snapshot。

## 遗留问题

- 真实 provider quota API、identity adoption 审核流和更细粒度风控隔离尚未在本轮实现。

## 后续建议

- 后续把 Codex/Copilot/Claude Plan 的真实 quota endpoint 和 refresh endpoint 做成 provider adapter，并把 adoption decision 接入审核流程。
