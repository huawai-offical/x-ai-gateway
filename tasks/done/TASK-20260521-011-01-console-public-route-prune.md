# TASK-20260521-011-01 控制台与公开重复入口下线

状态：Done  
优先级：Critical  
上游来源：[TASK-20260521-011](./TASK-20260521-011-console-surface-prune-logo-credential-redis.md)

## 任务类型

子任务

## 背景

用户明确要求删除变更维护系列运维功能、首页状态/价格/文档功能，并认为错误规则导航与治理策略重复。

## 目标

- 从控制台导航中删除“变更维护”一级分组。
- 将 `/console/operations/*` 旧路由重定向到智能运维总览或现役主路径。
- 删除或重定向 `/console/error-rules`，保留治理策略主入口。
- 删除公开首页状态、价格、文档入口和独立公开路由。

## 非目标

- 不删除后端仍被主线使用的健康、日志、治理策略、file/vector 支撑接口。
- 不处理门户 `/portal/status`，除非验证发现它引用公开首页状态入口。

## 输入

- `web/src/app/navigation.ts`
- `web/src/app/router.tsx`
- `web/src/app/route-surfaces.ts`
- `web/src/features/public/`
- 相关路由与导航测试

## 输出

旧入口下线、路由重定向、测试断言更新。

## 验收标准

- [x] 控制台侧栏没有“变更维护”和“错误规则”。
- [x] 旧 operations/error-rules 路径不加载旧页面。
- [x] 公开首页没有状态、价格、文档入口。
- [x] `/docs`、`/pricing`、`/status` 不再作为独立公开页面。

## 测试边界

- `navigation`、`route-surfaces`、`router` 相关测试
- 浏览器验证公开首页和控制台侧栏

## 当前状态

已完成。通过导航、公开路由、operations 路由和 public pages 定向测试验证。
