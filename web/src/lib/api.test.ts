// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AUTH_UNAUTHORIZED_EVENT, ApiError, apiClient } from './api'

describe('apiClient', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('returns parsed json for successful responses', async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await expect(apiClient.get<{ ok: boolean }>('/admin/ping')).resolves.toEqual({ ok: true })
    expect(fetchMock).toHaveBeenCalledWith(
      '/admin/ping',
      expect.objectContaining({
        credentials: 'include',
      }),
    )
  })

  it('returns undefined for 204 void responses', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await expect(
      apiClient.delete<void>('/admin/ping', {
        responseType: 'void',
      }),
    ).resolves.toBeUndefined()
  })

  it('normalizes unauthorized responses', async () => {
    const unauthorizedListener = vi.fn()
    window.addEventListener(AUTH_UNAUTHORIZED_EVENT, unauthorizedListener)
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          code: 'UNAUTHORIZED',
          message: 'missing token',
          traceId: 'trace-401',
        }),
        {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    )

    await expect(apiClient.get('/admin/secure')).rejects.toMatchObject({
      status: 401,
      code: 'UNAUTHORIZED',
      message: 'missing token',
      traceId: 'trace-401',
    })
    expect(unauthorizedListener).toHaveBeenCalledTimes(1)
    window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, unauthorizedListener)
  })

  it('does not dispatch unauthorized events for auth endpoints', async () => {
    const unauthorizedListener = vi.fn()
    window.addEventListener(AUTH_UNAUTHORIZED_EVENT, unauthorizedListener)
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          code: 'UNAUTHORIZED',
          message: 'missing token',
        }),
        {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    )

    await expect(apiClient.get('/admin/auth/session')).rejects.toMatchObject({
      status: 401,
      code: 'UNAUTHORIZED',
    })
    expect(unauthorizedListener).not.toHaveBeenCalled()
    window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, unauthorizedListener)
  })

  it('preserves structured gateway rule errors', async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          code: 'GATEWAY_RULE_MATCHED',
          message: 'traffic blocked by rule',
          traceId: 'trace-429',
        }),
        {
          status: 429,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    )

    await expect(apiClient.get('/v1/chat/completions')).rejects.toMatchObject({
      status: 429,
      code: 'GATEWAY_RULE_MATCHED',
      message: 'traffic blocked by rule',
      traceId: 'trace-429',
    })
  })

  it('normalizes plain text server errors', async () => {
    fetchMock.mockResolvedValue(
      new Response('upstream unavailable', {
        status: 503,
        headers: { 'Content-Type': 'text/plain' },
      }),
    )

    await expect(apiClient.get('/admin/fail')).rejects.toMatchObject({
      status: 503,
      code: 'INTERNAL_ERROR',
      message: 'upstream unavailable',
    })
  })

  it('wraps network failures into ApiError', async () => {
    fetchMock.mockRejectedValue(new Error('socket hang up'))

    try {
      await apiClient.get('/admin/network')
      throw new Error('expected request to fail')
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect(error).toMatchObject({
        status: 0,
        code: 'NETWORK_ERROR',
        message: 'socket hang up',
      })
    }
  })
})
