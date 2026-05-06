# TASK-20260506-021 AI IDE/CLI 官方账号导入与配额刷新

状态：Backlog  
优先级：High  
来源：[REP-20260506 参考项目功能深度再复核](../../docs/reports/REP-20260506-reference-feature-depth-recheck.md)  
关联需求：[REQ-20260506-012 参考项目功能深度复核与任务再生成](../../docs/requirements/REQ-20260506-012-reference-depth-recheck-task-generation.md)

## 背景

对照 `cockpit-tools-main`，AI IDE/CLI 账号运营的关键能力是账号导入、订阅/配额识别、批量管理和自动刷新。当前 `x-ai-gateway` 有服务端 account pool、quota 字段和 client metadata 契约，但缺针对 Codex、GitHub Copilot、Gemini CLI 等官方账号的云端导入与配额刷新闭环。

## 目标

- 设计并实现云端官方账号导入、刷新和配额查询的最小闭环。
- 将 quota window、reset time、plan/subscription tier、quota error 写入账号池运营数据。
- 为调度器提供可解释的账号可用性和配额状态。

## 范围

- Codex、GitHub Copilot、Gemini CLI 首批可行性与 adapter 边界。
- OAuth/Token/JSON 导入的安全模型和加密存储。
- 配额刷新 job、失败降级、账号标签与批量操作。
- Admin 账号池 UI 展示 quota window、reset time、plan 和 last refresh。

## 非目标

- 不读取用户本机 profile 或 workspace。
- 不实现设备指纹、风控规避或切号注入。
- 不承诺非公开接口长期稳定。

## 验收标准

- 至少一个官方账号类型完成导入、刷新、配额查询和调度可见闭环。
- 配额刷新失败有可解释错误和下一次重试时间。
- Token/secret 加密存储并有审计。
- Admin UI 可筛选 quota 状态、plan 和 refresh health。

## 实现记录

待处理。

## 测试/验证

待处理。

## 遗留问题

待处理。
