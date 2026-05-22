# TASK-20260521-011-04 请求日志 Codex 独立面板删除

状态：Done  
优先级：High  
上游来源：[TASK-20260521-011](./TASK-20260521-011-console-surface-prune-logo-credential-redis.md)

## 任务类型

子任务

## 背景

用户要求请求日志界面不需要给 Codex 请求单独列出来。Codex 请求应作为统一日志的一类筛选或详情字段，而不是额外表格。

## 目标

- 删除请求日志页 Codex 请求独立面板和独立表格。
- 保留统一请求日志中的必要 Codex 字段展示或详情能力。
- 保持已有请求日志、路由决策、缓存命中和资源记录视图可用。

## 非目标

- 不删除后端 Codex observability API，除非后续冗余接口清理确认无调用。
- 不删除统一请求日志里的 Codex 识别字段。

## 输入

- `web/src/features/request-logs/request-logs-page.tsx`
- `web/src/features/request-logs/request-logs-page.test.tsx`
- Codex observability 相关 API 调用

## 输出

统一请求日志页面和测试断言更新。

## 验收标准

- [x] 页面不再出现“Codex 请求”独立表格。
- [x] 页面不再出现“只看 Codex”的独立面板控件。
- [x] 统一请求日志仍能打开详情。

## 测试边界

- request-logs 定向 vitest
- 浏览器验证

## 当前状态

已完成。Codex 独立脱敏包和恢复命令入口随独立面板删除，统一请求日志保留客户端与会话列；request-logs 定向测试通过。
