# REQ-20260522-004 .gitignore 通配符化整理

## 背景

当前 `.gitignore` 混入了大量本地缓存和依赖目录下的具体文件路径，尤其是 `.gradle-user-home`、`.gradle-user-home-installed` 与 `web/node_modules` 相关条目。逐文件忽略会导致文件体积膨胀、后续依赖更新时持续产生噪声，也不利于人工审查。

## 目标

- 将 `.gitignore` 中可归并的 Gradle、IDE、前端依赖、构建产物和本地缓存条目改为目录级或通配符规则。
- 保留必要的项目例外规则，例如 `gradle/wrapper/gradle-wrapper.jar`。
- 通过 `git check-ignore` 验证典型缓存、依赖和构建产物仍被忽略。

## 非目标

- 不清理磁盘上的 `.gradle-user-home`、`.gradle-user-home-installed`、`web/node_modules` 或其他本地缓存目录。
- 不修改 Java、前端、数据库迁移或测试代码。
- 不处理当前工作区中与 `.gitignore` 无关的既有改动。

## 方案

- 用较短的分组规则覆盖 Java/Gradle、IDE、Node/Vite、临时文件和环境变量文件。
- 对本地 Gradle 用户目录和前端依赖目录使用目录级忽略。
- 对 `*.tmp`、`*.tsbuildinfo` 等可跨目录复用的产物使用通配符；仓库已有跟踪日志，暂不全局忽略 `*.log`。

## 范围

- `.gitignore`
- `docs/requirements/REQ-20260522-004-gitignore-wildcard-cleanup.md`
- `tasks/done/TASK-20260522-006-gitignore-wildcard-cleanup.md`
- `docs/index.md`
- `tasks/index.md`

## 风险

- 如果规则过宽，可能会隐藏本应跟踪的文件；需要用 `git ls-files -ci --exclude-standard` 检查已跟踪文件是否被新规则误伤。
- 如果规则过窄，仍会产生大量本地缓存未跟踪噪声；需要用典型路径验证 ignore 命中。

## 验收标准

- `.gitignore` 不再列出大量 `web/node_modules` 与 Gradle cache 的具体文件路径。
- 典型路径 `web/node_modules/...`、`.gradle-user-home/...`、`.gradle-user-home-installed/...`、`build/...`、`.idea/...` 可被 `git check-ignore -v` 命中。
- `git ls-files -ci --exclude-standard` 不报告被误忽略的已跟踪文件。
- 本需求与对应任务在本地索引中可追踪。

## 实现结果

- 已将 `.gitignore` 从逐文件缓存清单整理为 74 行分组规则。
- 已用目录级规则覆盖 `.gradle/`、`.gradle-user-home/`、`.gradle-user-home-installed/`、`**/node_modules/`、`web/dist/`、`.idea/` 等本地产物和工具目录。
- 已保留 `gradle/wrapper/gradle-wrapper.jar`、`src/main|test` 下 `build/bin/out` 例外，以及 `.env.example` 类模板例外。
- 已避免全局忽略 `*.log` 和 `output/`，防止误伤仓库已跟踪的审计输出文件。

## 验证情况

- 已执行 `git check-ignore -v .gradle-user-home\caches\9.4.1\file-changes\last-build.bin .gradle-user-home-installed\native\jansi\1.18\windows64\jansi.dll web\node_modules\commander\package.json web\node_modules\.vite\deps\react.js build\classes .gradle\caches .idea\workspace.xml web\dist\assets\index.js`，典型缓存、依赖、IDE 和构建产物均命中新规则。
- 已执行 `git ls-files -ci --exclude-standard`，无输出，确认没有误忽略已跟踪文件。
- 已执行 `git status --short --untracked-files=all | Select-String -Pattern "node_modules|gradle-user-home|web/dist|\.gradle|\.idea"`，无输出，确认典型本地噪声未继续暴露。

## 遗留问题与后续建议

- 本轮不清理磁盘缓存；如后续要释放空间，可另开任务处理实际目录删除。
