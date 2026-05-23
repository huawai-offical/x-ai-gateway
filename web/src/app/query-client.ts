import { MutationCache, QueryClient } from '@tanstack/react-query'
import { showActionErrorToast, showActionSuccessToast, type ActionFeedbackMeta } from '@/components/app/action-feedback'

type MutationWithMeta = {
  options: {
    meta?: unknown
  }
}

export const queryClient = new QueryClient({
  mutationCache: new MutationCache({
    onSuccess: (
      data: unknown,
      variables: unknown,
      _context: unknown,
      mutation: MutationWithMeta,
    ) => {
      showActionSuccessToast(mutation.options.meta as ActionFeedbackMeta | undefined, data, variables)
    },
    onError: (
      error: unknown,
      variables: unknown,
      _context: unknown,
      mutation: MutationWithMeta,
    ) => {
      showActionErrorToast(mutation.options.meta as ActionFeedbackMeta | undefined, error, variables)
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 10_000,
      refetchOnWindowFocus: false,
    },
  },
})
