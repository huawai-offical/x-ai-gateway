# 生产部署与升级体系

## 部署入口

- 配置样例：`deploy/.env.example`
- Compose 编排：`deploy/docker-compose.yml`
- 首次部署脚本：`scripts/install.ps1`
- 升级脚本：`scripts/upgrade.ps1`
- 回滚脚本：`scripts/rollback.ps1`

## 首次部署

```powershell
Copy-Item deploy/.env.example .env
notepad .env
.\scripts\install.ps1 -EnvFile .env
```

部署后检查：

```powershell
docker compose --env-file .env -f deploy/docker-compose.yml ps
curl http://localhost:8080/actuator/health/readiness
```

## 升级预检

管理端提供可机器读取的预检：

```text
GET /admin/operations/deployment/manifest
GET /admin/operations/deployment/preflight?targetVersion=2026.05.06
```

预检中的 `blockingCount` 大于 0 时禁止升级。`warningCount` 大于 0 时可在本地或灰度环境继续演练，但生产升级前需要确认风险。

## 升级

```powershell
.\gradlew.bat clean test
.\scripts\upgrade.ps1 -TargetVersion 2026.05.06 -Confirm
```

升级前需要确保已有备份或 recovery checkpoint。应用启动后 Liquibase 会按 `src/main/resources/db/changelog/db.changelog-master.yaml` 执行迁移。

## 回滚

```powershell
.\scripts\rollback.ps1 -BackupId <pre-upgrade-backup-id> -PreviousImage x-ai-gateway:<previous-version> -Confirm
```

回滚只切回应用镜像并提示恢复快照；数据库反向迁移仍应以升级前备份和管理端 recovery checkpoint 为准，避免破坏账务和审计可追溯性。

## 生产配置注意事项

- `GATEWAY_ENCRYPTION_KEY` 必须替换为生产密钥，且不能提交到仓库。
- 多实例部署建议设置 `GATEWAY_ROUTING_RUNTIME_STORE_TYPE=redis` 且关闭 `GATEWAY_ROUTING_RUNTIME_STORE_FALLBACK_TO_MEMORY`。
- Postgres、Redis、文件目录和日志目录都需要持久化卷。
- 管理员密码、支付 webhook secret、OAuth client secret 均应进入 secret manager。
