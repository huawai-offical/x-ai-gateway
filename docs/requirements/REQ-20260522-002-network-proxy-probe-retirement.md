# REQ-20260522-002 网络代理拨测功能下线

状态：Done  
日期：2026-05-22  
上游来源：用户指令“不需要对网络代理进行拨测，删除掉这个功能”

## 背景

当前控制台存在网络代理拨测能力：网络拨测页可手动选择代理执行拨测，代理详情页展示手动拨测和拨测历史，运维拨测任务页仅支持 `NETWORK_PROXY` 类型的定时任务。用户确认不需要对网络代理进行拨测，因此这些入口和后端支撑应从现役产品面删除。

## 目标

- 删除网络代理手动拨测入口、结果列表和详情展示。
- 删除仅服务于网络代理拨测的运维定时拨测任务入口。
- 删除网络代理拨测后端 API、服务逻辑、实体、仓储和 baseline 表结构。
- 更新前端导航、路由、测试和本地任务索引，避免继续暴露无效入口。

## 范围

- 前端网络代理拨测页面、代理详情页拨测区块、运维代理拨测任务页、导航和路由。
- 后端 `/admin/network/probes`、`/admin/network/proxies/{id}/probe`、`/admin/ops/probes` 相关接口和服务。
- `network_proxy_probe_result` 与仅用于代理定时拨测任务的持久化结构。
- 相关前后端测试与本地文档任务。

## 非目标

- 不删除网络代理本身的创建、编辑、绑定和治理能力。
- 不删除凭证和账号上的 `proxyId` 绑定能力。
- 不删除通用运维事件页面中的即时 URL/TCP 健康探测，除非后续确认该通用探测也下线。
- 不改变路由治理中 `proxyId` 作为策略维度的语义。

## 风险

- 运维总览可能引用定时拨测任务摘要，删除时需要同步响应模型和前端消费。
- 代理详情页去掉拨测后仍需保留基础信息、引用关系和可编辑能力。
- baseline 已重建为单一入口，删除表结构需要直接修改 `db.changelog-0001-baseline.yaml`。

## 验收标准

1. 控制台不再展示网络拨测或代理拨测任务入口。
2. 代理详情页不再展示手动拨测按钮和拨测历史。
3. 后端不再暴露网络代理拨测 API 与仅支持 `NETWORK_PROXY` 的定时拨测 API。
4. baseline 不再包含网络代理拨测结果表与代理定时拨测任务表。
5. 相关定向测试和类型/编译检查通过。

## 测试边界

- 前端：导航、网络代理详情、路由相关定向测试。
- 后端：网络代理、运维控制器、服务编译和相关定向测试。
- 类型检查：`npm run typecheck`。
- Java 编译：`.\gradlew.bat compileJava compileTestJava` 或更小范围测试。

## 实现结果

- 前端删除 `network/probes` 和 `ops/probes` 代理拨测页面，导航不再展示“网络拨测”和“拨测记录”。
- 旧路由 `/console/network/probes` 重定向到代理池，`/console/ops/probes` 重定向到智能运维总览。
- 代理详情页移除手动拨测按钮、最近拨测状态和拨测历史，仅保留代理基础信息。
- 后端删除 `/admin/network/probes`、`/admin/network/proxies/{id}/probe`、`/admin/ops/probes` 相关 controller、service、request/response、entity 和 repository。
- `OpsSummaryResponse` 删除 `probeJobs` 字段，智能运维总览不再聚合代理拨测任务。
- `network_proxy` 删除拨测状态列，baseline 删除 `network_proxy_probe_result` 和 `ops_scheduled_probe_job` 表。
- 代理健康判断从“启用且最近拨测未失败”收敛为“代理已启用”，避免依赖已下线拨测状态。

## 验证结果

- `npm run typecheck`：通过。
- `npm test -- --run src/app/navigation.test.ts src/features/network/proxies-page.test.tsx src/features/network/proxy-detail-page.test.tsx src/features/ops/ops-page.test.tsx src/features/ops/system-events-page.test.tsx`：5 个测试文件、7 条测试通过。
- `.\gradlew.bat compileJava compileTestJava`：通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.api.ProxyAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.api.OpsAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.NetworkGovernanceServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests"`：通过。
- 残留扫描确认现役代码无 `/admin/network/probes`、`/admin/network/proxies/{id}/probe`、`/admin/ops/probes`、`network_proxy_probe_result`、`ops_scheduled_probe_job` 引用。

## 关联任务

- [TASK-20260522-002](../../tasks/done/TASK-20260522-002-network-proxy-probe-retirement.md)
