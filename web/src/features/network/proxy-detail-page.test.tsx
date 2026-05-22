// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../../lib/api'
import { ProxyDetailPage } from './proxy-detail-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(),
}))

const mockedApiRequest = apiRequest as unknown as ReturnType<typeof vi.fn>

mockedApiRequest.mockImplementation(async (url: string) => {
  if (url === '/admin/network/proxies') {
    return [
      {
        id: 1,
        proxyName: 'proxy-bj-1',
        proxyUrl: 'https://proxy-1.example.com',
        active: true,
        description: '北京出口代理',
      },
    ]
  }
  throw new Error(`unexpected url: ${url}`)
})

afterEach(() => {
  mockedApiRequest.mockClear()
})

describe('ProxyDetailPage', () => {
  it('renders proxy details without probe actions or history', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/network/proxies/1']}>
          <Routes>
            <Route path="/network/proxies/:id" element={<ProxyDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('proxy-bj-1')).toBeInTheDocument()
    expect(screen.getByText('https://proxy-1.example.com')).toBeInTheDocument()
    expect(screen.getByText('北京出口代理')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '手动拨测' })).not.toBeInTheDocument()
    expect(screen.queryByText('拨测历史')).not.toBeInTheDocument()
  })
})
