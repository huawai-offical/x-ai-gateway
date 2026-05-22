// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../../lib/api'
import { ProxiesPage } from './proxies-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(),
}))

const mockedApiRequest = apiRequest as unknown as ReturnType<typeof vi.fn>

mockedApiRequest.mockImplementation(async (url: string, init?: RequestInit) => {
  if (url === '/admin/network/proxies') {
    if (init?.method === 'POST') {
      return {
        id: 2,
        proxyName: 'proxy-bj-2',
        proxyUrl: 'https://proxy-2.example.com',
        active: true,
      }
    }
    return [
      {
        id: 1,
        proxyName: 'proxy-bj-1',
        proxyUrl: 'https://proxy-1.example.com',
        active: true,
      },
    ]
  }
  if (url === '/admin/network/proxies/1' && init?.method === 'DELETE') {
    return null
  }
  throw new Error(`unexpected url: ${url}`)
})

afterEach(() => {
  mockedApiRequest.mockClear()
})

describe('ProxiesPage', () => {
  it('renders proxy table and supports create/delete', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <ProxiesPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('代理池')).toBeInTheDocument()
    expect(await screen.findByText('proxy-bj-1')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '新增代理' }))
    fireEvent.change(screen.getByPlaceholderText('代理名称'), { target: { value: 'proxy-bj-2' } })
    fireEvent.change(screen.getByPlaceholderText('socks5://127.0.0.1:1080'), { target: { value: 'https://proxy-2.example.com' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() => {
      expect(mockedApiRequest).toHaveBeenCalledWith(
        '/admin/network/proxies',
        expect.objectContaining({ method: 'POST' }),
      )
    })

    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    fireEvent.click(await screen.findByRole('button', { name: '删除' }))
    await waitFor(() => {
      expect(mockedApiRequest).toHaveBeenCalledWith(
        '/admin/network/proxies/1',
        expect.objectContaining({ method: 'DELETE' }),
      )
    })
    confirmSpy.mockRestore()
  })
})
