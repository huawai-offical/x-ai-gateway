// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AccountPoolDetailPage } from './account-pool-detail-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(async (url: string) => {
    if (url.startsWith('/admin/account-pools/')) {
      return { id: 1, poolName: 'OpenAI Pool', providerType: 'OPENAI_OAUTH' }
    }
    return []
  }),
}))

describe('AccountPoolDetailPage', () => {
  it('renders pool detail heading', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/account-pools/1']}>
          <Routes>
            <Route path="/account-pools/:id" element={<AccountPoolDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('OpenAI Pool')).toBeInTheDocument()
  })
})
