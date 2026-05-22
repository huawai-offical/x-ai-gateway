// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { ErrorRulesPage } from './error-rules-page'

describe('ErrorRulesPage', () => {
  it('redirects to governance error policies tab', async () => {
    const router = createMemoryRouter(
      [
        { path: '/error-rules', element: <ErrorRulesPage /> },
        { path: '/ops/governance', element: <div>已跳转到治理策略</div> },
      ],
      { initialEntries: ['/error-rules'] },
    )

    render(
      <QueryClientProvider client={new QueryClient()}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('已跳转到治理策略')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?tab=error-policies')
  })
})
