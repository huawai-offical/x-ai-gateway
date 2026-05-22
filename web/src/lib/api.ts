export type ApiErrorResponse = {
  code: string
  message: string
  traceId?: string | null
}

export const AUTH_UNAUTHORIZED_EVENT = 'x-ai-gateway:auth-unauthorized'

type QueryParamValue =
  | string
  | number
  | boolean
  | null
  | undefined
  | Array<string | number | boolean>

type ApiRequestOptions = Omit<RequestInit, 'body'> & {
  body?: BodyInit | Record<string, unknown> | unknown[] | null
  params?: Record<string, QueryParamValue>
  responseType?: 'json' | 'text' | 'void'
  timeoutMs?: number
}

const JSON_CONTENT_TYPE = 'application/json'

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly traceId?: string | null
  readonly details?: unknown
  readonly isNetworkError: boolean

  constructor({
    status,
    code,
    message,
    traceId,
    details,
    isNetworkError = false,
  }: {
    status: number
    code: string
    message: string
    traceId?: string | null
    details?: unknown
    isNetworkError?: boolean
  }) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.traceId = traceId
    this.details = details
    this.isNetworkError = isNetworkError
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

export async function apiRequest<T>(
  input: string,
  init?: ApiRequestOptions,
): Promise<T> {
  const controller = new AbortController()
  const timeoutMs = init?.timeoutMs ?? 15_000
  const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs)

  try {
    const url = appendQueryParams(input, init?.params)
    const headers = new Headers(init?.headers)
    const body = serializeBody(init?.body, headers)

    const response = await fetch(url, {
      ...init,
      body,
      credentials: init?.credentials ?? 'include',
      headers,
      signal: mergeAbortSignal(init?.signal, controller.signal),
    })

    if (!response.ok) {
      const apiError = await toApiError(response)
      dispatchUnauthorized(input, apiError.status)
      throw apiError
    }

    if (init?.responseType === 'void' || response.status === 204) {
      return undefined as T
    }

    if (init?.responseType === 'text') {
      return (await response.text()) as T
    }

    return (await parseSuccessBody(response)) as T
  } catch (error) {
    if (error instanceof ApiError) {
      throw error
    }

    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new ApiError({
        status: 0,
        code: 'REQUEST_ABORTED',
        message: '请求超时或被取消。',
        isNetworkError: true,
      })
    }

    throw new ApiError({
      status: 0,
      code: 'NETWORK_ERROR',
      message: error instanceof Error ? error.message : '网络请求失败。',
      details: error,
      isNetworkError: true,
    })
  } finally {
    window.clearTimeout(timeoutId)
  }
}

export const apiClient = {
  request: apiRequest,
  get: <T>(input: string, init?: Omit<ApiRequestOptions, 'method'>) =>
    apiRequest<T>(input, { ...init, method: 'GET' }),
  post: <T>(input: string, init?: Omit<ApiRequestOptions, 'method'>) =>
    apiRequest<T>(input, { ...init, method: 'POST' }),
  put: <T>(input: string, init?: Omit<ApiRequestOptions, 'method'>) =>
    apiRequest<T>(input, { ...init, method: 'PUT' }),
  patch: <T>(input: string, init?: Omit<ApiRequestOptions, 'method'>) =>
    apiRequest<T>(input, { ...init, method: 'PATCH' }),
  delete: <T>(input: string, init?: Omit<ApiRequestOptions, 'method'>) =>
    apiRequest<T>(input, { ...init, method: 'DELETE' }),
}

function appendQueryParams(
  input: string,
  params?: Record<string, QueryParamValue>,
) {
  if (!params || !Object.keys(params).length) {
    return input
  }

  const url = new URL(input, window.location.origin)
  for (const [key, value] of Object.entries(params)) {
    if (value == null) continue
    if (Array.isArray(value)) {
      value.forEach((item) => url.searchParams.append(key, String(item)))
      continue
    }
    url.searchParams.set(key, String(value))
  }

  return `${url.pathname}${url.search}${url.hash}`
}

function serializeBody(body: ApiRequestOptions['body'], headers: Headers) {
  if (body == null) return null
  if (
    typeof body === 'string' ||
    body instanceof FormData ||
    body instanceof URLSearchParams ||
    body instanceof Blob ||
    body instanceof ArrayBuffer
  ) {
    if (typeof body === 'string' && !headers.has('Content-Type')) {
      headers.set('Content-Type', JSON_CONTENT_TYPE)
    }
    return body as BodyInit
  }

  if (!headers.has('Content-Type')) {
    headers.set('Content-Type', JSON_CONTENT_TYPE)
  }
  return JSON.stringify(body)
}

async function parseSuccessBody(response: Response) {
  const contentType = response.headers.get('Content-Type') ?? ''
  if (contentType.includes(JSON_CONTENT_TYPE) || contentType.includes('+json')) {
    return response.json()
  }

  const text = await response.text()
  if (!text) return undefined
  return text
}

async function toApiError(response: Response) {
  const contentType = response.headers.get('Content-Type') ?? ''

  if (contentType.includes(JSON_CONTENT_TYPE) || contentType.includes('+json')) {
    const payload = (await response.json()) as Partial<ApiErrorResponse> & Record<string, unknown>
    return new ApiError({
      status: response.status,
      code: normalizeErrorCode(response.status, payload.code),
      message: payload.message ?? fallbackErrorMessage(response.status),
      traceId: payload.traceId,
      details: payload,
    })
  }

  const text = await response.text()
  return new ApiError({
    status: response.status,
    code: normalizeErrorCode(response.status),
    message: text || fallbackErrorMessage(response.status),
    details: text,
  })
}

function normalizeErrorCode(status: number, code?: string) {
  if (code) return code
  if (status === 401) return 'UNAUTHORIZED'
  if (status === 404) return 'NOT_FOUND'
  if (status === 400) return 'INVALID_ARGUMENT'
  if (status >= 500) return 'INTERNAL_ERROR'
  return `HTTP_${status}`
}

function fallbackErrorMessage(status: number) {
  if (status === 401) return '未授权，请检查网关身份信息。'
  if (status === 404) return '请求的资源不存在。'
  if (status === 400) return '请求参数不合法。'
  if (status >= 500) return '服务暂时不可用，请稍后重试。'
  return `请求失败：${status}`
}

function mergeAbortSignal(
  externalSignal: AbortSignal | null | undefined,
  internalSignal: AbortSignal,
) {
  if (!externalSignal) return internalSignal
  if (externalSignal.aborted) return externalSignal

  const controller = new AbortController()

  const forward = () => controller.abort()
  externalSignal.addEventListener('abort', forward)
  internalSignal.addEventListener('abort', forward)

  return controller.signal
}

function dispatchUnauthorized(input: string, status: number) {
  if (status !== 401 || typeof window === 'undefined') {
    return
  }

  const pathname = new URL(input, window.location.origin).pathname
  if (pathname.startsWith('/admin/auth/')) {
    return
  }

  window.dispatchEvent(new CustomEvent(AUTH_UNAUTHORIZED_EVENT))
}
