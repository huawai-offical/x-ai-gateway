# x-ai-gateway web

`web/` 是 `x-ai-gateway` 的前端控制台代码目录，当前已经接入业务路由和多组后台页面，固定使用 `Bun` 作为运行时与包管理器。

## 技术栈

- `React 19`
- `TypeScript 6`
- `Vite 8`
- `Bun 1.3.12`

## 常用命令

```bash
bun install
bun run dev
bun run typecheck
bun run lint
bun run build
bun run preview
```

## 当前范围

- DistributedKey / keys 页面
- account pools / OAuth connect / account detail 页面
- provider sites / capability matrix / translation debug 页面
- ops / alerts / probes / logs 页面
- error rules、install、backups、upgrades、rollbacks 页面
- `typed-react-query` 与统一 API 请求封装

## 当前非目标

- 还没有完整鉴权流与登录态管理
- 还没有浏览器级 E2E 自动化
- 还没有做生产构建发布编排

## 路由概览

- `/keys`
- `/account-pools`
- `/accounts/connect/:provider`
- `/network/proxies`
- `/ops`
- `/error-rules`
- `/provider-sites`
- `/capability-matrix`
- `/translation-debug`
- `/operations/install`
- `/operations/backups`
- `/operations/upgrades`
- `/operations/rollbacks`

## 常用命令

```bash
bun install
bun run dev
bun run test -- --run
bun run check
bun run build
```
