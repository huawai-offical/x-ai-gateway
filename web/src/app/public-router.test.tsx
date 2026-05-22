// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { RouterProvider, createMemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppProviders } from './providers'
import { appRoutes } from './router'

const fetchMock = vi.fn<typeof fetch>()

describe('public router', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('loads the visitor home without bootstrapping admin auth', async () => {
    const consoleWarnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const router = createMemoryRouter(appRoutes, {
      initialEntries: ['/'],
    })

    render(
      <AppProviders>
        <RouterProvider router={router} />
      </AppProviders>,
    )

    expect(await screen.findByRole('heading', { name: 'x-ai-gateway' })).toBeInTheDocument()
    await waitFor(() => {
      expect(fetchMock).not.toHaveBeenCalled()
    })

    const consoleMessages = [...consoleWarnSpy.mock.calls.flat(), ...consoleErrorSpy.mock.calls.flat()]
      .map((message) => String(message))
      .join('\n')
    expect(consoleMessages).not.toContain('HydrateFallback')
  })

  it.each(['/docs', '/pricing', '/status'])('redirects retired public route %s to home', async (initialPath) => {
    const router = createMemoryRouter(appRoutes, {
      initialEntries: [initialPath],
    })

    render(
      <AppProviders>
        <RouterProvider router={router} />
      </AppProviders>,
    )

    expect(await screen.findByRole('heading', { name: 'x-ai-gateway' })).toBeInTheDocument()
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/')
    })
  })
})
