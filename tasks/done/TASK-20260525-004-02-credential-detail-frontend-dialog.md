# TASK-20260525-004-02 凭证详情编辑合并弹窗前端

## 类型

子任务 / task spec

## 背景

用户不希望新增独立详情页面，而是希望点击已录入凭证后在当前页面弹出详情/编辑合并界面，并可直接做联通性测试。

## 目标

- 上游凭证列表支持点击凭证打开详情/编辑合并弹窗。
- 弹窗展示基础信息、绑定信息、健康/探测状态和用量摘要。
- 弹窗内保留编辑保存能力。
- 增加“联通性测试”按钮与结果反馈。

## 非目标

- 不新增路由。
- 不调整全局导航。
- 不展示密钥明文。

## 上游来源

- `docs/requirements/REQ-20260525-004-credential-detail-probe.md`
- `tasks/in-progress/TASK-20260525-004-credential-detail-probe-parent.md`

## 输入

- `web/src/features/credentials/credentials-page.tsx`
- `web/src/features/credentials/credentials-page.test.tsx`
- 凭证 API 类型定义。

## 输出

- 详情/编辑合并弹窗。
- 联通性测试按钮与状态展示。
- 前端定向测试。

## 影响范围

- Admin 控制台上游凭证页面。

## 依赖

- 后端凭证详情响应与联通性测试 API。
- 现有 `apiRequest` 和 toast/错误展示模式。

## 风险

- 页面可能已有复杂新增弹窗，需避免卡片嵌套和布局过载。
- 点击行与行内操作按钮需要避免事件冲突。

## 验收标准

- 点击凭证名称或行可打开弹窗。
- 弹窗可保存编辑。
- 点击联通性测试后展示最新探测状态。
- 前端测试与 typecheck 通过。

## 测试边界

- React Testing Library 定向测试。
- Browser 渲染检查在本地服务可用时执行。

## 当前状态

Done

## 实现结果

- API Key 类型凭证名称、账号分组入口和查看按钮均打开详情/编辑合并弹窗。
- 弹窗展示基础信息、绑定信息、用量摘要、最近联通性探测、错误摘要和可编辑表单。
- 弹窗新增“联通性测试”按钮，调用保存凭证探测 API 并刷新凭证与 inventory 查询。
- 前端类型补齐 connectivity 字段，并兼容旧响应为空的情况。

## 验证结果

- `cd web; bun run typecheck`：通过。
- `cd web; bun run test -- credentials-page.test.tsx`：通过，覆盖打开合并弹窗、展示用量/探测结果、触发保存凭证联通性测试。
- Browser 打开 `http://127.0.0.1:5173/console/credentials` 后被登录守卫拦截到登录页；未自动登录。

## 遗留问题

- AUTH_JSON_ACCOUNT 行仍使用只读详情弹窗；本轮仅将 API Key 凭证落地为“详情即编辑”。
