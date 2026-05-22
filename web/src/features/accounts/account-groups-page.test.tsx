// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AccountGroupsPage } from './account-groups-page'

const { apiRequestMock } = vi.hoisted(() => ({
  apiRequestMock: vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/admin/account-groups' && !init?.method) {
      return [
        {
          id: 1,
          groupName: 'OpenAI OAuth Group',
          providerType: 'OPENAI_OAUTH',
          supportedModels: ['gpt-4o'],
          supportedProtocols: ['openai'],
          allowedClientFamilies: ['GENERIC_OPENAI'],
          description: null,
          oauthAccountCount: 2,
          apiCredentialCount: 1,
          totalAccountCount: 3,
          active: true,
        },
      ]
    }
    if (url === '/admin/account-groups/1' && !init?.method) {
      return {
        id: 1,
        groupName: 'OpenAI OAuth Group',
        providerType: 'OPENAI_OAUTH',
        supportedModels: ['gpt-4o'],
        supportedProtocols: ['openai'],
        allowedClientFamilies: ['GENERIC_OPENAI'],
        description: 'default group',
        defaultGroup: true,
        oauthAccountCount: 2,
        apiCredentialCount: 1,
        totalAccountCount: 3,
        active: true,
        createdAt: '2026-04-20T10:00:00Z',
        updatedAt: '2026-04-21T10:00:00Z',
      }
    }
    if (url === '/admin/account-groups' && init?.method === 'POST') {
      return {
        id: 3,
        groupName: 'OpenAI OAuth Group',
        providerType: 'OPENAI_OAUTH',
        supportedModels: [],
        supportedProtocols: ['openai'],
        allowedClientFamilies: [],
        description: null,
        active: true,
      }
    }
    if (typeof url === 'string' && url.startsWith('/admin/account-groups/model-catalog')) {
      return ['gpt-4o', 'gpt-4.1', 'gpt-4o-mini']
    }
    if (url === '/admin/account-groups/1' && init?.method === 'PUT') {
      return {
        id: 1,
        groupName: 'OpenAI OAuth Group',
        providerType: 'OPENAI_OAUTH',
        supportedModels: ['gpt-4o', 'gpt-4.1'],
        supportedProtocols: ['openai', 'responses'],
        allowedClientFamilies: ['GENERIC_OPENAI', 'CODEX'],
        description: null,
        active: true,
      }
    }
    if (url === '/admin/accounts/import-auth-json' && init?.method === 'POST') {
      return {
        id: Math.floor(Math.random() * 1000),
        accountName: 'imported-account',
        healthy: true,
        frozen: false,
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
})

describe('AccountGroupsPage', () => {
  it('renders oauth/session form and creates account group', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <AccountGroupsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect((await screen.findAllByText('账号分组与治理')).length).toBeGreaterThan(0)
    fireEvent.click(screen.getByRole('button', { name: '创建账号分组' }))
    const dialog = await screen.findByRole('dialog', { name: '创建账号分组' })
    fireEvent.change(within(dialog).getByPlaceholderText('例如：OpenAI OAuth 分组'), { target: { value: 'OpenAI OAuth Group' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '创建账号分组' }))

    await waitFor(() => {
      expect(apiRequestMock).toHaveBeenCalledWith(
        '/admin/account-groups',
        expect.objectContaining({
          method: 'POST',
        }),
      )
    })
  })

  it('supports batch oauth/session import in list page', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <AccountGroupsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect((await screen.findAllByText('账号分组与治理')).length).toBeGreaterThan(0)
    fireEvent.click(screen.getByRole('button', { name: '导入官方账号' }))
    fireEvent.change(screen.getByRole('combobox', { name: '导入模式' }), { target: { value: 'batch' } })
    fireEvent.click(screen.getByRole('button', { name: '下一步' }))
    expect(screen.getByRole('textbox', { name: '批量文本（支持 JSON 数组 / 每行 JSON / 每行 token）' })).toBeInTheDocument()
  })

  it('renders table view and links to the unified detail page', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <AccountGroupsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect((await screen.findAllByText('账号分组与治理')).length).toBeGreaterThan(0)
    expect(await screen.findByRole('table')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '查看' })).toHaveAttribute('href', '/console/account-groups/1')
    expect(screen.queryByRole('dialog', { name: '账号分组详情' })).not.toBeInTheDocument()
  })
})
