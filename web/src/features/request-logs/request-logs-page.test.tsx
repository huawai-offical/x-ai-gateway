// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../lib/api'
import { RequestLogsPage } from './request-logs-page'

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
    if (url === '/admin/observability/request-logs') {
      return [
        {
          id: 1,
          requestId: 'req-codex-1',
          protocol: 'responses',
          requestPath: '/v1/responses',
          requestedModel: 'gpt-5.4@low',
          providerType: 'OPENAI_DIRECT',
          credentialId: 17,
          status: 'FAILED',
          clientInstanceId: 'codex-cli-default',
          sessionAffinityKey: 'session-alpha',
          filterAction: 'REDACT',
          filterRuleId: 'rule-secret',
          filterSummaryJson: '{"action":"REDACT","ruleId":"rule-secret","target":"input[0].content","summary":"masked Bearer abcdefghijklmnopqrstuvwxyz"}',
          usageInputTokens: 120,
          usageOutputTokens: 30,
          usageTotalTokens: 150,
          errorCode: 'UPSTREAM_429',
          errorMessage: 'Bearer abcdefghijklmnopqrstuvwxyz hit rate limit',
          createdAt: '2026-05-07T02:00:00Z',
        },
        {
          id: 2,
          requestId: 'req-chat-1',
          protocol: 'openai',
          requestPath: '/v1/chat/completions',
          requestedModel: 'gpt-4o-mini',
          providerType: 'OPENAI_DIRECT',
          status: 'SUCCESS',
          createdAt: '2026-05-07T02:01:00Z',
        },
      ]
    }
    if (url === '/admin/observability/route-decisions') {
      return [
        {
          id: 11,
          requestId: 'req-codex-1',
          resolvedModelKey: 'gpt-5.4@low',
          selectedProviderType: 'OPENAI_DIRECT',
          selectedCredentialId: 17,
          supportStatus: 'NATIVE',
          candidateCount: 3,
          clientInstanceId: 'codex-cli-default',
          sessionAffinityKey: 'session-alpha',
          createdAt: '2026-05-07T02:00:00Z',
        },
      ]
    }
    if (url === '/admin/observability/cache-hits') {
      return [
        {
          id: 21,
          requestId: 'req-codex-1',
          providerType: 'OPENAI_DIRECT',
          credentialId: 17,
          cacheHitTokens: 20,
          cacheWriteTokens: 5,
          savedInputTokens: 40,
          createdAt: '2026-05-07T02:00:00Z',
        },
      ]
    }
    if (url === '/admin/observability/upstream-cache-references') {
      return []
    }
    throw new Error(`unexpected get url: ${url}`)
  })
})

afterEach(() => {
  cleanup()
  mockedApiGet.mockReset()
})

function renderPage() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <RequestLogsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('RequestLogsPage Codex observability', () => {
  it('renders Codex and non-Codex requests in the same request log table', async () => {
    renderPage()

    expect(await screen.findByText('观测数据')).toBeInTheDocument()
    expect(screen.queryByText('实时请求、用量与过滤命中观测台')).not.toBeInTheDocument()
    expect(mockedApiGet).not.toHaveBeenCalledWith('/admin/observability/codex-requests', expect.anything())

    const requestLogTable = await screen.findByRole('table', { name: '请求日志表' })
    expect(within(requestLogTable).getByText('req-codex-1')).toBeInTheDocument()
    expect(within(requestLogTable).getByText('req-chat-1')).toBeInTheDocument()
    expect(within(requestLogTable).getByText('codex-cli-default')).toBeInTheDocument()
    expect(within(requestLogTable).getByText('session-alpha')).toBeInTheDocument()
  })

  it('keeps route decision detail available from the generic tabs', async () => {
    renderPage()

    const routeTab = await screen.findByRole('tab', { name: '选路决策' })
    fireEvent.mouseDown(routeTab)
    fireEvent.click(routeTab)

    const routeTable = await screen.findByRole('table', { name: '选路决策表' }, { timeout: 3000 })
    expect(within(routeTable).getByText('gpt-5.4@low')).toBeInTheDocument()
    fireEvent.click(within(routeTable).getByRole('button', { name: '详情' }))
    expect(screen.getByText('选路决策详情')).toBeInTheDocument()
    expect(screen.getByText(/selectedCredentialId/)).toBeInTheDocument()
  })

  it('links request log details to trace detail lookup by requestId', async () => {
    renderPage()

    const requestLogTable = await screen.findByRole('table', { name: '请求日志表' })
    const firstRow = within(requestLogTable).getByText('req-codex-1').closest('tr')
    expect(firstRow).not.toBeNull()

    fireEvent.click(within(firstRow as HTMLTableRowElement).getByRole('button', { name: '详情' }))

    expect(screen.getByText('请求日志详情')).toBeInTheDocument()
    expect(screen.getByText('这里展示当前观测行的原始字段；完整请求与上游载荷阶段请跳转到链路追踪按 requestId 联查。')).toBeInTheDocument()
    expect(screen.getByText('requestId：req-codex-1')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '去链路追踪查看完整请求详情' })).toHaveAttribute(
      'href',
      '/console/traces?requestId=req-codex-1',
    )
  })
})
