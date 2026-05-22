// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CapabilityMatrixPage } from './capability-matrix-page'

const { apiRequestMock } = vi.hoisted(() => ({
  apiRequestMock: vi.fn(async (url: string) => {
    if (url === '/admin/provider-sites/capability-matrix') {
      return [
        {
          siteProfileId: 1,
          profileCode: 'site:deepseek_openai',
          displayName: 'DeepSeek OpenAI 入口',
          providerFamily: 'OPENAI',
          siteKind: 'DEEPSEEK',
          profileSource: 'MANUAL',
          authStrategy: 'BEARER',
          pathStrategy: 'OPENAI_V1',
          errorSchemaStrategy: 'OPENAI_ERROR',
          healthState: 'READY',
          blockedReason: null,
          supportedProtocols: ['openai'],
          compatibilitySurface: 'openai',
          credentialRequirements: ['api_key'],
          streamTransport: 'sse',
          fallbackStrategy: 'provider-native',
          cooldownCredentialCount: 0,
          cooldownUntil: null,
          linkedCredentialCount: 2,
          hasSnapshot: true,
          modelCount: 8,
          refreshedAt: '2026-05-22T00:00:00Z',
          preferredBackend: 'SPRING_AI',
          supportedBackends: ['SPRING_AI'],
          features: {},
          surfaces: {},
          supportsResponses: true,
          supportsEmbeddings: true,
          supportsAudio: false,
          supportsImages: false,
          supportsModeration: false,
          supportsFiles: true,
          supportsUploads: false,
        },
      ]
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

describe('CapabilityMatrixPage', () => {
  it('renders provider capability matrix', async () => {
    render(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient()}>
          <CapabilityMatrixPage />
        </QueryClientProvider>
      </MemoryRouter>,
    )

    expect(await screen.findByText('API 入口能力矩阵')).toBeInTheDocument()
    expect(await screen.findByText('DeepSeek OpenAI 入口')).toBeInTheDocument()
    expect(await screen.findAllByText('支持')).toHaveLength(3)
  })
})
