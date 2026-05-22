// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../lib/api'
import { SystemSettingsPage } from './system-settings-page'

vi.mock('../../lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/api')>()
  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      get: vi.fn(),
      put: vi.fn(),
      post: vi.fn(),
    },
  }
})

const mockedApiGet = apiClient.get as unknown as ReturnType<typeof vi.fn>
const mockedApiPut = apiClient.put as unknown as ReturnType<typeof vi.fn>
const mockedApiPost = apiClient.post as unknown as ReturnType<typeof vi.fn>

const SAMPLE_SETTINGS = {
  upstreamCache: {
    enabled: true,
    stickyByDistributedKey: true,
    prefixAffinityEnabled: true,
    fingerprintAffinityEnabled: true,
    affinityTtl: 'PT20M',
    fingerprintMaxPrefixTokens: 2048,
    keyPrefix: 'cache:',
  },
  upstream: {
    sdkTimeoutMs: 180000,
    sdkStreamTimeoutMs: 600000,
    httpTimeoutMs: 180000,
    httpStreamTimeoutMs: 600000,
  },
  updatedAt: '2026-04-23T11:30:00Z',
}

beforeEach(() => {
  mockedApiGet.mockResolvedValue(SAMPLE_SETTINGS)
  mockedApiPut.mockResolvedValue({ ...SAMPLE_SETTINGS, updatedAt: '2026-04-23T12:00:00Z' })
  mockedApiPost.mockResolvedValue({ ...SAMPLE_SETTINGS, updatedAt: '2026-04-23T12:05:00Z' })
})

afterEach(() => {
  mockedApiGet.mockReset()
  mockedApiPut.mockReset()
  mockedApiPost.mockReset()
})

describe('SystemSettingsPage', () => {
  it('loads settings and supports save/reset actions', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <SystemSettingsPage />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('系统运行参数')).toBeInTheDocument()
    expect(await screen.findByText('上游缓存')).toBeInTheDocument()
    expect(await screen.findByText('上游运行时')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /保存参数/i }))

    await waitFor(() => {
      expect(mockedApiPut).toHaveBeenCalledWith(
        '/admin/settings',
        expect.objectContaining({
          body: expect.objectContaining({
            upstreamCache: expect.objectContaining({
              affinityTtl: 'PT20M',
            }),
          }),
        }),
      )
    })

    fireEvent.click(screen.getByRole('button', { name: /恢复默认/i }))

    await waitFor(() => {
      expect(mockedApiPost).toHaveBeenCalledWith('/admin/settings/reset')
    })
  })
})
