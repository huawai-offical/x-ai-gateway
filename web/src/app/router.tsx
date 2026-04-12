import { createBrowserRouter, Navigate } from 'react-router-dom'
import { AppLayout } from './layout'
import { KeysPage } from '../features/keys/keys-page'
import { KeyDetailPage } from '../features/keys/key-detail-page'
import { AccountPoolsPage } from '../features/accounts/account-pools-page'
import { AccountPoolDetailPage } from '../features/accounts/account-pool-detail-page'
import { OauthConnectPage } from '../features/accounts/oauth-connect-page'
import { OauthCallbackPage } from '../features/accounts/oauth-callback-page'
import { AccountDetailPage } from '../features/accounts/account-detail-page'
import { ProxiesPage } from '../features/network/proxies-page'
import { ProxyDetailPage } from '../features/network/proxy-detail-page'
import { TlsProfilesPage } from '../features/network/tls-profiles-page'
import { ProbesPage } from '../features/network/probes-page'
import { OpsPage } from '../features/ops/ops-page'
import { OpsAlertsPage } from '../features/ops/ops-alerts-page'
import { OpsProbesPage } from '../features/ops/ops-probes-page'
import { OpsLogsPage } from '../features/ops/ops-logs-page'
import { ErrorRulesPage } from '../features/error-rules/error-rules-page'
import { InstallPage } from '../features/operations/install-page'
import { BackupsPage } from '../features/operations/backups-page'
import { UpgradesPage } from '../features/operations/upgrades-page'
import { RollbacksPage } from '../features/operations/rollbacks-page'
import { ProviderSitesPage } from '../features/provider-sites/provider-sites-page'
import { ProviderSiteDetailPage } from '../features/provider-sites/provider-site-detail-page'
import { CapabilityMatrixPage } from '../features/provider-sites/capability-matrix-page'
import { TranslationDebugPage } from '../features/provider-sites/translation-debug-page'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to="/keys" replace /> },
      { path: 'keys', element: <KeysPage /> },
      { path: 'keys/:id', element: <KeyDetailPage /> },
      { path: 'account-pools', element: <AccountPoolsPage /> },
      { path: 'account-pools/:id', element: <AccountPoolDetailPage /> },
      { path: 'accounts/connect/:provider', element: <OauthConnectPage /> },
      { path: 'accounts/callback/:provider', element: <OauthCallbackPage /> },
      { path: 'accounts/:id', element: <AccountDetailPage /> },
      { path: 'network/proxies', element: <ProxiesPage /> },
      { path: 'network/proxies/:id', element: <ProxyDetailPage /> },
      { path: 'network/tls-profiles', element: <TlsProfilesPage /> },
      { path: 'network/probes', element: <ProbesPage /> },
      { path: 'ops', element: <OpsPage /> },
      { path: 'ops/alerts', element: <OpsAlertsPage /> },
      { path: 'ops/probes', element: <OpsProbesPage /> },
      { path: 'ops/logs', element: <OpsLogsPage /> },
      { path: 'error-rules', element: <ErrorRulesPage /> },
      { path: 'provider-sites', element: <ProviderSitesPage /> },
      { path: 'provider-sites/:id', element: <ProviderSiteDetailPage /> },
      { path: 'capability-matrix', element: <CapabilityMatrixPage /> },
      { path: 'translation-debug', element: <TranslationDebugPage /> },
      { path: 'operations/install', element: <InstallPage /> },
      { path: 'operations/backups', element: <BackupsPage /> },
      { path: 'operations/upgrades', element: <UpgradesPage /> },
      { path: 'operations/rollbacks', element: <RollbacksPage /> },
    ],
  },
])
