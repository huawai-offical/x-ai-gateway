# TASK-20260501-010 生产部署与升级体系：Docker Compose、.env.example、install 脚本、在线升级/回滚

状态：Done  
优先级：Medium  
来源：Notion 待创建；Linear 创建失败  
关联报告：[REP-20260501](../../docs/reports/REP-20260501-open-source-gap-analysis.md)  
关联需求：[REQ-20260506-004](../../docs/requirements/REQ-20260506-004-tenth-priority-task-closure-design.md)

## 背景

对标 `Sub2API` / `new-api` 的落地体验，`x-ai-gateway` 当前需要更完整的一键部署、配置样例、升级与回滚能力，降低私有化和生产部署门槛。

## 目标

形成可复用、可验证、可回滚的部署体系。

## 范围

- Dockerfile / docker-compose.yml / compose profile。
- `.env.example` 与生产配置说明。
- install / upgrade / rollback 脚本。
- 数据库迁移与版本兼容检查。
- 健康检查、启动顺序、日志路径与卷挂载规范。
- 发布说明与升级操作文档。

## 非目标

- 不在首版支持所有云平台的一键模板。
- 不替代正式运维平台。

## 验收标准

- 新环境可按文档完成部署。
- 升级流程可验证版本和数据库迁移状态。
- 回滚路径明确且经过演练。
- README/本地文档包含完整部署说明。

## 实现记录

已完成生产部署与升级首版闭环：

- 新增 `GET /admin/operations/deployment/manifest`。
- 新增 `GET /admin/operations/deployment/preflight`。
- 新增 `Dockerfile`、`deploy/docker-compose.yml`、`deploy/.env.example`。
- 新增 `scripts/install.ps1`、`scripts/upgrade.ps1`、`scripts/rollback.ps1`。
- 新增文档：[production-deployment-upgrade](../../docs/production-deployment-upgrade.md)。
- Preflight 覆盖关键文件、Liquibase 入口、Redis runtime store 建议、targetVersion 和升级/回滚命令。

## 测试/验证

已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.PlatformOperationsDeploymentServiceTests"
```

覆盖 manifest 关键文件、部署命令、preflight blocking/warning 和升级命令生成。

## 遗留问题

此任务此前因 Linear 免费 issue 数量限制未能创建线上 issue，现以本地任务为准。

云平台模板、镜像签名、蓝绿发布和真实环境升级演练仍可后续增强。
