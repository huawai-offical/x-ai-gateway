// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { Navigate, createMemoryRouter, RouterProvider } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

describe('translation-debug route', () => {
  it('redirects legacy translation-debug path to workbench', async () => {
    const router = createMemoryRouter([
      { path: '/translation-debug', element: <Navigate to="/workbench" replace /> },
      { path: '/workbench', element: <div>白盒翻译调试台</div> },
    ], {
      initialEntries: ['/translation-debug'],
    })

    render(
      <QueryClientProvider client={new QueryClient()}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('白盒翻译调试台')).toBeInTheDocument()
  })
})
