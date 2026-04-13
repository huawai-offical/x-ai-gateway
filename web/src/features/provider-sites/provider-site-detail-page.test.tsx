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
      },
    ]
  }
  throw new Error(`unexpected url: ${url}`)
})

const sampleSite = {
  id: 1,
  profileCode: 'site:openai_direct',
  displayName: 'OPENAI_DIRECT',
  providerFamily: 'OPENAI',
  siteKind: 'OPENAI_DIRECT',
  authStrategy: 'BEARER',
  pathStrategy: 'OPENAI_V1',
  modelAddressingStrategy: 'MODEL_NAME',
  errorSchemaStrategy: 'OPENAI_ERROR',
  baseUrlPattern: 'https://api.openai.com',
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
    response_object: {
      declaredLevel: 'EMULATED',
      implementedLevel: 'EMULATED',
      effectiveLevel: 'EMULATED',
      blockedReasons: [],
      lossReasons: [],
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
        <MemoryRouter initialEntries={['/provider-sites/1?feature=response_object']}>
          <Routes>
            <Route path="/provider-sites/:id" element={<ProviderSiteDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('surface: openai')).toBeInTheDocument()
    expect(screen.getAllByText('gpt-4o').length).toBeGreaterThan(0)
    expect(screen.queryByText('chat-only')).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('displayName'), {
      target: { value: 'OPENAI_DIRECT_EDITED' },
    })
    fireEvent.click(screen.getByRole('button', { name: '保存站点档案' }))

    await waitFor(() => {
      expect(apiRequest).toHaveBeenCalledWith(
        '/admin/provider-sites/1',
        expect.objectContaining({
          method: 'PUT',
          body: expect.stringContaining('OPENAI_DIRECT_EDITED'),
        }),
      )
    })
  })
})
