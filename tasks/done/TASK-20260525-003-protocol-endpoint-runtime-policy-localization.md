# TASK-20260525-003 协议入口运行时策略页签汉化

## 类型

子任务 / task spec

## 背景

用户反馈“编辑协议入口的 2. 运行时策略，没有汉化”。当前 Admin 厂商站点详情页的协议入口新增/编辑弹窗中，“2. 运行时策略”页签仍存在多处英文可见标签，和控制台中文化目标不一致。

## 目标

- 将协议入口新增/编辑弹窗“2. 运行时策略”页签内的可见字段标签统一汉化。
- 保留枚举值、协议值和技术配置值的原始英文，不改变后端 API payload。
- 补充前端测试断言，防止这些英文标签再次回退到界面中。

## 非目标

- 不调整协议入口 API schema。
- 不改变 Provider Type、Site Kind 等枚举值。
- 不重构协议入口编辑弹窗布局。
- 不扩展其他页面的大范围汉化扫描。

## 上游来源

- 用户反馈：编辑协议入口的“2. 运行时策略”没有汉化。
- 关联本地任务：`tasks/in-progress/TASK-20260521-007-01-console-admin-ui-chinese-localization.md`

## 输入

- `web/src/features/provider-sites/provider-site-detail-page.tsx`
- `web/src/features/provider-sites/provider-site-detail-page.test.tsx`

## 输出

- 运行时策略页签的可见标签改为中文。
- 对应页面测试更新为使用中文 label，并断言旧英文 label 不再出现。

## 影响范围

- Admin 控制台厂商站点详情页。
- 协议入口新增/编辑弹窗的运行时策略页签。

## 依赖

- 前端现有 React Testing Library 测试能力。
- 不依赖后端服务或数据库数据。

## 风险

- `getByLabelText` 测试若仍使用旧英文 label，会导致测试失败，需要同步更新。
- 枚举值不应被翻译，否则可能破坏保存 payload。

## 验收标准

- “2. 运行时策略”页签不再显示旧英文标签。
- 新增/编辑协议入口仍可保存原始枚举和配置值。
- `provider-site-detail-page` 定向测试通过。
- 前端 typecheck 通过。

## 测试边界

- 自动化测试：`bun run test -- src/features/provider-sites/provider-site-detail-page.test.tsx`
- 类型检查：`bun run typecheck`
- 不进行后端集成测试，因为本任务不改变后端行为。

## 关联文档

- `docs/requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md`

## 关联任务

- `tasks/in-progress/TASK-20260521-007-01-console-admin-ui-chinese-localization.md`

## 实现结果

- 已将协议入口新增/编辑弹窗“2. 运行时策略”页签内的可见字段标签汉化：
  - `Provider Type` -> `厂商类型`
  - `Site Kind` -> `站点类型`
  - `Auth Strategy` -> `鉴权策略`
  - `Path Strategy` -> `路径策略`
  - `Model Addressing` -> `模型寻址策略`
  - `Error Schema` -> `错误结构策略`
  - `Stream Transport` -> `流式传输方式`
- 保留下拉枚举值和提交 payload 中的英文技术值，不改变后端 API 契约。
- 已更新协议入口详情页测试，使用中文 label 操作表单，并断言旧英文 label 不再出现。

## 验证情况

- 通过：`cd web; bun run test -- src/features/provider-sites/provider-site-detail-page.test.tsx`
- 通过：`cd web; bun run typecheck`
- 未执行真实浏览器页面检查：当前本地 `5173` 与 `8080` 端口无监听服务，本任务改动已由定向组件测试覆盖弹窗交互和保存 payload。

## 遗留问题

- 无。

## 后续建议

- 若继续做全界面汉化，可对协议入口编辑弹窗之外的现役页面再跑一轮英文静态 label 扫描。

## 当前状态

Done
