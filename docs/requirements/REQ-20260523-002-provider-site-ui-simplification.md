# REQ-20260523-002 厂商管理界面与编辑界面收敛

## 背景

用户反馈厂商管理界面整体太混乱，编辑厂商的界面也过于复杂。当前列表页同时堆叠厂商聚合、API 入口、站点类型、协议入口、预设导入、模型和凭证信息；详情页又把协议入口、兼容画像、模型能力、surface 和 feature 解析全部纵向展开，编辑弹窗直接暴露底层字段，导致主路径和高级维护路径混杂。

进一步澄清：厂商聚合和预设导入本质上是同一套厂商目录的不同状态，却被展示成两个表格；厂商聚合里的“编辑”和预设导入里的“查看入口”又进入不同交互路径，造成同一厂商有两套管理体验。

## 目标

- 厂商管理列表页优先服务单一厂商目录：同一厂商只在一个列表里出现，按“已导入/可导入/手工入口”等状态呈现。
- 预设导入不再作为独立大表存在，而是成为统一厂商目录中的状态和操作。
- 同一厂商不再提供两套互相割裂的操作路径；已导入厂商统一进入详情页管理，未导入厂商统一执行导入。
- 编辑/新增 API 入口弹窗按“基本信息”“连接方式”“高级 JSON”分组，弱化低频字段。
- API 入口详情页按“概览”“协议入口”“模型能力”“高级诊断”分层，默认不把 surface/feature/JSON 全量摊开。
- 协议入口编辑弹窗同样区分基本配置、运行时策略和高级 JSON，减少一次性字段压力。

## 范围

- `web/src/features/provider-sites/provider-sites-page.tsx`
- `web/src/features/provider-sites/provider-site-detail-page.tsx`
- `web/src/features/provider-sites/provider-sites-page.test.tsx`
- `web/src/features/provider-sites/provider-site-detail-page.test.tsx`
- 必要时更新本地文档、任务索引和已有前端类型。

## 非目标

- 不修改后端数据结构、导入逻辑、模型发现或凭证绑定逻辑。
- 不移除厂商多协议能力。
- 不重做整套控制台信息架构。
- 不引入新的 UI 组件库。

## 风险

- 信息隐藏过多会影响高级维护，所以需要保留高级区入口。
- 表格过度压缩可能影响可扫描性，需要控制列数并保留关键状态。
- 详情页如果只做视觉重排而不改交互层级，混乱感仍会保留。

## 验收标准

- 列表页不再同时展开“已导入入口”和“预设导入”两个大表，统一为单一厂商目录。
- 已导入厂商和预设厂商的操作路径一致：已导入进入详情管理，未导入执行导入。
- 已导入入口表格列数收敛，关键字段集中在 4 到 5 个运营列内。
- 新增/编辑 API 入口弹窗字段分组清晰，高级 JSON 不占据主路径。
- 详情页默认聚焦概览、协议入口和模型能力，surface/feature 进入高级诊断模式。
- 协议入口编辑弹窗按主路径与高级字段分层。
- 前端定向测试、类型检查和浏览器抽查通过或记录限制。

## 实现结果

- 厂商管理列表页已合并为单一“厂商与 API 入口”目录，预设与已导入站点按同一行模型渲染。
- 已导入厂商行只保留“管理 / 刷新 / 删除”操作，管理入口统一进入详情页；未导入预设在同一目录内提供“导入”操作。
- 列表页新增自定义入口只用于没有预设的自定义兼容入口，不再承担已导入厂商编辑路径。
- 详情页按“概览 / 协议入口 / 模型能力 / 高级诊断”分层，默认只展示概览，不再把 surface/feature 诊断铺在主路径。
- 协议入口新增/编辑弹窗已拆为“基本信息 / 运行时策略 / 高级 JSON”，运行时策略和 conversation profile 不再挤在第一屏。
- 前端测试覆盖单一目录、管理入口、预设导入、新增自定义入口、详情页 Tab 和协议入口弹窗分步。

## 验证记录

- 通过：`bun run typecheck`
- 通过：`bun run test -- --run src/features/provider-sites/provider-sites-page.test.tsx src/features/provider-sites/provider-site-detail-page.test.tsx`
- 通过：`bunx eslint src/features/provider-sites/provider-sites-page.tsx src/features/provider-sites/provider-site-detail-page.tsx src/features/provider-sites/provider-sites-page.test.tsx src/features/provider-sites/provider-site-detail-page.test.tsx src/features/provider-sites/types.ts`
- 浏览器抽查：登录 `http://localhost:5173/console/provider-sites` 后，列表只保留“厂商与 API 入口”单一表格，不再出现独立“厂商聚合 / 预设导入”双表；已导入 Xiaomi MiMo 行显示两个协议入口，并通过“管理”进入详情页。
- 浏览器抽查：`/console/provider-sites/2` 详情页默认展示“概览”，切换“协议入口”后显示厂商协议入口表格，新增协议入口弹窗包含“基本信息 / 运行时策略 / 高级 JSON”三个分组。
- 浏览器限制：当前本地库所有 catalog preset 已导入，未导入行的“导入”按钮由前端定向测试覆盖。
- 浏览器控制台：本轮抽查无 warning/error。

## 当前状态

Done
