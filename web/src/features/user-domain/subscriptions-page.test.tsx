// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '@/components/app/confirm-provider'
import { SubscriptionsPage } from './subscriptions-page'

const { apiRequestMock } = vi.hoisted(() => ({
  apiRequestMock: vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/admin/users?active=true' && !init?.method) {
      return [
        { id: 1, email: 'user@example.com', displayName: 'Demo', active: true },
      ]
    }
    if (url === '/admin/plans?active=true' && !init?.method) {
      return [
        { id: 2, planName: 'starter', active: true },
      ]
    }
    if (url.startsWith('/admin/subscriptions') && !init?.method) {
      return []
    }
    if (url === '/admin/subscriptions' && init?.method === 'POST') {
      return {
        id: 1,
        userId: 1,
        userEmail: 'user@example.com',
        planId: 2,
        planName: 'starter',
        status: 'ACTIVE',
        startsAt: '2026-04-23T00:00:00Z',
        expiresAt: '2026-05-23T00:00:00Z',
        autoRenew: false,
        notes: null,
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

describe('SubscriptionsPage', () => {
  it('renders page and creates subscription in dialog', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmProvider>
          <SubscriptionsPage />
        </ConfirmProvider>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('订阅关系')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '创建订阅' }))

    const submitButton = screen.getAllByRole('button', { name: '创建订阅' }).at(-1)
    expect(submitButton).toBeDefined()
    fireEvent.click(submitButton!)

    await waitFor(() => {
      expect(apiRequestMock).toHaveBeenCalledWith(
        '/admin/subscriptions',
        expect.objectContaining({
          method: 'POST',
        }),
      )
    })
  })
})
