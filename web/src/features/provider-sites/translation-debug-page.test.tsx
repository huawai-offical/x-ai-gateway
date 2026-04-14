// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { TranslationDebugPage } from './translation-debug-page'

const { apiRequest } = vi.hoisted(() => ({
  apiRequest: vi.fn(),
}))

apiRequest.mockImplementation(async (url: string) => {
  if (url === '/admin/translation/explain') {
    return {
      executable: true,
      ingressProtocol: 'OPENAI',
      requestPath: '/v1/chat/completions',
      requestedModel: 'gpt-4o',
      publicModel: 'gpt-4o',
      resolvedModel: 'gpt-4o',
      resourceType: 'CHAT',
      operation: 'CHAT_COMPLETION',
      executionKind: 'NATIVE',
      executionCapabilityLevel: 'NATIVE',
      renderCapabilityLevel: 'NATIVE',
      overallCapabilityLevel: 'NATIVE',
      requiredFeatures: ['CHAT_TEXT'],
      featureLevels: { chat_text: 'NATIVE' },
      degradations: [],
      blockers: [],
    }
  }
  if (url === '/admin/chat/execute') {
    return {
      requestId: 'req-1',
      routeSelection: { selectedCandidate: { candidate: { credentialId: 1 } } },
      text: 'hello from runtime',
      usage: { totalTokens: 12 },
      toolCalls: [],
    }
  }
  throw new Error(`unexpected url: ${url}`)
})

vi.mock('../../lib/api', () => ({
  apiRequest,
}))

afterEach(() => {
  cleanup()
  apiRequest.mockClear()
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

    fireEvent.click(screen.getByRole('button', { name: '查看 Explain' }))
    expect(await screen.findByText('protocol: OPENAI')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '执行 Chat 调试' }))
    expect(await screen.findByText('hello from runtime')).toBeInTheDocument()
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
    fireEvent.click(screen.getByRole('button', { name: '查看 Explain' }))

    await waitFor(() => {
      expect(screen.getByText(/JSON 解析失败/)).toBeInTheDocument()
    })
  })
})
