// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../lib/api'
import { WorkbenchPage } from './workbench-page'

vi.mock('../../lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/api')>()
  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
    },
  }
})

const mockedApiGet = apiClient.get as unknown as ReturnType<typeof vi.fn>
const mockedApiPost = apiClient.post as unknown as ReturnType<typeof vi.fn>

beforeEach(() => {
  mockedApiPost.mockImplementation(async (url: string) => {
    if (url === '/admin/routing/preview') {
      return {
        selection: {
          selectionSource: 'PREFIX_AFFINITY',
          selectedCandidate: { candidate: { providerType: 'OPENAI_DIRECT', modelKey: 'gpt-4o' } },
          candidateEvaluations: [],
          attempts: [],
          candidates: [],
          governanceNotes: [],
        },
        requestedSemantics: {
          resourceType: 'CHAT',
          operation: 'CHAT_COMPLETION',
          requiredFeatures: ['CHAT_TEXT'],
          requiresRouteSelection: true,
        },
        canonicalRequest: { model: 'gpt-4o' },
        plan: {
          executable: true,
          requestPath: '/v1/chat/completions',
          normalizedPath: '/v1/chat/completions',
          requiredFeatures: ['CHAT_TEXT'],
          featureLevels: { CHAT_TEXT: 'NATIVE' },
          supportStatus: 'NATIVE',
          routeSelectionMode: 'CATALOG_SELECTION',
          executionBackend: 'NATIVE',
          routePolicyReason: 'chat route',
          renderPolicyReason: 'native render',
          fallbackPolicyReason: 'no fallback',
          degradationLevel: 'NATIVE',
          supportedBackends: ['NATIVE'],
          blockerReasons: [],
          degradations: [],
          blockers: [],
        },
        candidateEvaluations: [],
      }
    }
    if (url === '/admin/execution/preview') {
      return {
        selection: {
          selectionSource: 'PREFIX_AFFINITY',
          selectedCandidate: { candidate: { providerType: 'OPENAI_DIRECT', modelKey: 'gpt-4o' } },
          candidateEvaluations: [],
          attempts: [],
          candidates: [],
          governanceNotes: [],
        },
        canonicalRequest: { model: 'gpt-4o' },
        plan: {
          executable: true,
          requestPath: '/v1/chat/completions',
          normalizedPath: '/v1/chat/completions',
          requiredFeatures: ['CHAT_TEXT'],
          featureLevels: { CHAT_TEXT: 'NATIVE' },
          supportStatus: 'NATIVE',
          routeSelectionMode: 'CATALOG_SELECTION',
          executionBackend: 'NATIVE',
          routePolicyReason: 'chat route',
          renderPolicyReason: 'native render',
          fallbackPolicyReason: 'no fallback',
          degradationLevel: 'NATIVE',
          supportedBackends: ['NATIVE'],
          blockerReasons: [],
          degradations: [],
          blockers: [],
        },
        providerBinding: {},
        providerOptions: { temperature: 0.2 },
        translatedUpstreamPayload: {
          providerType: 'OPENAI_DIRECT',
          resolvedModel: 'gpt-4o',
          requestPath: '/v1/chat/completions',
          objectMode: 'chat',
          messages: [{ role: 'user', text: 'hello', parts: [{ type: 'text', text: 'hello' }] }],
          providerOptions: { temperature: 0.2 },
        },
        providerBindingSummary: {
          providerType: 'OPENAI_DIRECT',
          siteKind: 'OPENAI_DIRECT',
          capabilityLevel: 'NATIVE',
        },
        normalizedResponsePreview: {
          surface: 'chat.completions',
          objectMode: 'chat',
          supportStatus: 'NATIVE',
          degradationLevel: 'NATIVE',
          notes: ['执行后会返回标准 chat completion。'],
        },
      }
    }
    if (url === '/admin/chat/execute') {
      return {
        requestId: 'req-1',
        routeSelection: {
          selectionSource: 'PREFIX_AFFINITY',
          selectedCandidate: { candidate: { providerType: 'OPENAI_DIRECT', modelKey: 'gpt-4o' } },
          candidateEvaluations: [],
          attempts: [],
          candidates: [],
          governanceNotes: [],
        },
        plan: {
          executable: true,
          requestPath: '/v1/chat/completions',
          normalizedPath: '/v1/chat/completions',
          requiredFeatures: ['CHAT_TEXT'],
          featureLevels: { CHAT_TEXT: 'NATIVE' },
          supportStatus: 'NATIVE',
          routeSelectionMode: 'CATALOG_SELECTION',
          executionBackend: 'NATIVE',
          routePolicyReason: 'chat route',
          renderPolicyReason: 'native render',
          fallbackPolicyReason: 'no fallback',
          degradationLevel: 'NATIVE',
          supportedBackends: ['NATIVE'],
          blockerReasons: [],
          degradations: [],
          blockers: [],
        },
        executionBackend: 'NATIVE',
        text: 'hello from runtime',
        usage: {
          rawPromptTokens: 10,
          promptTokens: 10,
          completionTokens: 8,
          reasoningTokens: 0,
          cacheHitTokens: 0,
          cacheWriteTokens: 0,
          upstreamCacheHitTokens: 0,
          upstreamCacheWriteTokens: 0,
          savedInputTokens: 0,
          totalTokens: 18,
          completeness: 'FINAL',
          source: 'DIRECT_RESPONSE',
        },
        toolCalls: [],
      }
    }
    throw new Error(`unexpected post url: ${url}`)
  })

  mockedApiGet.mockImplementation(async (url: string) => {
    if (url === '/admin/observability/traces/req-1') {
      return {
        requestLog: {
          requestId: 'req-1',
          supportStatus: 'NATIVE',
          degradationLevel: 'NATIVE',
          responseKind: 'CHAT',
        },
        routeDecision: {
          selectionSource: 'PREFIX_AFFINITY',
          supportStatus: 'NATIVE',
          degradationLevel: 'NATIVE',
          objectMode: 'chat',
        },
        cacheHits: [],
        upstreamCacheReferences: [],
        asyncResourceSummary: null,
        asyncResourceDetail: null,
      }
    }
    throw new Error(`unexpected get url: ${url}`)
  })
})

afterEach(() => {
  cleanup()
  mockedApiGet.mockReset()
  mockedApiPost.mockReset()
})

describe('WorkbenchPage', () => {
  it('renders three-stage preview and execute flow', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <WorkbenchPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(screen.getByText('客户端请求')).toBeInTheDocument()
    expect(screen.getByText('规范化计划')).toBeInTheDocument()
    expect(screen.getByText('上游载荷与结果')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '生成白盒预览' }))

    expect(await screen.findByText('请求语义与能力')).toBeInTheDocument()
    expect(await screen.findByText('上游执行载荷预览')).toBeInTheDocument()

    fireEvent.click(await screen.findByRole('button', { name: '执行对话调试' }))

    expect(await screen.findByText('真实规范化结果')).toBeInTheDocument()
    expect((await screen.findAllByText('hello from runtime')).length).toBeGreaterThan(0)
    expect(await screen.findByText('路由决策')).toBeInTheDocument()
  })
})
