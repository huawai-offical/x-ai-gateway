# TASK-20260521-007-02 Portal / Public / Workbench 界面汉化

状态：进行中  
上游来源：[TASK-20260521-007](./TASK-20260521-007-ui-chinese-only-localization.md)

## 背景

Portal、Public 与 Workbench 页面仍存在较多英文标题、副标题、按钮和说明文案，影响整体中文一致性。

## 目标

- 汉化 Portal、Public 与 Workbench 现役页面中的静态 UI 文案。
- 删除仅用于解释页面边界的说明性正文。
- 保留必要技术术语、品牌名、协议名与用户数据。

## 非目标

- 不改动 Console / Admin 页面。
- 不翻译公告正文、用户数据和后端返回的自由文本内容。

## 输入

- `web/src/features/portal/`
- `web/src/features/public/`
- `web/src/features/workbench/`

## 输出

- Portal / Public / Workbench 页面中文化结果

## 验收标准

- [ ] 相关现役页面静态 UI 文案默认中文化。
- [ ] 顶部介绍文案和多余说明性正文按需删除或汉化。
- [ ] 页面相关测试断言同步通过。

## 测试边界

- 检索相关目录中的明显英文静态文案
- 定向前端类型检查与测试

## 当前状态

进行中
