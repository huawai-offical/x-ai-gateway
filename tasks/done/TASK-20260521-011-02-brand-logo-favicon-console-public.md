# TASK-20260521-011-02 品牌 Logo 与 favicon 接入

状态：Done  
优先级：High  
上游来源：[TASK-20260521-011](./TASK-20260521-011-console-surface-prune-logo-credential-redis.md)

## 任务类型

子任务

## 背景

项目需要一个可识别的 Logo，用于替换默认标签页图标，并在公开首页和控制后台统一展示。

## 目标

- 创建项目 Logo 资源。
- 替换 favicon。
- 在公开首页品牌区和控制台侧栏品牌区展示 Logo。

## 非目标

- 不引入外部在线素材依赖。
- 不做复杂品牌手册。

## 输入

- `web/public/`
- `web/index.html`
- `web/src/features/public/`
- `web/src/components/app/app-shell.tsx`

## 输出

本地 Logo 资源与接入点。

## 验收标准

- [x] 浏览器标签页使用新 Logo。
- [x] 公开首页可见新 Logo。
- [x] 控制台侧栏可见新 Logo。
- [x] Logo 在 dark/light 下均清晰。

## 测试边界

- 前端 typecheck
- 浏览器截图/手工验证

## 当前状态

已完成。`web/public/logo.svg` 与 `web/public/favicon.svg` 已接入公开首页和控制台侧栏，前端 typecheck 通过。
