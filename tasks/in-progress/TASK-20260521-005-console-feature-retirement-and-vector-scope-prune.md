# TASK-20260521-005 控制台重复功能下线与向量能力范围收窄

## 任务类型

父任务 + 子任务编排中

## 背景

来源：`docs/requirements/REQ-20260521-005-console-feature-retirement-and-vector-scope-prune.md`

用户要求删除以下功能：

1. `官方账号运行态`
2. `能力矩阵`
3. `Native 命名空间兼容`
4. `Provider 参考差距`
5. `站点档案`
6. `成本路由策略中心`
7. `向量检索排障沙盒`
8. `向量 API`

其中前 7 项以控制台页面为主；`向量 API` 和 `官方账号运行态` 则同时连接前端、Admin API、公开协议面与文档事实源。最新边界已确认：`vector` 相关 gateway-local 支撑能力暂不删除，`file` 相关 API 保留；历史 Responses `file_search` 本地绑定成功语义已被 `TASK-20260524-001-04` supersede，当前 hosted `file_search` 必须 native-required。

## 目标

- 明确本轮删除是“控制台下线”还是“能力彻底清理”。
- 基于确认后的范围，删除重复/低价值功能并同步清理路由、API、文档和测试残留。

## 关联子任务

- [TASK-20260521-005-01 控制台重复功能前端下线](..\done\TASK-20260521-005-01-console-feature-retirement-frontend-surface-prune.md)
- [TASK-20260521-005-02 官方账号与向量 API 后端清理边界审计](..\backlog\TASK-20260521-005-02-official-account-vector-api-eradication-boundary.md)
- [TASK-20260521-005-03 任务索引下线能力清理与边界补充](TASK-20260521-005-03-task-index-retirement-and-boundary-cleanup.md)

## 非目标

- 不删除公开 `vector_stores` API 与 `files` API；不恢复 Responses hosted `file_search` 本地绑定成功语义。
- 不贸然删除仍承载官方账号导入、freeze、quota-refresh 与 smoke 的后端逻辑。

## 输入

- `web/src/app/navigation.ts`
- `web/src/app/router.tsx`
- `web/src/features/accounts/`
- `web/src/features/provider-sites/`
- `web/src/features/ops/cost-routing-page.tsx`
- `web/src/features/workbench/VectorStoreSandbox.tsx`
- `src/main/java/...` 中 `accounts`、`provider-sites`、`vector stores` 相关 Admin / public API
- `docs/public-api-compatibility.md`
- `docs/openapi/public-openapi.json`

## 输出

- 一份确认后的删除边界。
- 受影响功能的前端入口/页面/API/文档清理结果。
- 受影响任务索引与历史任务口径清理结果。
- 残留扫描与验证记录。

## 影响范围

- 控制台信息架构与路由。
- Admin 管理 API。
- 公开 OpenAI-compatible 向量接口与文档事实源。
- 本地 `tasks/` 任务索引与历史任务边界说明。

## 依赖

- 用户对 `官方账号运行态` 与 `向量 API` 的删除粒度确认。

## 风险

- 仅删 UI 不删能力，会留下维护负担但可避免破坏现有协议面。
- 彻底删能力会影响已归档需求/任务、公开 API 兼容声明和相关测试。

## 本轮执行假设

- [x] 先删除控制台导航、路由、页面、页内入口和对应前端测试。
- [x] 先保留 `官方账号` 后端治理链路与 `vector_stores` 公开/API 兼容能力。
- [x] 旧路由优先重定向到保留页面，不直接制造 404。

## 验收标准

- [x] 用户已明确确认：`vector` 相关能力保留，`file` 相关 API 保留，Responses `file_search` 绑定保留。
- [x] 本地文档和任务文件更新到位。
- [x] 确认删除范围内的导航、路由、页面、文案和测试一致清理。
- [ ] 如涉及后端/API，相关 OpenAPI、文档和残留扫描同步完成。

## 测试边界

- 范围确认前，不执行删除验证。
- 范围确认后，根据删除层级执行前端 `typecheck` / 定向 `vitest`，以及必要的后端编译或残留扫描。

## 当前状态

进行中（前端下线已完成；后端已继续收口冗余 admin 面，`vector/file` 保留边界已封口，任务索引与历史口径仍在继续清理）
