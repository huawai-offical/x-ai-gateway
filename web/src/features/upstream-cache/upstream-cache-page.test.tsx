// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../lib/api'
import { UpstreamCachePage } from './upstream-cache-page'

vi.mock('../../lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/api')>()
  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      get: vi.fn(),
    },
  }
})

const mockedApiGet = apiClient.get as unknown as ReturnType<typeof vi.fn>

beforeEach(() => {
  mockedApiGet.mockImplementation(async (url: string) => {
    if (url === '/admin/observability/summary') {
      return {
        sampledFrom: '2026-05-01T00:00:00Z',
        sampledTo: '2026-05-01T01:00:00Z',
        sampledRouteDecisionCount: 10,
        sampledCacheHitCount: 4,
        sampledActiveUpstreamCacheReferenceCount: 1,
        sampledUsageRecordCount: 10,
        sampledFinalUsageRecordCount: 9,
        sampledPartialUsageRecordCount: 1,
        totalCacheHitTokens: 100,
        totalCacheWriteTokens: 20,
        totalSavedInputTokens: 80,
      }
    }
    if (url === '/admin/observability/upstream-cache-references') {
      return [
        {
          id: 1,
          providerType: 'GEMINI_DIRECT',
          credentialId: 11,
          modelGroup: 'gemini-2.5-pro',
          externalCacheRef: 'cachedContents/demo',
          status: 'ACTIVE',
          effectiveStatus: 'EXPIRED',
          expired: true,
          active: false,
          expireAt: '2026-05-01T00:30:00Z',
          lastUsedAt: '2026-05-01T00:20:00Z',
          lifecycle: {
            status: 'ACTIVE',
            effective_status: 'EXPIRED',
            expired: true,
            active: false,
            expire_at: '2026-05-01T00:30:00Z',
            last_used_at: '2026-05-01T00:20:00Z',
          },
        },
      ]
    }
    if (url === '/admin/observability/cache-hits') {
      return []
    }
    throw new Error(`unexpected get url: ${url}`)
  })
})

afterEach(() => {
  mockedApiGet.mockReset()
})

describe('UpstreamCachePage', () => {
  it('在缓存引用详情弹窗结构化展示 lifecycle', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <UpstreamCachePage />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('cachedContents/demo')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '详情' }))

    expect(await screen.findByText('有效状态')).toBeInTheDocument()
    expect((await screen.findAllByText('活跃 / 过期')).length).toBeGreaterThan(0)
    expect(await screen.findByText('否 / 是')).toBeInTheDocument()
  })
})
