/* eslint-disable react-refresh/only-export-components */
import type { ComponentType } from 'react'
import { createBrowserRouter, Navigate, useLocation } from 'react-router-dom'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { AppLayout } from './layout'
import { RequireAdminAuth } from '@/features/auth/auth-provider'
import { consoleLegacyRoutes, toConsolePath } from './route-surfaces'
import { RouteErrorBoundary, RouteNotFoundPage } from './route-error-boundary'

type RouteModule = Record<string, ComponentType>
type LazyHydrateRoute = {
  lazy?: unknown
  HydrateFallback?: ComponentType
  errorElement?: unknown
  children?: LazyHydrateRoute[]
  [key: string]: unknown
}

function RouteHydrateFallback() {
  return (
    <main className="min-h-svh bg-background px-4 py-6 sm:px-6 lg:px-8">
      <PageSkeleton count={1} />
    </main>
  )
}

function lazyPage<TModule extends RouteModule>(loader: () => Promise<TModule>, exportName: keyof TModule) {
  return async () => {
    const module = await loader()
    return {
      Component: module[exportName] as ComponentType,
    }
  }
}

function withRouteDefaults<TRoute extends LazyHydrateRoute>(routes: TRoute[]): TRoute[] {
  return routes.map((route) => {
    const children = route.children ? withRouteDefaults(route.children) : undefined
    return {
      ...route,
      ...(route.lazy && !route.HydrateFallback ? { HydrateFallback: RouteHydrateFallback } : {}),
      ...(!route.errorElement ? { errorElement: <RouteErrorBoundary /> } : {}),
      ...(children ? { children } : {}),
    } as TRoute
  })
}

function LegacyConsoleRedirect() {
  const location = useLocation()
  return <Navigate to={`${toConsolePath(location.pathname)}${location.search}${location.hash}`} replace />
}

const consoleChildren = [
  { index: true, element: <Navigate to="/console/ops" replace /> },
  { path: 'credentials', lazy: lazyPage(() => import('../features/credentials/credentials-page'), 'CredentialsPage') },
  { path: 'keys', lazy: lazyPage(() => import('../features/keys/keys-page'), 'KeysPage') },
  { path: 'keys/:id', lazy: lazyPage(() => import('../features/keys/key-detail-page'), 'KeyDetailPage') },
  { path: 'accounts', element: <Navigate to="/console/account-groups" replace /> },
  { path: 'account-groups', lazy: lazyPage(() => import('../features/accounts/account-groups-page'), 'AccountGroupsPage') },
  { path: 'users', lazy: lazyPage(() => import('../features/user-domain/users-page'), 'UsersPage') },
  { path: 'plans', lazy: lazyPage(() => import('../features/user-domain/plans-page'), 'PlansPage') },
  { path: 'access-groups', lazy: lazyPage(() => import('../features/user-domain/access-groups-page'), 'AccessGroupsPage') },
  { path: 'subscriptions', lazy: lazyPage(() => import('../features/user-domain/subscriptions-page'), 'SubscriptionsPage') },
  { path: 'announcements', lazy: lazyPage(() => import('../features/user-domain/announcements-page'), 'AnnouncementsPage') },
  { path: 'invitation-codes', lazy: lazyPage(() => import('../features/user-domain/invitation-codes-page'), 'InvitationCodesPage') },
  { path: 'promo-codes', lazy: lazyPage(() => import('../features/user-domain/promo-codes-page'), 'PromoCodesPage') },
  { path: 'models', lazy: lazyPage(() => import('../features/models/models-page'), 'ModelsPage') },
  { path: 'resources', lazy: lazyPage(() => import('../features/resources/resources-page'), 'ResourcesPage') },
  { path: 'upstream-cache', lazy: lazyPage(() => import('../features/upstream-cache/upstream-cache-page'), 'UpstreamCachePage') },
  { path: 'account-groups/:id', lazy: lazyPage(() => import('../features/accounts/account-group-detail-page'), 'AccountGroupDetailPage') },
  { path: 'accounts/connect/codex', lazy: lazyPage(() => import('../features/accounts/codex-onboarding-page'), 'CodexOnboardingPage') },
  { path: 'accounts/:id', element: <Navigate to="/console/account-groups" replace /> },
  { path: 'network/proxies', lazy: lazyPage(() => import('../features/network/proxies-page'), 'ProxiesPage') },
  { path: 'network/proxies/:id', lazy: lazyPage(() => import('../features/network/proxy-detail-page'), 'ProxyDetailPage') },
  { path: 'network/tls-profiles', lazy: lazyPage(() => import('../features/network/tls-profiles-page'), 'TlsProfilesPage') },
  { path: 'network/probes', element: <Navigate to="/console/network/proxies" replace /> },
  { path: 'incidents', lazy: lazyPage(() => import('../features/incidents/incidents-page'), 'IncidentsPage') },
  { path: 'request-logs', lazy: lazyPage(() => import('../features/request-logs/request-logs-page'), 'RequestLogsPage') },
  { path: 'dashboard', lazy: lazyPage(() => import('../features/dashboard/dashboard-page'), 'DashboardPage') },
  { path: 'traces', lazy: lazyPage(() => import('../features/traces/traces-page'), 'TracesPage') },
  { path: 'workbench', lazy: lazyPage(() => import('../features/workbench/workbench-page'), 'WorkbenchPage') },
  { path: 'settings/system', lazy: lazyPage(() => import('../features/settings/system-settings-page'), 'SystemSettingsPage') },
  { path: 'settings/admin-auth', lazy: lazyPage(() => import('../features/auth/auth-settings-page'), 'AuthSettingsPage') },
  { path: 'ops', lazy: lazyPage(() => import('../features/ops/ops-page'), 'OpsPage') },
  { path: 'ops/alerts', lazy: lazyPage(() => import('../features/ops/ops-alerts-page'), 'OpsAlertsPage') },
  { path: 'ops/governance', lazy: lazyPage(() => import('../features/ops/governance-page'), 'GovernancePage') },
  { path: 'ops/probes', element: <Navigate to="/console/ops" replace /> },
  { path: 'ops/system-events', lazy: lazyPage(() => import('../features/ops/system-events-page'), 'SystemEventsPage') },
  { path: 'ops/cost-routing', element: <Navigate to="/console/ops" replace /> },
  { path: 'ops/logs', element: <Navigate to="/console/traces" replace /> },
  { path: 'error-rules', element: <Navigate to="/console/ops/governance" replace /> },
  { path: 'provider-sites', lazy: lazyPage(() => import('../features/provider-sites/provider-sites-page'), 'ProviderSitesPage') },
  { path: 'provider-sites/new', element: <Navigate to="/console/provider-sites" replace /> },
  { path: 'provider-sites/new/settings', element: <Navigate to="/console/provider-sites" replace /> },
  { path: 'provider-sites/:id', lazy: lazyPage(() => import('../features/provider-sites/provider-site-detail-page'), 'ProviderSiteDetailPage') },
  { path: 'provider-sites/:id/settings', element: <Navigate to="/console/provider-sites" replace /> },
  { path: 'capability-matrix', lazy: lazyPage(() => import('../features/provider-sites/capability-matrix-page'), 'CapabilityMatrixPage') },
  { path: 'provider-reference-gap', element: <Navigate to="/console/models" replace /> },
  { path: 'native-compatibility', element: <Navigate to="/console/models" replace /> },
  { path: 'translation-debug', element: <Navigate to="/console/workbench" replace /> },
  { path: 'vector-stores', element: <Navigate to="/console/workbench" replace /> },
  { path: 'operations/*', element: <Navigate to="/console/ops" replace /> },
  { path: 'integrations', element: <Navigate to="/console/integrations/webhooks" replace /> },
  { path: 'integrations/webhooks', lazy: lazyPage(() => import('../features/integrations/webhooks-page'), 'WebhooksPage') },
  { path: 'integrations/channels', lazy: lazyPage(() => import('../features/integrations/channels-page'), 'ChannelsPage') },
  { path: 'integrations/runbooks', element: <Navigate to="/console/ops" replace /> },
  { path: 'integrations/subscriptions', lazy: lazyPage(() => import('../features/integrations/subscriptions-page'), 'SubscriptionsPage') },
  { path: 'integrations/deliveries', lazy: lazyPage(() => import('../features/integrations/deliveries-page'), 'DeliveriesPage') },
  { path: 'integrations/external-apps', lazy: lazyPage(() => import('../features/integrations/external-apps-page'), 'ExternalAppsPage') },
  { path: 'integrations/extensions/:slug', lazy: lazyPage(() => import('../features/integrations/extension-runtime-page'), 'ExtensionRuntimePage') },
  { path: '*', element: <RouteNotFoundPage /> },
]

const legacyConsoleRedirectRoutes = consoleLegacyRoutes
  .filter((path) => path !== '/')
  .map((path) => ({
    path: `${path.slice(1)}/*`,
    element: <LegacyConsoleRedirect />,
  }))

const appRoutesBase = [
  {
    path: '/login',
    lazy: lazyPage(() => import('../features/auth/login-page'), 'LoginPage'),
  },
  {
    path: '/portal/login',
    lazy: lazyPage(() => import('../features/portal/portal-login-page'), 'PortalLoginPage'),
  },
  {
    path: '/portal/register',
    lazy: lazyPage(() => import('../features/portal/portal-login-page'), 'PortalRegisterPage'),
  },
  {
    path: '/portal',
    lazy: lazyPage(() => import('../features/portal/portal-home-page'), 'PortalHomePage'),
  },
  {
    path: '/portal/subscriptions',
    lazy: lazyPage(() => import('../features/portal/portal-subscriptions-page'), 'PortalSubscriptionsPage'),
  },
  {
    path: '/portal/keys',
    lazy: lazyPage(() => import('../features/portal/portal-keys-page'), 'PortalKeysPage'),
  },
  {
    path: '/portal/redeem',
    lazy: lazyPage(() => import('../features/portal/portal-redeem-page'), 'PortalRedeemPage'),
  },
  {
    path: '/portal/invitations',
    lazy: lazyPage(() => import('../features/portal/portal-invitations-page'), 'PortalInvitationsPage'),
  },
  {
    path: '/portal/usage',
    lazy: lazyPage(() => import('../features/portal/portal-usage-page'), 'PortalUsagePage'),
  },
  {
    path: '/portal/status',
    lazy: lazyPage(() => import('../features/portal/portal-status-page'), 'PortalStatusPage'),
  },
  {
    path: '/portal/orders',
    lazy: lazyPage(() => import('../features/portal/portal-orders-page'), 'PortalOrdersPage'),
  },
  {
    path: '/portal/security',
    lazy: lazyPage(() => import('../features/portal/portal-security-page'), 'PortalSecurityPage'),
  },
  {
    path: '/portal/announcements/:id',
    lazy: lazyPage(() => import('../features/portal/portal-announcement-detail-page'), 'PortalAnnouncementDetailPage'),
  },
  {
    path: '/console',
    element: (
      <RequireAdminAuth>
        <AppLayout />
      </RequireAdminAuth>
    ),
    children: consoleChildren,
  },
  {
    path: '/',
    lazy: lazyPage(() => import('../features/public/public-home-page'), 'PublicHomePage'),
  },
  {
    path: '/docs',
    element: <Navigate to="/" replace />,
  },
  {
    path: '/pricing',
    element: <Navigate to="/" replace />,
  },
  {
    path: '/status',
    element: <Navigate to="/" replace />,
  },
  {
    path: '/public/docs/openapi.json',
    element: <Navigate to="/" replace />,
  },
  ...legacyConsoleRedirectRoutes,
  {
    path: '*',
    element: <RouteNotFoundPage />,
  },
]

export const appRoutes = withRouteDefaults(appRoutesBase)

export const router = createBrowserRouter(appRoutes)
