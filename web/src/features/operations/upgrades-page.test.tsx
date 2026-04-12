// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { UpgradesPage } from './upgrades-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(async (url: string) => (url.includes('/releases') ? [] : [])),
}))

describe('UpgradesPage', () => {
  it('renders upgrades heading', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <UpgradesPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('升级任务')).toBeInTheDocument()
  })
})
