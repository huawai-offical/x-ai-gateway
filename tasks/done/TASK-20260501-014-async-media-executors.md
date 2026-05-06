# TASK-20260501-014 Async Media Provider Executors 与任务状态存储

状态：Done  
优先级：High  
来源：本地拆分  
关联任务：[TASK-20260501-002](../done/TASK-20260501-002-non-chat-resources.md)  
关联设计：[REQ-20260501-003](../../docs/requirements/REQ-20260501-003-second-priority-task-closure-design.md)

## 背景

非 Chat 资源族已完成 canonical 语义层，但 Video、Music、Task lifecycle 仍缺少任务状态存储、lineage 和 usage 归一。

## 本轮目标

先完成 gateway-local async media task 生命周期，让 Video / Music 可以创建、查询、取消并进入统一 operations 与 admin async resource 视图。

## 本轮范围

- `GatewayAsyncResourceType` 增加 `VIDEO`、`MUSIC`。
- `GatewayAsyncResourceService` 增加 local media task create/get/cancel。
- public API 增加 Video / Music generation task endpoints。
- operations 支持按 `video`、`music` 查询。

## 非目标

- 不一次性覆盖所有视频/音乐供应商。
- 不在本轮接入真实第三方 media provider。

## 验收标准

- Video 和 Music 任务可 create/get/cancel。
- 任务状态可在本地 operations 查询。
- 失败和取消路径有测试。

## 实现记录

- `GatewayAsyncResourceType` 增加 `VIDEO`、`MUSIC`。
- `GatewayAsyncResourceService` 增加 gateway-local media task `create/get/cancel`，写入 `gateway_async_resource`，metadata 标记 `gateway_local_async_task`，cancel 会记录 `user_cancelled` 事件。
- `GatewayPublicResourceService` 增加 Video / Music wrapper，并支持 `operations?resourceType=videos|music` 查询与取消。
- 新增 `GatewayMediaTasksController`，提供 `/api/v1/videos/generations`、`/api/v1/videos/{videoId}`、`/api/v1/videos/{videoId}/cancel`、`/api/v1/music/generations`、`/api/v1/music/{musicId}`、`/api/v1/music/{musicId}/cancel`。

## 测试/验证

- 通过：`GatewayAsyncResourceServiceTests`，覆盖 Video 创建/取消、Music failed 终态不被 cancel 改写。
- 通过：`GatewayPublicResourceServiceTests`，覆盖 Video operations 查询与取消。
- 通过：`GatewayPublicResourceControllersTests`，覆盖 Video public API。
- 通过补充回归：`GatewayRequestFeatureServiceTests`、`GatewayRouteSelectionServiceTests`、`GovernancePolicyEngineServiceTests`。

## 遗留问题

- 本轮只完成本地 async task contract，不代表真实第三方 Video / Music provider 已接入。
- 任务执行 worker、进度更新、usage 计费与产物下载还需后续拆分。
