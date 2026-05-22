// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../lib/api'
import { TracesPage } from './traces-page'

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
    if (url === '/admin/traces/lookup?requestId=req-1') {
      return {
        requestId: 'req-1',
        gatewayResourceKey: 'file_123',
        upstreamObjectId: 'upstream-456',
        matches: [
          {
            requestId: 'req-1',
            gatewayResourceKey: 'file_123',
            providerType: 'OPENAI_DIRECT',
            responseStatus: 'COMPLETED',
            durationMs: 620,
          },
        ],
        trace: {
          requestLog: {
            requestId: 'req-1',
            protocol: 'openai',
            requestPath: '/v1/chat/completions',
            requestedModel: 'gpt-4o',
            providerType: 'OPENAI_DIRECT',
            resourceType: 'CHAT',
            operation: 'CHAT_COMPLETION',
            supportStatus: 'NATIVE',
            degradationLevel: 'NATIVE',
            status: 'COMPLETED',
            responseKind: 'CHAT',
            responseObjectType: 'chat.completion',
            responseObjectId: 'chatcmpl-1',
            responseStatus: 'COMPLETED',
            selectionSource: 'PREFIX_AFFINITY',
            executionBackend: 'NATIVE',
            gatewayResourceKey: 'file_123',
            canonicalEventCount: 8,
            durationMs: 620,
            startedAt: '2026-04-20T11:59:00Z',
            completedAt: '2026-04-20T11:59:01Z',
            createdAt: '2026-04-20T11:59:00Z',
          },
          routeDecision: {
            selectionSource: 'PREFIX_AFFINITY',
            supportStatus: 'NATIVE',
            degradationLevel: 'NATIVE',
            objectMode: 'chat',
            selectedProviderType: 'OPENAI_DIRECT',
            selectedBaseUrl: 'https://api.openai.com',
            selectedCredentialId: 101,
            executionBackend: 'NATIVE',
            candidateCount: 3,
            resolvedModelKey: 'gpt-4o',
            createdAt: '2026-04-20T11:59:00Z',
          },
          cacheHits: [
            {
              cacheKind: 'prompt_cache',
              savedInputTokens: 240,
              cachedContentRef: 'cached-content-1',
              createdAt: '2026-04-20T11:59:00Z',
            },
          ],
          upstreamCacheReferences: [
            {
              externalCacheRef: 'ext-cache-1',
              status: 'ACTIVE',
              updatedAt: '2026-04-20T11:59:00Z',
            },
          ],
          asyncResourceSummary: {
            resourceKey: 'file_123',
            resourceType: 'FILE',
            status: 'COMPLETED',
            normalizedStatus: 'COMPLETED',
            upstreamObjectId: 'upstream-456',
            updatedAt: '2026-04-20T11:59:01Z',
          },
        },
      }
    }
    throw new Error(`unexpected get url: ${url}`)
  })
})

afterEach(() => {
  mockedApiGet.mockReset()
})

describe('TracesPage', () => {
  it('renders timeline-first trace narrative and deep-link actions', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/traces?requestId=req-1&providerType=OPENAI_DIRECT&requestPath=%2Fv1%2Fchat%2Fcompletions&tab=actions']}>
          <TracesPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('链路时间轴工作台')).toBeInTheDocument()
    expect(await screen.findByText('阶段时间轴')).toBeInTheDocument()
    expect(await screen.findByText('请求解析')).toBeInTheDocument()
    expect(await screen.findByText('路由选择')).toBeInTheDocument()
    expect(await screen.findByText('缓存检索')).toBeInTheDocument()
    expect(await screen.findByText('API 翻译')).toBeInTheDocument()
    expect(await screen.findByText('远端调用')).toBeInTheDocument()
    expect(await screen.findByText('流式返回处理')).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: '打开调试工作台' })).toHaveAttribute(
      'href',
      '/console/workbench?requestId=req-1&requestPath=%2Fv1%2Fchat%2Fcompletions',
    )
    expect(await screen.findByRole('link', { name: '打开运维总览' })).toHaveAttribute('href', '/console/ops')
    expect(await screen.findByText('实体、动作与原始数据')).toBeInTheDocument()
  })
})
