// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '@/components/app/confirm-provider'
import { apiClient } from '../../lib/api'
import { OpsAlertsPage } from './ops-alerts-page'

vi.mock('../../lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/api')>()
  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
    },
  }
})

const mockedApiGet = apiClient.get as unknown as ReturnType<typeof vi.fn>
const mockedApiPost = apiClient.post as unknown as ReturnType<typeof vi.fn>
const mockedApiPut = apiClient.put as unknown as ReturnType<typeof vi.fn>
const mockedApiDelete = apiClient.delete as unknown as ReturnType<typeof vi.fn>

beforeEach(() => {
  mockedApiGet.mockImplementation(async (url: string) => {
    if (url === '/admin/ops/alerts/rules') {
      return [{ id: 1, ruleName: 'qps-watch', metricKey: 'qps', comparisonOperator: '>', thresholdValue: 1, severity: 'HIGH' }]
    }
    if (url === '/admin/ops/alerts') {
      return [{ id: 11, title: 'credential unstable', severity: 'HIGH', status: 'OPEN', message: 'error spike', entityType: 'CREDENTIAL', entityRef: '101' }]
    }
    if (url === '/admin/ops/policies/auto-actions') {
      return [{ id: 1, ruleName: 'credential-auto-quarantine', eventType: 'REQUEST_ERROR_RATIO', entityType: 'CREDENTIAL', actionType: 'QUARANTINE', recoveryMode: 'AUTO_RESUME', enabled: true }]
    }
    if (url === '/admin/ops/health-scores') {
      return {
        sites: [{ siteProfileId: 1, profileCode: 'openai-main', displayName: 'OpenAI 主站', providerFamily: 'OPENAI', siteKind: 'OPENAI_DIRECT', active: true, score: 63, healthState: 'DEGRADED', summary: '站点下存在被治理阻断或冷却的凭证。', activeQuarantineCount: 1, cooldownCredentialCount: 1 }],
        credentials: [
          { sourceType: 'API_KEY', sourceId: 101, credentialId: 101, credentialName: 'openai-primary', displayName: 'openai-primary', providerType: 'OPENAI_DIRECT', siteProfileId: 1, active: true, score: 100, healthState: 'HEALTHY' },
          { sourceType: 'AUTH_JSON_ACCOUNT', sourceId: 201, accountId: 201, credentialName: 'codex-auth-json', displayName: 'codex-auth-json', providerType: 'OPENAI_DIRECT', active: true, frozen: false, score: 100, healthState: 'HEALTHY' },
        ],
      }
    }
    if (url === '/admin/ops/alerts/silences') {
      return [{ id: 1, silenceName: 'mute-request-error-ratio', eventType: 'REQUEST_ERROR_RATIO', severity: 'HIGH', entityType: 'CREDENTIAL', entityRef: '101', enabled: true, reason: '夜间维护窗口' }]
    }
    if (url === '/admin/ops/quarantines') {
      return [{ id: 1, targetType: 'CREDENTIAL', credentialId: 101, actionType: 'QUARANTINE', recoveryMode: 'AUTO_RESUME', reason: 'auto quarantine', status: 'ACTIVE', startedAt: '2026-04-17T08:00:00Z' }]
    }
    if (url === '/admin/integrations/deliveries') {
      return [{ id: 91, eventId: 'evt-91', eventType: 'ALERT_OPENED', channelId: 7, entityType: 'CREDENTIAL', entityRef: '101', deliveryStatus: 'SUCCEEDED', attemptCount: 1, payloadJson: '{"summary":"credential unstable"}', occurredAt: '2026-04-18T02:00:00Z', responseSummary: '200 OK' }]
    }
    throw new Error(`unexpected get url: ${url}`)
  })

  mockedApiPost.mockImplementation(async (url: string) => {
    if (url === '/admin/ops/alerts/rules') {
      return { id: 2, ruleName: 'rule-created', metricKey: 'qps', comparisonOperator: '>', thresholdValue: 1, severity: 'HIGH' }
    }
    if (url === '/admin/ops/policies/auto-actions') {
      return { id: 2, ruleName: 'auto-created', eventType: 'REQUEST_ERROR_RATIO', entityType: 'CREDENTIAL', actionType: 'QUARANTINE', recoveryMode: 'AUTO_RESUME', enabled: true }
    }
    if (url === '/admin/ops/alerts/11/ack') {
      return { id: 11, title: 'credential unstable', severity: 'HIGH', status: 'ACKED', message: 'error spike' }
    }
    if (url === '/admin/ops/alerts/silences') {
      return { id: 2, silenceName: 'mute-request-error-ratio', eventType: 'REQUEST_ERROR_RATIO', severity: 'HIGH', entityType: 'CREDENTIAL', entityRef: '101', enabled: true, reason: '夜间维护窗口' }
    }
    if (url === '/admin/ops/quarantines/1/release') {
      return { id: 1, targetType: 'CREDENTIAL', credentialId: 101, actionType: 'QUARANTINE', recoveryMode: 'AUTO_RESUME', reason: 'auto quarantine', status: 'RELEASED', releaseReason: 'manual-release-from-ui', startedAt: '2026-04-17T08:00:00Z' }
    }
    throw new Error(`unexpected post url: ${url}`)
  })

  mockedApiPut.mockImplementation(async (url: string) => {
    if (url === '/admin/ops/policies/auto-actions/1') {
      return { id: 1, ruleName: 'credential-auto-quarantine', eventType: 'REQUEST_ERROR_RATIO', entityType: 'CREDENTIAL', actionType: 'COOLDOWN', recoveryMode: 'AUTO_RESUME', enabled: true }
    }
    throw new Error(`unexpected put url: ${url}`)
  })

  mockedApiDelete.mockImplementation(async (url: string) => {
    if (url === '/admin/ops/policies/auto-actions/1') {
      return undefined
    }
    throw new Error(`unexpected delete url: ${url}`)
  })
})

afterEach(() => {
  cleanup()
  mockedApiGet.mockReset()
  mockedApiPost.mockReset()
  mockedApiPut.mockReset()
  mockedApiDelete.mockReset()
})

function renderPage() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ConfirmProvider>
        <MemoryRouter>
          <OpsAlertsPage />
        </MemoryRouter>
      </ConfirmProvider>
    </QueryClientProvider>,
  )
}

describe('OpsAlertsPage', () => {
  it('renders ops view with governance cta', async () => {
    renderPage()

    expect(await screen.findByText('告警、静默与隔离运营台')).toBeInTheDocument()
    expect(await screen.findByText('治理健康分')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: '开放告警' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: '告警规则' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: '自动动作' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: '告警静默' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: '隔离记录' })).toBeInTheDocument()
    expect(await screen.findByText('外发投递状态')).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: /打开治理编排/i })).toBeInTheDocument()
    expect(await screen.findByText('OpenAI 主站')).toBeInTheDocument()
    expect(await screen.findByText('账号凭证')).toBeInTheDocument()
    expect(await screen.findByText('codex-auth-json')).toBeInTheDocument()
    expect(await screen.findByText('credential-auto-quarantine')).toBeInTheDocument()
    expect(await screen.findByText('mute-request-error-ratio')).toBeInTheDocument()
  })

  it('loads quarantines without injecting query context into status params', async () => {
    renderPage()

    await screen.findByRole('heading', { name: '隔离记录' })

    await waitFor(() => {
      expect(mockedApiGet).toHaveBeenCalledWith(
        '/admin/ops/quarantines',
        expect.objectContaining({ params: undefined }),
      )
    })
  })

  it('can release a quarantine from the page', async () => {
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '解除隔离' }))

    await waitFor(() => {
      expect(mockedApiPost).toHaveBeenCalledWith(
        '/admin/ops/quarantines/1/release',
        expect.objectContaining({ body: { releaseReason: 'manual-release-from-ui' } }),
      )
    })
  })

  it('can edit auto action from the page', async () => {
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '编辑动作' }))
    fireEvent.change(await screen.findByPlaceholderText('QUARANTINE / COOLDOWN'), {
      target: { value: 'COOLDOWN' },
    })
    fireEvent.click(await screen.findByRole('button', { name: '保存自动动作' }))

    await waitFor(() => {
      expect(mockedApiPut).toHaveBeenCalledWith(
        '/admin/ops/policies/auto-actions/1',
        expect.objectContaining({
          body: expect.objectContaining({
            actionType: 'COOLDOWN',
          }),
        }),
      )
    })
  })

  it('can delete auto action from the page', async () => {
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '删除动作' }))
    fireEvent.click(await screen.findByRole('button', { name: '删除' }))

    await waitFor(() => {
      expect(mockedApiDelete).toHaveBeenCalledWith(
        '/admin/ops/policies/auto-actions/1',
        expect.objectContaining({ responseType: 'void' }),
      )
    })
  })

  it('can create alert silence from the page', async () => {
    renderPage()

    const silencePanel = (await screen.findByRole('heading', { name: '告警静默' })).closest('[data-slot="card"]')
    expect(silencePanel).toBeTruthy()
    const scope = within(silencePanel as HTMLElement)

    fireEvent.change(await scope.findByPlaceholderText('静默名称'), { target: { value: 'mute-request-error-ratio' } })
    fireEvent.change(await scope.findByPlaceholderText('事件类型'), { target: { value: 'REQUEST_ERROR_RATIO' } })
    fireEvent.click(await scope.findByRole('button', { name: '创建告警静默' }))

    await waitFor(() => {
      expect(mockedApiPost).toHaveBeenCalledWith(
        '/admin/ops/alerts/silences',
        expect.objectContaining({
          body: expect.objectContaining({
            silenceName: 'mute-request-error-ratio',
          }),
        }),
      )
    })
  })
})
