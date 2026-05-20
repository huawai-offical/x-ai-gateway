# Codex 功能性服务 API 事实源

关联需求：[REQ-20260519-002](requirements/REQ-20260519-002-codex-priority-functional-service-api.md)  
关联任务：[TASK-20260519-002-01](../tasks/done/TASK-20260519-002-01-codex-functional-truth-source-priority.md)

## 当前结论

Codex 不是通用 provider catalog preset，不作为 OpenAI、Anthropic、Gemini 或 Vertex 的官方 API 全量替代。当前产品面只把 Codex 作为 ChatGPT 官方账号的 Responses smoke/反代边界，按 OpenAI 标准功能区校验 Responses request body、streaming、reasoning effort、usage budget guard、dry-run、record/replay 与脱敏。

## 支持面

| 能力 | 当前口径 | 事实源 |
| --- | --- | --- |
| OpenAI-compatible client 接入 | Codex CLI 使用 `/v1` OpenAI-compatible base URL 和 Distributed Key | `PublicDocsBundleService`、`docs/public-api-compatibility.md` |
| Codex App API smoke | 仅调用 `/backend-api/codex/responses` | `NativeCompatibilityService`、`CodexResponsesSmokeHttpClient` |
| Streaming | 只在 Responses 标准区内校验 | `NativeCompatibilityService` |
| Reasoning effort | 作为 Responses smoke 参数保留 | `NativeCompatibilityService`、`CodexResponsesSmokeHttpClient` |
| Usage budget guard | 必须默认保护，不能绕过预算 | `OfficialAccountAdminService`、`CodexResponsesSmokeHttpClient` |
| Record/replay | Codex Responses smoke 输出版本化 `recordReplayFixture`，默认 network/billable/write 都是 replay-only | `OfficialAccountAdminService`、`CodexResponsesSmokeRecordReplayFixtureVerifier`、[TASK-20260519-002-02](../tasks/done/TASK-20260519-002-02-codex-smoke-record-replay-priority.md) |

## 明确非支持面

- 非 Responses 的 Codex 内部 API。
- Codex admin、session、内部 lifecycle API。
- Fine-tuning、Batches、Evals、Admin 等非核心官方 API。
- 将 Codex 注册为通用 provider catalog preset。
- 使用 Codex 官方账号能力承诺 OpenAI Direct 全量 API parity。

## 后续执行清单

- Public docs bundle 中 Codex 说明必须独立于 Anthropic/Gemini/Vertex 的 embeddings/files 支撑面。
- Native compatibility matrix 必须保留 `SMOKE_ONLY`，不能升级为通用 `SUPPORTED`。
- Conformance 标识使用 `codex.responses-smoke-boundary`，表示边界检查，不表示线上真实 smoke 已通过。
- 后续测试恢复后，补充 `PublicDocsBundleServiceTests` 与 `NativeCompatibilityServiceTests` 对上述边界的断言。
