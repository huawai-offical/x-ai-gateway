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
        blockedReasons: [],
        lossReasons: [],
      },
    },
    surfaces: {
      response_create: {
        resourceType: 'RESPONSE',
        operation: 'RESPONSE_CREATE',
        executionCapabilityLevel: 'EMULATED',
        renderCapabilityLevel: 'EMULATED',
        overallCapabilityLevel: 'EMULATED',
        requiredFeatures: ['response_object'],
        featureResolutions: {
          response_object: {
            declaredLevel: 'EMULATED',
            implementedLevel: 'EMULATED',
            effectiveLevel: 'EMULATED',
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
      response_object: {
        declaredLevel: 'UNSUPPORTED',
        implementedLevel: 'UNSUPPORTED',
        effectiveLevel: 'UNSUPPORTED',
        blockedReasons: ['missing metadata'],
        lossReasons: [],
      },
    },
    surfaces: {
      response_create: {
        resourceType: 'RESPONSE',
        operation: 'RESPONSE_CREATE',
        executionCapabilityLevel: 'UNSUPPORTED',
        renderCapabilityLevel: 'EMULATED',
        overallCapabilityLevel: 'UNSUPPORTED',
        requiredFeatures: ['response_object'],
        featureResolutions: {
          response_object: {
            declaredLevel: 'UNSUPPORTED',
            implementedLevel: 'UNSUPPORTED',
            effectiveLevel: 'UNSUPPORTED',
            blockedReasons: ['missing metadata'],
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
    expect(screen.getByText('VERTEX_AI')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /RESPONSE_CREATE/ })).toHaveAttribute('href', '/provider-sites/2?surface=response_create')
  })
})
