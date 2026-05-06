# ADR-0007 第六批任务采用“先校准差距，再补生产运行态与账号入口”闭环

状态：Accepted
日期：2026-05-05
关联需求：[REQ-20260505-002](../requirements/REQ-20260505-002-sixth-priority-task-closure-design.md)

## 背景

X-263 是第二轮差距总览，其中大量子项已经在 Linear 中显示完成。与此同时，本地 backlog 中仍存在两个明确高价值缺口：Route Policy runtime store 仍偏单实例内存态，社交 OAuth provider 仍只完成 Google/GitHub 基础链路。

## 决策

本批采用三段式范围：

- 先做 X-263 代码态审计，只校准和拆分，不盲目重复实现。
- Route Policy runtime store 先抽象接口，再提供 Redis 实现，保留内存 fallback。
- 社交 OAuth provider 扩展优先做 provider client、配置、JWK/key 缓存和 mock contract tests；真实线上 smoke 以 checklist 和环境变量方式沉淀，不写入真实 secret。

## 影响

- 本批既能校准 backlog，又能推进两个生产化底座能力。
- Redis 不可用时默认不让系统硬失败，先回退本地内存并通过后续告警/配置增强。
- OAuth provider 差异通过 provider client 隔离，避免把 QQ、WeChat、Meta、X 的特例塞进 portal service 主流程。

## 取舍

- 不在本批解决 X-263 的所有剩余缺口。
- 不把真实 OAuth smoke 做成默认 CI。
- 不强制 Redis 成为唯一 runtime store，避免本地开发和测试环境被外部依赖阻塞。
