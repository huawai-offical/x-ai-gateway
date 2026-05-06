# LINEAR-20260505 x-ai-gateway issue 全量归档

状态：Done
日期：2026-05-05
来源项目：`x-ai-gateway V1 多协议 AI 网关`
项目 ID：`a265a53a-e605-46ae-ab98-6f68268b951e`
团队：`X-ai`
关联迁移记录：[MIG-20260505](MIG-20260505-notion-linear-back-migration.md)

## 归档说明

本文件用于满足“Linear 上即使已经完结的任务也需要回迁到本地”的要求。2026-05-05 通过 Linear 项目级分页读取当前可见 issue，`hasNextPage=false` 后停止。

本次共回迁 `180` 条 issue：

- `Done / completed`：164 条
- `Duplicate / canceled`：15 条
- `Backlog / backlog`：1 条
- 未发现 `archivedAt` 非空的 issue

线上 URL 可通过 `https://linear.app/x-ai/issue/{ID}` 访问或在 Linear 中按 ID 搜索。后续事实来源以本地归档和本地任务为准。

## 全量 issue 清单

| ID | 状态 | 标题 |
| --- | --- | --- |
| X-5 | Done | 对齐技术基线到 Spring Boot 3.5.x、Java 21 与 Spring AI BOM |
| X-6 | Done | 初始化多层网关模块骨架与基础工程结构 |
| X-7 | Duplicate | 设计并实现统一请求响应 IR 与能力模型 |
| X-8 | Done | 实现 DistributedKey、UpstreamCredential 与 ModelMapping 数据模型 |
| X-9 | Done | 实现 Router、Key Pool、限流与 Redis 热缓存骨架 |
| X-10 | Done | 接入 OpenAI、Anthropic、Gemini provider adapter 与调用封装 |
| X-11 | Done | 实现 OpenAI、Anthropic、Gemini 三套入口协议与流式转译 |
| X-12 | Done | 补齐 usage 计量、审计观测、管理员 API 与集成测试 |
| X-13 | Done | 引入 LQBase 并建立 baseline / migration 规范 |
| X-14 | Done | 重构构建与运行基线到 Spring Boot 3.5.x + Java 21 |
| X-15 | Duplicate | 设计单体分层包结构与模块边界 |
| X-16 | Done | 建立 WebFlux 基础设施、错误码与 traceId 透传 |
| X-17 | Done | 定义统一 Chat / Embedding / Stream IR 与兼容策略 |
| X-18 | Done | 定义协议 DTO 到统一 IR 的映射与校验规范 |
| X-19 | Done | 设计核心表结构、ER 关系与索引策略 |
| X-20 | Done | 编写核心实体类并补齐完整 SQL 注解 |
| X-21 | Done | 实现 DistributedKey / Credential / ModelMapping 查询服务 |
| X-22 | Done | 实现候选路由打分、健康状态与 fallback 骨架 |
| X-23 | Done | 接入 Redis 鉴权缓存、路由缓存与限流存储 |
| X-24 | Done | 实现 OpenAI provider adapter |
| X-25 | Done | 实现 Anthropic provider adapter |
| X-26 | Done | 实现 Gemini provider adapter |
| X-27 | Done | 实现 OpenAI 协议入口与 SSE 流式输出 |
| X-28 | Done | 实现 Anthropic 协议入口与响应转译 |
| X-29 | Done | 实现 Gemini 协议入口与响应转译 |
| X-30 | Done | 实现 request / usage / audit 持久化结构与观测指标 |
| X-31 | Done | 实现管理员侧 credential、DistributedKey 与模型映射 API |
| X-32 | Done | 补齐集成测试、契约测试与运行文档 |
| X-33 | Duplicate | 对标本地 ai-gateway 提炼可迁移设计与差异清单 |
| X-34 | Duplicate | 对标开源网关形成功能边界与安全基线 |
| X-35 | Done | 设计 DistributedKey 维度的 prompt cache 能力、命中统计与结算模型 |
| X-36 | Done | 实现 Redis Lua 原子 key 粘性、前缀亲和与缓存路由计数器 |
| X-37 | Done | 设计 DistributedKey 级 Prompt 指纹、多级缓存与厂商缓存协同策略 |
| X-38 | Done | 实现厂商缓存收益对账、流式统计补全与缓存命中观测 |
| X-39 | Done | 按 ai-gateway 模块拆解 Java 包映射与职责边界 |
| X-40 | Done | 设计 DistributedKey、Credential、ModelAlias、Settings 的核心表与 SQL 注解清单 |
| X-41 | Done | 实现模型目录聚合、别名展开与候选解析服务 |
| X-42 | Done | 实现上游凭证管理 API、联通性测试与模型刷新 |
| X-43 | Done | 实现 DistributedKey 管理 API、轮换与协议/模型白名单 |
| X-44 | Done | 实现模型别名管理 API、规则预演与命中测试 |
| X-45 | Done | 实现 OpenAI prompt_cache_key 注入与 cached_tokens 归一化 |
| X-46 | Done | 实现系统配置与缓存策略管理 API |
| X-47 | Done | 实现 Anthropic cache_control 注入与 cache read/write 归一化 |
| X-48 | Done | 实现 Gemini cached_content 复用与 usage_metadata 归一化 |
| X-49 | Done | 实现 route decision、cache hit、upstream cache reference 三类日志表 |
| X-50 | Done | 补齐缓存亲和、路由解释与后台管理的集成测试 |
| X-51 | Done | 定义 Spring AI 可复用抽象与网关自建抽象的边界矩阵 |
| X-52 | Done | 实现 Spring AI Usage 到网关统一 Usage 的归一化层 |
| X-53 | Done | 实现网关 IR 到 Spring AI ChatOptions / ProviderOptions 的映射层 |
| X-54 | Done | 实现 Spring AI ChatResponse 到多协议响应编码器的分层隔离 |
| X-65 | Duplicate | 补齐 requestLog 详细指标在多协议 proxy 链路的全量覆盖 |
| X-137 | Done | x-ai-gateway：对标 ai-gateway + Sub2API 的平台增强路线图（去用户社区版） |
| X-138 | Duplicate | x-ai-gateway：建立 Provider 扩展与站点级 upstream |
| X-139 | Done | x-ai-gateway：补齐 OpenAI 兼容资源协议面（audio / images / moderations） |
| X-140 | Done | x-ai-gateway：补齐异步资源与会话对象生命周期 |
| X-141 | Done | x-ai-gateway：建立公开兼容预检、降级策略与兼容矩阵 |
| X-142 | Done | x-ai-gateway：建立多类型上游账号池与 OAuth / Refresh Token 接入 |
| X-143 | Done | x-ai-gateway：将 DistributedKey 升级为配额、预算与客户端策略对象 |
| X-144 | Done | x-ai-gateway：建立网络出站治理（代理池 / Probe / TLS 指纹） |
| X-145 | Done | x-ai-gateway：建立 Ops 实时指挥台（实时信号 / 告警 / 拨测 / 日志） |
| X-146 | Done | x-ai-gateway：建立错误透传与协议例外规则中心 |
| X-147 | Done | x-ai-gateway：建立备份恢复、安装向导与升级回滚控制面 |
| X-148 | Done | x-ai-gateway：初始化 web 前端工程（React + TypeScript + Bun） |
| X-152 | Done | x-ai-gateway：全厂商自动翻译闭环（主流厂商全覆盖） |
| X-154 | Done | x-ai-gateway：建立 Provider Registry 与站点能力快照 |
| X-155 | Done | x-ai-gateway：统一 Translation IR V2 与资源执行计划 |
| X-156 | Done | x-ai-gateway：补齐聊天自动翻译闭环（Ollama / reasoning / tools / sidecar） |
| X-157 | Done | x-ai-gateway：补齐 embeddings / moderations / audio / images 跨厂商执行层 |
| X-158 | Done | x-ai-gateway：把 uploads / files / batches / tuning / realtime 从本地占位升级为真实上游编排 |
| X-159 | Done | x-ai-gateway：建立 capability 真相源、损耗等级与选择策略闭环 |
| X-160 | Done | x-ai-gateway：接入主流厂商第一批站点档案与兼容策略 |
| X-161 | Done | x-ai-gateway：建立跨协议 × 上游 provider 一致性回归矩阵与 conformance harness |
| X-162 | Done | x-ai-gateway：补齐后台控制面与调试面（provider sites / capability refresh / translation explainability） |
| X-163 | Done | 清理疑似已实现但未关单的 Linear 任务 |
| X-164 | Done | 升级 Spring Boot 到 4.0.5 并调整本地 PostgreSQL 连接 |
| X-165 | Done | x-ai-gateway：清理本地 Markdown 并统一迁移到 Notion |
| X-166 | Done | x-ai-gateway：协议无感自动路由与全自动互转 V2 |
| X-167 | Done | x-ai-gateway：Phase A 删除 legacy chat 边界并完成 Canonical-only 外壳 |
| X-168 | Done | x-ai-gateway：Phase B 补齐 Anthropic/Gemini native runtime 与 Canonical cutover |
| X-169 | Done | x-ai-gateway：Phase C admin/debug/control 切透 Canonical 契约 |
| X-170 | Done | x-ai-gateway：Phase D 切透 Canonical planner/runtime 与结构化展示 |
| X-171 | Done | x-ai-gateway：Phase F 资源执行与 multipart 调试闭环 |
| X-172 | Done | x-ai-gateway：Phase G files/uploads/batches/tuning/realtime 统一到 resource executor |
| X-173 | Done | x-ai-gateway：Phase H request/route/cache 日志补 executionBackend/objectMode |
| X-174 | Done | x-ai-gateway：核心理念最终收官（全厂商全 API 统一语义模型与自动路由自动互转） |
| X-175 | Done | Phase A：收官判定基线、资源能力地图与验收口径冻结 |
| X-176 | Done | Phase B：建立 Canonical Resource Response / Event / Degradation 统一语义模型 |
| X-177 | Done | Phase C：对象型资源的 Canonical Lifecycle / Lineage 语义收敛 |
| X-178 | Done | Phase D：provider-native non-chat 资源适配与支持矩阵收敛 |
| X-179 | Done | Phase E：全 API 自动路由、自动互转与 render-degradation 闭环 |
| X-180 | Done | Phase F：Explain / Debug / Control / Observability 的全 API 语义化收官 |
| X-181 | Done | Phase G：跨厂商一致性矩阵验证、文档回写与最终关单 |
| X-182 | Done | X-178-A：VERTEX_AI provider-native non-chat 第一批实现与矩阵收敛 |
| X-183 | Done | X-178-B：GEMINI_DIRECT uploads / realtime 语义决策、blocked 策略与最小实现 |
| X-184 | Done | X-178-C：Google native non-chat ingress 到 IR 的扩面 |
| X-185 | Done | X-178-D：ANTHROPIC_DIRECT non-chat 能力矩阵、blocked 策略与最小实现 |
| X-186 | Done | X-178-E：OPENAI_DIRECT / OPENAI_COMPATIBLE non-chat 直连补漏与 parity audit |
| X-187 | Done | X-179-A：non-chat planner / route policy 语义化 |
| X-188 | Done | X-179-B：non-chat canonical renderer / auto-conversion 闭环 |
| X-189 | Done | X-179-C：degradation / blocker / fallback 规则与回归矩阵 |
| X-190 | Done | X-180-A：全 API explain / plan / admin execute 语义统一 |
| X-191 | Done | X-180-B：observability 联查与 canonical 复盘字段收口 |
| X-192 | Done | X-180-C：前端控制面与对象资源调试页收口 |
| X-193 | Done | X-181-A：OpenAI / Google / Anthropic endpoint-level conformance matrix |
| X-194 | Done | X-181-B：最终文档回写、证据归档与关单 |
| X-196 | Done | x-ai-gateway：web 控制台改造成左侧菜单 + 右侧工作区通用后台壳 |
| X-197 | Done | x-ai-gateway：web 侧栏二级菜单与滚动体验收口 |
| X-198 | Done | x-ai-gateway：下一阶段增强（强自治 AI 平台） |
| X-199 | Done | G1：自治治理内核 |
| X-200 | Done | G2：SLO / 成本 / 容量守护 |
| X-201 | Done | G3：变更与恢复编排 |
| X-202 | Done | G4：控制台 UX 重构 |
| X-203 | Done | G5：外部联动与事件出口 |
| X-212 | Done | 凭证一等入口与上游接入导航重构 |
| X-218 | Done | x-ai-gateway：前端 UI/UX 重设计与基建重构（2026-04-20） |
| X-219 | Done | x-ai-gateway：全自动翻译网关可视化呈现（2026-04-20） |
| X-221 | Done | x-ai-gateway：AIOps 智能运维与全链路观测（2026-04-20） |
| X-223 | Done | x-ai-gateway：控制台登录登出与 POW/数学验证码（2026-04-20） |
| X-224 | Done | x-ai-gateway：登录页收口 + Redis 热路径异步落库（2026-04-20） |
| X-225 | Done | x-ai-gateway 真接入 Prometheus 指标抓取链路 |
| X-226 | Done | x-ai-gateway：控制台 viewport 固定布局与 Gateway Live Feed 视口约束收口（2026-04-20） |
| X-227 | Done | x-ai-gateway：全站中文化与移除 Gateway Live Feed 全局浮窗（2026-04-21） |
| X-228 | Done | x-ai-gateway：后台界面间距与边界微调审查（2026-04-21） |
| X-229 | Done | x-ai-gateway：后台界面间距与边界微调实现（2026-04-21） |
| X-230 | Done | x-ai-gateway：修复告警中心 QuarantineStatus [object Object] 导致的运营视图加载失败 |
| X-231 | Done | x-ai-gateway：后台界面二次细调审查（2026-04-21） |
| X-232 | Done | x-ai-gateway：后台界面二次细调实现（2026-04-21） |
| X-233 | Done | x-ai-gateway：白盒调试工作台与变更编排二次微调（2026-04-21） |
| X-234 | Done | x-ai-gateway：增强站点档案与能力矩阵闭环（2026-04-21） |
| X-235 | Done | x-ai-gateway：修复新建站点加载并录入 Gemini 凭证与初始数据（2026-04-21） |
| X-240 | Done | x-ai-gateway：用户域最小闭环（Users/Plans/Subscriptions，第一阶段） |
| X-241 | Done | x-ai-gateway：有入口但实现偏浅/偏占位的功能闭环清仓 |
| X-242 | Done | x-ai-gateway vs ai-gateway 功能差距补齐总览 |
| X-243 | Done | 补齐用户门户最小闭环：登录/注册/订阅/Key/公告入口 |
| X-244 | Done | 补齐 Access Group 与套餐权益继承模型 |
| X-245 | Done | 补齐公告中心：后台发布、受众定向、门户已读 |
| X-246 | Done | 补齐兑换码、促销权益与用户余额流水 |
| X-247 | Done | 深化 OAuth / Session 账号：Provider 扩展元数据、刷新调度与配额窗口 |
| X-248 | Done | 补齐分发 Key 客户端配置导出与一次性 Secret 管理 |
| X-249 | Done | 补齐控制台外部应用扩展与 signed context 接入 |
| X-250 | Done | 强化维护控制面：真实预检/备份/恢复演练/升级检查/回滚计划 |
| X-251 | Done | 补齐 Native 协议命名空间兼容：Ollama/Anthropic/Google catch-all 路由 |
| X-252 | Done | 补齐 Realtime / Live Session 运行时：SSE/音频双工/会话恢复 |
| X-253 | Done | 补齐 Ops Probe Run 与系统事件时间线 |
| X-254 | Done | 补齐成本路由策略中心与成本模型可视化 |
| X-255 | Done | 深化 Realtime / Live Session：Provider Live WebSocket、音频双工与运行时指标接入 |
| X-256 | Done | 深化成本路由：接入真实 Route Selection、预算拒绝、用户余额与成本统计 |
| X-257 | Done | 深化 Uploads 资源生命周期：分片上传、合并 File、Lineage 与协议兼容 |
| X-258 | Done | 深化 Fine-tuning 资源生命周期：Gemini tuning、模型导入与 Alias 注册 |
| X-259 | Done | 补齐 Native catch-all 扩展：Google Upload、/upload/v1beta 与通用 /v1beta 兼容 |
| X-260 | Done | 深化 OAuth / Session 刷新调度：Provider 专属刷新、Header Snapshot 与失败冷却队列 |
| X-261 | Done | 补齐控制台扩展应用运行页：iframe 挂载、signed context 注入与导航展示 |
| X-262 | Done | 收口用户门户体验：独立注册、公告详情、Key 自助创建与兑换/余额页面 |
| X-263 | Backlog | x-ai-gateway vs ai-gateway 第二轮深度差距补齐总览 |
| X-264 | Done | 补齐 Gateway Cache 资源生命周期 API：/api/v1/caches、import 与运行时复用 |
| X-265 | Done | 补齐 Public Resource Lineage API：/api/v1/resources/{type}/{id}/lineage |
| X-266 | Done | 补齐 Google-style Operations / Tunings 公共端点与 tuned model 导入闭环 |
| X-267 | Done | 把 Ops Probe / Maintenance / Release Rollout 从模拟态推进到真实执行态 |
| X-268 | Done | 扩展 Admin Workbench：从 chat-only 执行到 responses/images/audio/files/batches/tuning/cache |
| X-269 | Done | 强化 Client Config Export 与一次性 Secret 托管：下载、轮换、撤销和审计 |
| X-270 | Done | 补齐 Ollama Native 多模态与模型能力清单兼容：file/document、usage/error parity |
| X-271 | Done | 修复全量测试阻断并建立 E2E 环境基线：Redis、operations-router 与 CI 文档 |
| X-272 | Done | 收口 Operations 子页面路由策略：backups/upgrades/rollbacks 独立页或正式合并 |
| X-273 | Done | 生产级修复 Secret Export：持久化 sealed grant、消费/撤销/过期审计 |
| X-274 | Done | 首次启动默认资源引导：default 账号池、基础访问策略与测试零配置基线 |
| X-275 | Done | 补深 Gateway Cache 资源生命周期 API：默认值、状态归一、touch/invalidate 与测试 |
| X-276 | Done | 补深 Public Resource Lineage API：分发 Key、模型、缓存与请求关系图谱 |
| X-277 | Done | 补齐 Operations / Tunings 公共端点：wait/delete/cancel 响应一致性与测试 |
| X-278 | Done | 前端资源详情弹窗展示 lineage summary 与 cache lifecycle |
| X-279 | Done | Operations wait 从 immediate wrapper 升级为可配置短轮询 |
| X-280 | Done | 设计并实现真实上游缓存创建 executor：Gemini cachedContents 首版 |
| X-281 | Duplicate | 已迁移到本地：x-ai-gateway 对标差距增强总览 |
| X-282 | Duplicate | 已迁移到本地：Provider Registry 2.0 |
| X-283 | Duplicate | 已迁移到本地：非 Chat 资源族扩展 |
| X-284 | Duplicate | 已迁移到本地：Realtime 与 Streaming 真实代理闭环 |
| X-285 | Duplicate | 已迁移到本地：SaaS 计费与支付闭环 |
| X-286 | Duplicate | 已迁移到本地：Portal 用户自助增强 |
| X-287 | Duplicate | 已迁移到本地：编程类账号身份治理 |
| X-288 | Duplicate | 已迁移到本地：客户端接入包 |
| X-289 | Duplicate | 已迁移到本地：路由策略 2.0 |

## 继续跟踪项

- X-263 是本次全量读取中唯一仍为 `Backlog` 的项目任务，已单独迁移为本地任务：[TASK-20260505-003](../../tasks/backlog/TASK-20260505-003-linear-x263-second-gap-overview.md)。
- X-281 至 X-289 已在 2026-05-01 迁移到本地，本次保留其线上 canceled 状态作为历史事实。
