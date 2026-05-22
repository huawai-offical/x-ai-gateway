# TASK-20260521-002 上游凭证与账号分组收敛

## 任务类型

父任务 + 本轮实现子任务

## 背景

来源：`docs/requirements/REQ-20260521-002-upstream-credentials-group-live-scope.md`

用户要求直接推进：

1. 编辑上游凭证通过弹窗修改，不跳转其他页面，不展示过多无法编辑数据。
2. Codex 凭证也属于上游凭证。
3. 上游凭证都需要被导入到一个账号分组。
4. 取消旧账号归集概念，统一改为账号分组。
5. Live 类功能先删除。
6. Realtime 类能力也删除，不再保留。

## 目标

- 改造上游凭证列表页编辑体验。
- 将 Codex 接入入口放回上游凭证语境。
- 将账号归属文案改为账号分组，并强制凭证归组。
- 删除 Live Session 与 Realtime 控制台入口、Admin API 和 OpenAI Realtime 公开 API 暴露。

## 非目标

- 不新增自动化测试。
- 不保留旧账号归集命名兼容层。
- 不做历史数据库兼容迁移；已应用旧 changeset 的环境按重建库或清理 Liquibase checksum 处理。

## 输入

- `web/src/features/credentials/credentials-page.tsx`
- `web/src/app/navigation.ts`
- `web/src/app/router.tsx`
- `web/src/components/app/app-shell.tsx`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminService.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/LiveSessionAdminController.java`
- Realtime / Live 相关 Controller、WebSocket 配置、前端 Ops Realtime 面板和能力声明。

## 输出

- 可弹窗编辑的上游凭证列表页。
- 凭证创建/编辑账号分组必选。
- Codex 接入入口归入上游凭证。
- Live Session 控制台入口和 Admin Controller 下线。
- Realtime 对外 API、WebSocket handler、Ops Realtime 面板和能力矩阵声明下线。

## 影响范围

- 管理控制台接入模块。
- 上游凭证 Admin API 创建/更新校验。
- Live Session Admin API 暴露面。

## 依赖

- 现有 `/admin/account-groups` 数据作为账号分组来源。
- 现有 `/admin/credentials` 创建/更新接口。

## 风险

- 历史未归组凭证在保存时需要补选账号分组。
- 路由下线后旧书签会被重定向或失效。

## 验收标准

- [x] 编辑按钮打开弹窗并保存凭证。
- [x] 创建/编辑凭证必须选择账号分组。
- [x] Codex 接入入口出现在上游凭证页。
- [x] 导航、搜索、前端路由和 Admin API 使用账号分组命名。
- [x] Live Session 控制台路由和 Admin Controller 下线。
- [x] Realtime 对外 API、WebSocket handler、Ops Realtime 面板和能力声明下线。
- [x] `bun run typecheck` 通过。
- [x] `.\gradlew.bat compileJava` 通过。
- [x] `.\gradlew.bat compileTestJava` 通过。

## 测试边界

用户要求本轮不做测试。仅做类型检查和编译确认；为保证删除 Realtime 后测试源码不残留旧 API，本轮额外执行 `.\gradlew.bat compileTestJava`，未运行测试用例。

## 实施结果

- 上游凭证列表页提供编辑弹窗，仅展示可编辑字段；旧详情页和详情路由已删除。
- `CredentialAdminService`、`AccountAdminService`、`OfficialAccountAdminService` 强制凭证/账号导入必须归入账号分组。
- `AccountGroup`、`UpstreamAccountGroup`、`DistributedKeyAccountGroupBinding`、`/admin/account-groups`、`/console/account-groups`、`groupId/groupName/defaultGroup` 已替代旧命名。
- Codex 接入入口已归入上游凭证页与接入分组。
- Live Session 与 Realtime 的前端入口、后端 Controller/WebSocket 配置、能力声明、资源枚举、OpenAI path matcher 和测试 fixture 已清理。
- 残留扫描确认运行代码、测试代码和资源中不再存在 `Realtime`、`/v1/realtime`、`LiveSession`、旧账号归集命名、旧凭证详情页引用。

## 验证结果

- `bun run typecheck`：通过。
- `.\gradlew.bat compileJava compileTestJava`：通过。
- `git diff --check`：未通过，原因是工作区既有 unrelated trailing whitespace 位于 `web/src/features/integrations/deliveries-page.tsx`、`web/src/features/keys/keys-page.tsx`、`web/src/features/workbench/workbench-page.tsx`，本轮未触碰。

## 当前状态

已完成，归档到 `tasks/done/`。
