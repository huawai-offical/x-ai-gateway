// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '@/components/app/confirm-provider'
import { apiRequest } from '../../lib/api'
import { WebhooksPage } from './webhooks-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(),
}))

const mockedApiRequest = apiRequest as unknown as ReturnType<typeof vi.fn>

mockedApiRequest.mockImplementation(async (url: string, init?: RequestInit) => {
  if (url === '/admin/integrations/webhooks') {
    if (init?.method === 'POST') {
      return { id: 2, endpointName: 'ops-webhook', endpointUrl: 'https://example.com/hook', signingMode: 'HMAC_SHA256', timeoutMs: 5000, enabled: true, secretFingerprint: 'abc123' }
    }
    if (init?.method === 'DELETE') {
      return null
    }
    return [{ id: 1, endpointName: 'primary-webhook', endpointUrl: 'https://example.com/hook', signingMode: 'HMAC_SHA256', timeoutMs: 5000, enabled: true, secretFingerprint: 'abc123' }]
  }
  if (url === '/admin/integrations/webhooks/1' && init?.method === 'DELETE') {
    return null
  }
  throw new Error(`unexpected url: ${url}`)
})

afterEach(() => {
  mockedApiRequest.mockClear()
})

describe('WebhooksPage', () => {
  it('renders configured webhooks and can create one', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmProvider>
          <WebhooksPage />
        </ConfirmProvider>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('已配置回调终端')).toBeInTheDocument()
    expect(await screen.findByText('primary-webhook')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '创建终端' }))

    fireEvent.change(screen.getByPlaceholderText('请输入终端名称'), { target: { value: 'ops-webhook' } })
    fireEvent.change(screen.getByPlaceholderText('https://example.com/hook'), { target: { value: 'https://example.com/hook' } })
    fireEvent.click(screen.getByRole('button', { name: '下一步' }))
    fireEvent.change(await screen.findByPlaceholderText('可选密钥'), { target: { value: 'secret-1' } })
    fireEvent.click(screen.getByRole('button', { name: '创建终端' }))

    await waitFor(() => {
      const call = mockedApiRequest.mock.calls.find(
        ([url, init]) => url === '/admin/integrations/webhooks' && init?.method === 'POST',
      )
      expect(call).toBeTruthy()
      expect(JSON.parse(call?.[1]?.body as string).endpointName).toBe('ops-webhook')
    })

    fireEvent.click(await screen.findByRole('button', { name: '删除' }))
    const confirmDialog = await screen.findByRole('dialog', { name: '删除回调终端' })
    fireEvent.click(within(confirmDialog).getByRole('button', { name: '删除' }))

    await waitFor(() => {
      expect(mockedApiRequest).toHaveBeenCalledWith(
        '/admin/integrations/webhooks/1',
        expect.objectContaining({ method: 'DELETE' }),
      )
    })
  })
})
