// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CapabilityMatrixPage } from './capability-matrix-page'

const { apiRequest } = vi.hoisted(() => ({
  apiRequest: vi.fn(),
}))

apiRequest.mockImplementation(async () => [
  {
    siteProfileId: 1,
    profileCode: 'site:openai_direct',
    displayName: 'OPENAI_DIRECT',
    providerFamily: 'OPENAI',
    siteKind: 'OPENAI_DIRECT',
    authStrategy: 'BEARER',
    pathStrategy: 'OPENAI_V1',
    errorSchemaStrategy: 'OPENAI_ERROR',
    healthState: 'READY',
    blockedReason: null,
    supportedProtocols: ['openai', 'responses'],
    compatibilitySurface: 'openai',
    credentialRequirements: ['api_key'],
    streamTransport: 'sse',
    fallbackStrategy: 'provider-native',
    cooldownCredentialCount: 0,
    cooldownUntil: null,
    features: {
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
        blockerReasons: [],
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
    supportsResponses: true,
    supportsEmbeddings: true,
    supportsAudio: false,
    supportsImages: false,
    supportsModeration: false,
    supportsFiles: false,
    supportsUploads: false,
    supportsBatches: false,
    supportsTuning: false,
    supportsRealtime: false,
  },
  {
    siteProfileId: 2,
    profileCode: 'site:gemini_direct',
    displayName: 'GEMINI_DIRECT',
    providerFamily: 'GEMINI',
    siteKind: 'GEMINI_DIRECT',
    authStrategy: 'API_KEY_QUERY',
    pathStrategy: 'GEMINI_V1BETA_MODELS',
    errorSchemaStrategy: 'GEMINI_ERROR',
    healthState: 'READY',
    blockedReason: null,
    supportedProtocols: ['google_native'],
    compatibilitySurface: 'google_native',
    credentialRequirements: ['api_key_query'],
    streamTransport: 'sse',
    fallbackStrategy: 'gateway-orchestration',
    cooldownCredentialCount: 0,
    cooldownUntil: null,
    preferredBackend: 'ORCHESTRATION',
    supportedBackends: ['ORCHESTRATION'],
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
      file_object: {
        declaredLevel: 'UNSUPPORTED',
        implementedLevel: 'UNSUPPORTED',
        effectiveLevel: 'UNSUPPORTED',
        supportStatus: 'BLOCKED',
        degradationLevel: 'UNSUPPORTED',
        blockedReasons: ['file object unsupported'],
        lossReasons: [],
      },
    },
    surfaces: {
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
      file_create: {
        resourceType: 'FILE',
        operation: 'FILE_CREATE',
        surface: 'openai',
        normalizedPath: '/v1/files',
        preferredBackend: 'ORCHESTRATION',
        supportedBackends: ['ORCHESTRATION'],
        supportStatus: 'ORCHESTRATION',
        degradationLevel: 'NATIVE',
        executionCapabilityLevel: 'NATIVE',
        renderCapabilityLevel: 'NATIVE',
        overallCapabilityLevel: 'NATIVE',
        blockerReasons: [],
        lossReasons: [],
        requiredFeatures: ['file_object'],
        featureResolutions: {
          file_object: {
            declaredLevel: 'UNSUPPORTED',
            implementedLevel: 'UNSUPPORTED',
            effectiveLevel: 'UNSUPPORTED',
            supportStatus: 'BLOCKED',
            degradationLevel: 'UNSUPPORTED',
            blockedReasons: ['file object unsupported'],
            lossReasons: [],
          },
        },
      },
      image_edit: {
        resourceType: 'IMAGE',
        operation: 'IMAGE_EDIT',
        surface: 'openai',
        normalizedPath: '/v1/images/edits',
        preferredBackend: 'NATIVE',
        supportedBackends: [],
        supportStatus: 'BLOCKED',
        degradationLevel: 'UNSUPPORTED',
        executionCapabilityLevel: 'UNSUPPORTED',
        renderCapabilityLevel: 'NATIVE',
        overallCapabilityLevel: 'UNSUPPORTED',
        blockerReasons: ['image edits unsupported'],
        lossReasons: [],
        requiredFeatures: ['image_edit'],
        featureResolutions: {
          image_edit: {
            declaredLevel: 'UNSUPPORTED',
            implementedLevel: 'UNSUPPORTED',
            effectiveLevel: 'UNSUPPORTED',
            supportStatus: 'BLOCKED',
            degradationLevel: 'UNSUPPORTED',
            blockedReasons: ['image edits unsupported'],
            lossReasons: [],
          },
        },
      },
    },
    supportsResponses: false,
    supportsEmbeddings: true,
    supportsAudio: true,
    supportsImages: true,
    supportsModeration: true,
    supportsFiles: false,
    supportsUploads: false,
    supportsBatches: false,
    supportsTuning: false,
    supportsRealtime: false,
  },
  {
    siteProfileId: 3,
    profileCode: 'site:vertex_ai',
    displayName: 'VERTEX_AI',
    providerFamily: 'GEMINI',
    siteKind: 'VERTEX_AI',
    authStrategy: 'BEARER',
    pathStrategy: 'GEMINI_V1BETA_MODELS',
    errorSchemaStrategy: 'GEMINI_ERROR',
    healthState: 'BLOCKED',
    blockedReason: 'missing metadata',
    supportedProtocols: ['google_native'],
    compatibilitySurface: 'google_native',
    credentialRequirements: ['google_access_token'],
    streamTransport: 'sse',
    fallbackStrategy: 'vertex-google-native',
    cooldownCredentialCount: 1,
    cooldownUntil: '2026-04-13T03:00:00Z',
    features: {
      embeddings: {
        declaredLevel: 'UNSUPPORTED',
        implementedLevel: 'UNSUPPORTED',
        effectiveLevel: 'UNSUPPORTED',
        supportStatus: 'BLOCKED',
        degradationLevel: 'UNSUPPORTED',
        blockedReasons: ['embeddings unavailable'],
        lossReasons: [],
      },
    },
    surfaces: {
      embedding_create: {
        resourceType: 'EMBEDDING',
        operation: 'EMBEDDING_CREATE',
        surface: 'openai',
        normalizedPath: '/v1/embeddings',
        preferredBackend: 'NATIVE',
        supportedBackends: ['NATIVE'],
        supportStatus: 'BLOCKED',
        degradationLevel: 'UNSUPPORTED',
        executionCapabilityLevel: 'UNSUPPORTED',
        renderCapabilityLevel: 'NATIVE',
        overallCapabilityLevel: 'UNSUPPORTED',
        blockerReasons: ['embeddings unavailable'],
        lossReasons: [],
        requiredFeatures: ['embeddings'],
        featureResolutions: {
          embeddings: {
            declaredLevel: 'UNSUPPORTED',
            implementedLevel: 'UNSUPPORTED',
            effectiveLevel: 'UNSUPPORTED',
            supportStatus: 'BLOCKED',
            degradationLevel: 'UNSUPPORTED',
            blockedReasons: ['embeddings unavailable'],
            lossReasons: [],
          },
        },
      },
    },
    supportsResponses: false,
    supportsEmbeddings: false,
    supportsAudio: false,
    supportsImages: false,
    supportsModeration: false,
    supportsFiles: false,
    supportsUploads: false,
    supportsBatches: false,
    supportsTuning: false,
    supportsRealtime: false,
  },
])

vi.mock('../../lib/api', () => ({
  apiRequest,
}))

afterEach(() => {
  cleanup()
  apiRequest.mockClear()
})

describe('CapabilityMatrixPage', () => {
  it('filters blocked rows and exposes deep links', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <CapabilityMatrixPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('OPENAI_DIRECT')).toBeInTheDocument()
    fireEvent.change(screen.getByRole('combobox', { name: 'resolution' }), {
      target: { value: 'blocked' },
    })

    expect(screen.queryByText('OPENAI_DIRECT')).not.toBeInTheDocument()
    expect(screen.getByText('GEMINI_DIRECT')).toBeInTheDocument()
    expect(screen.getByText('VERTEX_AI')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /FILE_CREATE/ })).toHaveAttribute('href', '/provider-sites/2?surface=file_create')
    expect(screen.getByRole('link', { name: /EMBEDDING_CREATE/ })).toHaveAttribute('href', '/provider-sites/3?surface=embedding_create')
    expect(screen.getByText('supportStatus: ORCHESTRATION')).toBeInTheDocument()
    expect(screen.getAllByText('supportStatus: BLOCKED').length).toBeGreaterThan(0)
    expect(screen.getAllByText('supportStatus: NATIVE').length).toBeGreaterThan(0)
    expect(screen.getByText('normalizedPath: /v1/files')).toBeInTheDocument()
    expect(screen.getByText('normalizedPath: /v1/embeddings')).toBeInTheDocument()
    expect(screen.getByText('normalizedPath: /v1/audio/transcriptions')).toBeInTheDocument()
    expect(screen.getByText('normalizedPath: /v1/images/generations')).toBeInTheDocument()
    expect(screen.getByText('normalizedPath: /v1/moderations')).toBeInTheDocument()
    expect(screen.getByText('normalizedPath: /v1/images/edits')).toBeInTheDocument()
    expect(screen.getByText('featureSupport: file_object:BLOCKED')).toBeInTheDocument()
    expect(screen.getByText('featureSupport: embeddings:BLOCKED')).toBeInTheDocument()
    expect(screen.getByText('featureSupport: audio_transcription:NATIVE')).toBeInTheDocument()
    expect(screen.getByText('featureSupport: image_generation:NATIVE')).toBeInTheDocument()
    expect(screen.getByText('featureSupport: moderation:NATIVE')).toBeInTheDocument()
  })
})
