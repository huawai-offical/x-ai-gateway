// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { InvitationCodesPage } from './invitation-codes-page'

class MockApiError extends Error {
  readonly traceId?: string | null

  constructor(message: string, traceId?: string | null) {
    super(message)
    this.name = 'ApiError'
    this.traceId = traceId
  }
}

const { apiRequestMock } = vi.hoisted(() => ({
  apiRequestMock: vi.fn(async (url: string) => {
    if (url === '/admin/invitation-codes/leaderboard?limit=20') {
      return [
        {
          userId: 1,
          email: 'root@example.com',
          displayName: '根用户',
          directInviteCount: 1,
          totalInviteCount: 2,
          referrerRewardTokenCredits: 600,
          latestInviteAt: '2026-05-24T10:00:00Z',
        },
      ]
    }
    if (url === '/admin/invitation-codes/tree/1?maxDepth=5') {
      return {
        userId: 1,
        email: 'root@example.com',
        displayName: '根用户',
        depth: 0,
        invitedAt: null,
        children: [
          {
            userId: 2,
            email: 'child@example.com',
            displayName: '下级用户',
            depth: 1,
            invitedAt: '2026-05-24T10:00:00Z',
            children: [],
          },
        ],
      }
    }
    if (url === '/admin/invitation-codes/tree/999999?maxDepth=5') {
      throw new MockApiError('未找到指定用户。', 'trace-tree-missing')
    }
    if (url.startsWith('/admin/invitation-codes')) {
      return []
    }
    if (url.startsWith('/admin/plans')) {
      return []
    }
    if (url.startsWith('/admin/access-groups')) {
      return []
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
  apiRequestMock.mockClear()
})

function renderPage() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <InvitationCodesPage />
    </QueryClientProvider>,
  )
}

describe('InvitationCodesPage', () => {
  it('loads invitation tree from manual root user query', async () => {
    renderPage()

    expect(await screen.findByText('邀请树')).toBeInTheDocument()
    fireEvent.change(screen.getByPlaceholderText('输入邀请人用户 ID'), {
      target: { value: '1' },
    })
    fireEvent.click(screen.getByRole('button', { name: '查询' }))

    await waitFor(() => {
      expect(apiRequestMock).toHaveBeenCalledWith('/admin/invitation-codes/tree/1?maxDepth=5')
    })
    expect(await screen.findByText(/下级用户/)).toBeInTheDocument()
    expect(await screen.findByText(/累计下级 1 人/)).toBeInTheDocument()
  })

  it('opens invitation tree from leaderboard row', async () => {
    renderPage()

    expect(await screen.findByText('根用户')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '邀请树' }))

    await waitFor(() => {
      expect(apiRequestMock).toHaveBeenCalledWith('/admin/invitation-codes/tree/1?maxDepth=5')
    })
    expect(await screen.findByText(/下级用户/)).toBeInTheDocument()
  })

  it('shows visible error content when invitation tree query fails', async () => {
    renderPage()

    expect(await screen.findByText('邀请树')).toBeInTheDocument()
    fireEvent.change(screen.getByPlaceholderText('输入邀请人用户 ID'), {
      target: { value: '999999' },
    })
    fireEvent.click(screen.getByRole('button', { name: '查询' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('邀请树加载失败')
    expect(screen.getByRole('alert')).toHaveTextContent('未找到指定用户。')
    expect(screen.getByRole('alert')).toHaveTextContent('traceId: trace-tree-missing')
  })

  it('shows visible validation error before sending invalid tree query', async () => {
    renderPage()

    expect(await screen.findByText('邀请树')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '查询' }))

    expect(screen.getByRole('alert')).toHaveTextContent('邀请树查询失败')
    expect(screen.getByRole('alert')).toHaveTextContent('根用户 ID不能为空。')
    expect(apiRequestMock).not.toHaveBeenCalledWith('/admin/invitation-codes/tree/?maxDepth=5')
  })
})
