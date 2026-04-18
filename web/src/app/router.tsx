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
import { OpsAlertsPage } from '../features/ops/ops-alerts-page'
import { OpsProbesPage } from '../features/ops/ops-probes-page'
import { ErrorRulesPage } from '../features/error-rules/error-rules-page'
import { InstallPage } from '../features/operations/install-page'
import { ChangesPage } from '../features/operations/changes-page'
import { WindowsPage } from '../features/operations/windows-page'
import { CheckpointsPage } from '../features/operations/checkpoints-page'
import { WebhooksPage } from '../features/integrations/webhooks-page'
import { ChannelsPage } from '../features/integrations/channels-page'
import { SubscriptionsPage } from '../features/integrations/subscriptions-page'
import { DeliveriesPage } from '../features/integrations/deliveries-page'
import { ProviderSitesPage } from '../features/provider-sites/provider-sites-page'
import { ProviderSiteDetailPage } from '../features/provider-sites/provider-site-detail-page'
import { ProviderSiteSettingsPage } from '../features/provider-sites/provider-site-settings-page'
import { CapabilityMatrixPage } from '../features/provider-sites/capability-matrix-page'
import { WorkbenchPage } from '../features/workbench/workbench-page'
import { IncidentsPage } from '../features/incidents/incidents-page'
import { TracesPage } from '../features/traces/traces-page'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to="/incidents" replace /> },
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
      { path: 'incidents', element: <IncidentsPage /> },
      { path: 'traces', element: <TracesPage /> },
      { path: 'workbench', element: <WorkbenchPage /> },
      { path: 'ops', element: <Navigate to="/incidents" replace /> },
      { path: 'ops/alerts', element: <OpsAlertsPage /> },
      { path: 'ops/probes', element: <OpsProbesPage /> },
      { path: 'ops/logs', element: <Navigate to="/traces" replace /> },
      { path: 'error-rules', element: <ErrorRulesPage /> },
      { path: 'provider-sites', element: <ProviderSitesPage /> },
      { path: 'provider-sites/new', element: <Navigate to="/provider-sites/new/settings" replace /> },
      { path: 'provider-sites/new/settings', element: <ProviderSiteSettingsPage /> },
      { path: 'provider-sites/:id', element: <ProviderSiteDetailPage /> },
      { path: 'provider-sites/:id/settings', element: <ProviderSiteSettingsPage /> },
      { path: 'capability-matrix', element: <CapabilityMatrixPage /> },
      { path: 'translation-debug', element: <Navigate to="/workbench" replace /> },
      { path: 'operations/install', element: <InstallPage /> },
      { path: 'operations/changes', element: <ChangesPage /> },
      { path: 'operations/windows', element: <WindowsPage /> },
      { path: 'operations/checkpoints', element: <CheckpointsPage /> },
      { path: 'operations/backups', element: <Navigate to="/operations/changes" replace /> },
      { path: 'operations/upgrades', element: <Navigate to="/operations/changes" replace /> },
      { path: 'operations/rollbacks', element: <Navigate to="/operations/changes" replace /> },
      { path: 'integrations', element: <Navigate to="/integrations/webhooks" replace /> },
      { path: 'integrations/webhooks', element: <WebhooksPage /> },
      { path: 'integrations/channels', element: <ChannelsPage /> },
      { path: 'integrations/subscriptions', element: <SubscriptionsPage /> },
      { path: 'integrations/deliveries', element: <DeliveriesPage /> },
    ],
  },
])
