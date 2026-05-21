# 编程类账号身份治理

关联需求：[REQ-20260506-003 第九批任务闭环设计](requirements/REQ-20260506-003-ninth-priority-task-closure-design.md)  
关联任务：[TASK-20260501-006 编程类账号身份治理](../tasks/done/TASK-20260501-006-programming-account-identity.md)

> 当前状态：控制台中的 `官方账号运行态` 入口已下线。本文描述的是 `GET /admin/accounts/{id}/programming-identity` 等后端治理事实，不再表示账号身份治理仍以独立控制台能力对外暴露。

## 实现范围

- 扩展 `UpstreamAccountProviderType`：
  - `CODEX_OAUTH`
  - `ANTIGRAVITY_OAUTH`
  - `COPILOT_OAUTH`
  - `CLAUDE_PLAN`
- 新增对应本地 refresh adapter，沿用现有 OAuth/session refresh 状态机、冷却和审计。
- `CredentialMaterialResolver` 与 OAuth connection mapping 已覆盖新增 provider type。
- 新增管理端 identity summary：`GET /admin/accounts/{id}/programming-identity?clientFamily=CODEX`。
- identity summary 从账号 metadata 中读取 identity subject、email、adoption decision 和 client family，并结合账号健康、冻结、quota 和 pool client family 判断路由可用性。

## 路由可用性判断

以下情况会阻断：

- 账号 inactive。
- 账号 frozen。
- 账号 unhealthy。
- adoption decision 为 `REJECTED`。
- refresh status 为 `FAILED`。
- quota remaining tokens 或 requests 已耗尽。
- pool 的 allowed client families 不包含请求的 client family。

## 验证

已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.AccountAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.OAuthSessionRefreshServiceTests"
```

覆盖：

- Codex OAuth identity subject、email、quota 和 route eligible。
- Codex 默认 refresh adapter 能刷新并写入 header snapshot。
