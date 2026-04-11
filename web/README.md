# x-ai-gateway web

`web/` 是 `x-ai-gateway` 的前端代码目录，当前已初始化为 `React + TypeScript + Vite` 工程，并固定使用 `Bun` 作为运行时与包管理器。

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

- 提供前端工程基础骨架
- 提供最小页面、样式基线与脚本约定
- 为后续管理控制台、运维指挥台和平台配置页预留扩展入口

## 当前非目标

- 还未接入真实后端 API
- 还未引入业务路由、鉴权、状态管理或组件库
- 还未开始实现具体控制台业务页面
