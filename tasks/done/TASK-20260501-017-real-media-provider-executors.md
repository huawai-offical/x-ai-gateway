# TASK-20260501-017 真实 Video/Music Provider Executors 与产物闭环

状态：Done  
优先级：High  
来源：TASK-20260501-014 后续拆分  
关联任务：[TASK-20260501-014](../done/TASK-20260501-014-async-media-executors.md)
关联推进需求：[REQ-20260506-002](../../docs/requirements/REQ-20260506-002-eighth-priority-task-closure-design.md)
关联说明文档：[media-provider-executors](../../docs/media-provider-executors.md)

## 背景

当前 Video / Music 已有 gateway-local async task contract，但尚未接入真实第三方 provider、后台轮询、产物下载和 usage 归一。

## 目标

接入至少一个 Video provider 和一个 Music provider，让任务可以真实提交、轮询、取消并保存产物。

## 范围

- Provider executor SPI。
- 后台 worker / polling。
- 产物 URL、文件绑定、lineage、usage metadata。
- provider error 到 gateway async status 的映射。

## 非目标

- 不在此任务中重做通用 files/uploads 模型。

## 验收标准

- Video / Music 至少各一个真实 provider 可 create/get/cancel。
- 完成产物 lineage 与 operations 状态同步。
- 覆盖成功、失败、取消、provider timeout 测试。

## 本批推进记录

- 2026-05-06：进入第八批高优先级任务闭环，目标是补上 OpenAI-style 上游 media executor、状态同步和取消链路。
- 2026-05-06：完成 OpenAI-style Video/Music provider executor，显式上游模式支持 create/get/cancel，本地 fallback 和终态保护保持兼容。

## 实现结果

- `createVideoTask` 与 `createMusicTask` 在 `provider_mode` 或 `preferred_credential_id` 指定时进入上游 provider 模式。
- 上游 Video 路径为 `/v1/videos/generations`，Music 路径为 `/v1/music/generations`，查询与取消会使用持久化 metadata 中的 `upstream_object_id`。
- 本地 Video/Music task 的取消逻辑继续使用本地专用分支，保留 `user_cancelled` 元数据，并避免已失败终态被覆盖。
- capability truth 与 execution support matrix 已补齐 Video/Music/Async Task 原生执行标记。

## 测试/验证情况

已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests"
```

覆盖：

- 本地 Video 创建与取消。
- 本地 Music 失败终态取消保护。
- 上游 Video/Music create、get、cancel 路径、状态同步与 metadata lineage。

## 遗留问题

- 后台 polling worker、产物下载到 gateway file storage、usage 归一仍可拆成后续任务继续增强。

## 后续建议

- 下一步把 provider 完成事件接入统一 operations/usage rollup，并补真实 sandbox smoke 配置模板。
