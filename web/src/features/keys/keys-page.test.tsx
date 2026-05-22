// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { KeysPage } from './keys-page'

const { apiRequestMock } = vi.hoisted(() => ({
  apiRequestMock: vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/admin/account-groups') {
      return [
        {
          id: 4,
          groupName: 'MiMo Group',
          providerType: 'OPENAI_OAUTH',
          allowedClientFamilies: ['GENERIC_OPENAI'],
          active: true,
          defaultGroup: true,
        },
      ]
    }
    if (url === '/admin/distributed-keys' && init?.method === 'POST') {
      return {
        record: {
          id: 9,
          keyName: 'MiMo Key',
          keyPrefix: 'sk-gw',
          active: true,
          allowedProtocolSuites: ['openai.native'],
          allowedModels: ['gpt-4o-mini'],
          allowedProviderTypes: ['OPENAI_DIRECT'],
          allowedClientFamilies: ['GENERIC_OPENAI'],
          requireClientFamilyMatch: true,
        },
        fullKey: 'sk-gw-full',
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

describe('KeysPage', () => {
  it('renders distributed key heading', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <KeysPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('策略对象列表')).toBeInTheDocument()
  })

  it('creates active key with initial account group binding', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <KeysPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    fireEvent.click(await screen.findByRole('button', { name: '创建访问密钥' }))
    fireEvent.change(await screen.findByPlaceholderText('例如：Production Default Key'), {
      target: { value: 'MiMo Key' },
    })
    const submitButtons = screen.getAllByRole('button', { name: '创建访问密钥' })
    fireEvent.click(submitButtons[submitButtons.length - 1])

    await waitFor(() => {
      const createCall = apiRequestMock.mock.calls.find(([url, init]) =>
        url === '/admin/distributed-keys' && init?.method === 'POST')
      expect(createCall).toBeTruthy()
      const payload = JSON.parse(String(createCall?.[1]?.body))
      expect(payload.initialAccountGroupBindings).toEqual([
        {
          groupId: 4,
          providerType: 'OPENAI_DIRECT',
          priority: 100,
          active: true,
        },
      ])
    })
  })
})
