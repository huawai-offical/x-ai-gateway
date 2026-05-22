// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ProviderSiteDetailPage } from './provider-site-detail-page'

const { apiRequestMock } = vi.hoisted(() => ({
  apiRequestMock: vi.fn(async (url: string) => {
    if (url === '/admin/provider-sites/1') {
      return {
        id: 1,
        profileCode: 'site:mimo_openai',
        displayName: 'MiMo OpenAI 入口',
        vendorCode: 'xiaomi_mimo',
        vendorName: '小米 MiMo',
        providerFamily: 'OPENAI',
        siteKind: 'OPENAI_COMPATIBLE_GENERIC',
        authStrategy: 'BEARER',
        pathStrategy: 'OPENAI_V1',
        modelAddressingStrategy: 'MODEL_NAME',
        errorSchemaStrategy: 'OPENAI_ERROR',
        baseUrlPattern: 'https://token-plan-sgp.xiaomimimo.com/v1',
        description: 'MiMo',
        conversationProfile: { reasoningContentMode: 'passthrough' },
        profileSource: 'MANUAL',
        active: true,
        healthState: 'READY',
        supportedProtocols: ['openai'],
        compatibilitySurface: 'openai',
        credentialRequirements: ['api_key'],
        cooldownCredentialCount: 0,
        linkedCredentialCount: 1,
        hasSnapshot: true,
        preferredBackend: 'SPRING_AI',
        supportedBackends: ['SPRING_AI'],
        features: {
          response_object: {
            declaredLevel: 'NATIVE',
            implementedLevel: 'EMULATED',
            effectiveLevel: 'EMULATED',
            supportStatus: 'emulated',
            blockedReasons: [],
            lossReasons: [],
          },
        },
        surfaces: {
          chat_completion: {
            resourceType: 'CHAT',
            operation: 'CHAT_COMPLETION',
            surface: 'openai',
            normalizedPath: '/v1/chat/completions',
            preferredBackend: 'SPRING_AI',
            supportedBackends: ['SPRING_AI'],
            supportStatus: 'native',
            executionCapabilityLevel: 'NATIVE',
            renderCapabilityLevel: 'NATIVE',
            overallCapabilityLevel: 'NATIVE',
            blockerReasons: [],
            lossReasons: [],
            requiredFeatures: ['chat_text'],
            featureResolutions: {},
          },
        },
        modelCount: 1,
        refreshedAt: '2026-05-22T00:00:00Z',
      }
    }
    if (url === '/admin/provider-sites/1/capabilities') {
      return [
        {
          id: 11,
          modelName: 'mimo-v2-omni',
          modelKey: 'mimo-v2-omni',
          supportedProtocols: ['openai'],
          supportsChat: true,
          supportsTools: true,
          supportsImageInput: false,
          supportsEmbeddings: false,
          supportsCache: false,
          supportsThinking: true,
          supportsVisibleReasoning: true,
          supportsReasoningReuse: true,
          reasoningTransport: 'REASONING_CONTENT',
          capabilityLevel: 'EMULATED',
          preferredBackend: 'SPRING_AI',
          supportedBackends: ['SPRING_AI'],
          surfaces: {},
          sourceRefreshedAt: '2026-05-22T00:00:00Z',
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

describe('ProviderSiteDetailPage', () => {
  it('renders provider site detail, model capability and surface matrix', async () => {
    render(
      <MemoryRouter initialEntries={['/console/provider-sites/1']}>
        <QueryClientProvider client={new QueryClient()}>
          <Routes>
            <Route path="/console/provider-sites/:id" element={<ProviderSiteDetailPage />} />
          </Routes>
        </QueryClientProvider>
      </MemoryRouter>,
    )

    expect(await screen.findByText('MiMo OpenAI 入口')).toBeInTheDocument()
    expect(await screen.findByText('小米 MiMo')).toBeInTheDocument()
    expect((await screen.findAllByText('mimo-v2-omni')).length).toBeGreaterThan(0)
    expect(await screen.findByText('chat_completion')).toBeInTheDocument()
    expect(await screen.findByText('response_object')).toBeInTheDocument()
  })
})
