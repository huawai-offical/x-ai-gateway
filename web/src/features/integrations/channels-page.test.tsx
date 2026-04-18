// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../../lib/api'
import { ChannelsPage } from './channels-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(),
}))

const mockedApiRequest = apiRequest as unknown as ReturnType<typeof vi.fn>

mockedApiRequest.mockImplementation(async (url: string, init?: RequestInit) => {
  if (url === '/admin/integrations/webhooks') {
    return [{ id: 1, endpointName: 'primary-webhook', endpointUrl: 'https://example.com/hook', signingMode: 'HMAC_SHA256', timeoutMs: 5000, enabled: true }]
  }
  if (url === '/admin/integrations/channels') {
    if (init?.method === 'POST') {
      return { id: 2, channelName: 'ops-im', channelType: 'IM_WEBHOOK', webhookEndpointId: 1, templateMode: 'DEFAULT', enabled: true }
    }
    return [{ id: 1, channelName: 'ops-email', channelType: 'EMAIL', emailTo: 'ops@example.com', templateMode: 'DEFAULT', enabled: true }]
  }
  throw new Error(`unexpected url: ${url}`)
})

afterEach(() => {
  mockedApiRequest.mockClear()
})

describe('ChannelsPage', () => {
  it('renders channels and can create a webhook style channel', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ChannelsPage />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('通知通道')).toBeInTheDocument()
    expect(await screen.findByText('ops-email')).toBeInTheDocument()

    fireEvent.change(screen.getByPlaceholderText('channel name'), { target: { value: 'ops-im' } })
    fireEvent.change(screen.getByLabelText('channel type'), { target: { value: 'IM_WEBHOOK' } })
    fireEvent.change(screen.getByLabelText('webhook endpoint'), { target: { value: '1' } })
    fireEvent.click(screen.getByRole('button', { name: '创建 channel' }))

    await waitFor(() => {
      const call = mockedApiRequest.mock.calls.find(
        ([url, init]) => url === '/admin/integrations/channels' && init?.method === 'POST',
      )
      expect(call).toBeTruthy()
      expect(JSON.parse(call?.[1]?.body as string).channelType).toBe('IM_WEBHOOK')
    })
  })
})
