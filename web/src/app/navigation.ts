import { matchPath } from 'react-router-dom'

export type NavigationIcon =
  | 'key'
  | 'accounts'
  | 'proxy'
  | 'tls'
  | 'probe'
  | 'overview'
  | 'alert'
  | 'logs'
  | 'site'
  | 'matrix'
  | 'explain'
  | 'rules'
  | 'install'
  | 'backup'
  | 'upgrade'
  | 'history'
  | 'integration'

export type NavigationItem = {
  to: string
  label: string
  description: string
  icon: NavigationIcon
  end?: boolean
}

export type NavigationGroup = {
  label: string
  items: NavigationItem[]
}

type RouteMeta = {
  patterns: string[]
  title: string
  description: string
  groupLabel: string
  navTo: string
}

export const navigationGroups: NavigationGroup[] = [
  {
    label: '访问与账号',
    items: [
      { to: '/keys', label: 'Keys', description: '预算、模型与协议权限', icon: 'key' },
      { to: '/account-pools', label: '账号池', description: '管理上游账号接入与池化', icon: 'accounts' },
    ],
  },
  {
    label: '网络治理',
    items: [
      { to: '/network/proxies', label: '代理池', description: '出口代理与状态追踪', icon: 'proxy' },
      { to: '/network/tls-profiles', label: 'TLS 指纹', description: '出站画像与指纹策略', icon: 'tls', end: true },
      { to: '/network/probes', label: '网络 Probe', description: '连通性检测与结果汇总', icon: 'probe', end: true },
    ],
  },
  {
    label: '运行工作台',
    items: [
      { to: '/incidents', label: 'Incidents', description: '当前风险事件、影响范围与建议动作', icon: 'overview', end: true },
      { to: '/traces', label: 'Traces', description: '按 requestId / resource 串联 trace 与实体', icon: 'logs', end: true },
      { to: '/ops/alerts', label: '告警中心', description: '告警规则、状态与处置', icon: 'alert', end: true },
      { to: '/ops/probes', label: '拨测记录', description: '主动拨测与结果回溯', icon: 'probe', end: true },
    ],
  },
  {
    label: '站点真相',
    items: [
      { to: '/provider-sites', label: '站点档案', description: '站点能力、健康与鉴权', icon: 'site' },
      { to: '/capability-matrix', label: '能力矩阵', description: '协议与能力兼容视图', icon: 'matrix', end: true },
      { to: '/workbench', label: 'Workbench', description: '请求、计划、执行与 trace 工作台', icon: 'explain', end: true },
    ],
  },
  {
    label: '策略与操作',
    items: [
      { to: '/error-rules', label: '错误规则', description: '例外、透传与重写策略', icon: 'rules', end: true },
      { to: '/operations/install', label: '安装初始化', description: '平台初始化与引导流程', icon: 'install', end: true },
      { to: '/operations/changes', label: '变更编排', description: '统一申请、审批、执行与回滚', icon: 'upgrade', end: true },
      { to: '/operations/windows', label: '维护窗口', description: '维护窗口与执行时段治理', icon: 'backup', end: true },
      { to: '/operations/checkpoints', label: '恢复检查点', description: '真实快照、核验与恢复基线', icon: 'history', end: true },
    ],
  },
  {
    label: '外部联动',
    items: [
      { to: '/integrations/webhooks', label: 'Webhooks', description: '管理 webhook endpoint 与签名配置', icon: 'integration', end: true },
      { to: '/integrations/channels', label: 'Channels', description: '定义 webhook / IM / email 通道', icon: 'integration', end: true },
      { to: '/integrations/subscriptions', label: 'Subscriptions', description: '配置事件订阅与过滤条件', icon: 'integration', end: true },
      { to: '/integrations/deliveries', label: 'Deliveries', description: '查看投递记录、失败重试与重放', icon: 'integration', end: true },
    ],
  },
]

const routeMeta: RouteMeta[] = [
  {
    patterns: ['/keys/:id'],
    title: 'Key 详情',
    description: '查看单个策略对象的预算、协议许可与绑定状态。',
    groupLabel: '访问与账号',
    navTo: '/keys',
  },
  {
    patterns: ['/keys'],
    title: 'Keys',
    description: '统一管理 DistributedKey、预算限制与协议可见性。',
    groupLabel: '访问与账号',
    navTo: '/keys',
  },
  {
    patterns: ['/account-pools/:id'],
    title: '账号池详情',
    description: '查看池内账号、健康状态与池化规则。',
    groupLabel: '访问与账号',
    navTo: '/account-pools',
  },
  {
    patterns: ['/accounts/connect/:provider'],
    title: '连接上游账号',
    description: '发起 OAuth 或等价授权流程，将账号接入统一控制面。',
    groupLabel: '访问与账号',
    navTo: '/account-pools',
  },
  {
    patterns: ['/accounts/callback/:provider'],
    title: '账号授权回调',
    description: '处理授权回调并回写账号接入结果。',
    groupLabel: '访问与账号',
    navTo: '/account-pools',
  },
  {
    patterns: ['/accounts/:id'],
    title: '账号详情',
    description: '查看单个上游账号的授权信息、状态与限制条件。',
    groupLabel: '访问与账号',
    navTo: '/account-pools',
  },
  {
    patterns: ['/account-pools'],
    title: '账号池',
    description: '统一维护各厂商账号池、接入状态与容量分布。',
    groupLabel: '访问与账号',
    navTo: '/account-pools',
  },
  {
    patterns: ['/network/proxies/:id'],
    title: '代理详情',
    description: '查看单个代理节点的状态、链路与绑定关系。',
    groupLabel: '网络治理',
    navTo: '/network/proxies',
  },
  {
    patterns: ['/network/proxies'],
    title: '代理池',
    description: '集中治理出口代理、可用性与网络隔离策略。',
    groupLabel: '网络治理',
    navTo: '/network/proxies',
  },
  {
    patterns: ['/network/tls-profiles'],
    title: 'TLS 指纹',
    description: '维护 TLS 指纹画像与出站策略绑定。',
    groupLabel: '网络治理',
    navTo: '/network/tls-profiles',
  },
  {
    patterns: ['/network/probes'],
    title: '网络 Probe',
    description: '查看网络探测目标、延迟数据与最近失败原因。',
    groupLabel: '网络治理',
    navTo: '/network/probes',
  },
  {
    patterns: ['/ops/alerts'],
    title: '告警中心',
    description: '跟踪当前告警状态、受影响对象与处置入口。',
    groupLabel: '运行工作台',
    navTo: '/ops/alerts',
  },
  {
    patterns: ['/ops/probes'],
    title: '拨测记录',
    description: '查看主动拨测计划、结果和历史变化。',
    groupLabel: '运行工作台',
    navTo: '/ops/probes',
  },
  {
    patterns: ['/traces'],
    title: 'Trace Workbench',
    description: '按 requestId、resource key 和 upstream object 串联链路与实体。',
    groupLabel: '运行工作台',
    navTo: '/traces',
  },
  {
    patterns: ['/incidents'],
    title: 'Incident Command Center',
    description: '先回答发生了什么、影响谁、为什么危险以及下一步建议动作。',
    groupLabel: '运行工作台',
    navTo: '/incidents',
  },
  {
    patterns: ['/provider-sites/new/settings'],
    title: '新建站点设置',
    description: '创建新的 provider site，并初始化基础配置与鉴权设置。',
    groupLabel: '站点真相',
    navTo: '/provider-sites',
  },
  {
    patterns: ['/provider-sites/:id'],
    title: '站点运行档案',
    description: '先查看 blocker、accepted exception、推荐动作和下一步入口。',
    groupLabel: '站点真相',
    navTo: '/provider-sites',
  },
  {
    patterns: ['/provider-sites/:id/settings'],
    title: '站点设置',
    description: '编辑站点配置、刷新能力快照并维护基础信息。',
    groupLabel: '站点真相',
    navTo: '/provider-sites',
  },
  {
    patterns: ['/provider-sites'],
    title: '站点档案',
    description: '统一查看 provider site、健康快照与能力真相源。',
    groupLabel: '站点真相',
    navTo: '/provider-sites',
  },
  {
    patterns: ['/capability-matrix'],
    title: '能力矩阵',
    description: '按 blocked、degraded 和 accepted exception 发现全局限制。',
    groupLabel: '站点真相',
    navTo: '/capability-matrix',
  },
  {
    patterns: ['/workbench', '/translation-debug'],
    title: 'Translation Workbench',
    description: '按 Request → Plan → Execute → Trace → Raw 的顺序调试请求。',
    groupLabel: '站点真相',
    navTo: '/workbench',
  },
  {
    patterns: ['/error-rules'],
    title: '错误规则',
    description: '集中配置错误透传、重写、阻断和例外策略。',
    groupLabel: '策略与操作',
    navTo: '/error-rules',
  },
  {
    patterns: ['/operations/install'],
    title: '安装初始化',
    description: '管理平台初始化步骤、状态与必要检查。',
    groupLabel: '策略与操作',
    navTo: '/operations/install',
  },
  {
    patterns: ['/operations/changes', '/operations/backups', '/operations/upgrades', '/operations/rollbacks'],
    title: '变更编排',
    description: '统一处理 ChangePlan、审批、执行阶段与自动回滚。',
    groupLabel: '策略与操作',
    navTo: '/operations/changes',
  },
  {
    patterns: ['/operations/windows'],
    title: '维护窗口',
    description: '配置变更窗口，约束升级与高风险动作的执行时段。',
    groupLabel: '策略与操作',
    navTo: '/operations/windows',
  },
  {
    patterns: ['/operations/checkpoints'],
    title: '恢复检查点',
    description: '查看 metadata、runtime、data 三类快照与 checkpoint 核验结果。',
    groupLabel: '策略与操作',
    navTo: '/operations/checkpoints',
  },
  {
    patterns: ['/integrations/webhooks'],
    title: 'Webhook Endpoints',
    description: '管理 webhook endpoint、签名策略和 timeout，作为统一外发底座。',
    groupLabel: '外部联动',
    navTo: '/integrations/webhooks',
  },
  {
    patterns: ['/integrations/channels'],
    title: 'Notification Channels',
    description: '定义 webhook / IM / email 通道，并绑定对外出口。',
    groupLabel: '外部联动',
    navTo: '/integrations/channels',
  },
  {
    patterns: ['/integrations/subscriptions'],
    title: 'Event Subscriptions',
    description: '配置事件筛选规则，让外发通知稳定命中目标。',
    groupLabel: '外部联动',
    navTo: '/integrations/subscriptions',
  },
  {
    patterns: ['/integrations/deliveries'],
    title: 'Outbound Deliveries',
    description: '查看最近投递、失败重试和 replay，形成完整外发审计链。',
    groupLabel: '外部联动',
    navTo: '/integrations/deliveries',
  },
]

const navigationItems = navigationGroups.flatMap((group) => group.items)

export function resolveRouteMeta(pathname: string) {
  const matchedRoute = routeMeta.find((item) =>
    item.patterns.some((pattern) => matchPath({ path: pattern, end: true }, pathname)),
  )

  if (!matchedRoute) {
    return {
      title: 'Admin Console',
      description: '统一承载多协议网关的配置、站点、网络与运行观测视图。',
      groupLabel: '控制台',
      navLabel: '总览',
      navTo: '/keys',
    }
  }

  const matchedNav = navigationItems.find((item) => item.to === matchedRoute.navTo)

  return {
    ...matchedRoute,
    navLabel: matchedNav?.label ?? matchedRoute.title,
  }
}
