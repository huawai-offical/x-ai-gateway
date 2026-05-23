// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '@/components/app/confirm-provider'
import { apiRequest } from '../../lib/api'
import { TlsProfilesPage } from './tls-profiles-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(),
}))

const mockedApiRequest = apiRequest as unknown as ReturnType<typeof vi.fn>

mockedApiRequest.mockImplementation(async (url: string, init?: RequestInit) => {
  if (url === '/admin/network/tls-profiles') {
    if (init?.method === 'POST') {
      return {
        id: 2,
        profileName: 'Android TLS',
        profileCode: 'android-tls',
        settingsJson: init.body ? JSON.parse(String(init.body)).settingsJson : null,
        active: true,
      }
    }
    return [
      {
        id: 1,
        profileName: 'Chrome TLS',
        profileCode: 'chrome-like',
        settingsJson: '{"headers":{"user-agent":"Chrome"}}',
        active: true,
        updatedAt: '2026-04-23T12:00:00Z',
      },
    ]
  }
  if (url === '/admin/network/tls-profiles/1' && init?.method === 'DELETE') {
    return null
  }
  throw new Error(`unexpected url: ${url}`)
})

afterEach(() => {
  mockedApiRequest.mockClear()
})

describe('TlsProfilesPage', () => {
  it('renders tls profile table and supports create/delete', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmProvider>
          <TlsProfilesPage />
        </ConfirmProvider>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('TLS 指纹画像')).toBeInTheDocument()
    expect(await screen.findByText('Chrome TLS')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '新增画像' }))
    fireEvent.change(screen.getByRole('combobox', { name: '常用画像' }), { target: { value: 'codex-cli' } })
    fireEvent.click(screen.getByRole('button', { name: '下一步' }))
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() => {
      const call = mockedApiRequest.mock.calls.find(([url, init]) => url === '/admin/network/tls-profiles' && init?.method === 'POST')
      expect(call).toBeTruthy()
      expect(JSON.parse(String(call?.[1]?.body)).settingsJson).toContain('codex_cli_rs')
      expect(JSON.parse(String(call?.[1]?.body)).settingsJson).toContain('x-client-family')
      expect(mockedApiRequest).toHaveBeenCalledWith(
        '/admin/network/tls-profiles',
        expect.objectContaining({ method: 'POST' }),
      )
    })

    fireEvent.click(await screen.findByRole('button', { name: '删除' }))
    const confirmDialog = await screen.findByRole('dialog', { name: '删除 TLS 指纹画像' })
    fireEvent.click(within(confirmDialog).getByRole('button', { name: '删除' }))
    await waitFor(() => {
      expect(mockedApiRequest).toHaveBeenCalledWith(
        '/admin/network/tls-profiles/1',
        expect.objectContaining({ method: 'DELETE' }),
      )
    })
  })
})
