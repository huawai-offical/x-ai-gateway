// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppLayout } from './layout'
import { AppProviders } from './providers'

const fetchMock = vi.fn<typeof fetch>()

function renderLayout(initialEntry = '/console/workbench') {
  window.history.replaceState({}, '', initialEntry)
  return render(
    <AppProviders>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/console" element={<AppLayout />}>
            <Route path="workbench" element={<div>workbench</div>} />
            <Route path="keys" element={<div>keys</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AppProviders>,
  )
}

describe('AppLayout', () => {
  beforeEach(() => {
    window.localStorage.clear()
    fetchMock.mockReset()
    fetchMock.mockImplementation(async (input) => {
      const pathname = new URL(String(input), 'http://localhost').pathname
      if (pathname === '/admin/auth/session') {
        return new Response(
          JSON.stringify({
            authenticated: true,
            username: 'console-admin',
            authenticatedAt: '2026-04-20T12:00:00Z',
            expiresAt: '2026-04-20T14:00:00Z',
          }),
          {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          },
        )
      }

      throw new Error(`Unhandled fetch request: ${pathname}`)
    })
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders breadcrumb title without unused workspace switchers', async () => {
    renderLayout()

    expect(await screen.findByRole('heading', { name: '白盒调试工作台' })).toBeInTheDocument()
    expect(screen.getAllByText('管理控制台').length).toBeGreaterThan(0)
    expect(screen.getByTestId('app-shell-header')).toBeInTheDocument()
    expect(screen.getByTestId('app-shell-sidebar')).toHaveClass('h-full')
    expect(screen.getByTestId('app-shell-main')).toHaveClass('overflow-y-auto')
    expect(screen.getByTestId('app-shell-main')).toHaveClass('overscroll-contain')
    await waitFor(() => {
      expect(screen.getAllByText('console-admin')[0]).toBeInTheDocument()
    })
    expect(screen.queryByText('预发')).not.toBeInTheDocument()
    expect(screen.queryByText('运维')).not.toBeInTheDocument()
    expect(screen.queryByText('控制平面')).not.toBeInTheDocument()
  })

  it('persists collapsed sidebar state after toggling the desktop rail', async () => {
    renderLayout('/console/keys')

    expect(screen.getAllByText('任务：X-227').length).toBeGreaterThan(0)

    fireEvent.click(screen.getAllByRole('button', { name: '切换侧边栏' })[0])

    await waitFor(() => {
      expect(window.localStorage.getItem('x-ai-gateway:sidebar-collapsed')).toBe('true')
      expect(screen.queryAllByText('任务：X-227')).toHaveLength(1)
    })
  })

  it('opens the command palette with task-oriented console entries', async () => {
    renderLayout('/console/workbench')

    fireEvent.click((await screen.findAllByRole('button', { name: '搜索' }))[0])

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('控制台搜索')).toBeInTheDocument()

    fireEvent.change(screen.getByPlaceholderText('输入页面、密钥、账号分组、请求 ID 或客户端实例'), {
      target: { value: 'request' },
    })

    expect(await screen.findByText('按请求 ID 排查')).toBeInTheDocument()
    expect(screen.getAllByText('观测').length).toBeGreaterThan(0)
  })

  it('defaults to dark theme and persists theme changes', async () => {
    renderLayout('/console/workbench')

    await waitFor(() => {
      expect(document.documentElement).toHaveClass('dark')
    })

    fireEvent.click((await screen.findAllByRole('button', { name: '切换到浅色主题' }))[0])

    await waitFor(() => {
      expect(window.localStorage.getItem('x-ai-gateway:theme')).toBe('light')
    })
  })
})
