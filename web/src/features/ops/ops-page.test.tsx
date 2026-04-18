// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { OpsPage } from './ops-page'

class MockSocket {
  onmessage: ((event: MessageEvent) => void) | null = null
  close() {}
}

vi.stubGlobal('WebSocket', MockSocket as unknown as typeof WebSocket)
vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(async (url: string) => {
    if (url === '/admin/ops/summary') {
      return {
        snapshot: { observedAt: '', qps: 1.2, errorRate: 0.1, p95LatencyMs: 0, providerFailures: 1, activeAlerts: 2, affectedEntities: [] },
        alerts: [],
      }
    }
    if (url === '/admin/ops/slo') {
      return {
        summary: {
          requestCount: 20,
          failedRequestCount: 2,
          errorRate: 0.1,
          errorBudgetRatio: 0.05,
          errorBudgetRemainingRatio: 0.0,
          burnRate: 2.0,
          riskLevel: 'HIGH',
          silencedAlertCount: 1,
        },
        risks: [],
        recommendedActions: ['优先检查热点 distributed key 的预算守门'],
      }
    }
    if (url === '/admin/ops/capacity') {
      return {
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
        providerRanking: [{ key: 'OPENAI_DIRECT', count: 30 }],
        modelGroupRanking: [],
        credentialRanking: [],
        alerts: [],
        recommendedActions: ['检查热点 credential 的冗余与配额'],
      }
    }
    throw new Error(`unexpected url: ${url}`)
  }),
}))

describe('OpsPage', () => {
  it('renders risk and capacity panels', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <OpsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('实时指挥台')).toBeInTheDocument()
    expect(await screen.findByText('错误预算')).toBeInTheDocument()
    expect(await screen.findByText('预算压力')).toBeInTheDocument()
    expect(await screen.findByText('优先检查热点 distributed key 的预算守门')).toBeInTheDocument()
    expect(await screen.findByText('检查热点 credential 的冗余与配额')).toBeInTheDocument()
  })
})
