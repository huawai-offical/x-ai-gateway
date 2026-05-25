# TASK-20260525-004-01 凭证详情与联通性探测后端

## 类型

子任务 / task spec

## 背景

凭证详情弹窗需要真实后端数据支撑，包括基础信息、用量摘要和最近联通性探测结果；探测结果需要持久化到凭证记录。

## 目标

- 扩展凭证响应结构，提供详情与最近探测状态。
- 增加凭证联通性测试 API。
- 为凭证表增加最近探测结果字段。
- 覆盖探测成功/失败记录测试。

## 非目标

- 不做批量探测。
- 不新增定时任务。
- 不暴露密钥明文。

## 上游来源

- `docs/requirements/REQ-20260525-004-credential-detail-probe.md`
- `tasks/in-progress/TASK-20260525-004-credential-detail-probe-parent.md`

## 输入

- Admin 凭证 Controller / Service / Response。
- `UpstreamCredentialEntity`
- Liquibase changelog。

## 输出

- `POST /admin/credentials/{id}/connectivity-test`
- 扩展后的凭证详情响应。
- 数据库迁移。
- 后端定向测试。

## 影响范围

- Admin 凭证 API。
- 上游凭证持久化结构。

## 依赖

- Credential material resolver / crypto service。
- 现有 Gateway chat execution 或轻量 HTTP 探测能力。

## 风险

- 探测请求需要适配多 provider auth/path。
- 错误摘要必须脱敏和截断。

## 验收标准

- 探测成功记录 `AVAILABLE`。
- 探测失败记录 `UNAVAILABLE` 和错误摘要。
- API 响应不包含密钥明文。

## 测试边界

- 后端 unit/service tests。
- 不默认执行真实上游请求。

## 当前状态

Done

## 实现结果

- 新增 `POST /admin/credentials/{id}/connectivity-test`。
- 新增 `upstream_credential` 最近联通性探测字段与 Liquibase `0008` 迁移。
- 扩展 `CredentialResponse`、`UpstreamCredentialInventoryResponse` 与 `CredentialConnectivityResponse`。
- 探测会发送 chat/messages/generateContent 类极短请求，并将成功记录为 `AVAILABLE`、失败记录为 `UNAVAILABLE`、不支持记录为 `UNSUPPORTED`。
- 错误摘要与返回摘要均做截断，失败消息脱敏后再持久化。

## 验证结果

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests" --no-daemon`：通过。
- 本地后端重启后已执行 `0008-credential-connectivity-probe`，健康检查为 UP。

## 遗留问题

- 未自动执行真实上游 live probe，避免消耗真实凭证额度。
