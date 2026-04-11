// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { OauthConnectPage } from './oauth-connect-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(async () => ({ authorizationUrl: 'https://example.com' })),
}))

describe('OauthConnectPage', () => {
  it('renders connect action', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/accounts/connect/openai_oauth?poolId=1']}>
          <Routes>
            <Route path="/accounts/connect/:provider" element={<OauthConnectPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(screen.getByText('开始授权')).toBeInTheDocument()
  })
})
