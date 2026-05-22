# TASK-20260521-010-02 系统参数页面深度汉化

状态：Done  
优先级：High  
上游来源：[TASK-20260521-010](./TASK-20260521-010-console-navigation-settings-codex-auth-import-refresh.md)

## 任务类型

子任务

## 背景

系统参数页面仍有英文标题、字段标签、状态文案或 camelCase 内部字段名直接展示给用户，需要继续按中文单语界面口径收口。

## 目标

- 汉化系统参数页面用户可见文案。
- 将内部字段名展示转换为中文标签，例如 `requestId` 类似口径应展示为 `请求 ID`。
- 保留配置 key、枚举值、Provider 名称等技术识别信息。

## 非目标

- 不改后端配置 API 契约。
- 不引入运行时多语言切换。

## 输入

- `web/src/features/settings/`
- 相关测试文件

## 输出

- 中文化后的系统参数页面。
- 更新后的测试断言。

## 影响范围

- Console 系统参数页面。
- 相关表格、表单、空态、错误态和状态标签。

## 依赖

- [TASK-20260521-007](../in-progress/TASK-20260521-007-ui-chinese-only-localization.md)

## 风险

- 误翻译配置 key 会影响用户定位真实系统参数。

## 验收标准

- [x] 系统参数页面静态英文文案完成汉化。
- [x] 用户可见内部字段名完成中文标签化。
- [x] 技术 key 与枚举值仍可辨认。

## 测试边界

- 前端：系统参数相关 vitest
- 前端：`bun run typecheck`
- 静态检索：`web/src/features/settings` 英文文案残留

## 实现结果

- 已将 `System Settings`、`Prefix Affinity`、`Fingerprint Max Tokens`、`Upstream Cache`、`Upstream Runtime` 等用户可见英文改为中文。
- 保留 Provider、SDK、HTTP、Token、TTL 等技术术语，避免误改真实配置 key 或协议名。
- 更新系统参数页面测试断言。

## 验证结果

- `npm test -- --run src/features/settings/system-settings-page.test.tsx`
- `npm run typecheck`

## 当前状态

已完成

