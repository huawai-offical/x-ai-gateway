# TASK-20260522-006 .gitignore 通配符化整理

## 当前状态

Done

## 任务类型

父任务，内含 1 个可独立验收的子任务。

## 背景

`.gitignore` 当前包含大量具体缓存文件路径，导致文件体积过大且容易随着 Gradle、Bun/Vite、Node 依赖更新继续膨胀。本任务承接 [REQ-20260522-004](../../docs/requirements/REQ-20260522-004-gitignore-wildcard-cleanup.md)，仅整理 ignore 规则与本地追踪文档。

## 目标

- 将 `.gitignore` 整理为分组清晰、可维护的目录级与通配符规则。
- 保留必要例外，避免误忽略项目应跟踪文件。
- 完成后回写需求、任务和索引状态。

## 非目标

- 不删除本地缓存或依赖目录。
- 不修改业务代码、前端功能、数据库 changelog 或测试用例。
- 不接管当前工作区中其他未完成改动。

## 上游来源

- 用户请求：整理 `.gitignore` 文件，改用通配符。
- 需求文档：`docs/requirements/REQ-20260522-004-gitignore-wildcard-cleanup.md`

## 输入

- 当前 `.gitignore`
- 当前仓库本地 docs/tasks 工作流约定

## 输出

- 精简后的 `.gitignore`
- 本任务归档到 `tasks/done/`
- `docs/index.md` 与 `tasks/index.md` 追加追踪入口

## 影响范围

- `.gitignore`
- `docs/requirements/REQ-20260522-004-gitignore-wildcard-cleanup.md`
- `tasks/done/TASK-20260522-006-gitignore-wildcard-cleanup.md`
- `docs/index.md`
- `tasks/index.md`

## 依赖

- Git ignore 规则语义。
- 当前仓库已有 Gradle 与 web 前端目录结构。

## 风险

- 规则过宽可能误忽略已跟踪文件。
- 规则过窄可能无法覆盖本地缓存与依赖噪声。

## 验收标准

- `.gitignore` 采用通配符或目录级规则，不再保留大量逐文件缓存清单。
- `git check-ignore -v` 可验证典型缓存和依赖路径。
- `git ls-files -ci --exclude-standard` 不报告误忽略的已跟踪文件。
- 需求与任务状态完成回写。

## 测试边界

- 只执行 Git ignore 行为验证，不运行 Java/前端测试。
- 不做构建产物清理。

## 关联文档

- `docs/requirements/REQ-20260522-004-gitignore-wildcard-cleanup.md`

## 关联任务

- 父任务：`TASK-20260522-006`
- 子任务：`TASK-20260522-006-01`

## 子任务 TASK-20260522-006-01 .gitignore 规则归并与验证

### 当前状态

Done

### 背景

Gradle 本地用户目录、安装缓存、Node 依赖和 Vite 缓存应由通配符或目录规则覆盖，避免逐文件追加。

### 目标

- 将具体缓存文件路径归并为 `.gradle-user-home/`、`.gradle-user-home-installed/`、`**/node_modules/` 等规则。
- 保留 `gradle/wrapper/gradle-wrapper.jar` 例外。
- 验证典型路径命中 ignore 规则。

### 非目标

- 不删除实际缓存。
- 不修改代码。

### 输入

- `.gitignore`

### 输出

- 精简后的 `.gitignore`
- 验证命令结果

### 影响范围

- `.gitignore`

### 依赖

- Git ignore 规则。

### 风险

- 忽略规则误伤 tracked 文件。

### 验收标准

- 典型缓存路径被 `git check-ignore -v` 命中。
- `git ls-files -ci --exclude-standard` 无输出。

### 测试边界

- 不运行代码测试，仅验证 Git ignore 行为。

## 实现结果

- 已将 `.gitignore` 从大规模逐文件缓存清单压缩为 74 行分组规则。
- 已用目录级和通配符规则覆盖 Gradle、本地 Gradle 用户目录、IDE、Node/Bun/Vite、构建产物、临时文件与环境变量模板。
- 已保留 Gradle wrapper jar、源码目录下 `build/bin/out` 和 `.env.example` 类模板例外。
- 已避免全局忽略 `*.log` 和 `output/`，防止误伤仓库已有跟踪日志。

## 验证情况

- `git check-ignore -v .gradle-user-home\caches\9.4.1\file-changes\last-build.bin .gradle-user-home-installed\native\jansi\1.18\windows64\jansi.dll web\node_modules\commander\package.json web\node_modules\.vite\deps\react.js build\classes .gradle\caches .idea\workspace.xml web\dist\assets\index.js`：典型路径均命中新规则。
- `git ls-files -ci --exclude-standard`：无输出，确认没有误忽略已跟踪文件。
- `git status --short --untracked-files=all | Select-String -Pattern "node_modules|gradle-user-home|web/dist|\.gradle|\.idea"`：无输出，确认典型本地噪声未继续暴露。

## 遗留问题与后续建议

- 未清理磁盘上的缓存目录；如需要释放空间，应另开清理任务。
