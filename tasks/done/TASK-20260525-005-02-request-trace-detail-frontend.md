# TASK-20260525-005-02 请求详情追踪前端

## 类型

子任务 / task spec

## 背景

后端返回 trace detail 后，Admin 需要在链路追踪和请求日志入口中展示请求详情阶段，帮助管理员理解下游请求、转换过程和上游响应。

## 目标

- 扩展前端 trace 类型，接收 `traceDetails`。
- 链路追踪页展示请求详情阶段列表与 JSON 内容。
- 请求日志详情提供到链路追踪的深链入口。
- 补充前端测试。

## 非目标

- 不实现 replay。
- 不在前端展示未脱敏密钥。
- 不新建独立请求详情页面。

## 上游来源

- `docs/requirements/REQ-20260525-005-request-trace-detail-audit.md`
- `tasks/in-progress/TASK-20260525-005-request-trace-detail-audit-parent.md`

## 输入

- `web/src/features/traces/`
- `web/src/features/request-logs/`

## 输出

- Trace detail 展示 UI。
- 请求日志到链路追踪的深链。
- 前端定向测试。

## 影响范围

- Admin 请求日志页。
- Admin 链路追踪页。

## 依赖

- 后端 `traceDetails` 响应字段。
- 现有前端 API client 和分页/弹窗组件。

## 风险

- JSON 内容过长，需要滚动与断行。
- 阶段名需要中文化，避免把内部 enum 直接暴露给用户。

## 验收标准

- trace detail 阶段可见。
- payload/metadata/hash/截断/脱敏标记可见。
- 请求日志可快速跳转到链路追踪。
- 前端测试通过。

## 测试边界

- `cd web; bun run test -- traces-page.test.tsx`
- `cd web; bun run test -- request-logs-page.test.tsx`
- `cd web; bun run typecheck`

## 当前状态

Done

## 实现结果

- 扩展 trace 前端类型以接收 `traceDetails`。
- 链路追踪页展示请求详情阶段、方向、内容类型、payload、metadata、hash、长度、脱敏和截断标记。
- 请求日志页提供到链路追踪详情的深链入口。
- 补充 trace/request-log 前端测试。

## 验证结果

- `cd web; bun run test src/features/traces/traces-page.test.tsx src/features/request-logs/request-logs-page.test.tsx` 通过。
- `cd web; bun run typecheck` 通过。

## 遗留问题

- 大 payload 展示依赖当前滚动容器和后端截断；后续如引入对象存储留痕，需要再补下载或引用查看入口。
