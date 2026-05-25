// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ProviderSiteDetailPage } from './provider-site-detail-page'

const { apiRequestMock } = vi.hoisted(() => ({
  apiRequestMock: vi.fn(async (url: string, init?: RequestInit) => {
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
        protocolEndpoints: [
          {
            id: 11,
            siteProfileId: 1,
            endpointCode: 'xiaomi_mimo:openai-compatible',
            displayName: 'MiMo OpenAI-compatible',
            protocolSuite: 'xiaomi_mimo.openai_compatible',
            providerType: 'OPENAI_COMPATIBLE',
            siteKind: 'OPENAI_COMPATIBLE_GENERIC',
            baseUrl: 'https://token-plan-sgp.xiaomimimo.com/v1',
            authStrategy: 'BEARER',
            pathStrategy: 'OPENAI_V1',
            modelAddressingStrategy: 'MODEL_NAME',
            errorSchemaStrategy: 'OPENAI_ERROR',
            conversationProfile: {
              upstreamSurface: 'chat_completions',
              protocolEndpoint: 'openai_compatible',
              customFlag: 'keep',
            },
            active: true,
            linkedCredentialCount: 1,
          },
          {
            id: 12,
            siteProfileId: 1,
            endpointCode: 'xiaomi_mimo:anthropic-compatible',
            displayName: 'MiMo Anthropic-compatible',
            protocolSuite: 'xiaomi_mimo.anthropic_compatible',
            providerType: 'ANTHROPIC_DIRECT',
            siteKind: 'ANTHROPIC_DIRECT',
            baseUrl: 'https://token-plan-sgp.xiaomimimo.com/anthropic',
            authStrategy: 'API_KEY_HEADER',
            pathStrategy: 'ANTHROPIC_V1_MESSAGES',
            modelAddressingStrategy: 'MODEL_NAME',
            errorSchemaStrategy: 'ANTHROPIC_ERROR',
            conversationProfile: { targetProtocol: 'anthropic_messages' },
            active: true,
            linkedCredentialCount: 0,
          },
        ],
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
            requiredFeatures: ['response_object'],
            featureResolutions: {
              response_object: {
                declaredLevel: 'NATIVE',
                implementedLevel: 'EMULATED',
                effectiveLevel: 'EMULATED',
                supportStatus: 'emulated',
                blockedReasons: [],
                lossReasons: [],
              },
            },
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
    if (url === '/admin/provider-sites/1/protocol-endpoints' && init?.method === 'POST') {
      return {
        id: 13,
        siteProfileId: 1,
        displayName: 'MiMo Custom',
      }
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

describe('ProviderSiteDetailPage', () => {
  it('renders provider site detail with scoped tabs', async () => {
    renderPage()

    expect(await screen.findByText('MiMo OpenAI 入口')).toBeInTheDocument()
    expect(await screen.findByText('小米 MiMo')).toBeInTheDocument()
    expect(await screen.findByText('调用与兼容画像')).toBeInTheDocument()
    expect(screen.queryByText('mimo-v2-omni')).not.toBeInTheDocument()
    expect(screen.queryByText('chat_completion')).not.toBeInTheDocument()
    expect(screen.queryByText('response_object')).not.toBeInTheDocument()

    const endpointsTab = screen.getByRole('tab', { name: '协议入口' })
    fireEvent.mouseDown(endpointsTab)
    fireEvent.click(endpointsTab)
    expect(await screen.findByText('xiaomi_mimo.openai_compatible')).toBeInTheDocument()
    expect(await screen.findByText('xiaomi_mimo.anthropic_compatible')).toBeInTheDocument()

    const modelsTab = screen.getByRole('tab', { name: '模型能力' })
    fireEvent.mouseDown(modelsTab)
    fireEvent.click(modelsTab)
    expect((await screen.findAllByText('mimo-v2-omni')).length).toBeGreaterThan(0)

    const diagnosticsTab = screen.getByRole('tab', { name: '高级诊断' })
    fireEvent.mouseDown(diagnosticsTab)
    fireEvent.click(diagnosticsTab)
    expect(await screen.findByText('chat_completion')).toBeInTheDocument()
    expect(await screen.findByText('response_object')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '特性解析' })).not.toBeInTheDocument()
  })

  it('creates provider protocol endpoint from detail page', async () => {
    renderPage()

    expect(await screen.findByText('MiMo OpenAI 入口')).toBeInTheDocument()
    const endpointsTab = screen.getByRole('tab', { name: '协议入口' })
    fireEvent.mouseDown(endpointsTab)
    fireEvent.click(endpointsTab)
    fireEvent.click(await screen.findByRole('button', { name: '新增入口' }))
    const dialog = await screen.findByRole('dialog', { name: '新增协议入口' })
    expect(within(dialog).getByRole('tab', { name: '1. 基本信息' })).toBeInTheDocument()
    expect(within(dialog).getByRole('tab', { name: '2. 运行时策略' })).toBeInTheDocument()
    expect(within(dialog).getByRole('tab', { name: '3. 兼容画像' })).toBeInTheDocument()
    expect(within(dialog).queryByLabelText('Conversation Profile JSON')).not.toBeInTheDocument()

    fireEvent.change(within(dialog).getByLabelText('入口编码'), { target: { value: 'xiaomi_mimo:custom-anthropic' } })
    fireEvent.change(within(dialog).getByLabelText('显示名称'), { target: { value: 'MiMo Custom Anthropic' } })
    fireEvent.change(within(dialog).getByLabelText('协议簇'), { target: { value: 'xiaomi_mimo.anthropic_compatible' } })
    fireEvent.change(within(dialog).getByLabelText('Base URL'), { target: { value: 'https://token-plan-sgp.xiaomimimo.com/anthropic' } })
    const runtimeTab = within(dialog).getByRole('tab', { name: '2. 运行时策略' })
    fireEvent.mouseDown(runtimeTab)
    fireEvent.click(runtimeTab)
    expect(await within(dialog).findByLabelText('厂商类型')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('站点类型')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('鉴权策略')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('路径策略')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('模型寻址策略')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('错误结构策略')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('流式传输方式')).toBeInTheDocument()
    expect(within(dialog).queryByLabelText('Provider Type')).not.toBeInTheDocument()
    expect(within(dialog).queryByLabelText('Site Kind')).not.toBeInTheDocument()
    expect(within(dialog).queryByLabelText('Auth Strategy')).not.toBeInTheDocument()
    expect(within(dialog).queryByLabelText('Path Strategy')).not.toBeInTheDocument()
    expect(within(dialog).queryByLabelText('Model Addressing')).not.toBeInTheDocument()
    expect(within(dialog).queryByLabelText('Error Schema')).not.toBeInTheDocument()
    expect(within(dialog).queryByLabelText('Stream Transport')).not.toBeInTheDocument()
    fireEvent.change(within(dialog).getByLabelText('厂商类型'), { target: { value: 'ANTHROPIC_DIRECT' } })
    fireEvent.change(within(dialog).getByLabelText('站点类型'), { target: { value: 'ANTHROPIC_DIRECT' } })

    const profileTab = within(dialog).getByRole('tab', { name: '3. 兼容画像' })
    fireEvent.mouseDown(profileTab)
    fireEvent.click(profileTab)
    fireEvent.change(await within(dialog).findByLabelText('兼容画像'), { target: { value: 'anthropic_messages' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '保存入口' }))

    await waitFor(() => {
      const call = apiRequestMock.mock.calls.find(([url, init]) =>
        url === '/admin/provider-sites/1/protocol-endpoints' && init?.method === 'POST')
      expect(call).toBeTruthy()
      expect(JSON.parse(String(call?.[1]?.body))).toMatchObject({
        endpointCode: 'xiaomi_mimo:custom-anthropic',
        displayName: 'MiMo Custom Anthropic',
        protocolSuite: 'xiaomi_mimo.anthropic_compatible',
        providerType: 'ANTHROPIC_DIRECT',
        siteKind: 'ANTHROPIC_DIRECT',
        baseUrl: 'https://token-plan-sgp.xiaomimimo.com/anthropic',
        conversationProfile: {
          reasoningContentMode: 'passthrough',
          targetProtocol: 'anthropic_messages',
          reasoningTransport: 'thinking_blocks',
        },
      })
    })
  })

  it('edits protocol endpoint profile with structured controls and keeps unknown fields', async () => {
    renderPage()

    expect(await screen.findByText('MiMo OpenAI 入口')).toBeInTheDocument()
    const endpointsTab = screen.getByRole('tab', { name: '协议入口' })
    fireEvent.mouseDown(endpointsTab)
    fireEvent.click(endpointsTab)
    fireEvent.click((await screen.findAllByRole('button', { name: '编辑' }))[0])

    const dialog = await screen.findByRole('dialog', { name: '编辑协议入口' })
    const profileTab = within(dialog).getByRole('tab', { name: '3. 兼容画像' })
    fireEvent.mouseDown(profileTab)
    fireEvent.click(profileTab)

    expect(await within(dialog).findByText('运行时画像预览')).toBeInTheDocument()
    expect(within(dialog).queryByLabelText('Conversation Profile JSON')).not.toBeInTheDocument()
    expect(within(dialog).getByLabelText('兼容画像')).toHaveValue('openai_chat_completions')

    fireEvent.change(within(dialog).getByLabelText('兼容画像'), { target: { value: 'responses_to_chat_completions' } })
    fireEvent.change(within(dialog).getByLabelText('Thinking 注入'), { target: { value: 'extra_body_thinking_enabled' } })
    fireEvent.change(within(dialog).getByLabelText('Assistant Reasoning 字段'), { target: { value: 'reasoning_content' } })
    fireEvent.change(within(dialog).getByLabelText('工具历史回放'), { target: { value: 'required_when_tool_calls' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '保存入口' }))

    await waitFor(() => {
      const call = apiRequestMock.mock.calls.find(([url, init]) =>
        url === '/admin/provider-sites/1/protocol-endpoints/11' && init?.method === 'PUT')
      expect(call).toBeTruthy()
      expect(JSON.parse(String(call?.[1]?.body))).toMatchObject({
        conversationProfile: {
          protocolEndpoint: 'openai_compatible',
          customFlag: 'keep',
          ingressProtocol: 'responses',
          upstreamSurface: 'chat_completions',
          responsesCompatibility: {
            mode: 'emulate_with_chat_completions',
          },
          reasoning: {
            requestField: 'extra_body.thinking',
            requestEnabledValue: {
              type: 'enabled',
            },
            assistantReasoningField: 'reasoning_content',
            historyReplayPolicy: 'required_when_tool_calls',
          },
        },
      })
    })
  })
})

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/console/provider-sites/1']}>
      <QueryClientProvider client={new QueryClient()}>
        <Routes>
          <Route path="/console/provider-sites/:id" element={<ProviderSiteDetailPage />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  )
}
