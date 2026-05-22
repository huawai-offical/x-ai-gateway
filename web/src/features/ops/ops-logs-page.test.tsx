// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../../lib/api'
import { OpsLogsPage } from './ops-logs-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(),
}))

const mockedApiRequest = apiRequest as unknown as ReturnType<typeof vi.fn>

mockedApiRequest.mockImplementation(async (url: string) => {
  if (url === '/admin/ops/logs/system') {
    return [{ id: 1, category: 'ops', action: 'refresh', resourceType: 'provider-site', resourceRef: 'site:openai' }]
  }
  if (url === '/admin/ops/logs/runtime') {
    return [{ id: 1, loggerName: 'gateway.runtime', logLevel: 'INFO', payloadLoggingEnabled: true }]
  }
  if (url === '/admin/observability/traces/req-1') {
    return {
      requestLog: {
        requestId: 'req-1',
        gatewayResourceKey: 'batch_1',
        supportStatus: 'NATIVE',
        degradationLevel: 'NATIVE',
      },
      routeDecision: { selectionSource: 'PREFIX_AFFINITY' },
      cacheHits: [{ cacheKind: 'prompt_cache' }],
      upstreamCacheReferences: [{ externalCacheRef: 'cachedContents/abc' }],
      asyncResourceSummary: { resourceKey: 'batch_1' },
      asyncResourceDetail: null,
    }
  }
  throw new Error(`unexpected url: ${url}`)
})

afterEach(() => {
  cleanup()
  mockedApiRequest.mockClear()
})

describe('OpsLogsPage', () => {
  it('shows system logs runtime logs and prefilled trace query', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/ops/logs?requestId=req-1&providerType=OPENAI_DIRECT&requestPath=/v1/batches/batch_1']}>
          <OpsLogsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('ops / refresh')).toBeInTheDocument()
    expect(await screen.findByText('gateway.runtime')).toBeInTheDocument()
    expect(await screen.findByText('req-1')).toBeInTheDocument()
    expect(await screen.findByText('PREFIX_AFFINITY')).toBeInTheDocument()
    expect(screen.getByText(/提供方类型 · OPENAI_DIRECT/)).toBeInTheDocument()
  })

  it('can submit a manual trace query', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <OpsLogsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    fireEvent.change(screen.getByPlaceholderText('输入请求 ID'), {
      target: { value: 'req-1' },
    })
    fireEvent.click(screen.getByRole('button', { name: '查询链路' }))

    await screen.findByText(/prompt_cache/)
    expect(screen.getAllByText('batch_1').length).toBeGreaterThan(0)
    expect(screen.getByText(/prompt_cache/)).toBeInTheDocument()
  })
})
