import { toast } from 'sonner'
import { ApiError } from '@/lib/api'

export type ActionFeedbackMeta = {
  actionName?: string
  successMessage?: string | ((data: unknown, variables: unknown) => string | null | undefined)
  errorMessage?: string | ((error: unknown, variables: unknown) => string | null | undefined)
  suppressSuccessToast?: boolean
  suppressErrorToast?: boolean
}

const actionErrorToastShown = new WeakSet<object>()

export function showActionSuccessToast(
  meta: ActionFeedbackMeta | undefined,
  data: unknown,
  variables: unknown,
) {
  if (meta?.suppressSuccessToast) {
    return
  }

  const message = resolveMessage(meta?.successMessage, data, variables)
  const title = message ?? buildSuccessTitle(meta?.actionName)

  toast.success(title, {
    id: buildToastId('success', title),
    duration: 2600,
  })
}

export function showActionErrorToast(
  meta: ActionFeedbackMeta | undefined,
  error: unknown,
  variables: unknown,
) {
  if (meta?.suppressErrorToast) {
    return
  }

  markActionErrorToastShown(error)

  const detail = normalizeErrorMessage(error)
  const message = resolveMessage(meta?.errorMessage, error, variables) ?? buildErrorTitle(meta?.actionName)
  const description = detail.traceId
    ? `${detail.message}\ntraceId: ${detail.traceId}`
    : detail.message

  toast.error(message, {
    id: buildToastId('error', message, detail.message, detail.traceId),
    description,
    duration: 4200,
  })
}

export function markActionErrorToastShown(error: unknown) {
  if (isObject(error)) {
    actionErrorToastShown.add(error)
  }
}

export function hasActionErrorToastShown(error: unknown) {
  return isObject(error) && actionErrorToastShown.has(error)
}

function resolveMessage(
  message: ActionFeedbackMeta['successMessage'] | ActionFeedbackMeta['errorMessage'] | undefined,
  value: unknown,
  variables: unknown,
) {
  if (typeof message === 'function') {
    const resolved = message(value, variables)
    return resolved?.trim() || undefined
  }
  return message?.trim() || undefined
}

function buildSuccessTitle(actionName?: string) {
  return actionName?.trim() ? `${actionName.trim()}成功` : '操作成功'
}

function buildErrorTitle(actionName?: string) {
  return actionName?.trim() ? `${actionName.trim()}失败` : '操作失败'
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

function buildToastId(...parts: Array<string | undefined>) {
  return `action-feedback:${parts.map((part) => part ?? '').join(':')}`
}

function isObject(value: unknown): value is object {
  return (typeof value === 'object' && value !== null) || typeof value === 'function'
}
