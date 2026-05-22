// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ProviderSitesPage } from './provider-sites-page'

const { apiRequestMock } = vi.hoisted(() => ({
  apiRequestMock: vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/admin/provider-sites' && !init?.method) {
      return [sampleSite()]
    }
    if (url === '/admin/provider-sites/presets' && !init?.method) {
      return [
        {
          code: 'mimo-openai',
          profileCode: 'site:mimo_openai',
          displayName: 'MiMo OpenAI 入口',
          vendorCode: 'xiaomi_mimo',
          vendorName: '小米 MiMo',
          siteKind: 'OPENAI_COMPATIBLE_GENERIC',
          providerFamily: 'OPENAI',
          authStrategy: 'BEARER',
          pathStrategy: 'OPENAI_V1',
          modelAddressingStrategy: 'MODEL_NAME',
          errorSchemaStrategy: 'OPENAI_ERROR',
          defaultBaseUrl: 'https://token-plan-sgp.xiaomimimo.com/v1',
          description: 'MiMo OpenAI-compatible',
          supportedProtocols: ['openai'],
          streamTransport: 'sse',
          fallbackStrategy: 'provider-native',
          capabilityTags: ['chat'],
          costProfile: 'third_party',
          errorMode: 'openai',
          catalogVersion: '2026-05-22',
          catalogSource: 'local',
          deprecated: false,
          conformanceChecks: [],
          compatibilitySurface: 'openai',
          supportStrategy: 'compatible',
          modelFamilies: ['mimo'],
          pricingMetadata: '{}',
          unsupportedFeatures: [],
          imported: false,
          existingSiteProfileId: null,
        },
      ]
    }
    if (url === '/admin/provider-sites' && init?.method === 'POST') {
      return sampleSite({ displayName: 'DeepSeek OpenAI 入口', vendorCode: 'deepseek', vendorName: 'DeepSeek' })
    }
    if (url === '/admin/provider-sites/1/refresh' && init?.method === 'POST') {
      return sampleSite()
    }
    if (url === '/admin/provider-sites/presets/mimo-openai/import' && init?.method === 'POST') {
      return sampleSite({ displayName: 'MiMo OpenAI 入口', vendorCode: 'xiaomi_mimo', vendorName: '小米 MiMo' })
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
  apiRequestMock.mockClear()
})

describe('ProviderSitesPage', () => {
  it('renders vendor center and creates provider site', async () => {
    renderPage()

    expect(await screen.findByText('厂商管理中心')).toBeInTheDocument()
    expect(await screen.findByText('OpenAI 主站')).toBeInTheDocument()
    expect(await screen.findByText('MiMo OpenAI 入口')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '新增 API 入口' }))
    const dialog = await screen.findByRole('dialog', { name: '新增 API 入口' })
    fireEvent.change(within(dialog).getByLabelText('入口编码'), { target: { value: 'site:deepseek_openai' } })
    fireEvent.change(within(dialog).getByLabelText('入口名称'), { target: { value: 'DeepSeek OpenAI 入口' } })
    fireEvent.change(within(dialog).getByLabelText('厂商编码'), { target: { value: 'deepseek' } })
    fireEvent.change(within(dialog).getByLabelText('厂商名称'), { target: { value: 'DeepSeek' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '保存入口' }))

    await waitFor(() => {
      const createCall = apiRequestMock.mock.calls.find(([url, init]) => url === '/admin/provider-sites' && init?.method === 'POST')
      expect(createCall).toBeTruthy()
    })
  })

  it('imports preset and refreshes a site', async () => {
    renderPage()

    await screen.findByText('OpenAI 主站')
    fireEvent.click(await screen.findByRole('button', { name: '导入' }))
    fireEvent.click(await screen.findByRole('button', { name: '刷新' }))

    await waitFor(() => {
      expect(apiRequestMock).toHaveBeenCalledWith('/admin/provider-sites/presets/mimo-openai/import', expect.objectContaining({ method: 'POST' }))
      expect(apiRequestMock).toHaveBeenCalledWith('/admin/provider-sites/1/refresh', expect.objectContaining({ method: 'POST' }))
    })
  })
})

function renderPage() {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient()}>
        <ProviderSitesPage />
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

function sampleSite(patch: Partial<ReturnType<typeof baseSite>> = {}) {
  return {
    ...baseSite(),
    ...patch,
  }
}

function baseSite() {
  return {
    id: 1,
    profileCode: 'site:openai_direct',
    displayName: 'OpenAI 主站',
    vendorCode: 'openai',
    vendorName: 'OpenAI',
    providerFamily: 'OPENAI',
    siteKind: 'OPENAI_DIRECT',
    authStrategy: 'BEARER',
    pathStrategy: 'OPENAI_V1',
    modelAddressingStrategy: 'MODEL_NAME',
    errorSchemaStrategy: 'OPENAI_ERROR',
    baseUrlPattern: 'https://api.openai.com',
    description: 'OpenAI official',
    conversationProfile: { reasoningContentMode: 'passthrough' },
    profileSource: 'MANUAL',
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
    linkedCredentialCount: 1,
    hasSnapshot: true,
    preferredBackend: 'SPRING_AI',
    supportedBackends: ['SPRING_AI'],
    features: {},
    surfaces: {},
    modelCount: 2,
    refreshedAt: '2026-05-22T00:00:00Z',
    createdAt: '2026-05-22T00:00:00Z',
    updatedAt: '2026-05-22T00:00:00Z',
  }
}
