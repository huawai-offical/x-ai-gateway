# REQ-20260506-013 Provider 真实 Smoke 与价格同步闭环

状态：Done  
日期：2026-05-06  
关联任务：

- [TASK-20260506-017 Provider 真实凭证 Smoke 与价格同步自动化](../../tasks/done/TASK-20260506-017-provider-smoke-pricing-sync.md)

## 背景

Provider Catalog 已扩展到 18 个 preset，并补齐 metadata、capability matrix 与 conformance fixture。用户已提供专门用于测试的 Google AI Studio API key，但当前仓库还缺“真实 provider smoke 可选执行、凭证缺失可跳过、报告脱敏、pricing metadata 可校验”的闭环。

本轮从 `TASK-20260506-017` 开始推进，优先使用 Google AI Studio/Gemini 作为第一批真实 smoke 对象；其他 provider 在没有真实 key 时必须以 `SKIPPED` 状态记录，不阻塞任务闭环。

## 目标

- 建立 Gemini-first 的真实 provider smoke harness。
- 支持多测试 key 轮询/逐个验证，但不在仓库内保存真实 key。
- 凭证缺失、权限失败、限流、网络失败和 provider 响应异常必须分类清楚。
- 建立 provider catalog pricing metadata 的快照或一致性校验，减少后续漂移。
- 将 smoke 结果输出为脱敏报告。

## 范围

- `XAG_SMOKE_GEMINI` 开关和 `XAG_SMOKE_GEMINI_API_KEYS`/`GEMINI_API_KEY` 环境变量读取。
- Gemini `models.list` 和最小 `generateContent` smoke。
- Smoke 报告中仅输出 masked key、模型名、状态、错误分类和脱敏响应摘要。
- Provider Catalog、Public Docs、Conformance fixture 和 pricing metadata 的一致性测试。
- 本地 docs/tasks 回写。

## 非目标

- 不把测试 key 写入源码、文档、任务、配置、日志或测试快照。
- 不对免费 key 做压测、循环消耗或大 token 请求。
- 不要求本轮覆盖所有 provider 的真实 key。
- 不把缺少 key 判定为测试失败。

## 方案

1. 将 `TASK-017` 迁移到 `tasks/in-progress/`。
2. 新增或扩展 smoke harness，按环境变量读取 Gemini key。
3. 请求默认短 prompt、低 token、低频执行。
4. 对其他 provider 先建立 credential optional 的报告格式。
5. 新增 pricing metadata/catalog/docs/fixture 一致性测试。
6. 完成后回写任务和需求，归档任务。

## 风险

- 免费 key 有 RPM/RPD 限制，必须避免批量消耗。
- 用户提供的测试 key 仍属于敏感凭证，不能出现在任何仓库文件和报告中。
- Google Gemini API 版本和模型列表会变化，因此 smoke 应优先通过 `models.list` 自动选择支持 `generateContent` 的模型。
- 网络或区域问题可能导致真实 smoke 失败，需要与 gateway 逻辑错误区分。

## 验收标准

- 无 key 时 smoke 被 JUnit assumption skip，不影响普通测试。
- 有 Gemini key 时可执行 `models.list` 和最小 `generateContent`，并输出脱敏报告。
- 多 key 输入时报告逐个 key 的 masked 状态，不输出明文。
- pricing metadata、provider catalog、public docs 和 conformance fixture 有一致性测试。
- 任务和需求文档记录实现结果、验证情况、遗留问题和后续建议。

## 实现结果

- 新增 Gemini/Google AI Studio 真实 provider smoke harness：`GeminiProviderSmokeHarnessTests`。
- Smoke harness 默认跳过，只有设置 `XAG_SMOKE_GEMINI=true` 且提供 `XAG_SMOKE_GEMINI_API_KEYS` 或 `GEMINI_API_KEY` 时才执行真实网络请求。
- Smoke 会先调用 `models.list`，再选择支持 `generateContent` 的 model 执行短 prompt。
- Smoke 报告只输出测试 key 的 `sha256` 前缀引用、模型名、状态分类和脱敏响应摘要。
- `SmokeHarnessSupport` 增加环境变量列表解析和 secret fingerprint 工具。
- `ProviderCatalogLoaderTests` 增加 pricing metadata、public docs bundle 与 conformance fixture 一致性校验。
- 新增 [Provider Smoke 与 Pricing Sync](../provider-smoke-pricing-sync.md) 本地执行文档。

## 测试/验证

已通过：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiProviderSmokeHarnessTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests"
```

验证结果：

- `ProviderCatalogLoaderTests` 通过，覆盖新增一致性校验。
- `GeminiProviderSmokeHarnessTests` 在未设置 smoke 环境变量时按预期 skip。
- `PublicDocsBundleServiceTests` 通过，确认公开 docs bundle 仍可暴露 provider preset 和 OpenAPI 信息。

## 遗留问题

- 本轮没有把用户提供的测试 key 写入命令、文件或报告，因此未在当前工具进程中执行真实远程 smoke。需要在本机 PowerShell 中按 `docs/provider-smoke-pricing-sync.md` 设置环境变量后执行。
- 其他 provider 的真实 smoke 仍需后续在具备对应测试 key 后扩展。
