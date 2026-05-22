# REQ-20260522-001 账号分组详情入口统一

状态：Done  
日期：2026-05-22  
上游来源：用户指令“从已录入凭证的详情页面打开账号分组详情，和直接从账号分组界面进入账号分组详情，两个界面不一致，统一融合一下”

## 背景

已录入凭证详情中的“打开账号分组”会跳转到 `/console/account-groups/:id` 完整账号分组详情页；账号分组列表中的“查看”当前打开本页内的账号分组详情弹窗。两条入口展示的信息层级、可操作能力和页面结构不一致，用户会感知为两个不同的详情界面。

## 目标

- 将账号分组详情收敛为同一个权威详情页。
- 从已录入凭证详情进入和从账号分组列表进入时，最终看到同一套账号分组详情界面。
- 完整详情页保留并补齐列表弹窗原有的基础信息、能力范围、编辑、启停、删除等能力。
- 账号分组列表保持扁平，不再用详情弹窗承载第二套详情视图。

## 范围

- `web/src/features/accounts/account-groups-page.tsx`
- `web/src/features/accounts/account-group-detail-page.tsx`
- `web/src/features/accounts/account-groups-page.test.tsx`
- `web/src/features/accounts/account-group-detail-page.test.tsx`
- 本地任务索引与需求索引。

## 非目标

- 不改后端 API 契约。
- 不改变凭证详情页已有跳转路径。
- 不恢复已下线的官方账号运行态独立页面。
- 不做账号分组以外的导航重构。

## 风险

- 账号分组完整详情页已有 Codex 运行态、OAuth/API Key 成员、导入和绑定功能，新增编辑/启停/删除需要避免状态刷新冲突。
- 删除当前正在查看的账号分组后，需要跳转回账号分组列表，避免停留在失效详情页。
- 原列表页测试依赖详情弹窗，需要改为验证导航入口。

## 验收标准

1. 账号分组列表点击“查看”进入 `/console/account-groups/:id`，不再打开列表页详情弹窗。
2. 已录入凭证详情里的“打开账号分组”和账号分组列表入口进入的是同一个完整详情页。
3. 完整账号分组详情页展示基础信息、能力范围、成员信息和原有治理/导入能力。
4. 完整账号分组详情页支持编辑、启停、删除账号分组。
5. 相关前端测试与类型检查通过。

## 测试边界

- `npm run typecheck`
- 账号分组与凭证相关定向 vitest。
- 必要时浏览器检查入口跳转。

## 实现结果

- `web/src/features/accounts/account-groups-page.tsx`：移除本页账号分组详情弹窗，列表“查看”改为链接到 `/console/account-groups/:id`。
- `web/src/features/accounts/account-group-detail-page.tsx`：完整详情页补齐基础信息、能力范围、编辑、启停、删除能力。
- 完整详情页编辑弹窗使用下拉和勾选模式维护提供方、协议、客户端、支持模型，避免主要枚举继续手写。
- 删除账号分组成功后跳转回 `/console/account-groups`，避免停留在失效详情页。
- 已录入凭证详情中的“打开账号分组”保持原路径，因此与列表入口进入同一个完整详情页。

## 验证结果

- `npm run typecheck`：通过。
- `npm test -- --run src/features/accounts/account-groups-page.test.tsx src/features/accounts/account-group-detail-page.test.tsx src/features/credentials/credentials-page.test.tsx`：3 个测试文件、15 个测试通过。
- 静态检查确认账号分组列表页不再保留 `selectedGroupId`/`groupDetailQuery` 弹窗详情状态，列表入口和凭证详情入口均指向 `/console/account-groups/:id`。

## 关联任务

- [TASK-20260522-001](../../tasks/done/TASK-20260522-001-account-group-detail-entry-unification.md)
