# Linux/systemd 部署与数据迁移兼容

关联需求：[REQ-20260506-020 Linux/systemd 部署、数据管理与迁移兼容](requirements/REQ-20260506-020-linux-systemd-data-migration.md)  
关联任务：[TASK-20260506-024 Linux/systemd 部署、数据管理与迁移兼容](../tasks/done/TASK-20260506-024-linux-systemd-data-migration.md)

## Linux 部署入口

| 文件 | 用途 |
| --- | --- |
| `scripts/linux/install.sh` | 安装 release、创建目录、写入 systemd unit、启动服务。 |
| `scripts/linux/upgrade.sh` | 发布新 release、切换 current symlink、健康检查。 |
| `scripts/linux/rollback.sh` | 回滚到指定 release 或 `previous-release`。 |
| `scripts/linux/x-ai-gateway.env.example` | 生产环境变量模板。 |
| `deploy/systemd/x-ai-gateway.service` | systemd unit。 |

脚本均支持 `--dry-run`，用于在生产执行前预览动作。

## 数据管理入口

`scripts/data-management.mjs` 支持：

```powershell
node scripts/data-management.mjs migrate --source one-api --input docs/migrations/samples/one-api-export.sample.json --dry-run
node scripts/data-management.mjs migrate --source sub2api --input docs/migrations/samples/sub2api-export.sample.json --dry-run
node scripts/data-management.mjs export-template --output export-template.json
```

## 迁移映射

| 来源 | x-ai-gateway 目标 |
| --- | --- |
| One API user / Sub2API account | Gateway user 或外部用户映射记录。 |
| token / api_key | DistributedKey，明文 key 不落地，导入前先脱敏校验。 |
| channel / provider | UpstreamCredential 或 Provider site profile。 |
| quota / balance | 用户余额或订阅权益。 |
| logs / usage | `usage_record` dry-run 映射，真实导入需二次确认。 |

## 安全边界

- dry-run 默认只输出映射报告，不写数据库。
- 样例 secret 会脱敏为 `sk-x...tail`。
- 不迁移真实生产数据，除非另有授权和备份。
- 第三方扩展字段进入 `warnings`，不做无损承诺。
