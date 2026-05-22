// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppProviders } from '@/app/providers'
import { AuthSettingsPage } from './auth-settings-page'

const fetchMock = vi.fn<typeof fetch>()

function LoginDestination() {
  const location = useLocation()
  return <div>{`login destination ${location.search}`}</div>
}

function renderAuthSettingsPage() {
  return render(
    <AppProviders>
      <MemoryRouter initialEntries={['/settings/admin-auth']}>
        <Routes>
          <Route path="/settings/admin-auth" element={<AuthSettingsPage />} />
          <Route path="/login" element={<LoginDestination />} />
        </Routes>
      </MemoryRouter>
    </AppProviders>,
  )
}

describe('AuthSettingsPage', () => {
  beforeEach(() => {
    window.localStorage.clear()
    fetchMock.mockReset()
    fetchMock.mockImplementation(async (input, init) => {
      const pathname = new URL(String(input), 'http://localhost').pathname
      const method = init?.method ?? 'GET'

      if (pathname === '/admin/auth/session') {
        return jsonResponse({
          authenticated: true,
          username: 'console-admin',
          authenticatedAt: '2026-04-20T12:00:00Z',
          expiresAt: '2026-04-20T14:00:00Z',
        })
      }

      if (pathname === '/admin/auth/settings' && method === 'GET') {
        return jsonResponse({
          username: 'console-admin',
          persisted: true,
          credentialSource: 'RANDOM_BOOTSTRAP',
          initializedAt: '2026-04-20T08:00:00Z',
          updatedAt: '2026-04-20T08:15:00Z',
        })
      }

      if (pathname === '/admin/auth/settings' && method === 'PUT') {
        const payload = JSON.parse(String(init?.body))
        expect(payload).toMatchObject({
          username: 'rotated-admin',
          currentPassword: 'secret-123',
          newPassword: 'rotated-secret-456',
        })
        return jsonResponse({
          username: 'rotated-admin',
          persisted: true,
          credentialSource: 'MANUAL_UPDATE',
          initializedAt: '2026-04-20T08:00:00Z',
          updatedAt: '2026-04-20T09:00:00Z',
        })
      }

      if (pathname === '/admin/auth/logout' && method === 'POST') {
        return new Response(null, { status: 200 })
      }

      throw new Error(`Unhandled fetch request: ${method} ${pathname}`)
    })
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('loads persisted auth settings and forces re-login after a successful rotation', async () => {
    renderAuthSettingsPage()

    expect(await screen.findByRole('heading', { name: '控制台认证' })).toBeInTheDocument()
    expect(screen.getAllByText('首启随机密码').length).toBeGreaterThan(0)

    fireEvent.change(screen.getByLabelText('新账号'), {
      target: { value: 'rotated-admin' },
    })
    fireEvent.change(screen.getByLabelText('当前密码'), {
      target: { value: 'secret-123' },
    })
    fireEvent.change(screen.getByLabelText('新密码'), {
      target: { value: 'rotated-secret-456' },
    })
    fireEvent.change(screen.getByLabelText('确认新密码'), {
      target: { value: 'rotated-secret-456' },
    })

    fireEvent.click(screen.getByRole('button', { name: '保存账号 / 密码' }))

    expect(await screen.findByText('login destination ?reason=credentials-updated')).toBeInTheDocument()
    await waitFor(() => {
      expect(window.localStorage.getItem('x-ai-gateway:last-admin-username')).toBe('rotated-admin')
    })
  })
})

function jsonResponse(payload: unknown) {
  return new Response(JSON.stringify(payload), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}
