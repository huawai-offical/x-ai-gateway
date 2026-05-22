// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../lib/api'
import { GovernancePage } from './governance-page'

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
    if (url === '/admin/error-rules') {
      return [{
        id: 1,
        enabled: true,
        priority: 100,
        protocol: 'openai',
        requestPath: '/v1/chat/completions',
        httpStatus: 500,
        errorCode: 'UPSTREAM_ERROR',
        matchScope: 'UPSTREAM',
        action: 'REWRITE',
        rewriteStatus: 502,
        rewriteCode: 'REWRITTEN_ERROR',
        rewriteMessage: 'rewrite after upstream failure',
      }]
    }
    if (url === '/admin/ops/policies/route-guards') {
      return [{
        id: 11,
        policyName: 'guard-openai-primary',
        targetType: 'CREDENTIAL',
        credentialId: 101,
        policyMode: 'ENFORCE',
        actionType: 'QUARANTINE',
        priority: 100,
        enabled: true,
        description: 'block unstable credential',
        retryPolicy: '{"maxAttempts":2}',
      }]
    }
    if (url === '/admin/ops/policies/routing-runtime-plan') {
      return {
        maxAttempts: 2,
        fallbackEnabled: true,
        fallbackOrder: ['score', 'priority'],
        circuitBreakerEnabled: true,
        circuitFailureThreshold: 3,
        rateLimitEnabled: true,
        requestsPerMinute: 60,
        sourcePolicyIds: [11],
        warnings: [],
      }
    }
    if (url === '/admin/ops/policies/routing-runtime-states') {
      return [{
        runtimeKey: 'policy:11:credential:101',
        policyId: 11,
        targetRef: 'credential:101',
        state: 'OPEN',
        failureCount: 3,
        openUntil: '2026-05-01T08:10:00Z',
        currentWindowCount: 12,
        windowExpiresAt: '2026-05-01T08:01:00Z',
        reason: 'upstream 503',
      }]
    }
    if (url === '/admin/ops/quarantines') {
      return [{
        id: 21,
        targetType: 'CREDENTIAL',
        credentialId: 101,
        actionType: 'QUARANTINE',
        recoveryMode: 'AUTO_RESUME',
        reason: 'active quarantine',
        status: 'ACTIVE',
        startedAt: '2026-04-20T00:00:00Z',
      }]
    }
    throw new Error(`unexpected get url: ${url}`)
  })

  mockedApiPost.mockImplementation(async (url: string, init?: { body?: unknown }) => {
    if (url === '/admin/error-rules/preview') {
      return {
        matchedRules: [{
          id: 1,
          enabled: true,
          priority: 100,
          protocol: 'openai',
          requestPath: '/v1/chat/completions',
          httpStatus: 500,
          errorCode: 'UPSTREAM_ERROR',
          matchScope: 'UPSTREAM',
          action: 'DOWNGRADE',
          downgradePolicy: 'fallback:gpt-4o-mini',
        }],
      }
    }
    if (url === '/admin/ops/policies/route-guards') {
      return { id: 12, ...(init?.body as object) }
    }
    if (url === '/admin/error-rules') {
      return { id: 2, ...(init?.body as object) }
    }
    if (url === '/admin/ops/policies/routing-runtime-states/reset') {
      return undefined
    }
    throw new Error(`unexpected post url: ${url}`)
  })

  mockedApiPut.mockImplementation(async (url: string, init?: { body?: unknown }) => {
    if (url.startsWith('/admin/error-rules/')) {
      return { id: 1, ...(init?.body as object) }
    }
    if (url.startsWith('/admin/ops/policies/route-guards/')) {
      return { id: 11, ...(init?.body as object) }
    }
    throw new Error(`unexpected put url: ${url}`)
  })

  mockedApiDelete.mockImplementation(async (url: string) => {
    if (url === '/admin/error-rules/1') {
      return undefined
    }
    if (url === '/admin/ops/policies/route-guards/11') {
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
      <MemoryRouter>
        <GovernancePage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('GovernancePage', () => {
  it('renders governance workspace', async () => {
    renderPage()

    expect(await screen.findByText('错误、路由与降级编排')).toBeInTheDocument()
    expect(await screen.findByText('错误策略链')).toBeInTheDocument()
  })

  it('can create a route guard from the form', async () => {
    renderPage()

    const routeGuardsTab = await screen.findByRole('tab', { name: '路由守卫' })
    fireEvent.mouseDown(routeGuardsTab)
    fireEvent.click(routeGuardsTab)
    expect(await screen.findByText('新建路由守卫')).toBeInTheDocument()
    fireEvent.change(await screen.findByPlaceholderText('guard-openai-primary'), { target: { value: 'guard-credential-102' } })
    fireEvent.change(await screen.findByPlaceholderText('仅 CREDENTIAL'), { target: { value: '102' } })
    fireEvent.click(await screen.findByRole('button', { name: '创建守卫' }))

    await waitFor(() => {
      expect(mockedApiPost).toHaveBeenCalledWith(
        '/admin/ops/policies/route-guards',
        expect.objectContaining({
          body: expect.objectContaining({
            policyName: 'guard-credential-102',
            credentialId: 102,
          }),
        }),
      )
    })
  })

  it('renders simulation chain with final decision', async () => {
    renderPage()

    const simulationTab = await screen.findByRole('tab', { name: '模拟预览' })
    fireEvent.mouseDown(simulationTab)
    fireEvent.click(simulationTab)
    expect(await screen.findByText('模拟输入')).toBeInTheDocument()
    const simulationForm = document.getElementById('simulation-form')
    const simulationInputs = simulationForm?.querySelectorAll('input')
    fireEvent.change(simulationInputs?.[6] as HTMLInputElement, { target: { value: '101' } })
    fireEvent.click(await screen.findByRole('button', { name: '运行模拟' }))

    expect(await screen.findByText('治理链路')).toBeInTheDocument()
    expect(await screen.findByText('错误规则 #1')).toBeInTheDocument()
    expect(await screen.findByText('active quarantine')).toBeInTheDocument()
    expect(screen.getAllByText('Route QUARANTINE / Error DOWNGRADE').length).toBeGreaterThan(0)
    expect(await screen.findByText('最终决策')).toBeInTheDocument()
  })

  it('can delete error rule from governance page', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '删除规则 1' }))

    await waitFor(() => {
      expect(mockedApiDelete).toHaveBeenCalledWith(
        '/admin/error-rules/1',
        expect.objectContaining({ responseType: 'void' }),
      )
    })

    confirmSpy.mockRestore()
  })

  it('can delete route guard from governance page', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderPage()

    const routeGuardsTab = await screen.findByRole('tab', { name: '路由守卫' })
    fireEvent.mouseDown(routeGuardsTab)
    fireEvent.click(routeGuardsTab)
    fireEvent.click(await screen.findByRole('button', { name: '删除守卫 11' }))

    await waitFor(() => {
      expect(mockedApiDelete).toHaveBeenCalledWith(
        '/admin/ops/policies/route-guards/11',
        expect.objectContaining({ responseType: 'void' }),
      )
    })

    confirmSpy.mockRestore()
  })

  it('can inspect and reset routing runtime states', async () => {
    renderPage()

    const routeGuardsTab = await screen.findByRole('tab', { name: '路由守卫' })
    fireEvent.mouseDown(routeGuardsTab)
    fireEvent.click(routeGuardsTab)

    expect(await screen.findByText('路由运行时状态')).toBeInTheDocument()
    expect((await screen.findAllByText(/credential:101/)).length).toBeGreaterThan(0)
    fireEvent.click(await screen.findByRole('button', { name: '重置状态' }))

    await waitFor(() => {
      expect(mockedApiPost).toHaveBeenCalledWith(
        '/admin/ops/policies/routing-runtime-states/reset',
        expect.objectContaining({ responseType: 'void' }),
      )
    })
  })
})
