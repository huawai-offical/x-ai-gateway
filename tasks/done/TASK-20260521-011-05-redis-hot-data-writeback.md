# TASK-20260521-011-05 Redis 热数据增强与 PostgreSQL 回写边界

状态：Done  
优先级：High  
上游来源：[TASK-20260521-011](./TASK-20260521-011-console-surface-prune-logo-credential-redis.md)

## 任务类型

子任务

## 背景

项目中请求日志、配额、凭证运行态、账号刷新状态等数据更新频繁。用户要求加强 Redis 依赖介入，提高并发度，并让数据逐步同步到 PostgreSQL。

## 目标

- 梳理当前已有 Redis 使用点和高频写入点。
- 选择首批可控热点引入 Redis 写入或缓存。
- 明确 Redis 到 PostgreSQL 的回写触发、幂等和降级策略。
- 补充测试或文档，避免只停留在口头设计。

## 非目标

- 不一次性重写所有统计、日志和账务持久化。
- 不让 Redis 成为唯一事实源；PostgreSQL 仍保留最终事实源。

## 输入

- 后端 Redis 配置与 repository/service
- 请求日志、账号/凭证运行态、配额/计数相关 service
- 现有测试

## 输出

首批 Redis 增强实现或可执行迁移边界、测试和文档回写。

## 验收标准

- [x] 明确首批 Redis 热点数据范围。
- [x] 至少一个高频运行态或计数链路完成 Redis 介入，或形成带代码边界的可执行迁移任务。
- [x] PostgreSQL 回写策略记录到任务与需求文档。
- [x] 相关后端测试或编译验证通过。

## 测试边界

- Redis 相关 service 定向测试
- 不依赖外部真实 Redis 的测试需使用 fake/stub 或现有本地测试配置

## 当前状态

已完成。首批范围为凭证与账号运行指标，Redis observability 队列批量合并后回写 PostgreSQL；Redis 写入失败时保留同步写库降级。后端定向测试通过。
