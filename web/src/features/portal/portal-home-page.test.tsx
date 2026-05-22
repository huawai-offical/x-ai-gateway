// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { PortalHomePage } from './portal-home-page'

vi.mock('./api', () => ({
  getPortalSession: vi.fn(async () => ({
    authenticated: true,
    userId: 1,
    email: 'user@example.com',
    displayName: '社区用户',
    expiresAt: '2026-05-07T10:00:00Z',
  })),
  listPortalSubscriptions: vi.fn(async () => [
    {
      id: 1,
      planId: 1,
      planName: 'Codex Basic',
      status: 'ACTIVE',
      startsAt: '2026-05-01T00:00:00Z',
      rpmLimit: 60,
      tpmLimit: 120000,
      autoRenew: false,
    },
  ]),
  listPortalKeys: vi.fn(async () => [
    {
      id: 10,
      keyName: 'codex-cli-key',
      maskedKey: 'xag_***codex',
      active: true,
      allowedProtocolSuites: ['openai', 'responses'],
      allowedModels: ['gpt-5.4@low'],
      rpmLimit: 60,
      lastUsedAt: '2026-05-07T02:00:00Z',
    },
  ]),
  listPortalAnnouncements: vi.fn(async () => []),
  getPortalRedeemStatus: vi.fn(async () => ({
    available: true,
    message: 'ok',
    currentTokenCredits: 500,
  })),
  listPortalBalanceLedger: vi.fn(async () => [
    {
      id: 1,
      deltaTokenCredits: 500,
      balanceAfterTokenCredits: 500,
      reason: 'REDEEM',
      createdAt: '2026-05-07T01:00:00Z',
    },
  ]),
  listPortalPaymentOrders: vi.fn(async () => []),
  logoutPortal: vi.fn(),
  markPortalAnnouncementRead: vi.fn(),
  redeemPortalCode: vi.fn(),
  createPortalPaymentOrder: vi.fn(),
}))

afterEach(() => {
  cleanup()
})

function renderPage() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/portal']}>
        <PortalHomePage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('PortalHomePage', () => {
  it('renders Codex self-service access without admin runtime details', async () => {
    renderPage()

    expect(await screen.findByText('Codex 接入')).toBeInTheDocument()
    expect(await screen.findByText('可用')).toBeInTheDocument()
    expect(await screen.findByText('http://localhost:3000/v1')).toBeInTheDocument()
    expect((await screen.findAllByText('xag_***codex')).length).toBeGreaterThan(0)
    expect(screen.queryByText('账号分组内部候选')).not.toBeInTheDocument()
    expect(screen.queryByText('Provider')).not.toBeInTheDocument()
  })

  it('keeps overview links on registered portal routes', async () => {
    const { container } = renderPage()

    expect(await screen.findByText('Codex 接入')).toBeInTheDocument()
    const hrefs = Array.from(container.querySelectorAll('a'))
      .map((link) => link.getAttribute('href'))
      .filter((href): href is string => Boolean(href))

    expect(hrefs).toEqual(expect.arrayContaining(['/portal/redeem', '/portal/keys', '/portal/subscriptions']))
    expect(hrefs.some((href) => href.includes('/public/docs/openapi.json'))).toBe(false)
    const exactPortalRoutes = new Set([
      '/portal',
      '/portal/redeem',
      '/portal/keys',
      '/portal/subscriptions',
      '/portal/usage',
      '/portal/status',
      '/portal/orders',
      '/portal/security',
    ])
    expect(hrefs.filter((href) =>
      href.startsWith('/portal')
      && !exactPortalRoutes.has(href)
      && !href.startsWith('/portal/announcements/'),
    )).toEqual([])
  })
})
