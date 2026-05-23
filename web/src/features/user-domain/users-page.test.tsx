// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '@/components/app/confirm-provider'
import { UsersPage } from './users-page'

const { apiRequestMock } = vi.hoisted(() => ({
  apiRequestMock: vi.fn(async (url: string, init?: RequestInit) => {
    if (url.startsWith('/admin/users') && !init?.method) {
      return []
    }
    if (url === '/admin/users' && init?.method === 'POST') {
      return {
        id: 1,
        email: 'user@example.com',
        displayName: 'Demo',
        active: true,
        subscriptionCount: 0,
      }
    }
    return []
  }),
}))

vi.mock('../../lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/api')>()
  return {
    ...actual,
    apiRequest: apiRequestMock,
  }
})

afterEach(() => {
  cleanup()
})

describe('UsersPage', () => {
  it('renders page and creates user in dialog', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmProvider>
          <UsersPage />
        </ConfirmProvider>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('用户清单')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '创建用户' }))
    fireEvent.change(screen.getByPlaceholderText('例如：user@example.com'), {
      target: { value: 'user@example.com' },
    })

    const submitButton = screen.getAllByRole('button', { name: '创建用户' }).at(-1)
    expect(submitButton).toBeDefined()
    fireEvent.click(submitButton!)

    await waitFor(() => {
      expect(apiRequestMock).toHaveBeenCalledWith(
        '/admin/users',
        expect.objectContaining({
          method: 'POST',
        }),
      )
    })
  })
})
