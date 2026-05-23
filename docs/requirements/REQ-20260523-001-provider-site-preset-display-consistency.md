# REQ-20260523-001 厂商管理与预设导入口径一致性

## 背景

用户反馈厂商管理页面中“厂商聚合”和“预设导入”看起来重合，且展示数据不一致。例如已导入的小米 MiMo 在厂商聚合中显示允许的入口协议为 `OPENAI_COMPATIBLE_GENERIC`，而预设导入中的小米 MiMo 厂商协议入口包含 `ANTHROPIC_DIRECT`，造成“同一厂商到底支持哪个入口协议”的理解冲突。

## 目标

- 明确“厂商/API 入口”“厂商协议入口”“预设导入”三层数据含义。
- 核对 MiMo 的 preset JSON、后端响应与前端展示是否一致。
- 若问题来自 UI 文案或列字段混用，收敛页面展示口径，让用户能同时看到站点默认入口与下挂协议入口列表。
- 避免把 `siteKind`、`providerType`、`protocolSuite` 混在同一概念下展示。

## 范围

- `src/main/resources/provider-catalog.json` 中 MiMo preset 与 protocol endpoints。
- `ProviderSiteRegistryService` / `ProviderSiteAdminService` 的 preset 与已导入站点响应映射。
- `web/src/features/provider-sites/provider-sites-page.tsx` 的厂商聚合与预设导入表格展示。
- 必要的前端类型与测试。

## 非目标

- 不删除 MiMo 的 Anthropic-compatible 协议入口能力。
- 不改变真实路由、凭证绑定或模型发现逻辑。
- 不修改用户已有 MiMo 凭证。
- 不扩大到完整厂商管理信息架构重做。

## 风险

- 如果只改文案不展示下挂 protocol endpoints，用户仍会误以为两块数据冲突。
- 如果把站点默认 `siteKind` 当成唯一协议入口，会掩盖多协议厂商的真实能力。
- 预设导入接口当前可能未返回 protocol endpoints，需要后端补字段或前端从已导入站点回查。

## 排查结论

- 当前数据模型不是冲突，而是两层概念被页面混在同一个“协议入口”文案下展示。
- `siteKind` 表示厂商/API 入口的默认站点类型。小米 MiMo 的默认站点类型是 `OPENAI_COMPATIBLE_GENERIC`。
- `protocolEndpoints` 表示该厂商/API 入口下挂的运行时协议入口。小米 MiMo 会生成：
  - `xiaomi_mimo.openai_compatible` / `OPENAI_COMPATIBLE` / `https://token-plan-sgp.xiaomimimo.com/v1`
  - `xiaomi_mimo.anthropic_compatible` / `ANTHROPIC_DIRECT` / `https://token-plan-sgp.xiaomimimo.com/anthropic`
- 预设导入接口此前没有返回即将导入的 `protocolEndpoints`，前端只能显示顶层 `siteKind`，而已导入表又有下挂入口列表，因此造成“厂商聚合和预设导入数据不一致”的观感。

## 实现结果

- `ProviderSitePresetResponse` 增加 `protocolEndpoints` 字段，预设列表可以直接返回即将导入的协议入口预览。
- `ProviderSiteRegistryService` 复用 preset import 的 `protocolEndpointSeeds` 生成预设协议入口响应，避免导入逻辑和预览逻辑分叉。
- 厂商聚合表格拆分为“站点类型”和“厂商协议入口”，不再把顶层 `siteKind` 泛称为协议入口。
- 预设导入表格拆分为“默认站点类型”和“将导入协议入口”，MiMo 可同时展示 OpenAI-compatible 与 Anthropic-compatible 两个入口。
- 前端搜索同步覆盖已导入站点的 `protocolSuite`、`providerType` 和 endpoint `siteKind`。

## 验收标准

- 小米 MiMo 在厂商聚合中能看出默认站点类型与下挂协议入口是两层信息。
- 预设导入中能看出 MiMo 将导入 OpenAI-compatible 与 Anthropic-compatible 两个协议入口，且不再与厂商聚合表头冲突。
- 前端文案不再把 `siteKind` 泛称为“协议入口”。
- 后端/前端定向验证通过。

## 验证记录

- 通过：`.\gradlew.bat compileJava compileTestJava test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ProviderSiteAdminControllerTests"`
- 通过：`bun run typecheck`
- 通过：`bun run test -- --run src/features/provider-sites/provider-sites-page.test.tsx`
- 通过：`bunx eslint src/features/provider-sites/provider-sites-page.tsx src/features/provider-sites/provider-sites-page.test.tsx src/features/provider-sites/types.ts`
- 浏览器抽查：登录 `http://localhost:5173/console/provider-sites` 后，页面已渲染“站点类型”“厂商协议入口”“将导入协议入口”等新列，已导入 MiMo 行显示 `xiaomi_mimo.openai_compatible` 与 `xiaomi_mimo.anthropic_compatible`，无 console warning/error 和框架 overlay。
- 浏览器限制：当前 8080 后端进程为本轮改动前已启动的旧进程，`/admin/provider-sites/presets` 响应尚未包含新增 `protocolEndpoints` 字段，所以预设导入行在该旧进程下仍显示兼容兜底“导入默认入口”。重启后端加载本轮变更后，预设导入表会按新响应显示将导入的协议入口列表。

## 当前状态

Done
