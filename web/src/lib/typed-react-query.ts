import { useMutation, useQuery } from '@tanstack/react-query'

type QueryOptions<TData> = {
  queryKey: readonly unknown[]
  queryFn: () => Promise<TData>
  enabled?: boolean
}

type MutationOptions<TData, TVariables> = {
  mutationFn: (variables: TVariables) => Promise<TData>
  onSuccess?: (data: TData) => void
}

export type TypedQueryResult<TData> = {
  data?: TData
}

export type TypedMutationResult<TData, TVariables> = {
  data?: TData
  isPending: boolean
  mutate: (variables: TVariables) => void
  mutateAsync: (variables: TVariables) => Promise<TData>
}

export const useTypedQuery = useQuery as unknown as <TData>(options: QueryOptions<TData>) => TypedQueryResult<TData>
export const useTypedMutation = useMutation as unknown as <TData, TVariables = void>(options: MutationOptions<TData, TVariables>) => TypedMutationResult<TData, TVariables>
