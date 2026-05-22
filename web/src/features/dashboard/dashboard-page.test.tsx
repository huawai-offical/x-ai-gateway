// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../lib/api'
import { DashboardPage } from './dashboard-page'

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
    if (url === '/admin/dashboard/overview') {
      return {
        sampledFrom: '2026-04-23T08:00:00Z',
        sampledTo: '2026-04-23T12:00:00Z',
        bucketMinutes: 15,
        summary: {
          routeDecisionCount: 180,
          cacheHitCount: 72,
          activeCacheReferenceCount: 12,
          usageRecordCount: 140,
          finalUsageRecordCount: 120,
          partialUsageRecordCount: 20,
          totalCacheHitTokens: 36000,
          totalCacheWriteTokens: 2200,
          totalSavedInputTokens: 28000,
          cacheHitRatio: 0.4,
          averageSavedInputTokensPerHit: 388.9,
        },
        providerRanking: [
          { key: 'OPENAI_DIRECT', count: 120, cacheHitTokens: 20000, cacheWriteTokens: 1500, savedInputTokens: 16000 },
        ],
        protocolRanking: [
          { key: 'openai', count: 180, cacheHitTokens: 36000, cacheWriteTokens: 2200, savedInputTokens: 28000 },
        ],
        modelGroupRanking: [
          { key: 'gpt-4o', count: 96, cacheHitTokens: 18000, cacheWriteTokens: 1200, savedInputTokens: 14000 },
        ],
        selectionSourceRanking: [
          { key: 'PREFIX_AFFINITY', count: 100, cacheHitTokens: 0, cacheWriteTokens: 0, savedInputTokens: 0 },
        ],
        cacheSourceRanking: [
          { key: 'native_cache', count: 72, cacheHitTokens: 36000, cacheWriteTokens: 2200, savedInputTokens: 28000 },
        ],
        usageCompletenessBreakdown: [
          { key: 'FINAL', count: 120 },
          { key: 'PARTIAL', count: 20 },
        ],
        credentialRanking: [
          {
            credentialId: 3,
            displayKey: 'OPENAI_DIRECT#3',
            baseUrl: 'https://api.openai.com',
            providerType: 'OPENAI_DIRECT',
            routeDecisionCount: 88,
            cacheHitCount: 40,
            cacheHitTokens: 20000,
            cacheWriteTokens: 1200,
            savedInputTokens: 15000,
          },
        ],
        alerts: [
          {
            severity: 'WARN',
            code: 'LOW_CACHE_HIT_RATIO',
            title: '缓存命中率偏低',
            detail: '当前窗口命中率低于预期阈值。',
            affectedEntities: ['OPENAI_DIRECT'],
            suspectedCauses: ['请求前缀波动'],
            suggestedActions: ['检查前缀稳定性'],
          },
        ],
        timeline: [
          {
            bucketStart: '2026-04-23T11:15:00Z',
            routeDecisionCount: 42,
            cacheHitCount: 16,
            cacheHitTokens: 8000,
            cacheWriteTokens: 600,
            savedInputTokens: 6200,
            usageRecordCount: 36,
            totalTokens: 24000,
            failedRequestCount: 2,
            p95LatencyMs: 680,
          },
          {
            bucketStart: '2026-04-23T11:30:00Z',
            routeDecisionCount: 48,
            cacheHitCount: 22,
            cacheHitTokens: 10000,
            cacheWriteTokens: 700,
            savedInputTokens: 7800,
            usageRecordCount: 40,
            totalTokens: 26000,
            failedRequestCount: 1,
            p95LatencyMs: 640,
          },
        ],
        recentRouteDecisions: [
          {
            id: 1,
            requestId: 'req-1',
            requestedModel: 'gpt-4o-mini',
            publicModel: 'chat-fast',
            resolvedModelKey: 'gpt-4o-mini',
            selectedProviderType: 'OPENAI_DIRECT',
            selectionSource: 'PREFIX_AFFINITY',
            createdAt: '2026-04-23T11:40:00Z',
          },
        ],
        recentCacheHits: [
          {
            id: 1,
            requestId: 'req-1',
            providerType: 'OPENAI_DIRECT',
            cacheKind: 'native_cache',
            cacheHitTokens: 2000,
            cacheWriteTokens: 100,
            savedInputTokens: 1800,
            createdAt: '2026-04-23T11:40:02Z',
          },
        ],
        activeUpstreamCacheReferences: [
          {
            id: 1,
            providerType: 'OPENAI_DIRECT',
            credentialId: 3,
            modelGroup: 'gpt-4o',
            externalCacheRef: 'cache-ref-1',
            status: 'ACTIVE',
            expireAt: '2026-04-23T12:30:00Z',
            lastUsedAt: '2026-04-23T11:58:00Z',
          },
        ],
        expiringUpstreamCacheReferences: [],
      }
    }
    throw new Error(`unexpected get url: ${url}`)
  })
})

afterEach(() => {
  mockedApiGet.mockReset()
})

describe('DashboardPage', () => {
  it('renders role collaboration and batch trust sections', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('角色协同视图')).toBeInTheDocument()
    expect(screen.queryByText('总览指标已经收口到智能运维总览主面板。')).not.toBeInTheDocument()
    expect(await screen.findByText('接入管理员')).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: '返回智能运维总览' })).toHaveAttribute('href', '/console/ops')
    expect(await screen.findByText('批量操作可信状态')).toBeInTheDocument()
    expect(await screen.findByText('预检就绪')).toBeInTheDocument()
    expect(await screen.findByText('按请求 ID 定位')).toBeInTheDocument()
  })
})
