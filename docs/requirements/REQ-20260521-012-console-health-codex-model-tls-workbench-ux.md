# REQ-20260521-012 控制台健康评分、Codex 模型、TLS 画像与调试台体验修正

状态：Done  
日期：2026-05-21  
上游来源：用户指令“健康评分做成表格，并且不要只显示 key 类凭证；Codex 类型账号需要模型刷新入口；账号分组与治理表格向已录入凭证表格看齐；TLS 指纹创建默认常用画像并改为键值对 header 编辑；白盒调试工作台 tab 布局不要贴太紧”

## 背景

上一轮已经把上游凭证列表从静态 key 扩展为统一 inventory，并把凭证列表操作收敛到详情弹窗。但用户继续发现几处主路径体验不一致：

1. 健康评分仍偏向 key 类凭证，不符合 Codex `auth.json`、OAuth 类账号同属上游凭证的产品口径。
2. Codex 类型账号没有明显的模型刷新入口，容易误以为 Codex 账号无法同步支持模型。
3. 账号分组与治理策略表格仍存在操作按钮挤压或拉高表格行的问题，尚未对齐“已录入凭证”的扁平表格模式。
4. TLS 指纹创建缺少常用默认画像，header 仍让用户手写 JSON，容易写错。
5. 白盒调试工作台多个 tab 间距过紧，界面观感和点击体验不足。

## 目标

- 健康评分改为表格展示，并覆盖静态凭证、Codex/OAuth 账号等统一凭证来源。
- Codex 类型账号展示模型刷新按钮或等价刷新入口，并明确落到账号支持模型。
- 账号分组与治理表格收敛主行操作，避免按钮竖排、行高异常和大块空白。
- TLS 指纹提供常用默认画像，包括 Codex CLI、Claude Code、Web Browser 等常见 header 画像。
- TLS 指纹创建/编辑中的 header 改为键值对增删行，由前端提交前自动转换为 JSON。
- 优化白盒调试工作台 tab 布局间距，让 tab 与内容之间有稳定留白。

## 范围

- `web/src/features/credentials/`
- `web/src/features/accounts/`
- `web/src/features/governance/`
- `web/src/features/network/`
- `web/src/features/workbench/`
- 账号模型刷新所需 Admin API、service、测试
- TLS 指纹默认数据初始化、请求/响应 DTO、前端表单与测试

## 非目标

- 不恢复已下线的官方账号运行态独立页面。
- 不删除 `vector/files/file_search` 主线支撑 API。
- 不把真实密钥、真实 `auth.json`、真实 AT/RT 写入仓库。
- 不一次性改造全站所有表格；本轮优先处理用户指出的账号分组与治理策略表格。
- 不承诺 Codex 存在独立于 OpenAI-compatible 的专属公开 `/models` API；若后端无真实远程模型接口，本轮提供系统模型目录刷新入口。

## 风险

- 统一健康评分需要跨静态凭证与账号实体，字段含义不完全一致，需要避免误导用户。
- Codex 模型刷新如果走系统目录而不是远端 API，需要在实现中保持行为可追踪。
- 表格操作收敛可能影响现有测试中按按钮文本查找的断言，需要同步更新。
- TLS header 键值对需要正确处理空行、重复 key、非法 JSON 回显和编辑回填。
- 默认 TLS 画像初始化需要幂等，不能覆盖用户已经修改的同 code 画像。

## 验收标准

1. 健康评分以表格展示，且至少覆盖静态 key 凭证与 Codex/OAuth 账号投影。
2. Codex 类型账号详情或列表可触发模型刷新，并将结果更新到账号支持模型字段。
3. 账号分组表格主行不因操作按钮拉宽或拉高，复杂操作进入详情或弹窗。
4. 治理策略相关表格主行操作保持扁平，不出现竖排按钮导致的异常行高。
5. TLS 指纹默认包含 Codex CLI、Claude Code、Web Browser 等常用画像。
6. TLS 指纹 header 创建/编辑为键值对增删行，不要求用户手写 JSON。
7. 白盒调试工作台 tab 与内容区有稳定间距，不再贴得过紧。

## 测试边界

- 前端：账号分组、上游凭证、治理策略、TLS 指纹、白盒工作台相关 vitest。
- 前端：`npm run typecheck`。
- 后端：账号模型刷新、TLS 默认画像初始化相关定向测试。
- 浏览器：检查 `/console/credentials`、`/console/account-groups`、治理策略页、TLS 指纹页、白盒调试工作台。

## 实现结果

- 健康评分改为统一表格展示，新增 `sourceType/sourceId/accountId` 等字段，后端同时返回静态凭证与 `auth.json`/OAuth 账号类凭证评分。
- Codex 类型账号新增 `/admin/accounts/{id}/refresh-models`，账号详情弹窗增加“刷新模型”入口；当前实现基于系统模型目录和 Codex 官方账号默认模型写回账号支持模型，不依赖未确认的 Codex 专属远端 `/models` 接口。
- 账号分组主表只保留“查看”，编辑、启停、删除进入详情弹窗；治理策略操作收敛为紧凑图标按钮，避免按钮竖排撑高表格行。
- TLS 指纹初始化新增 Codex CLI、Claude Code、Web Browser Chrome 默认画像，并保持按 `profileCode` 幂等创建。
- TLS 指纹表单新增常用画像选择与 Header 键值对编辑，提交前由前端自动转换为 `settingsJson.headers`。
- 白盒调试工作台增加 tab、阶段面板和表单间距，降低贴边和拥挤感。

## 验证结果

- 后端定向测试通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.AccountAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.GovernanceAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.NetworkGovernanceServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.DefaultResourceBootstrapServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.GovernanceAdminControllerTests"`。
- 前端定向测试通过：`npm test -- --run src/features/ops/ops-alerts-page.test.tsx src/features/ops/governance-page.test.tsx src/features/accounts/account-groups-page.test.tsx src/features/accounts/account-group-detail-page.test.tsx src/features/network/tls-profiles-page.test.tsx src/features/workbench/workbench-page.test.tsx src/features/credentials/credentials-page.test.tsx`，共 7 个测试文件、28 个用例。
- 前端类型检查通过：`npm run typecheck`。
- 浏览器轻量检查：本地 Vite 前端可打开，因本机未检测到 8080 后端会话服务，控制台路由按预期跳转到登录页；未执行真实登录后的页面截图验收。

## 关联任务

- [TASK-20260521-012](../../tasks/done/TASK-20260521-012-console-health-codex-model-tls-workbench-ux.md)
- [TASK-20260521-012-01](../../tasks/done/TASK-20260521-012-01-health-score-unified-table.md)
- [TASK-20260521-012-02](../../tasks/done/TASK-20260521-012-02-codex-model-refresh-entry.md)
- [TASK-20260521-012-03](../../tasks/done/TASK-20260521-012-03-account-groups-governance-table-detail-actions.md)
- [TASK-20260521-012-04](../../tasks/done/TASK-20260521-012-04-tls-default-profiles-key-value-editor.md)
- [TASK-20260521-012-05](../../tasks/done/TASK-20260521-012-05-workbench-tabs-spacing.md)
