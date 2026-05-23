# TASK-20260523-017 控制台 Apple-level UI/UX 精修

## 状态

Done

## 背景

用户在账号分组详情页截图中指出左侧导航与右侧内容区域的顶部对齐不够精细，并要求排查其他页面 UI/UX 是否能对标 Apple 的设计精细程度。本任务承接 `REQ-20260523-012`，先处理全局 App Shell 与第一屏观感，再抽查高频页面。

## 目标

- 修复 App Shell 侧栏滚动条与右侧标题栏之间的视觉断层。
- 统一侧栏品牌区与右侧 header 的高度节奏。
- 降低全局控制台滚动面、边框和按钮噪声。
- 抽查账号分组详情、厂商目录、上游凭证、账号分组列表四类高频页面。

## 非目标

- 不修改后端 API、数据模型、账号分组路由或厂商能力逻辑。
- 不做完整视觉重构，不引入新的设计依赖。
- 不把所有历史页面一次性全部重排；无法在本切片完成的问题需记录为后续 UI/UX backlog。

## 上游来源

- [REQ-20260523-012 控制台 Apple-level UI/UX 精修](../../docs/requirements/REQ-20260523-012-console-apple-level-uiux-polish.md)
- 用户截图反馈：账号分组详情页左侧导航与右侧内容区顶部对齐问题。

## 输入

- 用户提供的账号分组详情页截图。
- 现有 App Shell、PageSection 和高频页面样式实现。

## 输出

- App Shell 对齐与滚动条样式修复。
- 高频页面视觉抽查结果。
- 前端 typecheck、必要测试和浏览器视觉验证记录。
- 任务状态回写与后续建议。

## 影响范围

- `web/src/components/app/app-shell.tsx`
- `web/src/index.css`
- 可能触及：`web/src/components/app/page-section.tsx`
- 视觉抽查：账号分组详情、厂商目录、上游凭证、账号分组列表

## 依赖

- 本地前端 dev server 或可启动的 Vite 开发环境。
- 后端服务或现有浏览器会话用于真实页面渲染。

## 风险

- 侧栏品牌区高度绑定 header 高度后，极窄屏或多行 breadcrumb 可能改变抽屉节奏。
- 滚动条低干扰样式需要兼顾 Chrome/WebKit 与 Firefox。
- 如果后端服务不可用，浏览器只能验证 shell 和错误态，无法完整验证真实数据态。

## 验收标准

- 截图标注区域的突兀竖向滚动条消失或降到低干扰状态。
- 侧栏品牌区底线与右侧 header 底线在桌面宽度对齐。
- 桌面和移动宽度下，控制台 header、侧栏、主内容不重叠。
- `bun run typecheck` 通过。
- 若修改影响已有测试页面，运行对应 Vitest 测试。

## 测试边界

- 自动验证：`cd web; bun run typecheck`
- 定向测试：涉及账号分组或厂商目录页面时运行对应测试文件。
- 手工/浏览器验证：账号分组详情页桌面宽度、移动宽度；厂商目录、上游凭证、账号分组列表抽查。

## 关联文档

- [REQ-20260523-012 控制台 Apple-level UI/UX 精修](../../docs/requirements/REQ-20260523-012-console-apple-level-uiux-polish.md)

## 关联任务

- [TASK-20260523-013 厂商目录 UI 收敛为 Vendor -> Endpoint -> Group -> Credential](TASK-20260523-013-provider-catalog-vendor-endpoint-group-credential-ui.md)
- [TASK-20260521-012 控制台健康评分、Codex 模型、TLS 画像与调试台体验修正](../done/TASK-20260521-012-console-health-codex-model-tls-workbench-ux.md)

## 当前进度

- 2026-05-23：已确认首要问题位于 App Shell 侧栏滚动面和 header 高度节奏。
- 2026-05-23：已完成第一轮实现。
  - `web/src/components/app/app-shell.tsx`：右侧 header 实测高度同步到 `--app-shell-header-height`，侧栏品牌区使用同一高度变量，桌面实测 `headerBrandBottomDelta = 0`。
  - `web/src/index.css`：新增 `scrollbar-subtle`，让 App Shell 侧栏和主内容滚动条默认透明，hover/focus 时显示，避免截图中粗竖条常驻。
  - `web/src/components/app/page-section.tsx`：降低通用页面容器边框和阴影噪声。
  - `web/src/features/accounts/account-group-detail-page.tsx`、`web/src/features/accounts/account-groups-page.tsx`、`web/src/features/credentials/credentials-page.tsx`、`web/src/features/provider-sites/provider-sites-page.tsx`：高频表格容器降为轻边框、轻背景，并补横向滚动保护。
- 2026-05-23：已完成第二轮通用组件降噪。
  - `web/src/components/app/info-grid.tsx`：指标格从重圆角/重边框改为轻边框、轻背景、内侧高光。
  - `web/src/components/app/empty-state.tsx`：空状态容器和图标容器降低圆角与边框强度。
  - `web/src/components/app/code-panel.tsx`、`web/src/components/app/data-table-shell.tsx`、`web/src/components/app/panel-skeleton.tsx`：统一到 `PageSection` 的轻量卡片语言。
  - `web/src/components/app/table-pagination.tsx`：分页条背景和边框降噪。
  - `web/src/features/credentials/credentials-page.tsx`：账号分组链接支持长文本换行，避免表格控件裁切。
- 2026-05-23：已完成第三轮高频原生确认弹窗收敛。
  - 新增 `web/src/components/app/confirm-dialog.tsx`，提供统一确认弹窗。
  - `web/src/features/credentials/credentials-page.tsx`：删除上游凭证从 `window.confirm` 改为统一确认弹窗。
  - `web/src/features/accounts/account-group-detail-page.tsx`：删除账号分组从 `window.confirm` 改为统一确认弹窗，并更新对应测试。
  - `web/src/features/provider-sites/provider-sites-page.tsx`：删除 API 入口从 `window.confirm` 改为统一确认弹窗。
- 2026-05-23：已完成第四轮全局确认交互收口。
  - 新增 `web/src/components/app/confirm-provider.tsx`，通过 `ConfirmProvider` / `useConfirm` 提供 Promise 化确认服务，并接入 `AppProviders`。
  - 迁移密钥、模型、网络代理、TLS 指纹、集成通道/订阅/回调/Runbook、维护窗口、用户域、治理编排和告警运营页面的删除确认。
  - 将第三轮先落地的账号分组详情、上游凭证、厂商目录删除确认也改为全局确认服务，避免页面各自维护确认弹窗状态。
  - 业务源码中 `window.confirm` 已清零；对应 backlog 任务 `TASK-20260522-004` 已同步归档。
- 2026-05-23：开始第五轮弹窗与表单表面精修。
  - 范围：全局 `DialogContent` 基座、确认弹窗、账号分组详情/厂商目录/集成 Runbook 等高频管理面中残留的重圆角与重边框表面。
  - 非目标：不调整弹窗字段流程、不改变删除/保存/导入业务逻辑、不在本轮重排所有 Portal/Public 页面。
  - 验收：弹窗基座更轻、更系统化；高频表单提示面从 `rounded-2xl` / `border-border/60` 收敛到较轻的 `rounded-xl` / `border-border/45-55`；现有定向测试与 typecheck 继续通过。
- 2026-05-23：已完成第五轮弹窗与表单表面精修。
  - `web/src/components/ui/dialog.tsx`：全局 Dialog 从重圆角/重边框/重阴影收敛为轻边框、轻阴影、半透明 popover 背景与 backdrop blur。
  - `web/src/components/app/confirm-dialog.tsx`：确认弹窗警示图标容器降低红色背景和边框强度。
  - `web/src/features/accounts/account-groups-page.tsx`、`web/src/features/accounts/account-group-detail-page.tsx`、`web/src/features/provider-sites/provider-sites-page.tsx`、`web/src/features/integrations/runbooks-page.tsx`：清理本轮范围内残留 `rounded-2xl`、重边框和重背景表面。
- 2026-05-23：开始第六轮集成扩展表面收敛。
  - 范围：`web/src/features/integrations` 下通知通道、订阅、Webhook、外发投递、外部应用、扩展运行态页面的卡片/表格/筛选器表面。
  - 非目标：不调整集成扩展 API、事件投递逻辑、创建流程字段和后端结构。
  - 验收：集成扩展页面不再混用明显重圆角与重边框容器；列表表格使用轻量 `scrollbar-subtle` 表格容器；卡片列表降低阴影和边界噪声；已有集成页面测试继续通过。
- 2026-05-23：已完成第六轮集成扩展表面收敛。
  - `web/src/features/integrations/channels-page.tsx`、`subscriptions-page.tsx`、`webhooks-page.tsx`：列表卡片统一降为轻边框、轻背景和 1px 低阴影。
  - `web/src/features/integrations/external-apps-page.tsx`：外部应用表格改为轻量横向滚动容器，避免宽表格撑破页面。
  - `web/src/features/integrations/deliveries-page.tsx`：投递排障说明、筛选器和投递卡片降低边框/背景噪声，保留清晰的操作路径。
  - `web/src/features/integrations/extension-runtime-page.tsx`：iframe 容器降噪；不可运行状态恢复页面内可见阻断原因，同时保留 toast 反馈。
  - `web/src/components/app/app-shell.tsx`：为侧栏品牌区增加稳定 `data-testid`，便于后续对齐回归验证。
- 2026-05-23：开始第七轮观测记录表格密度与长文本可读性精修。
  - 范围：`web/src/features/request-logs/request-logs-page.tsx` 的筛选器、四张观测表、详情 JSON 面板和请求 ID/模型/客户端/会话等长文本单元格。
  - 非目标：不调整观测 API 查询参数、tab 数据源、详情弹窗打开逻辑和后端观测模型。
  - 验收：`request-logs` 页面不再使用明显重圆角/重边框表格壳；宽表格使用轻量 `scrollbar-subtle` 容器；关键长文本从硬截断改为可换行/可复制的可读展示；现有 request logs 测试和 typecheck 继续通过。
- 2026-05-23：已完成第七轮观测记录表格密度与长文本可读性精修。
  - `web/src/features/request-logs/request-logs-page.tsx`：筛选器从重卡片表面降为轻边框、轻背景；原生 select 高度与输入框节奏统一。
  - 四张观测表统一使用轻量 `scrollbar-subtle` 横向滚动壳，表头/行分割线从 `border-border/60` 收敛为 `border-border/35-55`。
  - 请求 ID、模型、客户端、会话、提供方、外部缓存引用等排障字段从强制 `truncate` 改为可换行展示，减少排障时必须打开详情的摩擦。
  - 详情 JSON 面板降为轻边框、轻背景和轻滚动条，保持内容可复制与可扫描。
- 2026-05-23：开始第八轮上游缓存观测表格与详情面板精修。
  - 范围：`web/src/features/upstream-cache/upstream-cache-page.tsx` 的筛选器、上游缓存引用表、缓存命中表、详情 JSON 面板和缓存引用/请求 ID/模型组等长文本单元格。
  - 非目标：不调整缓存观测 API 查询参数、摘要指标计算、tab 数据源、详情弹窗打开逻辑和缓存生命周期语义。
  - 验收：`upstream-cache` 页面与 `request-logs` 采用一致的轻量表格语言；不再出现明显重圆角/重边框表格壳；关键长文本可换行读取；现有 upstream-cache 测试和 typecheck 继续通过。
- 2026-05-23：已完成第八轮上游缓存观测表格与详情面板精修。
  - `web/src/features/upstream-cache/upstream-cache-page.tsx`：筛选器从重卡片表面降为轻边框、轻背景；原生 select 高度与输入框节奏统一。
  - 上游缓存引用表、缓存命中表统一使用轻量 `scrollbar-subtle` 横向滚动壳，表头/行分割线从 `border-border/60` 收敛为 `border-border/35-55`。
  - 缓存引用、请求 ID、提供方、模型组等排障字段从强制 `truncate` 改为可换行展示。
  - 详情 JSON 面板降为轻边框、轻背景和轻滚动条，与观测记录页保持一致。
- 2026-05-24：开始第九轮维护窗口页面表格与表单表面精修。
  - 范围：`web/src/features/operations/windows-page.tsx` 的维护窗口列表表格、创建/编辑表单提示区、提交摘要区和窗口名称/范围引用等长文本单元格。
  - 非目标：不调整维护窗口 API、保存/删除确认流程、时间计算、状态语义和后端模型。
  - 验收：维护窗口页采用轻量表格语言；不再出现明显 `rounded-2xl`、`border-border/60`、`bg-card/92` 残留；关键长文本可换行；现有 `windows-page.test.tsx` 和 typecheck 通过。
- 2026-05-24：已完成第九轮维护窗口组件代码级精修并停止继续扩展本任务。
  - `web/src/features/operations/windows-page.tsx`：维护窗口表格改为轻量 `scrollbar-subtle` 横向滚动壳，表头/行分割线降噪，窗口名称、范围和范围引用从强制 `truncate` 改为可换行展示。
  - 创建/编辑弹窗中的启用区和提交摘要区从 `rounded-2xl`、`border-border/60`、`bg-muted/20` 收敛为轻量 `rounded-xl`、`border-border/45`、`bg-muted/12`。
  - 收尾时确认 `/console/operations/*` 当前已在应用路由中退役并重定向到 `/console/ops`；因此维护窗口组件只做代码级和测试级验收，不再作为真实可访问页面浏览器验收对象。
  - 用户要求“这个任务做完收尾”，本任务不再继续扩大到 `ops/system-events`、`ops-alerts`、`governance` 等仍有残留的运维页面。

## 验证记录

- `cd web; bun run typecheck`：通过。
- `cd web; bun run test -- account-group-detail-page.test.tsx provider-sites-page.test.tsx credentials-page.test.tsx account-groups-page.test.tsx`：4 个测试文件、17 个测试通过。
- 第二轮后再次执行 `cd web; bun run typecheck`：通过。
- 第二轮后再次执行 `cd web; bun run test -- account-group-detail-page.test.tsx provider-sites-page.test.tsx credentials-page.test.tsx account-groups-page.test.tsx`：4 个测试文件、17 个测试通过。
- 第三轮后执行 `cd web; bun run typecheck`：通过。
- 第三轮后执行 `cd web; bun run test -- account-group-detail-page.test.tsx provider-sites-page.test.tsx credentials-page.test.tsx`：3 个测试文件、14 个测试通过。
- `rg -n "window\\.confirm" web/src/features/credentials/credentials-page.tsx web/src/features/accounts/account-group-detail-page.tsx web/src/features/provider-sites/provider-sites-page.tsx`：无匹配。
- 第四轮后执行 `cd web; bun run typecheck`：通过。
- 第四轮后执行 `cd web; bun run test -- keys-page.test.tsx models-page.test.tsx credentials-page.test.tsx account-group-detail-page.test.tsx provider-sites-page.test.tsx proxies-page.test.tsx tls-profiles-page.test.tsx channels-page.test.tsx runbooks-page.test.tsx subscriptions-page.test.tsx webhooks-page.test.tsx windows-page.test.tsx governance-page.test.tsx ops-alerts-page.test.tsx users-page.test.tsx plans-page.test.tsx`：17 个测试文件、39 个测试通过。
- `rg -n "window\\.confirm" web/src -g "*.tsx" -g "*.ts"`：无匹配。
- 第五轮后执行 `cd web; bun run typecheck`：通过。
- 第五轮后执行 `cd web; bun run test -- account-group-detail-page.test.tsx account-groups-page.test.tsx provider-sites-page.test.tsx runbooks-page.test.tsx`：4 个测试文件、14 个测试通过。
- `rg -n "rounded-2xl|border-border/60 bg-muted/20|border-border/60 bg-muted/10|overflow-hidden rounded-2xl" web/src/features/accounts/account-groups-page.tsx web/src/features/accounts/account-group-detail-page.tsx web/src/features/provider-sites/provider-sites-page.tsx web/src/features/integrations/runbooks-page.tsx web/src/components/ui/dialog.tsx web/src/components/app/confirm-dialog.tsx`：无匹配。
- 第六轮后执行 `cd web; bun run typecheck`：通过。
- 第六轮后执行 `cd web; bun run test -- channels-page.test.tsx subscriptions-page.test.tsx webhooks-page.test.tsx runbooks-page.test.tsx deliveries-page.test.tsx extension-runtime-page.test.tsx`：7 个测试文件、9 个测试通过。
- `rg -n "rounded-2xl|rounded-3xl|shadow-md|shadow-lg|hover:shadow-md|bg-card/92|border-border/60|overflow-hidden rounded-2xl|overflow-hidden rounded-3xl" web/src/features/integrations -g "*.tsx"`：无匹配。
- 第七轮后执行 `cd web; bun run typecheck`：通过。
- 第七轮后执行 `cd web; bun run test -- request-logs-page.test.tsx`：1 个测试文件、2 个测试通过。
- `rg -n "rounded-2xl|rounded-3xl|border-border/60|bg-card/92|shadow-md|shadow-lg|truncate" web/src/features/request-logs/request-logs-page.tsx`：无匹配。
- 第八轮后执行 `cd web; bun run typecheck`：通过。
- 第八轮后执行 `cd web; bun run test -- upstream-cache-page.test.tsx`：1 个测试文件、1 个测试通过。
- `rg -n "rounded-2xl|rounded-3xl|border-border/60|bg-card/92|shadow-md|shadow-lg|truncate" web/src/features/upstream-cache/upstream-cache-page.tsx`：无匹配。
- 第九轮后执行 `cd web; bun run typecheck`：通过。
- 第九轮后执行 `cd web; bun run test -- windows-page.test.tsx`：1 个测试文件、1 个测试通过。
- `rg -n "rounded-2xl|rounded-3xl|border-border/60|bg-card/92|shadow-md|shadow-lg|truncate" web/src/features/operations/windows-page.tsx`：无匹配。
- 路由复核：`/console/operations/*` 当前由 `web/src/app/router.tsx` 重定向到 `/console/ops`，`operations-router.test.tsx` 也覆盖了 retired operations route 的重定向语义；因此未对 `windows-page` 做真实浏览器页面验收。
- 浏览器桌面抽查：
  - 账号分组详情：无 alert，`headerBrandBottomDelta = 0`，无页面横向溢出。
  - 厂商目录：无 alert，`headerBrandBottomDelta = 0`，无页面横向溢出。
  - 上游凭证：无 alert，`headerBrandBottomDelta = 0`，无页面横向溢出；长账号分组名按钮已改为可换行，避免强制单行截断。
  - 账号分组列表：无 alert，`headerBrandBottomDelta = 0`，无页面横向溢出。
- 第二轮浏览器桌面复验：
  - 账号分组详情、厂商目录、上游凭证、账号分组列表均无 alert、无页面横向溢出、无主内容横向溢出、无可检测控件裁切。
  - 四个页面 `headerBrandBottomDelta = 0`。
  - 四个页面均未检测到 card-in-card 嵌套。
- 第三轮浏览器复验：
  - 上游凭证详情中点击删除后出现统一 `Dialog`，文案为“删除上游凭证”，不是原生 confirm。
  - 上游凭证页面无 alert、无页面横向溢出，`headerBrandBottomDelta = 0`。
- 第四轮浏览器复验：
  - TLS 指纹页存在 3 个可用删除入口；点击后出现统一 `Dialog`，文案为“删除 TLS 指纹画像确认删除 Claude Code”，不是原生 confirm，并通过取消关闭未执行删除。
  - TLS 指纹页无 alert、无页面横向溢出，`headerBrandBottomDelta = 0.12`，处于亚像素级对齐误差。
  - 账号分组详情页删除按钮在当前数据下为 disabled，未用于真实点击复验；页面仍无横向溢出，`headerBrandBottomDelta = 0.12`。
- 第五轮浏览器复验：
  - TLS 指纹页点击删除后仍打开统一确认 Dialog，Dialog 尺寸约 448x214，未溢出 1280x720 视口。
  - Dialog 已使用轻阴影 `0 18px 60px rgba(15,23,42,0.18)` 和轻边框；取消按钮可正常关闭。
  - TLS 指纹页无 alert、无页面横向溢出，`headerBrandBottomDelta = 0.12`。
- 第六轮浏览器复验：
  - 投递记录页 `/console/integrations/deliveries`：页面标题为 `x-ai-gateway 控制台`，DOM snapshot 包含“投递记录”“最近投递”“发送测试投递”等有效内容。
  - 1280x720 视口无 console warning/error、无 alert、无框架错误覆盖。
  - 页面与主内容均无横向溢出，`horizontalOverflow = 0`、`mainHorizontalOverflow = 0`，可检测控件裁切数量为 0。
  - 侧栏品牌区和右侧 header 底线稳定复测 `headerBrandBottomDelta = 0.12`，属于亚像素级误差。
  - 当前数据态为空投递记录，空态、筛选器和发送测试投递入口首屏可见。
- 第七轮浏览器复验：
  - 观测记录页 `/console/request-logs`：页面标题为 `x-ai-gateway 控制台`，DOM snapshot 包含“请求日志与缓存观测”“观测数据”“请求日志”等有效内容。
  - 1280x720 视口无 console warning/error、无 alert、无框架错误覆盖。
  - 页面与主内容均无横向溢出，`horizontalOverflow = 0`、`mainHorizontalOverflow = 0`，可检测控件裁切数量为 0。
  - 侧栏品牌区和右侧 header 底线复测 `headerBrandBottomDelta = 0.12`。
  - 当前后端数据态为空请求日志；筛选器、tab、汇总 badge 和空态首屏可见，数据行可读性由 `request-logs-page.test.tsx` 的请求日志与选路决策行渲染覆盖。
- 第八轮浏览器复验：
  - 上游缓存页 `/console/upstream-cache`：页面标题为 `x-ai-gateway 控制台`，DOM snapshot 包含“缓存记录”“引用与命中记录”“上游缓存引用”等有效内容。
  - 1280x720 视口无 console warning/error、无 alert、无框架错误覆盖。
  - 页面与主内容均无横向溢出，`horizontalOverflow = 0`、`mainHorizontalOverflow = 0`，可检测控件裁切数量为 0。
  - 侧栏品牌区和右侧 header 底线复测 `headerBrandBottomDelta = 0.12`。
  - 当前后端数据态为空上游缓存记录；筛选器、摘要指标、tab 和空态首屏可见，缓存引用详情结构化展示由 `upstream-cache-page.test.tsx` 覆盖。
- 浏览器截图命令 `Page.captureScreenshot` 在后续保存截图时多次超时；已用 DOM 度量和实时页面预览完成本轮验收，截图保存能力需后续单独排查或换工具补证据。

## 遗留问题

- 移动断点 viewport override 未实际生效，浏览器仍回报 1280x720；本任务不把移动视觉验收标为已完成。
- 上游凭证页凭证名称仍使用固定列截断和 title 展示；本任务浏览器未再检测到控件裁切，若要进一步接近 Apple 级可读性，建议后续拆成“表格密度与长文本可读性”专项。
- `ops/system-events`、`ops-alerts`、`governance` 等现役运维页面仍有 `rounded-2xl`、`border-border/60`、`bg-card/92` 或固定表格残留；根据用户收尾要求，本任务不再继续扩大范围。
- `/console/operations/*` 已退役，维护窗口等 `features/operations` 旧页面如需继续投入，应先决策是否恢复现役入口；否则只作为历史组件维护。
- 部分弹窗表单仍有说明文案偏重问题，本任务未大规模重排，避免影响创建/编辑流程。

## 收尾结论

- 本任务已完成用户截图指向的 App Shell 对齐问题、高频页面表面降噪、统一确认弹窗、观测/缓存表格可读性和维护窗口旧组件代码级收敛。
- 自动验证通过多轮 `bun run typecheck` 与受影响测试；浏览器验证覆盖账号分组详情、厂商目录、上游凭证、账号分组列表、投递记录、请求日志和上游缓存等真实路由。
- 剩余 UI/UX 精修不再挂在本任务下继续发散，后续应按“现役运维页面表格与卡片降噪”“移动端真实 viewport 复验”“凭证列表长文本可读性”拆分新任务。
