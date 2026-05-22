// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { PlansPage } from './plans-page'

const { apiRequestMock } = vi.hoisted(() => ({
  apiRequestMock: vi.fn(async (url: string, init?: RequestInit) => {
    if (url.startsWith('/admin/plans') && !init?.method) {
      return []
    }
    if (url === '/admin/plans' && init?.method === 'POST') {
      return {
        id: 1,
        planName: 'starter',
        active: true,
        description: null,
        defaultDurationDays: 30,
        maxActiveKeys: 3,
        rpmLimit: 60,
        tpmLimit: 120000,
        concurrencyLimit: 2,
        dailyTokenLimit: 1000000,
        activeSubscriptionCount: 0,
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

describe('PlansPage', () => {
  it('renders page and creates plan in dialog', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <PlansPage />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('套餐管理')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '创建套餐' }))
    fireEvent.change(screen.getByPlaceholderText('例如：starter'), {
      target: { value: 'starter' },
    })

    const submitButton = screen.getAllByRole('button', { name: '创建套餐' }).at(-1)
    expect(submitButton).toBeDefined()
    fireEvent.click(submitButton!)

    await waitFor(() => {
      expect(apiRequestMock).toHaveBeenCalledWith(
        '/admin/plans',
        expect.objectContaining({
          method: 'POST',
        }),
      )
    })
  })
})
