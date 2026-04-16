// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ProviderSiteDetailPage } from './provider-site-detail-page'

const { apiRequest } = vi.hoisted(() => ({
  apiRequest: vi.fn(),
}))

apiRequest.mockImplementation(async (url: string, init?: RequestInit) => {
  if (url === '/admin/provider-sites/1') {
    if (init?.method === 'PUT') {
      const parsed = JSON.parse(String(init.body))
      return {
        ...sampleSite,
        displayName: parsed.displayName,
      }
    }
    return sampleSite
  }
  if (url === '/admin/provider-sites/1/capabilities') {
    return [
      {
        id: 1,
        modelName: 'gpt-4o',
        modelKey: 'gpt-4o',
        supportedProtocols: ['openai', 'responses'],
        supportsChat: true,
        supportsTools: true,
        supportsImageInput: true,
        supportsEmbeddings: true,
        supportsCache: true,
        supportsThinking: true,
        supportsVisibleReasoning: true,
        supportsReasoningReuse: true,
        reasoningTransport: 'VISIBLE',
        capabilityLevel: 'NATIVE',
        surfaces: {
          chat_completion: {
            resourceType: 'CHAT',
            operation: 'CHAT_COMPLETION',
            surface: 'chat.completions',
            normalizedPath: '/v1/chat/completions',
            supportStatus: 'NATIVE',
            degradationLevel: 'NATIVE',
            executionCapabilityLevel: 'NATIVE',
            renderCapabilityLevel: 'NATIVE',
            overallCapabilityLevel: 'NATIVE',
            blockerReasons: [],
            lossReasons: [],
            requiredFeatures: ['chat_text'],
            featureResolutions: {
              chat_text: {
                declaredLevel: 'NATIVE',
                implementedLevel: 'NATIVE',
                effectiveLevel: 'NATIVE',
                supportStatus: 'NATIVE',
                degradationLevel: 'NATIVE',
                blockedReasons: [],
                lossReasons: [],
              },
            },
          },
          response_create: {
            resourceType: 'RESPONSE',
            operation: 'RESPONSE_CREATE',
            surface: 'responses',
            normalizedPath: '/v1/responses',
            supportStatus: 'DEGRADED',
            degradationLevel: 'EMULATED',
            executionCapabilityLevel: 'NATIVE',
            renderCapabilityLevel: 'EMULATED',
            overallCapabilityLevel: 'EMULATED',
            blockerReasons: ['response object fallback'],
            lossReasons: ['render emulation'],
            requiredFeatures: ['response_object'],
            featureResolutions: {
              response_object: {
                declaredLevel: 'EMULATED',
                implementedLevel: 'EMULATED',
                effectiveLevel: 'EMULATED',
                supportStatus: 'DEGRADED',
                degradationLevel: 'EMULATED',
                blockedReasons: [],
                lossReasons: [],
              },
            },
          },
        },
      },
      {
        id: 2,
        modelName: 'chat-only',
        modelKey: 'chat-only',
        supportedProtocols: ['openai'],
        supportsChat: true,
        supportsTools: false,
        supportsImageInput: false,
        supportsEmbeddings: false,
        supportsCache: false,
        supportsThinking: false,
        supportsVisibleReasoning: false,
        supportsReasoningReuse: false,
        reasoningTransport: null,
        capabilityLevel: 'EMULATED',
        surfaces: {
          chat_completion: {
            resourceType: 'CHAT',
            operation: 'CHAT_COMPLETION',
            surface: 'chat.completions',
            normalizedPath: '/v1/chat/completions',
            supportStatus: 'DEGRADED',
            degradationLevel: 'EMULATED',
            executionCapabilityLevel: 'EMULATED',
            renderCapabilityLevel: 'NATIVE',
            overallCapabilityLevel: 'EMULATED',
            blockerReasons: [],
            lossReasons: [],
            requiredFeatures: ['chat_text'],
            featureResolutions: {
              chat_text: {
                declaredLevel: 'EMULATED',
                implementedLevel: 'EMULATED',
                effectiveLevel: 'EMULATED',
                supportStatus: 'DEGRADED',
                degradationLevel: 'EMULATED',
                blockedReasons: [],
                lossReasons: [],
              },
            },
          },
        },
      },
    ]
  }
  throw new Error(`unexpected url: ${url}`)
})

const sampleSite = {
  id: 1,
  profileCode: 'site:gemini_direct',
  displayName: 'GEMINI_DIRECT',
  providerFamily: 'GEMINI',
  siteKind: 'GEMINI_DIRECT',
  authStrategy: 'API_KEY_QUERY',
  pathStrategy: 'GEMINI_V1BETA_MODELS',
  modelAddressingStrategy: 'MODEL_NAME',
  errorSchemaStrategy: 'GEMINI_ERROR',
  baseUrlPattern: 'https://generativelanguage.googleapis.com',
  description: 'sample',
  active: true,
  healthState: 'READY',
  blockedReason: null,
  supportedProtocols: ['openai', 'responses'],
  compatibilitySurface: 'openai',
  credentialRequirements: ['api_key'],
  streamTransport: 'sse',
  fallbackStrategy: 'provider-native',
  cooldownCredentialCount: 1,
  cooldownUntil: '2026-04-13T03:00:00Z',
  features: {
    audio_transcription: {
      declaredLevel: 'NATIVE',
      implementedLevel: 'NATIVE',
      effectiveLevel: 'NATIVE',
      supportStatus: 'NATIVE',
      degradationLevel: 'NATIVE',
      blockedReasons: [],
      lossReasons: [],
    },
    image_generation: {
      declaredLevel: 'NATIVE',
      implementedLevel: 'NATIVE',
      effectiveLevel: 'NATIVE',
      supportStatus: 'NATIVE',
      degradationLevel: 'NATIVE',
      blockedReasons: [],
      lossReasons: [],
    },
    moderation: {
      declaredLevel: 'NATIVE',
      implementedLevel: 'NATIVE',
      effectiveLevel: 'NATIVE',
      supportStatus: 'NATIVE',
      degradationLevel: 'NATIVE',
      blockedReasons: [],
      lossReasons: [],
    },
    response_object: {
      declaredLevel: 'EMULATED',
      implementedLevel: 'EMULATED',
      effectiveLevel: 'EMULATED',
      supportStatus: 'DEGRADED',
      degradationLevel: 'EMULATED',
      blockedReasons: [],
      lossReasons: [],
    },
    file_object: {
      declaredLevel: 'NATIVE',
      implementedLevel: 'NATIVE',
      effectiveLevel: 'NATIVE',
      supportStatus: 'NATIVE',
      degradationLevel: 'NATIVE',
      blockedReasons: [],
      lossReasons: [],
    },
    batch_create: {
      declaredLevel: 'NATIVE',
      implementedLevel: 'NATIVE',
      effectiveLevel: 'NATIVE',
      supportStatus: 'NATIVE',
      degradationLevel: 'NATIVE',
      blockedReasons: [],
      lossReasons: [],
    },
    tuning_create: {
      declaredLevel: 'NATIVE',
      implementedLevel: 'NATIVE',
      effectiveLevel: 'NATIVE',
      supportStatus: 'NATIVE',
      degradationLevel: 'NATIVE',
      blockedReasons: [],
      lossReasons: [],
    },
  },
  surfaces: {
    response_create: {
      resourceType: 'RESPONSE',
      operation: 'RESPONSE_CREATE',
      surface: 'responses',
      normalizedPath: '/v1/responses',
      supportStatus: 'DEGRADED',
      degradationLevel: 'EMULATED',
      executionCapabilityLevel: 'EMULATED',
      renderCapabilityLevel: 'EMULATED',
      overallCapabilityLevel: 'EMULATED',
      blockerReasons: ['response object fallback'],
      lossReasons: ['render emulation'],
      requiredFeatures: ['response_object'],
      featureResolutions: {
        response_object: {
          declaredLevel: 'EMULATED',
          implementedLevel: 'EMULATED',
          effectiveLevel: 'EMULATED',
          supportStatus: 'DEGRADED',
          degradationLevel: 'EMULATED',
          blockedReasons: [],
          lossReasons: [],
        },
      },
    },
    file_create: {
      resourceType: 'FILE',
      operation: 'FILE_CREATE',
      surface: 'openai',
      normalizedPath: '/v1/files',
      preferredBackend: 'ORCHESTRATION',
      supportedBackends: ['ORCHESTRATION'],
      supportStatus: 'NATIVE',
      degradationLevel: 'NATIVE',
      executionCapabilityLevel: 'NATIVE',
      renderCapabilityLevel: 'NATIVE',
      overallCapabilityLevel: 'NATIVE',
      blockerReasons: [],
      lossReasons: [],
      requiredFeatures: ['file_object'],
      featureResolutions: {
        file_object: {
          declaredLevel: 'NATIVE',
          implementedLevel: 'NATIVE',
          effectiveLevel: 'NATIVE',
          supportStatus: 'NATIVE',
          degradationLevel: 'NATIVE',
          blockedReasons: [],
          lossReasons: [],
        },
      },
    },
    batch_create: {
      resourceType: 'BATCH',
      operation: 'BATCH_CREATE',
      surface: 'openai',
      normalizedPath: '/v1/batches',
      preferredBackend: 'ORCHESTRATION',
      supportedBackends: ['ORCHESTRATION'],
      supportStatus: 'NATIVE',
      degradationLevel: 'NATIVE',
      executionCapabilityLevel: 'NATIVE',
      renderCapabilityLevel: 'NATIVE',
      overallCapabilityLevel: 'NATIVE',
      blockerReasons: [],
      lossReasons: [],
      requiredFeatures: ['batch_create'],
      featureResolutions: {
        batch_create: {
          declaredLevel: 'NATIVE',
          implementedLevel: 'NATIVE',
          effectiveLevel: 'NATIVE',
          supportStatus: 'NATIVE',
          degradationLevel: 'NATIVE',
          blockedReasons: [],
          lossReasons: [],
        },
      },
    },
    tuning_create: {
      resourceType: 'TUNING',
      operation: 'TUNING_CREATE',
      surface: 'openai',
      normalizedPath: '/v1/fine_tuning/jobs',
      preferredBackend: 'ORCHESTRATION',
      supportedBackends: ['ORCHESTRATION'],
      supportStatus: 'NATIVE',
      degradationLevel: 'NATIVE',
      executionCapabilityLevel: 'NATIVE',
      renderCapabilityLevel: 'NATIVE',
      overallCapabilityLevel: 'NATIVE',
      blockerReasons: [],
      lossReasons: [],
      requiredFeatures: ['tuning_create'],
      featureResolutions: {
        tuning_create: {
          declaredLevel: 'NATIVE',
          implementedLevel: 'NATIVE',
          effectiveLevel: 'NATIVE',
          supportStatus: 'NATIVE',
          degradationLevel: 'NATIVE',
          blockedReasons: [],
          lossReasons: [],
        },
      },
    },
    upload_create: {
      resourceType: 'UPLOAD',
      operation: 'UPLOAD_CREATE',
      surface: 'openai',
      normalizedPath: '/v1/uploads',
      preferredBackend: 'ORCHESTRATION',
      supportedBackends: ['ORCHESTRATION'],
      supportStatus: 'ORCHESTRATION',
      degradationLevel: 'NATIVE',
      executionCapabilityLevel: 'NATIVE',
      renderCapabilityLevel: 'NATIVE',
      overallCapabilityLevel: 'NATIVE',
      blockerReasons: [],
      lossReasons: [],
      requiredFeatures: ['upload_create'],
      featureResolutions: {
        upload_create: {
          declaredLevel: 'UNSUPPORTED',
          implementedLevel: 'UNSUPPORTED',
          effectiveLevel: 'UNSUPPORTED',
          supportStatus: 'BLOCKED',
          degradationLevel: 'UNSUPPORTED',
          blockedReasons: ['Gemini Files API 存在，但不等价于 OpenAI /v1/uploads 的 create/parts/complete/cancel contract，因此仅开放 gateway-local orchestration surface。'],
          lossReasons: [],
        },
      },
    },
    audio_transcription: {
      resourceType: 'AUDIO',
      operation: 'AUDIO_TRANSCRIPTION',
      surface: 'openai',
      normalizedPath: '/v1/audio/transcriptions',
      preferredBackend: 'NATIVE',
      supportedBackends: ['NATIVE'],
      supportStatus: 'NATIVE',
      degradationLevel: 'NATIVE',
      executionCapabilityLevel: 'NATIVE',
      renderCapabilityLevel: 'NATIVE',
      overallCapabilityLevel: 'NATIVE',
      blockerReasons: [],
      lossReasons: [],
      requiredFeatures: ['audio_transcription'],
      featureResolutions: {
        audio_transcription: {
          declaredLevel: 'NATIVE',
          implementedLevel: 'NATIVE',
          effectiveLevel: 'NATIVE',
          supportStatus: 'NATIVE',
          degradationLevel: 'NATIVE',
          blockedReasons: [],
          lossReasons: [],
        },
      },
    },
    image_generation: {
      resourceType: 'IMAGE',
      operation: 'IMAGE_GENERATION',
      surface: 'openai',
      normalizedPath: '/v1/images/generations',
      preferredBackend: 'NATIVE',
      supportedBackends: ['NATIVE'],
      supportStatus: 'NATIVE',
      degradationLevel: 'NATIVE',
      executionCapabilityLevel: 'NATIVE',
      renderCapabilityLevel: 'NATIVE',
      overallCapabilityLevel: 'NATIVE',
      blockerReasons: [],
      lossReasons: [],
      requiredFeatures: ['image_generation'],
      featureResolutions: {
        image_generation: {
          declaredLevel: 'NATIVE',
          implementedLevel: 'NATIVE',
          effectiveLevel: 'NATIVE',
          supportStatus: 'NATIVE',
          degradationLevel: 'NATIVE',
          blockedReasons: [],
          lossReasons: [],
        },
      },
    },
    moderation_create: {
      resourceType: 'MODERATION',
      operation: 'MODERATION_CREATE',
      surface: 'openai',
      normalizedPath: '/v1/moderations',
      preferredBackend: 'NATIVE',
      supportedBackends: ['NATIVE'],
      supportStatus: 'NATIVE',
      degradationLevel: 'NATIVE',
      executionCapabilityLevel: 'NATIVE',
      renderCapabilityLevel: 'NATIVE',
      overallCapabilityLevel: 'NATIVE',
      blockerReasons: [],
      lossReasons: [],
      requiredFeatures: ['moderation'],
      featureResolutions: {
        moderation: {
          declaredLevel: 'NATIVE',
          implementedLevel: 'NATIVE',
          effectiveLevel: 'NATIVE',
          supportStatus: 'NATIVE',
          degradationLevel: 'NATIVE',
          blockedReasons: [],
          lossReasons: [],
        },
      },
    },
  },
  modelCount: 2,
  refreshedAt: '2026-04-13T03:00:00Z',
  createdAt: '2026-04-13T02:00:00Z',
  updatedAt: '2026-04-13T03:00:00Z',
}

vi.mock('../../lib/api', () => ({
  apiRequest,
}))

afterEach(() => {
  cleanup()
  apiRequest.mockClear()
})

describe('ProviderSiteDetailPage', () => {
  it('filters capabilities by feature and saves edits', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/provider-sites/1?surface=response_create']}>
          <Routes>
            <Route path="/provider-sites/:id" element={<ProviderSiteDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect((await screen.findAllByText('surface: openai')).length).toBeGreaterThan(0)
    expect(screen.getAllByText('gpt-4o').length).toBeGreaterThan(0)
    expect(screen.queryByText('chat-only')).not.toBeInTheDocument()
    expect(screen.getAllByText('RESPONSE_CREATE').length).toBeGreaterThan(0)
    expect(screen.getByText('supportStatus: DEGRADED')).toBeInTheDocument()
    expect(screen.getByText('normalizedPath: /v1/responses')).toBeInTheDocument()
    expect(screen.getByText(/blockerReasons: response object fallback/)).toBeInTheDocument()
    expect(screen.getByText('supportStatus: ORCHESTRATION')).toBeInTheDocument()
    expect(screen.getByText('normalizedPath: /v1/files')).toBeInTheDocument()
    expect(screen.getByText('featureSupport: file_object:NATIVE')).toBeInTheDocument()
    expect(screen.getAllByText('supportStatus: NATIVE').length).toBeGreaterThan(0)
    expect(screen.getByText('normalizedPath: /v1/batches')).toBeInTheDocument()
    expect(screen.getByText('normalizedPath: /v1/fine_tuning/jobs')).toBeInTheDocument()
    expect(screen.getByText('normalizedPath: /v1/uploads')).toBeInTheDocument()
    expect(screen.getByText('normalizedPath: /v1/audio/transcriptions')).toBeInTheDocument()
    expect(screen.getByText('normalizedPath: /v1/images/generations')).toBeInTheDocument()
    expect(screen.getByText('normalizedPath: /v1/moderations')).toBeInTheDocument()
    expect(screen.getByText('featureSupport: audio_transcription:NATIVE')).toBeInTheDocument()
    expect(screen.getByText('featureSupport: image_generation:NATIVE')).toBeInTheDocument()
    expect(screen.getByText('featureSupport: moderation:NATIVE')).toBeInTheDocument()
    expect(screen.getByText('featureSupport: batch_create:NATIVE')).toBeInTheDocument()
    expect(screen.getByText('featureSupport: tuning_create:NATIVE')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('displayName'), {
      target: { value: 'GEMINI_DIRECT_EDITED' },
    })
    fireEvent.click(screen.getByRole('button', { name: '保存站点档案' }))

    await waitFor(() => {
      expect(apiRequest).toHaveBeenCalledWith(
        '/admin/provider-sites/1',
        expect.objectContaining({
          method: 'PUT',
          body: expect.stringContaining('GEMINI_DIRECT_EDITED'),
        }),
      )
    })
  })
})
