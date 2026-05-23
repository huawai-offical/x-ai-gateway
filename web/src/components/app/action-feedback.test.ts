import { describe, expect, it, vi, afterEach } from 'vitest'
import { ApiError } from '@/lib/api'
import {
  hasActionErrorToastShown,
  showActionErrorToast,
  showActionSuccessToast,
} from './action-feedback'

const mockedToast = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
}))

vi.mock('sonner', async () => {
  const actual = await vi.importActual<typeof import('sonner')>('sonner')
  return {
    ...actual,
    toast: mockedToast,
  }
})

describe('action-feedback', () => {
  afterEach(() => {
    mockedToast.success.mockReset()
    mockedToast.error.mockReset()
  })

  it('shows a default success toast for mutations without explicit copy', () => {
    showActionSuccessToast(undefined, { ok: true }, undefined)

    expect(mockedToast.success).toHaveBeenCalledWith(
      '操作成功',
      expect.objectContaining({
        id: 'action-feedback:success:操作成功',
      }),
    )
  })

  it('uses metadata to render contextual success copy', () => {
    showActionSuccessToast({
      actionName: '刷新凭证模型',
      successMessage: (data) => `模型刷新完成：发现 ${(data as { modelCount: number }).modelCount} 个模型。`,
    }, { modelCount: 3 }, 7)

    expect(mockedToast.success).toHaveBeenCalledWith(
      '模型刷新完成：发现 3 个模型。',
      expect.any(Object),
    )
  })

  it('shows failure details and marks the error for InlineError de-duplication', () => {
    const error = new ApiError({
      status: 500,
      code: 'MODEL_REFRESH_FAILED',
      message: '上游模型刷新失败',
      traceId: 'trace-action',
    })

    showActionErrorToast({ actionName: '刷新凭证模型' }, error, 7)

    expect(hasActionErrorToastShown(error)).toBe(true)
    expect(mockedToast.error).toHaveBeenCalledWith(
      '刷新凭证模型失败',
      expect.objectContaining({
        description: '上游模型刷新失败\ntraceId: trace-action',
      }),
    )
  })

  it('can suppress success toast for custom flows', () => {
    showActionSuccessToast({ suppressSuccessToast: true }, undefined, undefined)

    expect(mockedToast.success).not.toHaveBeenCalled()
  })
})
