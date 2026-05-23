# TASK-20260523-009 协议入口兼容画像结构化编辑

状态：Done  
优先级：P0  
类型：父任务 + 子任务  
来源：`docs/requirements/REQ-20260523-008-protocol-endpoint-structured-profile-editor.md`

## 背景

协议入口编辑界面暴露可编辑 `Conversation Profile JSON`，配置语义过底层，普通用户不知道可配置项、取值范围和保存后的运行时效果。用户已批准改为去除高级 JSON 编辑，使用结构化方案承接常用兼容画像和 reasoning/thinking 控制。

## 目标

- 用结构化控件替代协议入口编辑弹窗中的可编辑高级 JSON。
- 保存时生成后端仍能识别的 `conversationProfile` 对象。
- 保留只读预览和未知字段保守保留能力。
- 更新相关测试和本地文档状态。

## 非目标

- 不改变后端 API 字段名和数据库 schema。
- 不重做厂商预设导入页。
- 不处理存量凭证 metadata 的自动批量迁移。
- 不扩展本轮之外的 audio/file/image 接口。

## 上游来源

- 用户反馈：高级 JSON 对用户不可理解，不应作为协议编辑的主要配置入口。
- 需求文档：`docs/requirements/REQ-20260523-008-protocol-endpoint-structured-profile-editor.md`

## 输入

- 当前协议入口编辑页面代码。
- 当前 endpoint `conversationProfile` 读写逻辑。
- 既有前端测试与本地运行服务。

## 输出

- 结构化协议兼容画像编辑 UI。
- 前端生成 `conversationProfile` 的映射与反解析逻辑。
- 更新后的测试断言与验证结果。
- 归档后的任务文件和关联需求交付记录。

## 影响范围

- `web/src/features/provider-sites/provider-site-detail-page.tsx`
- `web/src/features/provider-sites/provider-site-detail-page.test.tsx`
- `docs/index.md`
- `tasks/index.md`
- 本任务文件与关联需求文件

## 依赖

- 后端 `/admin/provider-sites/{siteId}/protocol-endpoints` 继续接受 `conversationProfile` 对象。
- 前端 dev server 已可访问，后端本地服务已启动。

## 风险

- 已有 JSON 字段如果全部丢弃，会影响 future profile 扩展；实现必须保留未知字段。
- UI 仍展示只读 JSON 预览时，需要清楚区分其不可编辑属性。
- 类型推导和测试 mock 需要同步，否则前端测试会误报。

## 子任务

### TASK-20260523-009-01 结构化画像映射

状态：Done  
边界：仅处理协议入口 draft 与 `conversationProfile` 的前端映射，不改后端存储。  
输出：已知字段反解析、未知字段保留、保存 payload 生成。  
验证：单元测试断言保存 payload。

### TASK-20260523-009-02 协议入口编辑 UI 收敛

状态：Done  
边界：仅替换协议入口编辑弹窗高级 Tab，不改厂商列表和预设导入页。  
输出：结构化选项、只读预览、移除可编辑 JSON textarea。  
验证：单元测试和浏览器手工检查。

### TASK-20260523-009-03 文档与任务回写

状态：Done  
边界：更新需求、任务和索引状态。  
输出：任务归档、交付记录、验证结果。  
验证：检查任务链接和状态。

## 验收标准

- 不再出现可编辑 `Conversation Profile JSON`。
- 结构化选项能覆盖协议兼容画像和 reasoning/thinking 常用配置。
- 保存 payload 的 `conversationProfile` 与结构化选择一致。
- 未识别 profile 字段保存后仍保留。
- `cd web; bun run test -- provider-site-detail-page.test.tsx` 通过。
- 浏览器中协议入口编辑弹窗展示正常。

## 测试边界

- 自动测试覆盖前端映射与保存 payload。
- 浏览器验证覆盖实际页面渲染和编辑弹窗可见性。
- 不执行真实上游厂商请求，不触发真实模型刷新。

## 关联文档

- `docs/requirements/REQ-20260523-008-protocol-endpoint-structured-profile-editor.md`
- `tasks/done/TASK-20260523-009-protocol-endpoint-structured-profile-editor.md`

## 当前状态

- 2026-05-23：任务创建，进入实现。
- 2026-05-23：已完成结构化画像映射，保存 payload 仍写入 `conversationProfile`，并对未知字段做保守保留。
- 2026-05-23：已完成协议入口编辑 UI 收敛，新增/编辑弹窗不再展示可编辑高级 JSON，仅保留只读运行时画像预览。
- 2026-05-23：验证通过：
  - `cd web; bun run test -- provider-site-detail-page.test.tsx`
  - `cd web; bun run typecheck`
  - 浏览器验证 `http://localhost:5173/console/provider-sites/1` 新增协议入口弹窗，确认存在结构化控件且不存在 `高级 JSON` / `Conversation Profile JSON`。
