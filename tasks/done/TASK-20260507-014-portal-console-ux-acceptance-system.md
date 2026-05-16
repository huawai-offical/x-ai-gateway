# TASK-20260507-014 Portal/Console UI/UX 验收体系与组件硬化

状态：Done  
优先级：Medium  
排期：P1-05  
来源：[REQ-20260507-001 社区 Portal 与后台 Console 角色化界面任务体系](../../docs/requirements/REQ-20260507-001-portal-admin-role-surface-task-system.md)
关联需求：[REQ-20260507-003 第二批最高优先级任务闭环设计](../../docs/requirements/REQ-20260507-003-next3-priority-closure-design.md)

## 背景

Portal 和 Admin Console 的用户目标不同，但都需要稳定的可用性标准。当前前端已经有大量页面和组件，但仍存在裸 ID、CSV 输入、长表格、移动端溢出、空态缺少下一步、错误态不够可解释等问题。需要建立跨 Portal/Console 的验收体系，和 `TASK-20260507-007` 形成落地任务。

## 目标

- 建立 Portal/Console UI/UX 验收矩阵。
- 将高风险输入迁移为 picker、combobox、multi-select、stepper 或校验型 field array。
- 增加桌面和移动 viewport 回归。
- 统一 loading、empty、error、success、confirm 等状态标准。

## 范围

- 验收清单：路由、导航、表单、表格、空态、错误态、移动端、权限态。
- 组件硬化：资源 picker、masked secret、copy button、status badge、danger confirm、inline help。
- 测试 helper：viewport matrix、文字溢出检查、表格溢出检查、空态 CTA 检查。
- 优先页面：Portal 首页、Portal Key、Codex 接入向导、Console dashboard、账号池、request logs、usage、ops governance。

## 非目标

- 不做整站视觉重绘。
- 不引入大型 UI 框架替换。
- 不牺牲 Console 桌面端高密度扫描效率。

## 详细设计

- 定义每个页面必须覆盖的状态：loading、empty、loaded、error、permission denied。
- 高风险字段不再要求手写裸 ID，优先通过 API-backed picker 选择。
- 长表格在窄屏使用横向滚动、固定操作列或列表化呈现。
- 所有 destructive 操作必须有确认语义、影响说明和禁用条件。
- 对按钮和卡片文本做溢出测试，避免长中文、英文 ID、邮箱、模型名破坏布局。

## 验收标准

- 至少 5 个核心页面通过桌面与移动 viewport 回归。
- Codex 接入链路不要求用户手写关键裸 ID。
- 空态包含下一步 CTA 或明确说明。
- 错误态能说明业务原因和下一步动作。
- `TASK-20260507-007` 可以引用本验收矩阵逐步完成页面级硬化。

## 风险

- 表单组件化容易扩大改动范围，需优先覆盖 Codex 接入和高风险字段。
- 移动端优化应保证可操作，不要求把专业管理台做成低密度移动应用。

## 进度记录

- 2026-05-07：进入实现批次，先落 UI/UX 验收事实源和核心矩阵，后续页面级重构继续由 `TASK-20260507-007` 承接。
- 2026-05-07：完成 `ux-acceptance.ts` 和 `ux-acceptance.test.ts`，覆盖 Portal/Console 核心页面、desktop/mobile viewport、必备状态、高风险输入和破坏性操作规则。

## 实现结果

- 建立 `uxViewports`、`requiredUxStates`、`highRiskInputRules` 和 `uxAcceptancePages` 事实源。
- 核心页面覆盖 Portal 首页、Portal Key、Console account pools、request logs、ops governance、Codex onboarding。
- 校验函数能发现缺 viewport、缺状态、缺空态 CTA、缺错误恢复、Console/Portal 路径错误和破坏性操作缺确认。

## 验证结果

- `bun run test -- src/app/ux-acceptance.test.ts` 通过。
- `bun run typecheck` 通过。
- `bun run test` 通过。

## 遗留问题

- 本任务闭环验收体系和测试基线；页面级逐项视觉与表单重构继续由 `TASK-20260507-007` 承接。
