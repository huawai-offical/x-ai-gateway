// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { RouterProvider, createMemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppProviders } from './providers'
import { appRoutes } from './router'

vi.mock('../features/ops/ops-page', () => ({
  OpsPage: () => <div>ops page</div>,
}))

const fetchMock = vi.fn<typeof fetch>()

describe('operations router', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    cleanup()
    window.localStorage.clear()
    window.sessionStorage.clear()
    vi.unstubAllGlobals()
  })

  it('redirects unauthenticated users to login before loading protected routes', async () => {
    fetchMock.mockImplementation(async (input) => {
      const pathname = new URL(String(input), 'http://localhost').pathname
      if (pathname === '/admin/auth/session') {
        return new Response(
          JSON.stringify({
            authenticated: false,
            username: null,
            authenticatedAt: null,
            expiresAt: null,
          }),
          {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          },
        )
      }
      if (pathname === '/admin/auth/challenge') {
        return new Response(
          JSON.stringify({
            challengeId: 'challenge-redirect',
            mathPrompt: '1 + 1 = ?',
            issuedAt: '2026-04-20T12:00:00Z',
            expiresAt: '2026-04-20T12:05:00Z',
            powAlgorithm: 'SHA-256',
            powSalt: 'salt-redirect',
            powDifficulty: 0,
          }),
          {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          },
        )
      }

      throw new Error(`Unhandled fetch request: ${pathname}`)
    })

    const router = createMemoryRouter(appRoutes, {
      initialEntries: ['/console/operations/backups'],
    })

    render(
      <AppProviders>
        <RouterProvider router={router} />
      </AppProviders>,
    )

    expect(await screen.findByText('登录控制台')).toBeInTheDocument()
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/login')
      expect(router.state.location.search).toContain('redirect=')
      expect(decodeURIComponent(router.state.location.search)).toContain('/console/operations/backups')
    })
  })

  it.each([
    ['/console/operations/backups'],
    ['/console/operations/upgrades'],
    ['/console/operations/rollbacks'],
  ])('redirects retired operations route %s to ops overview', async (initialPath) => {
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

    const router = createMemoryRouter(appRoutes, {
      initialEntries: [initialPath],
    })

    render(
      <AppProviders>
        <RouterProvider router={router} />
      </AppProviders>,
    )

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/console/ops')
    })
    expect(await screen.findByText('ops page')).toBeInTheDocument()
  })

  it('redirects the console root to intelligent ops overview', async () => {
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

    const router = createMemoryRouter(appRoutes, {
      initialEntries: ['/console'],
    })

    render(
      <AppProviders>
        <RouterProvider router={router} />
      </AppProviders>,
    )

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/console/ops')
    })
  })

  it('redirects legacy console routes into the /console namespace', async () => {
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

    const router = createMemoryRouter(appRoutes, {
      initialEntries: ['/operations/backups?from=legacy'],
    })

    render(
      <AppProviders>
        <RouterProvider router={router} />
      </AppProviders>,
    )

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/console/ops')
    })
    expect(await screen.findByText('ops page')).toBeInTheDocument()
  })
})
