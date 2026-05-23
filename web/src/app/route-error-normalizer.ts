import { isRouteErrorResponse } from 'react-router-dom'

export type NormalizedRouteError = {
  title: string
  message: string
  detail?: string
  status?: number
}

const dynamicImportFailurePatterns = [
  'failed to fetch dynamically imported module',
  'importing a module script failed',
  'error loading dynamically imported module',
]

export function normalizeRouteError(error: unknown): NormalizedRouteError {
  if (isRouteErrorResponse(error)) {
    const routeError = error as { status: number; data: unknown }
    return {
      title: routeError.status === 404 ? '页面不存在' : '页面请求失败',
      message: routeError.status === 404
        ? '当前地址没有匹配的页面，请返回控制台或首页继续操作。'
        : '页面数据或路由请求没有成功完成，请稍后重试。',
      detail: normalizeDetail(routeError.data),
      status: routeError.status,
    }
  }

  if (error instanceof Error) {
    const isDynamicImportFailure = dynamicImportFailurePatterns.some((pattern) =>
      error.message.toLowerCase().includes(pattern),
    )

    return {
      title: isDynamicImportFailure ? '页面资源加载失败' : '页面运行出错',
      message: isDynamicImportFailure
        ? '当前页面的前端资源没有加载成功，通常刷新页面后即可重新拉取最新资源。'
        : '当前页面遇到运行异常，可以先刷新页面；如果问题反复出现，请保留技术细节用于排查。',
      detail: error.stack ?? error.message,
    }
  }

  return {
    title: '页面运行出错',
    message: '当前页面遇到未知异常，可以先刷新页面；如果问题反复出现，请保留当前地址用于排查。',
    detail: normalizeDetail(error),
  }
}

function normalizeDetail(value: unknown) {
  if (value == null) {
    return undefined
  }

  if (typeof value === 'string') {
    return value
  }

  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}
