# TASK-20260521-005-02 官方账号与向量 API 后端清理边界审计

## 任务类型

子任务 / Backlog

## 背景

父任务：`tasks/in-progress/TASK-20260521-005-console-feature-retirement-and-vector-scope-prune.md`

用户曾希望继续删除 `官方账号运行态` 与 `向量 API`，但后续边界已更新：`vector` 相关能力暂不删除，`file` 相关 API 必须保留，Responses `file_search` 绑定必须保留。因此本任务现在主要保留为历史边界审计与官方账号残余后端面的后续复核入口。

## 目标

- 固化为什么 `vector stores`、`files`、Responses `file_search` 及相关 schema 必须保留。
- 继续盘点 `官方账号` 其余后端/API 真实依赖，区分主线路径与历史附属面。
- 如后续仍需继续收口，仅围绕非主线的官方账号附属接口再拆分实施任务。

## 非目标

- 本任务不直接删除任何后端/API 代码。
- 本任务不替代前端下线子任务。

## 上游来源

- `docs/requirements/REQ-20260521-005-console-feature-retirement-and-vector-scope-prune.md`
- `tasks/in-progress/TASK-20260521-005-console-feature-retirement-and-vector-scope-prune.md`

## 输入

- `src/main/java/.../admin/accounts*`
- `src/main/java/.../admin/provider-sites*`
- `src/main/java/.../protocol/ingress/openai/*vector*`
- `src/main/java/.../protocol/ingress/openai/*file*`
- `docs/openapi/public-openapi.json`
- `docs/requirements/REQ-20260517-003` 至 `REQ-20260518-004`

## 输出

- 保留/删除边界审计结论。
- 后续实施任务拆分建议。

## 影响范围

- Admin 管理 API。
- OpenAI-compatible public API 与 file/vector 支撑链路说明。
- OpenAPI、公开文档与功能性服务 API 范围声明。

## 依赖

- `TASK-20260521-005-01` 完成前端下线。
- 最新用户边界：`vector` 相关能力保留，`file` 相关 API 保留。

## 风险

- 误删后端/API 会破坏 Codex/Responses 当前功能链路，尤其是 `vector stores`、`files` 与 `file_search`。
- 不先盘点事实源就直接删，会造成文档、OpenAPI 与实现不一致。

## 验收标准

- [ ] 明确列出 `vector/files/file_search` 为什么必须保留，以及官方账号哪些附属面仍可继续收口。
- [ ] 给出受影响 API、文档、测试与持久化对象清单。
- [ ] 如进入实施，形成新的可执行 task spec。

## 测试边界

- 本任务以静态盘点、残留扫描和依赖分析为主。
- 若进入实施，另行补充后端编译、测试与 OpenAPI 校验口径。

## 当前状态

Backlog

## 2026-05-21 口径补充

- 当前控制台入口已按父任务进入下线范围，本任务只负责判断后端/API 是否还应保留。
- 在未完成本任务前，不应把 `官方账号运行态` 或 `向量 API` 理解为仍然需要维持独立控制台产品面的现役功能。
- 最新边界已确认：不再把 `vector stores`、`files`、Responses `file_search` 及其持久化支撑作为当前删除候选。
