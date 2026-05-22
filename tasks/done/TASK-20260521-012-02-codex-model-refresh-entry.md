# TASK-20260521-012-02 Codex 模型刷新入口

状态：Done  
优先级：Critical  
上游来源：[TASK-20260521-012](./TASK-20260521-012-console-health-codex-model-tls-workbench-ux.md)

## 任务类型

子任务

## 背景

用户在 Codex 类型账号中没有找到刷新模型按钮。系统需要给 Codex 账号提供清晰的支持模型刷新入口。

## 目标

- 梳理当前 Codex 模型目录来源。
- 为 Codex 类型账号补齐模型刷新按钮或详情操作。
- 刷新后把模型列表写回账号支持模型字段。

## 非目标

- 不承诺存在 Codex 专属公开 model API。
- 不触发真实计费请求。

## 输入

- `web/src/features/accounts/`
- Admin account controller/service
- 模型目录 service

## 输出

前端刷新入口、后端刷新 API 或复用接口、测试更新。

## 影响范围

账号分组详情、Codex 账号模型同步。

## 依赖

现有 provider/model catalog 能力。

## 风险

如果 Codex 无独立远端模型接口，需要以系统目录刷新方式落地并保持文案准确。

## 验收标准

- [x] Codex 类型账号可见模型刷新操作。
- [x] 刷新结果写回账号支持模型。
- [x] 无真实敏感 token 输出。

## 测试边界

- 账号分组页面相关 vitest。
- 后端账号模型刷新定向测试。

## 当前状态

已完成。Codex 账号详情弹窗增加“刷新模型”，后端新增 `/admin/accounts/{id}/refresh-models`，以系统模型目录和 Codex 默认模型更新账号支持模型。
