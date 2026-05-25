# REQ-20260525-007 异步边界同步代码排查

状态：Done  
日期：2026-05-25  
上游来源：用户反馈“整个项目都是异步的，但是发现好像有部分代码是同步的，排查下”。

## 背景

项目后端入口以 Spring WebFlux 为主，网关请求、上游调用、资源执行和管理端查询都存在异步/响应式语义。但当前依赖中同时包含 JPA、同步 Redis、Spring Mail、Liquibase 等天然同步组件；如果这些同步调用进入 WebFlux event loop 或高并发热路径，可能造成线程阻塞、延迟抖动和吞吐下降。

## 目标

- 排查后端代码中可能破坏异步边界的同步/阻塞调用。
- 区分真实运行时热路径、管理端低频路径、启动/测试路径和合理同步依赖。
- 给出文件级证据、风险等级、影响范围和建议修复顺序。
- 形成本地分析报告，并为后续修复拆出可追踪任务。

## 范围

- `src/main/java` 下的 controller、service、runtime、executor、repository 调用链。
- `src/test/java` 中可能掩盖异步问题的阻塞测试写法，仅作为验证边界参考。
- `build.gradle` 中会影响异步边界的同步依赖。
- `web/src` 中会导致 UI 线程长时间同步阻塞的明显模式。

## 非目标

- 本轮不直接重构业务代码。
- 不替换数据库技术栈。
- 不做真实压力测试或 BlockHound 接入。
- 不把测试中的 `block()`、`Thread.sleep()` 直接等同于生产缺陷。

## 方案

1. 先用静态扫描定位 `block()`、`subscribe()`、`toFuture().get()`、`Thread.sleep()`、同步 HTTP client、同步 repository、锁和阻塞队列等特征。
2. 结合文件位置和调用链判断是否处于 WebFlux 请求热路径。
3. 对 JPA/Redis/Mail 等同步依赖建立项目级风险说明。
4. 输出分析报告，并把后续修复项拆入本地任务体系。

## 风险

- 纯文本扫描可能产生误报，需要结合上下文人工复核。
- 项目当前存在大量未提交改动，本轮只新增排查文档和任务，避免覆盖现有工作。
- 同步 JPA 是架构级事实，修复可能涉及较大迁移成本，需要分阶段处理。

## 验收标准

- 报告列出所有高风险或中风险同步点，并说明证据路径。
- 对低风险/合理同步点给出排除原因。
- 后续需要代码改造的事项进入 `tasks/backlog/` 或保持明确状态。
- 本轮文档和任务状态反映真实排查结果。

## 测试边界

- 本轮以静态扫描和代码阅读为主。
- 不运行全量测试；如需后续修复，再按对应子任务补充定向测试。

## 关联任务

- `tasks/done/TASK-20260525-007-async-boundary-sync-code-audit-parent.md`
- `tasks/done/TASK-20260525-007-01-backend-runtime-blocking-audit.md`
- `tasks/done/TASK-20260525-007-02-dependency-and-frontend-sync-audit.md`

## 排查结果

- 已产出 `docs/reports/REP-20260525-007-async-boundary-sync-code-audit.md`。
- 已确认项目当前是 WebFlux 入口与同步服务核心混合形态，不是端到端异步。
- 已将后续修复拆入 `tasks/backlog/TASK-20260525-007-03-chat-runtime-reactive-boundary.md`、`tasks/backlog/TASK-20260525-007-04-resource-executor-reactive-boundary.md`、`tasks/backlog/TASK-20260525-007-05-blocking-infra-isolation-guardrails.md`。

## 验证记录

- 已执行静态扫描和关键调用链人工复核。
- 本轮未修改业务代码，未运行自动化测试。
