// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CodexOnboardingPage } from './codex-onboarding-page'

const { apiRequestMock } = vi.hoisted(() => ({
  apiRequestMock: vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/admin/account-groups') {
      return [
        {
          id: 1,
          groupName: 'codex-group-main',
          providerType: 'CODEX_OAUTH',
          supportedModels: ['gpt-5.4@low'],
          supportedProtocols: ['openai', 'responses'],
          allowedClientFamilies: ['CODEX'],
          totalAccountCount: 2,
          active: true,
        },
      ]
    }
    if (url === '/admin/distributed-keys') {
      return [
        {
          id: 10,
          keyName: 'codex-cli-key',
          maskedKey: 'xag_***test',
          active: true,
          allowedProtocolSuites: ['openai', 'responses'],
          allowedModels: ['gpt-5.4@low'],
          allowedProviderTypes: ['OPENAI_DIRECT'],
          allowedClientFamilies: ['CODEX'],
          requireClientFamilyMatch: true,
        },
      ]
    }
    if (url === '/admin/client-instances?distributedKeyId=10') {
      return [
        {
          id: 21,
          distributedKeyId: 10,
          instanceId: 'codex-cli-default',
          displayName: 'Codex CLI 默认实例',
          clientFamily: 'CODEX',
          workspaceHint: 'default',
          status: 'ACTIVE',
        },
      ]
    }
    if (url.startsWith('/admin/distributed-keys/10/onboarding-pack')) {
      return {
        keyName: 'codex-cli-key',
        maskedKey: 'xag_***test',
        baseUrl: 'http://localhost:3000',
        apiBaseUrl: 'http://localhost:3000/v1',
        secretPolicy: 'one_time_only',
        clientConfigs: [
          {
            name: 'config.toml',
            clientFamily: 'CODEX',
            format: 'toml',
            content: 'base_url = "http://localhost:3000/v1"',
          },
        ],
        deepLinks: [],
        prompts: [],
        skills: [],
        smokeTests: ['codex exec --model gpt-5.4@low "ping"'],
        troubleshooting: [],
      }
    }
    if (url === '/admin/client-instances/21/authorizations' && init?.method === 'POST') {
      return {
        clientInstanceId: 21,
        instanceId: 'codex-cli-default',
        clientFamily: 'CODEX',
        grantToken: 'grant-token-for-test',
        expiresAt: '2026-05-07T03:00:00Z',
        consumed: false,
        revoked: false,
        deepLinkUrl: 'xag://codex/grant?token=grant-token-for-test',
        pluginMessageJson: '{"grantToken":"grant-token-for-test"}',
        warning: 'one time',
      }
    }
    throw new Error(`unexpected api url: ${url}`)
  }),
}))

vi.mock('../../lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/api')>()
  return {
    ...actual,
    apiRequest: apiRequestMock,
  }
})

function renderPage() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <CodexOnboardingPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(() => {
  cleanup()
  apiRequestMock.mockClear()
})

describe('CodexOnboardingPage', () => {
  it('renders guided Codex onboarding with snippets', async () => {
    renderPage()

    expect(await screen.findByText('Codex 接入向导')).toBeInTheDocument()
    expect((await screen.findAllByText(/codex-group-main/)).length).toBeGreaterThan(0)
    expect(await screen.findByText('config.toml')).toBeInTheDocument()
    expect(await screen.findByText(/codex exec --model/)).toBeInTheDocument()
  })

  it('issues a one-time Deep Link authorization for an existing key', async () => {
    renderPage()

    expect(await screen.findByText('Codex 接入向导')).toBeInTheDocument()
    expect(await screen.findByText('Codex CLI 默认实例')).toBeInTheDocument()
    fireEvent.change(await screen.findByLabelText('一次性导出令牌'), {
      target: { value: 'export-token-for-test' },
    })
    fireEvent.click(await screen.findByRole('button', { name: '发行一次性授权' }))

    await waitFor(() => {
      expect(apiRequestMock).toHaveBeenCalledWith(
        '/admin/client-instances/21/authorizations',
        expect.objectContaining({ method: 'POST' }),
      )
    })
    expect(await screen.findByText('Deep Link 链接')).toBeInTheDocument()
  })
})
