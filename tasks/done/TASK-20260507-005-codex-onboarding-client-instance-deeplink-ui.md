# TASK-20260507-005 Codex 接入向导、Client Instance 与 Deep Link UI 闭环

状态：Done  
优先级：High  
排期：P1-09  
来源：[REP-20260507 Codex 账户反代与 UI/UX 深度差距分析](../../docs/reports/REP-20260507-codex-proxy-uiux-gap-analysis.md)

## 背景

当前项目已经有 distributed key、account pool、official account、client instance 和一次性 Deep Link grant，但这些能力散落在多个 API 与页面中。用户真正想做的是“让 Codex CLI 接入并可用”，不应该理解所有内部实体后才能完成。

## 目标

- 提供一条 Codex 接入向导：导入账号、选择账号池、创建访问 key、创建 client instance、发行 Deep Link、验证请求。
- 将一次性 grant、masked key、安全提示和配置模板统一展示。
- 让用户能在向导内完成 smoke、复制配置、生成 Deep Link 或 plugin message。
- 向导结果回写到 docs/onboarding 或操作日志。

## 详细设计

- 前端新增 `Codex Onboarding` route 或在现有 key/account 页面加入向导入口。
- 步骤建议：选择接入方式、导入/选择官方账号、选择/创建账号池、创建 distributed key、注册 client instance、生成一次性授权、运行 smoke。
- 每一步都显示当前完成状态和下一步，不要求用户记忆 providerType、distributedKeyId、clientInstanceId。
- 后端补一个 orchestration preview API，避免前端硬拼多个实体之间的关系。
- Deep Link 页面必须明确：link 不携带长期 secret，grant 过期、只能消费一次。

## 验收标准

- 新用户可通过一个向导完成 Codex CLI 接入。
- 向导支持跳过已有资源，复用已有账号池/key/instance。
- 生成的 Deep Link 和 plugin message 与后端 grant API 对齐。
- smoke 成功/失败均给出下一步操作建议。
- 前端测试覆盖完整流程、复用资源、失败回退和过期 grant。

## 风险

- 向导不能把长期 secret 放进 URL、localStorage 或浏览器历史。
- orchestration API 只能聚合已有能力，不应创造新的隐式权限绕过。

## 本批实施设计

- 关联需求：[REQ-20260507-006 第四批最高优先级任务闭环设计](../../docs/requirements/REQ-20260507-006-next3-codex-onboarding-portal-runtime-closure.md)
- 新增 `/console/accounts/connect/codex` 专用向导页，普通 OAuth 仍走泛化连接页。
- 向导按资源流组织：账号池、官方账号、访问 Key、Client Instance、Deep Link/Plugin grant、smoke。
- 新建访问 Key 后立即绑定账号池并启用，复用已有 Key 时只展示 masked onboarding pack，不回显完整 secret。
- Client Instance 授权使用一次性 grant，Deep Link 明确短 TTL、一次性消费和不携带长期 secret。
- 测试覆盖向导步骤、Key 绑定启用、Client Instance 注册和授权结果展示。

## 进度记录

- 2026-05-07：进入第四批最高优先级任务闭环，开始实现 Admin Codex 接入主路径。
- 2026-05-07：完成 `/console/accounts/connect/codex` 专用向导页，覆盖账号池、访问 Key、Client Instance、一次性授权和 onboarding pack smoke。
- 2026-05-07：复用已有 Key 时要求一次性 secret export token，新建 Key 只在内存态保留 fullKey 用于发行短 TTL grant，避免长期 secret 进入 URL 或 localStorage。
- 2026-05-07：补充 `codex-onboarding-page.test.tsx`，覆盖向导渲染、配置片段、已有 Key 发行 Deep Link 授权。

## 验证记录

- `bun run test -- src/features/accounts/codex-onboarding-page.test.tsx src/features/accounts/account-pool-detail-page.test.tsx src/features/portal/portal-home-page.test.tsx`
- `bun run typecheck`
- `bun run build`
- Browser smoke：打开 `/console/accounts/connect/codex`，未登录状态按预期重定向到控制台登录页。

## 交付结果

- 新增 `web/src/features/accounts/codex-onboarding-page.tsx`。
- 更新 `web/src/app/router.tsx`，让 Codex 接入走专用页面，普通 provider 仍走泛化 OAuth 页面。
- 新增 `web/src/features/accounts/codex-onboarding-page.test.tsx`。

## 后续建议

- 后续可补一个后端 orchestration preview API，把多实体状态聚合从前端迁回后端，减少向导对多个 Admin API 的编排负担。
