// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { WindowsPage } from './windows-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(async () => [
    {
      id: 2,
      windowName: '夜间窗口',
      startsAt: '2026-04-18T22:00:00Z',
      endsAt: '2026-04-18T23:00:00Z',
      enabled: true,
      activeNow: true,
      description: 'night window',
    },
  ]),
}))

describe('WindowsPage', () => {
  it('renders maintenance windows', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <WindowsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('维护窗口')).toBeInTheDocument()
    expect(await screen.findByText('夜间窗口')).toBeInTheDocument()
    expect(await screen.findByText('当前命中')).toBeInTheDocument()
  })
})
