# TASK-20260521-012-04 TLS 默认画像与键值对 Header 编辑

状态：Done  
优先级：Critical  
上游来源：[TASK-20260521-012](./TASK-20260521-012-console-health-codex-model-tls-workbench-ux.md)

## 任务类型

子任务

## 背景

TLS 指纹创建缺少默认常用画像，header 字段要求用户手写 JSON，容易输错，也不符合“尽量不要让用户手填”的界面原则。

## 目标

- 初始化 Codex CLI、Claude Code、Web Browser 等默认 TLS/header 画像。
- 创建/编辑表单使用 key/value 行编辑 header。
- 前端提交前自动转换为 JSON，编辑时自动回填 key/value 行。

## 非目标

- 不保证默认画像完全等同各客户端实时版本。
- 不采集用户本机浏览器真实 header。

## 输入

- `web/src/features/network/tls-profiles-page.tsx`
- 后端 TLS 指纹实体、初始化 service、测试

## 输出

默认画像初始化、键值对编辑器、测试更新。

## 影响范围

网络治理中的 TLS 指纹管理。

## 依赖

现有 TLS profile CRUD。

## 风险

默认画像需要幂等，header 重复 key 与空值需要处理。

## 验收标准

- [x] 新库初始化后存在 Codex CLI、Claude Code、Web Browser 默认画像。
- [x] Header 字段不再要求用户手写 JSON。
- [x] 编辑已有 JSON 能正确回填为键值对。

## 测试边界

- TLS profile 前端测试。
- 后端默认画像初始化测试。
- `npm run typecheck`

## 当前状态

已完成。默认画像由后端幂等初始化，前端提供常用画像选择、Header 键值对增删行和 `settingsJson` 自动转换。
