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
          protocolEndpoints: [
            {
              id: null,
              siteProfileId: null,
              endpointCode: 'xiaomi_mimo:openai-compatible',
              displayName: 'Xiaomi MiMo OpenAI-compatible',
              protocolSuite: 'xiaomi_mimo.openai_compatible',
              providerType: 'OPENAI_COMPATIBLE',
              siteKind: 'OPENAI_COMPATIBLE_GENERIC',
              baseUrl: 'https://token-plan-sgp.xiaomimimo.com/v1',
              authStrategy: 'BEARER',
              pathStrategy: 'OPENAI_V1',
              modelAddressingStrategy: 'MODEL_NAME',
              errorSchemaStrategy: 'OPENAI_ERROR',
              streamTransport: 'sse',
              conversationProfile: {},
              active: true,
              linkedCredentialCount: 0,
              createdAt: null,
              updatedAt: null,
            },
            {
              id: null,
              siteProfileId: null,
              endpointCode: 'xiaomi_mimo:anthropic-compatible',
              displayName: 'Xiaomi MiMo Anthropic-compatible',
              protocolSuite: 'xiaomi_mimo.anthropic_compatible',
              providerType: 'ANTHROPIC_DIRECT',
              siteKind: 'ANTHROPIC_DIRECT',
              baseUrl: 'https://token-plan-sgp.xiaomimimo.com/anthropic',
              authStrategy: 'API_KEY_HEADER',
              pathStrategy: 'ANTHROPIC_V1',
              modelAddressingStrategy: 'MODEL_NAME',
              errorSchemaStrategy: 'ANTHROPIC_ERROR',
              streamTransport: 'sse',
              conversationProfile: {},
              active: true,
              linkedCredentialCount: 0,
              createdAt: null,
              updatedAt: null,
            },
          ],
          imported: false,
          existingSiteProfileId: null,
        },
      ]
    }
    if (url === '/admin/provider-sites/domain-catalog' && !init?.method) {
      return sampleDomainCatalog()
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
  it('renders a single vendor catalog and creates custom provider site', async () => {
    renderPage()

    expect(await screen.findByText('OpenAI 主站')).toBeInTheDocument()
    expect(await screen.findByText('OpenAI 生产组')).toBeInTheDocument()
    expect(await screen.findByText('1 Key 绑定')).toBeInTheDocument()
    expect(screen.queryByText('厂商管理中心')).not.toBeInTheDocument()
    expect(await screen.findByText('MiMo OpenAI 入口')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: '厂商目录' })).toBeInTheDocument()
    expect(await screen.findByRole('columnheader', { name: '厂商 / API 入口' })).toBeInTheDocument()
    expect(screen.getByText('可导入')).toBeInTheDocument()
    expect(screen.queryByText('厂商聚合')).not.toBeInTheDocument()
    expect(screen.queryByText('预设导入')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '编辑' })).not.toBeInTheDocument()

    const openAiRow = screen.getByText('OpenAI 主站').closest('tr')
    expect(openAiRow).not.toBeNull()
    expect(within(openAiRow!).getByRole('button', { name: '管理' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '新增自定义入口' }))
    const dialog = await screen.findByRole('dialog', { name: '新增自定义 API 入口' })
    expect(within(dialog).getByRole('tab', { name: '1. 基本信息' })).toBeInTheDocument()
    expect(within(dialog).getByRole('tab', { name: '2. 连接方式' })).toBeInTheDocument()
    expect(within(dialog).getByRole('tab', { name: '3. 高级配置' })).toBeInTheDocument()
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
    expect(await screen.findByText('xiaomi_mimo.openai_compatible')).toBeInTheDocument()
    expect(await screen.findByText('xiaomi_mimo.anthropic_compatible')).toBeInTheDocument()
    expect(screen.getAllByText('ANTHROPIC_DIRECT').length).toBeGreaterThan(0)
    const mimoRow = screen.getByText('MiMo OpenAI 入口').closest('tr')
    expect(mimoRow).not.toBeNull()
    fireEvent.click(within(mimoRow!).getByRole('button', { name: '导入' }))

    const openAiRow = screen.getByText('OpenAI 主站').closest('tr')
    expect(openAiRow).not.toBeNull()
    fireEvent.click(within(openAiRow!).getByRole('button', { name: '刷新' }))

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

function sampleDomainCatalog() {
  return {
    generatedAt: '2026-05-23T10:00:00Z',
    summary: {
      vendorCount: 1,
      protocolEndpointCount: 1,
      accountGroupCount: 1,
      credentialCount: 1,
      distributedKeyBindingCount: 1,
    },
    vendors: [
      {
        siteProfileId: 1,
        profileCode: 'site:openai_direct',
        displayName: 'OpenAI 主站',
        vendorCode: 'openai',
        vendorName: 'OpenAI',
        providerFamily: 'OPENAI',
        siteKind: 'OPENAI_DIRECT',
        active: true,
        healthState: 'READY',
        linkedCredentialCount: 1,
        modelCount: 2,
        protocolEndpoints: [
          {
            id: 11,
            endpointCode: 'openai:openai-compatible',
            displayName: 'OpenAI 默认入口',
            protocolSuite: 'openai.native',
            providerType: 'OPENAI_DIRECT',
            siteKind: 'OPENAI_DIRECT',
            baseUrl: 'https://api.openai.com',
            active: true,
            linkedCredentialCount: 1,
            accountGroupIds: [1],
          },
        ],
        accountGroups: [
          {
            id: 1,
            groupName: 'OpenAI 生产组',
            providerType: 'OPENAI_OAUTH',
            groupKind: 'ENVIRONMENT',
            groupKindSource: 'name_heuristic',
            defaultGroup: false,
            active: true,
            supportedModels: ['gpt-4o'],
            supportedProtocols: ['openai'],
            allowedClientFamilies: ['GENERIC_OPENAI'],
            apiCredentialCount: 1,
            endpointCoverage: [
              {
                endpointId: 11,
                endpointCode: 'openai:openai-compatible',
                displayName: 'OpenAI 默认入口',
                protocolSuite: 'openai.native',
                credentialCount: 1,
                source: 'credential_protocol_endpoint_id',
              },
            ],
            credentials: [
              {
                id: 501,
                credentialName: 'OpenAI Key 1',
                providerType: 'OPENAI_DIRECT',
                siteProfileId: 1,
                protocolEndpointId: 11,
                groupId: 1,
                active: true,
                cooldown: false,
                status: 'READY',
                supportedModelCount: 1,
                lastErrorCode: null,
                lastErrorMessage: null,
                cooldownUntil: null,
                lastUsedAt: null,
              },
            ],
            distributedKeyBindings: [
              {
                bindingId: 801,
                distributedKeyId: 701,
                keyName: '客户 A Key',
                keyPrefix: 'xagw_live',
                providerType: 'OPENAI_DIRECT',
                priority: 10,
                bindingActive: true,
                distributedKeyActive: true,
              },
            ],
          },
        ],
      },
    ],
    unassignedAccountGroups: [],
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
    protocolEndpoints: [
      {
        id: 11,
        siteProfileId: 1,
        endpointCode: 'openai:openai-compatible',
        displayName: 'OpenAI 默认入口',
        protocolSuite: 'openai.native',
        providerType: 'OPENAI_DIRECT',
        siteKind: 'OPENAI_DIRECT',
        baseUrl: 'https://api.openai.com',
        authStrategy: 'BEARER',
        pathStrategy: 'OPENAI_V1',
        modelAddressingStrategy: 'MODEL_NAME',
        errorSchemaStrategy: 'OPENAI_ERROR',
        streamTransport: 'sse',
        conversationProfile: {},
        active: true,
        linkedCredentialCount: 1,
        createdAt: '2026-05-22T00:00:00Z',
        updatedAt: '2026-05-22T00:00:00Z',
      },
    ],
  }
}
