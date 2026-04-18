// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../../lib/api'
import { OpsAlertsPage } from './ops-alerts-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(),
}))

const mockedApiRequest = apiRequest as unknown as ReturnType<typeof vi.fn>

mockedApiRequest.mockImplementation(async (url: string, init?: RequestInit) => {
  if (url === '/admin/ops/alerts/rules') {
    if (init?.method === 'POST') {
      return { id: 2, ruleName: 'rule-created', metricKey: 'qps', comparisonOperator: '>', thresholdValue: 1, severity: 'HIGH' }
    }
    return [{ id: 1, ruleName: 'qps-watch', metricKey: 'qps', comparisonOperator: '>', thresholdValue: 1, severity: 'HIGH' }]
  }
  if (url === '/admin/ops/alerts?status=OPEN') {
    return [{ id: 11, title: 'credential unstable', severity: 'HIGH', status: 'OPEN', message: 'error spike', entityType: 'CREDENTIAL', entityRef: '101' }]
  }
  if (url === '/admin/ops/policies/route-guards') {
    if (init?.method === 'POST') {
      return { id: 2, policyName: 'guard-created', targetType: 'CREDENTIAL', credentialId: 101, policyMode: 'ENFORCE', actionType: 'QUARANTINE', priority: 100, enabled: true }
    }
    return [{ id: 1, policyName: 'guard-openai', targetType: 'CREDENTIAL', credentialId: 101, policyMode: 'ENFORCE', actionType: 'QUARANTINE', priority: 100, enabled: true }]
  }
  if (url === '/admin/ops/policies/auto-actions') {
    if (init?.method === 'POST') {
      return { id: 2, ruleName: 'auto-created', eventType: 'REQUEST_ERROR_RATIO', entityType: 'CREDENTIAL', actionType: 'QUARANTINE', recoveryMode: 'AUTO_RESUME', enabled: true }
    }
    return [{ id: 1, ruleName: 'credential-auto-quarantine', eventType: 'REQUEST_ERROR_RATIO', entityType: 'CREDENTIAL', actionType: 'QUARANTINE', recoveryMode: 'AUTO_RESUME', enabled: true }]
  }
  if (url === '/admin/ops/quarantines') {
    return [{ id: 1, targetType: 'CREDENTIAL', credentialId: 101, actionType: 'QUARANTINE', recoveryMode: 'AUTO_RESUME', reason: 'auto quarantine', status: 'ACTIVE', startedAt: '2026-04-17T08:00:00Z' }]
  }
  if (url === '/admin/ops/health-scores') {
    return {
      sites: [{ siteProfileId: 1, profileCode: 'openai-main', displayName: 'OpenAI 主站', providerFamily: 'OPENAI', siteKind: 'OPENAI_DIRECT', active: true, score: 63, healthState: 'DEGRADED', reason: '站点下存在被治理阻断或冷却的凭证。', activeCredentialCount: 2, blockedCredentialCount: 1 }],
      credentials: [{ credentialId: 101, credentialName: 'openai-primary', providerType: 'OPENAI_DIRECT', siteProfileId: 1, active: true, score: 100, healthState: 'HEALTHY' }],
    }
  }
  if (url === '/admin/ops/alerts/silences') {
    if (init?.method === 'POST') {
      return { id: 2, silenceName: 'mute-request-error-ratio', eventType: 'REQUEST_ERROR_RATIO', severity: 'HIGH', entityType: 'CREDENTIAL', entityRef: '101', startsAt: '2026-04-18T01:00:00Z', endsAt: '2026-04-18T03:00:00Z', enabled: true, reason: '夜间维护窗口' }
    }
    return [{ id: 1, silenceName: 'mute-request-error-ratio', eventType: 'REQUEST_ERROR_RATIO', severity: 'HIGH', entityType: 'CREDENTIAL', entityRef: '101', startsAt: '2026-04-18T01:00:00Z', endsAt: '2026-04-18T03:00:00Z', enabled: true, reason: '夜间维护窗口' }]
  }
  if (url === '/admin/integrations/deliveries') {
    return [{ id: 91, eventId: 'evt-91', eventType: 'ALERT_OPENED', channelId: 7, entityType: 'CREDENTIAL', entityRef: '101', deliveryStatus: 'SUCCEEDED', attemptCount: 1, payloadJson: '{"summary":"credential unstable"}', occurredAt: '2026-04-18T02:00:00Z', responseSummary: '200 OK' }]
  }
  if (url === '/admin/ops/quarantines/1/release') {
    return { id: 1, targetType: 'CREDENTIAL', credentialId: 101, actionType: 'QUARANTINE', recoveryMode: 'AUTO_RESUME', reason: 'auto quarantine', status: 'RELEASED', startedAt: '2026-04-17T08:00:00Z', releaseReason: 'manual-release-from-ui' }
  }
  if (url === '/admin/ops/alerts/11/ack') {
    return { id: 11, title: 'credential unstable', severity: 'HIGH', status: 'ACKED', message: 'error spike' }
  }
  throw new Error(`unexpected url: ${url}`)
})

afterEach(() => {
  cleanup()
  mockedApiRequest.mockClear()
})

describe('OpsAlertsPage', () => {
  it('renders governance sections', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <OpsAlertsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('告警规则')).toBeInTheDocument()
    expect(await screen.findByText('路由守卫')).toBeInTheDocument()
    expect(await screen.findByText('自动动作')).toBeInTheDocument()
    expect(await screen.findByText('隔离记录')).toBeInTheDocument()
    expect(await screen.findByText('治理健康分')).toBeInTheDocument()
    expect(await screen.findByText('告警静默')).toBeInTheDocument()
    expect(await screen.findByText('外发投递状态')).toBeInTheDocument()
    expect(await screen.findByText('200 OK')).toBeInTheDocument()
    expect(await screen.findByText('OpenAI 主站')).toBeInTheDocument()
    expect(await screen.findByText('guard-openai')).toBeInTheDocument()
    expect(await screen.findByText('credential-auto-quarantine')).toBeInTheDocument()
    expect(await screen.findByText('mute-request-error-ratio')).toBeInTheDocument()
  })

  it('can release a quarantine from the page', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <OpsAlertsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Release' }))

    await waitFor(() => {
      expect(mockedApiRequest).toHaveBeenCalledWith('/admin/ops/quarantines/1/release', expect.objectContaining({ method: 'POST' }))
    })
  })

  it('does not send providerType for credential scoped guards', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <OpsAlertsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    fireEvent.change(await screen.findByPlaceholderText('策略名称'), { target: { value: 'guard-credential' } })
    fireEvent.change(await screen.findByPlaceholderText('credentialId'), { target: { value: '101' } })
    fireEvent.click(await screen.findByRole('button', { name: '创建路由守卫' }))

    await waitFor(() => {
      const call = mockedApiRequest.mock.calls.find(
        ([url, init]) => url === '/admin/ops/policies/route-guards' && init?.method === 'POST',
      )
      expect(call).toBeTruthy()
      expect(JSON.parse(call?.[1]?.body as string).providerType).toBeNull()
    })
  })

  it('can create alert silence from the page', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <OpsAlertsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    const silencePanel = (await screen.findByRole('heading', { name: '告警静默' })).closest('.panel')
    expect(silencePanel).toBeTruthy()
    const silenceScope = within(silencePanel as HTMLElement)

    fireEvent.change(await silenceScope.findByPlaceholderText('silence 名称'), { target: { value: 'mute-request-error-ratio' } })
    fireEvent.change(await silenceScope.findByPlaceholderText('eventType'), { target: { value: 'REQUEST_ERROR_RATIO' } })
    fireEvent.click(await silenceScope.findByRole('button', { name: '创建告警静默' }))

    await waitFor(() => {
      const call = mockedApiRequest.mock.calls.find(
        ([url, init]) => url === '/admin/ops/alerts/silences' && init?.method === 'POST',
      )
      expect(call).toBeTruthy()
      expect(JSON.parse(call?.[1]?.body as string).silenceName).toBe('mute-request-error-ratio')
    })
  })
})
