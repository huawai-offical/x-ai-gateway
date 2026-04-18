// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import { RouterProvider, createMemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AppLayout } from './layout'

vi.mock('../features/keys/keys-page', () => ({ KeysPage: () => <div>keys</div> }))
vi.mock('../features/accounts/account-pools-page', () => ({ AccountPoolsPage: () => <div>account pools</div> }))
vi.mock('../features/accounts/account-pool-detail-page', () => ({ AccountPoolDetailPage: () => <div>account pool detail</div> }))
vi.mock('../features/accounts/oauth-connect-page', () => ({ OauthConnectPage: () => <div>oauth connect</div> }))
vi.mock('../features/accounts/oauth-callback-page', () => ({ OauthCallbackPage: () => <div>oauth callback</div> }))
vi.mock('../features/accounts/account-detail-page', () => ({ AccountDetailPage: () => <div>account detail</div> }))
vi.mock('../features/network/proxies-page', () => ({ ProxiesPage: () => <div>proxies</div> }))
vi.mock('../features/network/proxy-detail-page', () => ({ ProxyDetailPage: () => <div>proxy detail</div> }))
vi.mock('../features/network/tls-profiles-page', () => ({ TlsProfilesPage: () => <div>tls</div> }))
vi.mock('../features/network/probes-page', () => ({ ProbesPage: () => <div>probes</div> }))
vi.mock('../features/ops/ops-page', () => ({ OpsPage: () => <div>ops</div> }))
vi.mock('../features/ops/ops-alerts-page', () => ({ OpsAlertsPage: () => <div>alerts</div> }))
vi.mock('../features/ops/ops-probes-page', () => ({ OpsProbesPage: () => <div>ops probes</div> }))
vi.mock('../features/ops/ops-logs-page', () => ({ OpsLogsPage: () => <div>ops logs</div> }))
vi.mock('../features/error-rules/error-rules-page', () => ({ ErrorRulesPage: () => <div>error rules</div> }))
vi.mock('../features/operations/install-page', () => ({ InstallPage: () => <div>install</div> }))
vi.mock('../features/operations/changes-page', () => ({ ChangesPage: () => <div>changes page</div> }))
vi.mock('../features/operations/windows-page', () => ({ WindowsPage: () => <div>windows page</div> }))
vi.mock('../features/operations/checkpoints-page', () => ({ CheckpointsPage: () => <div>checkpoints page</div> }))
vi.mock('../features/provider-sites/provider-sites-page', () => ({ ProviderSitesPage: () => <div>provider sites</div> }))
vi.mock('../features/provider-sites/provider-site-detail-page', () => ({ ProviderSiteDetailPage: () => <div>provider site detail</div> }))
vi.mock('../features/provider-sites/capability-matrix-page', () => ({ CapabilityMatrixPage: () => <div>capability matrix</div> }))
vi.mock('../features/provider-sites/translation-debug-page', () => ({ TranslationDebugPage: () => <div>translation debug</div> }))

describe('operations router', () => {
  it('redirects legacy operations routes to the unified changes page', async () => {
    const router = createMemoryRouter(
      [
        {
          path: '/',
          element: <AppLayout />,
          children: [
            { path: 'operations/changes', element: <div>changes page</div> },
            { path: 'operations/backups', element: <div>legacy route</div> },
          ],
        },
      ],
      {
        initialEntries: ['/operations/backups'],
      },
    )

    render(<RouterProvider router={router} />)

    expect(await screen.findByText('legacy route')).toBeInTheDocument()
  })
})
