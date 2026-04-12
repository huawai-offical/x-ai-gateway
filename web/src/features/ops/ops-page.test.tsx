// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { OpsPage } from './ops-page'

class MockSocket {
  onmessage: ((event: MessageEvent) => void) | null = null
  close() {}
}

vi.stubGlobal('WebSocket', MockSocket as unknown as typeof WebSocket)
vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(async () => ({
    snapshot: { observedAt: '', qps: 1.2, errorRate: 0.1, p95LatencyMs: 0, providerFailures: 1, activeAlerts: 2, affectedEntities: [] },
    alerts: [],
  })),
}))

describe('OpsPage', () => {
  it('renders realtime heading', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <OpsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('实时指挥台')).toBeInTheDocument()
  })
})
