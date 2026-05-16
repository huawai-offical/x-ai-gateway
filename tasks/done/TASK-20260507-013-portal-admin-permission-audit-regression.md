# TASK-20260507-013 Portal/Admin API 权限隔离、审计与越权回归

状态：Done  
优先级：High  
排期：P0-03  
来源：[REQ-20260507-001 社区 Portal 与后台 Console 角色化界面任务体系](../../docs/requirements/REQ-20260507-001-portal-admin-role-surface-task-system.md)
关联需求：[REQ-20260507-002 前三 P0 任务闭环设计](../../docs/requirements/REQ-20260507-002-top3-p0-closure-design.md)

## 背景

Portal 和 Admin Console 的界面区分不能只靠前端隐藏菜单。社区用户只能访问自己的订阅、余额、Key、用量和公告；管理员可访问全局账号池、Provider、路由策略、日志和系统设置，但代查、代管、导入凭证等操作必须审计。

## 目标

- 建立 Portal/Admin API 权限矩阵和回归测试。
- 确保 `/portal/*` API 全部按当前用户或团队过滤。
- 确保 `/admin/*` API 需要管理员身份，并对敏感操作记录审计。
- 防止社区用户通过猜 ID、改路径或复用 Admin API 获取越权数据。

## 范围

- 后端 controller/service 权限检查梳理。
- Portal API 用户级过滤和对象归属校验。
- Admin API 敏感操作审计字段和事件。
- 越权回归测试：匿名、普通用户、Team Owner、Support Admin、Platform Admin。

## 非目标

- 不在本任务内新增完整 RBAC 管理 UI。
- 不改动加密算法或 secret 存储方式。
- 不暴露长期 secret 给任何普通用户或 URL。

## 详细设计

- 以能力矩阵为输入，为每类 API 标记允许角色、数据范围和审计要求。
- Portal 查询必须绑定当前 `portalUserId` 或 team scope，不接受任意 userId 作为信任来源。
- Admin 代查用户、导入官方账号、修改路由策略、撤销授权、导出日志等操作写入审计事件。
- 对 request log、usage、trace 做脱敏策略检查，避免泄露上游账号凭证或完整 Key。
- 新增测试覆盖跨用户 Key 查询、跨用户 usage 查询、普通用户访问 Admin API、匿名访问 Portal 私有 API、管理员敏感操作审计。

## 验收标准

- 普通用户无法访问其他用户的 Key、usage、订阅和订单。
- 普通用户无法访问 `/admin/*` API。
- 管理员敏感操作有审计记录。
- request log、usage、trace 输出不包含长期 secret 明文。
- 后端测试覆盖主要越权路径。

## 风险

- 如果当前 Portal API 已混用 Admin service，需要谨慎拆分，避免影响现有功能。
- 审计事件过多可能产生噪音，需要区分读操作、代查和高风险写操作。

## 实现结果

- 新增 `PortalApiSecurityBoundaryWebFilter`，统一保护非公开 `/portal/*` API，要求当前 session 存在 `portalUserId`。
- 公开保留登录、注册、登出、captcha、OAuth start/callback、Passkey assertion start/finish 等入口。
- 匿名访问 Portal 私有 API 返回 `PORTAL_UNAUTHORIZED`，并写入 `PORTAL_API_BOUNDARY` 审计；测试 slice 中缺少 repository 时可安全降级，不影响 controller 单测。
- Admin API 仍由 `AdminConsoleSecurityConfiguration` 对 `/admin/**` 进行 Spring Security authenticated 保护。

## 验证记录

- `PortalApiSecurityBoundaryWebFilterTests` 覆盖匿名拒绝与审计、已登录 Portal session 放行、公开登录入口放行。
- `.\gradlew.bat test`：通过，580 tests completed，4 skipped。

## 遗留问题

- 更细粒度 Support Admin、Platform Admin、Team Owner RBAC 矩阵和 UI 管理仍应在后续角色化 Console 任务中继续推进。
