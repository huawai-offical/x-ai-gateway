// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { cleanup, render } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { markActionErrorToastShown } from './action-feedback'
import { InlineError } from './inline-error'

const mockedToast = vi.hoisted(() => ({
  error: vi.fn(),
}))

vi.mock('sonner', async () => {
  const actual = await vi.importActual<typeof import('sonner')>('sonner')
  return {
    ...actual,
    toast: mockedToast,
  }
})

describe('InlineError', () => {
  afterEach(() => {
    cleanup()
    mockedToast.error.mockReset()
  })

  it('uses a transient toast instead of rendering an inline block', () => {
    const { container } = render(<InlineError error={new Error('未找到可用的 DistributedKey')} title="调试工作台预览或执行失败" />)

    expect(container).toBeEmptyDOMElement()
    expect(mockedToast.error).toHaveBeenCalledWith(
      '调试工作台预览或执行失败',
      expect.objectContaining({
        id: 'inline-error:调试工作台预览或执行失败:未找到可用的 DistributedKey:',
        description: '未找到可用的 DistributedKey',
        duration: 4200,
      }),
    )
  })

  it('keeps traceId in the toast description when an ApiError provides it', () => {
    render(
      <InlineError
        error={new ApiError({
          status: 404,
          code: 'DISTRIBUTED_KEY_NOT_FOUND',
          message: '未找到可用的 DistributedKey',
          traceId: 'trace-123',
        })}
        title="调试工作台预览或执行失败"
      />,
    )

    expect(mockedToast.error).toHaveBeenCalledWith(
      '调试工作台预览或执行失败',
      expect.objectContaining({
        id: 'inline-error:调试工作台预览或执行失败:未找到可用的 DistributedKey:trace-123',
        description: '未找到可用的 DistributedKey\ntraceId: trace-123',
        duration: 4200,
      }),
    )
  })

  it('does not duplicate errors already shown by global action feedback', () => {
    const error = new Error('刷新模型失败')
    markActionErrorToastShown(error)

    const { container } = render(<InlineError error={error} title="凭证操作失败" />)

    expect(container).toBeEmptyDOMElement()
    expect(mockedToast.error).not.toHaveBeenCalled()
  })
})
