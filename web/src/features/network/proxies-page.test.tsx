// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ProxiesPage } from './proxies-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(async () => []),
}))

describe('ProxiesPage', () => {
  it('renders proxy form', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <ProxiesPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('代理池')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('代理名称')).toBeInTheDocument()
  })
})
