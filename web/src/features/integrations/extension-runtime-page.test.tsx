// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../../lib/api'
import { ExtensionRuntimePage } from './extension-runtime-page'

vi.mock('../../lib/api', () => ({
  ApiError: class ApiError extends Error {},
  apiRequest: vi.fn(),
}))

const mockedApiRequest = apiRequest as unknown as ReturnType<typeof vi.fn>

function renderRuntime(slug: string) {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={[`/integrations/extensions/${slug}`]}>
        <Routes>
          <Route path="/integrations/extensions/:slug" element={<ExtensionRuntimePage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(() => {
  cleanup()
  mockedApiRequest.mockReset()
})

describe('ExtensionRuntimePage', () => {
  it('renders iframe with signed launch url for runnable extension app', async () => {
    mockedApiRequest.mockImplementation(async (url: string) => {
      if (url === '/admin/integrations/external-apps/runtime/grafana-panel') {
        return {
          app: {
            id: 1,
            appName: 'Grafana 面板',
            slug: 'grafana-panel',
            iframeUrl: 'https://grafana.example.com/d/live',
            allowedOrigin: 'https://grafana.example.com',
            sandboxPermissions: 'allow-scripts allow-forms',
            signingSecretFingerprint: 'fp_test',
            enabled: true,
            navEnabled: true,
          },
          signedContext: {
            slug: 'grafana-panel',
            origin: 'https://grafana.example.com',
            context: 'ctx_test',
            signature: 'sig_test',
            launchUrl: 'https://grafana.example.com/d/live?x_context=ctx_test&x_signature=sig_test',
            expiresAt: '2026-04-30T15:00:00Z',
          },
          runnable: true,
          runtimeStatus: 'READY',
          runtimeMessage: '扩展应用可以安全挂载。',
          actualOrigin: 'https://grafana.example.com',
        }
      }
      throw new Error(`unexpected url: ${url}`)
    })

    renderRuntime('grafana-panel')

    expect(await screen.findByRole('heading', { name: 'Grafana 面板' })).toBeInTheDocument()
    const iframe = await screen.findByTitle('Grafana 面板 扩展运行页')
    expect(iframe).toHaveAttribute('src', expect.stringContaining('x_context=ctx_test'))
    expect(iframe).toHaveAttribute('sandbox', 'allow-scripts allow-forms')
    await waitFor(() => {
      expect(mockedApiRequest).toHaveBeenCalledWith(
        '/admin/integrations/external-apps/runtime/grafana-panel',
        expect.objectContaining({
          params: expect.objectContaining({
            actor: 'console-extension-runtime',
            ttlSeconds: 300,
          }),
        }),
      )
    })
    expect(mockedApiRequest).toHaveBeenCalledTimes(1)
  })

  it('blocks disabled and hidden extension app before signed context request', async () => {
    mockedApiRequest.mockImplementation(async (url: string) => {
      if (url === '/admin/integrations/external-apps/runtime/hidden-app') {
        return {
          app: {
            id: 2,
            appName: 'Hidden App',
            slug: 'hidden-app',
            iframeUrl: 'https://hidden.example.com/app',
            allowedOrigin: 'https://hidden.example.com',
            enabled: true,
            navEnabled: false,
          },
          signedContext: null,
          runnable: false,
          runtimeStatus: 'NAV_DISABLED',
          runtimeMessage: '应用未启用导航展示，运行页暂不开放。',
          actualOrigin: 'https://hidden.example.com',
        }
      }
      throw new Error(`unexpected url: ${url}`)
    })

    renderRuntime('hidden-app')

    expect(await screen.findByText('应用未启用导航展示，运行页暂不开放。')).toBeInTheDocument()
    expect(mockedApiRequest).toHaveBeenCalledTimes(1)
  })

  it('blocks origin mismatch before mounting iframe', async () => {
    mockedApiRequest.mockImplementation(async (url: string) => {
      if (url === '/admin/integrations/external-apps/runtime/mismatched-app') {
        return {
          app: {
            id: 3,
            appName: 'Mismatched App',
            slug: 'mismatched-app',
            iframeUrl: 'https://evil.example.com/app',
            allowedOrigin: 'https://trusted.example.com',
            enabled: true,
            navEnabled: true,
          },
          signedContext: null,
          runnable: false,
          runtimeStatus: 'ORIGIN_MISMATCH',
          runtimeMessage: 'iframe URL 来源 https://evil.example.com 与允许来源 https://trusted.example.com 不匹配。',
          actualOrigin: 'https://evil.example.com',
        }
      }
      throw new Error(`unexpected url: ${url}`)
    })

    renderRuntime('mismatched-app')

    expect(await screen.findByText(/与允许来源 https:\/\/trusted.example.com 不匹配/)).toBeInTheDocument()
    expect(screen.queryByTitle('Mismatched App 扩展运行页')).not.toBeInTheDocument()
  })
})
