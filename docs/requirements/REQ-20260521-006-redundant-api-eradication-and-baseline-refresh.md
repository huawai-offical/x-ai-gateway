# REQ-20260521-006 冗余接口清理与清库前 Baseline 重建

## 背景

在 `REQ-20260521-005` 已完成控制台重复功能前端下线后，用户继续要求：

1. 进一步仔细审视当前仓库里还有哪些“多余接口”。
2. 将可并行的清理工作分派给 subagent 执行。
3. 在用户清库前，基于清理后的真实能力面重新生成一版 Liquibase baseline。

当前仓库的风险点在于：

- 前端已经下线了一批控制台能力，但对应的 Admin API、public/protocol API、OpenAPI、文档和 schema 仍可能残留。
- 之前的 `REQ-20260521-003 / TASK-20260521-003` 已做过一轮 baseline 重建，但那一轮是在当时能力面基础上生成；若本轮继续删除冗余接口与实体，就需要再生成一次更收敛的 baseline。
- 用户明确表示“我将清库”，这意味着本轮 baseline 可以按“空库初始化”思路处理，不再为旧库兼容背负额外包袱。

## 目标

- 盘点并清理当前主线之外、已经失去产品面或与现有功能范围重复的接口。
- 将接口清理和配套文档/OpenAPI/schema 收口成一组可验证的本地任务。
- 以清理后的真实 schema 为输入，重新生成单一 baseline，供用户清库后直接初始化。

## 范围

### 包含

- `src/main/java/.../admin/api` 中仅服务于已下线控制台能力、或与当前主线重复的 Admin API。
- `src/main/java/.../protocol/ingress/*` 中与已下线能力绑定、且不再符合当前产品主线的协议入口。
- 与上述接口直接绑定的 application/service、entity、repository、test、OpenAPI、docs 和 baseline。
- `src/main/resources/db/changelog/changes/db.changelog-0001-baseline.yaml` 的重新生成与 `db.changelog-master.yaml` 的同步校准。

### 不包含

- 不为了“代码看起来更干净”而删除仍被主线依赖的对话、streaming、tools、多模态、认证、审计、usage、Responses、本地 lifecycle 等能力。
- 不支持旧库无损升级；本轮 baseline 以清库后的空库初始化为前提。
- 不删除仍需保留的官方账号接入后端、`vector stores` / `files` 相关协议能力，或 Responses `file_search` 支撑链路。

## 候选冗余接口方向

- 已经从控制台下线但后端仍保留独立入口的能力：
  - `Capability Matrix`
  - `Provider Reference Gap`
  - `Native Compatibility`
  - `Cost Routing`
  - `Provider Sites` 中只为旧控制台页面服务的部分 Admin 入口
- 旧命名或已失去主路径定位的能力：
  - 旧 `account pool` / `live` / `realtime` 遗留 API
  - 与旧控制台运营面绑定、但当前已经被 `account groups`、`credentials`、`models`、`workbench` 吸收的接口
- 经本轮边界确认，以下能力不纳入删除候选：
  - `vector stores` 协议面、本地资源实体与公开 `/v1/vector_stores*`
  - `files` 相关 API 与其本地持久化支撑
  - Responses `file_search` 本地绑定链路
  - 仍用于官方账号接入与最小运行态治理的后端入口

## 风险

- 一旦删错协议入口，可能会破坏当前仍保留的 Codex / Responses / files / file_search 支撑能力。
- baseline 若未同步反映清理后的实体，会导致用户清库后初始化失败或留下幽灵表。
- 当前工作区已有大量在途修改，实施时必须严格按文件边界操作，避免误伤他人改动。

## 验收标准

- 明确列出“已删除”“确认保留”“暂缓删除”的接口与实体清单。
- 冗余接口清理后，相关 OpenAPI、docs、tests 与 Liquibase baseline 保持一致。
- `db.changelog-master.yaml` 只指向最新 baseline，且 baseline 不再包含本轮已删除的 schema；对于本轮确认保留的 `vector` / `file` 相关表，不做误删。
- 至少完成一轮编译或定向测试验证，并记录清库后的启动前置说明。

## 边界补充

- 2026-05-21 最新用户边界：`vector` 相关能力暂不删除，`file` 相关 API 必须保留。
- 因此本轮重点转为：
  - 删除已失去产品面且无现役调用的 Admin API / 展示面接口；
  - 收口仅服务于旧控制台页面的附属治理入口；
  - 校准 baseline，使其反映“保留 vector/file 主线支撑、删除闲置控制面”的真实 schema。

## 关联文档

- [REQ-20260521-005](REQ-20260521-005-console-feature-retirement-and-vector-scope-prune.md)
- [REQ-20260521-003](REQ-20260521-003-liquibase-baseline-rebuild.md)
- [REQ-20260518-005](REQ-20260518-005-functional-service-api-scope.md)
