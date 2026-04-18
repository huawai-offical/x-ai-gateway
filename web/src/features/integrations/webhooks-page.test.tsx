// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
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
    return [{ id: 1, endpointName: 'primary-webhook', endpointUrl: 'https://example.com/hook', signingMode: 'HMAC_SHA256', timeoutMs: 5000, enabled: true, secretFingerprint: 'abc123' }]
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
        <WebhooksPage />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('统一 webhook 出口')).toBeInTheDocument()
    expect(await screen.findByText('primary-webhook')).toBeInTheDocument()

    fireEvent.change(screen.getByPlaceholderText('endpoint name'), { target: { value: 'ops-webhook' } })
    fireEvent.change(screen.getByPlaceholderText('https://example.com/hook'), { target: { value: 'https://example.com/hook' } })
    fireEvent.change(screen.getByPlaceholderText('secret (optional)'), { target: { value: 'secret-1' } })
    fireEvent.click(screen.getByRole('button', { name: '创建 endpoint' }))

    await waitFor(() => {
      const call = mockedApiRequest.mock.calls.find(
        ([url, init]) => url === '/admin/integrations/webhooks' && init?.method === 'POST',
      )
      expect(call).toBeTruthy()
      expect(JSON.parse(call?.[1]?.body as string).endpointName).toBe('ops-webhook')
    })
  })
})
