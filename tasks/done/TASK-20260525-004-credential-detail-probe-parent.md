# TASK-20260525-004 上游凭证详情编辑合并与可用性探测父任务

## 类型

父任务 / task spec

## 背景

用户希望已录入上游凭证可直接点击查看完整详情，详情界面与编辑界面合并，并能通过简单 prompt 主动测试上游联通性，把凭证可用性记录下来。

## 目标

- 打通上游凭证“列表 -> 详情/编辑合并弹窗”的主路径。
- 展示凭证基础详情、绑定详情、用量摘要、健康状态和最近联通性探测结果。
- 后端提供并持久化凭证联通性测试能力。
- 前端提供测试按钮和结果反馈。

## 非目标

- 不新增独立凭证详情页面。
- 不展示密钥明文。
- 不实现定时巡检或批量探测。
- 不覆盖非 chat 类所有功能性 API 的 smoke。

## 上游来源

- `docs/requirements/REQ-20260525-004-credential-detail-probe.md`

## 子任务

- `TASK-20260525-004-01-credential-detail-backend-probe.md`
- `TASK-20260525-004-02-credential-detail-frontend-dialog.md`

## 输入

- `web/src/features/credentials/`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/entity/UpstreamCredentialEntity.java`
- `src/main/resources/db/changelog/`

## 输出

- 凭证详情/编辑合并弹窗。
- 凭证联通性测试 API 与持久化状态。
- 后端与前端定向测试。
- 文档与任务回写。

## 影响范围

- Admin 控制台上游凭证页面。
- Admin 凭证 API。
- 上游凭证数据表。

## 依赖

- 现有凭证解密服务。
- 现有 provider type、base URL、protocol endpoint 和模型能力数据。
- 现有前端 API 请求与 toast 反馈能力。

## 风险

- 上游真实请求产生少量 token 消耗。
- 缺少模型或协议入口可能导致探测无法代表全部功能能力。
- 工作区已有大量前序改动，本任务必须避免回滚无关变更。

## 验收标准

- 点击已录入凭证可打开详情/编辑合并弹窗。
- 弹窗展示详情、用量摘要与最近探测结果。
- 联通性测试按钮可触发后端探测并刷新状态。
- 探测成功/失败均持久化记录。
- 定向后端/前端测试和 typecheck 通过。

## 测试边界

- 后端定向测试覆盖探测状态记录和详情响应。
- 前端定向测试覆盖弹窗、编辑、探测按钮和结果展示。
- 不默认执行真实上游 live smoke，除非用户明确要求或已有低风险测试凭证。

## 关联文档

- `docs/requirements/REQ-20260525-004-credential-detail-probe.md`

## 当前状态

Done

## 实现结果

- 完成上游凭证列表到详情/编辑合并弹窗的主路径。
- 完成凭证最近联通性探测 API 与持久化字段。
- 完成凭证响应与 inventory 响应的探测字段透出。
- 完成前端联通性测试按钮、反馈提示和数据刷新。

## 验证结果

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests" --no-daemon`：通过。
- `cd web; bun run typecheck`：通过。
- `cd web; bun run test -- credentials-page.test.tsx`：通过。
- 本地后端已重启并执行 `db.changelog-0008-credential-connectivity-probe.yaml`；前端仍运行在 `http://localhost:5173/`。

## 遗留问题与下一步

- 真实上游联通性测试按钮未自动点击，避免消耗真实 key 额度；用户可登录控制台后手工触发。
- 后续可把“详情即编辑”的界面模式推广到账号组、厂商协议入口、访问组等详情页。
