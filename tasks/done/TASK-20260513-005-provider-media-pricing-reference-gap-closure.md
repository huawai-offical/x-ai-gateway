# TASK-20260513-005 Provider/Media/价格同步参考差距补齐

状态：Done  
优先级：Medium  
类型：子任务  
父任务：[TASK-20260513-001](../done/TASK-20260513-001-reference-translation-admin-portal-audit.md)  
上游来源：[REQ-20260513-003](../../docs/requirements/REQ-20260513-003-provider-public-site-closure.md)、[REQ-20260513-001](../../docs/requirements/REQ-20260513-001-reference-translation-admin-portal-audit.md)、[REP-20260513](../../docs/reports/REP-20260513-reference-translation-admin-portal-audit.md)

## 背景

`new-api` 在 Provider/Channel 覆盖、媒体端点和价格能力上仍有宽度优势。当前项目已有 Provider Catalog、Provider Site、Capability Matrix 和 Provider Smoke 方向，但价格同步、媒体端点产品化和更多专属 Provider Adapter 需要继续闭环。

## 目标

- 对齐 `new-api` 的 channel/provider 覆盖清单，标记当前项目已支持、兼容支持、未支持。
- 梳理媒体端点：audio、image、video、music、realtime、rerank、web_search 等。
- 建立价格同步来源和更新策略，避免手工静态价格长期漂移。
- 为高价值 Provider 增加 dedicated adapter 或明确走 OpenAI-compatible 的边界。
- 增加真实 smoke 的配置说明和失败分级。

## 非目标

- 不在本任务内一次性实现所有 Provider。
- 不使用生产 secret 写入仓库。
- 不绕过 provider 官方限制做非授权访问。

## 输入

- `src/main/resources/provider-catalog.json`
- Provider Site/Capability Matrix 相关后端与前端。
- 参考项目 `new-api-main/relay/channel`。

## 输出

- Provider 差距表。
- 价格同步设计与实现。
- 媒体端点支持等级。
- Provider smoke 验证记录。

## 影响范围

- Provider Catalog。
- Provider Site。
- Capability Matrix。
- 价格与计费展示。
- Provider smoke。

## 依赖

- 可用测试 key 或 mock secret。
- 官方 provider model/pricing 来源。

## 风险

- 官方价格和模型列表更新频繁。
- 免费 key 或测试 key 可能受额度限制。
- 媒体端点返回格式差异大，需要避免误标支持等级。

## 验收标准

- 形成当前项目和 `new-api` provider/channel 的差距矩阵。
- 至少补齐一批高优 provider 的价格或模型同步策略。
- 媒体端点支持等级可在后台查看。
- smoke 失败能区分认证失败、额度不足、网络失败、参数不支持。

## 测试边界

- Provider catalog loader 测试。
- 价格同步 parser 测试。
- smoke 使用 mock 和可选真实 key 双路径验证。

## 当前状态

Done。

## 实现结果

- 新增后端只读 API：`GET /admin/provider-reference-gap`。
- 新增 `ProviderReferenceGapService`，把 `new-api relay/channel` 参考清单、当前 provider catalog、媒体能力面和价格同步状态合并成可展示矩阵。
- 新增后台页面 `/console/provider-reference-gap`，展示：
  - Provider/channel 覆盖状态：`SUPPORTED`、`COMPATIBLE`、`MISSING`。
  - 媒体与非 Chat 能力：audio、image、video、music、realtime、rerank、web_search。
  - 价格同步来源、策略、真实 key smoke 要求和失败分类。
- Admin 菜单在“路由”分组增加“参考差距”入口。
- 明确 Codex 是 account proxy/runtime namespace，不误归类为通用 provider catalog preset。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderReferenceGapServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ProviderReferenceGapAdminControllerTests"`：通过。
- `bunx vitest run src\features\provider-sites\provider-reference-gap-page.test.tsx src\app\route-surfaces.test.ts src\app\navigation.test.ts`：通过。
- `bun run typecheck`：通过。
- `bun run lint`：通过。

## 遗留与后续

- 当前价格同步状态是事实矩阵与策略合同，尚未接定时官方价格抓取器。
- 真实 provider smoke 仍应通过环境变量注入测试 key，不能把 secret 写入仓库。
