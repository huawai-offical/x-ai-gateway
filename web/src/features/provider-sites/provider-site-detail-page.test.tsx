// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../../lib/api'
import { ProviderSiteDetailPage } from './provider-site-detail-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(),
}))

const mockedApiRequest = apiRequest as unknown as ReturnType<typeof vi.fn>

const geminiDossier = {
  site: {
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
    features: {},
    modelCount: 2,
    refreshedAt: '2026-04-13T03:00:00Z',
    createdAt: '2026-04-13T02:00:00Z',
    updatedAt: '2026-04-13T03:00:00Z',
  },
  capabilities: [
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
      preferredBackend: 'ORCHESTRATION',
      supportedBackends: ['ORCHESTRATION'],
      surfaces: {
        file_create: {
          resourceType: 'FILE',
          operation: 'FILE_CREATE',
          surface: 'openai',
          normalizedPath: '/v1/files',
          supportStatus: 'NATIVE',
          degradationLevel: 'NATIVE',
          executionCapabilityLevel: 'NATIVE',
          renderCapabilityLevel: 'NATIVE',
          overallCapabilityLevel: 'NATIVE',
          blockerReasons: [],
          lossReasons: [],
          requiredFeatures: ['file_object'],
          featureResolutions: {},
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
      preferredBackend: 'NATIVE',
      supportedBackends: ['NATIVE'],
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
          lossReasons: ['render emulation'],
          requiredFeatures: ['chat_text'],
          featureResolutions: {},
        },
      },
    },
  ],
  blockedSurfaces: [
    {
      surfaceKey: 'upload_create',
      operation: 'UPLOAD_CREATE',
      normalizedPath: '/v1/uploads',
      supportStatus: 'BLOCKED',
      degradationLevel: 'UNSUPPORTED',
      overallCapabilityLevel: 'UNSUPPORTED',
      blockerReasons: ['Gemini Files API 存在，但不等价于 OpenAI /v1/uploads 的 create/parts/complete/cancel contract，因此仅开放 gateway-local orchestration surface。'],
      lossReasons: [],
    },
  ],
  degradedSurfaces: [
    {
      surfaceKey: 'response_create',
      operation: 'RESPONSE_CREATE',
      normalizedPath: '/v1/responses',
      supportStatus: 'DEGRADED',
      degradationLevel: 'EMULATED',
      overallCapabilityLevel: 'EMULATED',
      blockerReasons: ['response object fallback'],
      lossReasons: ['render emulation'],
    },
  ],
  acceptedExceptions: [
    {
      surfaceKey: 'realtime_client_secret_create',
      operation: 'REALTIME_CLIENT_SECRET_CREATE',
      normalizedPath: '/v1/realtime/client_secrets',
      supportStatus: 'BLOCKED',
      degradationLevel: 'UNSUPPORTED',
      overallCapabilityLevel: 'UNSUPPORTED',
      blockerReasons: ['Gemini ephemeral/live token 不等价于 OpenAI realtime client_secret object，因此当前不开放。'],
      lossReasons: [],
    },
  ],
  recommendedActions: ['优先检查 uploads/realtime 的 accepted exception，并从 Workbench 或 Trace 深链继续定位。'],
}

const openAiCompatibleDossier = {
  ...geminiDossier,
  site: {
    ...geminiDossier.site,
    id: 2,
    displayName: 'OPENAI_COMPATIBLE_GENERIC',
    providerFamily: 'OPENAI',
    siteKind: 'OPENAI_COMPATIBLE_GENERIC',
    compatibilitySurface: 'openai',
  },
  blockedSurfaces: [
    {
      surfaceKey: 'file_create',
      operation: 'FILE_CREATE',
      normalizedPath: '/v1/files',
      supportStatus: 'BLOCKED',
      degradationLevel: 'UNSUPPORTED',
      overallCapabilityLevel: 'UNSUPPORTED',
      blockerReasons: ['OpenAI-compatible 站点当前只冻结为 embeddings/audio/images/moderations 的 OpenAI-style 兼容面；files 仍作为 accepted exception，不在当前实现面内。'],
      lossReasons: [],
    },
  ],
  degradedSurfaces: [],
  acceptedExceptions: [
    {
      surfaceKey: 'upload_create',
      operation: 'UPLOAD_CREATE',
      normalizedPath: '/v1/uploads',
      supportStatus: 'BLOCKED',
      degradationLevel: 'UNSUPPORTED',
      overallCapabilityLevel: 'UNSUPPORTED',
      blockerReasons: ['OpenAI-compatible 站点当前只冻结为 embeddings/audio/images/moderations 的 OpenAI-style 兼容面；uploads 仍作为 accepted exception，不在当前实现面内。'],
      lossReasons: [],
    },
    {
      surfaceKey: 'batch_create',
      operation: 'BATCH_CREATE',
      normalizedPath: '/v1/batches',
      supportStatus: 'BLOCKED',
      degradationLevel: 'UNSUPPORTED',
      overallCapabilityLevel: 'UNSUPPORTED',
      blockerReasons: ['OpenAI-compatible 站点当前只冻结为 embeddings/audio/images/moderations 的 OpenAI-style 兼容面；batches 仍作为 accepted exception，不在当前实现面内。'],
      lossReasons: [],
    },
    {
      surfaceKey: 'tuning_create',
      operation: 'TUNING_CREATE',
      normalizedPath: '/v1/fine_tuning/jobs',
      supportStatus: 'BLOCKED',
      degradationLevel: 'UNSUPPORTED',
      overallCapabilityLevel: 'UNSUPPORTED',
      blockerReasons: ['OpenAI-compatible 站点当前只冻结为 embeddings/audio/images/moderations 的 OpenAI-style 兼容面；fine-tuning 仍作为 accepted exception，不在当前实现面内。'],
      lossReasons: [],
    },
    {
      surfaceKey: 'realtime_client_secret_create',
      operation: 'REALTIME_CLIENT_SECRET_CREATE',
      normalizedPath: '/v1/realtime/client_secrets',
      supportStatus: 'BLOCKED',
      degradationLevel: 'UNSUPPORTED',
      overallCapabilityLevel: 'UNSUPPORTED',
      blockerReasons: ['OpenAI-compatible 站点当前只冻结为 embeddings/audio/images/moderations 的 OpenAI-style 兼容面；realtime client secrets 仍作为 accepted exception，不在当前实现面内。'],
      lossReasons: [],
    },
  ],
}

mockedApiRequest.mockImplementation(async (url: string) => {
  if (url === '/admin/provider-sites/1/dossier') {
    return geminiDossier
  }
  if (url === '/admin/provider-sites/2/dossier') {
    return openAiCompatibleDossier
  }
  throw new Error(`unexpected url: ${url}`)
})

afterEach(() => {
  cleanup()
  mockedApiRequest.mockClear()
})

describe('ProviderSiteDetailPage', () => {
  it('renders dossier summary, surface tabs and deep links', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/provider-sites/1?surface=file_create']}>
          <Routes>
            <Route path="/provider-sites/:id" element={<ProviderSiteDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('GEMINI_DIRECT')).toBeInTheDocument()
    expect(screen.getByText('优先检查 uploads/realtime 的 accepted exception，并从 Workbench 或 Trace 深链继续定位。')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '编辑站点设置' })).toHaveAttribute('href', '/provider-sites/1/settings')
    expect(screen.getByRole('link', { name: '打开 Workbench' })).toHaveAttribute('href', '/workbench?protocol=openai&requestPath=%2Fv1%2Ffiles&requestedModel=gpt-4o')
    expect(screen.getByText('Blocked surfaces')).toBeInTheDocument()
    expect(screen.getByText('Accepted exceptions')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Surfaces' }))

    expect(await screen.findByText('UPLOAD_CREATE')).toBeInTheDocument()
    expect(screen.getByText('RESPONSE_CREATE')).toBeInTheDocument()
    expect(screen.getByText('REALTIME_CLIENT_SECRET_CREATE')).toBeInTheDocument()
    expect(
      screen.getAllByRole('link', { name: 'Workbench' }).some((link) => link.getAttribute('href') === '/workbench?protocol=openai&requestPath=%2Fv1%2Fuploads'),
    ).toBe(true)

    fireEvent.click(screen.getByRole('button', { name: 'Models' }))

    expect((await screen.findAllByText('gpt-4o')).length).toBeGreaterThan(0)
    expect(screen.queryByText('chat-only')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Trace links' }))

    expect(await screen.findByRole('link', { name: '打开 Traces' })).toHaveAttribute('href', '/traces?providerType=GEMINI_DIRECT&requestPath=%2Fv1%2Ffiles')
    expect(screen.getByRole('link', { name: '打开 Incidents' })).toHaveAttribute('href', '/incidents?entityType=SITE_PROFILE&entityRef=1')
  })

  it('shows openai-compatible accepted exceptions in dossier surfaces', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/provider-sites/2?surface=file_create']}>
          <Routes>
            <Route path="/provider-sites/:id" element={<ProviderSiteDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('OPENAI_COMPATIBLE_GENERIC')).toBeInTheDocument()
    expect(screen.getByText('Blocked surfaces')).toBeInTheDocument()
    expect(screen.getByText('Accepted exceptions')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Surfaces' }))

    expect(await screen.findByText('FILE_CREATE')).toBeInTheDocument()
    expect(screen.getByText('UPLOAD_CREATE')).toBeInTheDocument()
    expect(screen.getByText('BATCH_CREATE')).toBeInTheDocument()
    expect(screen.getByText('TUNING_CREATE')).toBeInTheDocument()
    expect(screen.getByText('REALTIME_CLIENT_SECRET_CREATE')).toBeInTheDocument()
    expect(screen.getAllByText(/accepted exception|不在当前实现面内/).length).toBeGreaterThan(0)
  })
})
