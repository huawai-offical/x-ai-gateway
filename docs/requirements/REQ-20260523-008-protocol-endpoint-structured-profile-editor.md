# REQ-20260523-008 协议入口兼容画像结构化编辑

状态：Done  
提出时间：2026-05-23  
关联任务：`tasks/done/TASK-20260523-009-protocol-endpoint-structured-profile-editor.md`

## 背景

厂商详情的协议入口编辑弹窗当前暴露了可编辑的 `Conversation Profile JSON`。该字段用于保存协议兼容画像、Responses 到 Chat Completions 兼容方式、thinking/reasoning 注入和历史回放策略，但普通用户无法从 JSON 字段名判断可配置项、取值范围和保存后的运行时含义。

用户明确反馈该高级 JSON “太过于鸡肋”，希望不要继续把底层 JSON 作为主要配置入口。

## 目标

- 移除协议入口编辑弹窗中的可编辑高级 JSON 区域。
- 将常用协议兼容画像改为结构化选项，包括默认直连、OpenAI-compatible Chat Completions、Responses 转 Chat Completions、Anthropic Messages 和 Gemini GenerateContent。
- 将 reasoning/thinking 相关配置改为结构化选项，覆盖 thinking 注入、assistant reasoning 字段和工具历史回放策略。
- 保留只读运行时画像预览，便于排查保存结果，但不允许用户直接编辑 JSON。
- 保存时仍复用后端既有 `conversationProfile` 字段，避免扩大 API 与数据库改动面。
- 对已有未知画像字段做保守保留，避免编辑协议入口时丢失未来扩展字段或已有补充元数据。

## 非目标

- 不重做后端 `conversationProfile` 存储模型。
- 不新增任意 JSON 编辑入口。
- 不改变模型刷新、凭证选择、厂商预设导入或资源型接口实现。
- 不在本任务内处理既有凭证 metadata 的批量回填策略。

## 范围

- 前端协议入口详情页：`web/src/features/provider-sites/provider-site-detail-page.tsx`
- 前端相关测试：`web/src/features/provider-sites/provider-site-detail-page.test.tsx`
- 本地文档与任务索引：`docs/index.md`、`tasks/index.md`

## 风险

- 如果已有 endpoint 存在无法识别的 profile 字段，结构化编辑后可能出现字段保留策略不清晰；本任务要求保留未知字段，仅替换已被结构化控件接管的字段。
- 如果现有测试直接断言 `Conversation Profile JSON` 文案，需要同步调整为结构化控件断言。
- 只读预览仍显示 JSON，需避免让用户误认为它可编辑。

## 验收标准

- 协议入口编辑弹窗不再出现可编辑的 `Conversation Profile JSON` 文本域。
- 编辑弹窗提供结构化的兼容画像、Thinking 注入、Assistant Reasoning 字段和工具历史回放配置。
- 保存协议入口时，前端 payload 仍包含正确生成的 `conversationProfile` 对象。
- 已有未知 profile 字段在保存时不被结构化控件误删。
- 相关前端测试通过，浏览器中能打开厂商详情并看到新的结构化编辑界面。

## 验证方式

- `cd web; bun run test -- provider-site-detail-page.test.tsx`
- 浏览器手工验证 `http://localhost:5173/admin/provider-sites/:id` 的协议入口编辑弹窗。

## 交付记录

- 2026-05-23：需求创建，进入实现。
- 2026-05-23：已移除协议入口编辑中的可编辑高级 JSON，改为结构化兼容画像、Thinking 注入、Assistant Reasoning 字段和工具历史回放控件；保留只读运行时画像预览，并在保存时生成后端既有 `conversationProfile` 对象。
- 2026-05-23：已通过 `cd web; bun run test -- provider-site-detail-page.test.tsx`、`cd web; bun run typecheck` 和浏览器手工验证。
