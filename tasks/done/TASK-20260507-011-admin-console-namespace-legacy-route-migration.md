# TASK-20260507-011 Admin Console 命名空间迁移与旧路由兼容

状态：Done  
优先级：High  
排期：P1-06  
来源：[REQ-20260507-001 社区 Portal 与后台 Console 角色化界面任务体系](../../docs/requirements/REQ-20260507-001-portal-admin-role-surface-task-system.md)
关联需求：[REQ-20260507-003 第二批最高优先级任务闭环设计](../../docs/requirements/REQ-20260507-003-next3-priority-closure-design.md)

## 背景

当前后台管理员页面位于根路径下，`/login` 登录后进入由 `RequireAdminAuth` 和 `AppLayout` 保护的管理路由。这在技术上可用，但产品面不够清楚，也不利于把 Public Site、Community Portal、Admin Console 三类入口长期区分。

## 目标

- 将后台管理端主路径迁移到 `/console/*`。
- 保留旧根管理路由 redirect，保护已有收藏、部署文档和测试。
- 明确 `/login` 是管理员登录，`/portal/login` 是社区用户登录。
- 为后续 Admin Console 导航重整提供稳定路由结构。

## 范围

- 前端 router 路由树调整。
- 旧路径 redirect map，例如 `/keys` 到 `/console/keys`、`/request-logs` 到 `/console/request-logs`。
- 管理端导航链接、breadcrumb、测试 helper 的路径更新。
- 文档中的后台入口链接更新。

## 非目标

- 不改 Portal 路由。
- 不改变 Admin API namespace。
- 不在本任务内重做页面视觉。

## 详细设计

- 新增 `/console` layout route，继续使用 `RequireAdminAuth` 与 `AppLayout`。
- 根路径可重定向到 `/console/incidents` 或新的 Console dashboard。
- 旧管理路径保留 redirect，并在测试中覆盖至少 key、account-pool、request-log、ops、settings 等代表性路径。
- `AppLayout` 内部链接统一输出 `/console/*`。
- 文档明确短期兼容策略和未来是否移除旧路径。

## 验收标准

- 管理员可以通过 `/console/*` 访问所有后台页面。
- 旧根管理路由仍可跳转到新路径。
- `/portal/*` 不受影响。
- 管理端登录与社区登录入口清晰区分。
- 前端路由测试覆盖新路径、旧路径 redirect 和未登录拦截。

## 风险

- 路由迁移容易造成测试快照和文档链接大面积变化，需要分批更新。
- 旧路径 redirect 必须避免与未来 Public Site 根路径冲突。

## 进度记录

- 2026-05-07：进入实现批次，设计 `/console/*` 新命名空间、旧管理路径 redirect map 与登录入口区分验证。
- 2026-05-07：完成 `/console` layout route、旧根管理路径 redirect、导航链接 canonical 化、breadcrumb 兼容和路由测试更新。

## 实现结果

- 管理端主路径迁移到 `/console/*`，继续使用 `RequireAdminAuth` 和 `AppLayout`。
- 旧路径如 `/operations/backups` 自动跳转到 `/console/operations/backups`，保留 query/hash。
- 导航链接统一输出 `/console/*`，`resolveRouteMeta` 同时兼容 legacy 和 canonical 路径。
- `/login` 保持管理员登录，`/portal/login` 与 `/portal/*` 不受影响。

## 验证结果

- `bun run test -- src/app/route-surfaces.test.ts src/app/operations-router.test.tsx src/app/layout.test.tsx` 通过。
- `bun run typecheck` 通过。
- `bun run test` 通过。
- Vite 服务已启动在 `http://127.0.0.1:5173`；in-app Browser 因 Node `22.20.0` 低于插件要求 `22.22.0` 未能自动截图验证。

## 遗留问题

- 需升级本机 Node 后补一次 Browser 插件渲染截图验证；自动化单元和路由回归已通过。
