# TASK-20260508-003 Codex 前后端联调 Smoke、后端测试与浏览器回归证据

状态：Done  
优先级：High  
排期：P0-03  
来源：[REQ-20260508-001 Codex 观测后端化、批量恢复与联调 Smoke 闭环](../../docs/requirements/REQ-20260508-001-codex-observability-batch-api-smoke-closure.md)

## 背景

用户明确说明测试时可以联调后端一起。前两项新增后端 API 后，需要补齐前后端联调证据，确认页面不是只在 mock 数据下可用。

## 目标

- 增加后端定向单测，覆盖新 API 的核心行为。
- 增加前端 Vitest，覆盖新 API 数据态、批量执行交互和错误/空态。
- 运行前端 typecheck/build。
- 尝试浏览器烟测当前 dev server 可达性，并记录是否受后端登录状态限制。

## 验收标准

- 后端新增测试通过。
- 前端新增/更新测试通过。
- `bun run typecheck` 与 `bun run build` 通过。
- 浏览器烟测有明确结果，若后端登录/session 不可用需记录限制。

## 实施记录

- 补齐后端定向测试，覆盖 Codex Projection API 聚合与批量恢复执行策略。
- 补齐前端 Vitest，覆盖请求观测页后端数据态、账号池批量预检/执行交互。
- 启动真实后端 `./gradlew.bat bootRun` 与前端 `bun run dev`，浏览器登录后验证新页面链路。
- 当前浏览器联调避免点击真实执行按钮，仅验证预检与 disabled 防误操作状态；真实执行由单元测试覆盖。

## 验证记录

- 后端定向测试：通过。
- 前端 Vitest：2 个文件 7 个测试通过。
- `bun run typecheck`：通过。
- `bun run build`：通过。
- 本轮改动文件定向 ESLint：通过。
- 项目级 `bun run lint`：失败于既有无关 lint 问题 `web/src/app/router.tsx`、`web/src/features/accounts/codex-onboarding-page.tsx`。
- 浏览器：登录页、`/console/request-logs`、`/console/account-pools/5` 均可访问；请求观测页无框架 overlay；账号池批量预检真实调用后端成功。截图采集受 in-app browser CDP 超时影响，已记录 DOM snapshot 与 console warning。
