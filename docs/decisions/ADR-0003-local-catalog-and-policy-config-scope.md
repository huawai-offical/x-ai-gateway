# ADR-0003 第二批任务继续采用本地可验证闭环

状态：Accepted  
日期：2026-05-01  
关联需求：[REQ-20260501-003 第二批最高优先级任务闭环设计](../requirements/REQ-20260501-003-second-priority-task-closure-design.md)

## 决策

第二批任务继续采用“本地可验证闭环优先”的范围控制：

- Provider Catalog 先支持 classpath JSON 与 fallback，不接远程 marketplace。
- Async Media 先支持 gateway-local task lifecycle，不接真实第三方视频/音乐 provider。
- Routing Policy 先落后端配置字段和摘要，不在本轮改路由选择行为。

## 理由

这些切片都能直接复用现有系统结构，并为后续真实 provider、前端配置、策略引擎执行提供稳定 contract。这样做可以在不扩大风险的前提下继续推进项目能力。

## 后果

- 本轮可交付后端可测试能力。
- 真实 media provider、远程 catalog、完整策略 UI 仍需后续任务承接。
