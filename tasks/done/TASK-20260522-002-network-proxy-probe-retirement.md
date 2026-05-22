# TASK-20260522-002 网络代理拨测功能下线

状态：Done  
优先级：Critical  
上游来源：[REQ-20260522-002](../../docs/requirements/REQ-20260522-002-network-proxy-probe-retirement.md)

## 任务类型

父任务

## 背景

网络代理拨测功能已被确认不需要继续保留。当前实现分散在网络拨测页、代理详情页、运维代理拨测任务页和后端代理拨测 API 中，需要统一下线。

## 目标

- 删除网络代理手动拨测和历史记录页面能力。
- 删除仅支持 `NETWORK_PROXY` 的运维定时拨测任务能力。
- 删除相关后端 API、服务、实体、仓储和 baseline 表结构。
- 更新导航、路由、测试、文档和索引。

## 非目标

- 不删除网络代理配置本身。
- 不删除凭证、账号、路由策略中对代理 ID 的引用。
- 不删除通用运维即时 URL/TCP 健康探测。

## 输入

- `web/src/features/network/probes-page.tsx`
- `web/src/features/network/proxy-detail-page.tsx`
- `web/src/features/ops/ops-probes-page.tsx`
- `web/src/app/router.tsx`
- `web/src/app/navigation.ts`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/*Probe*`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/*Probe*`
- `src/main/resources/db/changelog/changes/db.changelog-0001-baseline.yaml`

## 输出

- 已删除网络代理拨测功能的前后端代码。
- 已更新的 baseline 和测试。
- 已回写的需求、任务和索引。

## 影响范围

控制台网络代理页面、网络代理详情、运维拨测任务、运维摘要响应、后端 Admin API、baseline schema。

## 依赖

- 当前 baseline 为单一 `db.changelog-0001-baseline.yaml`。
- 网络代理本体和 proxyId 绑定能力继续保留。

## 风险

- 删除定时拨测任务服务后，运维总览响应和测试需要同步去掉 `probeJobs`。
- 删除网络代理拨测结果表后，实体和仓储引用必须同步清理，否则编译失败。
- 前端路由和导航若只删页面不删引用，会产生懒加载失败或死链。

## 验收标准

- [x] 网络代理拨测入口和页面已删除。
- [x] 代理详情页不再展示拨测操作和历史。
- [x] 后端网络代理拨测和代理定时拨测 API 已删除。
- [x] baseline 中相关表结构已删除。
- [x] 相关测试、类型检查和 Java 编译通过。

## 测试边界

- `npm run typecheck`
- 前端导航/代理相关定向 vitest。
- `.\gradlew.bat compileJava compileTestJava`
- 后端网络代理/运维相关定向测试。

## 当前状态

已完成，待归档到 `tasks/done/`。

## 实现结果

- 删除前端网络拨测页和运维代理拨测任务页，并从导航中移除入口。
- 旧路由保留重定向，避免历史链接直接进入空白页。
- 代理列表和详情页移除最近拨测状态、最近探测时间、手动拨测和拨测历史。
- 删除网络代理拨测 Admin API、代理定时拨测 Admin API、服务、实体和仓储。
- `OpsSummaryResponse` 移除 `probeJobs`，智能运维总览不再依赖代理拨测任务。
- baseline 删除 `network_proxy_probe_result`、`ops_scheduled_probe_job`，并删除 `network_proxy` 的拨测状态列。
- 路由选择和账号选择里的代理健康判断改为只依赖代理启用状态。

## 验证结果

- `npm run typecheck`：通过。
- `npm test -- --run src/app/navigation.test.ts src/features/network/proxies-page.test.tsx src/features/network/proxy-detail-page.test.tsx src/features/ops/ops-page.test.tsx src/features/ops/system-events-page.test.tsx`：5 个测试文件、7 条测试通过。
- `.\gradlew.bat compileJava compileTestJava`：通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.api.ProxyAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.api.OpsAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.NetworkGovernanceServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests"`：通过。
