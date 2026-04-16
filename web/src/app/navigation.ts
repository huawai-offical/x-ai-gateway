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
    label: '运行观测',
    items: [
      { to: '/ops', label: '实时总览', description: '核心运行信号与指挥台', icon: 'overview', end: true },
      { to: '/ops/alerts', label: '告警中心', description: '告警规则、状态与处置', icon: 'alert', end: true },
      { to: '/ops/probes', label: '拨测记录', description: '主动拨测与结果回溯', icon: 'probe', end: true },
      { to: '/ops/logs', label: '运行日志', description: '日志检索与异常排查', icon: 'logs', end: true },
    ],
  },
  {
    label: '站点真相',
    items: [
      { to: '/provider-sites', label: '站点档案', description: '站点能力、健康与鉴权', icon: 'site' },
      { to: '/capability-matrix', label: '能力矩阵', description: '协议与能力兼容视图', icon: 'matrix', end: true },
      { to: '/translation-debug', label: '执行解释', description: '计划、损耗与失败原因', icon: 'explain', end: true },
    ],
  },
  {
    label: '策略与操作',
    items: [
      { to: '/error-rules', label: '错误规则', description: '例外、透传与重写策略', icon: 'rules', end: true },
      { to: '/operations/install', label: '安装初始化', description: '平台初始化与引导流程', icon: 'install', end: true },
      { to: '/operations/backups', label: '备份恢复', description: '备份任务与恢复入口', icon: 'backup', end: true },
      { to: '/operations/upgrades', label: '升级回滚', description: '版本升级、检查与回滚', icon: 'upgrade', end: true },
      { to: '/operations/rollbacks', label: '回滚记录', description: '历史回滚与状态确认', icon: 'history', end: true },
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
    groupLabel: '运行观测',
    navTo: '/ops/alerts',
  },
  {
    patterns: ['/ops/probes'],
    title: '拨测记录',
    description: '查看主动拨测计划、结果和历史变化。',
    groupLabel: '运行观测',
    navTo: '/ops/probes',
  },
  {
    patterns: ['/ops/logs'],
    title: '运行日志',
    description: '检索运行日志、异常线索和实时输出。',
    groupLabel: '运行观测',
    navTo: '/ops/logs',
  },
  {
    patterns: ['/ops'],
    title: '实时总览',
    description: '聚合关键运行指标、实时事件流与操作入口。',
    groupLabel: '运行观测',
    navTo: '/ops',
  },
  {
    patterns: ['/provider-sites/new'],
    title: '新建站点档案',
    description: '录入新的 provider site，并初始化兼容与鉴权档案。',
    groupLabel: '站点真相',
    navTo: '/provider-sites',
  },
  {
    patterns: ['/provider-sites/:id'],
    title: '站点档案详情',
    description: '查看单个站点的能力快照、健康状态与协议支持面。',
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
    description: '按站点与协议对照兼容能力、损耗和限制条件。',
    groupLabel: '站点真相',
    navTo: '/capability-matrix',
  },
  {
    patterns: ['/translation-debug'],
    title: '执行解释',
    description: '查看 canonical plan、降级、阻断与执行解释结果。',
    groupLabel: '站点真相',
    navTo: '/translation-debug',
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
    patterns: ['/operations/backups'],
    title: '备份恢复',
    description: '查看备份任务、恢复入口和历史结果。',
    groupLabel: '策略与操作',
    navTo: '/operations/backups',
  },
  {
    patterns: ['/operations/upgrades'],
    title: '升级回滚',
    description: '处理版本检查、升级动作与回滚入口。',
    groupLabel: '策略与操作',
    navTo: '/operations/upgrades',
  },
  {
    patterns: ['/operations/rollbacks'],
    title: '回滚记录',
    description: '查看历史回滚、结果摘要与后续确认事项。',
    groupLabel: '策略与操作',
    navTo: '/operations/rollbacks',
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
