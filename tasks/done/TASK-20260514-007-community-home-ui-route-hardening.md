# TASK-20260514-007 社区首页排版、对比度与链接硬化

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260514-006](TASK-20260514-006-community-home-api-docs-refresh.md)  
上游来源：[REQ-20260514-006](../../docs/requirements/REQ-20260514-006-community-home-api-docs-refresh.md)

## 目标

- 优化 `/portal` 社区首页首屏和主要卡片的对比度、排版、按钮可读性。
- 修复首页链接，避免点击后进入 404 或 fallback。
- 在桌面与移动视口验证页面不空白、无明显 overlay、无关键 console error。

## 输出

- Portal 首页组件样式和链接修复。
- 单测或路由测试更新。
- 浏览器截图与验证结果。

## 影响范围

- `web/src/features/portal/portal-home-page.tsx`
- `web/src/features/portal/portal-shell.tsx`
- `web/src/app/router.tsx`
- 相关测试

## 验收标准

- 首屏用户身份、状态、Codex 接入信息可读。
- 首页动作链接均落到已注册路由或安全外链。
- 移动端不会出现关键文字溢出或被浅色背景吞掉。

## 当前状态

Done。

## 完成结果

- 优化 `portal-shell`、Portal 首页、Portal 登录页与公开首页的背景、边框、文字颜色和卡片半径，移除会导致样式不可预测的无效透明色。
- 首页表格增加横向滚动和稳定最小宽度，移动端不再把关键字段挤压到不可读。
- 公开 footer 的 `OpenAPI` 链接改到 `/docs`；旧 `/public/docs/openapi.json` 前端路径渲染公开文档页，避免用户进入 404。
- `/docs` 后端不可用时显示“本地文档”状态，OpenAPI JSON 未可用时禁用按钮，不再把 `fallback` 文案暴露给用户。

## 验证记录

- `bun run test -- portal-home-page.test.tsx public-pages.test.tsx`
- `bun run typecheck`
- Browser smoke：`/`、`/portal` 后端离线态、`/docs`、`/public/docs/openapi.json` 重定向与 390x844 移动首页均无 404、无可见 fallback 文案、无 console error。
