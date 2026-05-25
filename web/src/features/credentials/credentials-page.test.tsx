// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '@/components/app/confirm-provider'
import { CredentialsPage } from './credentials-page'

const { apiRequestMock } = vi.hoisted(() => ({
  apiRequestMock: vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/admin/account-groups' && !init?.method) {
      return [
        {
          id: 1,
          groupName: '默认 OpenAI 分组',
          providerType: 'OPENAI_OAUTH',
          allowedClientFamilies: ['GENERIC_OPENAI'],
          supportedModels: [],
          defaultGroup: true,
        },
        {
          id: 2,
          groupName: 'Codex 分组',
          providerType: 'CODEX_OAUTH',
          allowedClientFamilies: ['CODEX'],
          supportedModels: ['gpt-5.4'],
          defaultGroup: false,
        },
      ]
    }
    if (url === '/admin/credentials/inventory' && !init?.method) {
      return [
        ...Array.from({ length: 5 }, (_, index) => ({
          sourceType: 'API_KEY',
          sourceId: index + 1,
          rowKey: `api-key:${index + 1}`,
          displayName: `Gemini AI Studio ${String(index + 1).padStart(2, '0')}`,
          providerType: 'GEMINI_DIRECT',
          authKind: 'API_KEY',
          baseUrl: 'https://generativelanguage.googleapis.com',
          supportedModels: ['gemini-2.5-pro'],
          secretFingerprint: `fp-${index + 1}`,
          metadata: {},
          active: true,
          siteProfileId: 20,
          protocolEndpointId: 201,
          groupId: 3,
          groupName: 'Gemini AI Studio',
          lastUsedAt: null,
          totalRequestCount: index === 0 ? 12 : 0,
          successfulRequestCount: index === 0 ? 10 : 0,
          failedRequestCount: index === 0 ? 2 : 0,
          totalTokenCount: index === 0 ? 4096 : 0,
          requestSuccessRate: index === 0 ? 0.83 : 0,
          avgDurationMs: index === 0 ? 456 : null,
          connectivityStatus: index === 0 ? 'AVAILABLE' : null,
          lastConnectivityTestAt: index === 0 ? '2026-05-25T10:30:00Z' : null,
          lastConnectivityLatencyMs: index === 0 ? 123 : null,
          lastConnectivityResponseSummary: index === 0 ? '200 OK from Gemini' : null,
          lastConnectivityUpstreamRequestId: index === 0 ? 'req-gemini-1' : null,
          lastConnectivityModel: index === 0 ? 'gemini-2.5-pro' : null,
        })),
        ...Array.from({ length: 15 }, (_, index) => ({
          sourceType: 'AUTH_JSON_ACCOUNT',
          sourceId: index + 101,
          rowKey: `account:${index + 101}`,
          displayName: `Codex 账号 ${String(index + 1).padStart(2, '0')}`,
          providerType: 'CODEX_OAUTH',
          authKind: 'OAUTH_TOKEN',
          supportedModels: ['gpt-5.4'],
          externalAccountId: `codex:user-${index + 1}`,
          metadata: {},
          active: true,
          frozen: false,
          healthy: true,
          refreshStatus: 'READY',
          groupId: 2,
          groupName: 'Codex 分组',
          lastUsedAt: null,
        })),
      ]
    }
    if (url === '/admin/network/proxies' && !init?.method) {
      return [
        { id: 9, proxyName: '香港代理', proxyUrl: 'http://127.0.0.1:7890', active: true },
      ]
    }
    if (url === '/admin/network/tls-profiles' && !init?.method) {
      return [
        { id: 8, profileName: 'Chrome 指纹', profileCode: 'chrome-stable', active: true },
      ]
    }
    if (url === '/admin/provider-sites' && !init?.method) {
      return [
        {
          id: 10,
          profileCode: 'site:openai',
          displayName: 'OpenAI API',
          vendorCode: 'openai',
          vendorName: 'OpenAI',
          providerFamily: 'OPENAI',
          siteKind: 'OPENAI_DIRECT',
          authStrategy: 'BEARER',
          pathStrategy: 'OPENAI_V1',
          modelAddressingStrategy: 'MODEL_NAME',
          errorSchemaStrategy: 'OPENAI_ERROR',
          baseUrlPattern: 'https://api.openai.com',
          protocolEndpoints: [
            {
              id: 101,
              siteProfileId: 10,
              endpointCode: 'openai:default',
              displayName: 'OpenAI 默认入口',
              protocolSuite: 'openai.native',
              providerType: 'OPENAI_DIRECT',
              siteKind: 'OPENAI_DIRECT',
              baseUrl: 'https://api.openai.com',
              authStrategy: 'BEARER',
              pathStrategy: 'OPENAI_V1',
              modelAddressingStrategy: 'MODEL_NAME',
              errorSchemaStrategy: 'OPENAI_ERROR',
              active: true,
              linkedCredentialCount: 0,
            },
            {
              id: 102,
              siteProfileId: 10,
              endpointCode: 'openai:anthropic',
              displayName: 'OpenAI Anthropic 入口',
              protocolSuite: 'openai.anthropic_compatible',
              providerType: 'ANTHROPIC_DIRECT',
              siteKind: 'ANTHROPIC_DIRECT',
              baseUrl: 'https://api.openai.com/anthropic',
              authStrategy: 'BEARER',
              pathStrategy: 'ANTHROPIC_V1_MESSAGES',
              modelAddressingStrategy: 'MODEL_NAME',
              errorSchemaStrategy: 'ANTHROPIC_ERROR',
              active: true,
              linkedCredentialCount: 0,
            },
          ],
          profileSource: 'PRESET',
          active: true,
          healthState: 'UNKNOWN',
          supportedProtocols: ['openai'],
          compatibilitySurface: 'openai',
          credentialRequirements: ['api_key'],
          cooldownCredentialCount: 0,
          linkedCredentialCount: 0,
          hasSnapshot: false,
          supportedBackends: ['SPRING_AI', 'NATIVE'],
          features: {},
          surfaces: {},
          modelCount: 0,
        },
        {
          id: 20,
          profileCode: 'site:gemini',
          displayName: 'Gemini API',
          vendorCode: 'gemini',
          vendorName: 'Gemini',
          providerFamily: 'GOOGLE',
          siteKind: 'GEMINI_DIRECT',
          authStrategy: 'API_KEY_QUERY',
          pathStrategy: 'GEMINI_V1BETA',
          modelAddressingStrategy: 'MODEL_NAME',
          errorSchemaStrategy: 'GOOGLE_ERROR',
          baseUrlPattern: 'https://generativelanguage.googleapis.com',
          protocolEndpoints: [
            {
              id: 201,
              siteProfileId: 20,
              endpointCode: 'gemini:default',
              displayName: 'Gemini 默认入口',
              protocolSuite: 'gemini.native',
              providerType: 'GEMINI_DIRECT',
              siteKind: 'GEMINI_DIRECT',
              baseUrl: 'https://generativelanguage.googleapis.com',
              authStrategy: 'API_KEY_QUERY',
              pathStrategy: 'GEMINI_V1BETA',
              modelAddressingStrategy: 'MODEL_NAME',
              errorSchemaStrategy: 'GOOGLE_ERROR',
              active: true,
              linkedCredentialCount: 0,
            },
          ],
          profileSource: 'PRESET',
          active: true,
          healthState: 'UNKNOWN',
          supportedProtocols: ['gemini'],
          compatibilitySurface: 'gemini',
          credentialRequirements: ['api_key_query'],
          cooldownCredentialCount: 0,
          linkedCredentialCount: 0,
          hasSnapshot: false,
          supportedBackends: ['SPRING_AI', 'NATIVE'],
          features: {},
          surfaces: {},
          modelCount: 0,
        },
      ]
    }
    if (typeof url === 'string' && url.startsWith('/admin/credentials/model-catalog')) {
      return ['gpt-5.4', 'gemini-2.5-pro']
    }
    if (typeof url === 'string' && url.startsWith('/admin/account-groups/model-catalog')) {
      return ['gpt-5.4', 'gpt-5.3-codex']
    }
    if (url === '/admin/credentials/1/connectivity-test' && init?.method === 'POST') {
      return {
        credentialId: 1,
        status: 'AVAILABLE',
        model: 'gemini-2.5-pro',
        upstreamRequestId: 'req-after-test',
        responseSummary: 'probe ok',
        latencyMs: 321,
        testedAt: '2026-05-25T11:00:00Z',
      }
    }
    if (url === '/admin/credentials/multi-endpoint' && init?.method === 'POST') {
      const payload = init.body ? JSON.parse(String(init.body)) : {}
      return (payload.protocolEndpointIds ?? [payload.protocolEndpointId]).map((protocolEndpointId: number, index: number) => ({
        id: index + 1,
        credentialName: payload.credentialName ?? 'OpenAI Key',
        providerType: payload.providerType ?? 'OPENAI_DIRECT',
        baseUrl: payload.baseUrl ?? 'https://api.openai.com',
        authKind: 'API_KEY',
        secretFingerprint: 'fp',
        credentialMetadata: {},
        active: true,
        protocolEndpointId,
      }))
    }
    if (url === '/admin/accounts/import-auth-json' && init?.method === 'POST') {
      const payload = init.body ? JSON.parse(String(init.body)) : {}
      return {
        id: 2,
        groupId: payload.groupId,
        accountName: payload.accountName ?? 'Codex auth',
        refreshStatus: payload.authJsonContent?.includes('refresh_token') ? 'READY' : 'ACCESS_ONLY',
      }
    }

    return []
  }),
}))

vi.mock('../../lib/api', () => ({
  apiRequest: apiRequestMock,
}))

afterEach(() => {
  cleanup()
})

describe('CredentialsPage', () => {
  it('renders static credentials and Codex auth.json accounts in one inventory', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmProvider>
          <MemoryRouter>
            <CredentialsPage />
          </MemoryRouter>
        </ConfirmProvider>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('Gemini AI Studio 01')).toBeInTheDocument()
    expect(await screen.findByText('Codex 账号 15')).toBeInTheDocument()
    expect(screen.getAllByText('auth.json 账号').length).toBeGreaterThan(0)
  })

  it('renders secret form and creates credential', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmProvider>
          <MemoryRouter>
            <CredentialsPage />
          </MemoryRouter>
        </ConfirmProvider>
      </QueryClientProvider>,
    )

    expect((await screen.findAllByText('已录入凭证')).length).toBeGreaterThan(0)
    fireEvent.click(screen.getByRole('button', { name: '新增上游凭证' }))
    const createDialog = await screen.findByRole('dialog', { name: '创建上游凭证' })

    fireEvent.change(within(createDialog).getByPlaceholderText('例如：OpenAI 主账号 Key'), { target: { value: 'OpenAI Key' } })
    fireEvent.click(within(createDialog).getByRole('button', { name: '下一步' }))
    fireEvent.click(await within(createDialog).findByLabelText(/OpenAI 默认入口/))
    fireEvent.click(await within(createDialog).findByLabelText(/OpenAI Anthropic 入口/))
    fireEvent.click(within(createDialog).getByRole('button', { name: '下一步' }))
    fireEvent.change(await within(createDialog).findByPlaceholderText('输入上游密钥'), { target: { value: 'secret-token' } })
    fireEvent.click(within(createDialog).getByRole('button', { name: '下一步' }))
    fireEvent.change(within(createDialog).getByRole('combobox', { name: '所属账号分组' }), { target: { value: '1' } })
    fireEvent.click(within(createDialog).getByRole('button', { name: '下一步' }))
    fireEvent.change(within(createDialog).getByRole('textbox', { name: '搜索代理' }), { target: { value: '香港' } })
    fireEvent.change(within(createDialog).getByRole('textbox', { name: '搜索TLS 指纹' }), { target: { value: 'Chrome' } })
    const proxySelect = within(createDialog).getByRole('combobox', { name: '代理' })
    const tlsSelect = within(createDialog).getByRole('combobox', { name: 'TLS 指纹' })
    await waitFor(() => {
      expect(within(proxySelect).getByRole('option', { name: /香港代理/ })).toBeInTheDocument()
      expect(within(tlsSelect).getByRole('option', { name: /Chrome 指纹/ })).toBeInTheDocument()
    })
    fireEvent.change(proxySelect, { target: { value: '9' } })
    fireEvent.change(within(createDialog).getByRole('combobox', { name: 'TLS 指纹' }), { target: { value: '8' } })
    fireEvent.click(within(createDialog).getByRole('button', { name: '下一步' }))
    fireEvent.click(await within(createDialog).findByLabelText('gpt-5.4'))
    fireEvent.click(within(createDialog).getByRole('button', { name: '下一步' }))
    fireEvent.click(within(createDialog).getByRole('button', { name: '创建凭证' }))

    await waitFor(() => {
      const call = apiRequestMock.mock.calls.find(([url, init]) => url === '/admin/credentials/multi-endpoint' && init?.method === 'POST')
      expect(call).toBeTruthy()
      expect(JSON.parse(String(call?.[1]?.body))).toMatchObject({
        siteProfileId: 10,
        protocolEndpointId: 101,
        protocolEndpointIds: [101, 102],
        providerType: 'OPENAI_DIRECT',
        baseUrl: 'https://api.openai.com',
        proxyId: 9,
        tlsFingerprintProfileId: 8,
        supportedModels: ['gpt-5.4'],
      })
    })
  })

  it('opens API Key row as merged detail edit dialog and runs saved connectivity test', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmProvider>
          <MemoryRouter>
            <CredentialsPage />
          </MemoryRouter>
        </ConfirmProvider>
      </QueryClientProvider>,
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Gemini AI Studio 01' }))
    const dialog = await screen.findByRole('dialog', { name: 'Gemini AI Studio 01' })

    expect(within(dialog).getByText('用量摘要')).toBeInTheDocument()
    expect(within(dialog).getByText('最近联通性探测')).toBeInTheDocument()
    expect(within(dialog).getByDisplayValue('Gemini AI Studio 01')).toBeInTheDocument()
    expect(within(dialog).getByText('4,096')).toBeInTheDocument()
    expect(within(dialog).getByText('200 OK from Gemini')).toBeInTheDocument()
    expect(within(dialog).getByText('req-gemini-1')).toBeInTheDocument()

    const inventoryCallsBefore = apiRequestMock.mock.calls.filter(([url, init]) => url === '/admin/credentials/inventory' && !init?.method).length
    fireEvent.click(within(dialog).getByRole('button', { name: '联通性测试' }))

    await waitFor(() => {
      expect(apiRequestMock).toHaveBeenCalledWith('/admin/credentials/1/connectivity-test', { method: 'POST' })
    })
    expect(await within(dialog).findByText(/联通性测试成功：probe ok/)).toBeInTheDocument()

    await waitFor(() => {
      const inventoryCallsAfter = apiRequestMock.mock.calls.filter(([url, init]) => url === '/admin/credentials/inventory' && !init?.method).length
      expect(inventoryCallsAfter).toBeGreaterThan(inventoryCallsBefore)
    })
  })

  it('supports batch secret import by newline text', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmProvider>
          <MemoryRouter>
            <CredentialsPage />
          </MemoryRouter>
        </ConfirmProvider>
      </QueryClientProvider>,
    )

    expect((await screen.findAllByText('已录入凭证')).length).toBeGreaterThan(0)
    fireEvent.click(screen.getByRole('button', { name: '新增上游凭证' }))
    const createDialog = await screen.findByRole('dialog', { name: '创建上游凭证' })

    fireEvent.change(within(createDialog).getByRole('combobox', { name: '创建模式' }), { target: { value: 'batch' } })
    fireEvent.change(within(createDialog).getByPlaceholderText('例如：OpenAI-Prod'), { target: { value: 'BatchOpenAI' } })
    fireEvent.click(within(createDialog).getByRole('button', { name: '下一步' }))
    fireEvent.click(await within(createDialog).findByLabelText(/OpenAI 默认入口/))
    fireEvent.click(within(createDialog).getByRole('button', { name: '下一步' }))
    fireEvent.change(await within(createDialog).findByRole('textbox', { name: '批量密钥文本（每行一条）' }), { target: { value: 'sk-a\nsk-b' } })
    fireEvent.click(within(createDialog).getByRole('button', { name: '下一步' }))
    fireEvent.change(within(createDialog).getByRole('combobox', { name: '所属账号分组' }), { target: { value: '1' } })
    fireEvent.click(within(createDialog).getByRole('button', { name: '下一步' }))
    fireEvent.click(within(createDialog).getByRole('button', { name: '下一步' }))
    fireEvent.click(within(createDialog).getByRole('button', { name: '下一步' }))
    fireEvent.click(within(createDialog).getByRole('button', { name: '批量创建' }))

    await waitFor(() => {
      const calls = apiRequestMock.mock.calls.filter(([url, init]) => url === '/admin/credentials/multi-endpoint' && init?.method === 'POST')
      expect(calls.length).toBeGreaterThanOrEqual(2)
    })
  })

  it('imports Codex auth.json by file paths from upstream credential dialog', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmProvider>
          <MemoryRouter>
            <CredentialsPage />
          </MemoryRouter>
        </ConfirmProvider>
      </QueryClientProvider>,
    )

    expect((await screen.findAllByText('已录入凭证')).length).toBeGreaterThan(0)
    fireEvent.click(screen.getByRole('button', { name: '新增上游凭证' }))
    const createDialog = await screen.findByRole('dialog', { name: '创建上游凭证' })

    fireEvent.change(within(createDialog).getByRole('combobox', { name: '凭证类型' }), { target: { value: 'codexAuthJson' } })
    fireEvent.change(within(createDialog).getByPlaceholderText('例如：Codex'), { target: { value: 'Codex 导入' } })
    fireEvent.click(within(createDialog).getByRole('button', { name: '下一步' }))
    fireEvent.click(within(createDialog).getByRole('button', { name: '下一步' }))
    fireEvent.change(await within(createDialog).findByRole('textbox', { name: 'auth.json 文件路径' }), {
      target: {
        value: 'C:/auth/a.json\nC:/auth/b.json',
      },
    })
    fireEvent.click(within(createDialog).getByRole('button', { name: '下一步' }))
    fireEvent.change(within(createDialog).getByRole('combobox', { name: '所属账号分组' }), { target: { value: '2' } })
    fireEvent.click(within(createDialog).getByRole('button', { name: '下一步' }))
    fireEvent.click(within(createDialog).getByRole('button', { name: '下一步' }))
    fireEvent.click(within(createDialog).getByRole('button', { name: '下一步' }))
    fireEvent.click(within(createDialog).getByRole('button', { name: '导入 auth.json' }))

    await waitFor(() => {
      const calls = apiRequestMock.mock.calls.filter(([url, init]) => url === '/admin/accounts/import-auth-json' && init?.method === 'POST')
      expect(calls).toHaveLength(2)
      expect(JSON.parse(String(calls[0][1]?.body))).toMatchObject({
        groupId: 2,
        accountName: 'Codex 导入-01',
        authJsonFilePath: 'C:/auth/a.json',
      })
      expect(JSON.parse(String(calls[0][1]?.body)).authJsonContent).toBeUndefined()
    })
  })
})
