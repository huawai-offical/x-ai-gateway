# TASK-20260507-010 Community Portal Codex 自助接入与个人用量界面

状态：Done  
优先级：High  
排期：P1-10  
来源：[REQ-20260507-001 社区 Portal 与后台 Console 角色化界面任务体系](../../docs/requirements/REQ-20260507-001-portal-admin-role-surface-task-system.md)

## 背景

社区用户使用系统的目标不是管理 Provider、账号池和路由策略，而是快速拿到自己的接入方式、理解额度与用量、处理失败请求。当前 Portal 已有概览、订阅、Key、兑换和公告，但还缺少面向 Codex CLI 的用户级接入路径，以及个人视角的 usage、smoke 和错误解释。

## 目标

- 将 Portal 首屏从资源入口升级为“下一步行动”界面。
- 为社区用户提供 Codex CLI 接入卡片、Key 状态、个人 usage、订阅和余额摘要。
- 与 `TASK-20260507-005` 的 Codex 接入向导联动，隐藏不必要的内部实体。
- 为失败态提供用户能理解的原因和下一步，例如额度不足、Key 禁用、授权过期、模型不可用。

## 范围

- Portal 首页信息架构。
- Portal Key、usage、subscription、redeem 的用户任务入口整合。
- 用户级 Codex 接入状态：未接入、待验证、可用、失败、过期。
- Portal 空态、错误态和 smoke 结果文案。

## 非目标

- 不暴露账号池内部候选、上游官方账号、Provider secret、全局路由策略。
- 不在 Portal 中提供管理员级热切换、全局故障恢复或系统配置。
- 不读取用户本机 Codex 配置文件。

## 详细设计

- Portal 首页提供四个核心区域：接入状态、订阅与余额、访问 Key、最近用量/失败。
- Codex 接入卡片只显示用户需要的 endpoint、masked key、client instance alias、Deep Link 状态和 smoke 结果。
- 对 Key 和 client instance 使用用户可读 alias，不要求用户输入裸 ID。
- 用户 usage 只展示自己的聚合数据和可解释维度，例如日期、模型、状态、费用、失败原因。
- smoke 失败时给出可执行动作：刷新授权、检查余额、重新生成 Key、联系管理员。

## 验收标准

- 新社区用户进入 Portal 后可以在 3 个主动作内开始 Codex 接入。
- Portal 不出现上游账号 secret、账号池内部候选或全局策略字段。
- 用户可查看自己的 Key、订阅、余额、usage 和最近失败摘要。
- smoke 成功和失败都有明确状态与下一步。
- 前端测试覆盖未接入、已接入、额度不足、授权过期和 Key 禁用。

## 风险

- Portal 聚合 API 必须做用户级过滤，不能直接复用 Admin 列表接口。
- Deep Link 和 grant 展示必须避免长期 secret 泄露。

## 本批实施设计

- 关联需求：[REQ-20260507-006 第四批最高优先级任务闭环设计](../../docs/requirements/REQ-20260507-006-next3-codex-onboarding-portal-runtime-closure.md)
- Portal 首页新增 Codex 接入卡片，基于用户自己的 Key、订阅、余额和最近 ledger 推导状态。
- 状态覆盖：未接入、可用、额度不足、Key 停用、授权过期。
- 展示 endpoint、masked key、订阅状态、Token 余额、最近使用和下一步动作；不展示账号池、上游账号和全局策略字段。
- Portal 测试覆盖未接入、可用、额度不足和 Key 停用。

## 进度记录

- 2026-05-07：进入第四批最高优先级任务闭环，开始实现社区用户 Codex 自助接入面。
- 2026-05-07：Portal 首页新增 `社区 Codex 接入` 卡片，根据用户 Key、订阅、余额和 ledger 推导未接入、可用、额度不足、Key 停用、授权过期。
- 2026-05-07：Portal 只展示 API Base、masked key、默认模型、额度、最近使用和用户动作入口，不展示后台治理实体。
- 2026-05-07：补充 `portal-home-page.test.tsx`，覆盖社区 Codex 自助接入卡片和后台字段不外露。

## 验证记录

- `bun run test -- src/features/accounts/codex-onboarding-page.test.tsx src/features/accounts/account-pool-detail-page.test.tsx src/features/portal/portal-home-page.test.tsx`
- `bun run typecheck`
- `bun run build`

## 交付结果

- 更新 `web/src/features/portal/portal-home-page.tsx`。
- 新增 `web/src/features/portal/portal-home-page.test.tsx`。

## 后续建议

- 后续可新增 Portal 专用 usage 聚合 API，补充个人 request 成功率、最近失败原因、按模型/日期维度的 token 与费用统计。
