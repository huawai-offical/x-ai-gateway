# Ollama Native 文本 document/file 支持

状态：Active
创建日期：2026-05-06
关联需求：[REQ-20260506-001 第七批高优先级任务闭环设计](requirements/REQ-20260506-001-seventh-priority-task-closure-design.md)
关联任务：[TASK-20260505-005 Ollama Native document/file 真支持](../tasks/done/TASK-20260505-005-ollama-native-document-file-support.md)

## 背景

Ollama `/api/chat` 原生请求支持文本、图片和工具，但没有统一的通用文件对象协议。为避免把不可理解的二进制文件强行透传，本项目采用“文本提取后注入 prompt”的保守支持策略。

## 支持策略

- 支持 `gateway://` 文本文件：通过 `GatewayFileService.getFileContent` 读取本地文件内容。
- 支持 `data:` 文本文件：解析 data URL，并按 UTF-8 注入。
- 支持文本类 MIME，包括 `text/*`、JSON、XML、YAML、CSV、Markdown、JavaScript、NDJSON。
- 当 MIME 不精确时，允许通过常见文本扩展名兜底，例如 `.txt`、`.md`、`.json`、`.csv`、`.xml`、`.yaml`、`.log`。
- 注入格式包含文件名和 MIME 边界，便于模型区分用户文本与文件内容。
- 单个文本文件最多注入 60000 个字符，超出部分截断并写入截断提示。

## 拒绝策略

- 远程 URL file/document 返回 `OLLAMA_UNSUPPORTED_FILE_INPUT`。
- PDF、图片以外的二进制文件、未知二进制 MIME 返回 `OLLAMA_UNSUPPORTED_FILE_TYPE`。
- 本轮不实现 OCR、PDF 解析、Office 文档解析或复杂多文件检索。

## 本地复现

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.execution.OllamaGatewayChatRuntimeTests"
```

## 验收证据

- `OllamaGatewayChatRuntimeTests.shouldInjectTextDocumentInputForOllama` 验证 `gateway://` Markdown 文件被读取并注入 Ollama request body。
- `OllamaGatewayChatRuntimeTests.shouldRejectBinaryDocumentInputForOllama` 验证 PDF 文件返回标准化不支持错误码。
- 既有图片、reasoning、tool call、stream usage 测试保持通过。
