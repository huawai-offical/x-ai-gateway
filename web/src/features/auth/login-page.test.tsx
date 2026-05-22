// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppProviders } from '@/app/providers'
import { AUTH_LOGIN_NOTICE_STORAGE_KEY } from './auth-provider'
import { LoginPage } from './login-page'

const fetchMock = vi.fn<typeof fetch>()
const mockedToast = vi.hoisted(() => ({
  info: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
}))

vi.mock('sonner', async () => {
  const actual = await vi.importActual<typeof import('sonner')>('sonner')
  return {
    ...actual,
    toast: mockedToast,
  }
})

function renderLoginPage(initialEntry = '/login?redirect=/workbench') {
  return render(
    <AppProviders>
      <MemoryRouter initialEntries={[initialEntry]}>
        <LocationProbe />
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/workbench" element={<div>调试工作台落点</div>} />
        </Routes>
      </MemoryRouter>
    </AppProviders>,
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    mockedToast.info.mockReset()
    mockedToast.success.mockReset()
    mockedToast.warning.mockReset()
    mockedToast.error.mockReset()
    fetchMock.mockImplementation(async (input, init) => {
      const pathname = new URL(String(input), 'http://localhost').pathname

      if (pathname === '/admin/auth/session') {
        return jsonResponse({
          authenticated: false,
          username: null,
          authenticatedAt: null,
          expiresAt: null,
        })
      }

      if (pathname === '/admin/auth/challenge') {
        return jsonResponse({
          challengeId: 'challenge-1',
          mathPrompt: '3 + 5 = ?',
          issuedAt: '2026-04-20T12:00:00Z',
          expiresAt: '2026-04-20T12:05:00Z',
          powAlgorithm: 'SHA-256',
          powSalt: 'salt-1',
          powDifficulty: 0,
        })
      }

      if (pathname === '/admin/auth/login') {
        const payload = JSON.parse(String(init?.body))
        expect(payload).toMatchObject({
          username: 'console-admin',
          password: 'secret-123',
          challengeId: 'challenge-1',
          mathAnswer: 8,
          powNonce: '0',
        })
        return jsonResponse({
          authenticated: true,
          username: 'console-admin',
          authenticatedAt: '2026-04-20T12:00:00Z',
          expiresAt: '2026-04-20T14:00:00Z',
        })
      }

      throw new Error(`Unhandled fetch request: ${pathname}`)
    })
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
    window.localStorage.clear()
    window.sessionStorage.clear()
  })

  it('loads challenge, solves POW and redirects after a successful login', async () => {
    renderLoginPage()

    expect(await screen.findByText('登录控制台')).toBeInTheDocument()
    expect(screen.queryByText('为控制台加上一层真正可用的登录门槛')).not.toBeInTheDocument()
    expect(await screen.findByText('3 + 5 = ?')).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getAllByText('POW 已就绪').length).toBeGreaterThan(0)
    })

    fireEvent.change(screen.getByLabelText('用户名'), {
      target: { value: 'console-admin' },
    })
    fireEvent.change(screen.getByLabelText('密码'), {
      target: { value: 'secret-123' },
    })
    fireEvent.change(screen.getByLabelText('数学验证码结果'), {
      target: { value: '8' },
    })

    fireEvent.click(screen.getByRole('button', { name: '登录并进入控制台' }))

    expect(await screen.findByText('调试工作台落点')).toBeInTheDocument()
  })

  it('shows logged out feedback as a toast and clears the reason param', async () => {
    renderLoginPage('/login?reason=logged-out&redirect=/workbench')

    await waitFor(() => {
      expect(mockedToast.info).toHaveBeenCalledWith(
        '你已安全退出控制台，如需继续操作请重新登录。',
        { duration: 5000 },
      )
    })

    await waitFor(() => {
      expect(screen.getByTestId('login-location')).not.toHaveTextContent('reason=')
    })
  })

  it('shows credentials updated feedback when redirected after a password rotation', async () => {
    window.localStorage.setItem('x-ai-gateway:last-admin-username', 'rotated-admin')

    renderLoginPage('/login?reason=credentials-updated')

    await waitFor(() => {
      expect(mockedToast.success).toHaveBeenCalledWith(
        '控制台凭证已更新，请使用新账号重新登录。当前推荐用户名：rotated-admin。',
        { duration: 5000 },
      )
    })
  })

  it('shows expired session feedback from session storage', async () => {
    window.sessionStorage.setItem(AUTH_LOGIN_NOTICE_STORAGE_KEY, 'expired')

    renderLoginPage('/login?redirect=/workbench')

    await waitFor(() => {
      expect(mockedToast.warning).toHaveBeenCalledWith(
        '控制台会话已失效或被清除，请重新完成登录校验。',
        { duration: 5000 },
      )
    })
  })

  it('shows validation feedback as an error toast when the math answer is invalid', async () => {
    renderLoginPage()

    await waitFor(() => {
      expect(screen.getAllByText('POW 已就绪').length).toBeGreaterThan(0)
    })

    fireEvent.change(screen.getByLabelText('用户名'), {
      target: { value: 'console-admin' },
    })
    fireEvent.change(screen.getByLabelText('密码'), {
      target: { value: 'secret-123' },
    })
    fireEvent.change(screen.getByLabelText('数学验证码结果'), {
      target: { value: 'abc' },
    })

    fireEvent.click(screen.getAllByRole('button', { name: '登录并进入控制台' })[0])

    await waitFor(() => {
      expect(mockedToast.error).toHaveBeenCalledWith(
        '请输入正确的数学验证码结果。',
        { duration: 5000 },
      )
    })
  })

  it('shows login failure as an error toast and keeps the user on the login page', async () => {
    fetchMock.mockImplementation(async (input) => {
      const pathname = new URL(String(input), 'http://localhost').pathname

      if (pathname === '/admin/auth/session') {
        return jsonResponse({
          authenticated: false,
          username: null,
          authenticatedAt: null,
          expiresAt: null,
        })
      }

      if (pathname === '/admin/auth/challenge') {
        return jsonResponse({
          challengeId: 'challenge-1',
          mathPrompt: '3 + 5 = ?',
          issuedAt: '2026-04-20T12:00:00Z',
          expiresAt: '2026-04-20T12:05:00Z',
          powAlgorithm: 'SHA-256',
          powSalt: 'salt-1',
          powDifficulty: 0,
        })
      }

      if (pathname === '/admin/auth/login') {
        return jsonResponse(
          {
            code: 'INVALID_ARGUMENT',
            message: '账号或密码不正确。',
          },
          401,
        )
      }

      throw new Error(`Unhandled fetch request: ${pathname}`)
    })

    renderLoginPage()

    await waitFor(() => {
      expect(screen.getAllByText('POW 已就绪').length).toBeGreaterThan(0)
    })

    fireEvent.change(screen.getByLabelText('用户名'), {
      target: { value: 'console-admin' },
    })
    fireEvent.change(screen.getByLabelText('密码'), {
      target: { value: 'secret-123' },
    })
    fireEvent.change(screen.getByLabelText('数学验证码结果'), {
      target: { value: '8' },
    })

    fireEvent.click(screen.getAllByRole('button', { name: '登录并进入控制台' })[0])

    await waitFor(() => {
      expect(mockedToast.error).toHaveBeenCalledWith(
        '账号或密码不正确。',
        { duration: 5000 },
      )
    })
    expect(screen.queryByText('调试工作台落点')).not.toBeInTheDocument()
  })
})

function LocationProbe() {
  const location = useLocation()

  return <div data-testid="login-location">{location.search || '(empty)'}</div>
}

function jsonResponse(payload: unknown, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
