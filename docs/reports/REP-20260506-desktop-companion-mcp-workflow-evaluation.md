# REP-20260506 桌面 Companion、MCP/Skills/Session/Workspace 可行性评估

日期：2026-05-06  
关联需求：[REQ-20260506-021 桌面 Companion、MCP/Skills/Session/Workspace 可行性评估](../requirements/REQ-20260506-021-desktop-companion-mcp-workflow.md)  
关联任务：[TASK-20260506-025 桌面 Companion、MCP/Skills/Session/Workspace 可行性评估](../../tasks/done/TASK-20260506-025-desktop-companion-local-workflow-evaluation.md)

## 结论

建议：**延后进入主线服务端，作为独立 Companion / 浏览器插件 / IDE 插件项目推进 MVP**。

原因：

- `x-ai-gateway` 的主线定位是云端服务端网关，直接加入本机 workspace/session 管理会扩大权限边界。
- 当前已经具备 onboarding pack、client instance、plugin grant、Deep Link 授权下发，足以作为独立 companion 的服务端接入契约。
- MCP/Skills/Session/Workspace 涉及本地文件、IDE profile、shell、secret 和用户交互，应该在本机进程内受控执行，不应由云端主动读取。

## 推荐 MVP

| 模块 | MVP 行为 | 服务端关系 |
| --- | --- | --- |
| Client Instance 注册 | 用户确认后注册设备/插件实例。 | `/admin/client-instances` |
| Deep Link 授权 | 消费一次性 grant，写入本机配置。 | `/admin/client-instances/{id}/authorizations/{grantToken}/consume` |
| MCP Server 列表 | 本地读取用户显式选择的 MCP 配置。 | 只上传 capability summary，不上传 secret。 |
| Skills 列表 | 本地展示和启停 skills。 | 可上传 skill id/version/status。 |
| Session 浏览 | 仅本地读取，用户主动导出摘要。 | 云端只接收脱敏摘要。 |
| Workspace Hint | 用户手工命名 workspace，不扫描目录。 | 写入 `workspace_hint`。 |

## 能进云端的内容

- client family、client instance、workspace hint。
- 插件名称、版本、capability summary。
- 用户主动触发的配置导入状态。
- 脱敏后的 MCP/Skills 能力清单。

## 不进云端的内容

- 本地文件内容。
- IDE profile、浏览器 cookie、shell history。
- MCP server secret、OAuth token、provider key。
- Session 原文、workspace 路径树。
- 设备指纹、风控规避信息。

## 技术栈建议

| 方向 | 建议 |
| --- | --- |
| IDE 插件 | VS Code extension 优先，复用 Deep Link 和 plugin message schema。 |
| 桌面应用 | Tauri 优先于 Electron，减少体积与权限面。 |
| MCP 管理 | 本地读取用户指定配置文件，默认只做 summary。 |
| 云端同步 | 仅同步 instance 状态和 capability summary。 |
| Secret 保存 | 操作系统 keychain / credential manager，本地优先。 |

## 后续拆分任务

1. Companion MVP 仓库初始化：VS Code extension 或 Tauri shell。
2. 本地 secret manager 适配：Windows Credential Manager、macOS Keychain、libsecret。
3. MCP/Skills capability summary schema。
4. Session 摘要导出与脱敏策略。
5. UI：client instance 注册、授权消费、撤销、健康状态。

## 风险

- companion 误读 workspace 会触发隐私和合规风险。
- 自动同步 session 原文会突破服务端网关边界。
- 本机 secret 保存必须使用 OS keychain，不应落普通 JSON 文件。
- 插件生态兼容性强依赖各 IDE 能力，不能承诺一次覆盖所有客户端。
