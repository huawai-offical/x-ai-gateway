import { useEffect, useMemo } from 'react'
import type { ReactNode } from 'react'
import { toast } from 'sonner'
import { ApiError } from '@/lib/api'
import { hasActionErrorToastShown } from './action-feedback'

type InlineErrorProps = {
  error: unknown
  title?: string
  action?: ReactNode
  retryLabel?: string
  onRetry?: () => void
}

export function InlineError({
  error,
  title = '请求失败',
  action,
  retryLabel = '重试',
  onRetry,
}: InlineErrorProps) {
  const detail = useMemo(() => normalizeErrorMessage(error), [error])

  useEffect(() => {
    if (hasActionErrorToastShown(error)) {
      return
    }

    const description = detail.traceId
      ? `${detail.message}\ntraceId: ${detail.traceId}`
      : detail.message

    toast.error(title, {
      id: buildToastId(title, detail.message, detail.traceId),
      description,
      duration: 4200,
      action: action
        ? action
        : onRetry
          ? {
              label: retryLabel,
              onClick: onRetry,
            }
          : undefined,
    })
  }, [action, detail.message, detail.traceId, error, onRetry, retryLabel, title])

  return null
}

function normalizeErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return { message: error.message, traceId: error.traceId ?? undefined }
  }

  if (error instanceof Error) {
    return { message: error.message, traceId: undefined }
  }

  return { message: '发生未知错误。', traceId: undefined }
}

function buildToastId(title: string, message: string, traceId?: string) {
  return `inline-error:${title}:${message}:${traceId ?? ''}`
}
