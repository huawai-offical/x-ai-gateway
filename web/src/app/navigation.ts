import { matchPath } from 'react-router-dom'
import type { LucideIcon } from 'lucide-react'
import { toConsolePath } from './route-surfaces'
import {
  AlarmClockCheckIcon,
  AppWindowIcon,
  BlocksIcon,
  BotMessageSquareIcon,
  DatabaseZapIcon,
  KeyRoundIcon,
  LandmarkIcon,
  NetworkIcon,
  ShieldIcon,
  ShieldCheckIcon,
  SquareActivityIcon,
  TablePropertiesIcon,
  WaypointsIcon,
  WebhookIcon,
} from 'lucide-react'

export type NavigationItem = {
  to: string
  label: string
  icon: LucideIcon
  end?: boolean
}

export type NavigationGroup = {
  label: string
  items: NavigationItem[]
}

type RouteMeta = {
  patterns: string[]
  title: string
  groupLabel: string
  navTo: string
}

export type ResolvedRouteMeta = RouteMeta & {
  navLabel: string
  breadcrumbs: Array<{ label: string; to?: string }>
}

const baseNavigationGroups: NavigationGroup[] = [
  {
    label: '智能运维',
    items: [
      { to: '/ops', label: '智能运维总览', icon: SquareActivityIcon, end: true },
      { to: '/ops/alerts', label: '告警中心', icon: SquareActivityIcon, end: true },
      { to: '/ops/system-events', label: '系统事件', icon: AlarmClockCheckIcon, end: true },
    ],
  },
  {
    label: '接入与模型',
    items: [
      { to: '/credentials', label: '上游凭证', icon: KeyRoundIcon },
      { to: '/provider-sites', label: '厂商管理', icon: LandmarkIcon },
      { to: '/keys', label: '访问密钥', icon: ShieldIcon },
      { to: '/account-groups', label: '账号分组', icon: TablePropertiesIcon },
      { to: '/models', label: '模型目录', icon: BotMessageSquareIcon, end: true },
      { to: '/network/proxies', label: '代理池', icon: NetworkIcon },
    ],
  },
  {
    label: '路由治理',
    items: [
      { to: '/ops/governance', label: '治理策略', icon: ShieldCheckIcon, end: true },
      { to: '/network/tls-profiles', label: 'TLS 指纹', icon: ShieldCheckIcon, end: true },
    ],
  },
  {
    label: '观测记录',
    items: [
      { to: '/request-logs', label: '请求日志', icon: SquareActivityIcon, end: true },
      { to: '/traces', label: '链路追踪', icon: WaypointsIcon, end: true },
      { to: '/upstream-cache', label: '缓存记录', icon: DatabaseZapIcon, end: true },
      { to: '/resources', label: '资源记录', icon: BlocksIcon, end: true },
    ],
  },
  {
    label: '用户与计费',
    items: [
      { to: '/users', label: '用户清单', icon: AppWindowIcon, end: true },
      { to: '/plans', label: '套餐管理', icon: TablePropertiesIcon, end: true },
      { to: '/access-groups', label: '访问组', icon: ShieldCheckIcon, end: true },
      { to: '/subscriptions', label: '订阅关系', icon: ShieldIcon, end: true },
      { to: '/announcements', label: '公告中心', icon: AppWindowIcon, end: true },
      { to: '/promo-codes', label: '兑换码', icon: KeyRoundIcon, end: true },
    ],
  },
  {
    label: '系统工具',
    items: [
      { to: '/settings/admin-auth', label: '控制台认证', icon: ShieldCheckIcon, end: true },
      { to: '/settings/system', label: '系统参数', icon: DatabaseZapIcon, end: true },
      { to: '/workbench', label: '调试工作台', icon: AppWindowIcon, end: true },
    ],
  },
  {
    label: '集成扩展',
    items: [
      { to: '/integrations/webhooks', label: 'Webhook', icon: WebhookIcon, end: true },
      { to: '/integrations/channels', label: '通知通道', icon: WebhookIcon, end: true },
      { to: '/integrations/subscriptions', label: '订阅规则', icon: ShieldCheckIcon, end: true },
      { to: '/integrations/deliveries', label: '投递记录', icon: AlarmClockCheckIcon, end: true },
      { to: '/integrations/external-apps', label: '扩展应用', icon: AppWindowIcon, end: true },
    ],
  },
]

export const navigationGroups: NavigationGroup[] = baseNavigationGroups.map((group) => ({
  ...group,
  items: group.items.map((item) => ({
    ...item,
    to: toConsolePath(item.to),
  })),
}))

const routeMeta: RouteMeta[] = [
  {
    patterns: ['/credentials'],
    title: '上游凭证',
    groupLabel: '接入与模型',
    navTo: '/credentials',
  },
  {
    patterns: ['/provider-sites', '/provider-sites/:id', '/capability-matrix'],
    title: '厂商管理',
    groupLabel: '接入与模型',
    navTo: '/provider-sites',
  },
  {
    patterns: ['/accounts'],
    title: '账号分组',
    groupLabel: '接入与模型',
    navTo: '/account-groups',
  },
  {
    patterns: ['/account-groups'],
    title: '账号分组',
    groupLabel: '接入与模型',
    navTo: '/account-groups',
  },
  {
    patterns: ['/models'],
    title: '大模型管理',
    groupLabel: '接入与模型',
    navTo: '/models',
  },
  {
    patterns: ['/resources'],
    title: '异步资源记录',
    groupLabel: '观测记录',
    navTo: '/resources',
  },
  {
    patterns: ['/upstream-cache'],
    title: '缓存记录',
    groupLabel: '观测记录',
    navTo: '/upstream-cache',
  },
  {
    patterns: ['/keys/:id'],
    title: '访问密钥详情',
    groupLabel: '接入与模型',
    navTo: '/keys',
  },
  {
    patterns: ['/keys'],
    title: '访问密钥',
    groupLabel: '接入与模型',
    navTo: '/keys',
  },
  {
    patterns: ['/users'],
    title: '用户清单',
    groupLabel: '用户与计费',
    navTo: '/users',
  },
  {
    patterns: ['/plans'],
    title: '套餐管理',
    groupLabel: '用户与计费',
    navTo: '/plans',
  },
  {
    patterns: ['/access-groups'],
    title: '访问组与权益',
    groupLabel: '用户与计费',
    navTo: '/access-groups',
  },
  {
    patterns: ['/subscriptions'],
    title: '订阅关系',
    groupLabel: '用户与计费',
    navTo: '/subscriptions',
  },
  {
    patterns: ['/announcements'],
    title: '公告中心',
    groupLabel: '用户与计费',
    navTo: '/announcements',
  },
  {
    patterns: ['/promo-codes'],
    title: '兑换码活动',
    groupLabel: '用户与计费',
    navTo: '/promo-codes',
  },
  {
    patterns: ['/account-groups/:id'],
    title: '账号分组详情',
    groupLabel: '接入与模型',
    navTo: '/account-groups',
  },
  {
    patterns: ['/accounts/connect/codex'],
    title: 'Codex 接入',
    groupLabel: '接入与模型',
    navTo: '/account-groups',
  },
  {
    patterns: ['/accounts/connect/:provider'],
    title: '连接官方账号',
    groupLabel: '接入与模型',
    navTo: '/account-groups',
  },
  {
    patterns: ['/accounts/callback/:provider'],
    title: '官方账号授权回调',
    groupLabel: '接入与模型',
    navTo: '/account-groups',
  },
  {
    patterns: ['/accounts/:id'],
    title: '官方账号详情',
    groupLabel: '接入与模型',
    navTo: '/account-groups',
  },
  {
    patterns: ['/network/proxies/:id'],
    title: '代理详情',
    groupLabel: '接入与模型',
    navTo: '/network/proxies',
  },
  {
    patterns: ['/network/proxies'],
    title: '代理池',
    groupLabel: '接入与模型',
    navTo: '/network/proxies',
  },
  {
    patterns: ['/network/tls-profiles'],
    title: 'TLS 指纹',
    groupLabel: '路由治理',
    navTo: '/network/tls-profiles',
  },
  {
    patterns: ['/request-logs'],
    title: '请求日志',
    groupLabel: '观测记录',
    navTo: '/request-logs',
  },
  {
    patterns: ['/dashboard'],
    title: '角色协同视图',
    groupLabel: '智能运维',
    navTo: '/ops',
  },
  {
    patterns: ['/ops'],
    title: '智能运维总览',
    groupLabel: '智能运维',
    navTo: '/ops',
  },
  {
    patterns: ['/ops/alerts'],
    title: '告警中心',
    groupLabel: '智能运维',
    navTo: '/ops/alerts',
  },
  {
    patterns: ['/ops/system-events'],
    title: '系统事件时间线',
    groupLabel: '智能运维',
    navTo: '/ops/system-events',
  },
  {
    patterns: ['/traces'],
    title: '链路时间线',
    groupLabel: '观测记录',
    navTo: '/traces',
  },
  {
    patterns: ['/incidents'],
    title: '事件处置视图',
    groupLabel: '智能运维',
    navTo: '/ops',
  },
  {
    patterns: ['/workbench', '/translation-debug'],
    title: '白盒调试工作台',
    groupLabel: '系统工具',
    navTo: '/workbench',
  },
  {
    patterns: ['/settings/admin-auth'],
    title: '控制台认证',
    groupLabel: '系统工具',
    navTo: '/settings/admin-auth',
  },
  {
    patterns: ['/settings/system'],
    title: '系统参数',
    groupLabel: '系统工具',
    navTo: '/settings/system',
  },
  {
    patterns: ['/ops/governance'],
    title: '治理策略工作台',
    groupLabel: '路由治理',
    navTo: '/ops/governance',
  },
  {
    patterns: ['/error-rules', '/operations/install', '/operations/changes', '/operations/backups', '/operations/upgrades', '/operations/rollbacks', '/operations/windows', '/operations/checkpoints', '/operations/maintenance-runs', '/integrations/runbooks'],
    title: '智能运维总览',
    groupLabel: '智能运维',
    navTo: '/ops',
  },
  {
    patterns: ['/integrations/webhooks'],
    title: 'Webhook 终端',
    groupLabel: '集成扩展',
    navTo: '/integrations/webhooks',
  },
  {
    patterns: ['/integrations/channels'],
    title: '通知通道',
    groupLabel: '集成扩展',
    navTo: '/integrations/channels',
  },
  {
    patterns: ['/integrations/subscriptions'],
    title: '订阅规则',
    groupLabel: '集成扩展',
    navTo: '/integrations/subscriptions',
  },
  {
    patterns: ['/integrations/deliveries'],
    title: '投递记录',
    groupLabel: '集成扩展',
    navTo: '/integrations/deliveries',
  },
  {
    patterns: ['/integrations/external-apps'],
    title: '控制台扩展应用',
    groupLabel: '集成扩展',
    navTo: '/integrations/external-apps',
  },
  {
    patterns: ['/integrations/extensions/:slug'],
    title: '扩展应用运行页',
    groupLabel: '集成扩展',
    navTo: '/integrations/external-apps',
  },
]

const navigationItems = navigationGroups.flatMap((group) => group.items)

export function resolveRouteMeta(pathname: string): ResolvedRouteMeta {
  const routePathname = stripConsolePrefix(pathname)
  const matchedRoute = routeMeta.find((item) =>
    item.patterns.some((pattern) => matchPath({ path: pattern, end: true }, routePathname)),
  )

  if (!matchedRoute) {
    return {
      patterns: ['/'],
      title: '管理控制台',
      groupLabel: '控制台',
      navTo: '/ops',
      navLabel: '智能运维总览',
      breadcrumbs: [{ label: '控制台' }, { label: '智能运维总览' }],
    }
  }

  const canonicalNavTo = toConsolePath(matchedRoute.navTo)
  const matchedNav = navigationItems.find((item) => item.to === canonicalNavTo)
  const navLabel = matchedNav?.label ?? matchedRoute.title
  const breadcrumbs = [{ label: matchedRoute.groupLabel }, { label: navLabel }]

  if (matchedRoute.title !== navLabel) {
    breadcrumbs.push({ label: matchedRoute.title })
  }

  return {
    ...matchedRoute,
    navTo: canonicalNavTo,
    navLabel,
    breadcrumbs,
  }
}

function stripConsolePrefix(pathname: string) {
  if (pathname === '/console') {
    return '/'
  }
  if (pathname.startsWith('/console/')) {
    return pathname.slice('/console'.length)
  }
  return pathname
}
