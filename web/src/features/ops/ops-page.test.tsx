// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../lib/api'
import { OpsPage } from './ops-page'

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
    if (url === '/admin/ops/summary') {
      return {
        snapshot: {
          observedAt: '2026-04-20T12:00:00Z',
          qps: 12.6,
          errorRate: 0.08,
          p95LatencyMs: 842,
          providerFailures: 3,
          activeAlerts: 2,
          affectedEntities: ['credential:101', 'site:4'],
        },
        alerts: [
          {
            id: 11,
            eventType: 'REQUEST_ERROR_RATIO',
            title: 'credential unstable',
            severity: 'HIGH',
            status: 'OPEN',
            message: 'error spike',
            entityType: 'CREDENTIAL',
            entityRef: '101',
          },
        ],
        recentLogs: [],
      }
    }

    if (url === '/admin/analytics/overview') {
      return {
        sampledFrom: '2026-04-20T06:00:00Z',
        sampledTo: '2026-04-20T12:00:00Z',
        bucketMinutes: 15,
        sampledRouteDecisionCount: 180,
        sampledCacheHitCount: 72,
        sampledActiveCacheReferenceCount: 8,
        sampledUsageRecordCount: 140,
        sampledFinalUsageRecordCount: 120,
        sampledPartialUsageRecordCount: 20,
        totalCacheHitTokens: 32000,
        totalCacheWriteTokens: 1800,
        totalSavedInputTokens: 24000,
        providerBreakdown: [{ key: 'OPENAI_DIRECT', count: 120, cacheHitTokens: 24000, cacheWriteTokens: 900, savedInputTokens: 18000 }],
        protocolBreakdown: [{ key: 'openai', count: 180, cacheHitTokens: 32000, cacheWriteTokens: 1800, savedInputTokens: 24000 }],
        selectionSourceBreakdown: [{ key: 'PREFIX_AFFINITY', count: 140, cacheHitTokens: 0, cacheWriteTokens: 0, savedInputTokens: 0 }],
        modelGroupBreakdown: [{ key: 'gpt-4o', count: 96, cacheHitTokens: 21000, cacheWriteTokens: 700, savedInputTokens: 16000 }],
        cacheSourceBreakdown: [{ key: 'prompt_cache', count: 72, cacheHitTokens: 32000, cacheWriteTokens: 1800, savedInputTokens: 24000 }],
        usageCompletenessBreakdown: [{ key: 'FINAL', count: 120 }],
        distributedKeyBreakdown: [
          {
            distributedKeyId: 1,
            keyName: 'main-key',
            keyPrefix: 'sk-gw-main',
            routeDecisionCount: 90,
            cacheHitCount: 42,
            cacheHitTokens: 20000,
            cacheWriteTokens: 900,
            savedInputTokens: 15000,
            usageRecordCount: 70,
            finalUsageRecordCount: 64,
            partialUsageRecordCount: 6,
            promptTokens: 16000,
            completionTokens: 9000,
            totalTokens: 25000,
            failedRequestCount: 3,
            avgLatencyMs: 720,
            cacheHitRatio: 0.466,
          },
        ],
        timeline: [
          {
            bucketStart: '2026-04-20T11:15:00Z',
            routeDecisionCount: 48,
            cacheHitCount: 20,
            cacheHitTokens: 9000,
            cacheWriteTokens: 400,
            savedInputTokens: 7000,
            usageRecordCount: 40,
            totalTokens: 18000,
            failedRequestCount: 1,
            p95LatencyMs: 620,
          },
          {
            bucketStart: '2026-04-20T11:30:00Z',
            routeDecisionCount: 54,
            cacheHitCount: 24,
            cacheHitTokens: 11000,
            cacheWriteTokens: 600,
            savedInputTokens: 8000,
            usageRecordCount: 46,
            totalTokens: 21000,
            failedRequestCount: 2,
            p95LatencyMs: 710,
          },
        ],
      }
    }

    if (url === '/admin/observability/health') {
      return {
        sampledFrom: '2026-04-20T06:00:00Z',
        sampledTo: '2026-04-20T12:00:00Z',
        total: {
          totalRequests: 240,
          successfulRequests: 216,
          failedRequests: 18,
          canceledRequests: 6,
          successRate: 0.9,
          availabilityRate: 0.925,
          errorRate: 0.075,
          avgDurationMs: 688,
          lastSuccessfulAt: '2026-04-20T11:58:00Z',
          lastFailedAt: '2026-04-20T11:43:00Z',
        },
        credentials: [
          {
            credentialId: 101,
            providerType: 'OPENAI_DIRECT',
            credentialLabel: 'openai-main',
            credentialPrefix: 'sk-live',
            totalRequests: 120,
            successfulRequests: 111,
            failedRequests: 7,
            canceledRequests: 2,
            successRate: 0.925,
            availabilityRate: 0.942,
            errorRate: 0.058,
            avgDurationMs: 642,
            lastSuccessfulAt: '2026-04-20T11:58:00Z',
            lastFailedAt: '2026-04-20T11:43:00Z',
          },
        ],
        providers: [
          {
            providerType: 'OPENAI_DIRECT',
            totalRequests: 120,
            successfulRequests: 111,
            failedRequests: 7,
            canceledRequests: 2,
            successRate: 0.925,
            availabilityRate: 0.942,
            errorRate: 0.058,
            avgDurationMs: 642,
            lastSuccessfulAt: '2026-04-20T11:58:00Z',
            lastFailedAt: '2026-04-20T11:43:00Z',
          },
        ],
      }
    }

    if (url === '/admin/ops/slo') {
      return {
        summary: {
          requestCount: 320,
          failedRequestCount: 18,
          errorRate: 0.056,
          errorBudgetRatio: 0.05,
          errorBudgetRemainingRatio: 0.42,
          burnRate: 1.8,
          riskLevel: 'HIGH',
          silencedAlertCount: 1,
        },
        risks: [
          {
            scopeType: 'GATEWAY',
            scopeRef: 'global',
            policyName: 'gateway-availability',
            burnRate: 1.8,
            errorBudgetRemainingRatio: 0.42,
            riskLevel: 'HIGH',
            suspectedCauses: ['上游 5xx 抬升'],
            suggestedActions: ['优先检查热点 distributed key 的预算守门'],
          },
        ],
        recommendedActions: ['优先检查热点 distributed key 的预算守门'],
      }
    }

    if (url === '/admin/ops/capacity') {
      return {
        observedAt: '2026-04-20T12:00:00Z',
        distributedKeys: [
          {
            distributedKeyId: 1,
            keyName: 'main-key',
            maskedKey: 'sk-gw-main****',
            pressureLevel: 'HIGH',
            budgetLimitMicros: 100000,
            currentBudgetMicros: 90000,
            remainingBudgetMicros: 10000,
            rpmLimit: 100,
            currentRpm: 85,
            remainingRpm: 15,
            tpmLimit: 10000,
            currentTpm: 9000,
            remainingTpm: 1000,
            concurrencyLimit: 20,
            currentConcurrency: 18,
            remainingConcurrency: 2,
            notes: ['budget usage is close to the current window limit'],
          },
        ],
        recommendedActions: ['检查热点 credential 的冗余与配额'],
      }
    }

    throw new Error(`unexpected get url: ${url}`)
  })
})

afterEach(() => {
  mockedApiGet.mockReset()
})

describe('OpsPage', () => {
  it('renders aiops dashboard charts and operations sections', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <OpsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('智能运维总览主面板')).toBeInTheDocument()
    expect(await screen.findByText('总览协同视图入口')).toBeInTheDocument()
    expect(await screen.findByText('角色协同视图')).toBeInTheDocument()
    expect(await screen.findByText('事件处置视图')).toBeInTheDocument()
    expect(await screen.findByText('链路追踪')).toBeInTheDocument()
    expect(await screen.findByText('关键时间序列')).toBeInTheDocument()
    expect(await screen.findByText('总体成功率')).toBeInTheDocument()
    expect(await screen.findByText('总体可用率')).toBeInTheDocument()
    expect(await screen.findByText('总体失败率')).toBeInTheDocument()
    expect((await screen.findAllByText('平均耗时')).length).toBeGreaterThan(0)
    expect(await screen.findByText('凭证最近窗口健康统计')).toBeInTheDocument()
    expect(await screen.findByText('openai-main')).toBeInTheDocument()
    expect((await screen.findAllByText('OPENAI_DIRECT')).length).toBeGreaterThan(0)
    expect((await screen.findAllByText('92.5%')).length).toBeGreaterThan(0)
    expect(await screen.findAllByText('缓存命中率')).toHaveLength(2)
    expect(await screen.findByText('TPM 使用量')).toBeInTheDocument()
    expect(await screen.findByText('延迟 P95')).toBeInTheDocument()
    expect(await screen.findByText('失败请求趋势')).toBeInTheDocument()
    expect(await screen.findByText('缓存 Token 收益')).toBeInTheDocument()
    expect(await screen.findByText('热点来源与缓存画像')).toBeInTheDocument()
    expect(await screen.findByText('用量完整性')).toBeInTheDocument()
    expect(await screen.findByText('访问密钥用量与缓存明细')).toBeInTheDocument()
    expect(await screen.findByText('Top 访问密钥 Token 与命中率')).toBeInTheDocument()
    expect(await screen.findByText('SLO 风险')).toBeInTheDocument()
    expect(await screen.findByText('错误预算与风险')).toBeInTheDocument()
    expect(await screen.findByText('预算与并发压力')).toBeInTheDocument()
    expect(await screen.findByText('开放告警与高优先级入口')).toBeInTheDocument()
    expect(await screen.findByText('credential unstable')).toBeInTheDocument()
    expect(await screen.findByText('gateway-availability')).toBeInTheDocument()
    expect((await screen.findAllByText('main-key')).length).toBeGreaterThan(0)
  })
})
