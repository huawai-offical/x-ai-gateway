// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '@/lib/api'
import { PublicHomePage } from './public-home-page'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    apiRequest: vi.fn(),
  }
})

const mockedApiRequest = apiRequest as unknown as ReturnType<typeof vi.fn>

afterEach(() => {
  cleanup()
  mockedApiRequest.mockReset()
})

function renderWithProviders(element: ReactElement) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>{element}</MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('Public pages', () => {
  it('renders the public home as the visitor entry', () => {
    renderWithProviders(<PublicHomePage />)

    expect(screen.getByRole('heading', { name: 'x-ai-gateway' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /客户登录/i })).toHaveAttribute('href', '/portal/login')
    expect(screen.getByRole('link', { name: /进入控制台/i })).toHaveAttribute('href', '/console')
    expect(screen.queryByRole('link', { name: '接口文档' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '价格' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '状态' })).not.toBeInTheDocument()
  })
})
