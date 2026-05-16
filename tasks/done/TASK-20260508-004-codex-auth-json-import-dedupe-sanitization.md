# TASK-20260508-004 Codex auth.json 导入去重与脱敏加固

状态：Done  
优先级：High  
排期：P0-01  
来源：User Request / REQ-20260508-002  
关联需求：[REQ-20260508-002 Codex 导入去重、可信前端与审计追踪闭环](../../docs/requirements/REQ-20260508-002-codex-import-dedupe-audit-closure.md)

## 背景

同一 Codex 账号重复登录可能产生结构和 token 都不同的 `auth.json`。导入入口必须用稳定身份判断重复，同时不能把 raw token 写入 metadata 或审计字段。

## 目标

- Codex 导入按 canonical identity 幂等更新。
- 通用导入和官方导入都递归脱敏 metadata、header snapshot、last refresh result。
- 补充回归测试覆盖强身份去重、弱身份不合并、敏感字段不落库。

## 范围

- `AccountAdminService.importAuthJson`
- `OfficialAccountAdminService.importOfficialAccount`
- `UpstreamAccountRepository` 查询复用
- 后端单元测试

## 非目标

- 不执行真实 live smoke。
- 不清理历史重复账号数据。

## 验收标准

- 同一 Codex subject/email/account identity 的再次导入更新旧账号。
- `WEAK_TOKEN` 身份不会因为 token fingerprint 合并到已有账号。
- metadata/result/header 中不出现 access token、refresh token、Bearer、cookie 或 secret 明文。

## 实现记录

- 新增 `SensitiveJsonSanitizer`，将导入 metadata、header snapshot、last refresh result 写入前统一脱敏。
- `AccountAdminService.importAuthJson` 对 `CODEX_OAUTH` pool 解析 `auth.json`，强身份使用 canonical `identityKey` 去重，历史 `account_id` 与 metadata identity 兼容。
- `OfficialAccountAdminService.importOfficialAccount` 同步支持 canonical identity 幂等更新，避免官方导入和通用导入产生重复 Codex 账号。
- 弱身份 `WEAK_TOKEN` 不进行全账号 metadata 扫描，避免不同账号仅因 token 指纹路径误合并。

## 测试/验证

- 后端 `AccountAdminServiceTests` 覆盖强身份更新旧账号、弱身份不合并、metadata/header 不泄漏 token。
- 后端 `OfficialAccountAdminServiceTests` 覆盖官方 Codex 导入按 canonical identity 更新旧账号。
- 定向后端测试通过。

## 遗留问题

- 不自动清理历史重复账号；如需合并历史数据，应另开带人工确认的数据迁移任务。
