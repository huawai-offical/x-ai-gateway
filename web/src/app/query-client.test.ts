import { afterEach, describe, expect, it, vi } from 'vitest'
import { queryClient } from './query-client'

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

describe('queryClient action feedback', () => {
  afterEach(() => {
    mockedToast.success.mockReset()
    mockedToast.error.mockReset()
    queryClient.clear()
  })

  it('shows success toast from the global mutation cache', async () => {
    const mutation = queryClient.getMutationCache().build(queryClient, {
      mutationFn: async () => ({ ok: true }),
    })

    await mutation.execute(undefined)

    expect(mockedToast.success).toHaveBeenCalledWith('操作成功', expect.any(Object))
  })

  it('shows custom error toast from mutation metadata', async () => {
    const mutation = queryClient.getMutationCache().build(queryClient, {
      mutationFn: async () => {
        throw new Error('刷新失败')
      },
      meta: {
        actionName: '刷新凭证模型',
      },
    })

    await expect(mutation.execute(undefined)).rejects.toThrow('刷新失败')

    expect(mockedToast.error).toHaveBeenCalledWith(
      '刷新凭证模型失败',
      expect.objectContaining({
        description: '刷新失败',
      }),
    )
  })
})
