# TASK-20260507-009 Portal/Admin 路由、身份与产品面边界基线

状态：Done  
优先级：High  
排期：P0-02  
来源：[REQ-20260507-001 社区 Portal 与后台 Console 角色化界面任务体系](../../docs/requirements/REQ-20260507-001-portal-admin-role-surface-task-system.md)
关联需求：[REQ-20260507-002 前三 P0 任务闭环设计](../../docs/requirements/REQ-20260507-002-top3-p0-closure-design.md)

## 背景

当前项目已经通过 `/portal/*` 和根路径管理路由区分社区用户与后台管理员，但产品面边界还不够显式。后台管理端没有独立 `/console/*` 命名空间，Portal、Console、Public Site 的用户对象、导航语言、权限边界和术语映射也没有统一事实源。

## 目标

- 定义 Public Site、Community Portal、Admin Console 三个产品面的路由与身份边界。
- 明确 Anonymous、Community User、Team Owner、Support Admin、Platform Admin 的能力矩阵。
- 为后续路由迁移、权限回归和 UI 文案提供单一事实源。
- 保持现有路由兼容，避免影响已部署入口。

## 范围

- 更新前端路由设计文档和导航分组设计。
- 梳理现有 `/portal/*`、`/login`、根路径管理路由和建议 `/console/*` 路由映射。
- 明确 API namespace：`/portal/*` 面向社区用户，`/admin/*` 面向管理员，公开 API 单独标注。
- 产出术语映射：用户可见词、管理员可见词、内部实体名。

## 非目标

- 不在本任务内完成所有页面重构。
- 不迁移数据库 schema。
- 不改变现有认证算法或 token 格式。

## 详细设计

- 新增或更新 `docs/requirements` 中的路由/身份矩阵章节。
- 在前端建立 `routeSurface` 或等价常量，标记 `public`、`portal`、`console` 三类路由。
- 为后续迁移预留旧路径到 `/console/*` 的 redirect map。
- 梳理 Portal 不可见资源：上游账号、Provider secret、账号池内部候选、全局日志、全局策略。
- 梳理 Admin 操作需要审计的能力：代查用户、代创建 Key、导入官方账号、修改路由策略、撤销授权。

## 验收标准

- 能通过文档和代码常量清楚判断任意前端路由属于哪个产品面。
- 能通过权限矩阵判断任意角色是否可访问关键能力。
- `/portal/*` 与 `/admin/*` 的 API 边界明确，后续测试可引用。
- 旧管理端根路径的兼容策略明确。

## 风险

- 仅重命名路由不能等同于权限隔离，必须与后续 API guard 回归联动。
- 如果术语映射过度简化，可能让管理员丢失必要上下文。

## 实现结果

- 新增 `web/src/app/route-surfaces.ts`，提供 `RouteSurface`、`matchRouteSurface`、`getRouteSurface`、`toConsolePath`、`isPortalRoute`、`isConsoleRoute`。
- 当前 `/portal/*` 归属 `portal`，`/login` 和既有管理端根路径归属 `console`，未知营销类路径归属 `public`。
- 保持现有 router 不迁移，仅提供 legacy console route 到未来 `/console/*` 的 canonical path，供后续 `TASK-20260507-011/012` 复用。

## 验证记录

- `bun run test -- src/app/route-surfaces.test.ts`：通过，11 tests。
- `bun run typecheck`：通过。
- `bun run test`：通过，50 files / 105 tests。

## 遗留问题

- `/console/*` 实际迁移、旧路由 redirect 和角色化工作台仍由 `TASK-20260507-011/012` 承接。
