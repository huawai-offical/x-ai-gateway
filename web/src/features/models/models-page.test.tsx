// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '@/components/app/confirm-provider'
import { ModelsPage } from './models-page'

const { apiRequestMock } = vi.hoisted(() => ({
  apiRequestMock: vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/admin/provider-sites' && !init?.method) {
      return [
        {
          id: 1,
          profileCode: 'openai-main',
          displayName: 'OpenAI 主站',
          providerFamily: 'OPENAI',
          siteKind: 'OPENAI_DIRECT',
          active: true,
          refreshedAt: '2026-04-23T08:00:00Z',
        },
      ]
    }
    if (url === '/admin/provider-sites/1/capabilities' && !init?.method) {
      return [
        {
          id: 101,
          modelName: 'gpt-4o-mini',
          modelKey: 'gpt-4o-mini',
          supportedProtocols: ['openai', 'responses'],
          supportsCache: true,
          supportsThinking: false,
          supportsVisibleReasoning: false,
          supportsReasoningReuse: false,
          capabilityLevel: 'NATIVE',
          sourceRefreshedAt: '2026-04-23T08:00:00Z',
        },
      ]
    }
    if (url === '/admin/model-aliases' && !init?.method) {
      return [
        {
          id: 9,
          aliasName: 'chat-fast',
          aliasKey: 'chat-fast',
          enabled: true,
          description: '快速路由',
          rules: [
            {
              id: 1009,
              protocol: 'openai',
              targetModelName: 'gpt-4o-mini',
              targetModelKey: 'gpt-4o-mini',
              providerType: 'OPENAI_DIRECT',
              baseUrlPattern: null,
              priority: 100,
              enabled: true,
              description: null,
            },
          ],
          createdAt: '2026-04-23T07:00:00Z',
          updatedAt: '2026-04-23T08:10:00Z',
        },
      ]
    }
    if (url === '/admin/model-aliases/preview' && init?.method === 'POST') {
      return {
        requestedModel: 'chat-fast',
        protocol: 'openai',
        aliasMatched: true,
        publicModel: 'chat-fast',
        resolvedModelKey: 'gpt-4o-mini',
        candidateCount: 1,
        candidates: [
          {
            credentialId: 1,
            credentialName: 'openai-main-key',
            providerType: 'OPENAI_DIRECT',
            baseUrl: 'https://api.openai.com',
            modelName: 'gpt-4o-mini',
            modelKey: 'gpt-4o-mini',
          },
        ],
      }
    }
    return []
  }),
}))

vi.mock('../../lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/api')>()
  return {
    ...actual,
    apiRequest: apiRequestMock,
  }
})

afterEach(() => {
  cleanup()
})

describe('ModelsPage', () => {
  it('renders model directory and alias table, and opens model detail dialog', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmProvider>
          <ModelsPage />
        </ConfirmProvider>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('模型目录')).toBeInTheDocument()
    expect(await screen.findByText('gpt-4o-mini')).toBeInTheDocument()
    expect((await screen.findAllByText('chat-fast')).length).toBeGreaterThan(0)

    fireEvent.click(await screen.findAllByRole('button', { name: '查看详情' }).then((buttons) => buttons[0]!))
    expect(await screen.findByText('模型详情')).toBeInTheDocument()
    expect((await screen.findAllByText('OpenAI 主站')).length).toBeGreaterThan(0)
  })
})
