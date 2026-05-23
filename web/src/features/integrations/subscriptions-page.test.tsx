// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '@/components/app/confirm-provider'
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
  if (url === '/admin/integrations/subscriptions/1' && init?.method === 'DELETE') {
    return null
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
        <ConfirmProvider>
          <SubscriptionsPage />
        </ConfirmProvider>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('订阅列表')).toBeInTheDocument()
    expect(await screen.findByText('budget-exceeded')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '创建订阅' }))

    fireEvent.change(screen.getByPlaceholderText('请输入订阅名称'), { target: { value: 'alert-opened' } })
    fireEvent.click(screen.getByRole('combobox', { name: '通知通道' }))
    fireEvent.click(await screen.findByText('ops-webhook'))
    fireEvent.click(screen.getByRole('button', { name: '创建订阅' }))

    await waitFor(() => {
      const call = mockedApiRequest.mock.calls.find(
        ([url, init]) => url === '/admin/integrations/subscriptions' && init?.method === 'POST',
      )
      expect(call).toBeTruthy()
      expect(JSON.parse(call?.[1]?.body as string).subscriptionName).toBe('alert-opened')
    })

    fireEvent.click(await screen.findByRole('button', { name: '删除' }))
    const confirmDialog = await screen.findByRole('dialog', { name: '删除订阅' })
    fireEvent.click(within(confirmDialog).getByRole('button', { name: '删除' }))
    await waitFor(() => {
      expect(mockedApiRequest).toHaveBeenCalledWith(
        '/admin/integrations/subscriptions/1',
        expect.objectContaining({ method: 'DELETE' }),
      )
    })
  })
})
