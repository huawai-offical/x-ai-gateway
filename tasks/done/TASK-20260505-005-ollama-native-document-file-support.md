# TASK-20260505-005 Ollama Native document/file 真支持

状态：Done
优先级：High
来源：X-263 代码态审计
关联报告：[REP-20260505 X-263 代码态审计](../../docs/reports/REP-20260505-x263-code-state-audit.md)
关联任务：[TASK-20260505-003](../done/TASK-20260505-003-linear-x263-second-gap-overview.md)
关联推进需求：[REQ-20260506-001](../../docs/requirements/REQ-20260506-001-seventh-priority-task-closure-design.md)

## 背景

Ollama Native runtime 已有 image、usage 和 error parity 的测试锚点，但 document/file 输入仍需要明确真实支持策略：是转换为文本上下文、绑定外部文件引用，还是返回标准化不支持错误。

## 目标

将 Ollama Native document/file 从“部分支持或清晰拒绝”推进到可配置、可测试、对用户可解释的真支持链路。

## 范围

- 梳理 Ollama 本地模型对 document/file 的实际能力边界。
- 对文本类文档提供可控提取与 prompt 注入策略。
- 对二进制文件或不支持类型返回标准化错误。
- 补 usage、error、request log 与 native compatibility matrix 测试。

## 非目标

- 不实现完整文件 OCR 或复杂文档解析平台。
- 不绕过 provider 能力矩阵强行发送不可支持输入。

## 风险

- 不同本地模型对文件语义差异大，必须通过 capability matrix 控制启用范围。

## 验收标准

- document/file 输入路径有明确 capability gate。
- 文本类文档至少有一个本地 mock runtime 测试。
- 不支持类型的错误码、message 和 request log 可验证。

## 本批推进记录

- 2026-05-06：进入第七批高优先级任务闭环，目标是对文本类 document/file 做本地内容注入，对不支持类型返回标准化错误。

## 实现结果

- `OllamaGatewayChatRuntime` 支持 `gateway://` 与 `data:` 文本类 file/document。
- 文本文件通过 `GatewayFileService.getFileContent` 读取后按 UTF-8 注入 Ollama prompt，并保留文件名和 MIME 边界。
- 增加文本 MIME/扩展名 gate，支持 `text/*`、JSON、XML、YAML、CSV、Markdown、JavaScript、NDJSON 等文本类输入。
- 对远程 file URL 返回 `OLLAMA_UNSUPPORTED_FILE_INPUT`，对 PDF/二进制等不支持 MIME 返回 `OLLAMA_UNSUPPORTED_FILE_TYPE`。
- 新增本地说明文档：[Ollama Native 文本 document/file 支持](../../docs/ollama-document-file-support.md)。

## 测试/验证情况

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.MaintenanceRunServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.PlatformChangePlanServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OllamaGatewayChatRuntimeTests"
```

结果：通过。

## 遗留问题

- 不支持 OCR、PDF 解析、Office 文档解析或多文件检索。
- 文本注入有 60000 字符上限，超长文件需要后续引入摘要、分块或检索策略。

## 后续建议

- 如需 PDF/Office 真支持，建议新增独立任务，引入可控解析器和内容安全策略。
