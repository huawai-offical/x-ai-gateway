// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ProviderSitesPage } from './provider-sites-page'

const { apiRequest } = vi.hoisted(() => ({
  apiRequest: vi.fn(),
}))

apiRequest.mockImplementation(async (url: string) => {
  if (url === '/admin/provider-sites') {
    return [
      {
        id: 1,
        profileCode: 'site:openai_direct',
        displayName: 'OPENAI_DIRECT',
        providerFamily: 'OPENAI',
        siteKind: 'OPENAI_DIRECT',
        authStrategy: 'BEARER',
        pathStrategy: 'OPENAI_V1',
        modelAddressingStrategy: 'MODEL_NAME',
        errorSchemaStrategy: 'OPENAI_ERROR',
        active: true,
        healthState: 'READY',
        blockedReason: null,
        supportedProtocols: ['openai', 'responses'],
        compatibilitySurface: 'openai',
        credentialRequirements: ['api_key'],
        streamTransport: 'sse',
        fallbackStrategy: 'provider-native',
        cooldownCredentialCount: 0,
        cooldownUntil: null,
        features: {},
        modelCount: 2,
      },
      {
        id: 2,
        profileCode: 'site:vertex_ai',
        displayName: 'VERTEX_AI',
        providerFamily: 'GEMINI',
        siteKind: 'VERTEX_AI',
        authStrategy: 'BEARER',
        pathStrategy: 'GEMINI_V1BETA_MODELS',
        modelAddressingStrategy: 'MODEL_NAME',
        errorSchemaStrategy: 'GEMINI_ERROR',
        active: true,
        healthState: 'BLOCKED',
        blockedReason: 'credential metadata missing',
        supportedProtocols: ['google_native'],
        compatibilitySurface: 'google_native',
        credentialRequirements: ['google_access_token'],
        streamTransport: 'sse',
        fallbackStrategy: 'vertex-google-native',
        cooldownCredentialCount: 1,
        cooldownUntil: '2026-04-13T03:00:00Z',
        features: {},
        modelCount: 1,
      },
    ]
  }
  if (url === '/admin/provider-sites/refresh-capabilities') {
    return []
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

describe('ProviderSitesPage', () => {
  it('filters provider sites and refreshes selected rows', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <ProviderSitesPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('surface: openai')).toBeInTheDocument()
    expect(screen.getByLabelText('选择 VERTEX_AI')).toBeInTheDocument()

    fireEvent.change(screen.getByRole('combobox', { name: '健康状态' }), {
      target: { value: 'BLOCKED' },
    })

    expect(screen.queryByLabelText('选择 OPENAI_DIRECT')).not.toBeInTheDocument()
    expect(screen.getByLabelText('选择 VERTEX_AI')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('选择 VERTEX_AI'))
    fireEvent.click(screen.getByRole('button', { name: '刷新选中站点' }))

    await waitFor(() => {
      expect(apiRequest).toHaveBeenCalledWith(
        '/admin/provider-sites/refresh-capabilities',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ siteProfileIds: [2] }),
        }),
      )
    })
  })
})
