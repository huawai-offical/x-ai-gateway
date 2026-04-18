// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../../lib/api'
import { SubscriptionsPage } from './subscriptions-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(),
}))

const mockedApiRequest = apiRequest as unknown as ReturnType<typeof vi.fn>

mockedApiRequest.mockImplementation(async (url: string, init?: RequestInit) => {
  if (url === '/admin/integrations/channels') {
    return [{ id: 1, channelName: 'ops-webhook', channelType: 'WEBHOOK', webhookEndpointId: 1, templateMode: 'DEFAULT', enabled: true }]
  }
  if (url === '/admin/integrations/subscriptions') {
    if (init?.method === 'POST') {
      return { id: 2, subscriptionName: 'alert-opened', channelId: 1, eventType: 'ALERT_OPENED', severity: 'HIGH', entityType: 'CREDENTIAL', enabled: true }
    }
    return [{ id: 1, subscriptionName: 'budget-exceeded', channelId: 1, eventType: 'BUDGET_EXCEEDED', severity: 'HIGH', entityType: 'DISTRIBUTED_KEY', enabled: true }]
  }
  throw new Error(`unexpected url: ${url}`)
})

afterEach(() => {
  mockedApiRequest.mockClear()
})

describe('SubscriptionsPage', () => {
  it('renders subscriptions and can create one', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <SubscriptionsPage />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('事件订阅')).toBeInTheDocument()
    expect(await screen.findByText('budget-exceeded')).toBeInTheDocument()

    fireEvent.change(screen.getByPlaceholderText('subscription name'), { target: { value: 'alert-opened' } })
    fireEvent.change(screen.getByLabelText('channel id'), { target: { value: '1' } })
    fireEvent.click(screen.getByRole('button', { name: '创建订阅' }))

    await waitFor(() => {
      const call = mockedApiRequest.mock.calls.find(
        ([url, init]) => url === '/admin/integrations/subscriptions' && init?.method === 'POST',
      )
      expect(call).toBeTruthy()
      expect(JSON.parse(call?.[1]?.body as string).subscriptionName).toBe('alert-opened')
    })
  })
})
