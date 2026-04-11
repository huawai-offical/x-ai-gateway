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
    ],
  },
])
