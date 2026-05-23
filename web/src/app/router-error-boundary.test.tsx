// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { cleanup, render, screen } from '@testing-library/react'
import type { ReactElement } from 'react'
import { RouterProvider, createMemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AppProviders } from './providers'
import { appRoutes } from './router'
import { RouteErrorBoundary } from './route-error-boundary'
import { normalizeRouteError } from './route-error-normalizer'

type RouteForAssertion = {
  path?: string
  errorElement?: unknown
  children?: RouteForAssertion[]
}

function BrokenPage(): ReactElement {
  throw new Error('Failed to fetch dynamically imported module: http://localhost:5173/src/features/keys/keys-page.tsx')
}

describe('router error boundary', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('renders custom Chinese error page instead of the React Router default page', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const router = createMemoryRouter([
      {
        path: '/broken',
        element: <BrokenPage />,
        errorElement: <RouteErrorBoundary />,
      },
    ], {
      initialEntries: ['/broken'],
    })

    render(
      <AppProviders>
        <RouterProvider router={router} />
      </AppProviders>,
    )

    expect(await screen.findByRole('heading', { name: '页面资源加载失败' })).toBeInTheDocument()
    expect(screen.getByText('当前页面的前端资源没有加载成功，通常刷新页面后即可重新拉取最新资源。')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '刷新当前页面' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '返回控制台' })).toHaveAttribute('href', '/console/ops')
    expect(screen.getByRole('link', { name: '返回首页' })).toHaveAttribute('href', '/')
    expect(screen.queryByText('Unexpected Application Error!')).not.toBeInTheDocument()
    expect(screen.queryByText('Hey developer')).not.toBeInTheDocument()
  })

  it('adds errorElement to lazy console routes', () => {
    const routes = appRoutes as RouteForAssertion[]
    const consoleRoute = routes.find((route) => route.path === '/console')
    const keysRoute = consoleRoute?.children?.find((route) => route.path === 'keys')
    const consoleFallbackRoute = consoleRoute?.children?.find((route) => route.path === '*')
    const globalFallbackRoute = routes.find((route) => route.path === '*')

    expect(consoleRoute?.errorElement).toBeDefined()
    expect(keysRoute?.errorElement).toBeDefined()
    expect(consoleFallbackRoute?.errorElement).toBeDefined()
    expect(globalFallbackRoute?.errorElement).toBeDefined()
  })

  it('renders custom 404 page for unmatched routes', async () => {
    const router = createMemoryRouter(appRoutes, {
      initialEntries: ['/missing-route-for-error-boundary-test'],
    })

    render(
      <AppProviders>
        <RouterProvider router={router} />
      </AppProviders>,
    )

    expect(await screen.findByRole('heading', { name: '页面不存在' })).toBeInTheDocument()
    expect(screen.getByText('HTTP 404')).toBeInTheDocument()
    expect(screen.queryByText('Unexpected Application Error!')).not.toBeInTheDocument()
  })

  it('normalizes route response and unknown errors', () => {
    expect(normalizeRouteError(new Error('boom')).title).toBe('页面运行出错')
    expect(normalizeRouteError('plain failure')).toEqual({
      title: '页面运行出错',
      message: '当前页面遇到未知异常，可以先刷新页面；如果问题反复出现，请保留当前地址用于排查。',
      detail: 'plain failure',
    })
  })
})
