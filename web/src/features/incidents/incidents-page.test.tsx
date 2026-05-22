// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { IncidentsPage } from './incidents-page'

vi.mock('../../lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/api')>()
  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      get: vi.fn(async (url: string) => {
        if (url === '/admin/incidents/summary') {
          return {
            opsSummary: {
              snapshot: {
                observedAt: '2026-04-18T02:00:00Z',
                qps: 12,
                errorRate: 0.14,
                p95LatencyMs: 800,
                providerFailures: 3,
                activeAlerts: 1,
                affectedEntities: ['credential:101'],
              },
              alerts: [],
              recentLogs: [],
            },
            sloSummary: {
              observedAt: '2026-04-18T02:00:00Z',
              summary: {
                requestCount: 100,
                failedRequestCount: 14,
                errorRate: 0.14,
                errorBudgetRatio: 1,
                errorBudgetRemainingRatio: 0.62,
                burnRate: 2.4,
                riskLevel: 'HIGH',
                silencedAlertCount: 0,
              },
              breakdowns: [],
              risks: [],
              recommendedActions: ['检查 OPENAI_DIRECT 主站点'],
            },
            capacitySummary: {
              observedAt: '2026-04-18T02:00:00Z',
              distributedKeys: [],
              providerBreakdowns: [],
              siteBreakdowns: [],
              accountBreakdowns: [],
              proxyBreakdowns: [],
              recommendedActions: [],
            },
            healthScores: {
              sites: [{ siteProfileId: 1, profileCode: 'openai-main', displayName: 'OpenAI 主站', providerFamily: 'OPENAI', siteKind: 'OPENAI_DIRECT', active: true, score: 64, healthState: 'DEGRADED', activeQuarantineCount: 1, cooldownCredentialCount: 1 }],
              credentials: [],
            },
            incidents: [{ id: 1, eventType: 'REQUEST_ERROR_RATIO', severity: 'HIGH', title: 'credential unstable', message: 'error spike', status: 'OPEN', entityType: 'CREDENTIAL', entityRef: '101', createdAt: '2026-04-18T02:00:00Z', updatedAt: '2026-04-18T02:00:00Z' }],
            silences: [],
            quarantines: [{ id: 10, targetType: 'CREDENTIAL', credentialId: 101, actionType: 'QUARANTINE', recoveryMode: 'AUTO_RESUME', reason: 'auto quarantine', status: 'ACTIVE', startedAt: '2026-04-18T02:00:00Z', createdAt: '2026-04-18T02:00:00Z', updatedAt: '2026-04-18T02:00:00Z' }],
            affectedEntities: [{ entityType: 'CREDENTIAL', entityRef: '101', title: 'openai-primary', summary: 'error spike', severity: 'HIGH', status: 'DEGRADED', source: 'ALERT' }],
            timeline: [{ eventType: 'ALERT_OPENED', title: '告警打开', description: 'credential unstable', severity: 'HIGH', entityType: 'CREDENTIAL', entityRef: '101', source: 'OPS_ALERT', occurredAt: '2026-04-18T02:00:00Z' }],
            recommendedActions: ['查看 Trace 并确认外发状态'],
          }
        }
        if (url === '/admin/integrations/deliveries') {
          return [{ id: 51, eventId: 'evt-51', eventType: 'ALERT_OPENED', channelId: 7, entityType: 'CREDENTIAL', entityRef: '101', requestId: 'req-51', deliveryStatus: 'SUCCEEDED', attemptCount: 1, payloadJson: '{"summary":"credential unstable"}', occurredAt: '2026-04-18T02:01:00Z', responseSummary: '200 OK' }]
        }
        return []
      }),
    },
  }
})

describe('IncidentsPage', () => {
  it('renders outbound delivery summary alongside incident overview', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <IncidentsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('事件处置视图')).toBeInTheDocument()
    expect(screen.queryByText('全局指标已经收口到智能运维总览主面板。')).not.toBeInTheDocument()
    expect(await screen.findByRole('link', { name: '返回智能运维总览' })).toHaveAttribute('href', '/console/ops')
    expect((await screen.findAllByRole('link', { name: '查看链路追踪' })).length).toBeGreaterThan(0)
    expect(screen.queryByText('查看 Trace 并确认外发状态')).not.toBeInTheDocument()
    expect(await screen.findByText('外发状态摘要')).toBeInTheDocument()
    expect(await screen.findByText('SUCCEEDED / 尝试 1')).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: '查看全部投递' })).toHaveAttribute('href', '/console/integrations/deliveries')
  })
})
