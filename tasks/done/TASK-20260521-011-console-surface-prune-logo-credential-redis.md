# TASK-20260521-011 控制台瘦身、品牌 Logo、上游凭证统一与热数据缓存

状态：Done  
优先级：Critical  
上游来源：[REQ-20260521-011](../../docs/requirements/REQ-20260521-011-console-surface-prune-logo-credential-redis.md)

## 任务类型

父任务

## 背景

本轮承接用户对现役控制台继续瘦身和提高输入可靠性的反馈。核心方向是删除重复产品面，把 Codex `auth.json` 账号作为上游凭证的一种统一展示，并通过已有系统数据减少用户手填。

## 目标

- 删除变更维护系列控制台入口与重复错误规则入口。
- 删除公开首页状态、价格、文档入口并接入新 Logo。
- 修复上游凭证列表漏掉 Codex 账号的问题。
- 将模型、代理、TLS 指纹等可枚举字段改为搜索/勾选选择。
- 保持凭证表格单行扁平，将操作收敛进详情弹窗。
- 删除请求日志页 Codex 请求独立表格。
- 推进 Redis 热数据首批增强与 PostgreSQL 回写边界。

## 非目标

- 不删除 `vector/files/file_search` 主线支撑能力。
- 不删除 Codex 后端导入、刷新、反代和 smoke 所需能力。
- 不提交真实密钥或 auth.json 内容。

## 输入

- `web/src/app/`
- `web/src/features/public/`
- `web/src/features/credentials/`
- `web/src/features/request-logs/`
- `web/src/features/accounts/`
- `web/src/features/network/`
- 后端 Redis、账号、凭证、日志相关 service 与测试

## 输出

- 删除或重定向后的旧运维/公开/错误规则入口。
- 新 Logo 资源与使用点。
- 上游凭证统一列表和详情弹窗。
- 支持模型、代理、TLS 指纹搜索选择器。
- 请求日志统一视图。
- Redis 首批热数据增强实现或明确可执行边界。

## 影响范围

控制台信息架构、公开首页、上游凭证管理、请求日志观测、热数据缓存与持久化同步。

## 依赖

- [TASK-20260521-010](../done/TASK-20260521-010-console-navigation-settings-codex-auth-import-refresh.md)
- [TASK-20260521-005](./TASK-20260521-005-console-feature-retirement-and-vector-scope-prune.md)
- [TASK-20260521-006](./TASK-20260521-006-redundant-api-eradication-and-baseline-refresh.md)

## 风险

- 当前工作区存在大量并行改动，必须避免回退无关文件。
- 上游凭证统一列表跨静态凭证和账号实体，前端操作需要防止误调用。
- Redis 增强需要保护最终一致性，不能让 PostgreSQL 事实源丢失。

## 验收标准

- [x] 变更维护、公开状态/价格/文档、错误规则重复入口下线。
- [x] 新 Logo 已用于 favicon、公开首页和控制台。
- [x] 上游凭证页能显示 Codex 账号和 Gemini 静态凭证的统一清单。
- [x] 支持模型、代理、TLS 指纹不再依赖手填。
- [x] 凭证表格操作不再拉高行高。
- [x] 请求日志不再单列 Codex 请求。
- [x] Redis 首批增强有代码、测试和 PostgreSQL 回写边界。

## 测试边界

- 前端：`npm run typecheck`
- 前端：相关 vitest 定向执行
- 后端：Redis 热数据相关定向测试
- 浏览器：关键页面手工验证

## 关联任务

- [TASK-20260521-011-01](./TASK-20260521-011-01-console-public-route-prune.md)
- [TASK-20260521-011-02](./TASK-20260521-011-02-brand-logo-favicon-console-public.md)
- [TASK-20260521-011-03](./TASK-20260521-011-03-upstream-credential-unified-list-selectors.md)
- [TASK-20260521-011-04](./TASK-20260521-011-04-request-log-codex-panel-prune.md)
- [TASK-20260521-011-05](./TASK-20260521-011-05-redis-hot-data-writeback.md)

## 当前状态

已完成。实现与验证结果见 [REQ-20260521-011](../../docs/requirements/REQ-20260521-011-console-surface-prune-logo-credential-redis.md)。
