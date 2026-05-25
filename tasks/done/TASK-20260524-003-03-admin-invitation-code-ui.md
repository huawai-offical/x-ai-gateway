# TASK-20260524-003-03 Admin 邀请码管理页面

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260524-003](TASK-20260524-003-portal-invitation-code-system-parent.md)  
上游来源：[REQ-20260524-003](../../docs/requirements/REQ-20260524-003-portal-invitation-code-system.md)

## 背景

完整邀请码系统需要 Admin 可见、可操作。页面应跟随现有用户域管理页面模式，避免把邀请码继续藏在系统参数页中。

## 目标

- 新增用户域导航入口“邀请码”。
- 新增 Admin 邀请码管理页。
- 支持批量粘贴、自动生成、编辑状态/次数/过期时间和查看使用记录。

## 非目标

- 不做单独营销报表。
- 不做 CSV 导出。
- 不重做兑换码页面。

## 输入

- `web/src/features/user-domain/promo-codes-page.tsx`
- `web/src/app/navigation.ts`
- `web/src/app/router.tsx`

## 输出

- 前端邀请码管理页面和路由。

## 影响范围

- Admin 导航、路由和用户域页面。

## 依赖

- TASK-20260524-003-01 Admin API。

## 风险

- 大量管理动作堆在一个页面里可能影响可读性。
- 文案需要保持中文单语。

## 验收标准

- TypeScript typecheck 通过。
- 页面可以加载邀请码列表并发起创建、编辑、删除、查看使用记录请求。

## 测试边界

- `bun run typecheck`。

## 当前状态

- 2026-05-24：待实施。
- 2026-05-24：已完成 `/console/invitation-codes` 页面、导航、路由、搜索/状态过滤和使用记录弹窗。

## 验证记录

- `bun run typecheck`（工作目录：`web/`）
