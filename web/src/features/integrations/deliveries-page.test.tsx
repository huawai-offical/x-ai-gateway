// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../../lib/api'
import { DeliveriesPage } from './deliveries-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(),
}))

const mockedApiRequest = apiRequest as unknown as ReturnType<typeof vi.fn>

mockedApiRequest.mockImplementation(async (url: string, init?: RequestInit) => {
  if (url === '/admin/integrations/channels') {
    return [{ id: 1, channelName: 'ops-webhook', channelType: 'WEBHOOK', webhookEndpointId: 1, templateMode: 'DEFAULT', enabled: true }]
  }
  if (url === '/admin/integrations/deliveries') {
    return [{ id: 1, eventId: 'evt-1', eventType: 'ALERT_OPENED', channelId: 1, entityType: 'CREDENTIAL', entityRef: '101', requestId: 'req-1', deliveryStatus: 'FAILED', attemptCount: 2, payloadJson: '{"summary":"credential unstable"}', occurredAt: '2026-04-18T02:00:00Z', lastError: 'timeout' }]
  }
  if (url === '/admin/integrations/deliveries/1/replay' && init?.method === 'POST') {
    return { id: 1, eventId: 'evt-1', eventType: 'ALERT_OPENED', channelId: 1, entityType: 'CREDENTIAL', entityRef: '101', requestId: 'req-1', deliveryStatus: 'PENDING', attemptCount: 3, payloadJson: '{"summary":"credential unstable"}', occurredAt: '2026-04-18T02:00:00Z' }
  }
  if (url === '/admin/integrations/test-delivery' && init?.method === 'POST') {
    return { id: 2, eventId: 'evt-2', eventType: 'ALERT_OPENED', channelId: 1, entityType: 'SYSTEM', entityRef: 'test', deliveryStatus: 'PENDING', attemptCount: 1, payloadJson: '{"summary":"manual test delivery"}', occurredAt: '2026-04-18T02:05:00Z' }
  }
  throw new Error(`unexpected url: ${url}`)
})

afterEach(() => {
  mockedApiRequest.mockClear()
})

describe('DeliveriesPage', () => {
  it('renders delivery history and supports replay + test delivery', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <DeliveriesPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('投递记录、重试与重放')).toBeInTheDocument()
    expect(await screen.findByText('timeout')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('test channel'), { target: { value: '1' } })
    fireEvent.click(screen.getByRole('button', { name: '发送测试投递' }))
    fireEvent.click(screen.getByRole('button', { name: '重放' }))

    await waitFor(() => {
      expect(mockedApiRequest).toHaveBeenCalledWith('/admin/integrations/test-delivery', expect.objectContaining({ method: 'POST' }))
      expect(mockedApiRequest).toHaveBeenCalledWith('/admin/integrations/deliveries/1/replay', expect.objectContaining({ method: 'POST' }))
    })
  })
})
