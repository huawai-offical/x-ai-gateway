// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../../lib/api'
import { RunbooksPage } from './runbooks-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(),
}))

const mockedApiRequest = apiRequest as unknown as ReturnType<typeof vi.fn>

mockedApiRequest.mockImplementation(async (url: string, init?: RequestInit) => {
  if (url === '/admin/integrations/runbooks') {
    if (init?.method === 'POST') {
      return {
        id: 2,
        linkName: '新建排障文档',
        eventType: 'ALERT_OPENED',
        entityType: 'CREDENTIAL',
        linkUrl: 'https://example.com/new-runbook',
        enabled: true,
      }
    }
    return [
      {
        id: 1,
        linkName: '账号熔断处置',
        eventType: 'ALERT_OPENED',
        entityType: 'CREDENTIAL',
        linkUrl: 'https://example.com/runbook',
        enabled: true,
      },
    ]
  }
  if (url === '/admin/integrations/runbooks/1' && init?.method === 'DELETE') {
    return null
  }
  throw new Error(`unexpected url: ${url}`)
})

afterEach(() => {
  mockedApiRequest.mockClear()
})

describe('RunbooksPage', () => {
  it('renders runbook table and supports delete action', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <RunbooksPage />
      </QueryClientProvider>,
    )

    expect(await screen.findByRole('heading', { name: '排障文档链接' })).toBeInTheDocument()
    expect(await screen.findByText('账号熔断处置')).toBeInTheDocument()

    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    fireEvent.click(await screen.findByRole('button', { name: '删除' }))

    await waitFor(() => {
      expect(mockedApiRequest).toHaveBeenCalledWith(
        '/admin/integrations/runbooks/1',
        expect.objectContaining({ method: 'DELETE' }),
      )
    })
    confirmSpy.mockRestore()
  })
})
