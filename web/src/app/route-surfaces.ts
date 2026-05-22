export type RouteSurface = 'public' | 'portal' | 'console'

export type RouteSurfaceMatch = {
  surface: RouteSurface
  canonicalPath: string
  legacy: boolean
}

export const publicRoutes = ['/'] as const

export const portalRoutes = [
  '/portal',
  '/portal/login',
  '/portal/register',
  '/portal/subscriptions',
  '/portal/keys',
  '/portal/redeem',
  '/portal/usage',
  '/portal/status',
  '/portal/orders',
  '/portal/security',
  '/portal/announcements/:id',
] as const

export const consoleLegacyRoutes = [
  '/',
  '/credentials',
  '/keys',
  '/accounts',
  '/account-groups',
  '/users',
  '/plans',
  '/models',
  '/resources',
  '/upstream-cache',
  '/network/proxies',
  '/network/tls-profiles',
  '/incidents',
  '/request-logs',
  '/dashboard',
  '/traces',
  '/workbench',
  '/settings/system',
  '/settings/admin-auth',
  '/ops',
  '/error-rules',
  '/provider-sites',
  '/capability-matrix',
  '/provider-reference-gap',
  '/native-compatibility',
  '/operations',
  '/integrations',
] as const

const portalPrefixes = ['/portal/']
const consolePrefixes = [
  '/console',
  '/credentials',
  '/keys',
  '/accounts',
  '/account-groups',
  '/users',
  '/plans',
  '/access-groups',
  '/subscriptions',
  '/announcements',
  '/promo-codes',
  '/models',
  '/resources',
  '/upstream-cache',
  '/network',
  '/incidents',
  '/request-logs',
  '/dashboard',
  '/traces',
  '/workbench',
  '/settings',
  '/ops',
  '/error-rules',
  '/provider-sites',
  '/capability-matrix',
  '/provider-reference-gap',
  '/native-compatibility',
  '/operations',
  '/integrations',
]

export function normalizePathname(pathname: string) {
  const path = pathname.split(/[?#]/)[0] || '/'
  if (path.length > 1 && path.endsWith('/')) {
    return path.slice(0, -1)
  }
  return path
}

export function matchRouteSurface(pathname: string): RouteSurfaceMatch {
  const path = normalizePathname(pathname)
  if (path === '/login') {
    return { surface: 'console', canonicalPath: '/login', legacy: false }
  }
  if (publicRoutes.some((route) => path === route)) {
    return { surface: 'public', canonicalPath: path, legacy: false }
  }
  if (path === '/portal' || portalPrefixes.some((prefix) => path.startsWith(prefix))) {
    return { surface: 'portal', canonicalPath: path, legacy: false }
  }
  if (path === '/console' || path.startsWith('/console/')) {
    return { surface: 'console', canonicalPath: path, legacy: false }
  }
  if (consolePrefixes.some((prefix) => path === prefix || path.startsWith(`${prefix}/`))) {
    return { surface: 'console', canonicalPath: toConsolePath(path), legacy: path !== '/login' }
  }
  return { surface: 'public', canonicalPath: path, legacy: false }
}

export function getRouteSurface(pathname: string): RouteSurface {
  return matchRouteSurface(pathname).surface
}

export function toConsolePath(pathname: string) {
  const path = normalizePathname(pathname)
  if (path === '/') {
    return '/console'
  }
  if (path === '/login' || path.startsWith('/console')) {
    return path
  }
  return `/console${path}`
}

export function isPortalRoute(pathname: string) {
  return getRouteSurface(pathname) === 'portal'
}

export function isConsoleRoute(pathname: string) {
  return getRouteSurface(pathname) === 'console'
}
