// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { KeysPage } from './keys-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(async () => []),
}))

describe('KeysPage', () => {
  it('renders distributed key heading', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <KeysPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('策略对象列表')).toBeInTheDocument()
  })
})
