// @vitest-environment jsdom
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
  if (url === '/admin/integrations/channels/1' && init?.method === 'DELETE') {
    return null
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

    expect(await screen.findByRole('heading', { name: '已配置通道' })).toBeInTheDocument()
    expect(await screen.findByText('ops-email')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '创建通道' }))

    fireEvent.change(screen.getByPlaceholderText('请输入通道名称'), { target: { value: 'ops-im' } })
    fireEvent.click(screen.getByRole('combobox', { name: '通道类型' }))
    fireEvent.click(await screen.findByText('即时消息回调'))
    fireEvent.click(screen.getByRole('button', { name: '下一步' }))
    fireEvent.click(await screen.findByRole('combobox', { name: '回调终端' }))
    fireEvent.click(await screen.findByText('primary-webhook'))
    fireEvent.click(screen.getByRole('button', { name: '创建通道' }))

    await waitFor(() => {
      const call = mockedApiRequest.mock.calls.find(
        ([url, init]) => url === '/admin/integrations/channels' && init?.method === 'POST',
      )
      expect(call).toBeTruthy()
      expect(JSON.parse(call?.[1]?.body as string).channelType).toBe('IM_WEBHOOK')
    })

    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    fireEvent.click(await screen.findByRole('button', { name: '删除' }))

    await waitFor(() => {
      expect(mockedApiRequest).toHaveBeenCalledWith(
        '/admin/integrations/channels/1',
        expect.objectContaining({ method: 'DELETE' }),
      )
    })
    confirmSpy.mockRestore()
  })
})
