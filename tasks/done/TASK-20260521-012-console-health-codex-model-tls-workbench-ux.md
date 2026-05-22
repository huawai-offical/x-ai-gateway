# TASK-20260521-012 控制台健康评分、Codex 模型、TLS 画像与调试台体验修正

状态：Done  
优先级：Critical  
上游来源：[REQ-20260521-012](../../docs/requirements/REQ-20260521-012-console-health-codex-model-tls-workbench-ux.md)

## 任务类型

父任务

## 背景

用户在上一轮控制台瘦身与上游凭证统一后，继续指出健康评分、Codex 模型刷新、账号分组/治理表格、TLS 指纹表单和白盒调试工作台存在一致性与易用性问题。本任务承接这些反馈，作为同一批控制台体验修正推进。

## 目标

- 健康评分统一为表格，并覆盖静态凭证与账号类凭证。
- Codex 类型账号补齐模型刷新入口。
- 账号分组与治理表格对齐已录入凭证的扁平行模式。
- TLS 指纹增加常用默认画像，header 改为键值对编辑。
- 白盒调试工作台 tab 布局增加合理间距。

## 非目标

- 不恢复官方账号运行态独立页面。
- 不删除主线支撑的 vector、files、file_search API。
- 不提交真实凭证内容。

## 输入

- `web/src/features/credentials/`
- `web/src/features/accounts/`
- `web/src/features/governance/`
- `web/src/features/network/`
- `web/src/features/workbench/`
- 后端 Admin 账号、TLS 指纹、模型目录相关 service 与测试

## 输出

- 本轮前后端代码修改。
- 定向测试与 typecheck 结果。
- 回写后的需求文档与任务状态。

## 影响范围

控制台凭证治理、账号分组、治理策略、网络治理、白盒调试工作台，以及少量 Admin API 支撑能力。

## 依赖

- [TASK-20260521-011](../done/TASK-20260521-011-console-surface-prune-logo-credential-redis.md)
- [TASK-20260521-010](../done/TASK-20260521-010-console-navigation-settings-codex-auth-import-refresh.md)

## 风险

- 当前工作区可能存在并行未归档改动，必须避免回退无关文件。
- 表格操作收敛涉及测试断言更新，需保持用户可找到原有操作。
- TLS 默认画像应幂等初始化，不能覆盖用户自定义配置。

## 验收标准

- [x] 健康评分统一表格覆盖静态凭证与账号类凭证。
- [x] Codex 类型账号可以刷新支持模型。
- [x] 账号分组与治理表格行高稳定，主行操作不再撑开空白。
- [x] TLS 指纹内置常用画像，并使用键值对 header 编辑。
- [x] 白盒调试工作台 tab 间距优化。
- [x] 文档、任务、测试结果完成回写。

## 测试边界

- 前端定向 vitest。
- `npm run typecheck`。
- 后端 Admin 账号模型刷新与 TLS 初始化定向测试。
- 必要时使用浏览器检查关键页面。

## 关联任务

- [TASK-20260521-012-01](./TASK-20260521-012-01-health-score-unified-table.md)
- [TASK-20260521-012-02](./TASK-20260521-012-02-codex-model-refresh-entry.md)
- [TASK-20260521-012-03](./TASK-20260521-012-03-account-groups-governance-table-detail-actions.md)
- [TASK-20260521-012-04](./TASK-20260521-012-04-tls-default-profiles-key-value-editor.md)
- [TASK-20260521-012-05](./TASK-20260521-012-05-workbench-tabs-spacing.md)

## 当前状态

已完成。实现结果与验证命令已回写到关联需求文档，子任务同步归档。
