// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SystemEventsPage } from './system-events-page'

const { apiRequestMock } = vi.hoisted(() => ({
  apiRequestMock: vi.fn(async (url: string) => {
    if (url === '/admin/ops/system-events') {
      return [
        {
          id: 91,
          eventType: 'CODEX_RUNTIME_BATCH_RECOVERY',
          severity: 'INFO',
          source: 'account-group-admin',
          entityType: 'ACCOUNT_POOL',
          entityRef: 'account-group:1',
          title: 'Codex Runtime 批量恢复预检',
          detailJson: '{}',
          occurredAt: '2026-05-08T01:00:00Z',
        },
      ]
    }
    if (url === '/admin/ops/probe-runs') {
      return []
    }
    return []
  }),
}))

vi.mock('../../lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/api')>()
  return {
    ...actual,
    apiRequest: apiRequestMock,
  }
})

afterEach(() => {
  cleanup()
  apiRequestMock.mockClear()
})

describe('SystemEventsPage', () => {
  it('initializes filters from URL query for Codex batch audit events', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/console/ops/system-events?eventType=CODEX_RUNTIME_BATCH_RECOVERY&entityRef=account-group%3A1']}>
          <Routes>
            <Route path="/console/ops/system-events" element={<SystemEventsPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('Codex Runtime 批量恢复预检')).toBeInTheDocument()
    expect(screen.getByDisplayValue('CODEX_RUNTIME_BATCH_RECOVERY')).toBeInTheDocument()
    expect(screen.getByDisplayValue('account-group:1')).toBeInTheDocument()

    await waitFor(() => {
      expect(apiRequestMock).toHaveBeenCalledWith('/admin/ops/system-events', {
        params: expect.objectContaining({
          eventType: 'CODEX_RUNTIME_BATCH_RECOVERY',
          entityRef: 'account-group:1',
        }),
      })
    })
  })
})
