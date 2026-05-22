# TASK-20260521-012-01 健康评分统一表格

状态：Done  
优先级：Critical  
上游来源：[TASK-20260521-012](./TASK-20260521-012-console-health-codex-model-tls-workbench-ux.md)

## 任务类型

子任务

## 背景

健康评分仍偏向 key 类凭证，不符合“Codex 凭证也是上游凭证一种”的口径。

## 目标

- 将健康评分展示改为表格。
- 表格覆盖统一 inventory 中的静态凭证、Codex/OAuth 账号。
- 展示状态、健康分、成功率、请求量、最后使用和异常摘要等可比字段。

## 非目标

- 不重新定义后端全量评分算法。
- 不展示真实密钥、AT、RT 或 auth.json 内容。

## 输入

- `web/src/features/ops/ops-alerts-page.tsx`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/GovernanceAdminService.java`
- `/admin/ops/health-scores`

## 输出

统一健康评分表格、账号类凭证评分投影与测试更新。

## 影响范围

智能运维告警页健康评分区域。

## 依赖

统一凭证 inventory。

## 风险

账号类凭证与静态凭证字段不完全一致，需要兜底显示。

## 验收标准

- [x] 健康评分以表格呈现。
- [x] Codex/OAuth 账号不被过滤掉。
- [x] 表格字段中文化且不暴露敏感内容。

## 测试边界

- `web/src/features/ops/ops-alerts-page.test.tsx`
- `npm run typecheck`

## 当前状态

已完成。`/admin/ops/health-scores` 同时返回静态凭证与账号类凭证，前端以表格展示来源、提供方、评分、健康状态和关联 ID。
