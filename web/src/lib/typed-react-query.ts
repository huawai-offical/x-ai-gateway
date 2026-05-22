import { useMutation, useQuery } from '@tanstack/react-query'

type QueryOptions<TData> = {
  queryKey: readonly unknown[]
  queryFn: () => Promise<TData>
  enabled?: boolean
  refetchInterval?: number
}

type MutationOptions<TData, TVariables> = {
  mutationFn: (variables: TVariables) => Promise<TData>
  onSuccess?: (data: TData) => void
}

export type TypedQueryResult<TData> = {
  data?: TData
  isPending: boolean
  isLoading: boolean
  error?: unknown
}

export type TypedMutationResult<TData, TVariables> = {
  data?: TData
  error?: unknown
  isPending: boolean
  variables?: TVariables
  mutate: (variables: TVariables) => void
  mutateAsync: (variables: TVariables) => Promise<TData>
  reset: () => void
}

export const useTypedQuery = useQuery as unknown as <TData>(options: QueryOptions<TData>) => TypedQueryResult<TData>
export const useTypedMutation = useMutation as unknown as <TData, TVariables = void>(options: MutationOptions<TData, TVariables>) => TypedMutationResult<TData, TVariables>
