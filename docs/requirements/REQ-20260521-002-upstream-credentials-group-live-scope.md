# REQ-20260521-002 上游凭证、账号分组与 Live 功能收敛

## 背景

项目核心理念已收敛为对话、streaming、tools、多模态等功能性服务 API，不追求全量 OpenAI / Anthropic API 覆盖。控制台接入侧需要继续围绕“上游凭证 + 账号分组 + 路由治理”组织，而不是把 Codex、旧账号归集模型、Live Session 拆成独立产品概念。

## 目标

- 上游凭证编辑在列表页弹窗内完成，不跳转详情页，不展示大量不可编辑统计字段。
- Codex 凭证归入上游凭证接入路径，控制台入口从上游凭证页进入。
- 上游凭证创建与修改必须选择一个账号分组。
- 控制台文案、导航和内部 API 取消旧账号归集概念，统一为“账号分组”。
- Live Session 与 Realtime 类能力先下线，避免偏离对话、streaming、tools、多模态的核心服务面。

## 范围

- 控制台路由、导航、命令搜索、上游凭证列表页、Codex 接入入口。
- 凭证创建/更新的账号分组必填校验。
- Live Session 控制台路由与 Admin Controller。
- OpenAI Realtime WebSocket、Realtime client_secret、Ops Realtime 面板、能力矩阵中的 realtime 能力声明。

## 非目标

- 本轮不扩展全量 Provider API 覆盖。
- 本轮不做测试用例扩写；按用户要求跳过新增测试，仅做必要编译/类型检查。

## 风险

- 历史数据中可能存在未归组凭证，列表仍需要可见，但后续编辑会要求补齐账号分组。
- 后端持久化实体、接口路径和前端路由已按账号分组命名收敛；已应用旧 changeset 的本地数据库需要重建或按环境策略清理 Liquibase checksum。
- 本轮按“不兼容历史数据、彻底清理”的口径删除 `supports_realtime` changelog 列定义和旧 Realtime API 暴露；已应用旧 changeset 的本地数据库需要重建或按环境策略清理 Liquibase checksum。

## 验收标准

- 凭证列表中点击编辑打开弹窗并可保存，不进入 `/credentials/:id` 详情编辑页。
- 创建/编辑凭证未选择账号分组时阻止提交。
- Codex 接入入口在上游凭证页可达，一级导航不再单独强调 Codex。
- 控制台不再出现 Live Session / Realtime 入口，Admin Live Session Controller 不再暴露。
- `/v1/realtime` 和 `/v1/realtime/client_secrets` 不再作为公开功能性服务 API 暴露。
- `web` typecheck 与后端 `compileJava` 通过。

## 实施结果

- 上游凭证列表页已改为弹窗编辑，旧 `credential-detail-page` 和 `/credentials/:id` 路由已删除。
- 上游凭证创建/编辑都要求选择账号分组；后端 `CredentialAdminService`、`AccountAdminService`、`OfficialAccountAdminService` 对缺失或无效账号分组拒绝保存。
- `AccountGroup`、`UpstreamAccountGroup`、`DistributedKeyAccountGroupBinding`、`/admin/account-groups`、`/console/account-groups`、`groupId/groupName/defaultGroup` 已替代旧命名。
- Codex 接入入口放在上游凭证页和接入分组内，一级导航不再单列 Codex。
- 控制台面向用户文案统一为“账号分组”，运行代码和资源中不再出现旧账号归集文案。
- Live Session 与 Realtime Controller、WebSocket 配置、Ops 面板、能力声明、资源类型、测试 fixture 残留均已清理。
- 已执行 `bun run typecheck`、`.\gradlew.bat compileJava`、`.\gradlew.bat compileTestJava` 并通过；`git diff --check` 仍报告工作区既有 unrelated whitespace 问题，未在本轮修改。
