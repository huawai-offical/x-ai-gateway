# TASK-20260514-002 参考项目实现细节深度对比

## Task Spec

### 背景

用户要求再次深度对比参考项目，且强调必须对比具体实现细节，判断当前项目是否完全超越或者实现。

### 目标

- 扫描 `D:/WorkSpace/Project/ai/参考` 下五个参考项目的关键实现。
- 扫描当前 `x-ai-gateway` 对应模块实现。
- 输出结构化对比报告。
- 根据报告判断是否需要新增 backlog 任务。

### 非目标

- 不直接重构当前项目。
- 不改动参考项目。
- 不做线上资料补充。

### 上游来源

- 用户请求：`再次深度对比下参考项目的功能实现细节，要对比具体的实现细节，我们是否完全超越或者实现`
- 需求文档：[REQ-20260514-002 参考项目实现细节深度对比](../../docs/requirements/REQ-20260514-002-reference-implementation-detail-comparison.md)

### 输入

- 当前项目源码与文档。
- 本地参考项目目录：
  - `D:/WorkSpace/Project/ai/参考/new-api-main`
  - `D:/WorkSpace/Project/ai/参考/sub2api-main`
  - `D:/WorkSpace/Project/ai/参考/cc-switch-main`
  - `D:/WorkSpace/Project/ai/参考/cli_proxy-master`
  - `D:/WorkSpace/Project/ai/参考/cockpit-tools-main`

### 输出

- `docs/reports/REP-20260514-reference-implementation-detail-comparison.md`
- 更新 `docs/index.md`
- 更新 `tasks/index.md`
- 如存在新增缺口，创建 `tasks/backlog/` 任务；如不存在，写明“不新增 backlog”的证据。

### 影响范围

- 文档与任务体系。
- 不修改生产代码。

### 依赖

- 本地参考项目可读。
- 当前仓库文档和源码可读。

### 风险

- 参考项目可能不是最新上游状态，本轮只以本地目录为准。
- 某些参考项目的功能更偏桌面工具或单点代理，不一定适合服务端主线，需要区分“不适合纳入”与“缺失”。

### 验收标准

- 报告覆盖五个参考项目。
- 报告覆盖当前项目对应模块。
- 每个关键维度有明确结论：超越、等价、部分实现、未实现、不适合纳入。
- 若发现可执行缺口，已拆成 backlog；若未发现，明确说明。

### 测试边界

- 本任务是代码态审计与文档输出，不运行功能测试。
- 使用文件扫描、源码定位、已有任务和报告索引作为证据。

### 当前状态

- 状态：Done
- 创建日期：2026-05-14
- 完成日期：2026-05-14

## 执行记录

- 2026-05-14：创建任务，开始扫描参考项目与当前项目实现。
- 2026-05-14：完成五个参考项目实现细节复核，生成 [REP-20260514](../../docs/reports/REP-20260514-reference-implementation-detail-comparison.md)。
- 2026-05-14：新增 3 个后续 backlog：
  - [TASK-20260514-003 Provider 长尾 Preset、Web/Search 与 Native Adapter 追平](../backlog/TASK-20260514-003-provider-long-tail-web-search-native-adapter.md)
  - [TASK-20260514-004 专有 Media Task Adapter 与真实产物 Smoke](../backlog/TASK-20260514-004-provider-specific-media-task-adapters-smoke.md)
  - [TASK-20260514-005 官方价格源版本化同步与人工批准快照](../backlog/TASK-20260514-005-provider-pricing-versioned-sync.md)

## 实现结果

- 已逐项对比 `new-api-main`、`sub2api-main`、`cc-switch-main`、`cli_proxy-master`、`cockpit-tools-main` 的关键源码与 README 能力。
- 已对照当前项目的 Provider Catalog、Canonical Translation、Codex 官方账号、账号池、批量恢复、Gateway Routing、Distributed Key Governance、Public/Portal/Console、Client Instance、Deep Link 和 ADR 边界。
- 结论为：服务端主线多数维度已超越或等价实现，但不能宣称全维度完全超越；剩余可执行差距集中在长尾 provider/native adapter、专有 media task adapter、价格源版本化同步。

## 验证记录

- 使用本地文件扫描和源码定位完成审计。
- 本轮只改文档与任务体系，未修改生产代码，未运行功能测试。

## 遗留问题

- 3 个新 backlog 需后续按优先级推进。
- Desktop Companion、本地 proxy、本机 profile/session/MCP/Skills 自动管理不纳入服务端主线，继续遵循 ADR-0008 与 ADR-0009。
