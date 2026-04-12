// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ErrorRulesPage } from './error-rules-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(async () => []),
}))

describe('ErrorRulesPage', () => {
  it('renders error rules heading', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <ErrorRulesPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('结构化错误规则中心')).toBeInTheDocument()
  })
})
