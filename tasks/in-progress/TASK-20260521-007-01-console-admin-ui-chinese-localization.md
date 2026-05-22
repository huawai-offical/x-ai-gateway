# TASK-20260521-007-01 Console / Admin 界面汉化与说明性文案清理

状态：进行中  
上游来源：[TASK-20260521-007](./TASK-20260521-007-ui-chinese-only-localization.md)

## 背景

Console / Admin 现役页面中存在导航、按钮、Dialog 描述、空状态与说明性正文混杂英文或产品解释文案的情况，尤其影响凭证、账号分组、网络、集成、资源与模型等控制台页面的一致性。

## 目标

- 汉化 Console / Admin 现役页面中的静态 UI 文案。
- 清理凭证、账号分组等页面里解释产品边界的说明性正文。
- 保留必要技术术语、错误提示、运行反馈和数据内容。

## 非目标

- 不翻译后端枚举原值的存储形式，只处理界面展示。
- 不改动 Console 以外的 Portal/Public 主页面。

## 输入

- `web/src/app/navigation.ts`
- `web/src/features/credentials/`
- `web/src/features/accounts/`
- `web/src/features/models/`
- `web/src/features/network/`
- `web/src/features/integrations/`
- `web/src/features/resources/`
- `web/src/features/request-logs/`
- `web/src/features/user-domain/`

## 输出

- Console / Admin 页面中文化与说明性文案清理结果

## 验收标准

- [ ] 相关现役页面静态 UI 文案默认中文化。
- [ ] 凭证/账号分组等页面中的解释性正文删除完成。
- [ ] 页面相关测试断言同步通过。

## 测试边界

- 检索相关目录中的明显英文静态文案
- 定向前端类型检查与测试

## 当前状态

进行中

## 2026-05-21 当前进展

- 已将导航与控制台标题中的 `API 密钥 / Secret` 统一收口为 `上游凭证`，并同步更新凭证录入弹窗、步骤标题、字段标签与测试断言。
- 已将 `OAuth connect/callback` 页面中的 `ready`、`missing groupId`、`SUCCESS`、`FAILED`、`PENDING`、`Session Key` 等用户可见文案收成中文，同时把“回收进治理链路”这类内部实现口吻改成更自然的业务表达。
- 已删除 `resources` 页面顶部的解释性说明块，并把资源类型、生命周期状态、对象模式、维护窗口范围类型等直接暴露给用户的枚举值改成中文标签。
- 已删除 `ops alerts` 中的迁移提示徽标，补齐 `governance` 里的 `路由运行时状态`、`错误策略` 等中文口径，并把 `proxy detail` 的 `Last Latency`、未知状态展示改成中文。
- 已通过 `credentials`、`oauth-connect`、`resources`、`ops-alerts`、`governance`、`windows` 等相关定向测试与 `bun run typecheck`。
