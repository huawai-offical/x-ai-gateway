# TASK-20260522-001 账号分组详情入口统一

状态：Done  
优先级：Critical  
上游来源：[REQ-20260522-001](../../docs/requirements/REQ-20260522-001-account-group-detail-entry-unification.md)

## 任务类型

父任务

## 背景

账号分组详情存在两个产品面：凭证详情跳转到完整详情页，账号分组列表打开本页弹窗。两者展示内容和操作入口不一致，需要融合成同一个详情体验。

## 目标

- 删除账号分组列表页的重复详情弹窗入口。
- 列表页“查看”跳转完整账号分组详情页。
- 在完整账号分组详情页补齐基础信息、能力范围、编辑、启停、删除操作。
- 保持凭证详情现有跳转可用。

## 非目标

- 不改后端接口。
- 不改变 auth.json 导入、OAuth 连接和成员详情弹窗的主流程。
- 不处理其他实体详情页融合。

## 输入

- `web/src/features/accounts/account-groups-page.tsx`
- `web/src/features/accounts/account-group-detail-page.tsx`
- `web/src/features/accounts/*.test.tsx`

## 输出

- 统一详情入口后的前端代码。
- 更新后的定向测试。
- 需求和任务回写。

## 影响范围

账号分组列表、账号分组详情、已录入凭证详情到账号分组详情的跳转一致性。

## 依赖

- 现有 `/console/account-groups/:id` 路由。
- 现有 `/admin/account-groups/:id`、`PUT /admin/account-groups/:id`、`POST /status`、`DELETE` API。

## 风险

- 编辑弹窗复用字段较多，需要避免重新引入手写模型错误。
- 删除后要跳转列表，避免详情页 stale。
- 现有测试依赖弹窗语义，需要同步到路由语义。

## 验收标准

- [x] 列表页“查看”跳转完整详情页。
- [x] 完整详情页有基础信息和能力范围。
- [x] 完整详情页支持编辑账号分组并保存。
- [x] 完整详情页支持启停和删除。
- [x] 类型检查和定向测试通过。

## 测试边界

- `npm run typecheck`
- `npm test -- --run src/features/accounts/account-groups-page.test.tsx src/features/accounts/account-group-detail-page.test.tsx src/features/credentials/credentials-page.test.tsx`

## 当前状态

已完成实现、测试和文档回写。

## 实现结果

- 账号分组列表页移除重复详情弹窗，“查看”统一跳转 `/console/account-groups/:id`。
- 完整账号分组详情页补齐原弹窗中的基础信息、能力范围、编辑、启停、删除操作。
- 完整详情页编辑弹窗支持提供方下拉、协议勾选、客户端勾选、模型搜索勾选。
- 删除账号分组成功后自动回到账号分组列表。
- 保持凭证详情“打开账号分组”原跳转路径不变，两条入口最终进入同一个详情页。

## 验证结果

- `npm run typecheck`：通过。
- `npm test -- --run src/features/accounts/account-groups-page.test.tsx src/features/accounts/account-group-detail-page.test.tsx src/features/credentials/credentials-page.test.tsx`：3 个测试文件、15 个测试通过。
- 静态检查：账号分组列表页已无 `selectedGroupId`/`groupDetailQuery` 详情弹窗状态。
