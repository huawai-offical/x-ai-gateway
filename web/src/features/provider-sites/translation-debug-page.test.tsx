// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../../lib/api'
import { TranslationDebugPage } from './translation-debug-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(),
}))

const mockedApiRequest = apiRequest as unknown as ReturnType<typeof vi.fn>

mockedApiRequest.mockImplementation(async (url: string, init?: RequestInit) => {
  const parsed = init?.body ? JSON.parse(String(init.body)) : {}
  if (url === '/admin/translation/explain') {
    if (parsed.requestPath === '/v1/files/file_123/content') {
      return {
        executable: true,
        ingressProtocol: 'OPENAI',
        requestPath: '/v1/files/file_123/content',
        normalizedPath: '/v1/files/{fileId}/content',
        surface: 'files',
        requestedModel: 'gpt-4o-mini',
        publicModel: 'gpt-4o-mini',
        resolvedModel: 'gpt-4o-mini',
        resourceType: 'FILE',
        operation: 'FILE_CONTENT_GET',
        executionKind: 'NATIVE',
        executionBackend: 'NATIVE',
        supportStatus: 'NATIVE',
        degradationLevel: 'NATIVE',
        objectMode: 'resource-orchestration',
        executionCapabilityLevel: 'NATIVE',
        renderCapabilityLevel: 'NATIVE',
        overallCapabilityLevel: 'NATIVE',
        requiredFeatures: ['FILE_OBJECT'],
        featureLevels: { file_object: 'NATIVE' },
        blockerReasons: [],
        degradations: [],
        blockers: [],
      }
    }
    return {
      executable: true,
      ingressProtocol: 'OPENAI',
      requestPath: '/v1/chat/completions',
      normalizedPath: '/v1/chat/completions',
      surface: 'chat.completions',
      requestedModel: 'gpt-4o',
      publicModel: 'gpt-4o',
      resolvedModel: 'gpt-4o',
      resourceType: 'CHAT',
      operation: 'CHAT_COMPLETION',
      executionKind: 'NATIVE',
      executionBackend: 'NATIVE',
      supportStatus: 'NATIVE',
      degradationLevel: 'NATIVE',
      executionCapabilityLevel: 'NATIVE',
      renderCapabilityLevel: 'NATIVE',
      overallCapabilityLevel: 'NATIVE',
      requiredFeatures: ['CHAT_TEXT'],
      featureLevels: { chat_text: 'NATIVE' },
      blockerReasons: [],
      degradations: [],
      blockers: [],
    }
  }
  if (url === '/admin/chat/execute') {
    return {
      requestId: 'req-1',
      routeSelection: { selectedCandidate: { candidate: { credentialId: 1 } } },
      plan: {
        routeSelectionMode: 'CATALOG_SELECTION',
        routePolicyReason: 'chat route',
        renderPolicyReason: 'native render',
        fallbackPolicyReason: 'allow fallback',
        supportStatus: 'NATIVE',
        degradationLevel: 'NATIVE',
        objectMode: 'chat',
      },
      text: 'hello from runtime',
      usage: { totalTokens: 12 },
      toolCalls: [],
    }
  }
  if (url === '/admin/resource/execute') {
    return {
      requestId: 'req-resource-1',
      gatewayResourceKey: 'file_123',
      routeSelection: { selectedCandidate: { candidate: { credentialId: 1 } } },
      plan: {
        normalizedPath: '/v1/files/{fileId}/content',
        surface: 'files',
        supportStatus: 'NATIVE',
        degradationLevel: 'NATIVE',
        routeSelectionMode: 'STORED_LINEAGE',
        routePolicyReason: 'stored lineage',
        renderPolicyReason: 'native render',
        fallbackPolicyReason: 'no fallback',
        objectMode: 'resource-orchestration',
        blockerReasons: [],
      },
      executionBackend: 'NATIVE',
      upstreamPath: '/v1/files/file_123/content',
      objectMode: 'resource-orchestration',
      supportStatus: 'NATIVE',
      degradationLevel: 'NATIVE',
      blockerReasons: [],
      statusCode: 200,
      contentType: 'application/pdf',
      binaryLength: 128,
      canonicalResponse: {
        responseKind: 'binary',
        objectType: 'file.content',
        objectId: 'file_123',
        status: 'completed',
        events: [],
        degradations: [],
      },
    }
  }
  if (url === '/admin/observability/traces/req-1') {
    return {
      requestLog: {
        requestId: 'req-1',
        supportStatus: 'NATIVE',
        degradationLevel: 'NATIVE',
        gatewayResourceKey: null,
      },
      routeDecision: { selectionSource: 'PREFIX_AFFINITY' },
      cacheHits: [],
      upstreamCacheReferences: [],
      asyncResourceSummary: null,
      asyncResourceDetail: null,
    }
  }
  if (url === '/admin/observability/traces/req-resource-1') {
    return {
      requestLog: {
        requestId: 'req-resource-1',
        supportStatus: 'NATIVE',
        degradationLevel: 'NATIVE',
        gatewayResourceKey: 'file_123',
      },
      routeDecision: { selectionSource: 'STORED_LINEAGE' },
      cacheHits: [],
      upstreamCacheReferences: [],
      asyncResourceSummary: {
        resourceKey: 'file_123',
        resourceType: 'FILE',
        status: 'completed',
        upstreamObjectId: 'upstream-file-123',
      },
      asyncResourceDetail: {
        lifecycle: {},
        transitions: [],
        lineage: {},
        artifacts: [],
      },
    }
  }
  throw new Error(`unexpected url: ${url}`)
})

afterEach(() => {
  cleanup()
  mockedApiRequest.mockClear()
})

describe('TranslationDebugPage', () => {
  it('shows explain and execute results', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <TranslationDebugPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    fireEvent.click(screen.getByRole('button', { name: '查看 Plan' }))
    expect(await screen.findByText('routeSelectionMode')).toBeInTheDocument()
    expect(screen.getByText('routePolicyReason')).toBeInTheDocument()
    expect(screen.getByText('supportStatus')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Request' }))
    fireEvent.click(screen.getByRole('button', { name: '执行 Chat 调试' }))
    expect((await screen.findAllByText('hello from runtime')).length).toBeGreaterThan(0)
    expect((await screen.findAllByText('req-1')).length).toBeGreaterThan(0)

    fireEvent.click(screen.getByRole('button', { name: 'Trace' }))
    expect(await screen.findByText('PREFIX_AFFINITY')).toBeInTheDocument()
  })

  it('shows resource canonical panel and binary summary', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter
          initialEntries={[
            '/translation-debug?method=GET&requestPath=%2Fv1%2Ffiles%2Ffile_123%2Fcontent&requestedModel=gpt-4o-mini&body=%7B%7D',
          ]}
        >
          <TranslationDebugPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    fireEvent.click(screen.getByRole('button', { name: '查看 Plan' }))
    expect(await screen.findByText('routeSelectionMode')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Request' }))
    fireEvent.click(screen.getByRole('button', { name: '执行资源调试' }))
    expect((await screen.findAllByText('128')).length).toBeGreaterThan(0)
    expect(screen.getByText('responseKind: binary')).toBeInTheDocument()
    expect(screen.getByText('objectType: file.content')).toBeInTheDocument()
    expect((await screen.findAllByText('file_123')).length).toBeGreaterThan(0)
    expect((await screen.findAllByText('req-resource-1')).length).toBeGreaterThan(0)

    fireEvent.click(screen.getByRole('button', { name: 'Trace' }))
    expect(await screen.findByText('upstreamObjectId')).toBeInTheDocument()
    expect(await screen.findByText('upstream-file-123')).toBeInTheDocument()
  })

  it('validates invalid json body before explain', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <TranslationDebugPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    fireEvent.change(screen.getByLabelText('request body'), {
      target: { value: '{invalid-json' },
    })
    fireEvent.click(screen.getByRole('button', { name: '查看 Plan' }))

    await waitFor(() => {
      expect(screen.getByText(/JSON 解析失败/)).toBeInTheDocument()
    })
  })

  it('applies debug preset and reveals multipart helpers', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <TranslationDebugPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Audio' }))

    expect(screen.getByDisplayValue('/v1/audio/transcriptions')).toBeInTheDocument()
    expect(screen.getByLabelText('formFields JSON')).toBeInTheDocument()
    expect(screen.getByLabelText('fileRefs JSON')).toBeInTheDocument()
  })

  it('clears explain and execute panels when reset is requested', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <TranslationDebugPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    fireEvent.click(screen.getByRole('button', { name: '查看 Plan' }))
    expect(await screen.findByText('routeSelectionMode')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Request' }))
    fireEvent.click(screen.getByRole('button', { name: '执行 Chat 调试' }))
    expect((await screen.findAllByText('hello from runtime')).length).toBeGreaterThan(0)

    fireEvent.click(screen.getByRole('button', { name: 'Request' }))
    fireEvent.click(screen.getByRole('button', { name: '清空结果' }))

    await waitFor(() => {
      expect(screen.queryAllByText('hello from runtime')).toHaveLength(0)
      expect(screen.getByText('请求输入')).toBeInTheDocument()
    })
  })
})
