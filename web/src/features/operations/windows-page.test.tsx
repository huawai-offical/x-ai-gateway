// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '@/components/app/confirm-provider'
import { apiRequest } from '../../lib/api'
import { WindowsPage } from './windows-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(),
}))

const mockedApiRequest = apiRequest as unknown as ReturnType<typeof vi.fn>

mockedApiRequest.mockImplementation(async (url: string, init?: RequestInit) => {
  if (url === '/admin/operations/maintenance-windows') {
    if (init?.method === 'POST') {
      return {
        id: 3,
        windowName: '灰度窗口',
        startsAt: '2026-04-18T22:30:00Z',
        endsAt: '2026-04-18T23:30:00Z',
        enabled: true,
        activeNow: false,
      }
    }
    return [
      {
        id: 2,
        windowName: '夜间窗口',
        scopeType: 'ALL',
        scopeRef: null,
        startsAt: '2026-04-18T22:00:00Z',
        endsAt: '2026-04-18T23:00:00Z',
        enabled: true,
        activeNow: true,
        description: 'night window',
      },
    ]
  }
  if (url === '/admin/operations/maintenance-windows/2' && init?.method === 'DELETE') {
    return null
  }
  throw new Error(`unexpected url: ${url}`)
})

afterEach(() => {
  mockedApiRequest.mockClear()
})

describe('WindowsPage', () => {
  it('renders maintenance windows and supports create/delete', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmProvider>
          <MemoryRouter>
            <WindowsPage />
          </MemoryRouter>
        </ConfirmProvider>
      </QueryClientProvider>,
    )

    expect(await screen.findByRole('heading', { name: '维护窗口' })).toBeInTheDocument()
    expect(await screen.findByText('夜间窗口')).toBeInTheDocument()
    expect((await screen.findAllByText('当前命中')).length).toBeGreaterThan(0)

    fireEvent.click(screen.getByRole('button', { name: '新增维护窗口' }))
    fireEvent.change(screen.getByPlaceholderText('例如：夜间升级窗口'), { target: { value: '灰度窗口' } })
    fireEvent.change(screen.getByPlaceholderText('2026-04-23T22:00:00Z'), { target: { value: '2026-04-18T22:30:00Z' } })
    fireEvent.change(screen.getByPlaceholderText('2026-04-23T23:00:00Z'), { target: { value: '2026-04-18T23:30:00Z' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() => {
      expect(mockedApiRequest).toHaveBeenCalledWith(
        '/admin/operations/maintenance-windows',
        expect.objectContaining({ method: 'POST' }),
      )
    })

    fireEvent.click(await screen.findByRole('button', { name: '删除' }))
    const confirmDialog = await screen.findByRole('dialog', { name: '删除维护窗口' })
    fireEvent.click(within(confirmDialog).getByRole('button', { name: '删除' }))
    await waitFor(() => {
      expect(mockedApiRequest).toHaveBeenCalledWith(
        '/admin/operations/maintenance-windows/2',
        expect.objectContaining({ method: 'DELETE' }),
      )
    })
  })
})
